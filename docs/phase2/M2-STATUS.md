# M2 status — complete CoALA memory architecture

**Status: accepted — user-reported GitHub Actions CI passed on 2026-11-17.**

## Added capabilities

- New framework-neutral `supply-chain-agent-memory` Maven module.
- Working memory: immutable/versioned typed snapshots, evidence per slot, provisional hypotheses, optimistic updates, TTL and service-level slot removal.
- Episodic memory: structured episodes/events/outcomes, provenance, explicit verify/reject/supersede transitions, verified-only retrieval and retention.
- Semantic memory: typed facts, provenance-based admission, separate valid/knowledge time, conflict detection, audited rejection and atomic contiguous timeline replacement.
- Procedural memory: immutable artifact references with SHA-256, append-only version sequence, explicit validation/activation/audited rejection/retirement and historical knowledge-time lookup.
- Exact cross-memory retrieval service; B1 activation/ranking/utility policy remains a later Fan & Lan mechanism.
- Explicit type-specific retention policy and bounded maintenance service.
- Thread-safe in-memory reference adapters for tests and local execution.
- Initial PostgreSQL DDL schema contract for all four memory responsibilities. It is not yet wired to a migration runner or JDBC adapter; durable persistence is M7.
- Architecture documentation and ADR-0002 describing memory boundaries, authority, provenance, bitemporal semantics, lifecycle and persistence responsibilities.

## Validation performed in the authoring environment

- Compiled all M2 production sources with JDK 21 using `javac --release 21 -Xlint:all`.
- Compiled M2 test sources against a local JUnit API compatibility shim.
- Executed all **43 M2 test methods** using a local reflection harness; all passed.
- Recompiled and reran the M1 domain and simulator tests; all **31 M1 test methods** passed in the same harness.
- Combined local result: **74 passed, 0 failed** across M1 and M2.
- The local harness uses JUnit-compatible assertions and is supplementary evidence, not a substitute for Maven Surefire/JUnit Platform.
- Parsed all Maven POM files as XML and confirmed every reactor module path exists.
- Checked that memory production sources contain no LangGraph4j, LangChain4j, JDBC or PostgreSQL driver imports.
- Inspected the SQL schema statically. It is a forward schema contract only; no PostgreSQL migration runner was invoked in M2.

These authoring-environment checks are not a substitute for the real Maven reactor/JUnit/Surefire build. The next clean GitHub Actions run must confirm all M0 compatibility, M1 domain/simulator and M2 memory tests together.

## Scope boundaries

- In-memory repositories are reference/test adapters, not durable production storage.
- The PostgreSQL schema is included but not executed; M7 adds the selected migration runner and JDBC adapters.
- MemoryScopeId is an isolation key, not authorization. Application services must enforce the caller's authority.
- M2 exact retrieval does not implement B1 activation scoring, ranking, retrieval latency prediction, downstream utility or maintenance-cost optimization.
- Working memory is separate from LangGraph4j graph state/checkpoints; episodic memory is not an execution log; semantic memory is not operational source truth; procedural memory does not autonomously change production code.

## Acceptance gate

- All four memory types have typed models, repository ports and lifecycle services.
- Provenance, evidence references, scope, version and retention are enforced by the contracts.
- Semantic queries distinguish valid time from knowledge time.
- Verified episodes and active procedure versions can be retrieved at a requested knowledge time.
- Current-context evidence is recomputed when working-memory slots/hypotheses change.
- The in-memory reference adapters pass all 43 M2 lifecycle tests.
- The combined M1/M2 authoring-environment harness passes 74 tests; the seven M0 compatibility tests are unchanged.
- The user subsequently reported the complete Maven/Surefire/PostgreSQL checkpoint CI gate green on 2026-11-17; this is the acceptance evidence for M2.

## Next

M2 passed the user-reported CI gate. M3 implements explicit internal/external action contracts, observation/perception, grounding capabilities, scoped authorization, typed action results and model-provider adapters.
