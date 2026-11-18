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

M7-A is delivered as a change-only package. Maven/Surefire and live PostgreSQL execution remain a GitHub Actions acceptance gate; no green M7 result is claimed here.

## Planned — M7-B

- Durable Semantic and Procedural memory.
- Durable M5 planner sessions and artifacts; M6 approvals, action batches, operation steps, B2 child-step receipts, observations and intention/runtime ledgers.
- Transactional outbox/inbox, atomic operation claims, leases and reconciliation.
- Bounded connection pooling, restart recovery, concurrency/failure-injection tests, health/readiness, backup/restore and operations guidance.
