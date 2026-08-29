package com.heihu.workorder.dto;

import com.heihu.workorder.model.NotificationRecipient;
import java.time.LocalDateTime;

public class NotificationResponse {

    private Long id;
    private String eventType;
    private String title;
    private String content;
    private String priority;
    private String sourceType;
    private Long sourceId;
    private String targetMain;
    private String targetSub;
    private String targetParamsJson;
    private String state;
    private boolean unread;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private LocalDateTime handledAt;
    private LocalDateTime archivedAt;
    private LocalDateTime snoozedUntil;
    private LocalDateTime resolvedAt;

    public static NotificationResponse from(NotificationRecipient recipient) {
        NotificationResponse response = new NotificationResponse();
        response.id = recipient.getId();
        response.eventType = recipient.getEvent().getEventType();
        response.title = recipient.getEvent().getTitle();
        response.content = recipient.getEvent().getContent();
        response.priority = recipient.getEvent().getPriority();
        response.sourceType = recipient.getEvent().getSourceType();
        response.sourceId = recipient.getEvent().getSourceId();
        response.targetMain = recipient.getEvent().getTargetMain();
        response.targetSub = recipient.getEvent().getTargetSub();
        response.targetParamsJson = recipient.getEvent().getTargetParamsJson();
        response.state = recipient.getState().name();
        response.unread = recipient.getReadAt() == null;
        response.createdAt = recipient.getEvent().getCreatedAt();
        response.readAt = recipient.getReadAt();
        response.handledAt = recipient.getHandledAt();
        response.archivedAt = recipient.getArchivedAt();
        response.snoozedUntil = recipient.getSnoozedUntil();
        response.resolvedAt = recipient.getEvent().getResolvedAt();
        return response;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getPriority() { return priority; }
    public String getSourceType() { return sourceType; }
    public Long getSourceId() { return sourceId; }
    public String getTargetMain() { return targetMain; }
    public String getTargetSub() { return targetSub; }
    public String getTargetParamsJson() { return targetParamsJson; }
    public String getState() { return state; }
    public boolean isUnread() { return unread; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public LocalDateTime getSnoozedUntil() { return snoozedUntil; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
