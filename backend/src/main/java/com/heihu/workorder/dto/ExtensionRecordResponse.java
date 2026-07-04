package com.heihu.workorder.dto;

public class ExtensionRecordResponse {
    private Long id;
    private String moduleName;
    private String businessKey;
    private String title;
    private String status;
    private String payloadJson;
    private Long createdById;
    private String createdByName;
    private String createdAt;
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long createdById) { this.createdById = createdById; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
