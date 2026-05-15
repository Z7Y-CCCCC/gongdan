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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
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

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            ProductRepository productRepository,
                            UserRepository userRepository,
                            WorkOrderCategoryRepository categoryRepository) {
        this.workOrderRepository = workOrderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

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

    public WorkOrderResponse update(Long factoryId, Long id, WorkOrderRequest request) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        workOrder.setUpdatedAt(LocalDateTime.now());
        applyRequest(factoryId, workOrder, request, false);
        return toResponse(workOrderRepository.save(workOrder));
    }

    public void delete(Long factoryId, Long id) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        workOrderRepository.delete(workOrder);
    }

    private void applyRequest(Long factoryId, WorkOrder workOrder, WorkOrderRequest request, boolean creating) {
        if (request.getProductId() == null) {
            throw new IllegalArgumentException("产品不能为空");
        }
        if (request.getPlannedQuantity() == null || request.getPlannedQuantity() <= 0) {
            throw new IllegalArgumentException("计划数必须大于 0");
        }

        Product product = productRepository.findById(request.getProductId())
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("产品不存在"));

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
            categoryRepository.findById(request.getCategoryId())
                .filter(c -> c.getFactory().getId().equals(factoryId))
                .ifPresent(workOrder::setCategory);
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
        workOrder.setStatus(normalize(request.getStatus()) != null ? request.getStatus().trim() : "NOT_STARTED");
        workOrder.setPriority(normalize(request.getPriority()) != null ? request.getPriority().trim() : "NORMAL");
        workOrder.setPlanStart(parseDateTime(request.getPlanStart()));
        workOrder.setPlanEnd(parseDateTime(request.getPlanEnd()));
        workOrder.setRelatedOrderCode(normalize(request.getRelatedOrderCode()));
        workOrder.setRemark(normalize(request.getRemark()));
        workOrder.setCustomDataJson(writeJson(request.getCustomValues()));
        workOrder.setTaskDataJson(writeJson(request.getTasks()));
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
        response.setProgressPercent(calculateProgress(readTasks(item.getTaskDataJson())));
        response.setPlanStart(item.getPlanStart() != null ? item.getPlanStart().format(FMT) : null);
        response.setPlanEnd(item.getPlanEnd() != null ? item.getPlanEnd().format(FMT) : null);
        response.setRelatedOrderCode(item.getRelatedOrderCode());
        response.setRemark(item.getRemark());
        response.setCustomValues(readCustomValues(item.getCustomDataJson()));
        response.setTasks(readTasks(item.getTaskDataJson()));
        response.setMaterials(readMaterials(item.getMaterialDataJson()));
        response.setCreator(item.getCreator() != null ? item.getCreator().getName() : null);
        response.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(FMT) : null);
        return response;
    }

    private Integer calculateProgress(List<WorkOrderRequest.TaskItem> tasks) {
        if (tasks.isEmpty()) return 0;
        long enabledCount = tasks.stream().filter(task -> task.getEnabled() == null || task.getEnabled()).count();
        if (enabledCount == 0) return 0;
        return (int) Math.round((enabledCount * 100.0) / tasks.size());
    }

    private LocalDateTime parseDateTime(String value) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        try {
            return LocalDateTime.parse(normalized.replace(" ", "T"));
        } catch (Exception ignore) {
            try {
                return LocalDateTime.parse(normalized, FMT);
            } catch (Exception ex) {
                return null;
            }
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
