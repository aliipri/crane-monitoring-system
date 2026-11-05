CREATE TABLE IF NOT EXISTS cranes (
    id BIGSERIAL PRIMARY KEY,
    model VARCHAR(120) NOT NULL,
    inventory_no VARCHAR(80) NOT NULL UNIQUE,
    serial_no VARCHAR(80) NOT NULL,
    safety_device VARCHAR(120) NOT NULL,
    commissioning_date DATE NOT NULL,
    rope_installation_date DATE NOT NULL,
    normative_resource VARCHAR(160) NOT NULL
);

CREATE TABLE IF NOT EXISTS uploads (
    id BIGSERIAL PRIMARY KEY,
    crane_id BIGINT NOT NULL REFERENCES cranes(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    upload_date TIMESTAMP NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    file_path VARCHAR(500),
    CONSTRAINT uq_uploads_crane_file UNIQUE (crane_id, file_name, period_start, period_end)
);

CREATE TABLE IF NOT EXISTS load_profiles (
    id BIGSERIAL PRIMARY KEY,
    upload_id BIGINT NOT NULL UNIQUE REFERENCES uploads(id) ON DELETE CASCADE,
    load_lt_40 INTEGER NOT NULL,
    load_40_80 INTEGER NOT NULL,
    load_80_100 INTEGER NOT NULL,
    load_gt_100 INTEGER NOT NULL,
    cycles_total INTEGER NOT NULL,
    overload_cycles INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS mechanism_workloads (
    id BIGSERIAL PRIMARY KEY,
    upload_id BIGINT NOT NULL UNIQUE REFERENCES uploads(id) ON DELETE CASCADE,
    hoist_hours NUMERIC(10, 2) NOT NULL,
    boom_angle_hours NUMERIC(10, 2) NOT NULL,
    boom_length_hours NUMERIC(10, 2) NOT NULL,
    rotation_hours NUMERIC(10, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS visual_inspections (
    id BIGSERIAL PRIMARY KEY,
    crane_id BIGINT NOT NULL REFERENCES cranes(id) ON DELETE CASCADE,
    upload_id BIGINT REFERENCES uploads(id) ON DELETE SET NULL,
    inspection_date DATE NOT NULL,
    broken_wires_count INTEGER NOT NULL,
    diameter_loss_percent NUMERIC(5, 2) NOT NULL,
    corrosion_score INTEGER NOT NULL,
    abrasion_score INTEGER NOT NULL,
    deformation_score INTEGER NOT NULL,
    lubrication_score INTEGER NOT NULL,
    comment TEXT
);

CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    upload_id BIGINT NOT NULL REFERENCES uploads(id) ON DELETE CASCADE,
    event_time TIMESTAMP NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS metrics (
    id BIGSERIAL PRIMARY KEY,
    upload_id BIGINT NOT NULL UNIQUE REFERENCES uploads(id) ON DELETE CASCADE,
    intensity_index NUMERIC(5, 2) NOT NULL,
    damage_index NUMERIC(8, 4) NOT NULL,
    wear_percent INTEGER NOT NULL,
    remaining_resource INTEGER NOT NULL,
    intensity_class VARCHAR(80) NOT NULL,
    status VARCHAR(160) NOT NULL
);

CREATE TABLE IF NOT EXISTS predictions (
    id BIGSERIAL PRIMARY KEY,
    crane_id BIGINT NOT NULL REFERENCES cranes(id) ON DELETE CASCADE,
    upload_id BIGINT NOT NULL UNIQUE REFERENCES uploads(id) ON DELETE CASCADE,
    forecast_date DATE NOT NULL,
    predicted_cycles_total INTEGER NOT NULL,
    predicted_load_lt_40 INTEGER NOT NULL,
    predicted_load_40_80 INTEGER NOT NULL,
    predicted_load_80_100 INTEGER NOT NULL,
    predicted_load_gt_100 INTEGER NOT NULL,
    predicted_overload_cycles INTEGER NOT NULL,
    predicted_hoist_hours NUMERIC(10, 2) NOT NULL,
    predicted_boom_angle_hours NUMERIC(10, 2) NOT NULL,
    predicted_boom_length_hours NUMERIC(10, 2) NOT NULL,
    predicted_rotation_hours NUMERIC(10, 2) NOT NULL,
    predicted_broken_wires_count INTEGER NOT NULL,
    predicted_diameter_loss_percent NUMERIC(5, 2) NOT NULL,
    predicted_corrosion_score INTEGER NOT NULL,
    predicted_abrasion_score INTEGER NOT NULL,
    predicted_deformation_score INTEGER NOT NULL,
    predicted_lubrication_score INTEGER NOT NULL,
    predicted_damage_index NUMERIC(8, 4) NOT NULL,
    predicted_wear_percent INTEGER NOT NULL,
    predicted_remaining_resource INTEGER NOT NULL,
    predicted_status VARCHAR(160) NOT NULL,
    prediction_source VARCHAR(40) NOT NULL DEFAULT 'CNN_LSTM'
);

ALTER TABLE predictions
    ADD COLUMN IF NOT EXISTS prediction_source VARCHAR(40) NOT NULL DEFAULT 'LEGACY_FORMULA';

CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    crane_id BIGINT NOT NULL REFERENCES cranes(id) ON DELETE CASCADE,
    upload_id BIGINT REFERENCES uploads(id) ON DELETE SET NULL,
    report_date TIMESTAMP NOT NULL,
    report_path VARCHAR(500) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_uploads_crane_period ON uploads(crane_id, period_end);
CREATE INDEX IF NOT EXISTS idx_events_upload_time ON events(upload_id, event_time);
CREATE INDEX IF NOT EXISTS idx_visual_inspections_crane_date ON visual_inspections(crane_id, inspection_date);
