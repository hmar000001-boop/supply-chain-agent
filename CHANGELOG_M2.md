# M2 — CoALA memory architecture

**Milestone:** M2  
**Date:** 2026-11-17  
**Status:** Source implementation prepared; awaiting clean GitHub Actions validation.

## Scope delivered

M2 adds a standalone `supply-chain-agent-memory` Maven module for CoALA's four distinct memory responsibilities. It is framework-neutral and does not depend on LangGraph4j, LangChain4j, JDBC or the PostgreSQL driver.

### Working memory

- Immutable/versioned snapshots with typed slot values.
- Evidence references are attached to individual slots and provisional hypotheses.
- Aggregate snapshot evidence is derived from the current slots/hypotheses; replacement/removal no longer leaves stale references behind.
- Hypotheses carry explicit PROPOSED/SUPPORTED/REFUTED/WITHDRAWN status and evidence.
- Optimistic version updates, TTL enforcement and service-level slot removal.
- Expired working memory cannot be revived by a write.

### Episodic memory

- Structured episodes, ordered events, outcomes, entity references, evidence and provenance.
- Model-derived episodes require evidence, including evidence attached to an event.
- Recorded experience is not retrievable as trusted experience until explicitly verified.
- Verification, rejection and supersession are versioned/audited lifecycle transitions.
- Rejected episodes retain their review record; retention purges only eligible unverified RECORDED episodes.
- Historical knowledge-time retrieval recognizes a verified episode until its supersession time.

### Semantic memory

- Typed facts with separate valid-time and knowledge-time dimensions.
- Conservative provenance admission: model-derived/unverified sources cannot activate without human review.
- Conflict checking for overlapping, different-value active facts.
- Atomic replacement and contiguous timeline amendments.
- Rejections preserve reviewer, time and evidence.
- Stable deterministic result ordering for equal valid-time candidates.

### Procedural memory

- Immutable artifact references addressed by URI/location and SHA-256.
- Append-only version numbers and predecessor references.
- Explicit DRAFT, VALIDATED, ACTIVE, RETIRED and REJECTED states.
- Validation, activation, retirement and rejection retain actor/time metadata.
- Activation switches the active procedure atomically in the in-memory reference adapter; failed activation does not retire the prior active version.

### Cross-memory retrieval and maintenance

- Exact deterministic cross-memory retrieval across the four repository ports.
- Working-memory scope/expiry checks and knowledge-time queries for episodic, semantic and procedural memory.
- General episodic retrieval works without an entity filter.
- B1 activation/ranking/latency/utility/maintenance scoring is deliberately not implemented in M2; it remains a later Fan & Lan mechanism consuming these contracts.
- Type-specific retention purges expired working contexts, expired unverified episodes and aged superseded semantic facts. Verified episodes and procedural version history are retained.

## Persistence boundary

The module includes thread-safe in-memory reference adapters for deterministic local tests. They are not production-durable stores. An initial PostgreSQL DDL schema contract is added under `src/main/resources/db/migration/V001__coala_memory_schema.sql`, but M2 does not execute it and does not include JDBC repositories or a migration runner. M7 will implement and test PostgreSQL adapters, migration execution, optimistic concurrency, scope filtering, bitemporal queries and atomic cross-record lifecycle changes. Cross-instance semantic conflict checks must be serialized at the database transaction boundary.

## Validation evidence

- JDK 21 production source compilation with `--release 21 -Xlint:all`: passed for M1 and M2.
- M1 test-source and M2 test-source compilation with a local JUnit API compatibility shim: passed.
- Local reflection-harness result: **31 M1 + 43 M2 = 74 test methods passed, 0 failed**.
- Maven POM XML parsing and reactor module-path checks: passed.
- Static dependency-boundary check: no LangGraph4j, LangChain4j, JDBC or PostgreSQL imports in M2 production source.
- The SQL DDL was reviewed statically but not executed against PostgreSQL.
- Full Maven reactor/Maven Surefire/GitHub Actions validation remains pending. The seven M0 compatibility tests and all M1 tests must remain green in CI.

The local JUnit-compatible harness is supplementary evidence, not a claim that Maven Surefire or the real JUnit Platform ran in the authoring environment.

## File changes

See `CHANGE_MANIFEST.csv` for each added/modified path and SHA-256. No files are deleted.
