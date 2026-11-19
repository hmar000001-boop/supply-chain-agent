# M8-B0 — Baseline and evaluation specification

This is a documentation-only incremental patch based on `M8-M7-ACCEPTED-2026-11-19`. Apply it at the repository root, preserving paths. It updates the README/M7 acceptance text and adds `docs/phase8/` with the baseline audit, manifest, architecture traceability, test inventory, scenario register, evaluation protocol, decisions log and M8 status.

No Java, Maven POM, SQL migration, CI workflow, simulator or application behavior is changed. No Maven build or test run was executed for this documentation-only phase; the user-confirmed green M7 CI remains the starting acceptance evidence.

The full source checkpoint is delivered separately as `SUPPLY_CHAIN_AGENT_M7_ACCEPTED_BASELINE_2026-11-19.zip`. Verify it with its `.sha256` sidecar and `M8-BASELINE-FILE-MANIFEST.sha256`.

M8-B1 is the next implementation increment: create the reproducible scenario fixtures and full-graph evaluation host, and add a fair deterministic/greedy planner comparator before comparative runs. B3 outcome-attributed learning and B6 host-level durable budget ownership are explicit evaluation gates, not assumed features.
