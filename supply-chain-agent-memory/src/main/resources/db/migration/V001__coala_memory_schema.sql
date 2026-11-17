-- M2 schema contract for the four CoALA memory responsibilities.
-- This file is intentionally not wired to an automatic migration runner yet.
-- M7 will add the selected migration dependency, migration execution and JDBC adapters.

CREATE TABLE coala_working_memory (
    memory_id       varchar(128) PRIMARY KEY,
    scope_id        varchar(256) NOT NULL,
    version         bigint NOT NULL CHECK (version >= 0),
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    expires_at      timestamptz NOT NULL,
    CHECK (updated_at >= created_at),
    CHECK (expires_at > updated_at)
);
CREATE INDEX ix_coala_working_memory_scope_expiry
    ON coala_working_memory (scope_id, expires_at);

CREATE TABLE coala_working_memory_slot (
    memory_id           varchar(128) NOT NULL REFERENCES coala_working_memory(memory_id) ON DELETE CASCADE,
    slot_key            varchar(256) NOT NULL,
    value_type          varchar(32) NOT NULL CHECK (value_type IN ('TEXT','DECIMAL','FLAG','TIMESTAMP','ENTITY_LINK','TEXT_LIST')),
    text_value          text,
    decimal_value       numeric,
    flag_value          boolean,
    timestamp_value     timestamptz,
    entity_type         varchar(128),
    entity_id           varchar(256),
    text_list_value     jsonb,
    updated_at          timestamptz NOT NULL,
    PRIMARY KEY (memory_id, slot_key),
    CHECK (
        (value_type = 'TEXT' AND text_value IS NOT NULL AND decimal_value IS NULL AND flag_value IS NULL AND timestamp_value IS NULL AND entity_type IS NULL AND entity_id IS NULL AND text_list_value IS NULL) OR
        (value_type = 'DECIMAL' AND text_value IS NULL AND decimal_value IS NOT NULL AND flag_value IS NULL AND timestamp_value IS NULL AND entity_type IS NULL AND entity_id IS NULL AND text_list_value IS NULL) OR
        (value_type = 'FLAG' AND text_value IS NULL AND decimal_value IS NULL AND flag_value IS NOT NULL AND timestamp_value IS NULL AND entity_type IS NULL AND entity_id IS NULL AND text_list_value IS NULL) OR
        (value_type = 'TIMESTAMP' AND text_value IS NULL AND decimal_value IS NULL AND flag_value IS NULL AND timestamp_value IS NOT NULL AND entity_type IS NULL AND entity_id IS NULL AND text_list_value IS NULL) OR
        (value_type = 'ENTITY_LINK' AND text_value IS NULL AND decimal_value IS NULL AND flag_value IS NULL AND timestamp_value IS NULL AND entity_type IS NOT NULL AND entity_id IS NOT NULL AND text_list_value IS NULL) OR
        (value_type = 'TEXT_LIST' AND text_value IS NULL AND decimal_value IS NULL AND flag_value IS NULL AND timestamp_value IS NULL AND entity_type IS NULL AND entity_id IS NULL AND text_list_value IS NOT NULL AND jsonb_typeof(text_list_value) = 'array')
    )
);

CREATE TABLE coala_working_memory_slot_evidence (
    memory_id       varchar(128) NOT NULL,
    slot_key        varchar(256) NOT NULL,
    evidence_type   varchar(128) NOT NULL,
    evidence_id     varchar(256) NOT NULL,
    PRIMARY KEY (memory_id, slot_key, evidence_type, evidence_id),
    FOREIGN KEY (memory_id, slot_key)
        REFERENCES coala_working_memory_slot(memory_id, slot_key) ON DELETE CASCADE
);

