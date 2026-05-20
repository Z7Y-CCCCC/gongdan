package com.heihu.workorder.dto;

public class RecentReportItemResponse {
    private Long id;
    private Long workOrderId;
    private String workOrderCode;
    private String reporterName;
    private String processName;
    private int goodQuantity;
    private int defectQuantity;
    private String createdAt;

    public RecentReportItemResponse() {
    }

    public RecentReportItemResponse(Long id, Long workOrderId, String workOrderCode, String reporterName, String processName, int goodQuantity, int defectQuantity, String createdAt) {
        this.id = id;
        this.workOrderId = workOrderId;
        this.workOrderCode = workOrderCode;
        this.reporterName = reporterName;
        this.processName = processName;
        this.goodQuantity = goodQuantity;
        this.defectQuantity = defectQuantity;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }

    public String getWorkOrderCode() { return workOrderCode; }
    public void setWorkOrderCode(String workOrderCode) { this.workOrderCode = workOrderCode; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public int getGoodQuantity() { return goodQuantity; }
    public void setGoodQuantity(int goodQuantity) { this.goodQuantity = goodQuantity; }

    public int getDefectQuantity() { return defectQuantity; }
    public void setDefectQuantity(int defectQuantity) { this.defectQuantity = defectQuantity; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
