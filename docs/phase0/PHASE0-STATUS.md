# Phase 0 status — completed 2026-11-17

## Acceptance evidence

The author-reported GitHub Actions run completed successfully after the following source corrections:

- Maven 3.9.16 bootstrap uses a pinned Apache distribution and checksum.
- LangGraph4j artifacts use the aligned, published 1.8.20 BOM/core/PostgreSQL saver set.
- SLF4J modules converge through the official 2.0.20 BOM; Maven Enforcer remains enabled.
- Tests use the LangGraph4j 1.8.20 `GraphInput` and `CompiledGraph` invocation APIs.
- Checkpoint/resume tests assert terminal `NodeOutput` state instead of re-reading a historical checkpoint pinned by `updateState()`.
- PostgreSQL saver initialization propagates checked `SQLException` failures instead of suppressing them.
- The application host uses `invokeFinal(GraphInput.args(...))` and fails closed if execution is interrupted.

The successful CI output reported all seven runtime tests passing, including the PostgreSQL integration test, and all five Maven reactor modules present at that time completed. The subsequent M1 patch adds the domain and simulator modules and must be validated in a new CI run.

## What Phase 0 proves

- Java 21/Maven toolchain and dependency convergence.
- LangGraph4j 1.8.20 and LangChain4j 1.23.0 compilation/API compatibility for the initial graph.
- Basic conditional routing and a bounded clarification cycle.
- In-memory checkpoint interruption, update and resume.
- PostgreSQL checkpoint creation, reload through a new saver/graph instance, and resumed execution.

## What Phase 0 does not prove

- Process-level JVM termination/restart recovery.
- Persistence of all interruption metadata in PostgreSQL Saver V1.
- Durable approval business records, authorization revalidation or exactly-once external effects.
- Parallel graph execution or every compiled-subgraph behavior.
- The full CoALA architecture, Fan & Lan mechanisms, planning algorithms or supply-chain domain.

Those are deliberately assigned to later M1/M4/M6/M7/M8 implementation and verification work. The application must keep pending approvals in separate authoritative durable records and revalidate them before any external side effect.

## Handoff

**M0 is closed. M1 is active:** implement the framework-independent supply-chain domain model and deterministic side-effect-free world simulator. Existing seven Phase 0 compatibility tests remain mandatory in all later CI runs.
