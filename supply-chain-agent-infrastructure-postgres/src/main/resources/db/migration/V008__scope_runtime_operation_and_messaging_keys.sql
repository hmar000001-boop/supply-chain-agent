-- M7-B2 closure hardening: all runtime claims and message identities are tenant/scope-bound.
-- Existing records cannot be safely assigned to a tenant after the fact. Keep them in an
-- explicit legacy scope so they are not visible to any newly scope-bound runtime instance.

ALTER TABLE action_operation_ledger
    ADD COLUMN scope_id varchar(256) NOT NULL DEFAULT '__legacy_unscoped__';
ALTER TABLE action_operation_ledger
    ALTER COLUMN scope_id DROP DEFAULT;
ALTER TABLE action_operation_ledger
    DROP CONSTRAINT action_operation_ledger_pkey;
ALTER TABLE action_operation_ledger
    ADD CONSTRAINT action_operation_ledger_pkey PRIMARY KEY (scope_id, principal_id, action_id, idempotency_key);
ALTER TABLE action_operation_ledger
    ADD CONSTRAINT action_operation_ledger_scope_nonblank CHECK (length(btrim(scope_id)) > 0);
DROP INDEX action_operation_state_idx;
CREATE INDEX action_operation_state_idx ON action_operation_ledger(scope_id, state, lease_until, updated_at);

ALTER TABLE inbox_message
    ADD COLUMN scope_id varchar(256) NOT NULL DEFAULT '__legacy_unscoped__';
ALTER TABLE inbox_message
    ALTER COLUMN scope_id DROP DEFAULT;
ALTER TABLE inbox_message
    DROP CONSTRAINT inbox_message_pkey;
ALTER TABLE inbox_message
    ADD CONSTRAINT inbox_message_pkey PRIMARY KEY (scope_id, consumer_id, message_id);
ALTER TABLE inbox_message
    ADD CONSTRAINT inbox_message_scope_nonblank CHECK (length(btrim(scope_id)) > 0);
DROP INDEX inbox_claim_idx;
CREATE INDEX inbox_claim_idx ON inbox_message(scope_id, state, lease_until, received_at, consumer_id);

ALTER TABLE outbox_message
    ADD COLUMN scope_id varchar(256) NOT NULL DEFAULT '__legacy_unscoped__';
ALTER TABLE outbox_message
    ALTER COLUMN scope_id DROP DEFAULT;
ALTER TABLE outbox_message
    DROP CONSTRAINT outbox_message_pkey;
ALTER TABLE outbox_message
    ADD CONSTRAINT outbox_message_pkey PRIMARY KEY (scope_id, message_id);
ALTER TABLE outbox_message
    ADD CONSTRAINT outbox_message_scope_nonblank CHECK (length(btrim(scope_id)) > 0);
DROP INDEX outbox_claim_idx;
CREATE INDEX outbox_claim_idx ON outbox_message(scope_id, state, next_attempt_at, lease_until, created_at, message_id);

COMMENT ON COLUMN action_operation_ledger.scope_id IS 'Tenant/runtime scope; legacy rows are isolated in __legacy_unscoped__ for explicit reconciliation.';
COMMENT ON COLUMN inbox_message.scope_id IS 'Tenant/runtime scope; inbox deduplication is scoped per consumer and message.';
COMMENT ON COLUMN outbox_message.scope_id IS 'Tenant/runtime scope; outbox claims are isolated per runtime scope.';
