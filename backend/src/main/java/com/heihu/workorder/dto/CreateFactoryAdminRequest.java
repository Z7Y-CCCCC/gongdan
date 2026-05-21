package com.heihu.workorder.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CreateFactoryAdminRequest {

    @NotBlank(message = "工厂代码不能为空")
    private String factoryCode;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 64, message = "用户名长度需为1到64个字符")
    private String username;

    @Size(min = 6, message = "密码长度不能少于6位")
    private String password;

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
}
