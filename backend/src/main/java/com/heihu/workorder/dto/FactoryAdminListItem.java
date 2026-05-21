package com.heihu.workorder.dto;

import java.time.LocalDateTime;

public class FactoryAdminListItem {

    private Long id;
    private String factoryCode;
    private String username;
    private boolean enabled;
    private LocalDateTime createdAt;

    public FactoryAdminListItem() {
    }

    public FactoryAdminListItem(Long id, String factoryCode, String username, boolean enabled, LocalDateTime createdAt) {
        this.id = id;
        this.factoryCode = factoryCode;
        this.username = username;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
