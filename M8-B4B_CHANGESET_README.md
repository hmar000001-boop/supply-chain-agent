# M8-B4 cumulative patch — B4-A correction + B4-B full-graph workflows

This is a change-only overlay for the accepted M8-B3 repository uploaded as `supply-chain-agent-main (3).zip` (SHA-256 `84cc3ef2482235092189ed89a43752166de44afbef201836611dd29bf0c833f2`). It includes the M8-B4-A implementation and additive V009 migration, corrects the one reservation-release assertion that failed in the first user CI run, and adds M8-B4-B full-graph workflow tests and documentation.

## Apply

1. Start from the accepted M8-B3 project tree.
2. Extract this archive at the repository root, preserving relative paths and allowing overwrite of the listed files.
3. Do not extract it over a prior M8-B2 tree. The patch is cumulative from M8-B3, not a full repository archive.
4. Run `./mvnw -B -ntp -Dphase0.postgres=true clean verify` in GitHub Actions.

## Included B4-A correction

`releasedReservationCannotBeReusedAndDeadlineIsEnforcedAfterRestart` now expects four remaining MODEL_CALLS after releasing a two-unit reservation from an initial four-unit budget. Releasing the reservation restores its capacity; the original expectation of two was incorrect.

## B4-B content

`M8B4FullGraphWorkflowTest` contains ten JUnit test methods covering twelve deterministic fixture executions across eleven distinct scenario IDs through `FullGraphEvaluationHost.compileFull(Ports)` and the actual `CognitiveCycleApplicationGraph.compileFull(...)` composition. The method-to-fixture mapping and evidence boundaries are in `docs/phase8/M8-B4-B-WORKFLOW-MATRIX.csv`.

The action registry contains only explicit test-only inventory-reservation handlers. Scenario adapters are process-local and deterministic. No production supplier, carrier, ERP, WMS or identity integration is made.

## Explicit limits

- A typed read-only success terminal does not currently exist; the read-only fixture fails closed with no writes.
- The blocked-route scenario asserts evidence provenance and no dispatch; it does not exercise route rerouting.
- Conflicting inventory source references are preserved but not resolved into a new authoritative fact.
- The quality-hold scenario permits an explicitly approved reservation of qualified secondary stock only; it must not report the incomplete objective as success.
- Full OS-process stop/restart, durable B6 lifecycle registration/reserve/commit wiring, duplicate delivery, cross-host action resumption and adversarial/provider outage tests remain B4-C/D.

## Validation boundary

The archive, overlay and static metadata checks are validated by the delivery process. Maven, JUnit and PostgreSQL tests are intentionally not run here; this package is not claimed CI-green.


### B4-C CI correction — sparse resource amounts

The first cumulative CI run passed B4-A's seven reliability tests and three of four B4-C restart/reconciliation tests. The remaining final-verification assertion unboxed a missing key from the intentionally sparse `ResourceBudgetSnapshot.reserved()` map. Missing resource units mean zero (as already implemented by `ResourceBudgetSnapshot.remaining`). The B4-C test now uses `getOrDefault(unit, 0L)` consistently for zero and positive budget amounts. Production ledger code and migrations are unchanged by this correction.
