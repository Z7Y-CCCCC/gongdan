CREATE TABLE user_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    factory_id BIGINT NOT NULL,
    factory_code VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    kicked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_sessions_token (token),
    KEY idx_user_sessions_user_active (user_id, kicked),
    KEY idx_user_sessions_expires_at (expires_at)
);
