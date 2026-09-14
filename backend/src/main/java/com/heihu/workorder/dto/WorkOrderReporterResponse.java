package com.heihu.workorder.dto;

import com.heihu.workorder.model.FactoryUser;

public class WorkOrderReporterResponse {
    private final Long id;
    private final String name;
    private final String username;
    private final String departmentName;

    public WorkOrderReporterResponse(FactoryUser user) {
        id = user.getId();
        name = user.getName();
        username = user.getUsername();
        departmentName = user.getDepartment() == null ? null : user.getDepartment().getName();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getDepartmentName() { return departmentName; }
}
