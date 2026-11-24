package com.heihu.workorder.model;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "work_order_reports")
public class WorkOrderReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(name = "work_order_code", nullable = false, length = 64)
    private String workOrderCode;

    @Column(name = "product_name", length = 128)
    private String productName;

    @Column(name = "product_code", length = 64)
    private String productCode;

    @Column(name = "product_spec", length = 128)
    private String productSpec;

    @Column(name = "process_sequence_no", nullable = false)
    private Integer processSequenceNo;

    @Column(name = "process_name", nullable = false, length = 128)
    private String processName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private FactoryUser reporter;

    // Retain the actual actor's identity even if that account is later removed.
    @Column(name = "submitter_id")
    private Long submitterId;

    @Column(name = "reporter_name", nullable = false, length = 64)
    private String reporterName;

    @Column(name = "report_quantity", nullable = false)
    private Integer reportQuantity = 0;

    @Column(name = "good_quantity", nullable = false)
    private Integer goodQuantity = 0;

    @Column(name = "defect_quantity", nullable = false)
    private Integer defectQuantity = 0;

    @Column(name = "defect_item_name", length = 128)
    private String defectItemName;

    @Column(name = "report_duration_minutes", nullable = false)
    private Integer reportDurationMinutes = 0;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "material_name", length = 128)
    private String materialName;

    @Lob
    @Column(name = "remark")
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportStatus status = ReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private FactoryUser approver;

    @Column(name = "approver_name", length = 64)
    private String approverName;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_remark", length = 500)
    private String approvalRemark;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubmitterId() { return submitterId; }
    public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
    public Factory getFactory() { return factory; }
    public void setFactory(Factory factory) { this.factory = factory; }
    public WorkOrder getWorkOrder() { return workOrder; }
    public void setWorkOrder(WorkOrder workOrder) { this.workOrder = workOrder; }
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
    public FactoryUser getReporter() { return reporter; }
    public void setReporter(FactoryUser reporter) { this.reporter = reporter; }
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
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public FactoryUser getApprover() { return approver; }
    public void setApprover(FactoryUser approver) { this.approver = approver; }
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
}
