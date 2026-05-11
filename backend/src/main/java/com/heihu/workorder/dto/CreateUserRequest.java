package com.heihu.workorder.dto;

import com.heihu.workorder.model.Role;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CreateUserRequest {

    @NotBlank(message = "账号不能为空")
    @Size(max = 64, message = "账号不能超过64个字符")
    private String username;

    @Size(max = 32, message = "手机号不能超过32个字符")
    private String phone;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过64个字符")
    private String name;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为6到64个字符")
    private String password;

    @NotNull(message = "角色不能为空")
    private Role role;

    private Long departmentId;

    private Boolean virtualUser;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Boolean getVirtualUser() {
        return virtualUser;
    }

    public void setVirtualUser(Boolean virtualUser) {
        this.virtualUser = virtualUser;
    }
}
