# M8-B2 change set — matched comparative analysis

## Apply to

Apply this change-only patch to the repository after both M8-B1 deliveries have been applied and accepted by GitHub Actions:

- `M8_B1_EVALUATION_FOUNDATION_CHANGESET_ONLY.zip`
- `M8_B1_CI_FIX_CHANGESET_ONLY.zip`

The accepted M7 baseline remains `M8-M7-ACCEPTED-2026-11-19`, and the scenario corpus remains `M8-B1-v1`. M8-B2 does not reopen M7 or modify production execution paths.

## Delivered

- Strict validation of the matched 25-scenario × 5-comparator matrix, including fixture/request/seed/budget equality, uniqueness, effect boundary, and rotation indexes.
- Scenario-order-indexed rotation of comparator execution order to reduce systematic execution-order bias.
- Overall and stratified aggregates by budget profile, scenario kind and fixture profile.
- Ten paired procedure comparisons using feasibility-first win/tie/loss rules and deterministic scenario-level bootstrap percentile intervals (2,000 replicates).
- Candidate-level evaluation details and compact structured planner trace nodes in raw JSONL. No free-text rationale or private chain-of-thought is recorded.
- Repeat-run logical determinism verification. Wall-clock runtime and comparator-order position are excluded from the logical fingerprint; nondeterministic outcomes are reported with scenario/procedure IDs.
- `scripts/run-m8-comparison.sh` and `docs/phase8/M8-B2-COMPARATIVE-ANALYSIS.md`.

No production planner, planner router, simulator, action handler, authority policy, LangGraph4j graph, PostgreSQL schema or M7 infrastructure is modified.

## Execute after CI acceptance

```bash
./scripts/run-m8-comparison.sh
```

The script runs the full Java 21 Maven reactor with PostgreSQL phase-zero integration enabled (`clean install`) before executing the comparison CLI. Output is written to `target/m8-b2-results/`:

- `m8-b2-raw-runs.jsonl`
- `m8-b2-comparison-report.jsonl`
- `m8-b2-determinism-check.jsonl`

Set `M8_B2_OUTPUT_DIR` to select another output directory.

## Validation boundary

Tests and Maven compilation were intentionally not run while authoring this delivery, at the user's direction. The change set includes focused assertions in `M8EvaluationRunnerTest`; the user's GitHub Actions run is the acceptance gate. No comparison result is fabricated or bundled with this source patch. The 25 fixture rows are planner inputs; lifecycle scenarios remain for M8-B4. Runtime is descriptive only and no universal winning planner is asserted.
