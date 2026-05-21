package com.heihu.workorder.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class CreateFactoryRequest {

    @NotBlank(message = "工厂代码不能为空")
    @Pattern(regexp = "^[A-Z0-9]{1,16}$", message = "工厂代码格式无效，需为1-16位大写字母和数字")
    private String factoryCode;

    @NotBlank(message = "工厂名称不能为空")
    @Size(min = 1, max = 128, message = "工厂名称长度需为1到128个字符")
    private String factoryName;

    public String getFactoryCode() {
        return factoryCode;
    }

    public void setFactoryCode(String factoryCode) {
        this.factoryCode = factoryCode;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public void setFactoryName(String factoryName) {
        this.factoryName = factoryName;
    }
}
