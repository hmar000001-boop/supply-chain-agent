# Independent planning architecture

## Purpose and boundaries

M5 adds `supply-chain-agent-planning`, a framework-independent module that implements three independently callable planning strategies behind one contract. Its job is to propose and evaluate recovery plans over the trusted M1 supply-chain simulator. It does not authorize, dispatch, or execute business actions.

The four architectural levels remain distinct:

1. **CoALA** organizes memory, internal/external actions, and the repeated decision procedure.
2. **Fan & Lan mechanisms** provide B1 retrieval, B2 impasse handling, B3 workspace management, B4 intention/method governance, and B6 meta-control.
3. **Planning algorithms** produce candidate plans using sequential reasoning, bounded tree search, or MCTS.
4. **LangGraph4j** will orchestrate the outer cognitive lifecycle in M6. It does not implement the search algorithms.

The planning module has no dependency on LangGraph4j, LangChain4j, JDBC, or PostgreSQL. Model-backed proposal generation can be supplied through the `SequentialDecisionProvider` and `ThoughtActionRanker` ports, but deterministic implementations are provided for repeatable tests. Runtime wiring is an M6 responsibility.

## Common planner contract

`PlanningStrategy` exposes a strategy identifier and `plan(PlanningRequest)`. Each planner returns a `PlanningResult` containing:

- zero or more typed `PlanCandidate` values;
- a typed termination reason;
- aggregate `PlanningStatistics`;
- a planner-session identifier;
- an explanation of the strategy/selection;
- a structured trace of explored search nodes.

Inputs are immutable and include the authoritative recovery objective, initial `SupplyChainSnapshot`, working-memory snapshot, trusted evidence references, caller-approved action definitions, hard constraints, scoring/risk/authority policies, complexity description, budgets, and request time.

Planner outputs are proposals only. The caller must still independently validate feasibility, enforce the authority policy, obtain required human approval, and dispatch through M3's action system. A high plan score is never an execution permission.

## CoT-style sequential baseline

`SequentialReasoningPlanner` requests one next-step decision from an injected `SequentialDecisionProvider`, validates that the selected action is present in the trusted available-action set and that its evidence references are a subset of the evidence supplied by the caller/request, applies the action through the world model, and returns a single linear candidate for common evaluation.

This is a sequential reasoning baseline, not a search algorithm. It does not branch, backtrack, or maintain resumable planner sessions. Resume requests are explicitly rejected. The implementation retains typed decisions, evidence references, assumptions, and a concise rationale; it does not persist hidden chain-of-thought transcripts.

The default deterministic provider makes tests independent of an LLM. A provider must explicitly identify itself as model-backed for model-call accounting to be charged.

## ToT-inspired bounded search

`ThoughtSearchPlanner` explores a tree of structured partial action plans. `ThoughtNode` and `ThoughtTree` capture parent/child relationships and action paths. `ThoughtActionRanker` orders currently available, trusted domain actions; the default ranker is deterministic. The planner expands alternatives, evaluates partial paths, prunes invalid or dominated branches, records backtracks, and returns the best feasible candidates under explicit search limits.

This is a **ToT-inspired domain-action search**, not a literal reproduction of the original textual-thought generation/evaluation protocol. The tree nodes are structured partial recovery plans/action paths. They are not individual LangGraph4j nodes.

The planner enforces depth, expansion, evaluation, model-call, candidate, runtime, and deadline limits. Checkpoint intervals are honored. The checkpoint retains the tree, attempted actions, frontier (including unprocessed work when a budget ends mid-layer), candidates, counters, prune/backtrack metrics, and cumulative elapsed time. Resume rejects a changed request or algorithm configuration.

## RAP-inspired domain-grounded MCTS

`MctsSearchPlanner` implements a RAP-inspired Monte Carlo Tree Search adaptation. It performs:

1. selection using a UCT-style tree policy;
2. expansion with actions available in the current trusted state;
3. bounded simulation/rollout through `PlanningWorldModel`;
4. deterministic candidate evaluation;
5. reward backpropagation through visited ancestors;
6. extraction of the best feasible root-path candidates.

The `SimulatorBackedPlanningWorldModel` delegates modeled transitions to the deterministic M1 simulator. It is the authority for modeled supply-chain state changes. An LLM may eventually help propose or rank actions through a port, but it must not invent authoritative inventory, supplier, shipment, or order transitions.

