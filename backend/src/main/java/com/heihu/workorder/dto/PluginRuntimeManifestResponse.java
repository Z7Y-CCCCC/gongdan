package com.heihu.workorder.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class PluginRuntimeManifestResponse {
    private String pluginKey;
    private String name;
    private String version;
    private String runtimeMode;
    private JsonNode manifest;
    private JsonNode config;

    public String getPluginKey() { return pluginKey; }
    public void setPluginKey(String pluginKey) { this.pluginKey = pluginKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getRuntimeMode() { return runtimeMode; }
    public void setRuntimeMode(String runtimeMode) { this.runtimeMode = runtimeMode; }
    public JsonNode getManifest() { return manifest; }
    public void setManifest(JsonNode manifest) { this.manifest = manifest; }
    public JsonNode getConfig() { return config; }
    public void setConfig(JsonNode config) { this.config = config; }
}
