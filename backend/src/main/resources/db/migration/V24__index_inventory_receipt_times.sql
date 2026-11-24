CREATE INDEX idx_inbound_receipts_factory_time ON inbound_receipts (factory_id, inbound_time, id);
CREATE INDEX idx_outbound_receipts_factory_time ON outbound_receipts (factory_id, outbound_time, id);
