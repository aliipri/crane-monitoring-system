\set ON_ERROR_STOP on

BEGIN;

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

INSERT INTO mechanism_workloads (
    upload_id, hoist_hours, boom_angle_hours, boom_length_hours, rotation_hours
)
SELECT u.id, 0, 0, 0, 0
FROM uploads u
WHERE NOT EXISTS (
    SELECT 1
    FROM mechanism_workloads mw
    WHERE mw.upload_id = u.id
);

ALTER TABLE load_profiles ADD COLUMN IF NOT EXISTS cycles_total INTEGER;
ALTER TABLE load_profiles ADD COLUMN IF NOT EXISTS overload_cycles INTEGER;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'uploads' AND column_name = 'cycle_count'
    ) THEN
        EXECUTE '
            UPDATE load_profiles lp
            SET cycles_total = u.cycle_count,
                overload_cycles = u.overload_count
            FROM uploads u
            WHERE lp.upload_id = u.id
        ';
    END IF;
END
$migration$;

UPDATE load_profiles
SET cycles_total = COALESCE(cycles_total, 0),
    overload_cycles = COALESCE(overload_cycles, 0);
ALTER TABLE load_profiles ALTER COLUMN cycles_total SET NOT NULL;
ALTER TABLE load_profiles ALTER COLUMN overload_cycles SET NOT NULL;
ALTER TABLE uploads DROP COLUMN IF EXISTS cycle_count;
ALTER TABLE uploads DROP COLUMN IF EXISTS overload_count;
ALTER TABLE uploads DROP COLUMN IF EXISTS status;

ALTER TABLE metrics ADD COLUMN IF NOT EXISTS damage_index NUMERIC(8, 4);
UPDATE metrics
SET damage_index = COALESCE(damage_index, wear_percent / 100.0);
ALTER TABLE metrics ALTER COLUMN damage_index SET NOT NULL;

ALTER TABLE predictions ADD COLUMN IF NOT EXISTS crane_id BIGINT REFERENCES cranes(id) ON DELETE CASCADE;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS forecast_date DATE;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_cycles_total INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_load_lt_40 INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_load_40_80 INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_load_80_100 INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_load_gt_100 INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_overload_cycles INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_hoist_hours NUMERIC(10, 2);
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_boom_angle_hours NUMERIC(10, 2);
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_boom_length_hours NUMERIC(10, 2);
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_rotation_hours NUMERIC(10, 2);
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_broken_wires_count INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_diameter_loss_percent NUMERIC(5, 2);
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_corrosion_score INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_abrasion_score INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_deformation_score INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_lubrication_score INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_damage_index NUMERIC(8, 4);
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_wear_percent INTEGER;
ALTER TABLE predictions ADD COLUMN IF NOT EXISTS predicted_remaining_resource INTEGER;

UPDATE predictions p
SET crane_id = COALESCE(p.crane_id, u.crane_id),
    forecast_date = COALESCE(p.forecast_date, (u.period_end + INTERVAL '1 month')::date),
    predicted_cycles_total = COALESCE(p.predicted_cycles_total, lp.cycles_total, 0),
    predicted_load_lt_40 = COALESCE(p.predicted_load_lt_40, lp.load_lt_40, 0),
    predicted_load_40_80 = COALESCE(p.predicted_load_40_80, lp.load_40_80, 0),
    predicted_load_80_100 = COALESCE(p.predicted_load_80_100, lp.load_80_100, 0),
    predicted_load_gt_100 = COALESCE(p.predicted_load_gt_100, lp.load_gt_100, 0),
    predicted_overload_cycles = COALESCE(p.predicted_overload_cycles, lp.overload_cycles, 0),
    predicted_hoist_hours = COALESCE(p.predicted_hoist_hours, 0),
    predicted_boom_angle_hours = COALESCE(p.predicted_boom_angle_hours, 0),
    predicted_boom_length_hours = COALESCE(p.predicted_boom_length_hours, 0),
    predicted_rotation_hours = COALESCE(p.predicted_rotation_hours, 0),
    predicted_broken_wires_count = COALESCE(p.predicted_broken_wires_count, 0),
    predicted_diameter_loss_percent = COALESCE(p.predicted_diameter_loss_percent, 0),
    predicted_corrosion_score = COALESCE(p.predicted_corrosion_score, 0),
    predicted_abrasion_score = COALESCE(p.predicted_abrasion_score, 0),
    predicted_deformation_score = COALESCE(p.predicted_deformation_score, 0),
    predicted_lubrication_score = COALESCE(p.predicted_lubrication_score, 0),
    predicted_damage_index = COALESCE(p.predicted_damage_index, 0),
    predicted_wear_percent = COALESCE(p.predicted_wear_percent, 0),
    predicted_remaining_resource = COALESCE(p.predicted_remaining_resource, 100)
FROM uploads u
LEFT JOIN load_profiles lp ON lp.upload_id = u.id
WHERE p.upload_id = u.id;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'predictions' AND column_name = 'predicted_wear'
    ) THEN
        EXECUTE '
            UPDATE predictions
            SET predicted_wear_percent = predicted_wear,
                predicted_remaining_resource = predicted_resource,
                predicted_damage_index = predicted_wear / 100.0
        ';
    END IF;
END
$migration$;

ALTER TABLE predictions ALTER COLUMN crane_id SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN forecast_date SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_cycles_total SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_load_lt_40 SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_load_40_80 SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_load_80_100 SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_load_gt_100 SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_overload_cycles SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_hoist_hours SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_boom_angle_hours SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_boom_length_hours SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_rotation_hours SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_broken_wires_count SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_diameter_loss_percent SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_corrosion_score SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_abrasion_score SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_deformation_score SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_lubrication_score SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_damage_index SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_wear_percent SET NOT NULL;
ALTER TABLE predictions ALTER COLUMN predicted_remaining_resource SET NOT NULL;
ALTER TABLE predictions DROP COLUMN IF EXISTS predicted_wear;
ALTER TABLE predictions DROP COLUMN IF EXISTS predicted_resource;
ALTER TABLE predictions DROP COLUMN IF EXISTS model_name;
ALTER TABLE predictions DROP COLUMN IF EXISTS period_label;
ALTER TABLE predictions DROP COLUMN IF EXISTS created_at;

ALTER TABLE reports ADD COLUMN IF NOT EXISTS upload_id BIGINT REFERENCES uploads(id) ON DELETE SET NULL;

COMMIT;
