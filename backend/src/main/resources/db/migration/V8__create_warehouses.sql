CREATE TABLE `warehouses` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `location` VARCHAR(255),
    `remark` VARCHAR(255),
    `enabled` BIT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_warehouse_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `uk_warehouse_name` UNIQUE (`factory_id`, `name`),
    CONSTRAINT `fk_warehouses_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;
