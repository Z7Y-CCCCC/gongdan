package com.heihu.workorder.model;

public enum Role {
    ADMIN("管理员"),
    PRODUCTION("生产人员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
