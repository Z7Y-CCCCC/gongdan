package com.heihu.workorder.dto;

import java.util.List;

public class ProcessRouteResponse {
    private Long id;
    private String code;
    private String name;
    private List<RouteStepItem> steps;
    private String creator;
    private String createdAt;

    public static class RouteStepItem {
        private String type;
        private Long processId;
        private Integer reportRatio;
        private String remark;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getProcessId() { return processId; }
        public void setProcessId(Long processId) { this.processId = processId; }
        public Integer getReportRatio() { return reportRatio; }
        public void setReportRatio(Integer reportRatio) { this.reportRatio = reportRatio; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<RouteStepItem> getSteps() { return steps; }
    public void setSteps(List<RouteStepItem> steps) { this.steps = steps; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
