package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.WorkOrderReportApprovalRequest;
import com.heihu.workorder.dto.WorkOrderReportImageResponse;
import com.heihu.workorder.dto.WorkOrderReportRequest;
import com.heihu.workorder.dto.WorkOrderReportResponse;
import com.heihu.workorder.dto.WorkOrderRequest;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class WorkOrderReportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkOrderReportRepository reportRepository;
    private final WorkOrderReportImageRepository imageRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;

    @Value("${app.upload.report-image-dir:uploads/report-images}")
    private String reportImageDir;

    public WorkOrderReportService(
            WorkOrderReportRepository reportRepository,
            WorkOrderReportImageRepository imageRepository,
            WorkOrderRepository workOrderRepository,
            UserRepository userRepository
    ) {
        this.reportRepository = reportRepository;
        this.imageRepository = imageRepository;
        this.workOrderRepository = workOrderRepository;
        this.userRepository = userRepository;
    }

    public WorkOrderReportImageResponse uploadImage(Long factoryId, Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择图片");
        }
        if (file.getSize() > 10L * 1024L * 1024L) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单张图片不能超过10MB");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "image";
        String ext = extension(originalName);
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "";
        if (!isAllowedImage(ext, contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持 jpg、jpeg、png、webp 图片");
        }
        FactoryUser uploader = requireUser(factoryId, userId);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path root = Paths.get(reportImageDir).toAbsolutePath().normalize();
        Path target = root.resolve(storedName).normalize();
        try {
            Files.createDirectories(root);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
        }

        WorkOrderReportImage image = new WorkOrderReportImage();
        image.setFactory(uploader.getFactory());
        image.setUploader(uploader);
        image.setOriginalFileName(originalName);
        image.setStoredFileName(storedName);
        image.setAccessPath("/uploads/report-images/" + storedName);
        image.setFileSize(file.getSize());
        image.setContentType(StringUtils.hasText(contentType) ? contentType : "image/" + ext);
        image.setCreatedAt(LocalDateTime.now());
        return WorkOrderReportImageResponse.from(imageRepository.save(image));
    }

    public WorkOrderReportResponse createReport(Long factoryId, Long userId, WorkOrderReportRequest request) {
        FactoryUser reporter = requireUser(factoryId, userId);
        if (request.getWorkOrderId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择工单");
        }
        if (request.getProcessSequenceNo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择工序");
        }
        int reportQty = nonNegative(request.getReportQuantity());
        int goodQty = nonNegative(request.getGoodQuantity());
        int defectQty = nonNegative(request.getDefectQuantity());
        if (reportQty <= 0 && goodQty <= 0 && defectQty <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请填写报工数量");
        }
        if (request.getImageIds() != null && request.getImageIds().size() > 9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单次报工最多上传9张图片");
        }
        WorkOrder workOrder = workOrderRepository.findById(request.getWorkOrderId())
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "工单不存在"));

        WorkOrderReport report = new WorkOrderReport();
        LocalDateTime now = LocalDateTime.now();
        report.setFactory(workOrder.getFactory());
        report.setWorkOrder(workOrder);
        report.setWorkOrderCode(workOrder.getCode());
        report.setProductCode(workOrder.getProductCode());
        report.setProductName(workOrder.getProductName());
        report.setProductSpec(workOrder.getProductSpec());
        report.setProcessSequenceNo(request.getProcessSequenceNo());
        report.setProcessName(resolveProcessName(workOrder, request));
        report.setReporter(reporter);
        report.setReporterName(reporter.getName());
        report.setReportQuantity(reportQty);
        report.setGoodQuantity(goodQty);
        report.setDefectQuantity(defectQty);
        report.setDefectItemName(normalize(request.getDefectItemName()));
        report.setReportDurationMinutes(nonNegative(request.getReportDurationMinutes()));
        report.setStartTime(parseDateTime(request.getStartTime()));
        report.setEndTime(parseDateTime(request.getEndTime()));
        report.setMaterialName(normalize(request.getMaterialName()));
        report.setRemark(normalize(request.getRemark()));
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        WorkOrderReport saved = reportRepository.save(report);
        attachImages(factoryId, userId, saved, request.getImageIds());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderReportResponse> listAdmin(
            Long factoryId,
            String status,
            String workOrderCode,
            String reporterName,
            String period,
            Pageable pageable
    ) {
        return reportRepository.search(
                factoryId,
                parseStatus(status),
                normalizeToEmpty(workOrderCode),
                normalizeToEmpty(reporterName),
                periodStart(period),
                pageable
        ).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderReportResponse> listMobile(Long factoryId, Long userId, String status, String period, Pageable pageable) {
        return reportRepository.searchMobile(factoryId, userId, parseStatus(status), periodStart(period), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkOrderReportResponse get(Long factoryId, Long reportId) {
        WorkOrderReport report = reportRepository.findById(reportId)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报工记录不存在"));
        return toResponse(report);
    }

    public WorkOrderReportResponse approve(Long factoryId, Long approverId, Long reportId, WorkOrderReportApprovalRequest request) {
        WorkOrderReport report = requirePending(factoryId, reportId);
        FactoryUser approver = requireUser(factoryId, approverId);
        LocalDateTime now = LocalDateTime.now();
        report.setStatus(ReportStatus.APPROVED);
        report.setApprover(approver);
        report.setApproverName(approver.getName());
        report.setApprovedAt(now);
        report.setApprovalRemark(normalize(request == null ? null : request.getRemark()));
        report.setUpdatedAt(now);
        applyApprovedReport(report, now);
        return toResponse(reportRepository.save(report));
    }

    public WorkOrderReportResponse reject(Long factoryId, Long approverId, Long reportId, WorkOrderReportApprovalRequest request) {
        WorkOrderReport report = requirePending(factoryId, reportId);
        FactoryUser approver = requireUser(factoryId, approverId);
        LocalDateTime now = LocalDateTime.now();
        report.setStatus(ReportStatus.REJECTED);
        report.setApprover(approver);
        report.setApproverName(approver.getName());
        report.setApprovedAt(now);
        report.setApprovalRemark(normalize(request == null ? null : request.getRemark()));
        report.setUpdatedAt(now);
        return toResponse(reportRepository.save(report));
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
        WorkOrder workOrder = report.getWorkOrder();
        List<WorkOrderRequest.TaskItem> tasks = readTasks(workOrder.getTaskDataJson());
        boolean matched = false;
        for (WorkOrderRequest.TaskItem task : tasks) {
            if (task.getSequenceNo() != null && task.getSequenceNo().equals(report.getProcessSequenceNo())) {
                task.setCompletedQuantity(nonNegative(task.getCompletedQuantity()) + nonNegative(report.getGoodQuantity()));
                task.setApprovedQuantity(nonNegative(task.getApprovedQuantity()) + nonNegative(report.getGoodQuantity()));
                task.setDefectQuantity(nonNegative(task.getDefectQuantity()) + nonNegative(report.getDefectQuantity()));
                task.setLastReportTime(now.format(FMT));
                matched = true;
                break;
            }
        }
        if (!matched) {
            WorkOrderRequest.TaskItem task = new WorkOrderRequest.TaskItem();
            task.setSequenceNo(report.getProcessSequenceNo());
            task.setProcessName(report.getProcessName());
            task.setPlannedQuantity(workOrder.getPlannedQuantity());
            task.setCompletedQuantity(nonNegative(report.getGoodQuantity()));
            task.setApprovedQuantity(nonNegative(report.getGoodQuantity()));
            task.setDefectQuantity(nonNegative(report.getDefectQuantity()));
            task.setEnabled(true);
            task.setLastReportTime(now.format(FMT));
            tasks.add(task);
        }
        workOrder.setTaskDataJson(writeJson(tasks));
        workOrder.setGoodQuantity(nonNegative(workOrder.getGoodQuantity()) + nonNegative(report.getGoodQuantity()));
        workOrder.setDefectQuantity(nonNegative(workOrder.getDefectQuantity()) + nonNegative(report.getDefectQuantity()));
        int total = nonNegative(workOrder.getGoodQuantity()) + nonNegative(workOrder.getDefectQuantity());
        if (total > 0) {
            workOrder.setDefectRate(String.format(Locale.US, "%.2f%%", workOrder.getDefectQuantity() * 100.0 / total));
        }
        workOrder.setUpdatedAt(now);
        workOrderRepository.save(workOrder);
    }

    private WorkOrderReport requirePending(Long factoryId, Long reportId) {
        WorkOrderReport report = reportRepository.findById(reportId)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报工记录不存在"));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只能审批未审批的报工记录");
        }
        return report;
    }

    private FactoryUser requireUser(Long factoryId, Long userId) {
        return userRepository.findByIdAndFactoryId(userId, factoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    private WorkOrderReportResponse toResponse(WorkOrderReport report) {
        List<WorkOrderReportImageResponse> images = imageRepository.findByReportIdOrderByCreatedAtAsc(report.getId())
                .stream()
                .map(WorkOrderReportImageResponse::from)
                .collect(Collectors.toList());
        return WorkOrderReportResponse.from(report, images);
    }

    private String resolveProcessName(WorkOrder workOrder, WorkOrderReportRequest request) {
        if (StringUtils.hasText(request.getProcessName())) {
            return request.getProcessName().trim();
        }
        for (WorkOrderRequest.TaskItem task : readTasks(workOrder.getTaskDataJson())) {
            if (task.getSequenceNo() != null && task.getSequenceNo().equals(request.getProcessSequenceNo())) {
                return task.getProcessName();
            }
        }
        return "工序" + request.getProcessSequenceNo();
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

    private LocalDateTime parseDateTime(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) return null;
        try {
            return LocalDateTime.parse(normalized.replace(" ", "T"));
        } catch (Exception ex) {
            try {
                return LocalDateTime.parse(normalized, FMT);
            } catch (Exception ignore) {
                return null;
            }
        }
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
            return objectMapper.readValue(json, new TypeReference<List<WorkOrderRequest.TaskItem>>() {});
        } catch (JsonProcessingException e) {
            return new java.util.ArrayList<>();
        }
    }

    private boolean isAllowedImage(String ext, String contentType) {
        boolean extOk = "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext);
        boolean typeOk = contentType == null || contentType.isEmpty() || contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        return extOk && typeOk;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "jpg";
    }

    private int nonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeToEmpty(String value) {
        return normalize(value);
    }
}
