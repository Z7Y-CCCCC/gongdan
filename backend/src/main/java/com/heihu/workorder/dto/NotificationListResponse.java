package com.heihu.workorder.dto;

import java.util.List;

public class NotificationListResponse {

    private final List<NotificationResponse> items;
    private final long total;
    private final long unreadCount;

    public NotificationListResponse(List<NotificationResponse> items, long total, long unreadCount) {
        this.items = items;
        this.total = total;
        this.unreadCount = unreadCount;
    }

    public List<NotificationResponse> getItems() { return items; }
    public long getTotal() { return total; }
    public long getUnreadCount() { return unreadCount; }
}
