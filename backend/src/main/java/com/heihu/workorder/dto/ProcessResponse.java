package com.heihu.workorder.dto;

import java.util.List;

public class ProcessResponse {
    private Long id;
    private String code;
    private String name;
    private List<String> reportPermission;
    private Integer reportRatio;
    private List<Long> defectItemIds;
    private Boolean taskPunchIn;
    private String goodQuantity;
    private String machineName;
    private String feedCount;
    private String correspondingMachine;
    private String requirements;
    private List<CollectDataItem> collectData;
    private String creator;
    private String createdAt;

    public static class CollectDataItem {
        private String type;
        private String name;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getReportPermission() { return reportPermission; }
    public void setReportPermission(List<String> reportPermission) { this.reportPermission = reportPermission; }
    public Integer getReportRatio() { return reportRatio; }
    public void setReportRatio(Integer reportRatio) { this.reportRatio = reportRatio; }
    public List<Long> getDefectItemIds() { return defectItemIds; }
    public void setDefectItemIds(List<Long> defectItemIds) { this.defectItemIds = defectItemIds; }
    public Boolean getTaskPunchIn() { return taskPunchIn; }
    public void setTaskPunchIn(Boolean taskPunchIn) { this.taskPunchIn = taskPunchIn; }
    public String getGoodQuantity() { return goodQuantity; }
    public void setGoodQuantity(String goodQuantity) { this.goodQuantity = goodQuantity; }
    public String getMachineName() { return machineName; }
    public void setMachineName(String machineName) { this.machineName = machineName; }
    public String getFeedCount() { return feedCount; }
    public void setFeedCount(String feedCount) { this.feedCount = feedCount; }
    public String getCorrespondingMachine() { return correspondingMachine; }
    public void setCorrespondingMachine(String correspondingMachine) { this.correspondingMachine = correspondingMachine; }
    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }
    public List<CollectDataItem> getCollectData() { return collectData; }
    public void setCollectData(List<CollectDataItem> collectData) { this.collectData = collectData; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
