package com.heihu.workorder.dto;

import java.util.List;

/** Full-scope scalar aggregates; only the two preview lists are bounded. */
public class MobileDashboardResponse {
    private final long workOrderTotal;
    private final long totalPlanned;
    private final long totalGood;
    private final long activeOrderCount;
    private final long reportTotal;
    private final long pendingReportCount;
    private final long todayReportCount;
    private final long totalDefect;
    private final long todayGood;
    private final long todayDefect;
    private final long todayPending;
    private final List<WorkOrderResponse> recentOrders;
    private final List<Ranking> rankings;

    public MobileDashboardResponse(long workOrderTotal, long totalPlanned, long totalGood, long activeOrderCount,
                                   long reportTotal, long pendingReportCount, long todayReportCount, long totalDefect,
                                   long todayGood, long todayDefect, long todayPending,
                                   List<WorkOrderResponse> recentOrders, List<Ranking> rankings) {
        this.workOrderTotal = workOrderTotal;
        this.totalPlanned = totalPlanned;
        this.totalGood = totalGood;
        this.activeOrderCount = activeOrderCount;
        this.reportTotal = reportTotal;
        this.pendingReportCount = pendingReportCount;
        this.todayReportCount = todayReportCount;
        this.totalDefect = totalDefect;
        this.todayGood = todayGood;
        this.todayDefect = todayDefect;
        this.todayPending = todayPending;
        this.recentOrders = recentOrders;
        this.rankings = rankings;
    }

    public long getWorkOrderTotal() { return workOrderTotal; }
    public long getTotalPlanned() { return totalPlanned; }
    public long getTotalGood() { return totalGood; }
    public long getActiveOrderCount() { return activeOrderCount; }
    public long getReportTotal() { return reportTotal; }
    public long getPendingReportCount() { return pendingReportCount; }
    public long getTodayReportCount() { return todayReportCount; }
    public long getTotalDefect() { return totalDefect; }
    public long getTodayGood() { return todayGood; }
    public long getTodayDefect() { return todayDefect; }
    public long getTodayPending() { return todayPending; }
    public List<WorkOrderResponse> getRecentOrders() { return recentOrders; }
    public List<Ranking> getRankings() { return rankings; }

    public static class Ranking {
        private final String name;
        private final long value;
        public Ranking(String name, long value) { this.name = name; this.value = value; }
        public String getName() { return name; }
        public long getValue() { return value; }
    }
}
