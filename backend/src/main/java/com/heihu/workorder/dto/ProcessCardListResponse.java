package com.heihu.workorder.dto;

import java.util.List;

public class ProcessCardListResponse {
    private List<ProcessCardItemResponse> items;

    public ProcessCardListResponse() {
    }

    public ProcessCardListResponse(List<ProcessCardItemResponse> items) {
        this.items = items;
    }

    public List<ProcessCardItemResponse> getItems() { return items; }
    public void setItems(List<ProcessCardItemResponse> items) { this.items = items; }
}
