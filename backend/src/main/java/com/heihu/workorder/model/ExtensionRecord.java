package com.heihu.workorder.model;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "extension_records")
public class ExtensionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @Column(name = "module_name", nullable = false, length = 32)
    private String moduleName;

    @Column(name = "business_key", length = 128)
    private String businessKey;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private FactoryUser createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Factory getFactory() { return factory; }
    public void setFactory(Factory factory) { this.factory = factory; }
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
    public FactoryUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(FactoryUser createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
