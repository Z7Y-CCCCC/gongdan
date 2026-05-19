package com.heihu.workorder.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class SmsSendRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
