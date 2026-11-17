# M6 changelog

## 2026-11-17 — LangGraph4j cognitive orchestration

- Added `CognitiveCycleOperations` and typed operation/result contracts.
- Added compact `CognitiveCycleState` for checkpointed control state.
- Added the main cyclic `CognitiveCycleGraph` and bounded `B2ResolutionSubgraph`.
- Wired four explicit host interruption/resumption boundaries.
- Added exact approval/candidate/intention binding checks and actor/response-reference checks.
- Added pre-execution plan revalidation, idempotent execution operation references, unknown-outcome reconciliation and working-memory optimistic version conflict handling with bounded retry of the persisted observation (never a repeated external action).
- Added 14 graph-level tests; CI acceptance remains pending.
- Recorded M5 CI green based on the user's report dated 2026-11-17.

## 2026-11-17 — First CI failure correction

- Corrected B2 child-context mapping so the parent impasse cause reaches `CycleContext.lastReason` and remains available through the child lifecycle.
- Strengthened the B2 test to assert that the original reason is passed to `openImpasse`.
- Updated the approval-binding and human-control identity tests to inspect LangGraph4j's asynchronous exception cause chain for `SecurityException`, while preserving the no-execution assertions.
- Recorded the first GitHub Actions run and its exact result: all 208 M0–M5 tests and seven runtime compatibility tests passed; the M6 graph test class had two assertion failures and one B2 error.
- M6 remains pending until the corrected full reactor passes CI.
