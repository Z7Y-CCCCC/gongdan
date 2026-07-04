CREATE TABLE `extension_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `factory_id` BIGINT NOT NULL,
    `module_name` VARCHAR(32) NOT NULL,
    `business_key` VARCHAR(128),
    `title` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `payload_json` LONGTEXT,
    `created_by` BIGINT,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_extension_records_factory_module` (`factory_id`, `module_name`, `created_at`),
    INDEX `idx_extension_records_business_key` (`factory_id`, `module_name`, `business_key`),
    CONSTRAINT `fk_extension_records_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`),
    CONSTRAINT `fk_extension_records_creator` FOREIGN KEY (`created_by`) REFERENCES `factory_users` (`id`)
) ENGINE=InnoDB;
