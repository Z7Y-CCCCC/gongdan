package com.heihu.workorder.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "work_orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_work_order_code", columnNames = {"factory_id", "code"})
        }
)
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @Column(nullable = false, length = 64)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_code", length = 64)
    private String productCode;

    @Column(name = "product_name", length = 128)
    private String productName;

    @Column(name = "product_spec", length = 128)
    private String productSpec;

    @Column(name = "planned_quantity", nullable = false)
    private Integer plannedQuantity = 0;

    @Column(name = "good_quantity", nullable = false)
    private Integer goodQuantity = 0;

    @Column(name = "defect_quantity", nullable = false)
    private Integer defectQuantity = 0;

    @Column(name = "defect_rate", length = 32)
    private String defectRate;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 32)
    private String priority;

    @Column(name = "plan_start")
    private LocalDateTime planStart;

    @Column(name = "plan_end")
    private LocalDateTime planEnd;

    @Column(name = "related_order_code", length = 64)
    private String relatedOrderCode;

    @Column(length = 500)
    private String remark;

    @Lob
    @Column(name = "custom_data_json")
    private String customDataJson;

    @Lob
    @Column(name = "task_data_json")
    private String taskDataJson;

    @Lob
    @Column(name = "material_data_json")
    private String materialDataJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private FactoryUser creator;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Factory getFactory() {
        return factory;
    }

    public void setFactory(Factory factory) {
        this.factory = factory;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
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

    public LocalDateTime getPlanStart() {
        return planStart;
    }

    public void setPlanStart(LocalDateTime planStart) {
        this.planStart = planStart;
    }

    public LocalDateTime getPlanEnd() {
        return planEnd;
    }

    public void setPlanEnd(LocalDateTime planEnd) {
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

    public String getCustomDataJson() {
        return customDataJson;
    }

    public void setCustomDataJson(String customDataJson) {
        this.customDataJson = customDataJson;
    }

    public String getTaskDataJson() {
        return taskDataJson;
    }

    public void setTaskDataJson(String taskDataJson) {
        this.taskDataJson = taskDataJson;
    }

    public String getMaterialDataJson() {
        return materialDataJson;
    }

    public void setMaterialDataJson(String materialDataJson) {
        this.materialDataJson = materialDataJson;
    }

    public FactoryUser getCreator() {
        return creator;
    }

    public void setCreator(FactoryUser creator) {
        this.creator = creator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
