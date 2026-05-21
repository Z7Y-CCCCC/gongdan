package com.heihu.workorder.dto;

import java.time.LocalDateTime;

public class FactoryAdminResponse {

    private String factoryCode;
    private String username;
    private String password;
    private boolean enabled;
    private LocalDateTime createdAt;

    public FactoryAdminResponse() {
    }

    public FactoryAdminResponse(String factoryCode, String username, String password, boolean enabled, LocalDateTime createdAt) {
        this.factoryCode = factoryCode;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public String getFactoryCode() {
        return factoryCode;
    }

    public void setFactoryCode(String factoryCode) {
        this.factoryCode = factoryCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
