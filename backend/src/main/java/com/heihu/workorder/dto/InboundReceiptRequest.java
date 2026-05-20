package com.heihu.workorder.dto;

import java.util.List;

public class InboundReceiptRequest {
    private String code;
    private String inboundType;
    private String inboundTime;
    private String remark;
    private List<InboundReceiptItemRequest> items;
    private List<CustomValueItem> customValues;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getInboundType() { return inboundType; }
    public void setInboundType(String inboundType) { this.inboundType = inboundType; }

    public String getInboundTime() { return inboundTime; }
    public void setInboundTime(String inboundTime) { this.inboundTime = inboundTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public List<InboundReceiptItemRequest> getItems() { return items; }
    public void setItems(List<InboundReceiptItemRequest> items) { this.items = items; }

    public List<CustomValueItem> getCustomValues() { return customValues; }
    public void setCustomValues(List<CustomValueItem> customValues) { this.customValues = customValues; }

    public static class InboundReceiptItemRequest {
        private Long productId;
        private Integer quantity;
        private List<CustomValueItem> customValues;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public List<CustomValueItem> getCustomValues() { return customValues; }
        public void setCustomValues(List<CustomValueItem> customValues) { this.customValues = customValues; }
    }

    public static class CustomValueItem {
        private Long fieldId;
        private String fieldName;
        private String fieldType;
        private Object value;

        public Long getFieldId() { return fieldId; }
        public void setFieldId(Long fieldId) { this.fieldId = fieldId; }

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }

        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }
}
