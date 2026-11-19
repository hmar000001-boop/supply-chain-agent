# M8 changelog

## 2026-11-19 — M8-B0 baseline and evaluation specification

- Frozen the exact M7-accepted source state as `M8-M7-ACCEPTED-2026-11-19`.
- Added a reproducible full-repository baseline checkpoint and sorted SHA-256 file manifest.
- Audited the ten-module Maven reactor, source/test inventory, runtime composition and simulator/reference-adapter boundary.
- Recorded the framework-scope reconciliation: the later accepted M6 LangGraph4j/LangChain4j implementation supersedes the earlier draft framework-free non-goal.
- Added the architecture-to-code traceability matrix, request/failure scenario register, evaluation protocol and M8 delivery status.
- Updated README and M7 status to reflect the user's confirmation that final M7 CI is green.
- No production Java, POM, SQL migration, workflow, simulator or application behavior changed in M8-B0.

## 2026-11-19 — M8-B1 evaluation foundation (implementation delivered; CI pending)

- Added a versioned 25-row corpus with 11 request-family fixtures and 14 failure/adversarial/recovery/concurrency fixtures.
- Added deterministic domain snapshots and immutable planning requests with per-scenario seeds, named budget profiles and stable fingerprints.
- Added an evaluation-only deterministic/greedy comparator alongside CoT-style sequential, ToT-inspired bounded search, RAP-inspired MCTS and the custom hybrid router.
- Added batch manifest and JSONL result rows, including planner-configuration fingerprint, resource-unit/pruning/backtracking counters, termination, feasibility and explicit planner-only/no-dispatch boundary.
- Replaced frozen planner clocks with moving clocks offset onto the static fixture timeline; the greedy comparator now enforces hard deadline and runtime as well as expansion/evaluation ceilings.
- Added explicit full-graph adapter injection via `FullGraphEvaluationHost.Ports`; the compile probe uses fail-closed ports, shared in-memory working/episodic repositories and an empty action registry.
- Added focused evaluation tests and `docs/phase8/M8-B1-EVALUATION-ARCHITECTURE.md`.
- No production planning/router, M7 persistence or external integration behavior was changed. Maven CI remains the acceptance gate.
