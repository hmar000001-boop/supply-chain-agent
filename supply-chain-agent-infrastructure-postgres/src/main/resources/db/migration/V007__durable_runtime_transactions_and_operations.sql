-- M7-B2: durable M5/M6/B2/B4 runtime records, M3 operation ledger and transactional messaging.
-- Applied DDL is rolled forward by a new migration; do not edit an already-installed migration.

CREATE TABLE runtime_artifact (
    scope_id                 varchar(256) NOT NULL,
    artifact_kind            varchar(80) NOT NULL,
    artifact_id              varchar(512) NOT NULL,
    execution_id             varchar(256),
    owner_id                 varchar(256),
    owner_version            bigint NOT NULL DEFAULT 0 CHECK (owner_version >= 0),
    payload_fingerprint      char(64) NOT NULL,
    payload                  jsonb NOT NULL,
    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    expires_at               timestamptz,
    PRIMARY KEY (scope_id, artifact_kind, artifact_id),
    CHECK (updated_at >= created_at),
    CHECK (expires_at IS NULL OR expires_at > created_at)
);
CREATE INDEX runtime_artifact_execution_idx ON runtime_artifact(scope_id, execution_id, artifact_kind);
CREATE INDEX runtime_artifact_expiry_idx ON runtime_artifact(expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE planning_artifact (
    scope_id                 varchar(256) NOT NULL,
    candidate_set_id         varchar(512) NOT NULL,
    execution_id             varchar(256) NOT NULL,
    request_fingerprint      char(64) NOT NULL,
    planner_id               varchar(96) NOT NULL,
    planner_session_id       varchar(256) NOT NULL,
    algorithm_config_fingerprint char(64),
    working_memory_id        varchar(256) NOT NULL,
    working_memory_version   bigint NOT NULL CHECK (working_memory_version >= 0),
    payload_fingerprint      char(64) NOT NULL,
    payload                  jsonb NOT NULL,
    created_at               timestamptz NOT NULL,
    PRIMARY KEY (scope_id, candidate_set_id),
    UNIQUE (scope_id, execution_id, candidate_set_id)
);
CREATE INDEX planning_artifact_execution_idx ON planning_artifact(scope_id, execution_id, created_at DESC);
CREATE INDEX planning_artifact_session_idx ON planning_artifact(scope_id, planner_session_id);

CREATE TABLE action_execution_batch (
    scope_id                 varchar(256) NOT NULL,
    batch_id                 varchar(256) NOT NULL,
    execution_id             varchar(256) NOT NULL,
    principal_id             varchar(256) NOT NULL,
    candidate_set_id         varchar(512) NOT NULL,
    candidate_id             varchar(512) NOT NULL,
    plan_fingerprint         char(64) NOT NULL,
    status                   varchar(32) NOT NULL CHECK (status IN ('STARTED','SUCCEEDED','PARTIAL','UNKNOWN_OUTCOME','REJECTED','FAILED')),
    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    revision                 bigint NOT NULL DEFAULT 1 CHECK (revision >= 1),
    PRIMARY KEY (scope_id, batch_id),
    CHECK (updated_at >= created_at)
);
CREATE INDEX action_execution_batch_execution_idx ON action_execution_batch(scope_id, execution_id, created_at DESC);

CREATE TABLE action_execution_step (
    scope_id                 varchar(256) NOT NULL,
    batch_id                 varchar(256) NOT NULL,
    step_index               integer NOT NULL CHECK (step_index >= 0),
    action_id                varchar(160) NOT NULL,
    status                   varchar(32) NOT NULL,
    operation_id             varchar(256),
    result_reference         varchar(2048),
    failure_code             varchar(96),
    failure_message          varchar(2048),
    outcome_unknown          boolean NOT NULL DEFAULT false,
    receipt_fingerprint      char(64) NOT NULL,
    recorded_at              timestamptz NOT NULL,
    PRIMARY KEY (scope_id, batch_id, step_index),
    FOREIGN KEY (scope_id, batch_id) REFERENCES action_execution_batch(scope_id, batch_id) ON DELETE RESTRICT
);

CREATE TABLE impasse_child_artifact (
    scope_id                 varchar(256) NOT NULL,
    case_id                  varchar(256) NOT NULL,
    payload_fingerprint      char(64) NOT NULL,
    payload                  jsonb NOT NULL,
    created_at               timestamptz NOT NULL,
    PRIMARY KEY (scope_id, case_id)
);

CREATE TABLE b2_child_step_receipt (
    scope_id                 varchar(256) NOT NULL,
    case_id                  varchar(256) NOT NULL,
    step_id                  varchar(256) NOT NULL,
    input_fingerprint        char(64) NOT NULL,
    state                    varchar(24) NOT NULL CHECK (state IN ('CLAIMED','COMPLETED','FAILED','UNKNOWN_OUTCOME')),
    lease_owner              varchar(256),
    lease_until              timestamptz,
    fencing_token            bigint NOT NULL CHECK (fencing_token >= 1),
    result_payload           jsonb,
    result_fingerprint       char(64),
    failure_code             varchar(96),
    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    PRIMARY KEY (scope_id, case_id, step_id),
    CHECK (updated_at >= created_at),
    CHECK ((state = 'COMPLETED') = (result_payload IS NOT NULL AND result_fingerprint IS NOT NULL))
);
CREATE INDEX b2_child_step_lease_idx ON b2_child_step_receipt(state, lease_until);

CREATE TABLE external_observation (
    scope_id                 varchar(256) NOT NULL,
    observation_id           varchar(256) NOT NULL,
    execution_id             varchar(256) NOT NULL,
    principal_id             varchar(256) NOT NULL,
    source_reference         varchar(1024) NOT NULL,
    disposition              varchar(48) NOT NULL,
    evidence_set_id          varchar(512),
    observed_at              timestamptz NOT NULL,
    payload_fingerprint      char(64) NOT NULL,
    payload                  jsonb NOT NULL,
    PRIMARY KEY (scope_id, observation_id)
);
CREATE INDEX external_observation_execution_idx ON external_observation(scope_id, execution_id, observed_at DESC);

CREATE TABLE recovery_intention (
    scope_id                 varchar(256) NOT NULL,
    intention_id             varchar(256) NOT NULL,
    objective_fingerprint    char(64) NOT NULL,
    version                  bigint NOT NULL CHECK (version >= 0),
    status                   varchar(40) NOT NULL,
    payload                  jsonb NOT NULL,
    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    PRIMARY KEY (scope_id, intention_id),
    CHECK (updated_at >= created_at)
);
CREATE INDEX recovery_intention_status_idx ON recovery_intention(scope_id, status, updated_at DESC);
CREATE TABLE recovery_intention_history (
    scope_id                 varchar(256) NOT NULL,
    intention_id             varchar(256) NOT NULL,
    version                  bigint NOT NULL CHECK (version >= 0),
    objective_fingerprint    char(64) NOT NULL,
    status                   varchar(40) NOT NULL,
    payload                  jsonb NOT NULL,
    recorded_at              timestamptz NOT NULL,
    PRIMARY KEY (scope_id, intention_id, version),
    FOREIGN KEY (scope_id, intention_id) REFERENCES recovery_intention(scope_id, intention_id) ON DELETE RESTRICT
);

CREATE TABLE runtime_approval (
    scope_id                 varchar(256) NOT NULL,
    approval_id              varchar(256) NOT NULL,
    request_id               varchar(256) NOT NULL,
    execution_id             varchar(256) NOT NULL,
    candidate_set_id         varchar(512) NOT NULL,
    candidate_id             varchar(512) NOT NULL,
    intention_id             varchar(256) NOT NULL,
    intention_version        bigint NOT NULL CHECK (intention_version >= 0),
    action_plan_fingerprint  char(64) NOT NULL,
    requester_principal_id   varchar(256) NOT NULL,
    capability_scope         jsonb NOT NULL,
    resource_scope            jsonb NOT NULL,
    evidence_reference       varchar(1024) NOT NULL,
    policy_version           varchar(128) NOT NULL,
    status                   varchar(24) NOT NULL CHECK (status IN ('PENDING','APPROVED','DENIED','EXPIRED','REVOKED')),
    approver_principal_id    varchar(256),
    response_id              varchar(256),
    reason                   varchar(2048),
    expires_at               timestamptz NOT NULL,
    created_at               timestamptz NOT NULL,
    decided_at               timestamptz,
    revision                 bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    PRIMARY KEY (scope_id, approval_id),
    CHECK (expires_at > created_at),
    CHECK ((status = 'PENDING' AND decided_at IS NULL) OR (status <> 'PENDING' AND decided_at IS NOT NULL))
);
CREATE INDEX runtime_approval_binding_idx ON runtime_approval(scope_id, execution_id, candidate_set_id, candidate_id, intention_id, intention_version);
CREATE INDEX runtime_approval_expiry_idx ON runtime_approval(status, expires_at) WHERE status = 'PENDING';

CREATE TABLE action_operation_ledger (
    principal_id             varchar(256) NOT NULL,
    action_id                varchar(160) NOT NULL,
    idempotency_key          varchar(512) NOT NULL,
    request_fingerprint      char(64) NOT NULL,
    operation_id             varchar(256) NOT NULL,
    state                    varchar(24) NOT NULL CHECK (state IN ('IN_PROGRESS','COMPLETED','FAILED','UNKNOWN_OUTCOME')),
    retryable                boolean NOT NULL DEFAULT false,
    outcome_unknown          boolean NOT NULL DEFAULT false,
    lease_owner              varchar(256),
    lease_until              timestamptz,
    fencing_token            bigint NOT NULL CHECK (fencing_token >= 1),
    started_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    receipt_payload          jsonb,
    failure_payload          jsonb,
    PRIMARY KEY (principal_id, action_id, idempotency_key),
    UNIQUE (operation_id),
    CHECK ((state = 'COMPLETED') = (receipt_payload IS NOT NULL)),
    CHECK ((state IN ('FAILED','UNKNOWN_OUTCOME')) = (failure_payload IS NOT NULL)),
    CHECK (NOT (state = 'UNKNOWN_OUTCOME' AND retryable)),
    CHECK (updated_at >= started_at)
);
CREATE INDEX action_operation_state_idx ON action_operation_ledger(state, lease_until, updated_at);

CREATE TABLE inbox_message (
    consumer_id              varchar(256) NOT NULL,
    message_id               varchar(256) NOT NULL,
    payload_fingerprint      char(64) NOT NULL,
    payload                  jsonb NOT NULL,
    state                    varchar(24) NOT NULL CHECK (state IN ('RECEIVED','PROCESSING','PROCESSED','DEAD_LETTER')),
    attempts                 integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    claimed_by               varchar(256),
    lease_until              timestamptz,
    last_error               varchar(2048),
    received_at              timestamptz NOT NULL,
    next_attempt_at          timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    PRIMARY KEY (consumer_id, message_id)
);
CREATE INDEX inbox_claim_idx ON inbox_message(state, lease_until, received_at);

CREATE TABLE outbox_message (
    message_id               varchar(256) PRIMARY KEY,
    aggregate_id             varchar(256) NOT NULL,
    message_type             varchar(256) NOT NULL,
    payload                  jsonb NOT NULL,
    state                    varchar(24) NOT NULL CHECK (state IN ('PENDING','IN_FLIGHT','PUBLISHED','DEAD_LETTER')),
    attempts                 integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at          timestamptz NOT NULL,
    lease_owner              varchar(256),
    lease_until              timestamptz,
    last_error               varchar(2048),
    created_at               timestamptz NOT NULL,
    published_at             timestamptz
);
CREATE INDEX outbox_claim_idx ON outbox_message(state, next_attempt_at, lease_until, created_at);
