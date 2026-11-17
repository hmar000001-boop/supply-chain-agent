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
