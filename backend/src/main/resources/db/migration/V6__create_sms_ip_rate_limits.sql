CREATE TABLE IF NOT EXISTS `sms_ip_rate_limits` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `client_ip` VARCHAR(64) NOT NULL,
    `request_count` INTEGER NOT NULL,
    `window_start` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_sms_ip_rate_limits_client_ip` UNIQUE (`client_ip`),
    KEY `idx_sms_ip_rate_limits_window_start` (`window_start`)
) ENGINE=InnoDB;
