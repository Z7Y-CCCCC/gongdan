package com.heihu.workorder.dto;

public class PerformanceResponse {
    private int plannedQuantity;
    private int completedQuantity;
    private int workOrderCount;
    private double defectRatePercent;
    private double averageCycleDays;
    private int overdueUnfinishedCount;

    public PerformanceResponse() {
    }

    public PerformanceResponse(int plannedQuantity, int completedQuantity, int workOrderCount, double defectRatePercent, double averageCycleDays, int overdueUnfinishedCount) {
        this.plannedQuantity = plannedQuantity;
        this.completedQuantity = completedQuantity;
        this.workOrderCount = workOrderCount;
        this.defectRatePercent = defectRatePercent;
        this.averageCycleDays = averageCycleDays;
        this.overdueUnfinishedCount = overdueUnfinishedCount;
    }

    public int getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(int plannedQuantity) { this.plannedQuantity = plannedQuantity; }

    public int getCompletedQuantity() { return completedQuantity; }
    public void setCompletedQuantity(int completedQuantity) { this.completedQuantity = completedQuantity; }

    public int getWorkOrderCount() { return workOrderCount; }
    public void setWorkOrderCount(int workOrderCount) { this.workOrderCount = workOrderCount; }

    public double getDefectRatePercent() { return defectRatePercent; }
    public void setDefectRatePercent(double defectRatePercent) { this.defectRatePercent = defectRatePercent; }

    public double getAverageCycleDays() { return averageCycleDays; }
    public void setAverageCycleDays(double averageCycleDays) { this.averageCycleDays = averageCycleDays; }

    public int getOverdueUnfinishedCount() { return overdueUnfinishedCount; }
    public void setOverdueUnfinishedCount(int overdueUnfinishedCount) { this.overdueUnfinishedCount = overdueUnfinishedCount; }
}
