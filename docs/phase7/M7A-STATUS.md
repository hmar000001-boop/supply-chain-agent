# M7-A status — PostgreSQL foundation and durable core memory

**Status: implementation delivered and GitHub Actions CI confirmed green by the user (2026-11-18).**

## Components

- `PostgresDatabaseConfig`: validated environment-backed settings for host, port, database, credentials, SSL mode, connect/socket timeouts and application name. It accepts pgJDBC's hyphenated SSL values (`verify-ca`, `verify-full`) and its `toString()` redacts the password.
- `PostgresDataSourceFactory`: constructs the pgJDBC `PGSimpleDataSource`. It intentionally does not provide connection pooling; the bounded production pool is part of final M7-B2.
- `PostgresMigrationRunner`: applies the ordered, checksummed migration chain, records installation metadata and takes a PostgreSQL advisory lock before history-table creation and migration execution. It rejects a schema version newer than the running application.
- `PostgresWorkingMemoryRepository`: implements create/find/CAS/expiry deletion with one transaction for the header and all typed slots/evidence/hypotheses.
- `PostgresEpisodeRepository`: implements append, append-idempotently by scoped evidence reference, find, compare-and-set lifecycle transitions, historical verified lookup and expired-unverified deletion.
- `PostgresMemoryPersistence`: composes durable M2 repository ports; `connectAndMigrate(Map)` is available for host bootstrap.

## Migration sequence at M7-A acceptance

| Version | Purpose |
|---|---|
| V001 | Existing complete CoALA memory schema, loaded from the M2 memory module resource |
| V002 | Scoped episode idempotency key with deferred episode FK and cascade on retention deletion |
| V003 | Collection ordinals for exact Java collection ordering |
| V004 | Aggregate episode evidence order |
| V005 | Ordinal-based primary keys for ordered evidence/entity collections, preserving duplicate list entries |

M7-B1 subsequently adds V006 for planner-session tables; M7-B2 will extend the chain with forward-only migrations. Applied migration scripts are immutable.

## Concurrency and invariants

- Working-memory CAS locks the existing header row, validates scope and expected version, updates the header and replaces child rows atomically. Collection ordinals preserve order.
- Episode CAS locks the episode row, validates scope and expected version, updates lifecycle fields and child collections atomically.
- Episode idempotency reserves the unique `(scope_id, evidence_type, evidence_id)` key in the same transaction as episode insertion. Concurrent requests converge on the first committed episode; reusing the key with a different immutable outcome payload raises `MemoryConflictException`.
- The stored fingerprint excludes generated identity and later review metadata, including the review reference added to the episode evidence list. The M2 in-memory adapter uses the same rule.
- Only `RECORDED` episodes with expired retention can be deleted. Verified/reviewed history is retained by this repository method.

## CI acceptance

The user confirmed that the M7-A package's full GitHub Actions run is green. M7-A is accepted and remains part of the regression baseline for M7-B1 and M7-B2.
