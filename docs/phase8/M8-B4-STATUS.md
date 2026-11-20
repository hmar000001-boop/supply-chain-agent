# M8-B4 status — End-to-end reliability and safety evaluation

**Overall status:** In progress. B4-A and B4-B source increments are implemented in the cumulative delivery; user CI validation is pending. No Maven/JUnit execution is claimed by this delivery.

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
| B4-C | Full process interruption/restart and end-to-end external reconciliation/concurrency | Not started |
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
- The durable B6 adapter is exposed through `PostgresRuntimePersistence.resourceBudgets()`, but the current full-graph application lifecycle does not yet register, snapshot, reserve and commit that ledger automatically at each real meta-control boundary. B4-B currently supplies an in-memory `ResourceBudgetSnapshot` test port; durable budget lifecycle integration remains open for B4-C/D and must not be claimed as wired by B4-B.
- No ERP/WMS/carrier integration or autonomous production write is added.

## B4-B full-graph workflow coverage

`M8B4FullGraphWorkflowTest` drives the actual `CognitiveCycleApplicationGraph.compileFull(...)` through `FullGraphEvaluationHost.compileFull(Ports)`. It supplies deterministic, process-local test adapters and an explicitly registered inventory-reservation handler; it does not call real suppliers, carriers, ERP/WMS endpoints or production identities.

The ten full-graph test methods cover twelve fixture executions across eleven distinct scenario IDs (REQ-08 is exercised twice under different invalidation modes): approved write and verified observation (`REQ-09`); supplier delay proposal/denial (`REQ-01`); quality-held inventory with qualified partial reservation and no false completion (`REQ-05`); blocked-route and conflicting-inventory evidence provenance with no write (`REQ-04`, `REQ-03`); denied and revoked approval (`FAIL-07`, `FAIL-08`); stale action-definition replan requiring a new approval (`REQ-08`); demand change after approval requiring a new plan and new approval (`FAIL-04`); post-approval quality observation invalidation and replanning (`REQ-08`); and read-only/no-feasible fail-closed paths (`REQ-10`, `FAIL-11`). The exact mapping is in `M8-B4-B-WORKFLOW-MATRIX.csv`.

Important limits are explicit: the current graph has no typed read-only assessment terminal, so the read-only fixture is expected to escalate safely rather than claim a successful assessment; the blocked-route test verifies evidence binding and no dispatch, not rerouting, because this fixture's executable planning/action adapter only registers inventory reservations; the conflicting-evidence test verifies both provenance references survive into the request, not that a new authoritative inventory observation is resolved. Full process stop/restart, external timeout across host replacement, duplicate event/outbox delivery, durable budget lifecycle wiring and concurrency races remain B4-C/D.

## Acceptance gate for cumulative B4-A + B4-B

1. Apply `M8_B4_B_FULL_GRAPH_WORKFLOWS_CHANGESET_ONLY.zip` over the accepted M8-B3 repository. It is cumulative: it includes the B4-A code plus the corrected budget-release assertion and B4-B workflows.
2. Run `./mvnw -B -ntp -Dphase0.postgres=true clean verify` with PostgreSQL service configured.
3. Confirm V009 migration/restore checks, all PostgreSQL B4-A probes, and the new full-graph test class pass.
4. Only after the user confirms green CI mark B4-A and B4-B accepted; process restart and adversarial tests remain later B4-C/D work.
