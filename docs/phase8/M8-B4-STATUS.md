# M8-B4 status — End-to-end reliability and safety evaluation

**Overall status:** In progress. The B4-A source increment is prepared for user CI validation. No Maven test execution is claimed by this delivery.

## Baseline

- **Accepted source:** user-uploaded `supply-chain-agent-main (3).zip`, accepted M8-B3 repository after the user-confirmed green CI.
- **Upload ZIP SHA-256:** `84cc3ef2482235092189ed89a43752166de44afbef201836611dd29bf0c833f2`.
- **Git metadata:** the supplied archive does not provide a Git commit; this delivery therefore identifies the source using the uploaded ZIP hash and the per-file source-tree manifest.
- **M7:** remains frozen and accepted. M8-B4 does not edit migrations V001-V008.

## B4 slices

| Slice | Scope | Status |
|---|---|---|
| B4-A | Durable B6 computational budget store; PostgreSQL restart/authority/unknown-outcome probes; additive V009 | **Implemented in source; CI pending user confirmation** |
| B4-B | Full-graph request-to-terminal workflows using deterministic simulator and strict adapters | Not started |
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

## B4-A limitations

- Tests use a PostgreSQL repository/dispatcher replacement boundary and deterministic test codecs, not an operating-system process kill/restart of the entire application.
- The complete `CognitiveCycleApplicationGraph` has not yet been driven through the end-to-end scenarios listed in `M8-B4-RELIABILITY-TEST-PLAN.md`.
- B6 durability is now represented by a production adapter, but the application composition must still be wired to create/register/snapshot/reserve/commit budgets at the real lifecycle boundaries; B4-B/C will validate that integration rather than treating adapter exposure as proof of automatic accounting.
- No ERP/WMS/carrier integration or autonomous production write is added.
- No Maven/JUnit/CI result is asserted here. CI acceptance belongs to the user's GitHub Actions run.

## Acceptance gate for B4-A

1. User applies `M8_B4A_DURABLE_BUDGET_AND_RELIABILITY_TESTS_CHANGESET_ONLY.zip` on the accepted M8-B3 repository.
2. GitHub Actions runs `./mvnw -B -ntp -Dphase0.postgres=true clean verify` with PostgreSQL service configured.
3. All existing tests, V009 migration parsing, and the six PostgreSQL-gated M8-B4 integration tests pass.
4. Any failures are fixed in a change-only corrective patch before B4-B proceeds.
