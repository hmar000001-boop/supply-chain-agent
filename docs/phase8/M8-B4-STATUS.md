# M8-B4 status — End-to-end reliability and safety evaluation

**Overall status:** In progress. B4-A, B4-B and B4-C source increments are implemented in the cumulative delivery; user CI validation is pending. No Maven/JUnit execution is claimed by this delivery.

## Baseline

- **Accepted source:** user-uploaded `supply-chain-agent-main (3).zip`, accepted M8-B3 repository after the user-confirmed green CI.
- **Upload ZIP SHA-256:** `84cc3ef2482235092189ed89a43752166de44afbef201836611dd29bf0c833f2`.
- **Git metadata:** the supplied archive does not provide a Git commit; this delivery therefore identifies the source using the uploaded ZIP hash and the per-file source-tree manifest.
- **M7:** remains frozen and accepted. M8-B4 does not edit migrations V001-V008.

## B4 slices

| Slice | Scope | Status |
|---|---|---|
| B4-A | Durable B6 computational budget store; PostgreSQL restart/authority/unknown-outcome probes; additive V009 | **Implemented; first CI run exposed one incorrect assertion; corrected in this cumulative patch; re-run pending** |
| B4-B | Full-graph request-to-terminal workflows using deterministic simulator and strict adapters | **Implemented in source; CI pending** |
| B4-C | Graph-host replacement, durable resume, ambiguous external outcome reconciliation, safe retry after authoritative NOT_APPLIED, duplicate action replay, independent-pool idempotency race and intention CAS | **Implemented in source; CI pending** |
| B4-D | Adversarial evidence, malformed model output, dependency outages and comprehensive safety regression | Not started |

## B4-A changes

- Added `PostgresResourceBudgetLedger` implementing `ResourceBudgetLedger`, scoped by execution and tenant/runtime scope.
- Added transactionally serialized budget-account mutations and reservations. The account row is locked before availability calculation to prevent multiple hosts from oversubscribing one execution budget.
- Persisted deadline, per-unit limits, active reservations, released/committed states and actual usage. Actual overruns remain visible; an idempotent repeated commit cannot charge usage twice or replace its actual amount.
- Exposed the production adapter from `PostgresRuntimePersistence.resourceBudgets()`.
- Added forward-only migration V009. V001-V008 are untouched. Updated the isolated backup/restore verification script to require schema version 9 and verify the six B6 tables, so CI restore smoke remains aligned with the latest schema.
- Added PostgreSQL-gated integration tests for budget persistence/replacement, concurrent oversubscription, deadline and reservation lifecycle, intention restore/objective preservation, approval binding/revocation/expiry, and ambiguous external write/no blind redispatch/reconciliation.
- Added a migration parser test for V009.
- Updated the reference in-memory ledger documentation to make clear that it is a test/reference adapter and hosts can inject a durable implementation.

## B4-A correction from first CI run

The first user CI execution compiled all 12 reactor projects and passed all existing module tests up to the PostgreSQL integration suite. One B4-A assertion failed in `releasedReservationCannotBeReusedAndDeadlineIsEnforcedAfterRestart`: it expected two remaining MODEL_CALLS after releasing a two-unit reservation from an initial budget of four. The ledger correctly restores the released capacity, so the expected remaining budget is **four**. The cumulative B4-A+B4-B patch corrects this assertion. CI must be rerun before B4-A can be marked accepted.

## B4-A limitations

- Tests use a PostgreSQL repository/dispatcher replacement boundary and deterministic stateless JSON test codecs, not an operating-system process kill/restart of the entire application.
- The durable B6 adapter is exposed through `PostgresRuntimePersistence.resourceBudgets()`. B4-C proves persistence/reload and explicit register/reserve/commit across replacement in a focused graph fixture. The current production `CognitiveCycleApplicationGraph` lifecycle does not yet automatically register, snapshot, reserve and commit that ledger at each real meta-control boundary. Automatic production B6 lifecycle wiring remains open and must not be claimed from the fixture tests.
- No ERP/WMS/carrier integration or autonomous production write is added.

## B4-B full-graph workflow coverage

