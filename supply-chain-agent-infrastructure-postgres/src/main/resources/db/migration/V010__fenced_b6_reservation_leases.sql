-- M8-PreB7 R1-DEF: durable ownership, lease expiry, fencing and crash-recovery outcome.
-- V001-V009 remain immutable. Legacy reservations keep owner_id/lease_expires_at NULL and token 0.

ALTER TABLE b6_resource_budget_reservation
    ADD COLUMN owner_id varchar(256),
    ADD COLUMN lease_expires_at timestamptz,
    ADD COLUMN fencing_token bigint NOT NULL DEFAULT 0,
    ADD COLUMN started_at timestamptz,
    ADD COLUMN recovery_outcome varchar(80) NOT NULL DEFAULT 'NONE';

ALTER TABLE b6_resource_budget_reservation
    ADD CONSTRAINT b6_budget_reservation_owner_lease_ck CHECK (
        (owner_id IS NULL AND lease_expires_at IS NULL AND fencing_token = 0)
        OR (owner_id IS NOT NULL AND lease_expires_at IS NOT NULL AND fencing_token > 0)
    ),
    ADD CONSTRAINT b6_budget_reservation_started_owner_ck CHECK (
        started_at IS NULL OR owner_id IS NOT NULL
    ),
    ADD CONSTRAINT b6_budget_reservation_recovery_outcome_ck CHECK (
        recovery_outcome IN ('NONE', 'EXPIRED_BEFORE_START',
                             'EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED', 'EXPLICITLY_RELEASED')
    ),
    ADD CONSTRAINT b6_budget_reservation_recovery_state_ck CHECK (
        (recovery_outcome <> 'EXPIRED_BEFORE_START' OR (status = 'RELEASED' AND started_at IS NULL))
        AND (recovery_outcome <> 'EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED'
             OR (status = 'COMMITTED' AND started_at IS NOT NULL))
        AND (recovery_outcome <> 'EXPLICITLY_RELEASED'
             OR (status = 'RELEASED' AND started_at IS NULL))
    );

CREATE INDEX b6_resource_budget_expired_lease_idx
    ON b6_resource_budget_reservation(scope_id, execution_id, lease_expires_at, reservation_id)
    WHERE status = 'ACTIVE' AND owner_id IS NOT NULL;
