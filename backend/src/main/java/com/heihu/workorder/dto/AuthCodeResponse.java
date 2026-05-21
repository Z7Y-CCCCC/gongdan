package com.heihu.workorder.dto;

import java.time.LocalDateTime;

public class AuthCodeResponse {

    private String code;
    private LocalDateTime expiresAt;
    private String factoryCode;

    public AuthCodeResponse() {
    }

    public AuthCodeResponse(String code, LocalDateTime expiresAt, String factoryCode) {
        this.code = code;
        this.expiresAt = expiresAt;
        this.factoryCode = factoryCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getFactoryCode() {
        return factoryCode;
    }

    public void setFactoryCode(String factoryCode) {
        this.factoryCode = factoryCode;
    }
}
