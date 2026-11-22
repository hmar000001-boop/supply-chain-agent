# M8 architecture inventory correction — CI Fix 1

**Date:** 2026-11-22

## Failure

The hosted `verify-m8-b5-architecture.py` guard rejected the M8 evaluation module because its hard-coded inventory target and `M8-TEST-INVENTORY.csv` recorded `(109, 21, 69, 21, 0)`, while the checked-out Java tree contains `(95, 20, 68, 20, 0)`.

## Audit and correction

The inventory was checked directly against every `*.java` file under the module's `src/main/java` and `src/test/java` trees. The correct current values are:

- 95 production Java source files;
- 20 test Java source files;
- 68 JUnit test methods/factories;
- 20 test source files containing test annotations;
- 0 test helper-only source files.

The representative test list was also corrected to name the 20 test classes that actually exist. The previous inventory included `M8Ed25519AttestationTrustTest` and generic integration-test entries that are not files in this module; the Ed25519 trust behavior is covered by `M8TrustedResearchCompositionTest`.

This is an inventory correction, not an implementation deletion or a reduction of test requirements. No Java source was added, removed, or modified. The CI guard remains strict and continues to compare both the expected tuple and the CSV against the actual filesystem.

## Validation

The updated guard must pass against the actual checkout, then the full Maven reactor and hosted PostgreSQL integration suite must pass. This note does not claim Maven/Surefire success or experimental qualification.

## Current candidate identity and next qualification

The inventory-only successor candidate is `M8-GAP01-GAP05-EXPERIMENT-HOST-TRUSTED-VERIFIERS-CI-INVENTORY-FIX1`, with code-tree SHA-256 `773fa50a4930674f124cabeca6e22e67bee7df015438bc151b08c313d553183d` and 708 fingerprinted files. Git commit and hosted CI run identity are still unavailable in the reconstructed delivery workspace; the fingerprint correctly records them as unavailable/unknown.

The one-unit full-graph qualification is already represented by `M8B4FullGraphWorkflowTest.concreteDeterministicTrainHostComposesTheActualGraphAndStopsBeforeApprovalEffects`. It composes `ScenarioFixtureDeterministicTrainPortsFactory` through `ComposedDeterministicTrainSessionFactory` and the real `FullGraphExperimentAdapter`, then asserts both arms stop at the approval boundary, no side effects occur, and dispatch/effect counters remain zero. `ScenarioFixtureDeterministicTrainPortsFactoryTest` separately checks per-arm dependency isolation and TRAIN-only acceptance. `M8TrustedResearchCompositionTest` checks pinned Ed25519 key loading and fail-closed trust cases with ephemeral test keypairs. These tests must execute under the complete hosted Maven/Surefire run before any TRAIN collection is authorized.

After hosted CI is green, the next steps are: (1) inspect the one-unit test and all trust tests in the CI reports; (2) have the seven independent issuer custodians provision their real public keys into the protected, read-only key directory; (3) independently verify issuer identity/key ownership and the exact seven-key set; (4) run a separately authorized one-unit end-to-end TRAIN telemetry qualification; (5) inspect its full pair records and audit counters; and only then decide whether to authorize the 280-unit TRAIN telemetry pass. No private keys, fabricated signed endpoints, or experiment outcomes are included in this changeset.
