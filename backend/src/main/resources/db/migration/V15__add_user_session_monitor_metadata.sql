ALTER TABLE `user_sessions`
    ADD COLUMN IF NOT EXISTS `ip_address` VARCHAR(64) NULL;

ALTER TABLE `user_sessions`
    ADD COLUMN IF NOT EXISTS `login_location` VARCHAR(128) NULL;

ALTER TABLE `user_sessions`
    ADD COLUMN IF NOT EXISTS `operating_system` VARCHAR(128) NULL;

ALTER TABLE `user_sessions`
    ADD COLUMN IF NOT EXISTS `browser` VARCHAR(128) NULL;
