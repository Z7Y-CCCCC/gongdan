package com.heihu.workorder.dto;

import java.util.List;

public class OutboundReceiptResponse {
    private Long id;
    private String code;
    private String outboundType;
    private String outboundTime;
    private String remark;
    private String creator;
    private String createdAt;
    private List<OutboundReceiptItemResponse> items;
    private List<OutboundReceiptRequest.CustomValueItem> customValues;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getOutboundType() { return outboundType; }
    public void setOutboundType(String outboundType) { this.outboundType = outboundType; }

    public String getOutboundTime() { return outboundTime; }
    public void setOutboundTime(String outboundTime) { this.outboundTime = outboundTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<OutboundReceiptItemResponse> getItems() { return items; }
    public void setItems(List<OutboundReceiptItemResponse> items) { this.items = items; }

    public List<OutboundReceiptRequest.CustomValueItem> getCustomValues() { return customValues; }
    public void setCustomValues(List<OutboundReceiptRequest.CustomValueItem> customValues) { this.customValues = customValues; }

    public static class OutboundReceiptItemResponse {
        private Long id;
        private Long productId;
        private String productCode;
        private String productName;
        private String productSpec;
        private String productAttribute;
        private String unitName;
        private String minStock;
        private String maxStock;
        private String safetyStock;
        private Integer inventoryQty;
        private Integer quantity;
        private List<OutboundReceiptRequest.CustomValueItem> customValues;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getProductSpec() { return productSpec; }
        public void setProductSpec(String productSpec) { this.productSpec = productSpec; }

        public String getProductAttribute() { return productAttribute; }
        public void setProductAttribute(String productAttribute) { this.productAttribute = productAttribute; }

        public String getUnitName() { return unitName; }
        public void setUnitName(String unitName) { this.unitName = unitName; }

        public String getMinStock() { return minStock; }
        public void setMinStock(String minStock) { this.minStock = minStock; }

        public String getMaxStock() { return maxStock; }
        public void setMaxStock(String maxStock) { this.maxStock = maxStock; }

        public String getSafetyStock() { return safetyStock; }
        public void setSafetyStock(String safetyStock) { this.safetyStock = safetyStock; }

        public Integer getInventoryQty() { return inventoryQty; }
        public void setInventoryQty(Integer inventoryQty) { this.inventoryQty = inventoryQty; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public List<OutboundReceiptRequest.CustomValueItem> getCustomValues() { return customValues; }
        public void setCustomValues(List<OutboundReceiptRequest.CustomValueItem> customValues) { this.customValues = customValues; }
    }
}
