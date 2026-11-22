# Validation report — M8 mechanism experiment pipeline CI Fix 2

## Candidate

- ID: `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1-CI-FIX2`
- Code-tree SHA-256: `90d2b3263db8b8dd4aea28f8d03629053e6984c6554c38e3a8a7da416bdd9127`
- Fingerprinted source/config files: 676
- Hosted CI: **PENDING**. The owner-provided 2026-11-22 run reached the evaluation module and failed at compile time; CI Fix 2 has not yet been run there.

## Corrected compile defects

- Replaced lambda capture in `FullGraphExperimentPair` with an explicit `Map.Entry` loop.
- Corrected B1 memory reference accessor to `itemVersion()`.
- Correctly adapted the two distinct factory interfaces in `MechanismSpecificExperimentHarness`.
- Added two targeted regression tests and updated the exact inventory/architecture guard.

## Local checks

- Matched-pair delta smoke execution: PASS.
- B1 `itemVersion()` API compile/run probe: PASS.
- Factory bridge and B1 source invariant checks: PASS.
- M8 architecture/module/test inventory guard: PASS.
- Scenario split/register guard: PASS (280 TRAIN, 280 HOLDOUT, 210 TRANSFER; disjoint seed ranges).
- Full-graph adapter guard: PASS (22 checks).
- Mechanism evidence guard: PASS (25 checks).
- R2 evidence guard: PASS.
- F-03/F-04 guard: PASS.
- ZIP archive and all payload checksums: PASS.

The local checks above are not a substitute for a Maven compile or JUnit Surefire run. Maven was not available in this environment, and the complete reactor/live PostgreSQL suite was not run here.

## Statistical and evidence boundary

The evaluation code is project-authored Java; it uses LangGraph4j Core to execute the application graph and JUnit Jupiter for tests. It does not use a third-party statistics library. The legacy planner comparison includes a custom deterministic 2,000-resample bootstrap. The preregistered M8 10,000-resample clustered/stratified bootstrap, paired confirmatory tests, Holm correction, integrity report and TRANSFER runner remain unimplemented. No empirical effects or confirmatory runs are claimed.
