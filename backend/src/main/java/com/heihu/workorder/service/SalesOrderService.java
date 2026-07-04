package com.heihu.workorder.service;

import com.heihu.workorder.dto.SalesOrderItemRequest;
import com.heihu.workorder.dto.SalesOrderItemResponse;
import com.heihu.workorder.dto.SalesOrderRequest;
import com.heihu.workorder.dto.SalesOrderResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.SalesOrder;
import com.heihu.workorder.model.SalesOrderItem;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.SalesOrderRepository;
import com.heihu.workorder.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class SalesOrderService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED_STATUS = new HashSet<>(
            Arrays.asList("DRAFT", "CONFIRMED", "FINISHED", "CANCELLED")
    );

    private final SalesOrderRepository salesOrderRepository;
    private final FactoryRepository factoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                             FactoryRepository factoryRepository,
                             ProductRepository productRepository,
                             UserRepository userRepository) {
        this.salesOrderRepository = salesOrderRepository;
        this.factoryRepository = factoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> list(Long factoryId, String code, String customerName, String status, Pageable pageable) {
        return salesOrderRepository.search(
                factoryId,
                normalizeForQuery(code),
                normalizeForQuery(customerName),
                normalizeStatusForQuery(status),
                pageable
        ).map(this::toResponse);
    }

    public SalesOrderResponse create(Long factoryId, Long userId, SalesOrderRequest request) {
        requireValidRequest(request);
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = generateCode();
        }
        ensureUniqueCode(factoryId, code, null);

        LocalDateTime now = LocalDateTime.now();
        SalesOrder order = new SalesOrder();
        order.setFactory(factory);
        order.setCode(code);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        if (userId != null) {
            userRepository.findById(userId).ifPresent(order::setCreator);
        }
        applyRequest(order, factoryId, request);
        return toResponse(salesOrderRepository.save(order));
    }

    public SalesOrderResponse update(Long factoryId, Long id, SalesOrderRequest request) {
        requireValidRequest(request);
        SalesOrder order = salesOrderRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = order.getCode();
        }
        ensureUniqueCode(factoryId, code, id);
        order.setCode(code);
        order.setUpdatedAt(LocalDateTime.now());
        order.getItems().clear();
        applyRequest(order, factoryId, request);
        return toResponse(salesOrderRepository.save(order));
    }

    public void delete(Long factoryId, Long id) {
        SalesOrder order = salesOrderRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        salesOrderRepository.delete(order);
    }

    private void requireValidRequest(SalesOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getCustomerName())) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("销售订单明细不能为空");
        }
    }

    private void applyRequest(SalesOrder order, Long factoryId, SalesOrderRequest request) {
        order.setCustomerName(request.getCustomerName().trim());
        order.setOrderDate(parseDate(request.getOrderDate()));
        order.setDeliveryDate(parseDate(request.getDeliveryDate()));
        order.setStatus(normalizeStatus(request.getStatus()));
        order.setRemark(normalizeText(request.getRemark()));
        applyItems(order, factoryId, request.getItems());
    }

    private void applyItems(SalesOrder order, Long factoryId, List<SalesOrderItemRequest> requests) {
        Set<Long> productIds = new HashSet<>();
        int index = 1;
        for (SalesOrderItemRequest itemRequest : requests) {
            if (itemRequest == null || itemRequest.getProductId() == null) {
                throw new IllegalArgumentException("请选择产品");
            }
            if (!productIds.add(itemRequest.getProductId())) {
                throw new IllegalArgumentException("销售订单明细不能重复选择同一产品");
            }
            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new IllegalArgumentException("订单数量必须大于0");
            }
            Product product = productRepository.findById(itemRequest.getProductId())
                    .filter(item -> item.getFactory().getId().equals(factoryId))
                    .orElseThrow(() -> new IllegalArgumentException("产品不存在"));
            SalesOrderItem item = new SalesOrderItem();
            item.setSalesOrder(order);
            item.setProduct(product);
            item.setQuantity(itemRequest.getQuantity());
            item.setRemark(normalizeText(itemRequest.getRemark()));
            item.setSortOrder(index++);
            order.getItems().add(item);
        }
    }

    private SalesOrderResponse toResponse(SalesOrder order) {
        SalesOrderResponse response = new SalesOrderResponse();
        response.setId(order.getId());
        response.setCode(order.getCode());
        response.setCustomerName(order.getCustomerName());
        response.setOrderDate(order.getOrderDate() != null ? order.getOrderDate().toString() : null);
        response.setDeliveryDate(order.getDeliveryDate() != null ? order.getDeliveryDate().toString() : null);
        response.setStatus(order.getStatus());
        response.setStatusLabel(statusLabel(order.getStatus()));
        response.setRemark(order.getRemark());
        response.setCreator(order.getCreator() != null ? order.getCreator().getName() : null);
        response.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().format(FMT) : null);
        response.setItems(order.getItems().stream().map(this::toItemResponse).collect(Collectors.toList()));
        response.setTotalQuantity(response.getItems().stream()
                .map(SalesOrderItemResponse::getQuantity)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum());
        return response;
    }

    private SalesOrderItemResponse toItemResponse(SalesOrderItem item) {
        SalesOrderItemResponse response = new SalesOrderItemResponse();
        Product product = item.getProduct();
        response.setId(item.getId());
        response.setProductId(product != null ? product.getId() : null);
        response.setProductCode(product != null ? product.getCode() : null);
        response.setProductName(product != null ? product.getName() : null);
        response.setProductSpec(product != null ? product.getSpec() : null);
        response.setUnitName(product != null && product.getUnit() != null ? product.getUnit().getName() : null);
        response.setQuantity(item.getQuantity());
        response.setRemark(item.getRemark());
        return response;
    }

    private void ensureUniqueCode(Long factoryId, String code, Long currentId) {
        salesOrderRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("销售订单编码已存在：" + code);
            }
        });
    }

    private String generateCode() {
        return "SO-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    private LocalDate parseDate(String value) {
        return StringUtils.hasText(value) ? LocalDate.parse(value.trim()) : null;
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeForQuery(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeStatusForQuery(String value) {
        return StringUtils.hasText(value) ? normalizeStatus(value) : "";
    }

    private String normalizeStatus(String value) {
        String status = StringUtils.hasText(value) ? value.trim().toUpperCase() : "DRAFT";
        if (!ALLOWED_STATUS.contains(status)) {
            throw new IllegalArgumentException("销售订单状态不正确");
        }
        return status;
    }

    private String statusLabel(String status) {
        if ("CONFIRMED".equals(status)) return "已确认";
        if ("FINISHED".equals(status)) return "已完成";
        if ("CANCELLED".equals(status)) return "已取消";
        return "草稿";
    }
}
