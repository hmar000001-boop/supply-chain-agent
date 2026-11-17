# M6 status — LangGraph4j cognitive orchestration

**Status: graph-core CI is user-confirmed green after the M6 correction; M6 is still in progress and is split into two deliveries. M6-A (decision-service composition) is implemented; M6-B (remaining operations and full real-service graph integration) remains.**

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

## CI feedback and correction

The user's GitHub Actions log for `./mvnw -B -ntp -Dphase0.postgres=true clean verify` on 2026-11-17 showed that the M0–M5 modules passed, as did the seven runtime compatibility tests (checkpoint resume, framework compatibility and PostgreSQL checkpoint compatibility). In the 14 `CognitiveCycleGraphTest` tests, 11 passed, two failed and one errored.

The three failures were traced to two test/runtime integration issues and are corrected in the accompanying M6 CI-fix change-set:

- B2 initially mapped the parent impasse reason to `CycleContext.summary`, leaving `CycleContext.lastReason` blank. `B2ResolutionSubgraph.childContext` now preserves the initial cause in `lastReason` and then uses the latest child reason. The B2 test asserts the original cause reaches `openImpasse`.
- LangGraph4j wraps asynchronous node exceptions. The approval-binding and human-actor tests now assert that `SecurityException` occurs in the cause chain rather than requiring it to be the top-level exception. Both tests continue to assert that no external action executes.

The user subsequently confirmed that GitHub Actions CI is green after applying the correction. The corrected run log was not included, so this status records user-reported CI acceptance without inventing updated test totals. See [M6-CI-FAILURE-ANALYSIS-2026-11-17.md](M6-CI-FAILURE-ANALYSIS-2026-11-17.md) and [M6-TEST-RESULTS.md](M6-TEST-RESULTS.md).

## M6 two-delivery execution plan

### Delivery 1/2 — M6-A decision-service composition (implemented)

- `CognitiveCycleDecisionServices` now calls real M2 retrieval, B1 candidate construction/selection, B3 admission/broadcast, M5 `PlannerRouter`, and the common deterministic evaluator.
- `CognitiveCycleDecisionOperations` connects those four decision operations to the existing LangGraph4j graph while forwarding the remaining lifecycle/control methods to the host delegate.
- `PlanningArtifactStore` keeps complete M5 requests/results outside graph state; an in-memory reference adapter is provided until M7 durable persistence.
- `CognitiveCycleApplicationGraph` is the application-facing graph composition entry point.
- Five focused tests cover real M2→B1 retrieval selection, successful B3 broadcast acknowledgement, failed broadcast handling, M5 planner/artifact retrieval, and simulator-replayed candidate selection.
- Details: [M6A-STATUS.md](M6A-STATUS.md), [M6A-TEST-RESULTS.md](M6A-TEST-RESULTS.md), and [decision-service architecture](../architecture/COGNITIVE-CYCLE-DECISION-SERVICES.md).

### Delivery 2/2 — M6-B remaining service-backed operations and end-to-end graph integration (next)

- Implement the remainder of `CognitiveCycleOperations` using real `WorkingMemoryService`, `EpisodicMemoryService`, B2 `ImpasseResolutionService`, B4 `RecoveryIntentionService`, B6 `MetaControlGovernor`, and M3 `ActionDispatcher`.
- Bind approval, authenticated host responses, observation/reconciliation and operation references through explicit durable application ports.
- Replace the temporary host delegate used by the M6-A decorator with the complete application composition.
- Add real-service graph tests for complete read-only and approved state-changing cycles, B2 parent/child return, B4 method governance, B6 stop/interruption, action-dispatch idempotency and unknown-outcome reconciliation.
- Run the complete `clean verify` reactor and fix all integration failures before declaring M6 accepted.

M6 is not accepted until both deliveries pass full CI and the actual service graph has complete end-to-end coverage.
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
