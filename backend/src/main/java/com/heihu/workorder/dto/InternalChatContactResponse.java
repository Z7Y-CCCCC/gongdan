package com.heihu.workorder.dto;

import com.heihu.workorder.model.FactoryUser;
import java.time.LocalDateTime;
import java.util.List;

public class InternalChatContactResponse {

    private Long id;
    private String username;
    private String name;
    private String roleLabel;
    private String departmentName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private int unreadCount;
    private boolean online;
    private int onlineDeviceCount;
    private List<InternalChatDeviceResponse> onlineDevices;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastLoginAt;

    public InternalChatContactResponse(
            FactoryUser user,
            String lastMessage,
            LocalDateTime lastMessageAt,
            int unreadCount,
            boolean online,
            List<InternalChatDeviceResponse> onlineDevices,
            LocalDateTime lastSeenAt,
            LocalDateTime lastLoginAt
    ) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.name = user.getName();
        this.roleLabel = user.getRole().getLabel();
        this.departmentName = user.getDepartment() == null ? null : user.getDepartment().getName();
        this.lastMessage = lastMessage;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
        this.online = online;
        this.onlineDeviceCount = onlineDevices == null ? 0 : onlineDevices.size();
        this.onlineDevices = onlineDevices;
        this.lastSeenAt = lastSeenAt;
        this.lastLoginAt = lastLoginAt;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getRoleLabel() { return roleLabel; }
    public String getDepartmentName() { return departmentName; }
    public String getLastMessage() { return lastMessage; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public int getUnreadCount() { return unreadCount; }
    public boolean isOnline() { return online; }
    public int getOnlineDeviceCount() { return onlineDeviceCount; }
    public List<InternalChatDeviceResponse> getOnlineDevices() { return onlineDevices; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}
