# ADR-0009: Durable semantic/procedural memory and planner-session checkpoints

- Status: Accepted for M7-B1 implementation; GitHub Actions validation pending
- Date: 2026-11-18

## Context

M7-A established checksummed PostgreSQL migrations and durable Working/Episodic memory. Semantic and Procedural memory still used in-memory reference repositories, and M5 ToT/MCTS search sessions were JVM-local. A process restart could therefore lose approved semantic facts, procedure lifecycle state, and resumable search frontiers/tree statistics.

## Decision

The PostgreSQL infrastructure module implements the existing `SemanticFactRepository`, `ProceduralMemoryRepository`, and `PlannerSessionStore` contracts without adding JDBC or PostgreSQL dependencies to the memory or planning modules.

Semantic lifecycle writes use transaction-scoped advisory locks keyed by `(scope, subject, predicate)` before row locks. This serializes overlapping-validity conflict checks and multi-segment timeline replacement across application instances. All expected-version updates use row locks and compare-and-set predicates. Fact revisions and temporal validity remain in separate fields.

Procedural writes use a transaction-scoped advisory lock keyed by `(scope, procedure key)`. Version staging appends to the latest immutable version; activation retires the prior active version and activates the replacement in one transaction. Lifecycle compare-and-set preserves all immutable procedure identity/artifact/provenance fields. The database's partial unique index remains a final guard against two active versions.

Planner sessions use normalized header/frontier/tree-node tables. A session save updates the header and replaces ordered child collections in one transaction under an expected revision. A load uses repeatable-read isolation so the header, frontier and tree nodes come from one database snapshot. `actionIds` and `expandedActionIds` are stored as PostgreSQL `text[]`; frontier and tree-node ordinals preserve list order. Search nodes store action paths and statistics, not live domain objects; the planner replays paths against the same fingerprinted request/world model on resume.

`PostgresAgentPersistence` is the single-datasource composition entry point for all four CoALA stores and the planner-session store. `PostgresMemoryPersistence` remains available for compatibility and exposes all four memory repositories.

## Alternatives considered

- Global `SERIALIZABLE` isolation for every operation: rejected because logical-key advisory locks provide explicit conflict domains and avoid forcing retries on unrelated scopes.
- Java object serialization: rejected because it is brittle across versions and unsafe as an untrusted persistence format.
- Persisting planner frontier/tree as one opaque blob: rejected because normalized rows preserve order, support transactional replacement, and allow bounded inspection/retention.
- Moving JDBC into M2/M5: rejected because repository interfaces are the intended architecture/infrastructure boundary.

## Consequences

- Semantic facts, procedures and planner sessions survive application-process restart.
- Conflicting semantic activations and procedure version activations serialize across JVM instances.
- M5 resume remains conditional on exact request and algorithm-configuration fingerprints; path replay remains the authority check.
- M7-B1 does not yet persist M6 planning artifacts, approvals, authorized action plans, execution batches, B2 child-step receipts or reconciled observations. Those are explicitly assigned to M7-B2, together with action idempotency, outbox/inbox, crash recovery, connection pooling, and production operations.
- Live PostgreSQL/Surefire validation remains a release gate; local source compilation with temporary API stubs does not replace it.
