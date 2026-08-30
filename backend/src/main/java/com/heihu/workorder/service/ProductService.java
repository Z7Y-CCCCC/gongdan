package com.heihu.workorder.service;

import com.heihu.workorder.dto.ProductOperationLogRequest;
import com.heihu.workorder.dto.ProductOperationLogResponse;
import com.heihu.workorder.dto.ProductRequest;
import com.heihu.workorder.dto.ProductResponse;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.ProcessRoute;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.model.ProductOperationLog;
import com.heihu.workorder.model.Unit;
import com.heihu.workorder.repository.BomRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.InboundReceiptRepository;
import com.heihu.workorder.repository.OutboundReceiptRepository;
import com.heihu.workorder.repository.ProcessRouteRepository;
import com.heihu.workorder.repository.ProductOperationLogRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.SalesOrderRepository;
import com.heihu.workorder.repository.UnitRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Transactional
public class ProductService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductRepository productRepository;
    private final ProductOperationLogRepository productOperationLogRepository;
    private final FactoryRepository factoryRepository;
    private final UnitRepository unitRepository;
    private final ProcessRouteRepository routeRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;
    private final InboundReceiptRepository inboundReceiptRepository;
    private final OutboundReceiptRepository outboundReceiptRepository;
    private final BomRepository bomRepository;
    private final SalesOrderRepository salesOrderRepository;

    public ProductService(ProductRepository productRepository,
                          ProductOperationLogRepository productOperationLogRepository,
                          FactoryRepository factoryRepository,
                          UnitRepository unitRepository,
                          ProcessRouteRepository routeRepository,
                          UserRepository userRepository,
                          WorkOrderRepository workOrderRepository,
                          InboundReceiptRepository inboundReceiptRepository,
                          OutboundReceiptRepository outboundReceiptRepository,
                          BomRepository bomRepository,
                          SalesOrderRepository salesOrderRepository) {
        this.productRepository = productRepository;
        this.productOperationLogRepository = productOperationLogRepository;
        this.factoryRepository = factoryRepository;
        this.unitRepository = unitRepository;
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.workOrderRepository = workOrderRepository;
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.outboundReceiptRepository = outboundReceiptRepository;
        this.bomRepository = bomRepository;
        this.salesOrderRepository = salesOrderRepository;
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCode(product.getCode());
        response.setName(product.getName());
        response.setUnitId(product.getUnit() != null ? product.getUnit().getId() : null);
        response.setUnitName(product.getUnit() != null ? product.getUnit().getName() : null);
        response.setSpec(product.getSpec());
        response.setAttribute(product.getAttribute());
        response.setRouteId(product.getProcessRoute() != null ? product.getProcessRoute().getId() : null);
        response.setRouteName(product.getProcessRoute() != null ? product.getProcessRoute().getName() : null);
        response.setSupplierName(product.getSupplierName());
        response.setMaxStock(product.getMaxStock());
        response.setMinStock(product.getMinStock());
        response.setSafetyStock(product.getSafetyStock());
        response.setInventoryQty(product.getInventoryQty());
        response.setCostPrice(product.getCostPrice());
        response.setSalePrice(product.getSalePrice());
        response.setKeyPart(product.getKeyPart());
        response.setManufactureMode(product.getManufactureMode());
        response.setQuantity(product.getQuantity());
        response.setFixedWeight(product.getFixedWeight());
        response.setCustomerName(product.getCustomerName());
        response.setProductType(product.getProductType());
        response.setCreator(product.getCreator() != null ? product.getCreator().getName() : null);
        response.setCreatedAt(product.getCreatedAt() != null ? product.getCreatedAt().format(FMT) : null);
        return response;
    }

    private ProductOperationLogResponse toLogResponse(ProductOperationLog log) {
        ProductOperationLogResponse response = new ProductOperationLogResponse();
        response.setId(log.getId());
        response.setActionType(log.getActionType());
        response.setActionLabel(actionLabel(log.getActionType()));
        response.setOperatorName(log.getOperatorName());
        response.setProductCode(log.getProductCode());
        response.setProductName(log.getProductName());
        response.setDetail(log.getDetail());
        response.setCreatedAt(log.getCreatedAt() != null ? log.getCreatedAt().format(FMT) : null);
        response.setMessage(buildLogMessage(log));
        return response;
    }

    @Cacheable(cacheNames = "products.list", key = "#factoryId + ':' + #code + ':' + #name + ':' + #spec + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<ProductResponse> list(Long factoryId, String code, String name, String spec, Pageable pageable) {
        boolean hasCode = code != null && !code.trim().isEmpty();
        boolean hasName = name != null && !name.trim().isEmpty();
        boolean hasSpec = spec != null && !spec.trim().isEmpty();
        if (!hasCode && !hasName && !hasSpec) {
            return productRepository.findByFactoryId(factoryId, pageable).map(this::toResponse);
        }
        return productRepository.findByFactoryIdAndCodeContainingAndNameContainingAndSpecContaining(
                factoryId,
                hasCode ? code.trim() : "",
                hasName ? name.trim() : "",
                hasSpec ? spec.trim() : "",
                pageable
        ).map(this::toResponse);
    }

    public Page<ProductOperationLogResponse> listOperationLogs(Long factoryId, Pageable pageable) {
        return productOperationLogRepository.findByFactoryId(factoryId, pageable).map(this::toLogResponse);
    }

    @CacheEvict(cacheNames = {"products.list", "boms.list", "sales.orders.list", "workorders.list", "workorders.detail", "workorders.code"}, allEntries = true)
    public ProductResponse create(Long factoryId, Long userId, String operatorName, ProductRequest request) {
        Product product = new Product();
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        product.setFactory(factory);
        product.setCreatedAt(LocalDateTime.now());
        applyRequest(product, factoryId, request, true);
        if (userId != null) {
            userRepository.findById(userId).ifPresent(product::setCreator);
        }
        Product saved = productRepository.save(product);
        writeLog(factory, operatorName, "CREATE", saved.getCode(), saved.getName(), null);
        return toResponse(saved);
    }

    @CacheEvict(cacheNames = {"products.list", "boms.list", "sales.orders.list", "workorders.list", "workorders.detail", "workorders.code"}, allEntries = true)
    public ProductResponse update(Long factoryId, Long userId, String operatorName, Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("产品不存在"));
        applyRequest(product, factoryId, request, false);
        Product saved = productRepository.save(product);
        writeLog(saved.getFactory(), operatorName, "UPDATE", saved.getCode(), saved.getName(), null);
        return toResponse(saved);
    }

    @CacheEvict(cacheNames = {"products.list", "boms.list", "sales.orders.list", "workorders.list", "workorders.detail", "workorders.code"}, allEntries = true)
    public void delete(Long factoryId, String operatorName, Long id) {
        Product product = productRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("产品不存在"));
        String productCode = product.getCode();
        String productName = product.getName();
        Factory factory = product.getFactory();
        ensureProductCanBeDeleted(factoryId, id);
        productRepository.delete(product);
        writeLog(factory, operatorName, "DELETE", productCode, productName, null);
    }

    public void recordOperation(Long factoryId, String operatorName, ProductOperationLogRequest request) {
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        String actionType = normalize(request.getActionType());
        if (actionType == null) {
            throw new IllegalArgumentException("操作类型不能为空");
        }
        writeLog(
                factory,
                operatorName,
                actionType.toUpperCase(),
                normalize(request.getProductCode()),
                normalize(request.getProductName()),
                normalize(request.getDetail())
        );
    }

    private void applyRequest(Product product, Long factoryId, ProductRequest request, boolean creating) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("产品名称不能为空");
        }
        if (request.getUnitId() == null) {
            throw new IllegalArgumentException("库存单位不能为空");
        }

        String nextCode = normalize(request.getCode());
        if (creating) {
            if (nextCode == null) {
                nextCode = UUID.randomUUID().toString().substring(0, 8);
            }
        } else if (nextCode == null) {
            nextCode = product.getCode();
        }

        final String codeToUse = nextCode;
        productRepository.findByFactoryIdAndCode(factoryId, codeToUse).ifPresent(found -> {
            if (creating || !found.getId().equals(product.getId())) {
                throw new IllegalArgumentException("产品编号已存在：" + codeToUse);
            }
        });

        Unit unit = unitRepository.findById(request.getUnitId())
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("库存单位不存在"));

        ProcessRoute route = null;
        if (request.getRouteId() != null) {
            route = routeRepository.findById(request.getRouteId())
                    .filter(item -> item.getFactory().getId().equals(factoryId))
                    .orElseThrow(() -> new IllegalArgumentException("工艺路线不存在"));
        }

        product.setCode(codeToUse);
        product.setName(request.getName().trim());
        product.setUnit(unit);
        product.setSpec(normalize(request.getSpec()));
        product.setAttribute(normalize(request.getAttribute()) != null ? request.getAttribute().trim() : "自制");
        product.setProcessRoute(route);
        product.setSupplierName(normalize(request.getSupplierName()));
        product.setMaxStock(normalize(request.getMaxStock()));
        product.setMinStock(normalize(request.getMinStock()));
        product.setSafetyStock(normalize(request.getSafetyStock()));
        product.setInventoryQty(request.getInventoryQty() != null ? request.getInventoryQty() : 0);
        product.setCostPrice(normalize(request.getCostPrice()));
        product.setSalePrice(normalize(request.getSalePrice()));
        product.setKeyPart(request.getKeyPart() != null ? request.getKeyPart() : Boolean.FALSE);
        product.setManufactureMode(normalize(request.getManufactureMode()));
        product.setQuantity(normalize(request.getQuantity()));
        product.setFixedWeight(normalize(request.getFixedWeight()));
        product.setCustomerName(normalize(request.getCustomerName()));
        product.setProductType(normalize(request.getProductType()));
    }

    private void writeLog(Factory factory,
                          String operatorName,
                          String actionType,
                          String productCode,
                          String productName,
                          String detail) {
        ProductOperationLog log = new ProductOperationLog();
        log.setFactory(factory);
        log.setOperatorName(normalize(operatorName) != null ? operatorName.trim() : "系统");
        log.setActionType(actionType);
        log.setProductCode(productCode);
        log.setProductName(productName);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        productOperationLogRepository.save(log);
    }

    private void ensureProductCanBeDeleted(Long factoryId, Long productId) {
        if (workOrderRepository.existsByFactoryIdAndProductId(factoryId, productId)) {
            throw new IllegalArgumentException("产品已被工单引用，不能删除");
        }
        if (inboundReceiptRepository.existsByFactoryIdAndProductId(factoryId, productId)) {
            throw new IllegalArgumentException("产品已被入库单引用，不能删除");
        }
        if (outboundReceiptRepository.existsByFactoryIdAndProductId(factoryId, productId)) {
            throw new IllegalArgumentException("产品已被出库单引用，不能删除");
        }
        if (bomRepository.existsByFactoryIdAndProductId(factoryId, productId)
                || bomRepository.existsItemByFactoryIdAndProductId(factoryId, productId)) {
            throw new IllegalArgumentException("产品已被BOM引用，不能删除");
        }
        if (salesOrderRepository.existsItemByFactoryIdAndProductId(factoryId, productId)) {
            throw new IllegalArgumentException("产品已被销售订单引用，不能删除");
        }
    }

    @Transactional(readOnly = true)
    public String exportProductsCsv(Long factoryId, String code, String name, String spec) {
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(
                0, Integer.MAX_VALUE, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );
        org.springframework.data.domain.Page<ProductResponse> page = list(factoryId, code, name, spec, pageRequest);
        StringBuilder sb = new StringBuilder();
        sb.append("库存数量,单位,产品名称,产品编号,产品规格,工艺路线\n");
        for (ProductResponse p : page.getContent()) {
            sb.append(p.getInventoryQty() != null ? p.getInventoryQty() : 0).append(',');
            sb.append(csvEscape(p.getUnitName())).append(',');
            sb.append(csvEscape(p.getName())).append(',');
            sb.append(csvEscape(p.getCode())).append(',');
            sb.append(csvEscape(p.getSpec())).append(',');
            sb.append(csvEscape(p.getRouteName())).append('\n');
        }
        return sb.toString();
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String actionLabel(String actionType) {
        if ("CREATE".equalsIgnoreCase(actionType)) return "创建";
        if ("UPDATE".equalsIgnoreCase(actionType)) return "编辑";
        if ("DELETE".equalsIgnoreCase(actionType)) return "删除";
        if ("PRINT".equalsIgnoreCase(actionType)) return "打印";
        if ("EXPORT".equalsIgnoreCase(actionType)) return "导出";
        if ("IMPORT".equalsIgnoreCase(actionType)) return "导入";
        if ("COPY".equalsIgnoreCase(actionType)) return "复制";
        return "操作";
    }

    private String buildLogMessage(ProductOperationLog log) {
        String label = actionLabel(log.getActionType());
        String detail = normalize(log.getDetail());
        if (detail != null) {
            return detail;
        }
        String productName = normalize(log.getProductName());
        if (productName != null) {
            String productCode = normalize(log.getProductCode());
            return productCode == null
                    ? label + "产品【" + productName + "】"
                    : label + "产品【" + productName + "】（" + productCode + "）";
        }
        return label + "产品数据";
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
