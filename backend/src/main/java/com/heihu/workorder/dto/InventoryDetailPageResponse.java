package com.heihu.workorder.dto;

import java.util.List;

public class InventoryDetailPageResponse {
    private List<InventoryDetailResponse> items;
    private long total;

    public InventoryDetailPageResponse() {}

    public InventoryDetailPageResponse(List<InventoryDetailResponse> items, long total) {
        this.items = items;
        this.total = total;
    }

    public List<InventoryDetailResponse> getItems() { return items; }
    public void setItems(List<InventoryDetailResponse> items) { this.items = items; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
