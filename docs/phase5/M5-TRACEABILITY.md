# M5 algorithm and architecture traceability

| Book concept / application responsibility | Implementation | Verification / boundary |
|---|---|---|
| Common planning interface | `api/PlanningStrategy` | Router and strategy tests |
| Trusted request and constraints | `api/PlanningRequest` | Constructor invariants and fingerprint tests |
| Semantic execution identity | `api/PlanningRequestFingerprint` | Full snapshot and memory-content fingerprint tests |
| Typed planning state/actions | `api/PlanningState`, `PlanningAction`, `PlanningWorldModel` | Simulator replay tests |
| Domain-grounded world model | `api/SimulatorBackedPlanningWorldModel` | Uses the M1 deterministic simulator |
| CoT-style sequential baseline | `cot/SequentialReasoningPlanner`, `SequentialDecisionProvider` | Evidence/action validation and stop tests |
| ToT-inspired bounded tree search | `tot/ThoughtSearchPlanner`, `ThoughtTree`, `ThoughtNode` | Frontier, branching, pruning, checkpoint and resume tests |
| RAP-inspired MCTS | `mcts/MctsSearchPlanner`, `MctsParameters` | UCT/rollout, seed reproducibility, candidate retention tests |
| Shared plan feasibility/scoring | `api/DeterministicCandidateEvaluator` | Full simulator replay, hard constraints and scoring tests |
| Common plan result/trace | `api/PlanCandidate`, `PlanEvaluation`, `PlanningResult`, `PlannerTraceNode` | Candidate/evaluation tests |
| Explainable planner selection | `routing/PlannerRouter`, `DefaultPlannerSelectionPolicy` | Complexity routing, fallback and override tests |
| B6 computational resource accounting | `api/B6PlannerResourceMeter`, `PlannerExecutionContext` | Budget/usage and cumulative statistics exercised by planner tests |
| Planner state persistence contract | `session/PlannerSessionStore`, `PlannerSessionCheckpoint` | Revision/stale-write tests |
| Current local checkpoint adapter | `session/InMemoryPlannerSessionStore` | In-memory only; M7 durable adapter required |
| LangGraph4j integration | Not included in M5 | M6 outer orchestration |
| LangChain4j model provider wiring | Ports only; runtime adapter not included in M5 | M6 integration |
| Durable planner session and JVM restart recovery | Not included | M7 |
| Comparative benchmark and ablation | Not included | M8 |

## Seven-field mechanism interaction notes

M5 consumes M4 capabilities without reimplementing those mechanisms:

- **S — State:** `PlanningRequest`, `PlanningState`, candidate paths, planner session checkpoints, resource counters.
- **C — Control:** planner can propose only; common evaluator and M3 authority/action layer retain feasibility and execution authority.
- **T — Trigger:** called after application proposal/context preparation; M6 will route planner invocation in the outer decision cycle.
- **P — Persistence:** algorithm-specific session checkpoint contract with in-memory adapter; durable store deferred to M7.
- **F — Failure:** typed termination reason, invalid proposal and constraint violations; no candidate is fabricated on failure.
- **L — Learning:** M5 records metrics and traces but does not autonomously train/update a planner. Verified outcome-driven updates remain governed by M2/M3/M4 learning paths and later evaluation.
- **R — Resources:** algorithm budgets plus M4 B6 resource meter; budget exhaustion yields explicit termination and resumable frontier/tree where supported.

## Claim boundaries

- CoT: structured sequential decision baseline, not private reasoning transcript storage.
- ToT: inspired structured action-path tree search, not a literal replication of original textual-thought prompting.
- RAP/MCTS: domain-grounded RAP-inspired adaptation; the M1 simulator replaces an LLM world model for authoritative modeled transitions.
- Router thresholds are initial policy heuristics; M8 supplies comparative evidence.
- Current MCTS/ToT checkpoint storage is in-memory; not process-restart durable.
