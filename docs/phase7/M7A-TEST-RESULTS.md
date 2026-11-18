# M7-A test and validation record

## Added tests

Ten new JUnit test methods are defined across the M2 and PostgreSQL modules: six deterministic tests that do not require a database and four PostgreSQL integration tests gated by `-Dphase0.postgres=true`.

- `PostgresMigrationRunnerParsingTest`: comments, quoted semicolons and unterminated SQL literal/comment handling.
- `PostgresDatabaseConfigTest`: deployment setting parsing, malformed value rejection and password redaction.
- `EpisodePayloadFingerprintTest`: stable hash across generated episode identities and later verification metadata; conflicting immutable payloads produce different hashes.
- `EpisodicMemoryServiceTest.idempotentRecordRetryAfterVerificationReturnsTheExistingReviewedEpisode`: keeps the in-memory M2 reference adapter behavior aligned with PostgreSQL when an original idempotent write is retried after verification adds review evidence.
- `PostgresMemoryPersistenceIntegrationTest` (requires `-Dphase0.postgres=true` and the CI PostgreSQL service): idempotent migration rerun; Working Memory typed-value/evidence/hypothesis round-trip, collection-order and duplicate-reference preservation, CAS conflict and immediate-expiry boundary; Episode idempotent append, conflicting reuse, event/entity/evidence order and duplicate-reference preservation, review lifecycle, historical verified queries, supersession and retention deletion.

## Local authoring checks

| Check | Result |
|---|---|
| Java 21 compilation of M2 + M7-A production sources with `-Xlint:all` | Passed with temporary compile-only pgJDBC API stub |
| Java 21 compilation of M7-A test sources | Passed with temporary JUnit API stubs |
| SQL migration statement parsing (V001–V005) | Passed; 22, 2, 7, 1 and 12 statements respectively |
| TEXT_LIST JSON codec round-trip | Passed |
| Maven Surefire/JUnit Platform execution | Not run locally |
| Live PostgreSQL 16.4 integration | Not run locally |

The temporary stubs are not shipped. Additional standalone behavioral harnesses passed for the idempotency fingerprint (including replay after review), JSON TEXT_LIST codec, five-migration SQL splitting (44 statements), and `PGSSLMODE=verify-full` parsing/credential redaction. GitHub Actions is the authoritative validation for the actual pgJDBC API, PostgreSQL schema behavior and Surefire execution.
