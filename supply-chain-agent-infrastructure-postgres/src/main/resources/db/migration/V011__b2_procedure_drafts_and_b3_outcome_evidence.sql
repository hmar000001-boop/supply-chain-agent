-- M8-PreB7 R2: typed B2 recovery procedure drafts and append-only B3 downstream-use evidence.
-- The active B3 WorkspaceSnapshot remains ephemeral and cycle-bounded. Only verified audit evidence persists.

CREATE TABLE b2_recovery_procedure_draft (
    scope_id             varchar(256) NOT NULL,
    draft_id             varchar(256) NOT NULL,
    version              bigint NOT NULL CHECK (version >= 0),
    status               varchar(24) NOT NULL CHECK (status IN ('DRAFT','APPROVED','REJECTED','EXPIRED')),
    execution_id         varchar(256) NOT NULL,
    impasse_case_id      varchar(256) NOT NULL,
    objective_fingerprint varchar(256) NOT NULL,
    source_fingerprint   char(64) NOT NULL,
    payload              jsonb NOT NULL,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    expires_at           timestamptz NOT NULL,
    PRIMARY KEY (scope_id, draft_id),
    CHECK (updated_at >= created_at),
    CHECK (expires_at > created_at)
);

CREATE TABLE b2_recovery_procedure_draft_history (
    scope_id             varchar(256) NOT NULL,
    draft_id             varchar(256) NOT NULL,
    version              bigint NOT NULL CHECK (version >= 0),
    status               varchar(24) NOT NULL CHECK (status IN ('DRAFT','APPROVED','REJECTED','EXPIRED')),
    source_fingerprint   char(64) NOT NULL,
    payload              jsonb NOT NULL,
    recorded_at          timestamptz NOT NULL,
    PRIMARY KEY (scope_id, draft_id, version),
    FOREIGN KEY (scope_id, draft_id) REFERENCES b2_recovery_procedure_draft(scope_id, draft_id) ON DELETE RESTRICT
);

CREATE INDEX b2_recovery_procedure_expiry_idx
    ON b2_recovery_procedure_draft(scope_id, expires_at, draft_id) WHERE status = 'DRAFT';

CREATE TABLE b3_workspace_consumer_receipt (
    scope_id       varchar(256) NOT NULL,
    receipt_id     varchar(600) NOT NULL,
    cycle_id       varchar(256) NOT NULL,
    workspace_id   varchar(256) NOT NULL,
    consumer_id    varchar(256) NOT NULL,
    status         varchar(24) NOT NULL CHECK (status IN ('ACCEPTED','REJECTED','FAILED')),
    payload        jsonb NOT NULL,
    received_at    timestamptz NOT NULL,
    PRIMARY KEY (scope_id, receipt_id),
    UNIQUE (scope_id, workspace_id, consumer_id)
);

CREATE TABLE b3_downstream_outcome (
    scope_id          varchar(256) NOT NULL,
    idempotency_key   varchar(256) NOT NULL,
    receipt_id        varchar(600) NOT NULL,
    workspace_id      varchar(256) NOT NULL,
    cycle_id          varchar(256) NOT NULL,
    candidate_id      varchar(256) NOT NULL,
    signal            varchar(24) NOT NULL CHECK (signal IN ('USEFUL','HARMFUL','NEUTRAL')),
    payload           jsonb NOT NULL,
    verified_at       timestamptz NOT NULL,
    PRIMARY KEY (scope_id, idempotency_key),
    FOREIGN KEY (scope_id, receipt_id) REFERENCES b3_workspace_consumer_receipt(scope_id, receipt_id) ON DELETE RESTRICT
);

CREATE INDEX b3_downstream_outcome_candidate_idx
    ON b3_downstream_outcome(scope_id, candidate_id, verified_at, idempotency_key);
