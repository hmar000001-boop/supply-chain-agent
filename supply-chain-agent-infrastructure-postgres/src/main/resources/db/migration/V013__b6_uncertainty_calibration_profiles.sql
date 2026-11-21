-- Immutable target- and scope-bound B6 calibration profiles.
-- Training and validation datasets are identified by separate fingerprints; raw labels remain
-- in their governed evidence store. Only independently validated profiles can become active.
CREATE TABLE b6_uncertainty_calibration_profile (
    scope_id TEXT NOT NULL,
    profile_id TEXT NOT NULL,
    target_id TEXT NOT NULL,
    profile_version BIGINT NOT NULL CHECK (profile_version > 0),
    status TEXT NOT NULL CHECK (status IN ('TRAINED','VALIDATED','ACTIVE','RETIRED')),
    training_dataset_fingerprint CHAR(64) NOT NULL,
    validation_dataset_fingerprint CHAR(64),
    training_sample_count BIGINT NOT NULL CHECK (training_sample_count > 0),
    validation_sample_count BIGINT NOT NULL CHECK (validation_sample_count >= 0),
    validation_ece DOUBLE PRECISION NOT NULL CHECK (validation_ece >= 0 AND validation_ece <= 1),
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scope_id, profile_id),
    UNIQUE (scope_id, profile_id, profile_version),
    CHECK (validation_dataset_fingerprint IS NULL OR validation_dataset_fingerprint <> training_dataset_fingerprint),
    CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX b6_uncertainty_one_active_profile_per_target
    ON b6_uncertainty_calibration_profile(scope_id, target_id)
    WHERE status = 'ACTIVE';

CREATE INDEX b6_uncertainty_profile_history_idx
    ON b6_uncertainty_calibration_profile(scope_id, target_id, created_at, profile_id);
