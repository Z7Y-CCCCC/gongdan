CREATE TABLE IF NOT EXISTS plugin_catalog (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plugin_key VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    plugin_type VARCHAR(32) NOT NULL,
    runtime_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_stable_version VARCHAR(64),
    min_core_version VARCHAR(64),
    manifest_json TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plugin_catalog_key (plugin_key)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plugin_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plugin_id BIGINT NOT NULL,
    version VARCHAR(64) NOT NULL,
    artifact_url VARCHAR(512),
    image_name VARCHAR(255),
    checksum VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    release_notes TEXT,
    manifest_json TEXT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plugin_version (plugin_id, version),
    CONSTRAINT fk_plugin_version_catalog FOREIGN KEY (plugin_id) REFERENCES plugin_catalog (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS factory_plugins (
    id BIGINT NOT NULL AUTO_INCREMENT,
    factory_id BIGINT NOT NULL,
    plugin_id BIGINT NOT NULL,
    enabled_version VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    rollout_percent INT NOT NULL DEFAULT 100,
    config_json TEXT,
    health_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    health_message VARCHAR(500),
    last_health_at DATETIME(6),
    enabled_at DATETIME(6),
    disabled_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_factory_plugin (factory_id, plugin_id),
    CONSTRAINT fk_factory_plugin_factory FOREIGN KEY (factory_id) REFERENCES factories (id),
    CONSTRAINT fk_factory_plugin_catalog FOREIGN KEY (plugin_id) REFERENCES plugin_catalog (id)
) ENGINE=InnoDB;

CREATE INDEX idx_plugin_version_created_at ON plugin_versions (plugin_id, created_at);
CREATE INDEX idx_factory_plugin_status ON factory_plugins (factory_id, status);
