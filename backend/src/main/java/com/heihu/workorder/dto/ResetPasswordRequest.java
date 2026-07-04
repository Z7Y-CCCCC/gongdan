package com.heihu.workorder.dto;

import javax.validation.constraints.Size;

public class ResetPasswordRequest {

    @Size(min = 6, max = 64, message = "密码长度需为6到64个字符")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
