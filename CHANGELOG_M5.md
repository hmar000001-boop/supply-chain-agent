# M5 changelog — independent planning strategies

## Added

- `supply-chain-agent-planning` Maven module, independent of LangGraph4j, LangChain4j, JDBC and PostgreSQL.
- Common typed planner API, immutable request/state/action contracts, candidate/evaluation/result/trace contracts, budgets, statistics and termination reasons.
- Semantic request fingerprinting of complete supply-chain state and working-memory content, plus versioned world/evaluator/ranker/algorithm configuration fingerprints.
- CoT-style sequential decision baseline with trusted evidence/action validation and explicit stop semantics.
- ToT-inspired bounded search over structured partial action paths, with frontier/tree checkpoints, checkpoint interval, mid-layer budget recovery, pruning/backtracking metrics and resume validation.
- RAP-inspired domain-grounded MCTS with UCT-style selection, expansion, deterministic simulator-backed rollouts, evaluation, backpropagation, best-candidate retention and resumable tree state.
- Deterministic candidate evaluator that replays canonical actions against the M1 simulator and enforces explicit hard constraints before scoring.
- Explainable default planner router, fallback and explicit override reporting.
- B6 computational resource accounting integration and cumulative planner statistics.
- In-memory planner-session store with optimistic revision protection.
- ADR-0005 and M5 architecture/status/test/traceability documentation.

## Modified

- Root `pom.xml`: adds `supply-chain-agent-planning` to the Maven reactor after domain/simulator/mechanism modules.
- Root `README.md`: records accepted M0–M4 status and describes M5, its current limitations and next milestones.
- `docs/phase4/M4-STATUS.md`: updated to reflect the user's report that M4 GitHub Actions CI is green.

## Explicitly not included

- No M6 LangGraph4j graph composition or runtime model-provider adapter.
- No M7 PostgreSQL planner-session repository, durable memory/action adapters, or JVM process restart recovery.
- No M8 benchmark corpus, comparative results, or mechanism/planner ablation claims.
- No chapter prose; Chapters 19 and 20 remain after full application freeze.

## Validation

- Local JDK 21 framework-neutral production and test-source compilation.
- 208 local regression methods passed, 0 failed (172 M1–M4 plus 36 M5).
- Local test runner uses a JUnit-compatible shim and fresh instance per test; it is not Maven Surefire.
- Full Maven reactor and framework-dependent M0 tests require GitHub Actions CI.
