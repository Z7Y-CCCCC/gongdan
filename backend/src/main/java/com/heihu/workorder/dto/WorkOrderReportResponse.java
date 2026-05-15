package com.heihu.workorder.dto;

import com.heihu.workorder.model.WorkOrderReport;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkOrderReportResponse {
    private Long id;
    private Long workOrderId;
    private String workOrderCode;
    private String productName;
    private String productCode;
    private String productSpec;
    private Integer processSequenceNo;
    private String processName;
    private Long reporterId;
    private String reporterName;
    private Integer reportQuantity;
    private Integer goodQuantity;
    private Integer defectQuantity;
    private String defectItemName;
    private Integer reportDurationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String materialName;
    private String remark;
    private String status;
    private Long approverId;
    private String approverName;
    private LocalDateTime approvedAt;
    private String approvalRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<WorkOrderReportImageResponse> images = new ArrayList<>();

    public static WorkOrderReportResponse from(WorkOrderReport report, List<WorkOrderReportImageResponse> images) {
        WorkOrderReportResponse response = new WorkOrderReportResponse();
        response.setId(report.getId());
        response.setWorkOrderId(report.getWorkOrder() != null ? report.getWorkOrder().getId() : null);
        response.setWorkOrderCode(report.getWorkOrderCode());
        response.setProductName(report.getProductName());
        response.setProductCode(report.getProductCode());
        response.setProductSpec(report.getProductSpec());
        response.setProcessSequenceNo(report.getProcessSequenceNo());
        response.setProcessName(report.getProcessName());
        response.setReporterId(report.getReporter() != null ? report.getReporter().getId() : null);
        response.setReporterName(report.getReporterName());
        response.setReportQuantity(report.getReportQuantity());
        response.setGoodQuantity(report.getGoodQuantity());
        response.setDefectQuantity(report.getDefectQuantity());
        response.setDefectItemName(report.getDefectItemName());
        response.setReportDurationMinutes(report.getReportDurationMinutes());
        response.setStartTime(report.getStartTime());
        response.setEndTime(report.getEndTime());
        response.setMaterialName(report.getMaterialName());
        response.setRemark(report.getRemark());
        response.setStatus(report.getStatus().name());
        response.setApproverId(report.getApprover() != null ? report.getApprover().getId() : null);
        response.setApproverName(report.getApproverName());
        response.setApprovedAt(report.getApprovedAt());
        response.setApprovalRemark(report.getApprovalRemark());
        response.setCreatedAt(report.getCreatedAt());
        response.setUpdatedAt(report.getUpdatedAt());
        response.setImages(images);
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public String getWorkOrderCode() { return workOrderCode; }
    public void setWorkOrderCode(String workOrderCode) { this.workOrderCode = workOrderCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductSpec() { return productSpec; }
    public void setProductSpec(String productSpec) { this.productSpec = productSpec; }
    public Integer getProcessSequenceNo() { return processSequenceNo; }
    public void setProcessSequenceNo(Integer processSequenceNo) { this.processSequenceNo = processSequenceNo; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public Integer getReportQuantity() { return reportQuantity; }
    public void setReportQuantity(Integer reportQuantity) { this.reportQuantity = reportQuantity; }
    public Integer getGoodQuantity() { return goodQuantity; }
    public void setGoodQuantity(Integer goodQuantity) { this.goodQuantity = goodQuantity; }
    public Integer getDefectQuantity() { return defectQuantity; }
    public void setDefectQuantity(Integer defectQuantity) { this.defectQuantity = defectQuantity; }
    public String getDefectItemName() { return defectItemName; }
    public void setDefectItemName(String defectItemName) { this.defectItemName = defectItemName; }
    public Integer getReportDurationMinutes() { return reportDurationMinutes; }
    public void setReportDurationMinutes(Integer reportDurationMinutes) { this.reportDurationMinutes = reportDurationMinutes; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovalRemark() { return approvalRemark; }
    public void setApprovalRemark(String approvalRemark) { this.approvalRemark = approvalRemark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<WorkOrderReportImageResponse> getImages() { return images; }
    public void setImages(List<WorkOrderReportImageResponse> images) { this.images = images; }
}
