# ADR-0002: Separate CoALA memory responsibilities and lifecycle contracts

- **Status:** Accepted for M2
- **Date:** 2026-11-17
- **Decision owners:** Part III application architecture

## Context

The application must implement CoALA's working, episodic, semantic and procedural memory responsibilities without treating one vector database or the LangGraph4j graph-state object as the complete cognitive memory architecture. The memory layer must remain independently testable and must not depend on a model provider or orchestration framework.

## Decision

1. Introduce `supply-chain-agent-memory` as a standalone, framework-neutral Maven module.
2. Represent each memory type with a distinct typed model, lifecycle service and repository port.
3. Use immutable/versioned working-memory snapshots and compare-and-set updates.
4. Keep episodic experience untrusted until explicit verification; only verified episodes are returned as experience.
5. Use valid-time and knowledge-time axes for semantic facts, with conservative provenance-based admission and atomic timeline replacement.
6. Store procedural memory as immutable, digest-addressed artifact references with append-only versions and explicit validation/activation/retirement.
7. Keep exact cross-memory retrieval separate from B1 activation/ranking/utility policy.
8. Include thread-safe in-memory reference adapters for deterministic tests, while deferring durable PostgreSQL adapters and migration execution to M7.
9. Include an initial PostgreSQL schema contract in M2, but do not claim it is already wired or deployed.
10. Keep LangGraph4j graph state/checkpoints, application business state and CoALA memory stores separate.

## Consequences

- The memory module can be tested without model credentials, LangGraph4j, PostgreSQL or supply-chain infrastructure.
- Later M3/M4/M6 components can depend on explicit interfaces instead of accessing storage maps or embedding memory policy in graph nodes.
- The in-memory adapters validate local lifecycle semantics but do not prove multi-process durability or database transaction behavior.
- M7 must implement transaction isolation/optimistic concurrency, scope predicates and atomic multi-record lifecycle operations in PostgreSQL.
- B1 will add retrieval selection policy on top of the memory contracts; M2 exact retrieval is deliberately not described as B1.

## Alternatives rejected

- **One generic vector store for all memory:** rejected because it obscures typed lifecycle, authority, temporal validity, review and retention rules.
- **Use LangGraph4j state as working memory:** rejected because orchestration state/checkpoints and CoALA working memory have different lifecycles and persistence guarantees.
- **Treat execution logs as episodic memory:** rejected because episodes require selection, provenance and verification before reuse as experience.
- **Allow model-written semantic/procedural memory to become active automatically:** rejected because model-derived claims and executable procedures must not become authoritative without review.
- **Add JDBC adapters in M2:** deferred to M7 so the application can first settle the contracts and schema against domain/action requirements.
