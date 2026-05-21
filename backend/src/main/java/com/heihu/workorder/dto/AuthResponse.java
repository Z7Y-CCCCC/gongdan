package com.heihu.workorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthResponse {

    private String token;
    private UserResponse user;
    @JsonProperty("isSuperAdmin")
    private boolean isSuperAdmin;

    public AuthResponse() {
    }

    public AuthResponse(String token, UserResponse user) {
        this.token = token;
        this.user = user;
        this.isSuperAdmin = false;
    }

    public AuthResponse(String token, UserResponse user, boolean isSuperAdmin) {
        this.token = token;
        this.user = user;
        this.isSuperAdmin = isSuperAdmin;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }

    @JsonProperty("isSuperAdmin")
    public boolean isSuperAdmin() {
        return isSuperAdmin;
    }

    @JsonProperty("isSuperAdmin")
    public void setSuperAdmin(boolean isSuperAdmin) {
        this.isSuperAdmin = isSuperAdmin;
    }
}
