package com.heihu.workorder.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UpdateFactoryRequest {

    @NotBlank(message = "工厂名称不能为空")
    @Size(max = 128, message = "工厂名称不能超过128个字符")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
