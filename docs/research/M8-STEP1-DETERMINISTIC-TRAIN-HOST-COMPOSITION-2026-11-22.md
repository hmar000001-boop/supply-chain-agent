# M8 Step 1 — deterministic TRAIN host composition

Date: 2026-11-22  
Status: composition API and graph-session composer implemented; reusable scenario-port provider extraction remains open.  
Candidate code-tree SHA-256: `a1a2b4372b8c778e92a17fc363250c2f84dafdd0e6f2ed46abf0e15d964e5737`

## Scope

This increment starts the six-step execution plan while the previously submitted CI workflow is still running. It does not modify the candidate already being checked by that workflow. It adds a separate, additive candidate on top of CI Fix 3.

Step 1 creates the primary deterministic TRAIN composition boundary around the existing LangGraph4j graph and existing exact-280-unit telemetry runner. It does not run experiments, create outcome labels, train B1/B2/B3/B6 artifacts, authorize activation, or open HOLDOUT/TRANSFER.

## Added composition types

- `DeterministicTrainExecutionProfile`: an immutable declaration of request-interpreter, reasoning, planner and simulator profiles. It rejects LIVE_MODEL execution and non-deterministic planning/world behavior for the primary TRAIN track.
- `DeterministicTrainPortsFactory`: a per-unit/per-mechanism/per-arm factory for fresh deterministic graph ports and metric/audit inputs.
- `DeterministicTrainSessionConfiguration`: an immutable per-arm record carrying graph ports, a condition fingerprint, its own audit/metric collectors, optional B1 seeds, a B3 shadow adjustment and approved B2 seeds.
- `ComposedDeterministicTrainSessionFactory`: compiles the actual full application graph using `FullGraphEvaluationHost` and `FullGraphExperimentSession.compile`. It supplies unique session, sandbox and checkpoint-thread IDs, a fresh in-memory checkpoint saver, canonical scenario-bound graph input and a fail-closed sandbox attestation.
- `DeterministicTrainExperimentHost`: composes the TRAIN-only `FullGraphExperimentAdapter` and `FullGraphExperimentTrainingBatchRunner`. It runs the five mechanisms across the exact preregistered TRAIN split and writes one JSONL per mechanism plus an atomic run-status manifest. It refuses to reuse an existing output directory.

## Evidence boundary

All outputs are marked `FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS`; `confirmatoryInferenceAllowed=false`, `holdoutOpened=false`, and `transferOpened=false`. The host outputs graph telemetry only. It must not be mistaken for verified downstream outcome evidence or a frozen mechanism training-artifact bundle.

The declared deterministic profile is an explicit trusted-host attestation, not cryptographic proof that an arbitrary factory behaves deterministically. The underlying `FullGraphExperimentAdapter` continues to enforce exact fixture matching, per-arm graph/runtime/repository/consumer isolation, matched inputs, sandbox counters and the no-dispatch approval boundary.

## Test integration

`M8B4FullGraphWorkflowTest.fullGraphExperimentAdapterRunsMatchedIsolatedTrainingArmsWithoutDispatch` now exercises `ComposedDeterministicTrainSessionFactory` rather than manually assembling `FullGraphExperimentSession` objects. The test's deterministic ports factory creates a new existing full-graph fixture runtime for each arm and verifies that the graph pair reaches the approval boundary without dispatch and with distinct workspace consumers.

`DeterministicTrainExperimentHostTest` covers profile rejection, deterministic requirement checks, no graph construction during host initialization, and refusal to reuse an existing evidence directory.

The M8 source/test inventory was updated: evaluation module now has 78 production Java files, 12 test sources, 49 test methods/factories and 12 annotated test classes.

## Remaining Step 1 work

The concrete fixture-specific ports composition currently remains in the `M8B4FullGraphWorkflowTest` scenario runtime. The new production-facing composition contracts and session compiler are in place, but that large fixture adapter has not yet been extracted into a reusable main-source `DeterministicTrainPortsFactory`. Until that extraction is complete and CI validates it, the host is not yet ready for an unattended 1,400-pair TRAIN run.

No TRAIN execution has been performed. The current CI Fix 3 workflow was reported by the owner as having a green build step but was still running when this increment began. This candidate is therefore not CI-verified.
