-- Separately verified downstream outcomes for exact first-class B3 coalition proposals.
CREATE TABLE b3_coalition_downstream_outcome (
    scope_id              TEXT NOT NULL,
    idempotency_key       TEXT NOT NULL,
    receipt_id            TEXT NOT NULL,
    workspace_id          TEXT NOT NULL,
    cycle_id              TEXT NOT NULL,
    coalition_id          TEXT NOT NULL,
    proposal_fingerprint  CHAR(64) NOT NULL,
    signal                TEXT NOT NULL CHECK (signal IN ('USEFUL','HARMFUL','NEUTRAL')),
    payload               JSONB NOT NULL,
    verified_at           TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scope_id, idempotency_key),
    FOREIGN KEY (scope_id, receipt_id) REFERENCES b3_workspace_consumer_receipt(scope_id, receipt_id) ON DELETE RESTRICT,
    CHECK (proposal_fingerprint ~ '^[0-9a-f]{64}$'),
    CHECK (jsonb_typeof(payload) = 'object')
);
CREATE INDEX b3_coalition_outcome_scope_time_idx
    ON b3_coalition_downstream_outcome (scope_id, verified_at ASC);
