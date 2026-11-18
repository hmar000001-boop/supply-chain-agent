# M7 status — Production persistence, security and operations

M7 is split into two deliveries. The application remains a modular Java monolith with PostgreSQL as its durable persistence store. Database infrastructure depends inward on framework-neutral memory/runtime contracts; domain and cognitive modules do not acquire JDBC dependencies.

## Delivery plan

### M7-A — PostgreSQL foundation and durable core memory (delivered; CI pending)

- Add `supply-chain-agent-infrastructure-postgres` as the infrastructure adapter module.
- Read connection settings from deployment environment variables and redact credentials in diagnostics.
- Run ordered, checksummed SQL migrations under a PostgreSQL advisory lock, including serialization of initial history-table creation and rejection of schemas newer than the running application.
- Add durable JDBC implementations for M2 Working Memory and Episodic Memory.
- Preserve typed values, hypotheses, evidence links, entity/event collections, version metadata, insertion order and duplicate references across reloads.
- Implement atomic compare-and-set, episode idempotency, historical verified-episode queries and bounded unverified retention.
- Add optional PostgreSQL integration tests to the existing PostgreSQL-backed CI job.

M7-A does not yet supply durable Semantic/Procedural stores or persistent M5/M6 runtime ledgers. Its `PGSimpleDataSource` factory creates a non-pooling DataSource; an approved bounded connection pool is part of M7-B. The local authoring environment could not download Maven 3.9.16 because outbound DNS was unavailable, so the current M7-A sources and integration tests have not been run through Maven/Surefire or against a live PostgreSQL server here.

### M7-B — Complete durable runtime, transactions and operations (not started)

- Add PostgreSQL Semantic Fact and Procedural Memory repositories with transactionally atomic activation, supersession, temporal-segment replacement and procedure version activation.
- Persist planner sessions/artifacts, authorized plans, action execution batches and per-step receipts, B2 child step artifacts, observations, approvals and intention/runtime artifacts.
- Add durable operation ledger semantics, inbox/outbox records, transaction boundaries and replay/reconciliation workers.
- Add restart-safe interrupted execution recovery, lease/claim handling, stale-plan rejection, approval expiry/revalidation and concurrency tests.
- Add a bounded production connection pool, health/readiness checks, safe metrics/traces, backup/restore and migration operations documentation.
- Add integration tests that kill/recreate adapters or processes at transaction boundaries and prove that external effects are not duplicated.

## M7 acceptance gate

- All M0–M6 tests remain green under the real Maven Surefire/JUnit Platform reactor.
- Every CoALA memory repository has a durable PostgreSQL implementation.
- Every M5/M6 runtime artifact needed for recovery has a durable repository.
- Conflicting semantic-key updates and operational resource reservations are serialized safely across application instances.
- State-changing effects are idempotent/reconciled; checkpoints are not treated as exactly-once business transactions.
- Migrations are checksummed, concurrency-safe and repeatable; backup/restore and schema compatibility are documented.
- CI proves restart, failure, concurrency, approval and reconciliation behavior using PostgreSQL.

M8 remains responsible for complete business/algorithm evaluation, mechanism ablations, full application test corpus and code freeze. Chapters 19 and 20 are written only after M8.
