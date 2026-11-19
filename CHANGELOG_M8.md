# M8 changelog

## 2026-11-19 — M8-B0 baseline and evaluation specification

- Frozen the exact M7-accepted source state as `M8-M7-ACCEPTED-2026-11-19`.
- Added a reproducible full-repository baseline checkpoint and sorted SHA-256 file manifest.
- Audited the ten-module Maven reactor, source/test inventory, runtime composition and simulator/reference-adapter boundary.
- Recorded the framework-scope reconciliation: the later accepted M6 LangGraph4j/LangChain4j implementation supersedes the earlier draft framework-free non-goal.
- Added the architecture-to-code traceability matrix, request/failure scenario register, evaluation protocol and M8 delivery status.
- Updated README and M7 status to reflect the user's confirmation that final M7 CI is green.
- No production Java, POM, SQL migration, workflow, simulator or application behavior changed in M8-B0.

## 2026-11-19 — M8-B1 evaluation foundation (implementation delivered)

- Added a versioned 25-row corpus with 11 request-family fixtures and 14 failure/adversarial/recovery/concurrency fixtures.
- Added deterministic domain snapshots and immutable planning requests with per-scenario seeds, named budget profiles and stable fingerprints.
- Added an evaluation-only deterministic/greedy comparator alongside CoT-style sequential, ToT-inspired bounded search, RAP-inspired MCTS and the custom hybrid router.
- Added batch manifest and JSONL result rows, including planner-configuration fingerprint, resource-unit/pruning/backtracking counters, termination, feasibility and explicit planner-only/no-dispatch boundary.
- Replaced frozen planner clocks with moving clocks offset onto the static fixture timeline; the greedy comparator now enforces hard deadline and runtime as well as expansion/evaluation ceilings.
- Added explicit full-graph adapter injection via `FullGraphEvaluationHost.Ports`; the compile probe uses fail-closed ports, shared in-memory working/episodic repositories and an empty action registry.
- Added focused evaluation tests and `docs/phase8/M8-B1-EVALUATION-ARCHITECTURE.md`.
- No production planning/router, M7 persistence or external integration behavior was changed. The user subsequently confirmed that M8-B1 GitHub Actions CI is green.


## M8-B1 CI correction — Greedy comparator evaluation scope

- Corrected `GreedyDeterministicComparator` so the final candidate evaluation is retained outside the search loop and updated only when the corresponding best state is committed.
- Added a regression assertion that REQ-01 retains its two sequential reservations.
- No production planner, M7 PostgreSQL infrastructure, migration, or external-action execution path changed.
- GitHub Actions reported the initial compile-time failure at `GreedyDeterministicComparator.java:121`; local Maven 3.9.16 bootstrap is unavailable in this environment, so the corrected patch must be re-run through the repository CI.


## M8-B1 acceptance — GitHub Actions green (2026-11-19)

- User confirmed the complete M8-B1 GitHub Actions workflow is green after the greedy comparator scope correction and sequential-reservation regression assertion.
- M8-B1 is accepted; the M7 baseline remains frozen.

## 2026-11-19 — M8-B2 matched comparative analysis (implementation delivered; CI pending)

- Added complete matched-matrix validation for all scenario/procedure pairs and rotating per-scenario comparator execution order.
- Added overall and stratified comparative aggregates and ten pairwise feasibility-first comparisons with deterministic scenario-level bootstrap intervals.
- Extended raw JSONL with compact candidate evaluation fields and structured planner trace nodes, excluding private reasoning.
- Added logical replay determinism verification excluding wall-clock runtime and execution-order position.
- Added `scripts/run-m8-comparison.sh` and the M8-B2 comparative analysis specification.
- No production planner, router, simulator, action, authority, PostgreSQL or graph behavior changed. Tests were intentionally not executed during authoring at the user's request; CI acceptance is pending.
