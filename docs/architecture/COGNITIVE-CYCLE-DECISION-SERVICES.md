# Cognitive-cycle decision-service composition

This document describes the current full cognitive-cycle decision-service composition, including the B4 method-to-planner binding and B6 per-call planner-budget downshift added in the pre-M8-B7 integration increment. It does not claim that the Fan & Lan residual mechanisms are complete in every respect; B1/B2/B3 learning and composition gaps remain separately documented.

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
- **B4 → M5** carries the persisted intention's current method through graph state. `PlannerMethodResolver` maps it to a registered M5 strategy, and `PlannerSelectionOverride` binds the method to the intention version and immutable objective fingerprint. `PlannerRouter` applies this authoritative selection before generic complexity routing.
- **B6 → M5** turns `REDUCE_COMPUTATION` into a typed, cumulative downshift of the next request's consumable `PlannerBudget` limits. The shared execution-wide resource ceiling is not changed; M5's next reservation is based on the reduced per-call envelope.
- **M5** receives a complete `PlanningRequest` and runs the independently implemented strategy selected by `PlannerRouter`.
- **Candidate evaluation** replays proposed action paths against the trusted `PlanningWorldModel` and runs the shared `CandidateEvaluator`. Planner-provided evaluation is not treated as authorization or an authoritative feasibility result.
- **Planning artifacts** retain the complete request and result behind a candidate-set reference. Only that reference is carried through graph state.

## Domain adapter boundary

`CognitiveCycleDecisionPort` is a translation/persistence boundary. It supplies typed inputs created from authoritative supply-chain records and persists the evidence-set and broadcast-receipt references. Before M5 dispatch, the decision service requires both the persisted evidence-set reference and the admitted workspace snapshot, then checks the planning request's working-memory ID, version and tenant scope against the active graph context. Candidate-set evaluation also checks the graph execution, memory identity/version/scope, planner ID and planner-session ID. The port must not replace M2/B1/B3/M5 algorithms with host-side heuristics.

## B4 and B6 control bindings

The B4 selection override is reconstructed from the intention returned by the lifecycle service on each context load or governance transition. The decision service does not trust a planner proposal to select its own method. Unknown method IDs fail closed unless the host injects a `PlannerMethodResolver`. The override and objective fingerprint are retained in the stored planning request so the strategy choice is auditable and a different intention method cannot reuse the previous planner session. In addition, context reload and governance transitions clear the graph's previous planner-session reference whenever the intention identity, version, method reference or protected objective fingerprint changes. A B6-only budget downshift leaves the B4 binding unchanged.

A B6 downshift is applied before `PlannerRouter.plan`. It reduces only the local planner compute envelope; the shared execution resource account remains unchanged. The reservation idempotency key includes the graph planning round and the complete effective envelope because planning-session fingerprints intentionally omit consumable resource limits. This gives checkpoint retries stable identities without preventing a later reduced-budget call from acquiring a distinct reservation.

## Persistence boundary

`InMemoryPlanningArtifactStore` is a thread-safe reference implementation used by evaluation fixtures; it is process-local and not restart durable. The infrastructure module provides `PostgresPlanningArtifactStore` and `PostgresPlannerSessionStore` through `PostgresRuntimePersistence`. The application composition must select those durable adapters when process-restart recovery is required; merely having the adapters in the repository does not mean every evaluation host uses them.

## Remaining boundaries

The B4/B6 control bindings are implemented and covered by planner/service/full-graph tests. They do not close the separate B2 approved-draft-to-procedural-memory promotion gap or B3 competing-coalition learning gap. The runtime does not move ToT/MCTS search-tree internals into graph state or treat graph checkpoints as CoALA working memory.
