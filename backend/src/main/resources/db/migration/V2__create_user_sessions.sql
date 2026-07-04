CREATE TABLE IF NOT EXISTS `user_sessions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `token` VARCHAR(128) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `factory_id` BIGINT NOT NULL,
    `factory_code` VARCHAR(64) NOT NULL,
    `role` VARCHAR(32) NOT NULL,
    `display_name` VARCHAR(64) NOT NULL,
    `kicked` BIT NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_user_sessions_token` UNIQUE (`token`),
    KEY `idx_user_sessions_user_active` (`user_id`, `kicked`),
    KEY `idx_user_sessions_expires_at` (`expires_at`)
) ENGINE=InnoDB;
