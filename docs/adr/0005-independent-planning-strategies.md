# ADR-0005: Independent planning strategies behind a common contract

- **Status:** Accepted for M5 implementation; runtime integration remains M6.
- **Date:** 2026-11-17
- **Decision owners:** Part III application architecture

## Context

The application must demonstrate CoALA's repeated decision procedure and compare distinct ways of generating/recovering candidate plans. LangGraph4j is the outer execution framework, but a graph does not itself implement CoT, ToT, or RAP/MCTS. Mixing search internals into graph nodes would conflate the application's control-flow graph with an algorithm's internal search tree.

The application must also preserve domain authority: candidate generation cannot change authoritative supply-chain state, grant capabilities, approve commitments, or execute actions. All candidates need the same deterministic feasibility/replay and scoring boundary.

## Decision

Create a framework-neutral `supply-chain-agent-planning` Maven module with:

1. a common `PlanningStrategy` interface and typed `PlanningRequest`/`PlanningResult` contracts;
2. a CoT-style sequential baseline using an injected `SequentialDecisionProvider`;
3. a ToT-inspired bounded search over structured partial action plans using a `ThoughtActionRanker`;
4. a RAP-inspired domain-grounded MCTS planner using a deterministic simulator-backed `PlanningWorldModel`;
5. one common deterministic candidate evaluator and one explainable planner router;
6. versioned planner-session checkpoint contracts and an in-memory reference implementation;
7. integration with M4 B6 computational resource accounting.

LangGraph4j remains the future outer coordinator (M6). LangChain4j may be wired as an implementation of model-facing ports in M6, but the planning module does not depend on either framework.

## Rationale

- Algorithms can be unit-tested and compared without starting a graph runtime or calling a provider.
- The same business constraints, candidate evaluator, test corpus, and resource accounting can be used across algorithms.
- The simulator remains the source of truth for modeled state transitions.
- Search checkpoint/resume can evolve independently from LangGraph4j execution checkpoints.
- The graph can call a planner as an application service without turning every search-tree node into a graph node.

## Alternatives considered

### Implement each planning step as LangGraph4j nodes

Rejected. This would represent algorithm-internal expansion/backtracking as outer application orchestration, couple planner logic to framework state and obscure the distinction between a decision algorithm and the agent lifecycle.

### Use one model prompt for all planning approaches

Rejected. It would not provide distinct, testable algorithmic behavior or a fair comparison between sequential, tree-search and MCTS approaches.

### Use LangChain4j Agentic as the planning engine

Rejected for this responsibility. It would add a second orchestration abstraction and would not itself implement the specific ToT/MCTS search procedures required by the book. The experimental Agentic module remains outside the selected runtime stack.

### Let an LLM simulate authoritative supply-chain state transitions

Rejected. The planning world model must enforce explicit supplier, inventory, order, substitution, route and shipment invariants. Model output may propose or rank, but deterministic domain transitions must decide modeled state.

## Consequences

Positive:
- Algorithms have independent contracts and tests.
- Feasibility and authorization stay outside the planner.
- Search limits and termination reasons are explicit.
- Planner sessions can be checkpointed independently.

Trade-offs and limitations:
- The ToT implementation is ToT-inspired structured action-path search, not a literal reproduction of original textual thought prompting.
- MCTS is RAP-inspired with a deterministic domain world model, not faithful RAP's LLM world-model arrangement.
- The default router thresholds are initial heuristics and require M8 evaluation.
- Planner-session persistence is currently in memory; M7 must supply durable transactional storage and process-restart recovery.
- The current domain cannot verify protected customer commitments as a first-class object, so the evaluator fails closed for that constraint.
- Risk aggregation currently assumes independent per-action risks; this is an explicit modeling assumption requiring evaluation.
