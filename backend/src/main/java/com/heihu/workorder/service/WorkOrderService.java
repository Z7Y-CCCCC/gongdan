package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.WorkOrderRequest;
import com.heihu.workorder.dto.WorkOrderResponse;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import com.heihu.workorder.repository.WorkOrderCategoryRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.util.DateTimeParser;
import com.heihu.workorder.util.WorkOrderStates;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class WorkOrderService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkOrderRepository workOrderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WorkOrderCategoryRepository categoryRepository;
    private final WorkOrderReportRepository reportRepository;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            ProductRepository productRepository,
                            UserRepository userRepository,
                            WorkOrderCategoryRepository categoryRepository,
                            WorkOrderReportRepository reportRepository) {
        this.workOrderRepository = workOrderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.reportRepository = reportRepository;
    }

    @Cacheable(cacheNames = "workorders.list", key = "#factoryId + ':' + #code + ':' + #productName + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<WorkOrderResponse> list(Long factoryId, String code, String productName, Pageable pageable) {
        boolean hasCode = code != null && !code.trim().isEmpty();
        boolean hasProductName = productName != null && !productName.trim().isEmpty();
        if (!hasCode && !hasProductName) {
            return workOrderRepository.findByFactoryId(factoryId, pageable).map(this::toResponse);
        }
        return workOrderRepository.findByFactoryIdAndCodeContainingAndProductNameContaining(
                factoryId,
                hasCode ? code.trim() : "",
                hasProductName ? productName.trim() : "",
                pageable
        ).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "workorders.detail", key = "#factoryId + ':' + #id")
    public WorkOrderResponse get(Long factoryId, Long id) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        return toResponse(workOrder);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "workorders.code", key = "#factoryId + ':' + #code")
    public WorkOrderResponse getByCode(Long factoryId, String code) {
        WorkOrder workOrder = workOrderRepository.findByFactoryIdAndCode(factoryId, code)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在：" + code));
        return toResponse(workOrder);
    }

    @CacheEvict(cacheNames = {"workorders.list", "workorders.detail", "workorders.code"}, allEntries = true)
    public WorkOrderResponse create(Long factoryId, Long userId, WorkOrderRequest request) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setCreatedAt(LocalDateTime.now());
        workOrder.setUpdatedAt(LocalDateTime.now());
        applyRequest(factoryId, workOrder, request, true);
        if (userId != null) {
            userRepository.findById(userId).ifPresent(workOrder::setCreator);
        }
        return toResponse(workOrderRepository.save(workOrder));
    }

    @CacheEvict(cacheNames = {"workorders.list", "workorders.detail", "workorders.code"}, allEntries = true)
    public WorkOrderResponse update(Long factoryId, Long id, WorkOrderRequest request) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        if (request == null) {
            throw new IllegalArgumentException("请填写工单信息");
        }
        if (request.getVersion() != null && !request.getVersion().equals(workOrder.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(WorkOrder.class, id);
        }
        workOrder.setUpdatedAt(LocalDateTime.now());
        applyRequest(factoryId, workOrder, request, false);
        return toResponse(workOrderRepository.saveAndFlush(workOrder));
    }

    @CacheEvict(cacheNames = {"workorders.list", "workorders.detail", "workorders.code"}, allEntries = true)
    public void delete(Long factoryId, Long id) {
        WorkOrder workOrder = workOrderRepository.findByIdAndFactoryIdForUpdate(id, factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        if (reportRepository.existsByFactoryIdAndWorkOrderId(factoryId, id)) {
            throw new IllegalArgumentException("工单已有报工记录，不能删除");
        }
        workOrderRepository.delete(workOrder);
    }

    private void applyRequest(Long factoryId, WorkOrder workOrder, WorkOrderRequest request, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("请填写工单信息");
        }
        if (request.getProductId() == null) {
            throw new IllegalArgumentException("产品不能为空");
        }
        if (request.getPlannedQuantity() == null || request.getPlannedQuantity() <= 0) {
            throw new IllegalArgumentException("计划数必须大于 0");
        }

        Product product = productRepository.findById(request.getProductId())
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("产品不存在"));

        requireNonNegative(request.getGoodQuantity(), "良品数");
        requireNonNegative(request.getDefectQuantity(), "不良品数");
        validateTasks(request.getTasks());
        LocalDateTime planStart = DateTimeParser.parse(request.getPlanStart());
        LocalDateTime planEnd = DateTimeParser.parse(request.getPlanEnd());
        if (planStart != null && planEnd != null && planEnd.isBefore(planStart)) {
            throw new IllegalArgumentException("计划结束时间不能早于开始时间");
        }

        String nextCode = normalize(request.getCode());
        if (creating && nextCode == null) {
            nextCode = "GD" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        } else if (!creating && nextCode == null) {
            nextCode = workOrder.getCode();
        }

        final String codeToUse = nextCode;
        workOrderRepository.findByFactoryIdAndCode(factoryId, codeToUse).ifPresent(found -> {
            if (creating || !found.getId().equals(workOrder.getId())) {
                throw new IllegalArgumentException("工单编号已存在：" + codeToUse);
            }
        });

        workOrder.setFactory(product.getFactory());
        workOrder.setCode(codeToUse);
        
        if (request.getCategoryId() != null) {
            workOrder.setCategory(categoryRepository.findById(request.getCategoryId())
                .filter(c -> c.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工单分类不存在")));
        } else {
            workOrder.setCategory(null);
        }

        workOrder.setProduct(product);
        workOrder.setProductCode(product.getCode());
        workOrder.setProductName(product.getName());
        workOrder.setProductSpec(product.getSpec());
        workOrder.setPlannedQuantity(request.getPlannedQuantity());
        workOrder.setGoodQuantity(request.getGoodQuantity() != null ? request.getGoodQuantity() : 0);
        workOrder.setDefectQuantity(request.getDefectQuantity() != null ? request.getDefectQuantity() : 0);
        workOrder.setDefectRate(normalize(request.getDefectRate()));

        String previousStatus = creating ? null : workOrder.getStatus();
        String nextStatus = normalize(request.getStatus()) != null ? request.getStatus().trim() : "NOT_STARTED";
        if (!WorkOrderStates.isKnown(nextStatus)) {
            throw new IllegalArgumentException("无效的工单状态");
        }
        workOrder.setStatus(nextStatus);

        if (!WorkOrderStates.isFinished(previousStatus) && WorkOrderStates.isFinished(nextStatus)) {
            workOrder.setCompletedAt(LocalDateTime.now());
        }
        if (!WorkOrderStates.isFinished(nextStatus)) {
            workOrder.setCompletedAt(null);
        }

        workOrder.setPriority(normalize(request.getPriority()) != null ? request.getPriority().trim() : "NORMAL");
        workOrder.setPlanStart(planStart);
        workOrder.setPlanEnd(planEnd);
        workOrder.setRelatedOrderCode(normalize(request.getRelatedOrderCode()));
        workOrder.setRemark(normalize(request.getRemark()));
        workOrder.setCustomDataJson(writeJson(request.getCustomValues()));
        workOrder.setTaskDataJson(writeJson(normalizeTasks(request.getTasks())));
        workOrder.setMaterialDataJson(writeJson(request.getMaterials()));
    }

    private WorkOrderResponse toResponse(WorkOrder item) {
        WorkOrderResponse response = new WorkOrderResponse();
        response.setId(item.getId());
        response.setCode(item.getCode());
        response.setProductId(item.getProduct() != null ? item.getProduct().getId() : null);
        response.setProductCode(item.getProductCode());
        response.setProductName(item.getProductName());
        response.setProductSpec(item.getProductSpec());
        response.setPlannedQuantity(item.getPlannedQuantity());
        response.setGoodQuantity(item.getGoodQuantity());
        response.setDefectQuantity(item.getDefectQuantity());
        response.setDefectRate(item.getDefectRate());
        
        if (item.getCategory() != null) {
            response.setCategoryId(item.getCategory().getId());
            response.setCategoryName(item.getCategory().getName());
        }

        response.setStatus(item.getStatus());
        response.setPriority(item.getPriority());
        response.setRouteName(
                item.getProduct() != null && item.getProduct().getProcessRoute() != null
                        ? item.getProduct().getProcessRoute().getName()
                        : null
        );
        List<WorkOrderRequest.TaskItem> tasks = normalizeTasks(readTasks(item.getTaskDataJson()));
        response.setProgressPercent(calculateProgress(tasks));
        response.setPlanStart(item.getPlanStart() != null ? item.getPlanStart().format(FMT) : null);
        response.setPlanEnd(item.getPlanEnd() != null ? item.getPlanEnd().format(FMT) : null);
        response.setRelatedOrderCode(item.getRelatedOrderCode());
        response.setRemark(item.getRemark());
        response.setCustomValues(readCustomValues(item.getCustomDataJson()));
        response.setTasks(tasks);
        response.setMaterials(readMaterials(item.getMaterialDataJson()));
        response.setCreator(item.getCreator() != null ? item.getCreator().getName() : null);
        response.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(FMT) : null);
        response.setVersion(item.getVersion());
        return response;
    }

    private Integer calculateProgress(List<WorkOrderRequest.TaskItem> tasks) {
        if (tasks.isEmpty()) return 0;
        long planned = 0;
        long completed = 0;
        for (WorkOrderRequest.TaskItem task : tasks) {
            if (task.getEnabled() != null && !task.getEnabled()) {
                continue;
            }
            planned += task.getPlannedQuantity() == null ? 0 : Math.max(task.getPlannedQuantity(), 0);
            completed += task.getCompletedQuantity() == null ? 0 : Math.max(task.getCompletedQuantity(), 0);
        }
        if (planned <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(completed * 100.0 / planned));
    }

    private void validateTasks(List<WorkOrderRequest.TaskItem> tasks) {
        if (tasks == null) return;
        Set<Integer> sequences = new HashSet<>();
        for (WorkOrderRequest.TaskItem task : tasks) {
            if (task == null || task.getSequenceNo() == null || task.getSequenceNo() <= 0
                    || !sequences.add(task.getSequenceNo()) || normalize(task.getProcessName()) == null) {
                throw new IllegalArgumentException("工序不能为空，序号必须为不重复的正整数且工序名称必填");
            }
            requireNonNegative(task.getPlannedQuantity(), "工序计划数");
            requireNonNegative(task.getCompletedQuantity(), "工序完成数");
            requireNonNegative(task.getDefectQuantity(), "工序不良数");
            requireNonNegative(task.getApprovedQuantity(), "工序已审批数");
            requireNonNegative(task.getUnapprovedQuantity(), "工序待审批数");
        }
    }

    private void requireNonNegative(Integer quantity, String label) {
        if (quantity != null && quantity < 0) {
            throw new IllegalArgumentException(label + "不能为负数");
        }
    }

    private String writeJson(Object value) {
        if (value == null) return "[]";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<WorkOrderRequest.CustomValueItem> readCustomValues(String json) {
        if (normalize(json) == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkOrderRequest.CustomValueItem>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private List<WorkOrderRequest.TaskItem> readTasks(String json) {
        if (normalize(json) == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkOrderRequest.TaskItem>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private List<WorkOrderRequest.TaskItem> normalizeTasks(List<WorkOrderRequest.TaskItem> tasks) {
        if (tasks == null) return Collections.emptyList();
        List<WorkOrderRequest.TaskItem> result = new ArrayList<>();
        for (WorkOrderRequest.TaskItem task : tasks) {
            if (task == null) continue;
            String permission = normalize(task.getReportPermission());
            if (permission != null && "all".equalsIgnoreCase(permission)) {
                task.setReportPermission("所有人");
            }
            result.add(task);
        }
        return result;
    }

    private List<WorkOrderRequest.MaterialItem> readMaterials(String json) {
        if (normalize(json) == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkOrderRequest.MaterialItem>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
