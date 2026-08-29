ALTER TABLE `user_sessions`
    ADD COLUMN IF NOT EXISTS `last_active_at` DATETIME(6) NULL;

UPDATE `user_sessions`
SET `last_active_at` = `created_at`
WHERE `last_active_at` IS NULL;

CREATE INDEX `idx_user_sessions_last_active_at`
    ON `user_sessions` (`user_id`, `last_active_at`);
