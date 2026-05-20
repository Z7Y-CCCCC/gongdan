package com.heihu.workorder.dto;

import java.time.LocalDateTime;

public class InventoryDetailResponse {
    private String sourceType;
    private String receiptCode;
    private String receiptType;
    private LocalDateTime operationTime;
    private String remark;
    private String productCode;
    private String productName;
    private String productSpec;
    private String productAttribute;
    private String unitName;
    private Integer receiptQty;
    private Integer changeQty;

    public InventoryDetailResponse() {
    }

    public InventoryDetailResponse(String sourceType, String receiptCode, String receiptType,
                                   LocalDateTime operationTime, String remark, String productCode,
                                   String productName, String productSpec, String productAttribute,
                                   String unitName, Integer receiptQty, Integer changeQty) {
        this.sourceType = sourceType;
        this.receiptCode = receiptCode;
        this.receiptType = receiptType;
        this.operationTime = operationTime;
        this.remark = remark;
        this.productCode = productCode;
        this.productName = productName;
        this.productSpec = productSpec;
        this.productAttribute = productAttribute;
        this.unitName = unitName;
        this.receiptQty = receiptQty;
        this.changeQty = changeQty;
    }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getReceiptCode() { return receiptCode; }
    public void setReceiptCode(String receiptCode) { this.receiptCode = receiptCode; }

    public String getReceiptType() { return receiptType; }
    public void setReceiptType(String receiptType) { this.receiptType = receiptType; }

    public LocalDateTime getOperationTime() { return operationTime; }
    public void setOperationTime(LocalDateTime operationTime) { this.operationTime = operationTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

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

    public Integer getReceiptQty() { return receiptQty; }
    public void setReceiptQty(Integer receiptQty) { this.receiptQty = receiptQty; }

    public Integer getChangeQty() { return changeQty; }
    public void setChangeQty(Integer changeQty) { this.changeQty = changeQty; }
}
