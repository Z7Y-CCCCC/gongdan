package com.heihu.workorder.dto;

import javax.validation.constraints.NotBlank;

public class BindFactoryRequest {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "工厂代码不能为空")
    private String factoryCode;

    @NotBlank(message = "授权码不能为空")
    private String authorizationCode;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFactoryCode() {
        return factoryCode;
    }

    public void setFactoryCode(String factoryCode) {
        this.factoryCode = factoryCode;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }
}
