# Validation Report — M8 Mechanism-Specific Experiment Pipeline v1

## Source delta

- Added: 39 files
- Modified: 28 files (complete file replacements)
- Deleted: 0 files
- Project source/documentation/CI paths: 67
- Candidate code-tree fingerprint file count: 674

## Checks passed in the build workspace

- `scripts/verify-m8-b5-architecture.py` — PASS (11 modules; acyclic internal dependencies; source/test inventory and CI mirror consistent).
- `scripts/verify-m8-r2-evidence.py` — PASS.
- `scripts/verify-m8-preb7-f03-f04.py` — PASS.
- `scripts/verify-m8-experiment-scenario-register.py` — PASS (25 legacy development-only fixtures excluded; exact 280/280/210 split counts; disjoint seed ranges and profile/family counts).
- `scripts/verify-m8-fullgraph-experiment-adapter.py` — PASS (22 invariants covering graph safety, TRAIN telemetry, verified HOLDOUT gate and no real-world claims).
- `scripts/verify-m8-mechanism-experimental-evidence.py` — PASS (25 invariants covering B1/B2/B3/B4/B6 provenance and evidence gates, content fingerprints, TRAIN/HOLDOUT separation, and no confirmatory results claimed).
- Java 21 `javac --release 21` type-check of `MechanismTrainingArtifactFingerprints` and `MechanismTrainingArtifactBuilder`, with transitive project source dependencies — PASS.
- JSON, YAML and CSV parsing — PASS.
- CI workflow mirror (`.github/workflows/ci.yml` and `workflows/ci.yml`) — PASS through architecture guard.
- ZIP extraction, project file manifest, internal checksums and external SHA-256 — PASS.

## Not executed / not claimed

- Full Maven reactor, JUnit/Surefire and live PostgreSQL integration suite — NOT RUN in this workspace. The pinned Maven distribution was not available from the configured Apache hosts in the prior build attempt because DNS resolution failed. Hosted GitHub CI must qualify the exact candidate.
- Trusted host composition for planner/consumer/authority/action ports and actual source collection — NOT PROVIDED.
- Independent TRAIN evidence collection, B1/B2/B3/B6 artifact bundle construction and freeze — NOT EXECUTED.
- Any 280-unit confirmatory HOLDOUT run — NOT EXECUTED.
- Any TRANSFER run — NOT IMPLEMENTED.
- Clustered paired bootstrap, confidence intervals, hypothesis tests, Holm correction and integrity report — NOT IMPLEMENTED.
- Real-world business outcome corpus — ABSENT.

## Acceptance statement

This report establishes source/static validation and a focused Java type-check only. It does not assert Maven/CI success, successful experiment execution, empirical superiority, calibration acceptance, operational activation, real-world impact, or total CoALA/Fan & Lan paper conformance.
