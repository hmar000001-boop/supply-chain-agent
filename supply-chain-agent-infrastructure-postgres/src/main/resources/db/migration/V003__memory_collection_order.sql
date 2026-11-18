-- Preserve immutable Java collection ordering across durable load/restart cycles.
ALTER TABLE coala_working_memory_slot ADD COLUMN ordinal integer NOT NULL DEFAULT 0 CHECK (ordinal >= 0);
ALTER TABLE coala_working_memory_slot_evidence ADD COLUMN ordinal integer NOT NULL DEFAULT 0 CHECK (ordinal >= 0);
ALTER TABLE coala_working_memory_hypothesis ADD COLUMN ordinal integer NOT NULL DEFAULT 0 CHECK (ordinal >= 0);
ALTER TABLE coala_working_memory_hypothesis_evidence ADD COLUMN ordinal integer NOT NULL DEFAULT 0 CHECK (ordinal >= 0);
ALTER TABLE coala_episode_entity ADD COLUMN ordinal integer NOT NULL DEFAULT 0 CHECK (ordinal >= 0);
ALTER TABLE coala_episode_level_evidence ADD COLUMN ordinal integer NOT NULL DEFAULT 0 CHECK (ordinal >= 0);
ALTER TABLE coala_episode_event_evidence ADD COLUMN ordinal integer NOT NULL DEFAULT 0 CHECK (ordinal >= 0);
