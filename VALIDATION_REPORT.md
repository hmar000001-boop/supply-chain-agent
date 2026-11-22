# Validation report — M8 mechanism experiment pipeline CI Fix 1

## Candidate

- ID: `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1-CI-FIX1`
- Code-tree SHA-256: `8f64c3765be2e2eefe0ed1b00ff018526860e188ef6bb446a10dd1d60ef27236`
- Fingerprinted code/config files: 674
- Hosted CI: **PENDING**. The owner's 2026-11-22 log fails at the mechanisms module due to the missing `transitionKind(RecoveryIntention, RecoveryIntention)` method. The repair has not yet been run in hosted CI.

## Defects fixed

- Implemented and tested the missing B4 transition classifier. It checks intention identity, contiguous versioning and committed-objective immutability. Method change has precedence over simultaneous plan binding; entering `RECONSIDERATION_PENDING` is categorized as a reconsideration event; active plan reference changes are separately identified; other valid lifecycle transitions remain `OTHER`.
- Made the actor identity a typed recorder argument/field, avoiding unsafe inference from human-readable reason text.
- Fixed a Java 21 effectively-final lambda-capture compilation issue in `B4IntentionRunEvidence`.
- Updated architecture guard and test inventory to account for the one additional B4 regression test.

## Local verification performed

- Java 21 compilation of production modules: domain (53 files), memory (52), core (4), simulator (5), actions (58), mechanisms (166): **PASS**.
- Java 21 compilation of the B1/B2/B3/B4/B6 typed evidence dependency closure: **PASS**.
- B4 transition/evidence smoke execution, including typed actor attribution: **PASS**.
- `RecoveryIntentionServiceTest`: 18 methods executed with local assertion-compatible JUnit API stubs: **PASS**. This is not a Maven Surefire/JUnit-engine execution.
- `scripts/verify-m8-b5-architecture.py`: **PASS** (11 modules acyclic; module boundaries and inventory).
- `scripts/verify-m8-r2-evidence.py`: **PASS**.
- `scripts/verify-m8-preb7-f03-f04.py`: **PASS**.
- `scripts/verify-m8-fullgraph-experiment-adapter.py`: **PASS** (22 invariants).
- `scripts/verify-m8-mechanism-experimental-evidence.py`: **PASS** (25 invariants).
- `scripts/verify-m8-experiment-scenario-register.py`: **PASS** (280 TRAIN, 280 HOLDOUT, 210 TRANSFER; disjoint seeds and parameter vectors).
- Candidate fingerprint recomputation, JSON/YAML/CSV parsing: **PASS**.
- Archive integrity, file manifest and checksums: **PASS**.

## Not performed / not claimed

- Full `./mvnw -B -ntp -Dphase0.postgres=true clean install`: **NOT RUN locally**; Maven distribution is not installed/cached in this environment and network resolution is unavailable.
- Surefire/JUnit engine test run and live PostgreSQL integration for this candidate: **NOT RUN**.
- Hosted CI for this repair: **PENDING**.
- Any TRAIN/HOLDOUT/TRANSFER experiments or empirical mechanism-effectiveness claims: **NOT RUN**.

## Required hosted acceptance

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```
