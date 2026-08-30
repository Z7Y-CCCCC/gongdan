package com.heihu.workorder.dto;

public class PluginCreateRequest {
    private String pluginKey;
    private String name;
    private String description;
    private String pluginType;
    private String runtimeMode;
    private String minCoreVersion;
    private String manifestJson;

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
    public String getMinCoreVersion() { return minCoreVersion; }
    public void setMinCoreVersion(String minCoreVersion) { this.minCoreVersion = minCoreVersion; }
    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }
}
