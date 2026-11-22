# Validation report

## Source and experiment status

- Candidate code-tree SHA-256: `0adfb5ac051a4ffbf59a2262a0aaad352f619c3feda0a547d7129296e78752c7`
- Fingerprinted code-tree files: 723.
- Git commit/run identity: unavailable in this build workspace; the owner must capture the actual commit and hosted CI run ID/URL.
- No TRAIN batch has been executed; no training evidence or labels were generated.
- HOLDOUT and TRANSFER remain closed.

## Passed checks

- `scripts/verify-m8-b5-architecture.py`: PASS (11-module acyclic graph, framework dependency boundaries, test inventory, CI mirror).
- `scripts/verify-m8-r2-evidence.py`: PASS.
- `scripts/verify-m8-preb7-f03-f04.py`: PASS.
- `scripts/verify-m8-experiment-scenario-register.py`: PASS (280 TRAIN, 280 HOLDOUT, 210 TRANSFER and disjoint seed ranges).
- `scripts/verify-m8-fullgraph-experiment-adapter.py`: PASS (22 invariants).
- `scripts/verify-m8-mechanism-experimental-evidence.py`: PASS (25 invariants).
- Java 21 compilation and focused smoke execution of the actual `M8PinnedEd25519PublicKeyLoader` and `M8TrustedResearchComposition` against API stubs for the larger typed artifact/archive classes: PASS.
- Focused execution of the `M8TrustedResearchCompositionTest` source under a minimal JUnit-compatible harness: 3 cases PASS, including signature validation, altered evidence, missing keys, and rejection of repeated public-key material across issuer identities.
- A separate Java 21 smoke check confirmed rejection of repeated issuer key material: PASS.
- ZIP structure, project-file manifest entries, internal SHA-256 checksums and external sidecar: PASS.

## Not run / pending

- Full Maven reactor, Maven Surefire and PostgreSQL integration tests were not run locally. The Maven Wrapper could not download Maven 3.9.16 because DNS could not resolve the Apache distribution hosts (`archive.apache.org`, `downloads.apache.org`).
- `ScenarioFixtureDeterministicTrainPortsFactory` and the new full-graph test require the full Maven dependency graph; they are not claimed as locally type-checked or executed here.
- Hosted CI for this exact candidate is pending and must be the acceptance authority.
- External issuer public keys and signed attestations have not been provisioned or independently reviewed.

## Interpretation

The implementation completes the deterministic full-graph TRAIN telemetry composition and the cryptographic verification composition. It does **not** mean that source outcome evidence exists. The host cannot turn a proposal-consumer receipt into a downstream business outcome; B1/B2/B3 bundle construction remains fail-closed until the independent signed source evidence exists. Do not start the 280-unit TRAIN evidence collection until hosted CI passes and external trust provisioning/qualification is complete.
