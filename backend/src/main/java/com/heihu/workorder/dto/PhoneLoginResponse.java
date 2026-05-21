package com.heihu.workorder.dto;

import java.util.List;

public class PhoneLoginResponse {

    private boolean selectRequired;
    private boolean bindingRequired;  // 是否需要绑定工厂
    private String token;
    private String phone;             // 需要绑定的手机号（bindingRequired=true 时返回）
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

    public boolean isBindingRequired() {
        return bindingRequired;
    }

    public void setBindingRequired(boolean bindingRequired) {
        this.bindingRequired = bindingRequired;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
