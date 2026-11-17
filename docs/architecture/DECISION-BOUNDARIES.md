# Runtime and decision boundaries

The project separates four levels that interact but must not be conflated:

- **CoALA** defines the agent's information, action and decision-procedure responsibilities.
- **Fan & Lan mechanisms** are application-owned capabilities (B1/B2/B3/B4/B6); B5/GraSP remains a reviewed closed-candidate/comparison boundary.
- **CoT, ToT and RAP-inspired MCTS** are separate, independently testable decision/planning algorithms. LangGraph4j invokes them; it does not implement their internal reasoning/search trees.
- **LangGraph4j** owns outer orchestration, conditional routing, cycles, bounded subgraphs and checkpointed execution lifecycle. **LangChain4j stable core** supplies model/provider and structured-output integration.

The domain and simulator are intentionally framework-independent:

- `supply-chain-agent-domain` owns business identities, value objects, entities, cross-reference invariants and immutable snapshots.
- `supply-chain-agent-simulator` owns deterministic, side-effect-free transitions over those snapshots.
- `supply-chain-agent-core` owns framework-neutral application ports.
- `supply-chain-agent-runtime` adapts those ports to LangGraph4j/LangChain4j.
- `supply-chain-agent-app` is the composition root.

A graph node named `Plan` is not ToT or MCTS. It calls a planner service, which owns its search state and returns a typed outcome. Graph state is not the complete CoALA working memory. A graph checkpoint is not episodic memory or an approval ledger. Business invariants, authorization, execution, idempotency and reconciliation remain deterministic application/domain responsibilities.

Phase 0 verifies the initial framework seam. M1 adds the supply-chain business world and deterministic simulator. The complete CoALA memory architecture, internal/external action system, Fan & Lan mechanisms, planning engines and full decision-cycle graph are implemented in subsequent milestones; the current intake graph must not be described as the complete agent.
