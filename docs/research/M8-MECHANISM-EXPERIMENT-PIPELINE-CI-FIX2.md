# M8 mechanism experiment pipeline — CI Fix 2

## Scope

This repair addresses the six Java compiler errors reported by the hosted reactor on 2026-11-22 in `supply-chain-agent-evaluation`. It also clarifies the non-module root `src/` copies found in the previously delivered full-source archive.

## Corrections

1. `FullGraphExperimentPair` now calculates treatment-minus-baseline metrics with an explicit entry loop. This avoids capturing reassigned compact-constructor parameters in a lambda while preserving the same matched metric-key validation and delta semantics.
2. `FullGraphMechanismEvidenceCollector` now uses the actual `B1MemoryCandidateReference.itemVersion()` record accessor. The typed B1 reference has no `version()` accessor.
3. `MechanismSpecificExperimentHarness` now declares the graph adapter as a `FullGraphExperimentSessionFactory`. Its three inputs (scenario, mechanism, arm) are converted into a `MechanismArmPlan`, then passed to the mechanism-specific session factory with the frozen training bundle. This preserves the distinction between the two factory contracts and retains the condition/session identity checks.
4. Adds `MechanismExperimentCompileRegressionTest` for B1 reference-version fingerprinting and matched-pair metric deltas.
5. Synchronizes the test inventory and its architecture guard.

## Root-level `src/` finding

The previously delivered full-source ZIP included two stale files under the repository-root `src/` tree. The canonical, actively compiled copies live under `supply-chain-agent-runtime/src/`. The root POM is an aggregator (`packaging=pom`) and does not compile a root-level `src/` tree. The two root copies are not byte-identical to the canonical module sources and are not a separate Maven module. They are therefore orphaned archive entries, not a deliberate part of the multi-module architecture. Run `python scripts/cleanup-orphan-root-src.py` after applying this changeset; it verifies the exact archived SHA-256 of each known orphan before deleting it, and leaves any unexpected files untouched:

- `src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java`
- `src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java`

Do not remove `supply-chain-agent-runtime/src/`. That is the canonical runtime module source tree.

## Statistical tooling boundary

The evaluation implementation uses project-authored Java code for matched execution, evidence fingerprints, deterministic scenario generation, bootstrap resampling, percentile confidence intervals, Brier/ECE calculations and evidence validation. The evaluation module directly depends on JUnit Jupiter for tests and LangGraph4j Core to execute the actual application graph. It does not depend on Apache Commons Math, Smile, SciPy, R or another statistical-analysis package.

The existing `MatchedComparisonAnalyzer` implements a deterministic 2,000-resample bootstrap for its legacy planner comparison. The preregistered M8 mechanism protocol's 10,000-resample stratified/clustered bootstrap, paired confirmatory hypothesis calculations, Holm correction, integrity report and TRANSFER runner remain explicitly not implemented. Passing compiler/test checks is not an empirical result.

## Validation boundary

Static architecture, split and evidence guards plus focused regression checks are required locally. The full Maven/Surefire/PostgreSQL reactor must be rerun by hosted CI. No experiment is authorized by this compile repair; TRAIN, HOLDOUT and TRANSFER execution gates remain unchanged.
