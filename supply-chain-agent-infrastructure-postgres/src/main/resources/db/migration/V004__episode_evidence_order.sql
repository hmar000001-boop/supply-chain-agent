-- Retain the aggregate evidence order exposed by Episode.evidence(), independently of event links.
CREATE TABLE coala_episode_evidence (
    episode_id      varchar(128) NOT NULL REFERENCES coala_episode(episode_id) ON DELETE CASCADE,
    evidence_type   varchar(128) NOT NULL,
    evidence_id     varchar(256) NOT NULL,
    ordinal         integer NOT NULL CHECK (ordinal >= 0),
    PRIMARY KEY (episode_id, evidence_type, evidence_id),
    UNIQUE (episode_id, ordinal)
);