CREATE TABLE coala_working_memory_hypothesis (
    memory_id       varchar(128) NOT NULL REFERENCES coala_working_memory(memory_id) ON DELETE CASCADE,
    hypothesis_id   varchar(128) NOT NULL,
    statement       text NOT NULL,
    status          varchar(24) NOT NULL CHECK (status IN ('PROPOSED','SUPPORTED','REFUTED','WITHDRAWN')),
    confidence      double precision NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    PRIMARY KEY (memory_id, hypothesis_id),
    CHECK (updated_at >= created_at)
);
-- The application/repository adapter enforces that SUPPORTED hypotheses have at least one SUPPORTS row
-- and REFUTED hypotheses have at least one CONTRADICTS row in the same transaction.
CREATE TABLE coala_working_memory_hypothesis_evidence (
    memory_id       varchar(128) NOT NULL,
    hypothesis_id   varchar(128) NOT NULL,
    evidence_role   varchar(16) NOT NULL CHECK (evidence_role IN ('SUPPORTS','CONTRADICTS')),
    evidence_type   varchar(128) NOT NULL,
    evidence_id     varchar(256) NOT NULL,
    PRIMARY KEY (memory_id, hypothesis_id, evidence_role, evidence_type, evidence_id),
    FOREIGN KEY (memory_id, hypothesis_id)
        REFERENCES coala_working_memory_hypothesis(memory_id, hypothesis_id) ON DELETE CASCADE
);

CREATE TABLE coala_episode (
    episode_id          varchar(128) PRIMARY KEY,
    scope_id            varchar(256) NOT NULL,
    version             bigint NOT NULL CHECK (version >= 0),
    title               text NOT NULL,
    summary             text NOT NULL,
    outcome             varchar(32) NOT NULL CHECK (outcome IN ('SUCCESS','PARTIAL_SUCCESS','FAILURE','ABORTED','UNKNOWN')),
    status              varchar(24) NOT NULL CHECK (status IN ('RECORDED','VERIFIED','REJECTED','SUPERSEDED')),
    started_at          timestamptz NOT NULL,
    completed_at        timestamptz NOT NULL,
    recorded_at         timestamptz NOT NULL,
    retention_until     timestamptz,
    provenance_source   varchar(32) NOT NULL CHECK (provenance_source IN ('AUTHORITATIVE_SYSTEM','HUMAN_VERIFIED','VERIFIED_DOCUMENT','VERIFIED_EPISODE','SIMULATOR','UNVERIFIED_DOCUMENT','MODEL_DERIVED')),
    provenance_source_id varchar(256) NOT NULL,
    provenance_observed_at timestamptz NOT NULL,
    provenance_source_version varchar(256),
    reviewed_by         varchar(256),
    reviewed_at         timestamptz,
    review_evidence_type varchar(128),
    review_evidence_id  varchar(256),
    superseded_at       timestamptz,
    CHECK (completed_at >= started_at),
    CHECK (recorded_at >= completed_at),
    CHECK (provenance_observed_at <= recorded_at),
    CHECK (
        (status = 'RECORDED' AND retention_until IS NOT NULL AND reviewed_by IS NULL AND reviewed_at IS NULL
            AND review_evidence_type IS NULL AND review_evidence_id IS NULL AND superseded_at IS NULL)
        OR
        (status = 'VERIFIED' AND retention_until IS NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewed_at >= recorded_at AND review_evidence_type IS NOT NULL AND review_evidence_id IS NOT NULL
            AND superseded_at IS NULL)
        OR
        (status = 'REJECTED' AND retention_until IS NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewed_at >= recorded_at AND review_evidence_type IS NOT NULL AND review_evidence_id IS NOT NULL
            AND superseded_at IS NULL)
        OR
        (status = 'SUPERSEDED' AND retention_until IS NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewed_at >= recorded_at AND review_evidence_type IS NOT NULL AND review_evidence_id IS NOT NULL
            AND superseded_at IS NOT NULL AND superseded_at >= reviewed_at)
    )
);
CREATE INDEX ix_coala_episode_scope_status_time
    ON coala_episode (scope_id, status, completed_at DESC);
CREATE INDEX ix_coala_episode_retention
    ON coala_episode (retention_until) WHERE status = 'RECORDED';

