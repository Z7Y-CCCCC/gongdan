package com.heihu.workorder.dto;

import com.heihu.workorder.model.Role;
import javax.validation.constraints.NotNull;

public class UpdatePermissionRequest {

    @NotNull(message = "角色不能为空")
    private Role role;

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
