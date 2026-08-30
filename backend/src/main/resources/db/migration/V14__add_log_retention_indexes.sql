CREATE INDEX `idx_system_import_logs_created_at`
    ON `system_import_logs` (`created_at`);

CREATE INDEX `idx_system_operation_logs_created_at`
    ON `system_operation_logs` (`created_at`);

CREATE INDEX `idx_product_operation_logs_created_at`
    ON `product_operation_logs` (`created_at`);
