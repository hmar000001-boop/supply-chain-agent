# ADR-0008 — PostgreSQL persistence boundary and migration strategy

- **Status:** Accepted for M7 implementation; M7-A delivered, CI pending
- **Date:** 2026-11-17

## Context

M0–M6 deliberately use framework-neutral repository ports and in-memory reference adapters for deterministic development. The complete agent now needs durable CoALA memory, planner state, approvals, action receipts, observations and recoverable operation records. Graph checkpoints alone cannot guarantee exactly-once business effects or replace authoritative repositories.

## Decision

1. Add `supply-chain-agent-infrastructure-postgres` as a separate infrastructure module. The domain, memory, mechanisms, planning and runtime contracts remain free of JDBC types.
2. Use PostgreSQL as the durable relational store and the already pinned pgJDBC driver.
3. Use forward-only, ordered SQL migrations with SHA-256 checksums, an installation history table and a PostgreSQL advisory lock. A migration script that has been installed is immutable; changes are added as a new version.
4. Implement durable repository adapters behind existing M2/M6 ports. Multi-row lifecycle changes use explicit JDBC transactions, row locks/optimistic versions and unique constraints.
5. Preserve evidence/provenance, valid-time/knowledge-time semantics and insertion ordering; do not collapse CoALA memories into one opaque serialized blob.
6. Treat checkpoint state, business records, CoALA memory, planner sessions, approvals and external-operation receipts as separate persistence responsibilities.
7. M7-A delivers migration execution plus Working/Episodic repositories. M7-B delivers Semantic/Procedural repositories, runtime stores, outbox/inbox, pool integration and crash/concurrency/reconciliation hardening.

## Consequences

- The M2 API remains independently testable with in-memory adapters.
- PostgreSQL uniqueness/locking semantics, not JVM `synchronized` blocks, govern cross-instance idempotency and concurrent mutations.
- Database migration checksums provide drift detection, but deployment still needs a controlled migration role and a runtime role with narrower DML permissions; role provisioning is documented/implemented in M7-B.
- `PGSimpleDataSource` is a non-pooling factory for M7-A composition and integration tests. Production pooling is an M7-B acceptance item.
- Exactly-once external execution is not claimed. M7-B must provide an operation ledger and reconciliation/outbox boundaries.
