# M8 Trusted Research CI Fix 3 — B3 SHADOW overlay composition

Date: 2026-11-23
Candidate: `M8-GAP01-GAP05-B3-SHADOW-OVERLAY-CI-FIX3`
Status: pending hosted Maven/Surefire/PostgreSQL CI qualification

## Failure evidence

The hosted reactor compiled all 106 evaluation production sources and all 20 evaluation test sources. The evaluation test suite then ran 70 tests: 69 passed and one errored in
`M8B4FullGraphWorkflowTest.concreteDeterministicTrainHostComposesTheActualGraphAndStopsBeforeApprovalEffects`.

The exception was:

`SecurityException: only one B3 feedback overlay may be wired into an evaluation graph`

at `FullGraphEvaluationHost.compileFullRuntime`.

## Root cause

The deterministic TRAIN factory wired the same *neutral* B3 coalition SHADOW overlay twice:

1. As `R2WorkflowConfiguration.experimentShadowCoalitionFeedbackAdjustment`.
2. As `DeterministicTrainSessionConfiguration.b3ShadowAdjustment`, which is passed separately to `FullGraphEvaluationHost.compileFullRuntime` by `FullGraphExperimentSession.compile`.

`FullGraphEvaluationHost` deliberately rejects simultaneous configured and explicit overlays. This is a valuable fail-closed invariant: two sources of adjustment must not silently compose or override one another.

## Repair

- Keep the per-arm `DeterministicTrainSessionConfiguration.b3ShadowAdjustment` as the sole owner of the experiment SHADOW overlay.
- Set `R2WorkflowConfiguration.experimentShadowCoalitionFeedbackAdjustment` to `null` in `ScenarioFixtureDeterministicTrainPortsFactory`.
- Do not weaken or remove the host duplicate-overlay check.
- Add assertions to the existing factory isolation test proving both baseline and treatment R2 configurations carry no second overlay while the per-arm configuration still supplies the single overlay.

This preserves the existing neutral B3 behavior while preventing duplicate wiring. B3 receipt recording, outcome-verifier configuration, B2 draft governance, and the explicit per-arm overlay path remain intact.

## CI log analysis

The supplied hosted run shows:

- All 11 modules before evaluation completed successfully.
- The runtime module passed 87 tests.
- PostgreSQL infrastructure passed 57 tests.
- Evaluation compiled 106 production sources and 20 test sources.
- Evaluation ran 70 tests: 69 passed, 0 assertion failures, 1 error from the duplicate-overlay `SecurityException`.
- Overall, 487 tests passed and one errored across the shown run; the Maven reactor failed only in the final evaluation module.

The repeated SLF4J messages (`No SLF4J providers were found`) are warnings, not the cause of this build failure. They indicate that tests use the SLF4J API without a logging provider. This narrow repair intentionally does not add a logging implementation or alter runtime logging policy; that can be addressed separately if visible test logging is desired.

## Validation in this delivery environment

- All six M8 static guards passed after the change, including architecture/test-inventory, R2 evidence, F-03/F-04, scenario register, full-graph adapter and mechanism evidence checks.
- The tracked test inventory remains unchanged: the existing test method was extended; no new test method or test annotation was introduced.
- The candidate fingerprint was regenerated using the repository's canonical Python fingerprint script.
- Full Maven/Surefire execution was not available here because Maven is not installed and the Maven Wrapper distribution is not cached. Hosted CI remains the authoritative runtime qualification.

## Release gate

Do not collect TRAIN evidence or open HOLDOUT/TRANSFER based solely on this repair. Require a successful complete 12-module command:

`./mvnw -B -ntp -Dphase0.postgres=true clean install`

Then proceed through the already-defined trusted issuer provisioning and one-unit TRAIN qualification gates.
