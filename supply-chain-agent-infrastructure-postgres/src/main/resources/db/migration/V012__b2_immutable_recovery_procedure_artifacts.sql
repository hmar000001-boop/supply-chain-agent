-- M8 F-03: immutable typed payloads referenced by CoALA procedural-memory versions.
-- B2 approval does not activate a procedure; lifecycle control remains in coala_procedure_version.
CREATE TABLE coala_recovery_procedure_artifact (
    artifact_sha256 char(64) PRIMARY KEY,
    artifact_location varchar(96) NOT NULL UNIQUE,
    payload bytea NOT NULL,
    published_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_recovery_artifact_sha256 CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_recovery_artifact_location CHECK (artifact_location = 'urn:sha256:' || artifact_sha256),
    CONSTRAINT ck_recovery_artifact_payload CHECK (octet_length(payload) BETWEEN 1 AND 262144)
);
