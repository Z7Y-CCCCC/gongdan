CREATE TABLE `suppliers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `contact_name` VARCHAR(64),
    `phone` VARCHAR(32),
    `address` VARCHAR(255),
    `remark` VARCHAR(255),
    `enabled` BIT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_supplier_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `uk_supplier_name` UNIQUE (`factory_id`, `name`),
    CONSTRAINT `fk_suppliers_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;
