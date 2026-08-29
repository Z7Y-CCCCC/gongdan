package com.heihu.workorder.dto;

import com.heihu.workorder.model.FactoryUser;
import java.time.LocalDateTime;

public class InternalChatContactResponse {

    private Long id;
    private String username;
    private String name;
    private String roleLabel;
    private String departmentName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private int unreadCount;

    public InternalChatContactResponse(FactoryUser user, String lastMessage, LocalDateTime lastMessageAt, int unreadCount) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.name = user.getName();
        this.roleLabel = user.getRole().getLabel();
        this.departmentName = user.getDepartment() == null ? null : user.getDepartment().getName();
        this.lastMessage = lastMessage;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getRoleLabel() { return roleLabel; }
    public String getDepartmentName() { return departmentName; }
    public String getLastMessage() { return lastMessage; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public int getUnreadCount() { return unreadCount; }
}
