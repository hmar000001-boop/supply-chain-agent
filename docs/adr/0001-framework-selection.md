# ADR-0001: Java agentic framework selection

- Status: **Provisional selection; compatibility gate pending CI**
- Date: 2026-11-16
- Scope: Part III supply-chain application

## Decision

Use **LangGraph4j 1.8.28 LTS** as the primary agentic workflow/orchestration framework and **LangChain4j 1.23.0 stable core** for model integration and AI Services.

Do not include `langchain4j-agentic` in the reference application's initial architecture. It is an experimental module and would introduce a second orchestration model without a demonstrated requirement. It may be evaluated in a separate non-production spike, but it must not own long-running application workflow state.

## Why LangGraph4j 1.8.28

The application must expose an iterative CoALA decision procedure, conditional routing, cycles, bounded subgraphs, interruption, checkpointing and resumption. LangGraph4j provides these as explicit graph execution capabilities. Its maintainers identify the 1.8.x branch as the maintained LTS line; the 1.9.x line is a newer feature line with experimental additions preparing for 2.0. Version 1.8.28 was released on 2026-11-13 and contains a state-update/checkpoint-resume correctness fix.

For a production-oriented reference application and a book that needs to explain framework best practices, the maintained LTS line is the safer starting point. We will not move to 1.9.x solely for recency. A later upgrade requires a separate ADR, migration tests and proof that persisted executions remain compatible.

## Why LangChain4j stable core

LangChain4j is used for the model-facing boundary: `ChatModel`, AI Services, structured outputs, embeddings and provider adapters as required by later phases. The first integration calls a LangChain4j AI Service from a LangGraph4j node through an application-owned `RequestInterpreter` port. This keeps model integration replaceable and prevents framework types from leaking into the domain/core contracts.

The selected 1.23.0 stable core version is current in the official getting-started documentation as of 2026-11-16. The integration with LangGraph4j 1.8.28 is still a compatibility hypothesis until the clean Maven build and tests pass.

## Why not LangChain4j Agentic as the outer orchestrator

The `langchain4j-agentic` module is explicitly experimental. The application needs one clear owner of long-running workflow transitions, interruption and checkpoints. Adding Agentic as a second orchestrator risks split ownership of control flow and persistence. Stable LangChain4j model-facing APIs are sufficient for the current boundary.

## Alternatives considered

- **LangGraph4j 1.9.9:** newer feature line, but documented as experimental and preparing for 2.0; not selected for the initial production baseline.
- **AgentScope Java 2.0:** a serious alternative with a production-oriented agent harness and permission/subagent features. It is not selected because the book needs an explicit, inspectable graph-based decision-cycle controller aligned with CoALA.
- **Google ADK Java:** promising and rapidly evolving; workflow graph and pause-for-input features are recent. The required durable restart/resume behavior must be proven in the Java SDK before selection.
- **Embabel:** attractive for goal/action planning, but it is not the best match for the explicit graph-centered lifecycle and checkpoint boundary selected for this application.
- **Spring AI alone:** useful for model/provider and Spring integration, but it is complementary rather than the primary long-running cognitive workflow orchestrator.

These alternatives are not being added as dependencies. The application will have one primary workflow engine.

## Architectural rules

1. LangGraph4j controls application execution, not business truth.
2. CoALA memory types are application-owned services/stores; a graph checkpoint is not a substitute for working, episodic, semantic or procedural memory.
3. CoT, ToT and MCTS are separate planning implementations invoked by graph nodes; they are not represented as one graph node per thought/tree search step.
4. LangChain4j model calls return typed/validated outputs; they do not authorize external actions or mutate authoritative domain state.
5. Business invariants, authorization, idempotency, external-effect reconciliation and audit remain application/domain responsibilities.
6. Graph state contains a minimal serializable execution snapshot and references to authoritative records; it does not serialize service objects, providers or secrets.

## Compatibility proof required

The decision becomes accepted only after CI demonstrates:

- Clean Java 21 Maven build with the pinned pair.
- Deterministic LangChain4j AI Service call using a fake `ChatModel`.
- LangGraph4j conditional routing and a bounded graph cycle around that AI Service.
- Interruption/checkpoint/resume using the in-memory saver.
- PostgreSQL checkpoint save, fresh saver/graph reload and resume.
- Application-owned pending-approval persistence is kept separate from graph checkpoint storage. LangGraph4j PostgreSQL V1 does not persist interruption metadata; the production approval record must be authoritative and independently revalidated before a resumed state-changing action.
- Dependency tree inspection for version mediation/conflicts.

## Consequences

- Only LangGraph4j owns application workflow state and lifecycle.
- LangChain4j is an adapter/integration layer.
- Agentic patterns from LangChain4j may inform design but do not appear as a competing runtime.
- The framework version is pinned; upgrade is a deliberate migration, not an automatic dependency refresh.
