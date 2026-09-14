package com.heihu.workorder.dto;

public class ProcessCardItemResponse {
    private Long processId;
    private String processName;
    private long taskCount;
    private long plannedQuantity;
    private long goodQuantity;
    private long defectQuantity;

    public ProcessCardItemResponse() {
    }

    public ProcessCardItemResponse(Long processId, String processName, long taskCount, long plannedQuantity, long goodQuantity, long defectQuantity) {
        this.processId = processId;
        this.processName = processName;
        this.taskCount = taskCount;
        this.plannedQuantity = plannedQuantity;
        this.goodQuantity = goodQuantity;
        this.defectQuantity = defectQuantity;
    }

    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public long getTaskCount() { return taskCount; }
    public void setTaskCount(long taskCount) { this.taskCount = taskCount; }

    public long getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(long plannedQuantity) { this.plannedQuantity = plannedQuantity; }

    public long getGoodQuantity() { return goodQuantity; }
    public void setGoodQuantity(long goodQuantity) { this.goodQuantity = goodQuantity; }

    public long getDefectQuantity() { return defectQuantity; }
    public void setDefectQuantity(long defectQuantity) { this.defectQuantity = defectQuantity; }
}
