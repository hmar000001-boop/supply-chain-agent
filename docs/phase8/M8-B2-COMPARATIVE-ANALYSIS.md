# M8-B2 — matched comparative decision-procedure evaluation

**Accepted baseline:** `M8-M7-ACCEPTED-2026-11-19`  
**Scenario corpus:** `M8-B1-v1`  
**Comparators:** `COT_SEQUENTIAL`, `TOT_BOUNDED_SEARCH`, `GREEDY_DETERMINISTIC`, `RAP_INSPIRED_MCTS`, `HYBRID_ROUTER`

## Purpose and boundary

M8-B2 adds the analysis protocol around the M8-B1 planner-only runner. It does not change a production planner, production router, simulator transition, PostgreSQL migration, LangGraph4j graph, action handler or authority policy. Each procedure sees the same versioned fixture/request, evidence, action descriptors, hard constraints, simulator-backed world model and deterministic candidate evaluator. No comparator has an external dispatcher.

The corpus is 25 fixtures × 5 procedures = 125 primary run rows. The five procedure comparisons yield ten paired procedure pairs. The 14 failure/adversarial/recovery/concurrency fixtures remain planner inputs in this batch; lifecycle assertions for these cases belong to M8-B4.

## Matched design

Before aggregate analysis, `MatchedComparisonAnalyzer` validates the complete matrix:

- exactly one row for every scenario/procedure pair;
- no unknown planner IDs, duplicate rows or missing rows;
- identical fixture version, seed, budget profile, fixture/request fingerprints, scenario kind, fixture profile and effect boundary across all procedures for a scenario;
- the five per-scenario execution-order indices form a permutation of `0..4`;
- every row remains planner-only and reports no external dispatch.

Comparator order rotates by scenario index. This reduces systematic warm-up/cache/order effects; it does not make runtime a controlled microbenchmark. Per-row runtime remains a descriptive observation only.

## Primary outcomes

The analyzer reports, overall and stratified by budget profile, scenario kind and fixture profile:

- scenario count, feasible-scenario count and feasibility rate;
- mean best feasible score and fulfillment progress (calculated only over scenarios with a feasible candidate);
- mean estimated cost in the fixture's currency, aggregate risk and robustness;
- hard-constraint violations across returned candidate evaluations;
- counts of approval-required and target-deadline-breaching candidates;
- termination-reason distributions, resource-unit totals, mean expansions/evaluations and mean observed runtime.

For each of the ten procedure pairs, outcomes are compared scenario by scenario. A feasible result beats an infeasible result; when both are infeasible the pair is a tie; when both are feasible the best feasible score is compared with a declared `1e-9` tolerance. The report includes win/tie/loss counts and mean paired feasibility difference across all paired scenarios. The score-difference mean and bootstrap interval use only scenarios where both procedures returned a feasible candidate; `scoreComparedScenarioCount` makes this denominator explicit, and score fields are `null` when there are no jointly feasible pairs.

Uncertainty intervals are deterministic scenario-level bootstrap percentile intervals (2,000 resamples) with a seed derived from corpus fingerprint and pair identity. Scenarios—not individual planner rows or candidates—are the sampling units. The interval is descriptive and must be read with the number and diversity of paired scenarios; it is not a causal or universal superiority claim.

## Raw trace and provenance

Each raw run row retains:

- baseline-relevant fixture, request and planner identifiers/fingerprints, seed, budget profile and actual per-scenario execution-order index;
- candidate evaluation fields: feasibility, score, fulfillment progress, estimated cost/currency, expected duration, risk, robustness, predicted completion, deadline-breach flag, approval-required flag and hard-violation codes;
- compact planner trace nodes: node/parent/action IDs, depth, score, visits, mean reward, disposition and child IDs;
- termination, search/resource counters and measured runtime.

The trace is structured search provenance only. It does not capture private chain-of-thought or free-text rationale. Candidate evaluations describe returned candidates; they do not imply that every search expansion was materialized as a candidate.

## Repeat-run logical determinism

After the primary matrix, `DeterminismVerifier` reruns each scenario/procedure pair from a fresh fixture and planner instance. It compares a logical fingerprint containing planner selection, termination, candidate evaluations, structured trace, search counters and resource-unit counters. It excludes wall-clock runtime and execution-order position. Mismatches are reported rather than hidden or automatically treated as CI failures; investigate whether they arise from algorithmic nondeterminism, deadlines or an unstable adapter before interpreting a result.

## Execution and artifacts

From the repository root:

```bash
./scripts/run-m8-comparison.sh
```

The script first runs the full Java 21 Maven reactor, including PostgreSQL phase-zero integration tests, using `clean install`. Only after that succeeds does it execute the comparison CLI. It writes:

- `target/m8-b2-results/m8-b2-raw-runs.jsonl` — one batch manifest and 125 planner-run rows;
- `target/m8-b2-results/m8-b2-comparison-report.jsonl` — comparison manifest, stratified strategy aggregates and ten paired comparisons;
- `target/m8-b2-results/m8-b2-determinism-check.jsonl` — 125 replay comparisons and mismatch IDs.

Set `M8_B2_OUTPUT_DIR` to select a different output directory. Archive all three result files with the repository revision, exact delivery checksum and CI run link. A new corpus/evaluator/planner configuration is a new experiment identity; do not merge it silently with `M8-B1-v1`.

## Interpretation and limitations

- Sequential and ToT-inspired baselines use deterministic structured decision/ranking policies with no live LLM calls. This compares search procedures, not prompt engineering or model-generated reasoning quality.
- MCTS is RAP-inspired and grounded in the supply-chain simulator; it is not a claim of faithful RAP reproduction.
- The hybrid router is evaluated as a policy under the same per-fixture budget, not granted a larger aggregate allowance. Its selected procedure is recorded.
- The greedy comparator is evaluation-only and is not registered in the production router.
- Scores are outputs of the shared current evaluator. They are not proof of real supplier performance, calibrated probabilities or realized financial savings.
- Runtime means are reported only for diagnostics. No latency winner is declared from these runs.
- These runs do not evaluate B1/B2/B3/B4/B6 ablations, end-to-end lifecycle behavior, approval/dispatch safety, restart, duplicate effects or external system connectivity. Those are separate M8-B3/M8-B4 work.
- No universal planner winner is inferred from this finite pilot corpus. Report per-scenario outcomes, strata, sample sizes and uncertainty together.

## Acceptance

M8-B2 acceptance requires the user's GitHub Actions result for the change-only patch. The comparison artifacts are generated by the repository script after that CI/build gate; no experiment results are fabricated or included in the source patch.
