package com.heihu.workorder.service;

import com.heihu.workorder.dto.BomItemRequest;
import com.heihu.workorder.dto.BomItemResponse;
import com.heihu.workorder.dto.BomRequest;
import com.heihu.workorder.dto.BomResponse;
import com.heihu.workorder.model.BillOfMaterial;
import com.heihu.workorder.model.BillOfMaterialItem;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.repository.BomRepository;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class BomService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final BomRepository bomRepository;
    private final FactoryRepository factoryRepository;
    private final ProductRepository productRepository;

    public BomService(BomRepository bomRepository,
                      FactoryRepository factoryRepository,
                      ProductRepository productRepository) {
        this.bomRepository = bomRepository;
        this.factoryRepository = factoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "boms.list", key = "#factoryId + ':' + #code + ':' + #productName + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<BomResponse> list(Long factoryId, String code, String productName, Pageable pageable) {
        return bomRepository.search(
                factoryId,
                normalizeForQuery(code),
                normalizeForQuery(productName),
                pageable
        ).map(this::toResponse);
    }

    @CacheEvict(cacheNames = {"boms.list", "products.list"}, allEntries = true)
    public BomResponse create(Long factoryId, BomRequest request) {
        requireValidRequest(request);
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));
        Product product = requireProduct(factoryId, request.getProductId());
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = generateCode();
        }
        ensureUniqueCode(factoryId, code, null);
        ensureUniqueProduct(factoryId, product.getId(), null);

        LocalDateTime now = LocalDateTime.now();
        BillOfMaterial bom = new BillOfMaterial();
        bom.setFactory(factory);
        bom.setCode(code);
        bom.setProduct(product);
        bom.setOutputQuantity(normalizePositive(request.getOutputQuantity(), "产出数量"));
        bom.setRemark(normalizeText(request.getRemark()));
        bom.setEnabled(request.getEnabled() == null || request.getEnabled());
        bom.setCreatedAt(now);
        bom.setUpdatedAt(now);
        applyItems(bom, factoryId, product.getId(), request.getItems());
        return toResponse(bomRepository.save(bom));
    }

    @CacheEvict(cacheNames = {"boms.list", "products.list"}, allEntries = true)
    public BomResponse update(Long factoryId, Long id, BomRequest request) {
        requireValidRequest(request);
        BillOfMaterial bom = bomRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("BOM不存在"));
        Product product = requireProduct(factoryId, request.getProductId());
        String code = normalizeCode(request.getCode());
        if (!StringUtils.hasText(code)) {
            code = bom.getCode();
        }
        ensureUniqueCode(factoryId, code, id);
        ensureUniqueProduct(factoryId, product.getId(), id);

        bom.setCode(code);
        bom.setProduct(product);
        bom.setOutputQuantity(normalizePositive(request.getOutputQuantity(), "产出数量"));
        bom.setRemark(normalizeText(request.getRemark()));
        if (request.getEnabled() != null) {
            bom.setEnabled(request.getEnabled());
        }
        bom.setUpdatedAt(LocalDateTime.now());
        bom.getItems().clear();
        applyItems(bom, factoryId, product.getId(), request.getItems());
        return toResponse(bomRepository.save(bom));
    }

    @CacheEvict(cacheNames = {"boms.list", "products.list"}, allEntries = true)
    public void delete(Long factoryId, Long id) {
        BillOfMaterial bom = bomRepository.findById(id)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("BOM不存在"));
        bomRepository.delete(bom);
    }

    private void requireValidRequest(BomRequest request) {
        if (request == null || request.getProductId() == null) {
            throw new IllegalArgumentException("请选择成品");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("BOM明细不能为空");
        }
    }

    private void applyItems(BillOfMaterial bom, Long factoryId, Long headerProductId, List<BomItemRequest> requests) {
        Set<Long> productIds = new HashSet<>();
        int index = 1;
        for (BomItemRequest itemRequest : requests) {
            if (itemRequest == null || itemRequest.getProductId() == null) {
                throw new IllegalArgumentException("请选择子件");
            }
            if (itemRequest.getProductId().equals(headerProductId)) {
                throw new IllegalArgumentException("子件不能和成品相同");
            }
            if (!productIds.add(itemRequest.getProductId())) {
                throw new IllegalArgumentException("BOM明细不能重复选择同一产品");
            }
            Product product = requireProduct(factoryId, itemRequest.getProductId());
            BillOfMaterialItem item = new BillOfMaterialItem();
            item.setBom(bom);
            item.setProduct(product);
            item.setQuantity(normalizePositive(itemRequest.getQuantity(), "子件用量"));
            item.setScrapRate(normalizeScrapRate(itemRequest.getScrapRate()));
            item.setRemark(normalizeText(itemRequest.getRemark()));
            item.setSortOrder(index++);
            bom.getItems().add(item);
        }
    }

    private Product requireProduct(Long factoryId, Long productId) {
        return productRepository.findById(productId)
                .filter(item -> item.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("产品不存在"));
    }

    private void ensureUniqueCode(Long factoryId, String code, Long currentId) {
        bomRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("BOM编码已存在：" + code);
            }
        });
    }

    private void ensureUniqueProduct(Long factoryId, Long productId, Long currentId) {
        bomRepository.findByFactoryIdAndProductId(factoryId, productId).ifPresent(found -> {
            if (currentId == null || !found.getId().equals(currentId)) {
                throw new IllegalArgumentException("该成品已存在BOM");
            }
        });
    }

    private BomResponse toResponse(BillOfMaterial bom) {
        BomResponse response = new BomResponse();
        Product product = bom.getProduct();
        response.setId(bom.getId());
        response.setCode(bom.getCode());
        response.setProductId(product != null ? product.getId() : null);
        response.setProductCode(product != null ? product.getCode() : null);
        response.setProductName(product != null ? product.getName() : null);
        response.setProductSpec(product != null ? product.getSpec() : null);
        response.setUnitName(product != null && product.getUnit() != null ? product.getUnit().getName() : null);
        response.setOutputQuantity(bom.getOutputQuantity());
        response.setRemark(bom.getRemark());
        response.setEnabled(bom.isEnabled());
        response.setCreatedAt(bom.getCreatedAt() != null ? bom.getCreatedAt().format(FMT) : null);
        response.setUpdatedAt(bom.getUpdatedAt() != null ? bom.getUpdatedAt().format(FMT) : null);
        response.setItems(bom.getItems().stream().map(this::toItemResponse).collect(Collectors.toList()));
        return response;
    }

    private BomItemResponse toItemResponse(BillOfMaterialItem item) {
        BomItemResponse response = new BomItemResponse();
        Product product = item.getProduct();
        response.setId(item.getId());
        response.setProductId(product != null ? product.getId() : null);
        response.setProductCode(product != null ? product.getCode() : null);
        response.setProductName(product != null ? product.getName() : null);
        response.setProductSpec(product != null ? product.getSpec() : null);
        response.setUnitName(product != null && product.getUnit() != null ? product.getUnit().getName() : null);
        response.setQuantity(item.getQuantity());
        response.setScrapRate(item.getScrapRate());
        response.setRemark(item.getRemark());
        return response;
    }

    private BigDecimal normalizePositive(BigDecimal value, String label) {
        BigDecimal normalized = value == null ? BigDecimal.ONE : value;
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(label + "必须大于0");
        }
        return normalized;
    }

    private BigDecimal normalizeScrapRate(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0 || normalized.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("损耗率必须在0到100之间");
        }
        return normalized;
    }

    private String generateCode() {
        return "BOM-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
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
}
