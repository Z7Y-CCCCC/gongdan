package com.heihu.workorder.dto;

import javax.validation.constraints.NotNull;

public class UpdateStatusRequest {

    @NotNull(message = "状态不能为空")
    private Boolean enabled;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
