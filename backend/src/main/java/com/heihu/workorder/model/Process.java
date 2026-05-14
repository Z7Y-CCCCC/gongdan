package com.heihu.workorder.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_process_code", columnNames = {"factory_id", "code"})
        })
public class Process {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    // 报工权限，逗号分隔存储
    @Column(name = "report_permission", length = 1024)
    private String reportPermission;

    @Column(name = "report_ratio", nullable = false)
    private Integer reportRatio = 1;

    // 不良品项ID，逗号分隔
    @Column(name = "defect_item_ids", length = 512)
    private String defectItemIds;

    @Column(name = "task_punch_in", nullable = false)
    private Boolean taskPunchIn = false;

    @Column(name = "good_quantity", length = 64)
    private String goodQuantity;

    @Column(name = "machine_name", length = 128)
    private String machineName;

    @Column(name = "feed_count", length = 64)
    private String feedCount;

    @Column(name = "corresponding_machine", length = 128)
    private String correspondingMachine;

    @Column(length = 512)
    private String requirements;

    // 采集数据，JSON数组存储，格式: [{"type":"...","name":"..."}]
    @Lob
    @Column(name = "collect_data")
    private String collectData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private FactoryUser creator;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Factory getFactory() { return factory; }
    public void setFactory(Factory factory) { this.factory = factory; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getReportPermission() { return reportPermission; }
    public void setReportPermission(String reportPermission) { this.reportPermission = reportPermission; }

    public Integer getReportRatio() { return reportRatio; }
    public void setReportRatio(Integer reportRatio) { this.reportRatio = reportRatio; }

    public String getDefectItemIds() { return defectItemIds; }
    public void setDefectItemIds(String defectItemIds) { this.defectItemIds = defectItemIds; }

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

    public String getCollectData() { return collectData; }
    public void setCollectData(String collectData) { this.collectData = collectData; }

    public FactoryUser getCreator() { return creator; }
    public void setCreator(FactoryUser creator) { this.creator = creator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
