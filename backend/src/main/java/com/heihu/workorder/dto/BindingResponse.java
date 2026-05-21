package com.heihu.workorder.dto;

public class BindingResponse {

    private String token;
    private UserResponse user;
    private String defaultPassword;

    public BindingResponse() {
    }

    public BindingResponse(String token, UserResponse user, String defaultPassword) {
        this.token = token;
        this.user = user;
        this.defaultPassword = defaultPassword;
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

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public void setDefaultPassword(String defaultPassword) {
        this.defaultPassword = defaultPassword;
    }
}
