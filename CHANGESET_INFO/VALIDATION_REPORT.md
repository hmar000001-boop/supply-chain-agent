# Validation report — M8 Trusted Research CI Fix 1

## Candidate and package integrity

- Candidate: `M8-GAP01-GAP05-TRUSTED-RESEARCH-COMPILE-FIX1`
- Candidate code-tree SHA-256: `63bb49db4265ef7a9aabd19cab3a78b9b591deb198635f19ead2ae432722bd04`
- Canonical executable/configuration scope: 719 files.
- Project changes: 12 added, 9 replaced, 0 deleted.
- Every project file is recorded in `CHANGE_MANIFEST.csv` with byte size and SHA-256.

## Passed in the local validation environment

- Java 21 compilation of the added trusted/research implementation classes and their typed framework-neutral dependency closure.
- Five focused trust tests using a lightweight JUnit-compatible runner (not Maven Surefire).
- Ed25519 valid-signature acceptance, tamper rejection, missing/unexpected key rejection, and duplicate key-material rejection.
- Candidate fingerprint regression checks: documentation/generated report changes do not change the executable fingerprint; executable source changes do.
- Java/Python candidate fingerprint parity.
- Six static guards: architecture/inventory, R2 evidence, F-03/F-04, experiment split register, full-graph experiment adapter, and mechanism-specific experimental evidence.
- ZIP integrity and per-file SHA-256 manifest verification.

## Not run / still required

- Full Maven reactor and JUnit/Surefire suite.
- PostgreSQL integration tests for this exact candidate.
- Full graph factory compile/run against the hosted dependency classpath (external LangGraph4j artifacts were not cached in the local environment).
- Populated immutable TRAIN bundle codec round-trip using a verified TRAIN artifact set.
- Provisioned issuer-key custody and signed hosted-CI attestation.

Do not treat this package as CI-green until the hosted workflow completes successfully. No TRAIN experiment or outcome collection has been started.
