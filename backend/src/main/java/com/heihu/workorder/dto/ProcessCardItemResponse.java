package com.heihu.workorder.dto;

public class ProcessCardItemResponse {
    private Long processId;
    private String processName;
    private int taskCount;
    private int plannedQuantity;
    private int goodQuantity;
    private int defectQuantity;

    public ProcessCardItemResponse() {
    }

    public ProcessCardItemResponse(Long processId, String processName, int taskCount, int plannedQuantity, int goodQuantity, int defectQuantity) {
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

    public int getTaskCount() { return taskCount; }
    public void setTaskCount(int taskCount) { this.taskCount = taskCount; }

    public int getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(int plannedQuantity) { this.plannedQuantity = plannedQuantity; }

    public int getGoodQuantity() { return goodQuantity; }
    public void setGoodQuantity(int goodQuantity) { this.goodQuantity = goodQuantity; }

    public int getDefectQuantity() { return defectQuantity; }
    public void setDefectQuantity(int defectQuantity) { this.defectQuantity = defectQuantity; }
}
