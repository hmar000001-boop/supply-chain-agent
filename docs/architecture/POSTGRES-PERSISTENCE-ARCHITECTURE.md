# PostgreSQL persistence architecture

## Dependency direction

` supply-chain-agent-infrastructure-postgres ` implements repository ports owned by `supply-chain-agent-memory` and later the runtime/application modules. The memory/domain/mechanism/planning modules stay JDBC-free. The application composition root supplies a `DataSource` and chooses startup migration policy.

## Connection configuration

Environment variables:

- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`;
- `PGSSLMODE` (`disable`, `prefer`, `require`, `verify-ca`, `verify-full`);
- `PGCONNECT_TIMEOUT`, `PGSOCKET_TIMEOUT`, `PGAPPNAME`.

`PostgresDatabaseConfig.toString()` explicitly redacts the password. Production secrets must be injected by the deployment secret manager; `.env.example` contains no live secret. `PostgresDataSourceFactory` uses pgJDBC `PGSimpleDataSource`, which creates a physical connection per call and is not a pool. Production deployment must wrap the DataSource in the bounded pool selected and configured in M7-B. See the [pgJDBC DataSource documentation](https://jdbc.postgresql.org/documentation/datasource/).

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

## Boundaries not yet delivered

M7-A supplies only Working and Episodic memory. M7-B adds Semantic and Procedural persistence, durable planner/M6 artifacts and ledgers, outbox/inbox, connection pooling, restart reconciliation, concurrency control, operational health and recovery procedures. LangGraph4j checkpoints remain execution-resumption state, not a transaction manager or exactly-once guarantee for external effects.
