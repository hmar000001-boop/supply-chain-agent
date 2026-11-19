# M8-B1 change-only delivery

**Target baseline:** `M8-M7-ACCEPTED-2026-11-19`  
**Required overlay order:** accepted M7 baseline → M8-B0 patch → this M8-B1 patch.

## Contents

This change set adds the `supply-chain-agent-evaluation` Maven module and registers it in the root reactor. It includes:

- the versioned `M8-B1-v1` scenario catalog (11 request-family rows and 14 failure/adversarial/recovery/concurrency rows);
- deterministic domain snapshots, planning requests, evidence references, stable fingerprints, fixed seeds and checked budget profiles;
- proposal-only CoT-style sequential, ToT-inspired bounded search, evaluation-only greedy, RAP-inspired MCTS and hybrid-router comparators;
- JSONL result rows and a batch reproducibility manifest;
- a full-graph composition host that invokes `CognitiveCycleApplicationGraph.compileFull(...)` with explicit ports and a fail-closed compilation probe;
- focused tests, architecture documentation and a runner script.

## Apply

Extract the ZIP at the repository root, preserving the paths. It is a change-only overlay and does not contain the complete repository. It does not replace M8-B0; apply B0 first.

## Validate

The GitHub Actions Java 21 Maven reactor `clean verify` is the acceptance gate. After applying the patch, run the full CI workflow. A local build was not completed in the authoring environment because the Maven 3.9.16 wrapper distribution was not cached and the Apache download host could not be resolved; no local green build is claimed.

After the CI run is green, generate the evaluation batch with:

```bash
./scripts/run-m8-evaluation.sh > m8-evaluation-results.jsonl
```

The script runs the reactor test/install before invoking the evaluation CLI. Build output is sent to stderr; stdout contains one JSONL manifest and 125 planner-only result rows. To list scenario IDs without executing planners:

```bash
./scripts/run-m8-evaluation.sh --list
```

## Scope and safety

- Planner comparisons are proposal-only. They do not dispatch actions or call external systems.
- The greedy comparator is evaluation-only and is not registered in the production planner router.
- The full-graph host uses an empty action registry. Its default probe only compiles the real full graph; injected domain/authority ports throw if invoked.
- The 25 input fixtures are not 25 tested end-to-end workflows. Approval expiry/denial, external unknown outcomes, restart, duplicate delivery, races and provider failure require M8-B4 lifecycle execution.
- `RecordingPlannerResourceMeter` is per-run instrumentation, not a durable or authoritative B6 budget ledger.
- M7 PostgreSQL infrastructure remains frozen; no migration or persistence code is changed.

## Files and integrity

See `M8-B1-CHANGE-MANIFEST.csv` and `M8-B1-CHANGESET-ONLY.zip.sha256` in the delivery folder for the exact changed paths and package checksum.
