# M8_TRUSTED_RESEARCH_COMPILE_CI_FIX1

## Apply

Extract this ZIP at the repository root, preserving relative paths. It is a **single consolidated repair** for the 32 evaluation-module compiler diagnostics in the 2026-11-22 CI log. It supersedes the missing-class integration state; do not separately extract earlier partial trusted-composition source files over it.

This changeset contains **12 added project files, 9 complete file replacements, and zero project deletions**. No POM or external dependency was added.

## What it fixes

1. Supplies the missing `B6TransferEvaluationReport` and the B6 validation-label compatibility accessor used by `M8ConfirmatoryAnalysisEngine`.
2. Supplies the missing Ed25519 attestation model/repository/trust service, endpoint and safety verifiers, Git/CI freeze verifier, mechanism-specific B1/B2/B3/B6 artifact verification services, candidate fingerprint implementation, and bounded typed TRAIN bundle codec.
3. Corrects the missing imports in `ScenarioFixtureDeterministicTrainPortsFactory`.
4. Reconciles the evaluation source inventory to **106 production Java files, 20 test source files, 70 test annotations, 20 annotated test classes, 0 helper-only files**.
5. Unifies Java and Python candidate-code fingerprint algorithms; the fingerprint report and documentation are excluded so the report cannot hash itself. Java/Python parity is covered by a focused regression test and a local parity probe.
6. Restricts the TRAIN bundle codec to explicit M8 data-model package families; native Java object deserialization is not used.

## Apply and validate

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Hosted CI is required. The local environment did not have the Maven distribution cached and could not resolve Apache Maven distribution hosts, so this package is **not represented as Maven/Surefire green**.

## Trust and evidence boundaries

The composition requires seven distinct issuer identities and seven distinct Ed25519 public keys provisioned outside the repository. It does not create or include private keys. Distinct keys prove distinct cryptographic identities, not organizational independence; separate custodians and external private-key custody are operational requirements.

No TRAIN evidence is collected by this changeset. No signed issuer keys are provisioned in the project, no confirmatory run is claimed, and HOLDOUT/TRANSFER remain closed. The populated typed TRAIN bundle archive round-trip still requires qualification with a valid evidence corpus.

See `docs/research/M8-TRUSTED-RESEARCH-COMPOSITION-CI-FIX1-2026-11-22.md` for the repair and validation record.
