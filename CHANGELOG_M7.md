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

M7-B1 is delivered; Maven/Surefire and live PostgreSQL CI are still required before acceptance.

## Planned — M7-B2 (final M7 delivery; no third infrastructure phase)

- Persist complete M5 planning artifacts and all M6 runtime stores: authorized plans, action batches/step receipts, B2 child artifacts/step receipts, observations, approvals/authority records and intention/runtime lifecycle data.
- Implement the PostgreSQL M3 operation ledger, atomic claims/leases/fencing and unknown-outcome reconciliation.
- Implement transactional outbox/inbox with deduplication, retry/backoff, dead-letter handling and idempotent consumers.
- Add the production PostgreSQL LangGraph4j checkpoint saver bootstrap, restart/resume tests and strict separation from business-operation records.
- Add bounded connection pooling, health/readiness, safe metrics, concurrency and failure-injection tests.
- Add backup/restore/PITR, migration rollout, retention and operational recovery documentation with restore validation.

M7 is complete only when M7-A, M7-B1 and M7-B2 are all CI-green and every line in `docs/phase7/M7-STATUS.md`'s closure matrix has implementation and test evidence. M8 is business/algorithm evaluation and code freeze, not a place to defer M7 infrastructure gaps.
