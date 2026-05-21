package com.heihu.workorder.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class GenerateAuthCodeRequest {

    @NotBlank(message = "工厂代码不能为空")
    private String factoryCode;

    @Min(value = 1, message = "有效期必须在1-1440分钟之间")
    @Max(value = 1440, message = "有效期必须在1-1440分钟之间")
    private Integer expirationMinutes;

    public String getFactoryCode() {
        return factoryCode;
    }

    public void setFactoryCode(String factoryCode) {
        this.factoryCode = factoryCode;
    }

    public Integer getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(Integer expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
