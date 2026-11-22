# M8 mechanism experiment pipeline — CI Fix 2

## Apply

Extract this ZIP at the repository root. All project paths are rooted at the repository root. Change-set instructions and manifests live under `changesets/M8_MECHANISM_EXPERIMENT_PIPELINE_CI_FIX2/` so they do **not** overwrite the project's own root `README.md`, `CHANGE_MANIFEST.csv`, `SHA256SUMS.txt` or `VALIDATION_REPORT.md`.

Project delta: **8 complete file replacements, 3 added files, zero production deletions**.

After extracting, run the safe cleanup for the known orphan root-level `src/` files:

```bash
python scripts/cleanup-orphan-root-src.py
```

The script deletes only the two exact files listed in `changesets/M8_MECHANISM_EXPERIMENT_PIPELINE_CI_FIX2/ROOT_CLEANUP_MANIFEST.csv`, and only when their SHA-256 hashes match the archived orphan copies. It leaves any unexpected files alone. It never removes module source trees such as `supply-chain-agent-runtime/src/`.

## CI compiler repairs

1. `FullGraphExperimentPair`: replace the compact-constructor lambda with an explicit metric-entry loop to avoid Java's effectively-final restriction while preserving treatment-minus-baseline deltas.
2. `FullGraphMechanismEvidenceCollector`: call `B1MemoryCandidateReference.itemVersion()`; the typed reference record exposes `itemVersion`, not `version`.
3. `MechanismSpecificExperimentHarness`: bridge `FullGraphExperimentSessionFactory` to `MechanismSpecificExperimentSessionFactory` using the actual method contracts. The graph factory receives (scenario, mechanism, arm), derives the exact `MechanismArmPlan`, and calls the mechanism-specific factory with (scenario, plan, training bundle).
4. Add two regression tests for B1 reference-version fingerprinting and matched-pair metric deltas. Update the source/test inventory and its architecture guard.

## Root `src/` explanation

The root `src/` directory was not part of the Maven 11-module architecture. It came from two stale files accidentally included at repository-root paths in the earlier frozen full-source archive. The canonical runtime implementation and its tests are under `supply-chain-agent-runtime/src/`. The root aggregator POM has `packaging=pom`, so it does not compile the root-level `src/` tree. The exact stale copies are safe to remove using the guarded cleanup script.

## Candidate fingerprint

- Candidate: `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1-CI-FIX2`
- Code-tree SHA-256: `90d2b3263db8b8dd4aea28f8d03629053e6984c6554c38e3a8a7da416bdd9127`
- Fingerprinted source/config files: 676
- Hosted CI: pending; the supplied run failed on the previous candidate.

## Validation limits

The pair-delta smoke execution, B1 accessor compile probe, source-contract checks, architecture guard, scenario split guard, full-graph adapter guard, mechanism evidence guard, R2 guard and F-03/F-04 guard pass locally. The full Maven/Surefire/PostgreSQL reactor has **not** run locally. Hosted CI must confirm this repair. No TRAIN/HOLDOUT/TRANSFER experiment has been run, and inferential analysis is not yet implemented.
