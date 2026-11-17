# ADR-0006: LangGraph4j cognitive-cycle orchestration

- **Status:** Accepted for M6 implementation; Maven/GitHub Actions validation pending.
- **Date:** 2026-11-17
- **Decision owners:** Part III application architecture

## Context

M0 selected and validated LangGraph4j 1.8.20 LTS with LangChain4j stable core 1.23.0. M1–M5 now provide the supply-chain domain and deterministic simulator, CoALA memory and action contracts, Fan & Lan B1/B2/B3/B4/B6 capabilities, and independent CoT/ToT/MCTS planners. These components must cooperate without collapsing their responsibilities into the graph.

A graph node named `Plan` is not a planning algorithm. A LangGraph4j checkpoint is not episodic memory. A graph state object is not the full CoALA working memory. B2 child resolution needs a separate bounded lifecycle, whereas ToT/MCTS internal search trees must remain inside the planner session.

## Decision

1. Implement a single primary `CognitiveCycleGraph` in `supply-chain-agent-runtime` for request intake, repeated proposal/evaluation/selection, interruption, authorization, execution, observation and reconsideration.
2. Keep CoALA, action, mechanism and planner services framework-neutral and invoke them through `CognitiveCycleOperations`, an application-owned integration port.
3. Store only identifiers, versions, compact counters and routing/status values in `CognitiveCycleState`; large/authoritative records remain in their own stores.
4. Implement B2 as a bounded compiled child graph with a protected parent context and typed return validation. Do not represent ToT/MCTS search branches as LangGraph4j nodes.
5. Use explicit interruption-before boundaries for clarification, external evidence, human control and approval. Resumption requires host-issued references and service-side validation.
6. Require a durable approval record for every state-changing candidate. Validate approval identity, candidate ID, candidate-set reference and intention version, then revalidate the plan immediately before execution.
7. Execute through M3 capability/authorization/dispatch contracts. Route uncertain external outcomes to observation and reconciliation; never retry blindly.
8. Apply observations to working memory with optimistic version control before recording an episodic outcome. Record episodes as unverified until governed validation; do not automatically promote semantic facts or procedural changes.
9. Bound clarification, evidence, planner, reconsideration and recursion counts.

## Alternatives considered

### Put each planner thought/search node in LangGraph4j

Rejected. This would conflate the application control-flow graph with the ToT/MCTS search tree, increase checkpoint volume, and couple planner internals to the runtime framework.

### Use LangChain4j Agentic as a second workflow engine

Rejected. LangGraph4j remains the sole outer workflow orchestrator. LangChain4j stable APIs supply model integration only.

### Put full domain/memory/search state in the graph checkpoint

Rejected. This duplicates authority and persistence responsibilities, inflates checkpoints and confuses orchestration state with CoALA memory and planner-session state.

### Automatically retry unknown external operations

Rejected. A service may have committed an operation before a local crash or timeout. Reconciliation by durable operation/idempotency reference must precede any retry or compensation.

## Consequences

Positive:
- Outer application lifecycle is explicit, cyclic, checkpointable and testable.
- M2–M5 services and algorithms stay independently testable and reusable.
- Human interruption, approval binding, execution and reconciliation have named boundaries.
- B2 has a bounded child lifecycle without turning search trees into graph state.

Trade-offs and remaining work:
- `CognitiveCycleOperations` is a port; its production composition-root binding is M7 work.
- M6 tests use deterministic scripted operations and must pass under Maven Surefire and GitHub Actions before acceptance.
- PostgreSQL checkpoint integration does not by itself prove full process-crash recovery for every application record. M7/M8 must test all stores and operation ledgers together.
- Planner sessions and M2/M4 reference repositories remain in-memory until durable M7 adapters are implemented.
- The initial graph budgets are safety defaults subject to M8 evaluation.
