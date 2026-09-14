CREATE TABLE IF NOT EXISTS external_data_sources (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    factory_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    config_json TEXT NOT NULL,
    secret_cipher TEXT,
    enabled BIT NOT NULL DEFAULT 0,
    archived BIT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_external_source_code UNIQUE (factory_id, code),
    CONSTRAINT uk_external_source_tenant_id UNIQUE (factory_id, id),
    CONSTRAINT fk_external_source_factory FOREIGN KEY (factory_id) REFERENCES factories(id)
);
CREATE TABLE IF NOT EXISTS external_data_source_checks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    factory_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    config_version BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(32),
    message VARCHAR(300),
    http_status INT,
    duration_ms BIGINT,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6),
    trace_id VARCHAR(36) NOT NULL,
    CONSTRAINT fk_external_check_source FOREIGN KEY (factory_id, source_id) REFERENCES external_data_sources(factory_id, id)
);
CREATE INDEX idx_source_check_history ON external_data_source_checks(factory_id, source_id, id);
CREATE INDEX idx_source_check_retention ON external_data_source_checks(started_at);
