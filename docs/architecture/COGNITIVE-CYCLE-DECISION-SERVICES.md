# Cognitive-cycle decision-service composition

This document describes the M6-A composition slice. It does not claim the complete M6 application adapter is finished.

```text
LangGraph4j CognitiveCycleGraph
            |
            v
CognitiveCycleDecisionOperations
            |
            v
CognitiveCycleDecisionServices
     |             |              |
     v             v              v
 M2 retrieval    B3 workspace     M5 planner router
     |             |              |
     v             v              v
 B1 factory +    B3 admission +   planning artifacts
 selector        broadcast       outside graph state
     |             |              |
     +-------------+--------------+
                   |
                   v
       common deterministic evaluation
       + trusted world-model replay
                   |
                   v
       typed CandidateEvaluation to graph
```

## Responsibilities

- **M2 retrieval** performs exact, scope- and time-aware retrieval across working, episodic, semantic and procedural memory.
- **B1** converts the retrieved memory records into typed candidates using a trusted feature provider, then computes activation/accessibility, retrieval latency, downstream utility and maintenance-cost-aware admission.
- **B3** admits compatible content under an explicit capacity/coverage budget and broadcasts only an admitted snapshot to configured consumers. The graph advances only after all consumers acknowledge it.
- **M5** receives a complete `PlanningRequest` and runs the independently implemented strategy selected by `PlannerRouter`.
- **Candidate evaluation** replays proposed action paths against the trusted `PlanningWorldModel` and runs the shared `CandidateEvaluator`. Planner-provided evaluation is not treated as authorization or an authoritative feasibility result.
- **Planning artifacts** retain the complete request and result behind a candidate-set reference. Only that reference is carried through graph state.

## Domain adapter boundary

`CognitiveCycleDecisionPort` is a translation/persistence boundary. It supplies typed inputs created from authoritative supply-chain records and persists the evidence-set and broadcast-receipt references. Before M5 dispatch, the decision service requires both the persisted evidence-set reference and the admitted workspace snapshot, then checks the planning request's working-memory ID, version and tenant scope against the active graph context. Candidate-set evaluation also checks the graph execution, memory identity/version/scope, planner ID and planner-session ID. The port must not replace M2/B1/B3/M5 algorithms with host-side heuristics.

## Persistence boundary

`InMemoryPlanningArtifactStore` is a thread-safe reference implementation for M6 integration and tests. It is process-local and not restart durable. M7 must provide a durable implementation with unique candidate-set identity, execution ownership checks, immutable request/result storage, and transactional creation semantics.

## M6-B continuation

M6-B completes the rest of `CognitiveCycleOperations`, adds the concrete application composition for B2/B4/B6 and M3 execution/authorization/reconciliation, and validates a full LangGraph4j run against the real service composition. It must not move ToT/MCTS search-tree internals into graph state or treat graph checkpoints as CoALA memory.
