-- V16 and V22 used TEXT for fields that Hibernate maps as LONGTEXT.
-- Widen without modifying historical Flyway scripts, data, or nullability.
ALTER TABLE external_data_sources MODIFY COLUMN config_json LONGTEXT NOT NULL;
ALTER TABLE external_data_sources MODIFY COLUMN secret_cipher LONGTEXT NULL;
ALTER TABLE factory_plugins MODIFY COLUMN config_json LONGTEXT NULL;
ALTER TABLE plugin_catalog MODIFY COLUMN manifest_json LONGTEXT NULL;
ALTER TABLE plugin_versions MODIFY COLUMN manifest_json LONGTEXT NULL;
ALTER TABLE plugin_versions MODIFY COLUMN release_notes LONGTEXT NULL;
