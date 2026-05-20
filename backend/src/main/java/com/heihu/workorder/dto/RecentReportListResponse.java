package com.heihu.workorder.dto;

import java.util.List;

public class RecentReportListResponse {
    private List<RecentReportItemResponse> items;

    public RecentReportListResponse() {
    }

    public RecentReportListResponse(List<RecentReportItemResponse> items) {
        this.items = items;
    }

    public List<RecentReportItemResponse> getItems() { return items; }
    public void setItems(List<RecentReportItemResponse> items) { this.items = items; }
}
