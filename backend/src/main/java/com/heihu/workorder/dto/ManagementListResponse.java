package com.heihu.workorder.dto;

import java.util.List;

public class ManagementListResponse {

    private List<FactoryListItem> factories;
    private List<FactoryAdminListItem> factoryAdmins;
    private List<AuthCodeListItem> authCodes;

    public ManagementListResponse() {
    }

    public ManagementListResponse(List<FactoryListItem> factories, List<FactoryAdminListItem> factoryAdmins, List<AuthCodeListItem> authCodes) {
        this.factories = factories;
        this.factoryAdmins = factoryAdmins;
        this.authCodes = authCodes;
    }

    public List<FactoryListItem> getFactories() {
        return factories;
    }

    public void setFactories(List<FactoryListItem> factories) {
        this.factories = factories;
    }

    public List<FactoryAdminListItem> getFactoryAdmins() {
        return factoryAdmins;
    }

    public void setFactoryAdmins(List<FactoryAdminListItem> factoryAdmins) {
        this.factoryAdmins = factoryAdmins;
    }

    public List<AuthCodeListItem> getAuthCodes() {
        return authCodes;
    }

    public void setAuthCodes(List<AuthCodeListItem> authCodes) {
        this.authCodes = authCodes;
    }
}