CREATE TABLE coala_episode_entity (
    episode_id      varchar(128) NOT NULL REFERENCES coala_episode(episode_id) ON DELETE CASCADE,
    entity_type     varchar(128) NOT NULL,
    entity_id       varchar(256) NOT NULL,
    PRIMARY KEY (episode_id, entity_type, entity_id)
);
CREATE INDEX ix_coala_episode_entity_lookup
    ON coala_episode_entity (entity_type, entity_id, episode_id);

CREATE TABLE coala_episode_event (
    episode_id      varchar(128) NOT NULL REFERENCES coala_episode(episode_id) ON DELETE CASCADE,
    event_index     integer NOT NULL CHECK (event_index >= 0),
    event_type      varchar(128) NOT NULL,
    description     text NOT NULL,
    occurred_at     timestamptz NOT NULL,
    PRIMARY KEY (episode_id, event_index)
);
-- Episode-level evidence is distinct from event-level evidence. Review evidence is also stored in
-- coala_episode.review_evidence_type/review_evidence_id so the lifecycle transition remains auditable.
CREATE TABLE coala_episode_level_evidence (
    episode_id      varchar(128) NOT NULL REFERENCES coala_episode(episode_id) ON DELETE CASCADE,
    evidence_type   varchar(128) NOT NULL,
    evidence_id     varchar(256) NOT NULL,
    PRIMARY KEY (episode_id, evidence_type, evidence_id)
);
CREATE TABLE coala_episode_event_evidence (
    episode_id      varchar(128) NOT NULL,
    event_index     integer NOT NULL,
    evidence_type   varchar(128) NOT NULL,
    evidence_id     varchar(256) NOT NULL,
    PRIMARY KEY (episode_id, event_index, evidence_type, evidence_id),
    FOREIGN KEY (episode_id, event_index)
        REFERENCES coala_episode_event(episode_id, event_index) ON DELETE CASCADE
);

