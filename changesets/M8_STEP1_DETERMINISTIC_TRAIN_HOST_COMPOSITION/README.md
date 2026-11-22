# M8 Step 1 — deterministic TRAIN host composition

## Apply

Extract this ZIP at the repository root. All project entries use repository-root-relative paths. Package metadata is isolated under `changesets/M8_STEP1_DETERMINISTIC_TRAIN_HOST_COMPOSITION/`; it does not overwrite root README, manifests, or build files.

Project delta: **7 added Java files, 1 complete test-file replacement, 2 guard/inventory replacements, 1 added research note; zero deletions**.

## What this delivers

- An explicit deterministic TRAIN execution profile that rejects live-model execution and non-deterministic planning/simulation.
- A per-arm deterministic Ports factory contract and immutable session configuration.
- A concrete composer that compiles the actual full LangGraph4j application graph per TRAIN session, with a fresh in-memory checkpoint saver, unique checkpoint thread, unique session/sandbox identities, canonical generated-fixture input, and fail-closed sandbox attestation.
- A TRAIN-only host that orchestrates all five mechanism telemetry batches over the exact preregistered 280-unit TRAIN set, emits per-mechanism JSONL plus an atomic status manifest, and refuses to overwrite an existing output directory.
- A full-graph workflow regression test that now uses the composed session factory and retains the strict arm-isolation checks.

## Important boundaries

The host emits telemetry only: `FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS`. It does not generate independently verified outcome labels, build/freeze the B1/B2/B3/B6 training artifact bundle, authorize activation, or open HOLDOUT/TRANSFER. `runAll` has not been invoked.

The scenario-specific deterministic Ports factory remains an injected trusted dependency. The existing complete fixture-specific port adapters are currently private to `M8B4FullGraphWorkflowTest`; extracting them as a reusable main-source provider is still part of completing Step 1. Do not treat this package as an unattended TRAIN-run release.

The current hosted CI Fix 3 run was still in progress when this increment was prepared. This candidate has not been accepted by the full hosted CI workflow.

## Apply order

Apply only after the CI Fix 3 workflow has completed successfully. Then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

