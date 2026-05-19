package com.heihu.workorder.dto;

import java.util.List;

public class PhoneLoginResponse {

    private boolean selectRequired;
    private String token;
    private UserResponse user;
    private List<FactoryOption> factories;

    public PhoneLoginResponse() {
    }

    public boolean isSelectRequired() {
        return selectRequired;
    }

    public void setSelectRequired(boolean selectRequired) {
        this.selectRequired = selectRequired;
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

    public List<FactoryOption> getFactories() {
        return factories;
    }

    public void setFactories(List<FactoryOption> factories) {
        this.factories = factories;
    }
}
