package com.heihu.workorder.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_code", columnNames = {"factory_id", "code"})
        })
public class Product {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(length = 128)
    private String spec;

    @Column(nullable = false, length = 32)
    private String attribute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private ProcessRoute processRoute;

    @Column(length = 128)
    private String supplierName;

    @Column(length = 32)
    private String maxStock;

    @Column(length = 32)
    private String minStock;

    @Column(length = 32)
    private String safetyStock;

    @Column(nullable = false)
    private Integer inventoryQty = 0;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(length = 32)
    private String costPrice;

    @Column(length = 32)
    private String salePrice;

    @Column(nullable = false)
    private Boolean keyPart = Boolean.FALSE;

    @Column(length = 64)
    private String manufactureMode;

    @Column(length = 32)
    private String quantity;

    @Column(length = 32)
    private String fixedWeight;

    @Column(length = 128)
    private String customerName;

    @Column(length = 64)
    private String productType;

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

    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }

    public ProcessRoute getProcessRoute() { return processRoute; }
    public void setProcessRoute(ProcessRoute processRoute) { this.processRoute = processRoute; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getMaxStock() { return maxStock; }
    public void setMaxStock(String maxStock) { this.maxStock = maxStock; }

    public String getMinStock() { return minStock; }
    public void setMinStock(String minStock) { this.minStock = minStock; }

    public String getSafetyStock() { return safetyStock; }
    public void setSafetyStock(String safetyStock) { this.safetyStock = safetyStock; }

    public Integer getInventoryQty() { return inventoryQty; }
    public void setInventoryQty(Integer inventoryQty) { this.inventoryQty = inventoryQty; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getCostPrice() { return costPrice; }
    public void setCostPrice(String costPrice) { this.costPrice = costPrice; }

    public String getSalePrice() { return salePrice; }
    public void setSalePrice(String salePrice) { this.salePrice = salePrice; }

    public Boolean getKeyPart() { return keyPart; }
    public void setKeyPart(Boolean keyPart) { this.keyPart = keyPart; }

    public String getManufactureMode() { return manufactureMode; }
    public void setManufactureMode(String manufactureMode) { this.manufactureMode = manufactureMode; }

    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }

    public String getFixedWeight() { return fixedWeight; }
    public void setFixedWeight(String fixedWeight) { this.fixedWeight = fixedWeight; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public FactoryUser getCreator() { return creator; }
    public void setCreator(FactoryUser creator) { this.creator = creator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
