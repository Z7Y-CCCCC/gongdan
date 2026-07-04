CREATE TABLE IF NOT EXISTS `sms_codes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `phone` VARCHAR(32) NOT NULL,
    `code` VARCHAR(6) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `verified` BIT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_sms_codes_phone` UNIQUE (`phone`),
    KEY `idx_sms_codes_created_at` (`created_at`)
) ENGINE=InnoDB;
