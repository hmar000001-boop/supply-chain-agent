# Validation report — M8 B5 inventory drift CI Fix 1

- Candidate: `M8-GAP01-GAP05-EXPERIMENT-HOST-TRUSTED-VERIFIERS-CI-INVENTORY-FIX1`
- Code-tree SHA-256: `773fa50a4930674f124cabeca6e22e67bee7df015438bc151b08c313d553183d`
- Fingerprinted files: 708
- Project changes: 5 modified, 1 added, 0 deleted; no Java source changes.

## Passed

- `scripts/verify-m8-b5-architecture.py`: PASS (11 modules; inventory synchronized with actual source tree).
- `scripts/verify-m8-r2-evidence.py`: PASS.
- `scripts/verify-m8-preb7-f03-f04.py`: PASS.
- `scripts/verify-m8-experiment-scenario-register.py`: PASS (280 TRAIN / 280 HOLDOUT / 210 TRANSFER; disjoint seed ranges).
- `scripts/verify-m8-fullgraph-experiment-adapter.py`: PASS (22 invariants).
- `scripts/verify-m8-mechanism-experimental-evidence.py`: PASS (25 invariants).
- Exact evaluation test-source list: PASS (20 listed, 20 present).
- Fingerprint, experiment registry and conformance metadata alignment: PASS.
- ZIP CRC/integrity and internal file checksums: PASS.

## Not run / not claimed

- Full Maven/Surefire and PostgreSQL suite: NOT RUN locally because Maven 3.9.16 could not be downloaded (DNS failure for configured Apache hosts).
- Hosted CI for this exact candidate: PENDING.
- One-unit TRAIN graph test has not been executed in this environment; it is already part of `M8B4FullGraphWorkflowTest` and will execute under the hosted Maven test phase.
- No TRAIN telemetry or verified outcomes produced; HOLDOUT and TRANSFER remain closed.
