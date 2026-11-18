# M6 changelog

## 2026-11-17 — LangGraph4j cognitive orchestration

- Added `CognitiveCycleOperations` and typed operation/result contracts.
- Added compact `CognitiveCycleState` for checkpointed control state.
- Added the main cyclic `CognitiveCycleGraph` and bounded `B2ResolutionSubgraph`.
- Wired four explicit host interruption/resumption boundaries.
- Added exact approval/candidate/intention binding checks and actor/response-reference checks.
- Added pre-execution plan revalidation, idempotent execution operation references, unknown-outcome reconciliation and working-memory optimistic version conflict handling with bounded retry of the persisted observation (never a repeated external action).
- Added 14 graph-level tests.
- Recorded M5 CI green based on the user's report dated 2026-11-17.

## 2026-11-17 — First CI failure correction and user-confirmed green rerun

- Corrected B2 child-context mapping so the parent impasse cause reaches `CycleContext.lastReason` and remains available through the child lifecycle.
- Strengthened the B2 test to assert that the original reason is passed to `openImpasse`.
- Updated the approval-binding and human-control identity tests to inspect LangGraph4j's asynchronous exception cause chain for `SecurityException`, while preserving the no-execution assertions.
- The user subsequently confirmed that GitHub Actions CI is green after applying this correction. The updated run log was not included in the conversation.

## 2026-11-17 — M6-A decision-service composition (delivery 1/2)

- Added a concrete decision-service composition invoking M2 retrieval, B1 activation/selection, B3 admission/broadcast, M5 planner routing and the common deterministic candidate evaluator.
- Added a graph-facing `CognitiveCycleDecisionOperations` decorator and application graph factory.
- Added a planning-artifact storage port and a thread-safe in-memory reference adapter; durable persistence remains M7 work.
- Added five focused service-composition tests for retrieval/B1, successful B3 broadcast acknowledgement, fail-closed broadcast errors, M5 planner artifact retrieval, and deterministic simulator-replayed feasible-candidate selection.
- M6-B remains required to implement the remaining B2/B4/B6/M3 operations and full real-service graph integration tests.

## 2026-11-17 — M6-B lifecycle service composition (delivery 2/2)

- Added `CognitiveCycleServiceOperations`, which composes all graph operations from the M6-A decision services and M6-B lifecycle services without a catch-all `CognitiveCycleOperations` host delegate.
- Added service-backed lifecycle integration with M2 working/episodic memory, B2 impasse resolution, B4 recovery intentions, B6 meta-control, and M3 typed action dispatch/registry.
- Added explicit `CognitiveCycleLifecyclePort` and `CognitiveCycleAuthorityPort` contracts for authoritative domain translation, external observation/reconciliation, approval, authenticated human control and resource-scoped action principals.
- Added stable scoped working-memory/intention creation; B2/B4 paired delegation and compensation; child capability/action-budget checks; B4 protected-objective and authorized termination guards; B6 execution-bound budget validation/recording.
- Added exact M5 candidate to typed M3 command binding, action fingerprints, approval revalidation, authenticated principal-bound operation batches, idempotent per-step receipts, unknown-outcome reconciliation, and no blind retry of terminal/uncertain plans.
- Added versioned observation-to-working-memory updates and idempotent episode writes. Reuse of an episode idempotency key with conflicting outcome content now fails; B4 completion requires verified proof tied to the protected objective and exact reconciled observation. Episodes remain unverified until the M2 review policy verifies them.
- Added 12 focused service-integration tests. They passed in a local Java 21 lightweight JUnit-compatible harness against actual M2/M3/B2/B4/B6 service classes; Maven/Surefire/GitHub Actions validation is pending.
- Documented in-memory persistence limits, required domain/authority adapters and M7 durable-store/reconciliation work.
