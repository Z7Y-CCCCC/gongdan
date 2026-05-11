package com.heihu.workorder.service;

import com.heihu.workorder.model.Role;

public class AuthenticatedUser {

    private final Long userId;
    private final Long factoryId;
    private final Role role;
    private final String name;

    public AuthenticatedUser(Long userId, Long factoryId, Role role, String name) {
        this.userId = userId;
        this.factoryId = factoryId;
        this.role = role;
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public Role getRole() {
        return role;
    }

    public String getName() {
        return name;
    }
}
