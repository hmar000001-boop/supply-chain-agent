# M6 status — LangGraph4j cognitive orchestration

**Status: implementation delivered; CI acceptance pending.**

The user confirmed that M5 GitHub Actions CI is green on 2026-11-17. M6 starts from that accepted M5 state. The M5 CI run log was not included in the uploaded ZIP, so this status records user-reported acceptance rather than an independently inspected workflow artifact.

## Added

- `CognitiveCycleOperations`: application-owned, typed runtime boundary for CoALA memory, internal/external actions, B1/B2/B3/B4/B6, M5 planner routing/evaluation, authorization, human approval, execution, observation and learning.
- `CognitiveCycleState`: scalar/reference-only state schema with version and bounded-cycle metadata, audit events, planner termination/candidate metadata, uncertainty and approval/human-control evidence references.
- `CognitiveCycleGraph`: main LangGraph4j graph with conditional routes and repeated cognitive-cycle loops.
- `B2ResolutionSubgraph` and its child graph state: bounded impasse-resolution lifecycle, typed findings and parent-version validation.
- Four checkpointed host interruption boundaries: clarification, external evidence, human control and approval.
- Authorization and approval-binding guards: state-changing candidates cannot use the approval-free path; approved records must match the exact selected candidate, candidate set, intention and intention version.
- Human-control identity/response-reference checks with retained audit references.
- Post-execution observation/reconciliation; unknown outcomes never route directly to another execution attempt.
- Working-memory observation update with optimistic version conflict handling; a conflict reloads the latest version and retries the already-persisted observation without repeating the external action, while failed updates stop the cycle.
- 14 graph-level tests using deterministic scripted operations.
- ADR-0006, orchestration architecture, mechanism-to-runtime traceability and the M6 test inventory/results note.

## Control flow

`initialize -> interpret -> load-context -> retrieve/B1 -> workspace/B3 -> planner/M5 -> evaluate -> B2/B4/B6 -> authorize/approve -> revalidate -> execute -> observe/reconcile -> update working memory -> record episodic outcome -> complete`, with conditional loops for more evidence, more search, impasse resolution, method reconsideration, conflicts and escalation.

The B2 child graph is a separate compiled, bounded graph. CoT/ToT/MCTS are invoked via M5 service contracts and their internal trees/frontiers remain in M5.

## Validation performed in this authoring environment

- Java 21 `javac --release 21 -Xlint:all` syntax/type-shape compilation of the new M6 source and test classes succeeded against temporary minimal API/JUnit stubs. This checks source syntax and internal Java signatures only; it does **not** validate the real LangGraph4j/JUnit binaries or graph runtime behavior.
- Maven/Surefire and the full reactor were not run. The Maven distribution is not present locally and wrapper bootstrap could not reach Maven Central in this offline environment.
- No claim is made that the 14 M6 tests have passed; see [M6-TEST-RESULTS.md](M6-TEST-RESULTS.md). GitHub Actions is the acceptance gate.

## Remaining M6 integration gate and later milestones

- **Still required for M6 acceptance:** add the production composition-root implementation of `CognitiveCycleOperations`, binding every operation to concrete M2/M3/M4/M5 services and repositories, and add end-to-end tests that run the real services through this graph. The current graph tests use deterministic scripted operations; they validate routing and interruption contracts, not the complete service composition.
- M2 memory repositories, M4 mechanism ledgers and M5 planner sessions currently use their milestone reference/in-memory implementations. Durable PostgreSQL adapters belong to M7.
- Durable approvals, operation ledger, inbox/outbox, transactional reconciliation and complete OS-process restart recovery remain M7/M8 work.
- External ERP, WMS, supplier and carrier integrations remain outside the current simulator/reference adapters.
- M8 must validate end-to-end business scenarios, concurrency, ablations, resource routing and failure recovery before the application is frozen.

## Acceptance criteria

- All 14 M6 tests pass under Maven Surefire/JUnit Platform in GitHub Actions.
- All previously accepted M0–M5 tests remain green.
- Framework dependency convergence and PostgreSQL checkpoint compatibility remain green.
- Graph interruption/resume works against the selected LangGraph4j 1.8.20 APIs.
- State-changing approval, actor identity, stale-plan revalidation, unknown-outcome reconciliation and working-memory optimistic version handling pass tests.
- No graph node directly executes model-generated arbitrary commands or bypasses M3 authorization/action dispatch.