CREATE TABLE coala_semantic_fact (
    fact_id             varchar(128) PRIMARY KEY,
    scope_id            varchar(256) NOT NULL,
    version             bigint NOT NULL CHECK (version >= 0),
    subject_type        varchar(128) NOT NULL,
    subject_id          varchar(256) NOT NULL,
    predicate           varchar(256) NOT NULL,
    value_type          varchar(32) NOT NULL CHECK (value_type IN ('TEXT','DECIMAL','FLAG','TIMESTAMP','ENTITY_LINK')),
    text_value          text,
    decimal_value       numeric,
    flag_value          boolean,
    timestamp_value     timestamptz,
    entity_type         varchar(128),
    entity_id           varchar(256),
    valid_from          timestamptz NOT NULL,
    valid_until         timestamptz,
    recorded_at         timestamptz NOT NULL,
    status              varchar(24) NOT NULL CHECK (status IN ('PROPOSED','ACTIVE','SUPERSEDED','REJECTED')),
    provenance_source   varchar(32) NOT NULL CHECK (provenance_source IN ('AUTHORITATIVE_SYSTEM','HUMAN_VERIFIED','VERIFIED_DOCUMENT','VERIFIED_EPISODE','SIMULATOR','UNVERIFIED_DOCUMENT','MODEL_DERIVED')),
    provenance_source_id varchar(256) NOT NULL,
    provenance_observed_at timestamptz NOT NULL,
    provenance_source_version varchar(256),
    activated_by        varchar(256),
    activated_at        timestamptz,
    activation_evidence_type varchar(128),
    activation_evidence_id varchar(256),
    supersedes_fact_id  varchar(128) REFERENCES coala_semantic_fact(fact_id),
    superseded_at       timestamptz,
    rejected_by         varchar(256),
    rejected_at         timestamptz,
    rejection_evidence_type varchar(128),
    rejection_evidence_id varchar(256),
    CHECK (valid_until IS NULL OR valid_until > valid_from),
    CHECK (recorded_at >= provenance_observed_at),
    CHECK (
        (value_type = 'TEXT' AND text_value IS NOT NULL AND decimal_value IS NULL AND flag_value IS NULL AND timestamp_value IS NULL AND entity_type IS NULL AND entity_id IS NULL) OR
        (value_type = 'DECIMAL' AND text_value IS NULL AND decimal_value IS NOT NULL AND flag_value IS NULL AND timestamp_value IS NULL AND entity_type IS NULL AND entity_id IS NULL) OR
        (value_type = 'FLAG' AND text_value IS NULL AND decimal_value IS NULL AND flag_value IS NOT NULL AND timestamp_value IS NULL AND entity_type IS NULL AND entity_id IS NULL) OR
        (value_type = 'TIMESTAMP' AND text_value IS NULL AND decimal_value IS NULL AND flag_value IS NULL AND timestamp_value IS NOT NULL AND entity_type IS NULL AND entity_id IS NULL) OR
        (value_type = 'ENTITY_LINK' AND text_value IS NULL AND decimal_value IS NULL AND flag_value IS NULL AND timestamp_value IS NULL AND entity_type IS NOT NULL AND entity_id IS NOT NULL)
    ),
    CHECK (
        (status = 'PROPOSED' AND activated_by IS NULL AND activated_at IS NULL AND activation_evidence_type IS NULL
            AND activation_evidence_id IS NULL AND supersedes_fact_id IS NULL AND superseded_at IS NULL
            AND rejected_by IS NULL AND rejected_at IS NULL AND rejection_evidence_type IS NULL AND rejection_evidence_id IS NULL)
        OR
        (status = 'ACTIVE' AND activated_by IS NOT NULL AND activated_at IS NOT NULL AND activated_at >= recorded_at
            AND activation_evidence_type IS NOT NULL AND activation_evidence_id IS NOT NULL
            AND superseded_at IS NULL AND rejected_by IS NULL AND rejected_at IS NULL
            AND rejection_evidence_type IS NULL AND rejection_evidence_id IS NULL)
        OR
        (status = 'SUPERSEDED' AND activated_by IS NOT NULL AND activated_at IS NOT NULL AND activated_at >= recorded_at
            AND activation_evidence_type IS NOT NULL AND activation_evidence_id IS NOT NULL
            AND superseded_at IS NOT NULL AND superseded_at >= activated_at
            AND rejected_by IS NULL AND rejected_at IS NULL AND rejection_evidence_type IS NULL AND rejection_evidence_id IS NULL)
        OR
        (status = 'REJECTED' AND rejected_by IS NOT NULL AND rejected_at IS NOT NULL AND rejected_at >= recorded_at
            AND rejection_evidence_type IS NOT NULL AND rejection_evidence_id IS NOT NULL
            AND activated_at IS NULL AND activated_by IS NULL AND activation_evidence_type IS NULL AND activation_evidence_id IS NULL
            AND superseded_at IS NULL AND supersedes_fact_id IS NULL)
    )
);
CREATE INDEX ix_coala_semantic_fact_scope_subject_predicate
    ON coala_semantic_fact (scope_id, subject_type, subject_id, predicate, status);
CREATE INDEX ix_coala_semantic_fact_valid_time
    ON coala_semantic_fact (scope_id, subject_type, subject_id, valid_from, valid_until);
CREATE INDEX ix_coala_semantic_fact_knowledge_time
    ON coala_semantic_fact (scope_id, recorded_at, activated_at, superseded_at);
CREATE INDEX ix_coala_semantic_fact_retention
    ON coala_semantic_fact (superseded_at) WHERE status = 'SUPERSEDED';

