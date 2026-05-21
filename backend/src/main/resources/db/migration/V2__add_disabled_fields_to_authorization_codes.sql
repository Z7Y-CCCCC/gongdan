-- Add disabled and disabled_at columns to authorization_codes table
-- Note: With spring.jpa.hibernate.ddl-auto=update, Hibernate will auto-create the columns.
-- This script is provided for manual execution on existing databases.

ALTER TABLE authorization_codes ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE authorization_codes ADD COLUMN disabled_at DATETIME NULL;
