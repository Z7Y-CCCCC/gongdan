package com.heihu.workorder.dto;

public class PluginActivationRequest {
    private String version;
    private Integer rolloutPercent;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Integer getRolloutPercent() { return rolloutPercent; }
    public void setRolloutPercent(Integer rolloutPercent) { this.rolloutPercent = rolloutPercent; }
}
