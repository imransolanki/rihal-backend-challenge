-- Branches table
CREATE TABLE branches (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address VARCHAR(500) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Muscat',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_branches_city ON branches(city);
CREATE INDEX idx_branches_is_active ON branches(is_active);

-- Service Types table
CREATE TABLE service_types (
    id VARCHAR(50) PRIMARY KEY,
    branch_id VARCHAR(50) NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    duration_minutes INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_types_branch_id ON service_types(branch_id);
CREATE INDEX idx_service_types_is_active ON service_types(is_active);

-- Foreign key from users to branches
ALTER TABLE users
    ADD CONSTRAINT fk_users_branch
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL;

-- Staff Service Types (many-to-many)
CREATE TABLE staff_service_types (
    id SERIAL PRIMARY KEY,
    staff_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_type_id VARCHAR(50) NOT NULL REFERENCES service_types(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(staff_id, service_type_id)
);

CREATE INDEX idx_staff_service_types_staff_id ON staff_service_types(staff_id);
CREATE INDEX idx_staff_service_types_service_type_id ON staff_service_types(service_type_id);

-- Slots table
CREATE TABLE slots (
    id VARCHAR(50) PRIMARY KEY,
    branch_id VARCHAR(50) NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    service_type_id VARCHAR(50) NOT NULL REFERENCES service_types(id) ON DELETE CASCADE,
    staff_id VARCHAR(50) REFERENCES users(id) ON DELETE SET NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_slots_branch_id ON slots(branch_id);
CREATE INDEX idx_slots_service_type_id ON slots(service_type_id);
CREATE INDEX idx_slots_staff_id ON slots(staff_id);
CREATE INDEX idx_slots_start_at ON slots(start_at);
CREATE INDEX idx_slots_deleted_at ON slots(deleted_at);

-- Appointments table
CREATE TABLE appointments (
    id VARCHAR(50) PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id VARCHAR(50) NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    service_type_id VARCHAR(50) NOT NULL REFERENCES service_types(id) ON DELETE CASCADE,
    slot_id VARCHAR(50) REFERENCES slots(id) ON DELETE SET NULL,
    staff_id VARCHAR(50) REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    attachment_path VARCHAR(500),
    internal_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_appointments_customer_id ON appointments(customer_id);
CREATE INDEX idx_appointments_branch_id ON appointments(branch_id);
CREATE INDEX idx_appointments_slot_id ON appointments(slot_id);
CREATE INDEX idx_appointments_staff_id ON appointments(staff_id);
CREATE INDEX idx_appointments_status ON appointments(status);

-- Audit Logs table
CREATE TABLE audit_logs (
    id VARCHAR(50) PRIMARY KEY,
    actor_id VARCHAR(50) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_actor_id ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action_type ON audit_logs(action_type);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs(entity_type);
CREATE INDEX idx_audit_logs_entity_id ON audit_logs(entity_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);

-- System configuration table
CREATE TABLE system_config (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_config (key, value, description)
VALUES ('soft_delete_retention_days', '30', 'Number of days to retain soft-deleted records before hard delete')
ON CONFLICT (key) DO NOTHING;
