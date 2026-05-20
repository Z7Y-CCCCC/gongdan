package com.heihu.workorder.dto;

import java.util.List;

public class InboundReceiptResponse {
    private Long id;
    private String code;
    private String inboundType;
    private String inboundTime;
    private String remark;
    private String creator;
    private String createdAt;
    private List<InboundReceiptItemResponse> items;
    private List<InboundReceiptRequest.CustomValueItem> customValues;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getInboundType() { return inboundType; }
    public void setInboundType(String inboundType) { this.inboundType = inboundType; }

    public String getInboundTime() { return inboundTime; }
    public void setInboundTime(String inboundTime) { this.inboundTime = inboundTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<InboundReceiptItemResponse> getItems() { return items; }
    public void setItems(List<InboundReceiptItemResponse> items) { this.items = items; }

    public List<InboundReceiptRequest.CustomValueItem> getCustomValues() { return customValues; }
    public void setCustomValues(List<InboundReceiptRequest.CustomValueItem> customValues) { this.customValues = customValues; }

    public static class InboundReceiptItemResponse {
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
        private List<InboundReceiptRequest.CustomValueItem> customValues;

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

        public List<InboundReceiptRequest.CustomValueItem> getCustomValues() { return customValues; }
        public void setCustomValues(List<InboundReceiptRequest.CustomValueItem> customValues) { this.customValues = customValues; }
    }
}
