package com.heihu.workorder.dto;

import java.util.List;

public class WorkOrderReportRequest {
    private Long workOrderId;
    private Integer processSequenceNo;
    private String processName;
    private Integer reportQuantity;
    private Integer goodQuantity;
    private Integer defectQuantity;
    private String defectItemName;
    private Integer reportDurationMinutes;
    private String startTime;
    private String endTime;
    private String materialName;
    private String remark;
    private List<Long> imageIds;

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Integer getProcessSequenceNo() { return processSequenceNo; }
    public void setProcessSequenceNo(Integer processSequenceNo) { this.processSequenceNo = processSequenceNo; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
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
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<Long> getImageIds() { return imageIds; }
    public void setImageIds(List<Long> imageIds) { this.imageIds = imageIds; }
}
