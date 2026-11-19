# M7 changelog

## 2026-11-17 — M7-A: PostgreSQL foundation and durable core memory

- Added `supply-chain-agent-infrastructure-postgres` with a one-way dependency on M2 memory contracts.
- Added environment-backed PostgreSQL configuration, explicit SSL modes/timeouts/application name and password-redacted diagnostics.
- Added a checksummed, transactionally applied migration runner protected by a PostgreSQL advisory lock; migration history creation is covered by the lock and newer unsupported schemas are rejected.
- Applied the existing CoALA schema as V001 and added ordered/idempotency migrations V002–V005.
- Added durable JDBC Working Memory and Episodic Memory repositories with transactional child-row updates, optimistic compare-and-set, scoped idempotency, historical verification queries and retention.
- Aligned the M2 in-memory episodic reference adapter with PostgreSQL idempotency semantics after review metadata is attached.
- Preserved Java collection order and duplicate references where M2 contracts permit them.
- Added deterministic configuration/parser/fingerprint tests and PostgreSQL-backed integration tests.
- Updated M6 records to reflect the user's confirmation that the full M6 CI is green.

The user confirmed M7-A GitHub Actions CI is green; M7-A is accepted and forms the regression baseline for M7-B1.

## 2026-11-18 — M7-B1: Durable semantic/procedural memory and planner sessions

- Added PostgreSQL semantic-fact and procedural-memory repository implementations for every M2 repository-port operation.
- Added scoped advisory-lock serialization and temporal conflict checking for semantic facts; multi-segment timeline replacement is atomic.
- Added append-only procedure version staging, lifecycle CAS, atomic activation and retirement of the previous active version.
- Added PostgreSQL planner-session checkpoint persistence for ToT frontier and MCTS tree state, preserving order and revision fingerprints.
- Added migration V006 and single-DataSource composition through `PostgresAgentPersistence`.
- Added four PostgreSQL integration scenarios covering bitemporal semantics, conflicts, procedure version history and planner-session round-trip/CAS.
- Added M7-B1 architecture, test and acceptance documentation and an explicit M7 closure matrix.

M7-B1 is delivered; the first GitHub Actions PostgreSQL run found two semantic SQL errors (both caused by an untyped nullable parameter) and one incorrect assertion about the current status of a historically active procedure. A focused correction removes the nullable SQL parameter and aligns the test with the shared in-memory/PostgreSQL `findActiveAt` contract. The user confirmed the subsequent full Maven/PostgreSQL CI rerun is green; M7-B1 is accepted.

## 2026-11-19 — M7-B2 implementation delivery (final M7 delivery; no third infrastructure phase)

- Added typed/versioned M5 planning-artifact storage and PostgreSQL runtime stores for authorized action plans, execution batches/steps, B2 child artifacts/step receipts, external observations, recovery intentions/history and impasse cases.
- Added auditable approval records bound to candidate, intention version, plan fingerprint, principal, capability/resource scope, evidence/policy version and expiry/decision status.
- Added the PostgreSQL M3 idempotency ledger with payload conflict detection, leases, fencing tokens, terminal receipts and explicit unknown-outcome handling.
- Added transactional inbox/outbox operations with deduplication, leased claims, bounded retry/backoff and dead-letter handling; delivery semantics are explicitly at-least-once.
- Added the LangGraph4j PostgreSQL saver factory and stable execution thread IDs while keeping graph checkpoints distinct from business ledgers.
- Added bounded HikariCP pool configuration, health/readiness, aggregate runtime metrics, the V007 migration and backup/isolated-restore scripts.
- Added the operational runbook in `docs/phase7/M7B2-OPERATIONS-RUNBOOK.md`.
- Added PostgreSQL integration acceptance coverage for the M7-B2 persistence, approval, recovery, messaging, scope-isolation and LangGraph4j saver-factory invariants. The two legacy migration-count assertions now use `PostgresMigrationRunner.supportedMigrationCount()`; V007 remains immutable and V008 applies the scope-key correction forward-only. **No Maven build, test execution or live PostgreSQL verification was run in the authoring environment.** Test-source inclusion is not evidence of a passing run; M7-B2 acceptance remains pending the corrected GitHub Actions result and deployment-specific recovery exercises.

M7 is complete only when M7-A, M7-B1 and M7-B2 are all CI-green and every line in `docs/phase7/M7-STATUS.md`'s closure matrix has implementation and test evidence. M8 is business/algorithm evaluation and code freeze, not a place to defer M7 infrastructure gaps.
