package com.heihu.workorder.dto;

public class WorkOrderProgressItemResponse {
    private Long id;
    private String code;
    private String productCode;
    private String productName;
    private String productSpec;
    private String status;
    private String statusLabel;

    public WorkOrderProgressItemResponse() {
    }

    public WorkOrderProgressItemResponse(Long id, String code, String productCode, String productName, String productSpec, String status, String statusLabel) {
        this.id = id;
        this.code = code;
        this.productCode = productCode;
        this.productName = productName;
        this.productSpec = productSpec;
        this.status = status;
        this.statusLabel = statusLabel;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductSpec() { return productSpec; }
    public void setProductSpec(String productSpec) { this.productSpec = productSpec; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
}
