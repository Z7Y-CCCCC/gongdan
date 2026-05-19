package com.heihu.workorder.dto;

public class FactoryOption {

    private Long factoryId;
    private String factoryCode;
    private String factoryName;

    public FactoryOption() {
    }

    public FactoryOption(Long factoryId, String factoryCode, String factoryName) {
        this.factoryId = factoryId;
        this.factoryCode = factoryCode;
        this.factoryName = factoryName;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public String getFactoryCode() {
        return factoryCode;
    }

    public void setFactoryCode(String factoryCode) {
        this.factoryCode = factoryCode;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public void setFactoryName(String factoryName) {
        this.factoryName = factoryName;
    }
}
