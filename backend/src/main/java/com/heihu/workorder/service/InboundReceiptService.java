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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public InboundReceiptResponse getById(Long factoryId, Long id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .filter(r -> r.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("入库单不存在"));
        return toResponse(receipt);
    }

    public InboundReceiptResponse create(Long factoryId, Long userId, InboundReceiptRequest request) {
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
        receipt.setInboundTime(LocalDateTime.parse(request.getInboundTime().trim(), FMT));
        receipt.setRemark(normalize(request.getRemark()));
        receipt.setCustomDataJson(writeJson(request.getCustomValues()));
        receipt.setCreatedAt(LocalDateTime.now());

        if (userId != null) {
            userRepository.findById(userId).ifPresent(receipt::setCreator);
        }

        // Process items
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (InboundReceiptRequest.InboundReceiptItemRequest itemReq : request.getItems()) {
                if (itemReq.getProductId() == null) {
                    throw new IllegalArgumentException("产品不能为空");
                }
                if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                    throw new IllegalArgumentException("入库数量必须大于0");
                }
                Product product = productRepository.findById(itemReq.getProductId())
                        .filter(p -> p.getFactory().getId().equals(factoryId))
                        .orElseThrow(() -> new IllegalArgumentException("产品不存在"));

                InboundReceiptItem item = new InboundReceiptItem();
                item.setInboundReceipt(receipt);
                item.setProduct(product);
                item.setQuantity(itemReq.getQuantity());
                item.setCustomDataJson(writeJson(itemReq.getCustomValues()));
                receipt.getItems().add(item);

                // Update product inventory
                product.setInventoryQty(product.getInventoryQty() + itemReq.getQuantity());
                productRepository.save(product);
            }
        }

        InboundReceipt saved = inboundReceiptRepository.save(receipt);
        return toResponse(saved);
    }

    public void delete(Long factoryId, Long id) {
        InboundReceipt receipt = inboundReceiptRepository.findById(id)
                .filter(r -> r.getFactory().getId().equals(factoryId))
                .orElseThrow(() -> new IllegalArgumentException("入库单不存在"));

        // Revert inventory
        for (InboundReceiptItem item : receipt.getItems()) {
            Product product = item.getProduct();
            int newQty = product.getInventoryQty() - item.getQuantity();
            product.setInventoryQty(Math.max(newQty, 0));
            productRepository.save(product);
        }

        inboundReceiptRepository.delete(receipt);
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
        long count = inboundReceiptRepository.findByFactoryId(factoryId,
                org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        return prefix + String.format("%04d", count + 1);
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
