# Validation report — M8 effective-input provenance CI Fix 2

## Evidence analyzed

Hosted workflow started at `2026-11-22T22:56:01Z` with Temurin Java 21. The log shows:

- Reactor modules 1–11 succeeded, including their compile/test/package/install stages.
- 418 Surefire tests passed in those modules (14 domain, 44 memory, 20 simulator, 28 actions, 125 mechanisms, 43 planning, 87 runtime, 57 PostgreSQL). Core and app report no tests to run.
- Evaluation production compilation succeeded for 106 Java source files.
- Evaluation test compilation considered 20 test source files and reported one missing-symbol error. Evaluation Surefire tests did not run.
- No dependency convergence, Maven Enforcer, PostgreSQL integration, upstream test, or production compilation failures appear in this log.

## Repair validation performed here

- Focused Java 21 compilation of `ExperimentArm`, `ExperimentMechanism`, `ExperimentSandboxAudit`, `FullGraphExperimentRunObservation`, and `FullGraphExperimentPair`: PASS.
- Executable Java contract smoke: PASS — each run JSONL record contains the effective-input fingerprint, valid matched pair delta remains intact, and a mismatched treatment fingerprint is rejected.
- Architecture/inventory guard: PASS.
- R2 evidence guard: PASS.
- F-03/F-04 guard: PASS.
- Scenario split/register guard: PASS.
- Full-graph adapter guard: PASS.
- Mechanism-specific experimental evidence guard: PASS.
- Candidate fingerprint regenerated and re-run for parity: PASS (`62d1b39c75aab9a87b136a75d97c31b2371c0a396fc941581a5bbc614aa83b64`, 719 files).
- ZIP integrity and per-file SHA-256 validation: recorded in `SHA256SUMS.txt` and verified after packaging.

## Not performed / still required

The Maven Wrapper required Maven 3.9.16, but this environment could not resolve Apache download hosts (DNS unavailable); therefore a full Maven/Surefire run could not be performed locally. The new regression test source and full application graph adapter were not compiled against the complete hosted dependency classpath here. Hosted CI must verify both.

No claim is made that CI is green. No TRAIN episodes, outcome labels, HOLDOUT, or TRANSFER experiments were run or authorized by this repair.
