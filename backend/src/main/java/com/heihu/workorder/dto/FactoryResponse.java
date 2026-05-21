package com.heihu.workorder.dto;

import java.time.LocalDateTime;

public class FactoryResponse {

    private String factoryCode;
    private String factoryName;
    private boolean enabled;
    private LocalDateTime createdAt;

    public FactoryResponse() {
    }

    public FactoryResponse(String factoryCode, String factoryName, boolean enabled, LocalDateTime createdAt) {
        this.factoryCode = factoryCode;
        this.factoryName = factoryName;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public String getFactoryCode() {
        return factoryCode;
    }

    public void setFactoryCode(String factoryCode) {
        this.factoryCode = factoryCode;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public void setFactoryName(String factoryName) {
        this.factoryName = factoryName;
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
