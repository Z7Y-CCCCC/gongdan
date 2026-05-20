package com.heihu.workorder.dto;

import java.util.List;

public class OutboundReceiptRequest {
    private String code;
    private String outboundType;
    private String outboundTime;
    private String remark;
    private List<OutboundReceiptItemRequest> items;
    private List<CustomValueItem> customValues;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getOutboundType() { return outboundType; }
    public void setOutboundType(String outboundType) { this.outboundType = outboundType; }

    public String getOutboundTime() { return outboundTime; }
    public void setOutboundTime(String outboundTime) { this.outboundTime = outboundTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public List<OutboundReceiptItemRequest> getItems() { return items; }
    public void setItems(List<OutboundReceiptItemRequest> items) { this.items = items; }

    public List<CustomValueItem> getCustomValues() { return customValues; }
    public void setCustomValues(List<CustomValueItem> customValues) { this.customValues = customValues; }

    public static class OutboundReceiptItemRequest {
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
