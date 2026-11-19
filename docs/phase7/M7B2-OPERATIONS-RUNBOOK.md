# M7-B2 PostgreSQL operations runbook

This runbook accompanies the M7-B2 implementation patch. It documents operational boundaries; it is not evidence that the procedures have been executed successfully.

## Database migration and rollout

1. Provision PostgreSQL and a dedicated application role with only the privileges required by the deployment's migration and runtime policies. Keep migration privileges separate from the runtime role where the deployment requires stricter separation.
2. Supply connection settings through the deployment secret/configuration system (`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE`, `PGCONNECT_TIMEOUT`, `PGSOCKET_TIMEOUT`, and `PGAPPNAME`). Do not commit credentials. Prefer `verify-full` with correctly provisioned CA/trust configuration for production; `require` encrypts transport but does not by itself establish hostname identity.
3. Start one or more application instances using the same migration-capable release. The migration runner obtains a PostgreSQL advisory lock, checks the installed schema version and checksums, and applies V007 and V008 transactionally. Concurrent starters serialize on that lock.
4. Applied migrations are immutable. Do not edit V001–V008 after installation. Correct a schema defect with a new forward migration. A database schema newer than the application's supported migration version must be treated as incompatible and the application must not start against it.
5. Before rollback of application binaries, verify that the older binary supports the already-applied schema. This release does not provide automatic destructive DDL rollback. Database rollback is a restore/PITR operation and must be planned separately.

## Connection pool and lifecycle

`PostgresAgentPersistence.connectAndMigrate(...)` constructs a bounded HikariCP pool using `PostgresPoolSettings.productionDefaults()`, applies migrations, and exposes memory, planner-session, runtime, readiness and liveness accessors. The default pool is capped at 10 connections with 2 idle connections and a 10-second acquisition timeout. Tune through a deliberate code/configuration change against the PostgreSQL connection budget; do not multiply pool sizes by instance count without accounting for other clients.

Close the composition root when the application host shuts down. `PostgresAgentPersistence.close()` closes the Hikari pool when it owns that pool. When a caller injects an externally managed `DataSource`, that owner retains lifecycle responsibility.

## Runtime identity, authority and external effects

- Create a unique execution identity for every graph execution and use `PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution(scopeId, executionId)` for a stable per-execution graph thread ID across restarts.
- Treat the LangGraph4j checkpoint as execution position only. Durable approvals, action plans, receipts, intentions and the M3 operation ledger are the business authority and must be reloaded independently.
- Supply `PostgresRuntimeCodecs` from the host using explicit typed codecs. Do not use Java native object deserialization or unrestricted polymorphic class loading for persisted JSON payloads.
- The host must provide authenticated principal identities and current authorization policy. The approval store records the decision and its exact binding; it does not authenticate a person or mint a trusted identity.
- Before dispatch after resume, reload and revalidate the current approval, candidate/intention version and action-plan binding. An expired, revoked, stale or mismatched approval must stop dispatch.
- Never blindly redispatch an operation in `UNKNOWN_OUTCOME`. Reconcile with the external system using its idempotency/query/correlation contract; when no authoritative query exists, route to human reconciliation or a compensating business workflow.
- Inbox/outbox delivery is at-least-once. Consumers must deduplicate using stable message identity and payload fingerprint. Do not claim global exactly-once effects across PostgreSQL and an external system.

## Backup

The repository provides `scripts/postgres/backup.sh <destination.dump>`.

The script requires `PGHOST`, `PGDATABASE`, `PGUSER` and `PGPASSWORD`, creates a PostgreSQL custom-format dump, verifies the dump catalog with `pg_restore --list`, writes a SHA-256 sidecar, and restricts output file permissions. Schedule backups according to the deployment's recovery-point objective and copy the dump and checksum to protected off-host storage. Use PostgreSQL WAL archiving and base backups when point-in-time recovery (PITR) is required; `pg_dump` alone does not provide continuous PITR.

## Restore verification

The repository provides `scripts/postgres/restore-verify.sh <backup.dump>`.

1. Provision a new, empty, isolated restore target. Never point this script at production or a non-empty database.
2. Set `PGDATABASE` to that target and set `PGRESTORE_TARGET_CONFIRM` to exactly the same database name. The script refuses to proceed if they differ or if the target contains user relations.
3. Provide the normal PostgreSQL connection environment and execute the script with a readable backup file. It checks the checksum sidecar when present, validates the dump catalog, restores in one transaction, and checks that the target is empty before restore, verifies schema version 8 and confirms representative runtime tables.
4. Record restore duration, recovered schema version, backup timestamp, and any application-level smoke results in the deployment's recovery record. The script's schema/table check is a restore smoke check, not a full business-data integrity proof.

For PITR, restore the base backup and WAL to a separate recovery instance using the PostgreSQL-version-specific documented procedure. Validate the target before redirecting application traffic.

## Retention, maintenance and monitoring

- Define retention policies for runtime artifacts, planner artifacts, approvals, observations, receipts, inbox messages and outbox messages with business/legal requirements before enabling deletion jobs. No automatic destructive retention scheduler is enabled by this patch.
- Monitor PostgreSQL storage, dead tuples, index growth, connection saturation and transaction age with the platform's database observability stack.
- Poll `PostgresOperationalHealth` for liveness/readiness and `PostgresRuntimePersistence.metrics()` for aggregate operation, unknown-outcome, approval, outbox and inbox state counts. Metrics intentionally avoid returning payload bodies or credentials.
- Alert on sustained readiness failures, growing outbox backlog, expired leases, dead-letter messages, unknown external outcomes, and old unresolved reconciliation records.
- Review database logs and application diagnostics for secret/payload exposure before production rollout.

## Acceptance boundary

The M7-B2 integration tests are opt-in through `-Dphase0.postgres=true` and must run against the CI PostgreSQL service. Local implementation does not prove live PostgreSQL behavior. Do not mark M7 complete until the full CI job, migration upgrade path, backup/restore smoke, crash/failure-injection and multi-instance concurrency tests have actual passing evidence. Legacy unscoped records must be explicitly reconciled before any production tenant is allowed to reuse the same idempotency keys or message identities.
