# Validation Report — M8 Experimental Integrity/Freeze/Statistics/TRANSFER v1

**Candidate:** M8-GAP01-GAP05-EXPERIMENT-INTEGRITY-FREEZE-ANALYSIS-V1  
**Code-tree SHA-256:** `e5f94c2d7da325010b5720dcf750b436b6e07962e672be21659767fb9e59aec6`  
**Fingerprinted files:** 705  
**Changes:** 21 added, 4 modified, 0 deleted  
**Maven/hosted CI:** pending; this report does not claim a green build.

## Checks completed

- Java 21 API-stub compilation and focused smoke tests: PASS (`M8 research Java 21 smoke tests: PASS`). This is not Maven type-checking or JUnit/Surefire execution.
- M8 architecture/dependency/source inventory/CI mirror guard: PASS (11 modules; 569 production Java files; 84 test sources).
- R2 evidence guard: PASS.
- F-03/F-04 guards: PASS.
- Mechanism-specific evidence guard: PASS (25 invariants).
- Scenario register guard: PASS (280 TRAIN, 280 HOLDOUT, 210 TRANSFER; disjoint seed bands and profile/family counts).
- Full-graph experiment adapter guard: PASS (22 invariants).
- ZIP integrity and SHA-256 checks: performed after archive creation.

## What is implemented

1. Five-file TRAIN telemetry re-audit checks byte hashes, exact preregistered unit membership and per-unit effective-input fingerprints, producer evidence kind and no pending outputs.
2. Typed independent H1-H4 endpoint evidence and verifier port; B6 labels remain separately verified.
3. Paired statistical kernel with deterministic 10,000 bootstrap replicates; HOLDOUT profile-stratified resampling; TRANSFER family-cluster then profile-stratum hierarchical resampling; continuous paired sign-flip Monte Carlo; exact two-sided McNemar/sign tests; HOLDOUT-only Holm adjustment for H1-H5.
4. Safety evidence matrix with ten zero-tolerance counters for both arms across all five mechanisms, bound to split, episode count, verifier and source fingerprint. Mandatory host verifier gate applies to freeze and HOLDOUT/TRANSFER analysis.
5. Content-addressed full typed-bundle archive interface with codec read-back, immutable TRAIN freeze manifest, trusted Git/CI/dependency prerequisite verifier port, and dependency-ordered readiness receipts/reminders.

## Not yet qualified / not performed

- Full `./mvnw -B -ntp -Dphase0.postgres=true clean install` was not run locally: Maven Wrapper could not resolve the Apache Maven distribution hosts in this environment.
- New JUnit tests have not run under Maven Surefire or hosted CI for this increment.
- No confirmed result for the currently running hosted CI is inferred from earlier build-step progress.
- No TRAIN, HOLDOUT or TRANSFER experiment was executed.
- No verified B1/B2/B3/B4 endpoint outcomes or independent B6 labels were generated.
- No trusted concrete safety/endpoint verifiers, deterministic TRAIN host composition provider, versioned bundle codec or Git/CI attestation implementation is supplied here.
- No freeze manifest/archive exists; the code only implements the contracts that create them after evidence passes.
- No real-world operational outcomes or adaptive-effectiveness claims are made.

## Research integrity statement

The implementation rejects deriving primary endpoints from graph telemetry or LLM-as-a-judge scores. Simulator-derived labels remain simulator-only evidence. B1/B3 stay SHADOW/non-activated; B6 remains NOT_CALIBRATED. HOLDOUT/TRANSFER remain closed until all preceding gates are independently verified and explicitly authorized.
