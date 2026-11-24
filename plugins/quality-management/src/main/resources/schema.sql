CREATE TABLE IF NOT EXISTS plugin_quality_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    factory_id VARCHAR(32) NOT NULL,
    operator_user_id VARCHAR(32) NOT NULL,
    issue VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(500) NOT NULL,
    created_at VARCHAR(35) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_plugin_quality_factory
    ON plugin_quality_record(factory_id, id);