`M8B4FullGraphWorkflowTest` drives the actual `CognitiveCycleApplicationGraph.compileFull(...)` through `FullGraphEvaluationHost.compileFull(Ports)`. It supplies deterministic, process-local test adapters and an explicitly registered inventory-reservation handler; it does not call real suppliers, carriers, ERP/WMS endpoints or production identities.

The ten full-graph test methods cover twelve fixture executions across eleven distinct scenario IDs (REQ-08 is exercised twice under different invalidation modes): approved write and verified observation (`REQ-09`); supplier delay proposal/denial (`REQ-01`); quality-held inventory with qualified partial reservation and no false completion (`REQ-05`); blocked-route and conflicting-inventory evidence provenance with no write (`REQ-04`, `REQ-03`); denied and revoked approval (`FAIL-07`, `FAIL-08`); stale action-definition replan requiring a new approval (`REQ-08`); demand change after approval requiring a new plan and new approval (`FAIL-04`); post-approval quality observation invalidation and replanning (`REQ-08`); and read-only/no-feasible fail-closed paths (`REQ-10`, `FAIL-11`). The exact mapping is in `M8-B4-B-WORKFLOW-MATRIX.csv`.

Important limits are explicit: the current graph has no typed read-only assessment terminal, so the read-only fixture is expected to escalate safely rather than claim a successful assessment; the blocked-route test verifies evidence binding and no dispatch, not rerouting, because this fixture's executable planning/action adapter only registers inventory reservations; the conflicting-evidence test verifies both provenance references survive into the request, not that a new authoritative inventory observation is resolved. B4-C now covers targeted graph-host replacement, external timeout reconciliation, safe retry after authoritative NOT_APPLIED, duplicate action dispatch and repository concurrency. It does not claim an operating-system process kill/relaunch, real external provider integration or automatic production B6 lifecycle wiring.

## B4-C host-replacement and reconciliation evidence

`M8B4CRestartReconciliationIntegrationTest` adds four PostgreSQL-gated methods. The principal scenario interrupts a real LangGraph4j `StateGraph` backed by `PostgresLangGraph4jCheckpointSaverFactory`, closes the first Hikari pool, reconstructs the graph and all persistence adapters on a second pool, reloads the intention/approval/B6 budget/M3 operation state, blocks a direct replay while the operation is UNKNOWN_OUTCOME, queries a simulated remote provider, reconciles the confirmed commit, and verifies that no second external effect occurs and budget usage settles once.

The other methods cover (1) authoritative NOT_APPLIED reconciliation followed by a single safe retry from a replacement pool, (2) two dispatchers on independent PostgreSQL pools using the same idempotency key with one external effect, and (3) two intention repositories racing the same version with exactly one winning CAS and no objective/constraint drift.

B4-C also corrects `PostgresActionExecutionLedger.inspect(...)` to follow the `ActionExecutionLedger` contract for `FAILED + retryable + !outcome_unknown`: inspection returns empty so `ActionDispatcher` can attempt the fenced retry. UNKNOWN_OUTCOME remains a present claim and is never converted to a safe retry. Test time is relative to the execution clock to avoid a fixed historical approval expiry. The B4-A release assertion remains corrected to four available units after releasing a two-unit reservation from a four-unit budget.

These tests demonstrate replacement of the graph host and connection pool inside one test process, not an OS process kill. The external provider is a deterministic remote-system test double. The B4-C focused graph is not a claim that all B6 budget operations are automatically wired through the production full application lifecycle. See `M8-B4-C-RESTART-RECONCILIATION-PLAN.md` for the full boundary and test sequence.

## Acceptance gate for cumulative B4-A + B4-B + B4-C

1. Apply `M8_B4_C_CUMULATIVE_CHANGESET_ONLY.zip` over the accepted M8-B3 repository. It contains the corrected B4-A assertion, B4-B full-graph workflows and B4-C code/docs.
2. Run `./mvnw -B -ntp -Dphase0.postgres=true clean verify` with the configured PostgreSQL service.
3. Confirm V009 migration/restore checks, all B4-A PostgreSQL probes, B4-B full-graph workflows and all four B4-C host-replacement/reconciliation/concurrency tests pass.
4. Only after the user confirms green CI mark B4-A/B/C accepted. B4-D adversarial and dependency-failure coverage remains open.
