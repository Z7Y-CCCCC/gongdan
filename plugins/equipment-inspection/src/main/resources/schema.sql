CREATE TABLE IF NOT EXISTS plugin_inspection_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    factory_id VARCHAR(32) NOT NULL,
    operator_user_id VARCHAR(32) NOT NULL,
    equipment_code VARCHAR(64) NOT NULL,
    inspection_result VARCHAR(32) NOT NULL,
    remark VARCHAR(500) NOT NULL,
    created_at VARCHAR(35) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_plugin_inspection_factory
    ON plugin_inspection_record(factory_id, id);
