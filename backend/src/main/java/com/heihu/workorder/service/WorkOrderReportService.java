package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.WorkOrderReportApprovalRequest;
import com.heihu.workorder.dto.WorkOrderReportImageResponse;
import com.heihu.workorder.dto.WorkOrderReportRequest;
import com.heihu.workorder.dto.WorkOrderReportResponse;
import com.heihu.workorder.dto.WorkOrderRequest;
import com.heihu.workorder.dto.WorkOrderReporterResponse;
import com.heihu.workorder.util.DateTimeParser;
import com.heihu.workorder.util.ReportPermission;
import com.heihu.workorder.util.WorkOrderStates;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.ReportStatus;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.model.WorkOrderReport;
import com.heihu.workorder.model.WorkOrderReportImage;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderReportImageRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional
public class WorkOrderReportService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderReportService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_IMAGE_SIZE = 10L * 1024L * 1024L;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkOrderReportRepository reportRepository;
    private final WorkOrderReportImageRepository imageRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;
    private final ReportImageStorage reportImageStorage;
    private final NotificationService notificationService;

    @Value("${app.upload.pending-image-ttl-hours:24}")
    private int pendingImageTtlHours;

    public WorkOrderReportService(
            WorkOrderReportRepository reportRepository,
            WorkOrderReportImageRepository imageRepository,
            WorkOrderRepository workOrderRepository,
            UserRepository userRepository,
            ReportImageStorage reportImageStorage,
            NotificationService notificationService
    ) {
        this.reportRepository = reportRepository;
        this.imageRepository = imageRepository;
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
        this.reportImageStorage = reportImageStorage;
        this.notificationService = notificationService;
    }

    public WorkOrderReportImageResponse uploadImage(Long factoryId, Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单张图片不能超过10MB");
        }
        String originalName = sanitizeOriginalFileName(file.getOriginalFilename());
        String ext = extension(originalName);
        String contentType = normalizeContentType(file.getContentType());
        if (!isAllowedExt(ext)) {
            ext = extensionFromContentType(contentType);
        }
        if (!isAllowedImageMetadata(ext, contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持 jpg、jpeg、png、webp 图片");
        }
        validateImageSignature(file, ext);
        FactoryUser uploader = requireUser(factoryId, userId);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        reportImageStorage.store(storedName, file);
        boolean transactionOwnsCleanup = cleanupImageOnRollback(storedName);

        WorkOrderReportImage image = new WorkOrderReportImage();
        image.setFactory(uploader.getFactory());
        image.setUploader(uploader);
        image.setOriginalFileName(originalName);
        image.setStoredFileName(storedName);
        image.setAccessPath("/api/reports/images/pending");
        image.setFileSize(file.getSize());
        image.setContentType(StringUtils.hasText(contentType) ? contentType : resolvedContentType(ext));
        image.setCreatedAt(LocalDateTime.now());
        try {
            WorkOrderReportImage saved = imageRepository.save(image);
            saved.setAccessPath("/api/mobile/reports/images/" + saved.getId() + "/file");
            return WorkOrderReportImageResponse.from(imageRepository.save(saved));
        } catch (RuntimeException ex) {
            if (!transactionOwnsCleanup) deleteUncommittedImage(storedName);
            throw ex;
        }
    }

    private boolean cleanupImageOnRollback(String storedName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) deleteUncommittedImage(storedName);
            }
        });
        return true;
    }

    private void deleteUncommittedImage(String storedName) {
        try {
            if (!reportImageStorage.delete(storedName)) {
                log.warn("Unable to remove rolled-back report image: {}", storedName);
            }
        } catch (RuntimeException ex) {
            log.warn("Unable to remove rolled-back report image: {}", storedName, ex);
        }
    }

    @Transactional(readOnly = true)
    public ReportImageDownload getImageDownload(Long factoryId, Long imageId) {
        WorkOrderReportImage image = imageRepository.findById(imageId)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "图片不存在"));
        return new ReportImageDownload(image, reportImageStorage.load(image.getStoredFileName()));
    }

    @CacheEvict(cacheNames = {"reports.admin.list", "reports.mobile.list", "reports.detail", "workorders.list", "workorders.detail", "workorders.code"}, allEntries = true)
    public WorkOrderReportResponse createReport(Long factoryId, Long userId, WorkOrderReportRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请填写报工信息");
        }
        FactoryUser currentUser = requireUser(factoryId, userId);
        FactoryUser reporter = resolveReporter(factoryId, request.getReporterUserId(), currentUser);
        if (request.getWorkOrderId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择工单");
        }
        if (request.getProcessSequenceNo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择工序");
        }
        int reportQty = requireNonNegative(request.getReportQuantity(), "报工数");
        int goodQty = requireNonNegative(request.getGoodQuantity(), "良品数");
        int defectQty = requireNonNegative(request.getDefectQuantity(), "不良数");
        int duration = requireNonNegative(request.getReportDurationMinutes(), "报工时长");
        if (reportQty <= 0 && goodQty <= 0 && defectQty <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请填写报工数量");
        }
        if (reportQty != (long) goodQty + defectQty) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "良品数与不良数之和必须等于报工数");
        }
        if (request.getImageIds() != null && request.getImageIds().size() > 9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单次报工最多上传9张图片");
        }
        WorkOrder workOrder = workOrderRepository.findByIdAndFactoryIdForUpdate(request.getWorkOrderId(), factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工单不存在"));

        requireReportableOrder(workOrder);
        WorkOrderRequest.TaskItem task = requireTask(workOrder, request.getProcessSequenceNo());
        if (!ReportPermission.allows(task, currentUser) || !ReportPermission.allows(task, reporter)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "没有该工序的报工权限");
        }
        LocalDateTime startTime = DateTimeParser.parse(request.getStartTime());
        LocalDateTime endTime = DateTimeParser.parse(request.getEndTime());
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束时间不能早于开始时间");
        }

        WorkOrderReport report = new WorkOrderReport();
        LocalDateTime now = LocalDateTime.now();
        report.setFactory(workOrder.getFactory());
        report.setWorkOrder(workOrder);
        report.setWorkOrderCode(workOrder.getCode());
        report.setProductCode(workOrder.getProductCode());
        report.setProductName(workOrder.getProductName());
        report.setProductSpec(workOrder.getProductSpec());
        report.setProcessSequenceNo(request.getProcessSequenceNo());
        report.setProcessName(task.getProcessName());
        report.setReporter(reporter);
        report.setSubmitterId(currentUser.getId());
        report.setReporterName(reporter.getName());
        report.setReportQuantity(reportQty);
        report.setGoodQuantity(goodQty);
        report.setDefectQuantity(defectQty);
        report.setDefectItemName(normalize(request.getDefectItemName()));
        report.setReportDurationMinutes(duration);
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        report.setMaterialName(normalize(request.getMaterialName()));
        report.setRemark(normalize(request.getRemark()));
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        WorkOrderReport saved = reportRepository.save(report);
        attachImages(factoryId, userId, saved, request.getImageIds());
        notificationService.publishReportSubmitted(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reports.admin.list", key = "#factoryId + ':' + #status + ':' + #workOrderCode + ':' + #reporterName + ':' + #period + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<WorkOrderReportResponse> listAdmin(
            Long factoryId,
            String status,
            String workOrderCode,
            String reporterName,
            String period,
            Pageable pageable
    ) {
        return toPageResponse(factoryId, reportRepository.search(
                factoryId,
                parseStatus(status),
                normalizeToEmpty(workOrderCode),
                normalizeToEmpty(reporterName),
                periodStart(period),
                periodEnd(period),
                pageable
        ));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reports.mobile.list", key = "#factoryId + ':' + #userId + ':' + #includeSubmitted + ':' + #workOrderId + ':' + #status + ':' + #period + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<WorkOrderReportResponse> listMobile(Long factoryId, Long userId, boolean includeSubmitted,
                                                   Long workOrderId, String status, String period, Pageable pageable) {
        return toPageResponse(factoryId, reportRepository.searchMobile(
                factoryId, userId, includeSubmitted, workOrderId, parseStatus(status), periodStart(period), periodEnd(period), pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reports.detail", key = "#factoryId + ':' + #reportId")
    public WorkOrderReportResponse get(Long factoryId, Long reportId) {
        WorkOrderReport report = reportRepository.findById(reportId)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报工记录不存在"));
        return toResponse(report);
    }

    @CacheEvict(cacheNames = {"reports.admin.list", "reports.mobile.list", "reports.detail", "workorders.list", "workorders.detail", "workorders.code"}, allEntries = true)
    public WorkOrderReportResponse approve(Long factoryId, Long approverId, Long reportId, WorkOrderReportApprovalRequest request) {
        String remark = validateApprovalRemark(request, false);
        WorkOrderReport report = requirePending(factoryId, reportId);
        FactoryUser approver = requireUser(factoryId, approverId);
        LocalDateTime now = LocalDateTime.now();
        report.setStatus(ReportStatus.APPROVED);
        report.setApprover(approver);
        report.setApproverName(approver.getName());
        report.setApprovedAt(now);
        report.setApprovalRemark(remark);
        report.setUpdatedAt(now);
        applyApprovedReport(report, now);
        WorkOrderReport saved = reportRepository.save(report);
        notificationService.publishReportDecision(saved, true);
        return toResponse(saved);
    }

    @CacheEvict(cacheNames = {"reports.admin.list", "reports.mobile.list", "reports.detail"}, allEntries = true)
    public WorkOrderReportResponse reject(Long factoryId, Long approverId, Long reportId, WorkOrderReportApprovalRequest request) {
        String remark = validateApprovalRemark(request, true);
        WorkOrderReport report = requirePending(factoryId, reportId);
        FactoryUser approver = requireUser(factoryId, approverId);
        LocalDateTime now = LocalDateTime.now();
        report.setStatus(ReportStatus.REJECTED);
        report.setApprover(approver);
        report.setApproverName(approver.getName());
        report.setApprovedAt(now);
        report.setApprovalRemark(remark);
        report.setUpdatedAt(now);
        WorkOrderReport saved = reportRepository.save(report);
        notificationService.publishReportDecision(saved, false);
        return toResponse(saved);
    }

    private String validateApprovalRemark(WorkOrderReportApprovalRequest request, boolean required) {
        String remark = normalize(request == null ? null : request.getRemark());
        if (required && remark.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请填写驳回原因");
        if (remark.length() > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "审批备注不能超过500字");
        return remark;
    }

    @Scheduled(cron = "${app.upload.pending-image-cleanup-cron:0 0 3 * * *}")
    public int cleanupExpiredPendingImages() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(1, pendingImageTtlHours));
        List<WorkOrderReportImage> expired = imageRepository.findByReportIsNullAndCreatedAtBefore(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }

        List<WorkOrderReportImage> removable = expired.stream()
                .filter(image -> reportImageStorage.delete(image.getStoredFileName()))
                .collect(Collectors.toList());
        if (!removable.isEmpty()) {
            imageRepository.deleteAll(removable);
        }
        log.info("清理过期未绑定报工图片：expired={}, removed={}", expired.size(), removable.size());
        return removable.size();
    }

    private void attachImages(Long factoryId, Long userId, WorkOrderReport report, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) return;
        List<WorkOrderReportImage> images = imageRepository.findByFactoryIdAndIdIn(factoryId, imageIds);
        if (images.size() != imageIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片不存在或不属于当前工厂");
        }
        for (WorkOrderReportImage image : images) {
            if (image.getReport() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "图片已关联其他报工");
            }
            if (!image.getUploader().getId().equals(userId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "不能使用其他用户上传的图片");
            }
            image.setReport(report);
        }
        imageRepository.saveAll(images);
    }

    private void applyApprovedReport(WorkOrderReport report, LocalDateTime now) {
        WorkOrder workOrder = workOrderRepository.findByIdAndFactoryIdForUpdate(
                        report.getWorkOrder().getId(), report.getFactory().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工单不存在"));
        List<WorkOrderRequest.TaskItem> tasks = readTasks(workOrder.getTaskDataJson());
        boolean matched = false;
        for (WorkOrderRequest.TaskItem task : tasks) {
            if (task.getSequenceNo() != null && task.getSequenceNo().equals(report.getProcessSequenceNo())
                    && normalize(task.getProcessName()).equals(normalize(report.getProcessName()))) {
                task.setCompletedQuantity(addQuantity(task.getCompletedQuantity(), report.getGoodQuantity()));
                task.setApprovedQuantity(addQuantity(task.getApprovedQuantity(), report.getGoodQuantity()));
                task.setDefectQuantity(addQuantity(task.getDefectQuantity(), report.getDefectQuantity()));
                task.setLastReportTime(now.format(FMT));
                matched = true;
                break;
            }
        }
        if (!matched) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "报工工序已移除或变更，不能审批");
        }
        workOrder.setTaskDataJson(writeJson(tasks));
        workOrder.setGoodQuantity(addQuantity(workOrder.getGoodQuantity(), report.getGoodQuantity()));
        workOrder.setDefectQuantity(addQuantity(workOrder.getDefectQuantity(), report.getDefectQuantity()));
        long total = (long) nonNegative(workOrder.getGoodQuantity()) + nonNegative(workOrder.getDefectQuantity());
        if (total > 0) {
            workOrder.setDefectRate(String.format(Locale.US, "%.2f%%", workOrder.getDefectQuantity() * 100.0 / total));
        }
        workOrder.setUpdatedAt(now);
        workOrderRepository.save(workOrder);
    }

    private WorkOrderReport requirePending(Long factoryId, Long reportId) {
        WorkOrderReport report = reportRepository.findByIdAndFactoryIdForUpdate(reportId, factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报工记录不存在"));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "该报工已处理，请刷新列表");
        }
        return report;
    }

    private FactoryUser requireUser(Long factoryId, Long userId) {
        return userRepository.findByIdAndFactoryId(userId, factoryId)
                .filter(FactoryUser::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    private FactoryUser resolveReporter(Long factoryId, Long reporterUserId, FactoryUser currentUser) {
        if (reporterUserId == null || reporterUserId.equals(currentUser.getId())) {
            return currentUser;
        }
        return userRepository.findByIdAndFactoryId(reporterUserId, factoryId)
                .filter(FactoryUser::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "生产人员不存在或已停用"));
    }

    private WorkOrderReportResponse toResponse(WorkOrderReport report) {
        List<WorkOrderReportImageResponse> images = imageRepository.findByReportIdOrderByCreatedAtAsc(report.getId())
                .stream()
                .map(WorkOrderReportImageResponse::from)
                .collect(Collectors.toList());
        return WorkOrderReportResponse.from(report, images);
    }

    private Page<WorkOrderReportResponse> toPageResponse(Long factoryId, Page<WorkOrderReport> page) {
        if (page.isEmpty()) return page.map(report -> WorkOrderReportResponse.from(report, Collections.emptyList()));
        List<Long> ids = page.stream().map(WorkOrderReport::getId).collect(Collectors.toList());
        Map<Long, List<WorkOrderReportImageResponse>> images = imageRepository
                .findByFactoryIdAndReportIdInOrderByCreatedAtAscIdAsc(factoryId, ids).stream()
                .collect(Collectors.groupingBy(image -> image.getReport().getId(),
                        Collectors.mapping(WorkOrderReportImageResponse::from, Collectors.toList())));
        return page.map(report -> WorkOrderReportResponse.from(report,
                images.getOrDefault(report.getId(), new ArrayList<>())));
    }

    @Transactional(readOnly = true)
    public List<WorkOrderReporterResponse> listReporters(Long factoryId, Long workOrderId, Integer sequenceNo) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工单不存在"));
        requireReportableOrder(workOrder);
        List<WorkOrderRequest.TaskItem> tasks = sequenceNo == null ? readTasks(workOrder.getTaskDataJson())
                : Collections.singletonList(requireTask(workOrder, sequenceNo));
        return userRepository.findByFactoryIdAndEnabledTrue(factoryId).stream()
                .filter(user -> tasks.stream().anyMatch(task -> ReportPermission.allows(task, user)))
                .map(WorkOrderReporterResponse::new)
                .collect(Collectors.toList());
    }

    private void requireReportableOrder(WorkOrder workOrder) {
        if (!WorkOrderStates.isReportable(workOrder.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "已暂停、结束或取消的工单不能报工");
        }
    }

    private WorkOrderRequest.TaskItem requireTask(WorkOrder workOrder, Integer sequenceNo) {
        for (WorkOrderRequest.TaskItem task : readTasks(workOrder.getTaskDataJson())) {
            if (task.getSequenceNo() != null && task.getSequenceNo().equals(sequenceNo)
                    && !Boolean.FALSE.equals(task.getEnabled()) && StringUtils.hasText(task.getProcessName())) {
                return task;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "工序不存在或已停用");
    }

    private ReportStatus parseStatus(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return ReportStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime periodStart(String period) {
        if ("today".equalsIgnoreCase(normalize(period))) {
            return LocalDate.now().atStartOfDay();
        }
        if ("lastMonth".equalsIgnoreCase(normalize(period))) {
            return LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay();
        }
        return null;
    }

    private LocalDateTime periodEnd(String period) {
        return "lastMonth".equalsIgnoreCase(normalize(period)) ? LocalDate.now().withDayOfMonth(1).atStartOfDay() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<WorkOrderRequest.TaskItem> readTasks(String json) {
        if (!StringUtils.hasText(json)) return new java.util.ArrayList<>();
        try {
            List<WorkOrderRequest.TaskItem> tasks = objectMapper.readValue(json, new TypeReference<List<WorkOrderRequest.TaskItem>>() {});
            return tasks == null ? new ArrayList<>() : tasks.stream().filter(Objects::nonNull).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            return new java.util.ArrayList<>();
        }
    }

    private void validateImageSignature(MultipartFile file, String ext) {
        byte[] header = new byte[12];
        int length = 0;
        try (InputStream input = file.getInputStream()) {
            while (length < header.length) {
                int read = input.read(header, length, header.length - length);
                if (read < 0) break;
                if (read == 0) {
                    int next = input.read();
                    if (next < 0) break;
                    header[length++] = (byte) next;
                } else {
                    length += read;
                }
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片读取失败");
        }
        if (!hasImageSignature(ext, header, length)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片文件内容不合法");
        }
    }

    private boolean hasImageSignature(String ext, byte[] header, int length) {
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            return length >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A;
        }
        if ("webp".equals(ext)) {
            return length >= 12
                    && header[0] == 0x52
                    && header[1] == 0x49
                    && header[2] == 0x46
                    && header[3] == 0x46
                    && header[8] == 0x57
                    && header[9] == 0x45
                    && header[10] == 0x42
                    && header[11] == 0x50;
        }
        return false;
    }

    private boolean isAllowedImageMetadata(String ext, String contentType) {
        if (!isAllowedExt(ext)) {
            return false;
        }
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        return resolvedContentType(ext).equals(contentType);
    }

    private boolean isAllowedExt(String ext) {
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext);
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String extensionFromContentType(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return "jpg";
        }
        if ("image/png".equals(contentType)) {
            return "png";
        }
        if ("image/webp".equals(contentType)) {
            return "webp";
        }
        return "";
    }

    private String resolvedContentType(String ext) {
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return "image/jpeg";
        }
        if ("png".equals(ext)) {
            return "image/png";
        }
        if ("webp".equals(ext)) {
            return "image/webp";
        }
        return "";
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String value = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = value.indexOf(';');
        return semicolon >= 0 ? value.substring(0, semicolon).trim() : value;
    }

    private String sanitizeOriginalFileName(String fileName) {
        String value = StringUtils.hasText(fileName) ? fileName.trim() : "image";
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\r\\n\\t\\x00]", "_").trim();
        if (!StringUtils.hasText(value)) {
            value = "image";
        }
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private int nonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private int requireNonNegative(Integer value, String label) {
        if (value != null && value < 0) throw new ApiException(HttpStatus.BAD_REQUEST, label + "不能为负数");
        return value == null ? 0 : value;
    }

    private int addQuantity(Integer current, Integer increment) {
        try {
            return Math.addExact(nonNegative(current), nonNegative(increment));
        } catch (ArithmeticException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "累计报工数量超出系统支持范围");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeToEmpty(String value) {
        return normalize(value);
    }

    public static class ReportImageDownload {
        private final WorkOrderReportImage image;
        private final Resource resource;

        public ReportImageDownload(WorkOrderReportImage image, Resource resource) {
            this.image = image;
            this.resource = resource;
        }

        public WorkOrderReportImage getImage() {
            return image;
        }

        public Resource getResource() {
            return resource;
        }
    }
}
