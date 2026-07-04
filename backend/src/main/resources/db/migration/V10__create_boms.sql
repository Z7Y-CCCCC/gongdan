CREATE TABLE `bill_of_materials` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `product_id` BIGINT NOT NULL,
    `output_quantity` DECIMAL(18,4) NOT NULL,
    `remark` VARCHAR(255),
    `enabled` BIT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_bom_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `uk_bom_product` UNIQUE (`factory_id`, `product_id`),
    CONSTRAINT `fk_boms_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`),
    CONSTRAINT `fk_boms_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `bill_of_material_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `bom_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `quantity` DECIMAL(18,4) NOT NULL,
    `scrap_rate` DECIMAL(8,4) NOT NULL,
    `remark` VARCHAR(255),
    `sort_order` INT NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_bom_items_bom` (`bom_id`),
    INDEX `idx_bom_items_product` (`product_id`),
    CONSTRAINT `fk_bom_items_bom` FOREIGN KEY (`bom_id`) REFERENCES `bill_of_materials` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bom_items_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB;
