-- Atomic cross-instance idempotency for append-only episodic memory.
-- Deferred FK permits reserving an idempotency key before inserting the episode in the same transaction.
CREATE TABLE coala_episode_idempotency (
    scope_id        varchar(256) NOT NULL,
    evidence_type   varchar(128) NOT NULL,
    evidence_id     varchar(256) NOT NULL,
    episode_id      varchar(128) NOT NULL,
    payload_sha256  char(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    created_at      timestamptz NOT NULL,
    PRIMARY KEY (scope_id, evidence_type, evidence_id),
    CONSTRAINT fk_coala_episode_idempotency_episode
        FOREIGN KEY (episode_id) REFERENCES coala_episode(episode_id) ON DELETE CASCADE
        DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_coala_episode_idempotency_episode ON coala_episode_idempotency (episode_id);
