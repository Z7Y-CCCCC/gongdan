package com.heihu.workorder.dto;

import com.heihu.workorder.model.UserSession;
import java.time.LocalDateTime;

public class InternalChatDeviceResponse {

    private Long sessionId;
    private String deviceLabel;
    private String operatingSystem;
    private String browser;
    private String ipAddress;
    private String loginLocation;
    private LocalDateTime loginAt;
    private LocalDateTime lastActiveAt;

    public InternalChatDeviceResponse(UserSession session) {
        this.sessionId = session.getId();
        this.operatingSystem = valueOrDefault(session.getOperatingSystem(), "未知系统");
        this.browser = valueOrDefault(session.getBrowser(), "未知浏览器");
        this.deviceLabel = this.operatingSystem + " · " + this.browser;
        this.ipAddress = valueOrDefault(session.getIpAddress(), "-");
        this.loginLocation = valueOrDefault(session.getLoginLocation(), "内网IP");
        this.loginAt = session.getCreatedAt();
        this.lastActiveAt = session.getLastActiveAt() == null ? session.getCreatedAt() : session.getLastActiveAt();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public Long getSessionId() { return sessionId; }
    public String getDeviceLabel() { return deviceLabel; }
    public String getOperatingSystem() { return operatingSystem; }
    public String getBrowser() { return browser; }
    public String getIpAddress() { return ipAddress; }
    public String getLoginLocation() { return loginLocation; }
    public LocalDateTime getLoginAt() { return loginAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
}
