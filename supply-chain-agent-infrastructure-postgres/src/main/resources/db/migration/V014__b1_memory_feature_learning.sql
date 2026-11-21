-- Durable B1 accessibility, cue-association and downstream-utility features.
-- Features are scoped to one immutable CoALA memory revision; outcomes are applied with CAS.
CREATE TABLE b1_memory_feature_state (
    scope_id              TEXT NOT NULL,
    memory_kind           TEXT NOT NULL,
    memory_item_id        TEXT NOT NULL,
    memory_item_version   BIGINT NOT NULL CHECK (memory_item_version >= 0),
    revision              BIGINT NOT NULL CHECK (revision >= 1),
    payload               JSONB NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scope_id, memory_kind, memory_item_id, memory_item_version),
    CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX b1_memory_feature_updated_idx
    ON b1_memory_feature_state (scope_id, updated_at DESC);
