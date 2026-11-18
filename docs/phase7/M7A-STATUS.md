# M7-A status — PostgreSQL foundation and durable core memory

**Implementation delivered. GitHub Actions/Maven acceptance is pending.**

## Components

- `PostgresDatabaseConfig`: validated environment-backed settings for host, port, database, credentials, SSL mode, connect/socket timeouts and application name. It accepts pgJDBC's hyphenated SSL values (`verify-ca`, `verify-full`) and its `toString()` redacts the password.
- `PostgresDataSourceFactory`: constructs the pgJDBC `PGSimpleDataSource`. It intentionally does not provide connection pooling; M7-B supplies the approved bounded pool integration.
- `PostgresMigrationRunner`: applies five ordered migration scripts, verifies SHA-256 checksums, records installation metadata and takes a PostgreSQL advisory lock before history-table creation and migration execution. It rejects a schema version newer than the running application.
- `PostgresWorkingMemoryRepository`: implements create/find/CAS/expiry deletion with one transaction for the header and all typed slots/evidence/hypotheses.
- `PostgresEpisodeRepository`: implements append, append-idempotently by scoped evidence reference, find, compare-and-set lifecycle transitions, historical verified lookup and expired-unverified deletion.
- `PostgresMemoryPersistence`: composes migration and the two durable M2 repository ports; `connectAndMigrate(Map)` is available for host bootstrap.

## Migration sequence

| Version | Purpose |
|---|---|
| V001 | Existing complete CoALA memory schema, loaded from the M2 memory module resource |
| V002 | Scoped episode idempotency key with deferred episode FK and cascade on retention deletion |
| V003 | Collection ordinals for exact Java collection ordering |
| V004 | Aggregate episode evidence order |
| V005 | Ordinal-based primary keys for ordered evidence/entity collections, preserving duplicate list entries |

The runner rejects changed checksums for already-applied versions. Each migration executes in a database transaction and is recorded in `agent_schema_history` in that same transaction. The migration lock is session-level and released after the migration batch.

## Concurrency and invariants

- Working-memory CAS locks the existing header row, validates scope and expected version, updates the header and replaces child rows atomically. The schema accepts the same immediate-expiry boundary as the framework-neutral snapshot contract; the application service itself creates positive-TTL snapshots.
- Episode CAS locks the episode row, validates scope and expected version, updates lifecycle fields and child collections atomically.
- Episode idempotency reserves the unique `(scope_id, evidence_type, evidence_id)` key in the same transaction as episode insertion. Concurrent requests converge on the first committed episode; reusing the key with a different immutable outcome payload raises `MemoryConflictException`. The stored fingerprint excludes generated identity and later review metadata (including the review reference added to the episode evidence list), so retrying the original request after review returns the reviewed lifecycle state. The in-memory M2 reference adapter uses the same payload-equality rule and has a matching regression test.
- Only `RECORDED` episodes with expired retention can be deleted. Verified and reviewed episode history is retained by this repository method.
- All SQL uses prepared statements. Database credentials are not logged by configuration diagnostics.

## Validation performed in authoring environment

- Java 21 production compilation of all M2 memory sources plus M7-A production sources with `-Xlint:all` passed using a temporary compile-only `PGSimpleDataSource` API stub. This validates Java source integration, but not the actual pgJDBC binary linkage.
- M7-A test sources compile against temporary JUnit API stubs.
- Standalone parser/codec checks passed: all five migration files split into SQL statements, and the TEXT_LIST JSON codec round-tripped quotes, backslashes, tabs, line breaks and Unicode.
- Maven execution and live PostgreSQL integration tests could not be run locally because Maven 3.9.16 is not cached and outbound DNS to Apache distribution hosts is unavailable.

The GitHub Actions job already provisions PostgreSQL 16.4 and runs `./mvnw -B -ntp -Dphase0.postgres=true clean verify`; the new tests are gated by that same property. Do not mark M7-A accepted until the uploaded package passes that full CI run.
