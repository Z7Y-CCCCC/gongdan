CREATE TABLE IF NOT EXISTS `auth_attempts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `attempt_key` VARCHAR(255) NOT NULL,
    `failed_attempts` INTEGER NOT NULL,
    `window_start` DATETIME(6) NOT NULL,
    `locked_until` DATETIME(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_auth_attempts_key` UNIQUE (`attempt_key`),
    KEY `idx_auth_attempts_locked_until` (`locked_until`)
) ENGINE=InnoDB;
