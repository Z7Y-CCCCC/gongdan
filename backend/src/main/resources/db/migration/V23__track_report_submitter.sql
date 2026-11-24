ALTER TABLE work_order_reports ADD COLUMN submitter_id BIGINT NULL;

CREATE INDEX idx_reports_factory_submitter_created
    ON work_order_reports (factory_id, submitter_id, created_at, id);
