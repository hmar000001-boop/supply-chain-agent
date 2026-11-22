# Validation report

## Candidate

- Candidate ID: `M8-GAP01-GAP05-TRUSTED-RESEARCH-COMPILE-FIX1`
- Candidate code-tree SHA-256: `63bb49db4265ef7a9aabd19cab3a78b9b591deb198635f19ead2ae432722bd04`
- Candidate files in canonical executable/configuration scope: `719`

## Passed locally

- Java 21 compilation of the new trusted/research implementation classes and their typed framework-neutral dependency closure.
- Focused trust tests: 5 passed using a lightweight JUnit-compatible runner (not Maven Surefire).
- Ed25519 signature acceptance, tamper rejection, absent/unexpected key rejection, and duplicate key-material rejection.
- Candidate fingerprint regression: documentation and generated report changes do not change the executable fingerprint; source changes do.
- Java/Python candidate fingerprint parity: exact match.
- All six static guards: architecture/inventory, R2 evidence, F-03/F-04, experiment split register, full-graph experiment adapter, and mechanism-specific experimental evidence.
- ZIP integrity and per-file SHA-256 manifest verification.

## Not run / still required

- Full Maven reactor and JUnit/Surefire.
- PostgreSQL integration tests for this exact candidate.
- Full graph factory compile/run against the hosted dependency classpath (external LangGraph4j artifacts are not cached locally).
- Populated immutable TRAIN bundle codec round-trip using a verified TRAIN artifact set.
- Provisioned issuer-key custody and signed hosted-CI attestation.

The hosted CI result must be treated as pending until the user supplies the complete successful workflow result. No TRAIN experiment or outcome collection has been started.
