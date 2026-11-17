# Runtime and decision boundaries

Phase 0 establishes the framework seam used by the full application.

- **CoALA** defines information, action and decision-procedure responsibilities.
- **Fan & Lan mechanisms** will be implemented as application-owned, testable components in later increments.
- **CoT, ToT and MCTS** will be separate `PlanningStrategy` implementations with shared request/result contracts. LangGraph4j will invoke and route planner calls; its graph is not the ToT tree or MCTS search tree.
- **LangGraph4j** owns outer orchestration, conditional routing, cycles, subgraphs and checkpointed execution lifecycle.
- **LangChain4j** provides model-facing AI Services and model/provider adapters.
- **Domain/application services** own business truth, invariants, authorization, execution, idempotency and reconciliation.

The first Phase 0 graph only verifies request interpretation and a bounded clarification cycle. It is not a supply-chain business workflow and does not claim to implement CoALA or Fan & Lan in full.
