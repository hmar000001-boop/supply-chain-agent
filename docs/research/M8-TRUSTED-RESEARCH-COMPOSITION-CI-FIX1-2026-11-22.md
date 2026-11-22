# M8 trusted research composition — CI compile repair 1

Date: 2026-11-22  
Candidate: `M8-GAP01-GAP05-TRUSTED-RESEARCH-COMPILE-FIX1`  
Candidate code-tree SHA-256: `69a50ad69345683fadd73182ee8c543a882547a715ccc61d94c7ca533ddfae43`

## CI failure addressed

The hosted build reached the final `supply-chain-agent-evaluation` module and failed compilation with 32 diagnostics. These were caused by missing trust/transfer implementation classes and integration mismatches, not failures in the preceding 11-module reactor:

- Added the B6 TRANSFER report and aligned the validation-label accessor used by the analysis engine.
- Added the read-only signed-attestation repository, Ed25519 trust verifier, endpoint and safety verifiers, and Git/CI freeze prerequisite verifier.
- Added B1/B2/B3/B6 signed mechanism-artifact verification services.
- Added a versioned, bounded record-graph codec for the immutable TRAIN bundle; it does not use Java native object deserialization.
- Corrected imports for the deterministic scenario fixture TRAIN port factory.
- Reconciled the architecture guard and CSV to 106 evaluation production Java files and 70 test annotations across 20 test source files.

## Candidate fingerprint integrity

The Java freeze verifier and `scripts/fingerprint-m8-experiment-candidate.py` previously used different tree-selection and hash canonicalization rules. They now use one definition: SHA-256 over sorted UTF-8 repository-relative path, NUL, lowercase per-file SHA-256, newline records. The fingerprint includes module source, root Maven configuration, `.mvn`, `.github`, and `scripts`; it excludes documentation/research reports and generated build output. This prevents the fingerprint manifest from hashing itself.

A focused Java 21 test verifies that documentation changes do not change the code fingerprint, executable module source changes do, root-level orphan `src` content is not included, and target output is excluded. An independent Java-versus-Python parity probe produced the same candidate hash.

## Trust model and operational constraints

The composition requires seven distinct issuer IDs backed by seven distinct Ed25519 public-key materials. It loads only an exact allow-list of public PEM files and never loads or generates private keys. This establishes distinct cryptographic identities; it does **not**, by itself, prove organizational independence. For an independent-verifier claim, each key must be controlled and used by a separate authorized custodian/process, with private-key issuance and storage outside the application repository.

The codec accepts records only from explicit M8 data-model package families and rejects class names outside that allow-list. The archive is versioned and size/depth/collection bounded. This release includes a negative test for out-of-allow-list class rejection; a full populated TRAIN bundle round-trip remains a hosted integration qualification because no admissible B1/B3 outcome corpus currently exists.

## Validation performed in this environment

- Java 21 compilation of the missing trusted/research implementation classes and their typed dependencies: PASS.
- Focused trusted composition tests using a lightweight JUnit-compatible local runner: 5 passed. This is not Maven Surefire.
- Ed25519 valid signature, tamper rejection, missing/unexpected key rejection, and duplicate key-material rejection: PASS.
- Java/Python candidate fingerprint parity: PASS.
- All six static guards: PASS (`verify-m8-b5-architecture.py`, `verify-m8-r2-evidence.py`, `verify-m8-preb7-f03-f04.py`, `verify-m8-experiment-scenario-register.py`, `verify-m8-fullgraph-experiment-adapter.py`, `verify-m8-mechanism-experimental-evidence.py`).

The full Maven reactor, Surefire tests and live PostgreSQL integration were not run locally. Hosted CI is required to qualify this candidate. No TRAIN evidence was collected; HOLDOUT and TRANSFER remain closed.
