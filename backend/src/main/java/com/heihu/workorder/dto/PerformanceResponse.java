package com.heihu.workorder.dto;

public class PerformanceResponse {
    private long plannedQuantity;
    private long completedQuantity;
    private long workOrderCount;
    private double defectRatePercent;
    private double averageCycleDays;
    private long overdueUnfinishedCount;

    public PerformanceResponse() {
    }

    public PerformanceResponse(long plannedQuantity, long completedQuantity, long workOrderCount, double defectRatePercent, double averageCycleDays, long overdueUnfinishedCount) {
        this.plannedQuantity = plannedQuantity;
        this.completedQuantity = completedQuantity;
        this.workOrderCount = workOrderCount;
        this.defectRatePercent = defectRatePercent;
        this.averageCycleDays = averageCycleDays;
        this.overdueUnfinishedCount = overdueUnfinishedCount;
    }

    public long getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(long plannedQuantity) { this.plannedQuantity = plannedQuantity; }

    public long getCompletedQuantity() { return completedQuantity; }
    public void setCompletedQuantity(long completedQuantity) { this.completedQuantity = completedQuantity; }

    public long getWorkOrderCount() { return workOrderCount; }
    public void setWorkOrderCount(long workOrderCount) { this.workOrderCount = workOrderCount; }

    public double getDefectRatePercent() { return defectRatePercent; }
    public void setDefectRatePercent(double defectRatePercent) { this.defectRatePercent = defectRatePercent; }

    public double getAverageCycleDays() { return averageCycleDays; }
    public void setAverageCycleDays(double averageCycleDays) { this.averageCycleDays = averageCycleDays; }

    public long getOverdueUnfinishedCount() { return overdueUnfinishedCount; }
    public void setOverdueUnfinishedCount(long overdueUnfinishedCount) { this.overdueUnfinishedCount = overdueUnfinishedCount; }
}
