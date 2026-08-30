package com.heihu.workorder.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class WorkOrderCategoryRequest {

    @NotBlank(message = "工单分类名称不能为空")
    @Size(max = 64, message = "工单分类名称不能超过64个字符")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
