CREATE TABLE `factories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `enabled` BIT NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_factories_code` UNIQUE (`code`)
) ENGINE=InnoDB;

CREATE TABLE `departments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `created_by` VARCHAR(64) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_department_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `uk_department_name` UNIQUE (`factory_id`, `name`),
    CONSTRAINT `fk_departments_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `factory_users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `enabled` BIT NOT NULL,
    `last_mobile_login_at` DATETIME(6),
    `mobile_bound_at` DATETIME(6),
    `name` VARCHAR(64) NOT NULL,
    `password_hash` VARCHAR(128) NOT NULL,
    `phone` VARCHAR(32),
    `role` VARCHAR(32) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `username` VARCHAR(64) NOT NULL,
    `virtual_user` BIT NOT NULL,
    `wechat_openid` VARCHAR(128),
    `department_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_factory_username` UNIQUE (`factory_id`, `username`),
    CONSTRAINT `uk_factory_phone` UNIQUE (`factory_id`, `phone`),
    CONSTRAINT `uk_factory_users_wechat_openid` UNIQUE (`wechat_openid`),
    CONSTRAINT `fk_factory_users_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
    CONSTRAINT `fk_factory_users_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `authorization_codes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(8) NOT NULL,
    `consumed` BIT NOT NULL,
    `consumed_at` DATETIME(6),
    `created_at` DATETIME(6) NOT NULL,
    `disabled` BIT NOT NULL,
    `disabled_at` DATETIME(6),
    `expires_at` DATETIME(6) NOT NULL,
    `version` INTEGER,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_authorization_codes_code` UNIQUE (`code`),
    CONSTRAINT `fk_authorization_codes_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `custom_fields` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `allow_user_options` BIT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `default_value_json` LONGTEXT,
    `description_text` VARCHAR(255),
    `display_mode` VARCHAR(32),
    `field_type` VARCHAR(32) NOT NULL,
    `helper_text` VARCHAR(128),
    `field_name` VARCHAR(64) NOT NULL,
    `options_json` LONGTEXT,
    `placeholder` VARCHAR(64),
    `required` BIT NOT NULL,
    `scope_name` VARCHAR(32) NOT NULL,
    `sort_order` INTEGER NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_custom_field_name` UNIQUE (`factory_id`, `scope_name`, `field_name`),
    CONSTRAINT `fk_custom_fields_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_custom_fields_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `defect_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_defect_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `fk_defect_items_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `processes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `collect_data` LONGTEXT,
    `corresponding_machine` VARCHAR(128),
    `created_at` DATETIME(6) NOT NULL,
    `defect_item_ids` VARCHAR(512),
    `feed_count` VARCHAR(64),
    `good_quantity` VARCHAR(64),
    `machine_name` VARCHAR(128),
    `name` VARCHAR(128) NOT NULL,
    `report_permission` VARCHAR(1024),
    `report_ratio` INTEGER NOT NULL,
    `requirements` VARCHAR(512),
    `task_punch_in` BIT NOT NULL,
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_process_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `fk_processes_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_processes_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `process_routes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `steps` LONGTEXT,
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_route_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `fk_process_routes_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_process_routes_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `units` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `remark` VARCHAR(255),
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_unit_name` UNIQUE (`factory_id`, `name`),
    CONSTRAINT `fk_units_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `product_categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_product_category_name` UNIQUE (`factory_id`, `name`),
    CONSTRAINT `fk_product_categories_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_product_categories_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `products` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `attribute` VARCHAR(32) NOT NULL,
    `code` VARCHAR(64) NOT NULL,
    `cost_price` VARCHAR(32),
    `created_at` DATETIME(6) NOT NULL,
    `customer_name` VARCHAR(128),
    `fixed_weight` VARCHAR(32),
    `inventory_qty` INTEGER NOT NULL,
    `key_part` BIT NOT NULL,
    `manufacture_mode` VARCHAR(64),
    `max_stock` VARCHAR(32),
    `min_stock` VARCHAR(32),
    `name` VARCHAR(128) NOT NULL,
    `product_type` VARCHAR(64),
    `quantity` VARCHAR(32),
    `safety_stock` VARCHAR(32),
    `sale_price` VARCHAR(32),
    `spec` VARCHAR(128),
    `supplier_name` VARCHAR(128),
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    `route_id` BIGINT,
    `unit_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_product_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `fk_products_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_products_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`),
    CONSTRAINT `fk_products_route` FOREIGN KEY (`route_id`) REFERENCES `process_routes` (`id`),
    CONSTRAINT `fk_products_unit` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `inbound_receipts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `custom_data_json` LONGTEXT,
    `inbound_time` DATETIME(6) NOT NULL,
    `inbound_type` VARCHAR(32) NOT NULL,
    `remark` VARCHAR(512),
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_inbound_receipts_code` UNIQUE (`code`),
    CONSTRAINT `fk_inbound_receipts_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_inbound_receipts_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `inbound_receipt_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `custom_data_json` LONGTEXT,
    `quantity` INTEGER NOT NULL,
    `inbound_receipt_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_inbound_receipt_items_receipt` FOREIGN KEY (`inbound_receipt_id`) REFERENCES `inbound_receipts` (`id`),
    CONSTRAINT `fk_inbound_receipt_items_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `outbound_receipts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `custom_data_json` LONGTEXT,
    `outbound_time` DATETIME(6) NOT NULL,
    `outbound_type` VARCHAR(32) NOT NULL,
    `remark` VARCHAR(512),
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_outbound_receipts_code` UNIQUE (`code`),
    CONSTRAINT `fk_outbound_receipts_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_outbound_receipts_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `outbound_receipt_items` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `custom_data_json` LONGTEXT,
    `quantity` INTEGER NOT NULL,
    `outbound_receipt_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_outbound_receipt_items_receipt` FOREIGN KEY (`outbound_receipt_id`) REFERENCES `outbound_receipts` (`id`),
    CONSTRAINT `fk_outbound_receipt_items_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `product_operation_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `action_type` VARCHAR(32) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `detail` VARCHAR(255),
    `operator_name` VARCHAR(64) NOT NULL,
    `product_code` VARCHAR(64),
    `product_name` VARCHAR(128),
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_product_operation_logs_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `system_import_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `errors` LONGTEXT,
    `fail_count` INTEGER NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `module` VARCHAR(64) NOT NULL,
    `operator_name` VARCHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `success_count` INTEGER NOT NULL,
    `total_count` INTEGER NOT NULL,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_system_import_logs_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `system_operation_logs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `action_type` VARCHAR(32) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `detail` VARCHAR(255),
    `module` VARCHAR(64) NOT NULL,
    `operator_name` VARCHAR(64) NOT NULL,
    `target_code` VARCHAR(64),
    `target_name` VARCHAR(128),
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_system_operation_logs_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `user_preferences` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `pref_key` VARCHAR(64) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `pref_value` LONGTEXT,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_user_pref_key` UNIQUE (`user_id`, `pref_key`),
    CONSTRAINT `fk_user_preferences_user` FOREIGN KEY (`user_id`) REFERENCES `factory_users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `work_order_categories` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_work_order_category_name` UNIQUE (`factory_id`, `name`),
    CONSTRAINT `fk_work_order_categories_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_work_order_categories_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `work_orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(64) NOT NULL,
    `completed_at` DATETIME(6),
    `created_at` DATETIME(6) NOT NULL,
    `custom_data_json` LONGTEXT,
    `defect_quantity` INTEGER NOT NULL,
    `defect_rate` VARCHAR(32),
    `good_quantity` INTEGER NOT NULL,
    `material_data_json` LONGTEXT,
    `plan_end` DATETIME(6),
    `plan_start` DATETIME(6),
    `planned_quantity` INTEGER NOT NULL,
    `priority` VARCHAR(32) NOT NULL,
    `product_code` VARCHAR(64),
    `product_name` VARCHAR(128),
    `product_spec` VARCHAR(128),
    `related_order_code` VARCHAR(64),
    `remark` VARCHAR(500),
    `status` VARCHAR(32) NOT NULL,
    `task_data_json` LONGTEXT,
    `updated_at` DATETIME(6) NOT NULL,
    `version` BIGINT NOT NULL,
    `category_id` BIGINT,
    `creator_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    `product_id` BIGINT,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_work_order_code` UNIQUE (`factory_id`, `code`),
    CONSTRAINT `fk_work_orders_category` FOREIGN KEY (`category_id`) REFERENCES `work_order_categories` (`id`),
    CONSTRAINT `fk_work_orders_creator` FOREIGN KEY (`creator_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_work_orders_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`),
    CONSTRAINT `fk_work_orders_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `work_order_reports` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `approval_remark` VARCHAR(500),
    `approved_at` DATETIME(6),
    `approver_name` VARCHAR(64),
    `created_at` DATETIME(6) NOT NULL,
    `defect_item_name` VARCHAR(128),
    `defect_quantity` INTEGER NOT NULL,
    `end_time` DATETIME(6),
    `good_quantity` INTEGER NOT NULL,
    `material_name` VARCHAR(128),
    `process_name` VARCHAR(128) NOT NULL,
    `process_sequence_no` INTEGER NOT NULL,
    `product_code` VARCHAR(64),
    `product_name` VARCHAR(128),
    `product_spec` VARCHAR(128),
    `remark` LONGTEXT,
    `report_duration_minutes` INTEGER NOT NULL,
    `report_quantity` INTEGER NOT NULL,
    `reporter_name` VARCHAR(64) NOT NULL,
    `start_time` DATETIME(6),
    `status` VARCHAR(32) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `work_order_code` VARCHAR(64) NOT NULL,
    `approver_id` BIGINT,
    `factory_id` BIGINT NOT NULL,
    `reporter_id` BIGINT NOT NULL,
    `work_order_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_work_order_reports_approver` FOREIGN KEY (`approver_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_work_order_reports_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`),
    CONSTRAINT `fk_work_order_reports_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `factory_users` (`id`),
    CONSTRAINT `fk_work_order_reports_work_order` FOREIGN KEY (`work_order_id`) REFERENCES `work_orders` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `work_order_report_images` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `access_path` VARCHAR(500) NOT NULL,
    `content_type` VARCHAR(128) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `original_file_name` VARCHAR(255) NOT NULL,
    `stored_file_name` VARCHAR(255) NOT NULL,
    `factory_id` BIGINT NOT NULL,
    `report_id` BIGINT,
    `uploader_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_work_order_report_images_factory` FOREIGN KEY (`factory_id`) REFERENCES `factories` (`id`),
    CONSTRAINT `fk_work_order_report_images_report` FOREIGN KEY (`report_id`) REFERENCES `work_order_reports` (`id`),
    CONSTRAINT `fk_work_order_report_images_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `factory_users` (`id`)
) ENGINE=InnoDB;
