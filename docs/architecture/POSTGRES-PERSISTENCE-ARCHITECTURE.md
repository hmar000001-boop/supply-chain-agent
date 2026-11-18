# PostgreSQL persistence architecture

## Dependency direction

` supply-chain-agent-infrastructure-postgres ` implements repository ports owned by `supply-chain-agent-memory` and later the runtime/application modules. The memory/domain/mechanism/planning modules stay JDBC-free. The application composition root supplies a `DataSource` and chooses startup migration policy.

## Connection configuration

Environment variables:

- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`;
- `PGSSLMODE` (`disable`, `prefer`, `require`, `verify-ca`, `verify-full`);
- `PGCONNECT_TIMEOUT`, `PGSOCKET_TIMEOUT`, `PGAPPNAME`.

`PostgresDatabaseConfig.toString()` explicitly redacts the password. Production secrets must be injected by the deployment secret manager; `.env.example` contains no live secret. `PostgresDataSourceFactory` uses pgJDBC `PGSimpleDataSource`, which creates a physical connection per call and is not a pool. Production deployment must use the bounded pool delivered in M7-B2; the current M7-A/B1 factory remains a non-pooling pgJDBC DataSource for tests and explicit low-level composition. See the [pgJDBC DataSource documentation](https://jdbc.postgresql.org/documentation/datasource/).

## Migrations

`PostgresMigrationRunner` discovers a fixed, version-ordered resource list. For each migration:

1. Ensure the migration history table exists.
2. Acquire a session-level PostgreSQL advisory lock.
3. Read the SHA-256 checksum of any installed version.
4. Reject an already-installed version whose script checksum changed.
5. Execute the complete script in a JDBC transaction.
6. Insert the version, description, resource path, checksum, installation time and elapsed time in the same transaction.
7. Release the advisory lock.

This runner intentionally supports the repository's plain SQL migration language, not dollar-quoted procedural functions. Migrations are never silently edited after release; future corrections must be a new forward migration.

## Working memory

The repository stores an immutable snapshot header plus typed slots, slot evidence, hypotheses and hypothesis evidence. `compareAndSet` obtains a row lock, checks scope and version, updates the header and replaces child collections inside one transaction. Collection ordinals preserve the Java insertion order required by the immutable snapshot's evidence aggregation contract. `deleteExpired` is scoped to expiry and relies on schema cascades for children.

## Episodic memory

The repository stores episode headers, provenance, entity references, ordered events, event/aggregate evidence, review metadata and supersession metadata. `appendIdempotent` reserves a unique scoped evidence key and inserts the episode in one transaction; the idempotency FK is deferred to commit so the key can be reserved before the episode row. Competing application instances wait on the unique constraint and return the first committed row if the payload matches. A different payload for the same key is a conflict.

Episode lifecycle mutation uses row locking plus an expected-version predicate. Historical `findVerified` treats a superseded episode as known verified only before its supersession time. Retention removes only expired `RECORDED` episodes; the associated idempotency key cascades with the row.

## Semantic memory

`PostgresSemanticFactRepository` implements the M2 semantic repository port. It persists typed values, provenance, valid-time bounds, recorded/activation/supersession/rejection knowledge times and optimistic versions. Activation acquires a transaction-scoped advisory lock on the logical `(scope, subject, predicate)` key, then locks the target row and checks overlapping active valid-time intervals. A multi-segment amendment validates contiguous coverage and activates all replacement segments while superseding the prior fact in one transaction. Bitemporal reads include a superseded row only when the requested knowledge time precedes its supersession time.

## Procedural memory

`PostgresProceduralMemoryRepository` stores append-only immutable procedure artifact versions and their lifecycle metadata. Staging serializes version-number assignment for each `(scope, procedure key)`. Draft validation/rejection uses lifecycle-revision CAS. Activation serializes by logical procedure key, retires the former active version and activates the validated replacement in one transaction. The existing partial unique index provides a final one-active-version invariant. The repository stores artifact references and SHA-256 digests, not arbitrary executable code.

## Planner-session checkpoints

`PostgresPlannerSessionStore` stores M5 planner-session identity, planner type, request fingerprint, algorithm-configuration fingerprint, revision, status, resource counters, elapsed duration, ordered ToT frontier paths and ordered MCTS tree nodes. Header and child rows are replaced in one CAS-checked transaction. Loads use repeatable-read isolation so the header and frontier/tree belong to one consistent revision. It stores search state, not an executable business command or authorization. M7-B2 persists the complete planning request/result artifacts and binds their reuse to session and snapshot identity.

## Migration chain and M7 delivery status

M7-A is CI-accepted per user confirmation. M7-B1 adds migration V006 for durable planner sessions plus semantic/procedural adapters. The six current migrations split into 49 SQL statements. Each already-applied migration is immutable by checksum; future changes use a new forward migration.

M7-B2 is the final infrastructure delivery and must add the remaining durable M5/M6 artifacts, approvals, action ledger and step receipts, B2 child-step receipts, observations, outbox/inbox, reconciliation, PostgreSQL checkpoint bootstrap, bounded pooling, health/metrics, backup/restore and failure-injection evidence. The exact closure criteria are maintained in `docs/phase7/M7-STATUS.md`.

LangGraph4j checkpoints remain execution-resumption state, not a transaction manager or exactly-once guarantee for external effects. External operations require an independent idempotency ledger, receipt persistence and reconciliation. Outbox delivery is at least once; consumers must be idempotent.
