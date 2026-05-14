package com.heihu.workorder.dto;

import java.util.List;

public class WorkOrderResponse {

    private Long id;
    private String code;
    private Long productId;
    private String productCode;
    private String productName;
    private String productSpec;
    private Integer plannedQuantity;
    private Integer goodQuantity;
    private Integer defectQuantity;
    private String defectRate;
    private String status;
    private String priority;
    private String routeName;
    private Integer progressPercent;
    private String planStart;
    private String planEnd;
    private String relatedOrderCode;
    private String remark;
    private List<WorkOrderRequest.CustomValueItem> customValues;
    private List<WorkOrderRequest.TaskItem> tasks;
    private List<WorkOrderRequest.MaterialItem> materials;
    private String creator;
    private String createdAt;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductSpec() {
        return productSpec;
    }

    public void setProductSpec(String productSpec) {
        this.productSpec = productSpec;
    }

    public Integer getPlannedQuantity() {
        return plannedQuantity;
    }

    public void setPlannedQuantity(Integer plannedQuantity) {
        this.plannedQuantity = plannedQuantity;
    }

    public Integer getGoodQuantity() {
        return goodQuantity;
    }

    public void setGoodQuantity(Integer goodQuantity) {
        this.goodQuantity = goodQuantity;
    }

    public Integer getDefectQuantity() {
        return defectQuantity;
    }

    public void setDefectQuantity(Integer defectQuantity) {
        this.defectQuantity = defectQuantity;
    }

    public String getDefectRate() {
        return defectRate;
    }

    public void setDefectRate(String defectRate) {
        this.defectRate = defectRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getPlanStart() {
        return planStart;
    }

    public void setPlanStart(String planStart) {
        this.planStart = planStart;
    }

    public String getPlanEnd() {
        return planEnd;
    }

    public void setPlanEnd(String planEnd) {
        this.planEnd = planEnd;
    }

    public String getRelatedOrderCode() {
        return relatedOrderCode;
    }

    public void setRelatedOrderCode(String relatedOrderCode) {
        this.relatedOrderCode = relatedOrderCode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<WorkOrderRequest.CustomValueItem> getCustomValues() {
        return customValues;
    }

    public void setCustomValues(List<WorkOrderRequest.CustomValueItem> customValues) {
        this.customValues = customValues;
    }

    public List<WorkOrderRequest.TaskItem> getTasks() {
        return tasks;
    }

    public void setTasks(List<WorkOrderRequest.TaskItem> tasks) {
        this.tasks = tasks;
    }

    public List<WorkOrderRequest.MaterialItem> getMaterials() {
        return materials;
    }

    public void setMaterials(List<WorkOrderRequest.MaterialItem> materials) {
        this.materials = materials;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