CREATE TABLE coala_procedure_version (
    procedure_version_id varchar(128) PRIMARY KEY,
    scope_id            varchar(256) NOT NULL,
    procedure_key       varchar(256) NOT NULL,
    version_number      integer NOT NULL CHECK (version_number >= 1),
    kind                varchar(32) NOT NULL CHECK (kind IN ('RUNBOOK','CAPABILITY_DEFINITION','PROMPT_SCHEMA','PLANNING_STRATEGY','VALIDATION_POLICY')),
    description         text NOT NULL,
    artifact_location   text NOT NULL,
    artifact_sha256     char(64) NOT NULL CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$'),
    provenance_source   varchar(32) NOT NULL CHECK (provenance_source IN ('AUTHORITATIVE_SYSTEM','HUMAN_VERIFIED','VERIFIED_DOCUMENT','VERIFIED_EPISODE','SIMULATOR','UNVERIFIED_DOCUMENT','MODEL_DERIVED')),
    provenance_source_id varchar(256) NOT NULL,
    provenance_observed_at timestamptz NOT NULL,
    provenance_recorded_at timestamptz NOT NULL,
    provenance_source_version varchar(256),
    status              varchar(24) NOT NULL CHECK (status IN ('DRAFT','VALIDATED','ACTIVE','RETIRED','REJECTED')),
    lifecycle_revision  bigint NOT NULL CHECK (lifecycle_revision >= 0),
    created_at          timestamptz NOT NULL,
    created_by          varchar(256) NOT NULL,
    validated_at        timestamptz,
    validated_by        varchar(256),
    activated_at        timestamptz,
    activated_by        varchar(256),
    retired_at          timestamptz,
    rejected_at         timestamptz,
    rejected_by         varchar(256),
    predecessor_id      varchar(128) REFERENCES coala_procedure_version(procedure_version_id),
    UNIQUE (scope_id, procedure_key, version_number),
    CHECK (provenance_recorded_at = created_at AND provenance_observed_at <= provenance_recorded_at),
    CHECK (
        (status = 'DRAFT' AND validated_at IS NULL AND validated_by IS NULL AND activated_at IS NULL AND activated_by IS NULL
            AND retired_at IS NULL AND rejected_at IS NULL AND rejected_by IS NULL)
        OR
        (status = 'VALIDATED' AND validated_at IS NOT NULL AND validated_at >= created_at AND validated_by IS NOT NULL
            AND activated_at IS NULL AND activated_by IS NULL AND retired_at IS NULL AND rejected_at IS NULL AND rejected_by IS NULL)
        OR
        (status = 'ACTIVE' AND validated_at IS NOT NULL AND validated_at >= created_at AND validated_by IS NOT NULL
            AND activated_at IS NOT NULL AND activated_at >= validated_at AND activated_by IS NOT NULL
            AND retired_at IS NULL AND rejected_at IS NULL AND rejected_by IS NULL)
        OR
        (status = 'RETIRED' AND validated_at IS NOT NULL AND validated_at >= created_at AND validated_by IS NOT NULL
            AND activated_at IS NOT NULL AND activated_at >= validated_at AND activated_by IS NOT NULL
            AND retired_at IS NOT NULL AND retired_at >= activated_at AND rejected_at IS NULL AND rejected_by IS NULL)
        OR
        (status = 'REJECTED' AND validated_at IS NULL AND validated_by IS NULL AND activated_at IS NULL AND activated_by IS NULL
            AND retired_at IS NULL AND rejected_at IS NOT NULL AND rejected_at >= created_at AND rejected_by IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_coala_procedure_one_active_version
    ON coala_procedure_version (scope_id, procedure_key) WHERE status = 'ACTIVE';
CREATE INDEX ix_coala_procedure_versions
    ON coala_procedure_version (scope_id, procedure_key, version_number DESC);

-- Important adapter contract for M7:
-- 1. Use transactions and optimistic version predicates for every lifecycle mutation.
-- 2. Activate/replace semantic facts and procedural versions atomically.
-- 3. Serialize conflicting semantic-key updates (e.g. transaction advisory lock or SERIALIZABLE retry)
--    so different application instances cannot bypass in-memory repository conflict checks.
-- 4. Keep verified episodes and procedural version history; retention only purges explicitly eligible states.
