# ADR 0004 — Implement Fan & Lan mechanisms as framework-neutral capabilities

- **Status:** Accepted for M4 implementation; integration and durable persistence remain later milestones.
- **Date:** 2026-11-17

## Context

Part III must demonstrate selected Fan & Lan mechanisms in a complete Java application while preserving the separation between CoALA, mechanism implementations, decision algorithms and execution framework. M2 supplies framework-neutral CoALA memory contracts; M3 supplies framework-neutral internal/external action contracts. The mechanisms must remain independently testable and must not be equated with LangGraph4j nodes or graph state.

## Decision

Add `supply-chain-agent-mechanisms` as a framework-neutral Maven module with explicit implementations for B1, B2, B3, B4 and B6. Depend on the domain, memory and action contracts. Do not add LangGraph4j, LangChain4j, JDBC or PostgreSQL dependencies to this module. Do not implement B5/GraSP as a new residual mechanism.

Each mechanism is specified and evaluated using the seven fields S/C/T/P/F/L/R:

- operative state;
- control authority;
- trigger;
- persistence;
- failure behavior;
- learning behavior;
- resource/governance behavior.

## Consequences

### Positive

- Mechanism logic can be tested without model providers or graph runtime dependencies.
- CoALA memory and actions remain distinct from mechanism-specific control.
- M6 can compose the mechanisms into LangGraph4j without moving domain decisions into graph definitions.
- M7 can replace reference in-memory repositories with durable adapters while preserving mechanism contracts.
- Engineering adaptations and paper-specified claims can be distinguished explicitly.

### Trade-offs and limitations

- Current repositories and ledgers are in-memory reference adapters; they are not production persistence.
- B1's activation and utility model is configurable and approximate, not asserted as a paper-exact equation.
- B2 returns evidence-backed findings but does not mutate the parent or execute actions.
- B3 broadcast is synchronous and does not yet guarantee durable delivery.
- B4 lifecycle methods enforce objective/method invariants, but a durable authorization service must be integrated at the application boundary.
- B6 budgets cover computational/runtime units, not business resources.
- M4 does not implement planning algorithms or graph orchestration.

## Validation

M4 adds 70 mechanism test methods. The combined local non-runtime regression harness covers 172 methods across domain, memory, simulator, action and mechanism modules. GitHub Actions Maven/Surefire remains the authoritative acceptance gate for the complete reactor and existing M0 runtime tests.
