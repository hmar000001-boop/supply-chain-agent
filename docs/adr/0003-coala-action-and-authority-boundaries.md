# ADR-0003: CoALA action contracts, authorization and effect boundaries

- **Status:** Accepted for M3 implementation; production adapters remain deferred.
- **Date:** 2026-11-17

## Context

CoALA requires explicit internal and external actions. The supply-chain agent must use memory/reasoning operations and perceive/ground against its environment without allowing an LLM to access databases or invoke arbitrary side effects. Actions need typed contracts, evidence/provenance, capability and resource scope, preconditions, failure classification and idempotency.

## Decision

1. Add a framework-neutral `supply-chain-agent-actions` Maven module.
2. Partition action handlers into `INTERNAL`, `PERCEPTION` and `GROUNDING` categories.
3. Keep LangChain4j integration in `supply-chain-agent-runtime`; expose it through the application-owned `ReasoningProvider` port.
4. Do not expose LangChain4j tools or business capabilities to the reasoning AI Service.
5. Require trusted, explicit capability grants. Scoped grants cover every resource named by the handler.
6. Keep perception read-only and make observations carry source/evidence provenance plus snapshot version.
7. Require idempotency for every memory/domain write. Use read-only duplicate inspection, precondition evaluation and a subsequent atomic claim to suppress replays while preserving safe retry after a rejected precondition.
8. Classify uncertain outcomes of state-changing handlers as `UNKNOWN_OUTCOME`; do not blindly retry them.
9. Use the M1 simulator as the only M3 grounding adapter and label it test/reference-only. Real ERP/WMS/carrier adapters are deferred.
10. Keep M3 memory retrieval as M2 deterministic lookup; B1 activation/ranking remains M4.

## Consequences

- CoALA actions are independently testable without LangGraph4j or LangChain4j.
- The graph runtime will later orchestrate action dispatch but will not own business authorization or transition invariants.
- The current in-memory operation ledger and simulator world are not production durability guarantees. M7 must supply transactional persistent adapters and reconciliation.
- Structured POJO parsing is supported by LangChain4j AI Services. JSON Schema is only guaranteed when the configured provider/model supports it and explicitly enables that capability; provider-independent strict schema behavior is not claimed.
- Human approval is not a capability inferred from model output. It remains a separate durable, plan-bound authorization workflow for M6/M7.

## Alternatives rejected

- **Direct LLM tool execution:** rejected because it would mix model reasoning with authority, persistence and side-effect control.
- **Framework types in domain/action contracts:** rejected because it would couple cognitive/business services to an orchestration runtime.
- **Treating the LangGraph4j state map as CoALA memory:** rejected because working, episodic, semantic and procedural memory have separate semantics and lifecycles.
- **Calling the simulator an external system adapter:** rejected because it only applies deterministic transitions to an in-memory modeled snapshot.
