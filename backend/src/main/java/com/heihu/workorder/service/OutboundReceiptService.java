package com.heihu.workorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heihu.workorder.dto.OutboundReceiptRequest;
import com.heihu.workorder.dto.OutboundReceiptResponse;
import com.heihu.workorder.dto.OutboundReceiptResponse.OutboundReceiptItemResponse;
import com.heihu.workorder.model.*;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.OutboundReceiptRepository;
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
public class OutboundReceiptService {
    @org.springframework.beans.factory.annotation.Autowired
    private CustomFieldValueValidator customFieldValueValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OutboundReceiptRepository outboundReceiptRepository;
    private final FactoryRepository factoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OutboundReceiptService(OutboundReceiptRepository outboundReceiptRepository,
                                  FactoryRepository factoryRepository,
                                  ProductRepository productRepository,
                                  UserRepository userRepository) {
        this.outboundReceiptRepository = outboundReceiptRepository;
        this.factoryRepository = factoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Cacheable(cacheNames = "outbound.receipts.list", key = "#factoryId + ':' + #code + ':' + #outboundType + ':' + #startTime + ':' + #endTime + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public Page<OutboundReceiptResponse> list(Long factoryId, String code, String outboundType,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               Pageable pageable) {
        boolean hasFilter = (code != null && !code.trim().isEmpty())
                || (outboundType != null && !outboundType.trim().isEmpty())
                || startTime != null || endTime != null;

        if (!hasFilter) {
            return outboundReceiptRepository.findByFactoryId(factoryId, pageable).map(this::toResponse);
        }

        return outboundReceiptRepository.search(
                factoryId,
                normalize(code),
                normalize(outboundType),
                startTime,
                endTime,
                pageable
        ).map(this::toResponse);
    }

    @Cacheable(cacheNames = "outbound.receipts.detail", key = "#factoryId + ':' + #id")
    public OutboundReceiptResponse getById(Long factoryId, Long id) {
        OutboundReceipt receipt = outboundReceiptRepository.findById(id)
                .filter(r -> r.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("出库单不存在"));
        return toResponse(receipt);
    }

    @CacheEvict(cacheNames = {"products.list", "inventory.details", "inbound.receipts.list", "inbound.receipts.detail", "outbound.receipts.list", "outbound.receipts.detail"}, allEntries = true)
    public OutboundReceiptResponse create(Long factoryId, Long userId, OutboundReceiptRequest request) {
        if (request == null) throw new IllegalArgumentException("请填写出库单信息");
        if (customFieldValueValidator != null) customFieldValueValidator.validate(factoryId, "receipt", request.getCustomValues(), null, false);
        Factory factory = factoryRepository.findById(factoryId)
                .orElseThrow(() -> new IllegalArgumentException("工厂不存在"));

        if (request.getOutboundType() == null || request.getOutboundType().trim().isEmpty()) {
            throw new IllegalArgumentException("出库类型不能为空");
        }
        if (request.getOutboundTime() == null || request.getOutboundTime().trim().isEmpty()) {
            throw new IllegalArgumentException("出库时间不能为空");
        }

        String code = normalize(request.getCode());
        if (code == null) {
            code = generateCode(factoryId);
        } else {
            outboundReceiptRepository.findByFactoryIdAndCode(factoryId, code).ifPresent(existing -> {
                throw new IllegalArgumentException("出库单号已存在：" + existing.getCode());
            });
        }

        OutboundReceipt receipt = new OutboundReceipt();
        receipt.setFactory(factory);
        receipt.setCode(code);
        receipt.setOutboundType(request.getOutboundType().trim());
        receipt.setOutboundTime(DateTimeParser.parse(request.getOutboundTime()));
        receipt.setRemark(normalize(request.getRemark()));
        receipt.setCustomDataJson(writeJson(request.getCustomValues()));
        receipt.setCreatedAt(LocalDateTime.now());

        if (userId != null) {
            userRepository.findById(userId).ifPresent(receipt::setCreator);
        }

        // Process items
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<Long> productIds = new ArrayList<>();
            for (OutboundReceiptRequest.OutboundReceiptItemRequest itemReq : request.getItems()) {
                if (itemReq == null || itemReq.getProductId() == null) throw new IllegalArgumentException("产品不能为空");
                if (customFieldValueValidator != null) customFieldValueValidator.validate(factoryId, "receipt_detail", itemReq.getCustomValues(), null, false);
                if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) throw new IllegalArgumentException("出库数量必须大于0");
                productIds.add(itemReq.getProductId());
            }
            Map<Long, Product> lockedProducts = lockProducts(factoryId, productIds);
            for (OutboundReceiptRequest.OutboundReceiptItemRequest itemReq : request.getItems()) {
                if (itemReq.getProductId() == null) {
                    throw new IllegalArgumentException("产品不能为空");
                }
                if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                    throw new IllegalArgumentException("出库数量必须大于0");
                }
                Product product = lockedProducts.get(itemReq.getProductId());

                // Stock validation
                int currentStock = product.getInventoryQty() != null ? product.getInventoryQty() : 0;
                if (currentStock < itemReq.getQuantity()) {
                    throw new IllegalArgumentException("产品 [" + product.getName() + "] 库存不足，当前库存为 " + currentStock + "，无法出库 " + itemReq.getQuantity());
                }

                OutboundReceiptItem item = new OutboundReceiptItem();
                item.setOutboundReceipt(receipt);
                item.setProduct(product);
                item.setQuantity(itemReq.getQuantity());
                item.setCustomDataJson(writeJson(itemReq.getCustomValues()));
                receipt.getItems().add(item);

                // Update product inventory
                product.setInventoryQty(currentStock - itemReq.getQuantity());
                productRepository.save(product);
            }
        } else {
            throw new IllegalArgumentException("出库明细不能为空");
        }

        OutboundReceipt saved = outboundReceiptRepository.save(receipt);
        return toResponse(saved);
    }

    @CacheEvict(cacheNames = {"products.list", "inventory.details", "inbound.receipts.list", "inbound.receipts.detail", "outbound.receipts.list", "outbound.receipts.detail"}, allEntries = true)
    public void delete(Long factoryId, Long id) {
        OutboundReceipt receipt = outboundReceiptRepository.findByIdAndFactoryIdForUpdate(id, factoryId)
                .orElseThrow(() -> new IllegalArgumentException("出库单不存在"));

        Map<Long, Product> lockedProducts = lockProducts(factoryId, receipt.getItems().stream()
                .map(item -> item.getProduct().getId()).collect(Collectors.toList()));
        // The receipt lock prevents simultaneous deletions from reverting stock twice.
        for (OutboundReceiptItem item : receipt.getItems()) {
            Product product = lockedProducts.get(item.getProduct().getId());
            int currentStock = product.getInventoryQty() != null ? product.getInventoryQty() : 0;
            product.setInventoryQty(InventoryQuantities.add(currentStock, item.getQuantity()));
            productRepository.save(product);
        }

        outboundReceiptRepository.delete(receipt);
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

    private OutboundReceiptResponse toResponse(OutboundReceipt receipt) {
        OutboundReceiptResponse response = new OutboundReceiptResponse();
        response.setId(receipt.getId());
        response.setCode(receipt.getCode());
        response.setOutboundType(receipt.getOutboundType());
        response.setOutboundTime(receipt.getOutboundTime() != null ? receipt.getOutboundTime().format(FMT) : null);
        response.setRemark(receipt.getRemark());
        response.setCreator(receipt.getCreator() != null ? receipt.getCreator().getName() : null);
        response.setCreatedAt(receipt.getCreatedAt() != null ? receipt.getCreatedAt().format(FMT) : null);
        response.setCustomValues(readCustomValues(receipt.getCustomDataJson()));

        List<OutboundReceiptItemResponse> itemResponses = new ArrayList<>();
        if (receipt.getItems() != null) {
            for (OutboundReceiptItem item : receipt.getItems()) {
                OutboundReceiptItemResponse ir = new OutboundReceiptItemResponse();
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
        String prefix = "CK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
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

    private List<OutboundReceiptRequest.CustomValueItem> readCustomValues(String json) {
        if (normalize(json) == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<OutboundReceiptRequest.CustomValueItem>>() {});
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
