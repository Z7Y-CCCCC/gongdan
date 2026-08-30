package com.heihu.workorder.dto;

import java.util.List;

public class PluginCatalogResponse {
    private Long id;
    private String pluginKey;
    private String name;
    private String description;
    private String pluginType;
    private String runtimeMode;
    private String status;
    private String currentStableVersion;
    private String minCoreVersion;
    private String manifestJson;
    private String factoryStatus;
    private String enabledVersion;
    private Integer rolloutPercent;
    private String healthStatus;
    private String healthMessage;
    private String lastHealthAt;
    private String configJson;
    private List<PluginVersionResponse> versions;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPluginKey() { return pluginKey; }
    public void setPluginKey(String pluginKey) { this.pluginKey = pluginKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPluginType() { return pluginType; }
    public void setPluginType(String pluginType) { this.pluginType = pluginType; }
    public String getRuntimeMode() { return runtimeMode; }
    public void setRuntimeMode(String runtimeMode) { this.runtimeMode = runtimeMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentStableVersion() { return currentStableVersion; }
    public void setCurrentStableVersion(String currentStableVersion) { this.currentStableVersion = currentStableVersion; }
    public String getMinCoreVersion() { return minCoreVersion; }
    public void setMinCoreVersion(String minCoreVersion) { this.minCoreVersion = minCoreVersion; }
    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }
    public String getFactoryStatus() { return factoryStatus; }
    public void setFactoryStatus(String factoryStatus) { this.factoryStatus = factoryStatus; }
    public String getEnabledVersion() { return enabledVersion; }
    public void setEnabledVersion(String enabledVersion) { this.enabledVersion = enabledVersion; }
    public Integer getRolloutPercent() { return rolloutPercent; }
    public void setRolloutPercent(Integer rolloutPercent) { this.rolloutPercent = rolloutPercent; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public String getHealthMessage() { return healthMessage; }
    public void setHealthMessage(String healthMessage) { this.healthMessage = healthMessage; }
    public String getLastHealthAt() { return lastHealthAt; }
    public void setLastHealthAt(String lastHealthAt) { this.lastHealthAt = lastHealthAt; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public List<PluginVersionResponse> getVersions() { return versions; }
    public void setVersions(List<PluginVersionResponse> versions) { this.versions = versions; }
}
