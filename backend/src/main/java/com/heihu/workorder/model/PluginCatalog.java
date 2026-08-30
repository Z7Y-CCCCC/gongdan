package com.heihu.workorder.model;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "plugin_catalog")
public class PluginCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plugin_key", nullable = false, unique = true, length = 64)
    private String pluginKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "plugin_type", nullable = false, length = 32)
    private String pluginType;

    @Column(name = "runtime_mode", nullable = false, length = 32)
    private String runtimeMode;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "current_stable_version", length = 64)
    private String currentStableVersion;

    @Column(name = "min_core_version", length = 64)
    private String minCoreVersion;

    @Lob
    @Column(name = "manifest_json")
    private String manifestJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
