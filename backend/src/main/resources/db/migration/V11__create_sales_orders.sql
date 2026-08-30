CREATE TABLE `sales_orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `customer_name` VARCHAR(128) NOT NULL,
    `order_date` DATE,
    `delivery_date` DATE,
    `status` VARCHAR(32) NOT NULL,
    `remark` VARCHAR(500),
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_sales_order_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `fk_sales_orders_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_sales_orders_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `sales_order_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sales_order_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `quantity` INTEGER NOT NULL,
    `remark` VARCHAR(255),
    `sort_order` INT NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_sales_order_items_order` (`sales_order_id`),
    INDEX `idx_sales_order_items_product` (`product_id`),
    CONSTRAINT `fk_sales_order_items_order` FOREIGN KEY (`sales_order_id`) REFERENCES `sales_orders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_sales_order_items_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB;
