package com.heihu.workorder.dto;

import java.util.List;

public class WorkOrderProgressListResponse {
    private List<WorkOrderProgressItemResponse> items;

    public WorkOrderProgressListResponse() {
    }

    public WorkOrderProgressListResponse(List<WorkOrderProgressItemResponse> items) {
        this.items = items;
    }

    public List<WorkOrderProgressItemResponse> getItems() { return items; }
    public void setItems(List<WorkOrderProgressItemResponse> items) { this.items = items; }
}
