CREATE TABLE notification_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    factory_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    source_type VARCHAR(64),
    source_id BIGINT,
    target_main VARCHAR(64),
    target_sub VARCHAR(64),
    target_params_json LONGTEXT,
    dedupe_key VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6),
    resolved_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_events_factory FOREIGN KEY (factory_id) REFERENCES factories (id),
    CONSTRAINT uk_notification_events_dedupe UNIQUE (factory_id, dedupe_key)
) ENGINE=InnoDB;

CREATE INDEX idx_notification_events_active ON notification_events (factory_id, resolved_at, expires_at, created_at);
CREATE INDEX idx_notification_events_type_source ON notification_events (factory_id, event_type, source_id);

CREATE TABLE notification_recipients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'UNREAD',
    read_at DATETIME(6),
    handled_at DATETIME(6),
    archived_at DATETIME(6),
    snoozed_until DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipients_event FOREIGN KEY (notification_id) REFERENCES notification_events (id),
    CONSTRAINT fk_notification_recipients_user FOREIGN KEY (user_id) REFERENCES factory_users (id),
    CONSTRAINT uk_notification_recipients_user_event UNIQUE (notification_id, user_id)
) ENGINE=InnoDB;

CREATE INDEX idx_notification_recipients_unread ON notification_recipients (user_id, archived_at, read_at, created_at);
CREATE INDEX idx_notification_recipients_state ON notification_recipients (user_id, state, updated_at);
