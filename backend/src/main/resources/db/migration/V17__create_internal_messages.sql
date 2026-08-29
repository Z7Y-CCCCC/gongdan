CREATE TABLE internal_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    factory_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    read_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_internal_messages_factory FOREIGN KEY (factory_id) REFERENCES factories (id),
    CONSTRAINT fk_internal_messages_sender FOREIGN KEY (sender_id) REFERENCES factory_users (id),
    CONSTRAINT fk_internal_messages_receiver FOREIGN KEY (receiver_id) REFERENCES factory_users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_internal_messages_factory_time ON internal_messages (factory_id, created_at);
CREATE INDEX idx_internal_messages_receiver_read ON internal_messages (receiver_id, read_at, created_at);
CREATE INDEX idx_internal_messages_conversation ON internal_messages (factory_id, sender_id, receiver_id, created_at);
