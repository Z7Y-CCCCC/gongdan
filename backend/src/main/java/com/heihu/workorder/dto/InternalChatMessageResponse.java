package com.heihu.workorder.dto;

import com.heihu.workorder.model.InternalMessage;
import java.time.LocalDateTime;

public class InternalChatMessageResponse {

    private Long id;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String content;
    private boolean read;
    private LocalDateTime createdAt;

    public static InternalChatMessageResponse from(InternalMessage message) {
        InternalChatMessageResponse response = new InternalChatMessageResponse();
        response.id = message.getId();
        response.senderId = message.getSender().getId();
        response.senderName = message.getSender().getName();
        response.receiverId = message.getReceiver().getId();
        response.content = message.getContent();
        response.read = message.getReadAt() != null;
        response.createdAt = message.getCreatedAt();
        return response;
    }

    public Long getId() { return id; }
    public Long getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public Long getReceiverId() { return receiverId; }
    public String getContent() { return content; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
