package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.InboundReceiptRequest;
import com.heihu.workorder.dto.InboundReceiptResponse;
import com.heihu.workorder.dto.InboundReceiptResponse.InboundReceiptItemResponse;
import com.heihu.workorder.model.*;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.InboundReceiptRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.util.DateTimeParser;
import com.heihu.workorder.util.InventoryQuantities;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class InboundReceiptService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InboundReceiptRepository inboundReceiptRepository;
    private final FactoryRepository factoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public InboundReceiptService(InboundReceiptRepository inboundReceiptRepository,
                                 FactoryRepository factoryRepository,
                                 ProductRepository productRepository,
                                 UserRepository userRepository) {
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.factoryRepository = factoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Cacheable(cacheNames = "inbound.receipts.list", key = "#factoryId + ':' + #code + ':' + #inboundType + ':' + #startTime + ':' + #endTime + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<InboundReceiptResponse> list(Long factoryId, String code, String inboundType,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              Pageable pageable) {
        boolean hasFilter = (code != null && !code.trim().isEmpty())
                || (inboundType != null && !inboundType.trim().isEmpty())
                || startTime != null || endTime != null;

        if (!hasFilter) {
            return inboundReceiptRepository.findByFactoryId(factoryId, pageable).map(this::toResponse);
        }

        return inboundReceiptRepository.search(
                factoryId,
                normalize(code),
                normalize(inboundType),
                startTime,
                endTime,
                pageable
        ).map(this::toResponse);
    }

    @Cacheable(cacheNames = "inbound.receipts.detail", key = "#factoryId + ':' + #id")
    public InboundReceiptResponse getById(Long factoryId, Long id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .filter(r -> r.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("入库单不存在"));
        return toResponse(receipt);
    }

    @CacheEvict(cacheNames = {"products.list", "inventory.details", "inbound.receipts.list", "inbound.receipts.detail", "outbound.receipts.list", "outbound.receipts.detail"}, allEntries = true)
    public InboundReceiptResponse create(Long factoryId, Long userId, InboundReceiptRequest request) {
        if (request == null) throw new IllegalArgumentException("请填写入库单信息");
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));

        if (request.getInboundType() == null || request.getInboundType().trim().isEmpty()) {
            throw new IllegalArgumentException("入库类型不能为空");
        }
        if (request.getInboundTime() == null || request.getInboundTime().trim().isEmpty()) {
            throw new IllegalArgumentException("入库时间不能为空");
        }

        String code = normalize(request.getCode());
        if (code == null) {
            code = generateCode(factoryId);
        } else {
            inboundReceiptRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(existing -> {
                throw new IllegalArgumentException("入库单号已存在：" + existing.getCode());
            });
        }

        InboundReceipt receipt = new InboundReceipt();
        receipt.setFactory(factory);
        receipt.setCode(code);
        receipt.setInboundType(request.getInboundType().trim());
        receipt.setInboundTime(DateTimeParser.parse(request.getInboundTime()));
        receipt.setRemark(normalize(request.getRemark()));
        receipt.setCustomDataJson(writeJson(request.getCustomValues()));
        receipt.setCreatedAt(LocalDateTime.now());

        if (userId != null) {
            userRepository.findById(userId).ifPresent(receipt::setCreator);
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("入库明细不能为空");
        }

        List<Long> productIds = new ArrayList<>();
        for (InboundReceiptRequest.InboundReceiptItemRequest itemReq : request.getItems()) {
            if (itemReq == null || itemReq.getProductId() == null) throw new IllegalArgumentException("产品不能为空");
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) throw new IllegalArgumentException("入库数量必须大于0");
            productIds.add(itemReq.getProductId());
        }
        Map<Long, Product> lockedProducts = lockProducts(factoryId, productIds);

        // Lock every product in ID order before applying any line, including duplicate product lines.
        for (InboundReceiptRequest.InboundReceiptItemRequest itemReq : request.getItems()) {
            if (itemReq.getProductId() == null) {
                throw new IllegalArgumentException("产品不能为空");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("入库数量必须大于0");
            }
            Product product = lockedProducts.get(itemReq.getProductId());

            InboundReceiptItem item = new InboundReceiptItem();
            item.setInboundReceipt(receipt);
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setCustomDataJson(writeJson(itemReq.getCustomValues()));
            receipt.getItems().add(item);

            // Update product inventory
            int currentStock = product.getInventoryQty() != null ? product.getInventoryQty() : 0;
            product.setInventoryQty(InventoryQuantities.add(currentStock, itemReq.getQuantity()));
            productRepository.save(product);
        }

        InboundReceipt saved = inboundReceiptRepository.save(receipt);
        return toResponse(saved);
    }

    @CacheEvict(cacheNames = {"products.list", "inventory.details", "inbound.receipts.list", "inbound.receipts.detail", "outbound.receipts.list", "outbound.receipts.detail"}, allEntries = true)
    public void delete(Long factoryId, Long id) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdAndFactoryIdForUpdate(id, factoryId)
                .orElseThrow(() -> new IllegalArgumentException("入库单不存在"));

        Map<Long, Product> lockedProducts = lockProducts(factoryId, receipt.getItems().stream()
                .map(item -> item.getProduct().getId()).collect(Collectors.toList()));
        // The receipt lock prevents simultaneous deletions from reverting stock twice.
        for (InboundReceiptItem item : receipt.getItems()) {
            Product product = lockedProducts.get(item.getProduct().getId());
            int currentStock = product.getInventoryQty() != null ? product.getInventoryQty() : 0;
            if (currentStock < item.getQuantity()) {
                throw new IllegalArgumentException("产品 [" + product.getName() + "] 库存已被后续单据消耗，不能删除该入库单");
            }
            product.setInventoryQty(currentStock - item.getQuantity());
            productRepository.save(product);
        }

        inboundReceiptRepository.delete(receipt);
    }

    private Product productForInventoryUpdate(Long factoryId, Long productId) {
        return productRepository.findByIdAndFactoryIdForUpdate(productId, factoryId)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在"));
    }

    private Map<Long, Product> lockProducts(Long factoryId, List<Long> productIds) {
        Map<Long, Product> products = new LinkedHashMap<>();
        for (Long productId : new TreeSet<>(productIds)) {
            products.put(productId, productForInventoryUpdate(factoryId, productId));
        }
        return products;
    }

    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        InboundReceiptResponse response = new InboundReceiptResponse();
        response.setId(receipt.getId());
        response.setCode(receipt.getCode());
        response.setInboundType(receipt.getInboundType());
        response.setInboundTime(receipt.getInboundTime() != null ? receipt.getInboundTime().format(FMT) : null);
        response.setRemark(receipt.getRemark());
        response.setCreator(receipt.getCreator() != null ? receipt.getCreator().getName() : null);
        response.setCreatedAt(receipt.getCreatedAt() != null ? receipt.getCreatedAt().format(FMT) : null);
        response.setCustomValues(readCustomValues(receipt.getCustomDataJson()));

        List<InboundReceiptItemResponse> itemResponses = new ArrayList<>();
        if (receipt.getItems() != null) {
            for (InboundReceiptItem item : receipt.getItems()) {
                InboundReceiptItemResponse ir = new InboundReceiptItemResponse();
                ir.setId(item.getId());
                ir.setProductId(item.getProduct().getId());
                ir.setProductCode(item.getProduct().getCode());
                ir.setProductName(item.getProduct().getName());
                ir.setProductSpec(item.getProduct().getSpec());
                ir.setProductAttribute(item.getProduct().getAttribute());
                ir.setUnitName(item.getProduct().getUnit() != null ? item.getProduct().getUnit().getName() : null);
                ir.setMinStock(item.getProduct().getMinStock());
                ir.setMaxStock(item.getProduct().getMaxStock());
                ir.setSafetyStock(item.getProduct().getSafetyStock());
                ir.setInventoryQty(item.getProduct().getInventoryQty());
                ir.setQuantity(item.getQuantity());
                ir.setCustomValues(readCustomValues(item.getCustomDataJson()));
                itemResponses.add(ir);
            }
        }
        response.setItems(itemResponses);
        return response;
    }

    private String generateCode(Long factoryId) {
        String prefix = "RK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String writeJson(Object value) {
        if (value == null) return "[]";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<InboundReceiptRequest.CustomValueItem> readCustomValues(String json) {
        if (normalize(json) == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<InboundReceiptRequest.CustomValueItem>>() {});
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
