package com.heihu.workorder.service;

import com.heihu.workorder.dto.InventoryDetailResponse;
import com.heihu.workorder.model.InboundReceipt;
import com.heihu.workorder.model.InboundReceiptItem;
import com.heihu.workorder.model.OutboundReceipt;
import com.heihu.workorder.model.OutboundReceiptItem;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.repository.InboundReceiptRepository;
import com.heihu.workorder.repository.OutboundReceiptRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InventoryDetailService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final OutboundReceiptRepository outboundReceiptRepository;

    public InventoryDetailService(
            InboundReceiptRepository inboundReceiptRepository,
            OutboundReceiptRepository outboundReceiptRepository
    ) {
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.outboundReceiptRepository = outboundReceiptRepository;
    }

    @Cacheable(cacheNames = "inventory.details", key = "#factoryId + ':' + #receiptCode + ':' + #receiptType + ':' + #sourceType + ':' + #remark + ':' + #productCode + ':' + #productName + ':' + #productSpec + ':' + #productAttribute + ':' + #startTime + ':' + #endTime")
    public long count(Long factoryId, String receiptCode, String receiptType,
                      String sourceType, String remark, String productCode,
                      String productName, String productSpec, String productAttribute,
                      LocalDateTime startTime, LocalDateTime endTime) {
        return filteredDetails(factoryId, receiptCode, receiptType, sourceType, remark,
                productCode, productName, productSpec, productAttribute, startTime, endTime)
                .size();
    }

    @Cacheable(cacheNames = "inventory.details", key = "#factoryId + ':' + #receiptCode + ':' + #receiptType + ':' + #sourceType + ':' + #remark + ':' + #productCode + ':' + #productName + ':' + #productSpec + ':' + #productAttribute + ':' + #startTime + ':' + #endTime + ':' + #page + ':' + #size")
    public List<InventoryDetailResponse> list(Long factoryId, String receiptCode, String receiptType,
                                               String sourceType, String remark, String productCode,
                                               String productName, String productSpec, String productAttribute,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               int page, int size) {
        List<InventoryDetailResponse> details = filteredDetails(factoryId, receiptCode, receiptType,
                sourceType, remark, productCode, productName, productSpec, productAttribute,
                startTime, endTime);

        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int fromIndex = (int) Math.min((long) (safePage - 1) * safeSize, details.size());
        int toIndex = (int) Math.min((long) fromIndex + safeSize, details.size());
        return new ArrayList<>(details.subList(fromIndex, toIndex));
    }

    private List<InventoryDetailResponse> filteredDetails(
            Long factoryId,
            String receiptCode,
            String receiptType,
            String sourceType,
            String remark,
            String productCode,
            String productName,
            String productSpec,
            String productAttribute,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return buildDetails(factoryId).stream()
                .filter(item -> matchesLike(item.getReceiptCode(), receiptCode))
                .filter(item -> matchesEquals(item.getReceiptType(), receiptType))
                .filter(item -> matchesEquals(item.getSourceType(), sourceType))
                .filter(item -> matchesLike(item.getRemark(), remark))
                .filter(item -> matchesLike(item.getProductCode(), productCode))
                .filter(item -> matchesLike(item.getProductName(), productName))
                .filter(item -> matchesLike(item.getProductSpec(), productSpec))
                .filter(item -> matchesEquals(item.getProductAttribute(), productAttribute))
                .filter(item -> startTime == null || (item.getOperationTime() != null && !item.getOperationTime().isBefore(startTime)))
                .filter(item -> endTime == null || (item.getOperationTime() != null && !item.getOperationTime().isAfter(endTime)))
                .sorted(Comparator.comparing(InventoryDetailResponse::getOperationTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private List<InventoryDetailResponse> buildDetails(Long factoryId) {
        List<InventoryDetailResponse> details = new ArrayList<>();

        for (InboundReceipt receipt : inboundReceiptRepository.findByFactoryId(factoryId, Pageable.unpaged())) {
            if (receipt.getItems() == null) continue;
            for (InboundReceiptItem item : receipt.getItems()) {
                details.add(toInboundDetail(receipt, item));
            }
        }

        for (OutboundReceipt receipt : outboundReceiptRepository.findByFactoryId(factoryId, Pageable.unpaged())) {
            if (receipt.getItems() == null) continue;
            for (OutboundReceiptItem item : receipt.getItems()) {
                details.add(toOutboundDetail(receipt, item));
            }
        }

        return details;
    }

    private InventoryDetailResponse toInboundDetail(InboundReceipt receipt, InboundReceiptItem item) {
        Product product = item.getProduct();
        return new InventoryDetailResponse(
                "IN",
                receipt.getCode(),
                receipt.getInboundType(),
                receipt.getInboundTime(),
                receipt.getRemark(),
                product != null ? product.getCode() : null,
                product != null ? product.getName() : null,
                product != null ? product.getSpec() : null,
                product != null ? product.getAttribute() : null,
                product != null && product.getUnit() != null ? product.getUnit().getName() : null,
                item.getQuantity(),
                item.getQuantity()
        );
    }

    private InventoryDetailResponse toOutboundDetail(OutboundReceipt receipt, OutboundReceiptItem item) {
        Product product = item.getProduct();
        int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
        return new InventoryDetailResponse(
                "OUT",
                receipt.getCode(),
                receipt.getOutboundType(),
                receipt.getOutboundTime(),
                receipt.getRemark(),
                product != null ? product.getCode() : null,
                product != null ? product.getName() : null,
                product != null ? product.getSpec() : null,
                product != null ? product.getAttribute() : null,
                product != null && product.getUnit() != null ? product.getUnit().getName() : null,
                quantity,
                -quantity
        );
    }

    private boolean matchesLike(String actual, String expected) {
        String needle = normalize(expected);
        if (needle == null) return true;
        return normalize(actual) != null && normalize(actual).contains(needle);
    }

    private boolean matchesEquals(String actual, String expected) {
        String needle = normalize(expected);
        if (needle == null) return true;
        return needle.equals(normalize(actual));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
