package com.heihu.workorder.dto;

public class ProductRequest {
    private String code;
    private String name;
    private Long unitId;
    private String spec;
    private String attribute;
    private Long routeId;
    private String supplierName;
    private String maxStock;
    private String minStock;
    private String safetyStock;
    private Integer inventoryQty;
    private Long version;
    private String costPrice;
    private String salePrice;
    private Boolean keyPart;
    private String manufactureMode;
    private String quantity;
    private String fixedWeight;
    private String customerName;
    private String productType;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
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
}
