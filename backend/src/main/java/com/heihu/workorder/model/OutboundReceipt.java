package com.heihu.workorder.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "outbound_receipts")
public class OutboundReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 32)
    private String outboundType;

    @Column(nullable = false)
    private LocalDateTime outboundTime;

    @Column(length = 512)
    private String remark;

    @Lob
    @Column(name = "custom_data_json")
    private String customDataJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private FactoryUser creator;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "outboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutboundReceiptItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Factory getFactory() { return factory; }
    public void setFactory(Factory factory) { this.factory = factory; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getOutboundType() { return outboundType; }
    public void setOutboundType(String outboundType) { this.outboundType = outboundType; }

    public LocalDateTime getOutboundTime() { return outboundTime; }
    public void setOutboundTime(LocalDateTime outboundTime) { this.outboundTime = outboundTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCustomDataJson() { return customDataJson; }
    public void setCustomDataJson(String customDataJson) { this.customDataJson = customDataJson; }

    public FactoryUser getCreator() { return creator; }
    public void setCreator(FactoryUser creator) { this.creator = creator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<OutboundReceiptItem> getItems() { return items; }
    public void setItems(List<OutboundReceiptItem> items) { this.items = items; }
}
