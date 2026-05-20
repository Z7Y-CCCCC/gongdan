-- Add completed_at column to work_orders table
-- Note: With spring.jpa.hibernate.ddl-auto=update, Hibernate will auto-create the column.
-- This script is provided for manual execution to backfill existing data.

ALTER TABLE work_orders ADD COLUMN completed_at DATETIME NULL;

-- Backfill: for existing COMPLETED work orders, use updated_at as the completed_at value
UPDATE work_orders SET completed_at = updated_at WHERE status = 'COMPLETED';
