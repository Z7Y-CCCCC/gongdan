package com.heihu.workorder.model;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "factory_plugins", uniqueConstraints = {
        @UniqueConstraint(name = "uk_factory_plugin", columnNames = {"factory_id", "plugin_id"})
})
public class FactoryPlugin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    @Column(name = "plugin_id", nullable = false)
    private Long pluginId;

    @Column(name = "enabled_version", length = 64)
    private String enabledVersion;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "rollout_percent", nullable = false)
    private Integer rolloutPercent = 100;

    @Lob
    @Column(name = "config_json")
    private String configJson;

    @Column(name = "health_status", nullable = false, length = 32)
    private String healthStatus = "UNKNOWN";

    @Column(name = "health_message", length = 500)
    private String healthMessage;

    @Column(name = "last_health_at")
    private LocalDateTime lastHealthAt;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFactoryId() { return factoryId; }
    public void setFactoryId(Long factoryId) { this.factoryId = factoryId; }
    public Long getPluginId() { return pluginId; }
    public void setPluginId(Long pluginId) { this.pluginId = pluginId; }
    public String getEnabledVersion() { return enabledVersion; }
    public void setEnabledVersion(String enabledVersion) { this.enabledVersion = enabledVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRolloutPercent() { return rolloutPercent; }
    public void setRolloutPercent(Integer rolloutPercent) { this.rolloutPercent = rolloutPercent; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public String getHealthMessage() { return healthMessage; }
    public void setHealthMessage(String healthMessage) { this.healthMessage = healthMessage; }
    public LocalDateTime getLastHealthAt() { return lastHealthAt; }
    public void setLastHealthAt(LocalDateTime lastHealthAt) { this.lastHealthAt = lastHealthAt; }
    public LocalDateTime getEnabledAt() { return enabledAt; }
    public void setEnabledAt(LocalDateTime enabledAt) { this.enabledAt = enabledAt; }
    public LocalDateTime getDisabledAt() { return disabledAt; }
    public void setDisabledAt(LocalDateTime disabledAt) { this.disabledAt = disabledAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