This is explicitly **RAP-inspired, domain-grounded MCTS**, not a faithful RAP reproduction: RAP's original LLM world-model role is replaced by the explicit deterministic supply-chain transition model. MCTS search and rollout state are persisted in the planner-session checkpoint and restored with deterministic seed/configuration checks.

## Shared candidate evaluator

All strategies feed the same `CandidateEvaluator`. The deterministic implementation replays each proposed action against the trusted world model, verifies that each action exactly matches a currently available canonical action definition, checks root/objective identity, and compares the full proposed world snapshot with the replayed result.

It then evaluates the configured service progress, cost, timeliness, risk, and robustness criteria. Hard constraints are evaluated separately from the weighted score. An infeasible candidate receives a zero score and explicit violations. Approval requirements are reported as governance requirements, not treated as a planner's authority to execute.

Important current domain boundary: M1 does not yet represent protected customer commitments as a separately verifiable commitment object. The evaluator therefore fails closed when `PRESERVE_PROTECTED_CUSTOMER_COMMITMENTS` is requested. It does not claim to verify a constraint the current model cannot express.

The aggregate risk calculation treats per-action risks as independent for this initial engineering model. This is a documented scoring assumption, not a universal risk model. It must be reviewed against the eventual evaluation corpus in M8.

## Planner selection and fairness

`PlannerRouter` requires all three strategies to be registered exactly once and validates that the returned planner identity matches the selected strategy. `DefaultPlannerSelectionPolicy` uses explainable initial thresholds based on expected branching, horizon, uncertainty, consequence severity, expansion budget, and rollout budget:

- low-complexity cases select the sequential baseline;
- moderate cases select bounded ToT-style search;
- higher-complexity cases select MCTS when rollout budget exists;
- otherwise the router falls back to bounded ToT-style search.

These thresholds are starting policy values, not empirically optimal thresholds. M8 must tune/evaluate them using the same scenario corpus and comparable resource budgets for each strategy. Explicit planner overrides remain visible in the returned selection explanation.

## Identity, checkpoints, and resume

`PlanningRequestFingerprint` hashes canonical semantic content rather than storage UUIDs. It includes the complete supply-chain snapshot content, semantic working-memory content/evidence, objective, action definitions, evidence, constraints, scoring/risk/authority policy, complexity, request time, and structural budget fields that change the search space (maximum depth and candidate count). It excludes resumable session identity and resource ceilings/deadlines that may legitimately be replenished on resume. The planner algorithm/configuration fingerprint is stored separately.

This prevents resuming a search against a changed world merely because the snapshot version number is unchanged, while allowing a caller to provide additional compute budget to continue the same search. Working-memory UUID identity is not treated as semantic content; the scope, version, values, hypotheses, expiry and evidence are.

The current `InMemoryPlannerSessionStore` is a deterministic local/reference adapter. It enforces revision progression and rejects stale/concurrent writes, but it does not survive process restart. Durable transactional planner-session storage, schema migration, and process-restart recovery are M7 responsibilities.

## B6 resource integration

Planner limits in `PlannerBudget` are local algorithmic ceilings. `B6PlannerResourceMeter` separately reserves and consumes the shared computational resource units through the M4 meta-control contracts. The planner execution context records expansions, evaluations, model calls, rollouts, pruned branches, backtracks, and cumulative elapsed time across checkpoint/resume. A resource/deadline stop is represented as a typed termination reason and does not weaken domain hard constraints.

## Failure and stopping semantics

The result distinguishes normal completion from no available actions, no feasible candidates, maximum depth, search/resource budget exhaustion, deadline expiry, invalid model/provider proposal, and explicit strategy stop. Planners do not silently fabricate a candidate when a provider stops or returns an invalid action. Resource accounting and stopping metadata are returned to the caller for B6 and application-level policy.

## M5 limitations and next integration

M5 supplies the independent algorithms and a reference in-memory session store. It does not yet:

- connect the planners to the complete LangGraph4j graph (M6);
- wire production model-backed thought generation/decision providers (M6 runtime integration);
- store planner sessions durably in PostgreSQL or recover them across JVM process restarts (M7);
- establish empirically optimal router thresholds or prove one planner is superior (M8);
- implement external execution or authorization (M3/M6/M7 remain the responsible layers).

The source of truth is the `supply-chain-agent-planning` Maven module and its tests. Book listings will later point to exact tagged source files after M8 application freeze.
