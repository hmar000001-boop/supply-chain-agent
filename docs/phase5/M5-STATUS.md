# M5 status — independent decision algorithms

**Status: implementation delivered; awaiting GitHub Actions CI acceptance.**

## Added

- New framework-neutral Maven module `supply-chain-agent-planning`.
- Shared typed planning request, state, action, budget, statistics, result, trace and strategy contracts.
- Content-sensitive planning request fingerprinting and separate algorithm-configuration fingerprints.
- CoT-style sequential baseline using a provider port and evidence/action validation.
- ToT-inspired bounded search over structured partial recovery action paths, including expansion, ranking, pruning, backtracking, budget-aware frontier retention and checkpoint/resume.
- RAP-inspired domain-grounded MCTS with UCT-style selection, expansion, deterministic simulator-backed rollouts, evaluation, backpropagation and retained candidates/tree checkpoints.
- Shared deterministic candidate replay/evaluation and hard-constraint checking.
- Explainable default planner routing and explicit planner override support.
- B6 resource reservation/consumption integration and cumulative search metrics.
- In-memory planner-session store with optimistic revision checks; durable persistence is deferred to M7.
- ADR-0005, planning architecture, test results and algorithm-to-concept traceability.

## Validation performed in the authoring environment

- Compiled framework-neutral production and test sources with Java 21 (`--release 21 -Xlint:all`).
- Executed M1–M5 tests with a local JUnit-compatible runner that creates a fresh test instance per test method, matching Jupiter's default `PER_METHOD` lifecycle.
- **208 passed, 0 failed:** 14 domain, 43 memory, 20 simulator, 25 action, 70 mechanism and 36 planning tests.
- Parsed Maven POMs and reviewed the new module's dependency boundary.
- Verified the planning module has no LangGraph4j, LangChain4j, JDBC or PostgreSQL dependency.

**Limitation:** the local test harness is not Maven Surefire/JUnit Platform. The full Maven reactor, including M0 LangGraph4j/LangChain4j runtime tests and the PostgreSQL checkpoint integration test, was not run locally. GitHub Actions is the acceptance gate.

## Explicit limitations and deferred work

- CoT is a structured sequential decision baseline, not a stored private chain-of-thought transcript.
- ToT is an inspired bounded tree search over structured partial action paths, not a literal original text-thought implementation.
- MCTS is RAP-inspired and uses the deterministic M1 simulator as world model; it is not a faithful RAP reproduction.
- Default planner routing thresholds are initial explainable heuristics, not empirically optimal values.
- Planner output is proposal-only; M3 authority/dispatch contracts remain mandatory.
- The deterministic evaluator fails closed for protected-customer-commitment preservation because the current M1 domain cannot verify that concept as a first-class record.
- Aggregated action risk assumes independence as an initial scoring approximation.
- Planner sessions are in memory; durable PostgreSQL storage and cross-process resume belong to M7.
- LangGraph4j composition and LangChain4j provider wiring belong to M6.
- Comparative evaluation and mechanism/planner ablations belong to M8.

## Acceptance criteria

- M5's 36 planning tests pass under Maven Surefire in CI.
- All prior M0–M4 tests remain in the reactor and pass.
- The planning module remains independent from agent orchestration frameworks and database adapters.
- CoT/ToT/MCTS use the same candidate evaluation and hard-constraint boundary.
- Search identity/configuration mismatches are rejected on resume.
- Planner limits, termination reasons, trace and B6 usage are observable.
