CREATE TABLE IF NOT EXISTS `custom_field_attachments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `access_path` VARCHAR(500) NOT NULL,
    `content_type` VARCHAR(128) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `field_id` BIGINT,
    `field_type` VARCHAR(32) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `original_file_name` VARCHAR(255) NOT NULL,
    `scope_name` VARCHAR(32) NOT NULL,
    `stored_file_name` VARCHAR(255) NOT NULL,
    `factory_id` BIGINT NOT NULL,
    `uploader_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_custom_field_attachments_factory` (`factory_id`),
    KEY `idx_custom_field_attachments_scope_field` (`factory_id`, `scope_name`, `field_id`),
    CONSTRAINT `fk_custom_field_attachments_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`),
    CONSTRAINT `fk_custom_field_attachments_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `factory_users` (`id`)
) ENGINE=InnoDB;
