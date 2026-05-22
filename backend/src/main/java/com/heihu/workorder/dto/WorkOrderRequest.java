package com.heihu.workorder.dto;

import java.util.List;

public class WorkOrderRequest {

    private String code;
    private Long productId;
    private Integer plannedQuantity;
    private String status;
    private String priority;
    private String planStart;
    private String planEnd;
    private String relatedOrderCode;
    private String remark;
    private Integer goodQuantity;
    private Integer defectQuantity;
    private String defectRate;
    private Long categoryId;
    private List<CustomValueItem> customValues;
    private List<TaskItem> tasks;
    private List<MaterialItem> materials;
    private Long version;

    public static class CustomValueItem {
        private Long fieldId;
        private String fieldName;
        private String fieldType;
        private Object value;

        public Long getFieldId() {
            return fieldId;
        }

        public void setFieldId(Long fieldId) {
            this.fieldId = fieldId;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldType() {
            return fieldType;
        }

        public void setFieldType(String fieldType) {
            this.fieldType = fieldType;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }

    public static class TaskItem {
        private String processName;
        private String processCode;
        private Integer sequenceNo;
        private Integer plannedQuantity;
        private Integer completedQuantity;
        private Integer defectQuantity;
        private Integer approvedQuantity;
        private Integer unapprovedQuantity;
        private String lastReportTime;
        private String planDeadline;
        private String reportPermission;
        private String assignee;
        private Boolean enabled;

        public String getProcessName() {
            return processName;
        }

        public void setProcessName(String processName) {
            this.processName = processName;
        }

        public String getProcessCode() {
            return processCode;
        }

        public void setProcessCode(String processCode) {
            this.processCode = processCode;
        }

        public Integer getSequenceNo() {
            return sequenceNo;
        }

        public void setSequenceNo(Integer sequenceNo) {
            this.sequenceNo = sequenceNo;
        }

        public Integer getPlannedQuantity() {
            return plannedQuantity;
        }

        public void setPlannedQuantity(Integer plannedQuantity) {
            this.plannedQuantity = plannedQuantity;
        }

        public Integer getCompletedQuantity() {
            return completedQuantity;
        }

        public void setCompletedQuantity(Integer completedQuantity) {
            this.completedQuantity = completedQuantity;
        }

        public Integer getDefectQuantity() {
            return defectQuantity;
        }

        public void setDefectQuantity(Integer defectQuantity) {
            this.defectQuantity = defectQuantity;
        }

        public Integer getApprovedQuantity() {
            return approvedQuantity;
        }

        public void setApprovedQuantity(Integer approvedQuantity) {
            this.approvedQuantity = approvedQuantity;
        }

        public Integer getUnapprovedQuantity() {
            return unapprovedQuantity;
        }

        public void setUnapprovedQuantity(Integer unapprovedQuantity) {
            this.unapprovedQuantity = unapprovedQuantity;
        }

        public String getLastReportTime() {
            return lastReportTime;
        }

        public void setLastReportTime(String lastReportTime) {
            this.lastReportTime = lastReportTime;
        }

        public String getPlanDeadline() {
            return planDeadline;
        }

        public void setPlanDeadline(String planDeadline) {
            this.planDeadline = planDeadline;
        }

        public String getReportPermission() {
            return reportPermission;
        }

        public void setReportPermission(String reportPermission) {
            this.reportPermission = reportPermission;
        }

        public String getAssignee() {
            return assignee;
        }

        public void setAssignee(String assignee) {
            this.assignee = assignee;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class MaterialItem {
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private Integer quantity;

        public String getMaterialCode() {
            return materialCode;
        }

        public void setMaterialCode(String materialCode) {
            this.materialCode = materialCode;
        }

        public String getMaterialName() {
            return materialName;
        }

        public void setMaterialName(String materialName) {
            this.materialName = materialName;
        }

        public String getMaterialSpec() {
            return materialSpec;
        }

        public void setMaterialSpec(String materialSpec) {
            this.materialSpec = materialSpec;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
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

    public Integer getPlannedQuantity() {
        return plannedQuantity;
    }

    public void setPlannedQuantity(Integer plannedQuantity) {
        this.plannedQuantity = plannedQuantity;
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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public List<CustomValueItem> getCustomValues() {
        return customValues;
    }

    public void setCustomValues(List<CustomValueItem> customValues) {
        this.customValues = customValues;
    }

    public List<TaskItem> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskItem> tasks) {
        this.tasks = tasks;
    }

    public List<MaterialItem> getMaterials() {
        return materials;
    }

    public void setMaterials(List<MaterialItem> materials) {
        this.materials = materials;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
