ALTER TABLE slots 
ADD COLUMN booked_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_slots_availability ON slots(branch_id, service_type_id, start_at, booked_count, deleted_at);

COMMENT ON COLUMN slots.booked_count IS 'Number of appointments booked for this slot';
