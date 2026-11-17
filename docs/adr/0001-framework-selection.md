# ADR-0001: Java agentic framework selection

- Status: **Accepted — Phase 0 compatibility gate passed in GitHub Actions on 2026-11-17**
- Original decision date: 2026-11-16
- Dependency alignment corrections: 2026-11-17
- Scope: Part III supply-chain application

## Decision

Use **LangGraph4j 1.8.20 LTS artifacts, managed by the official LangGraph4j BOM**, as the primary agentic workflow/orchestration framework and **LangChain4j 1.23.0 stable core** for model integration and AI Services.

Do not include `langchain4j-agentic` in the reference application's initial architecture. The application has one workflow orchestrator. LangChain4j stable model-facing APIs are sufficient for the current boundary.

## Why LangGraph4j 1.8.20 for the initial Maven baseline

The application must expose an iterative CoALA decision procedure, conditional routing, cycles, bounded subgraphs, interruption, checkpointing and resumption. LangGraph4j provides these as explicit graph execution capabilities. The maintainers identify 1.8.x as the LTS line; 1.9.x is an evolving line with experimental features preparing for 2.0.

The original Phase 0 POM selected 1.8.28 because that release appears on the LangGraph4j GitHub releases page. CI demonstrated that this was not a usable Maven Central dependency set on 2026-11-17: Maven Central could not resolve either `langgraph4j-core:1.8.28` or `langgraph4j-postgres-saver:1.8.28`. The published Maven metadata showed a newer core artifact at 1.8.27, but the PostgreSQL saver 1.8.x artifact was only published through 1.8.20. The official 1.8 getting-started guide and Maven Central both publish the 1.8.20 BOM/core set, and the PostgreSQL saver 1.8.20 artifact is also present.

We therefore select **1.8.20 as the latest common published 1.8.x artifact baseline for the core and PostgreSQL saver**. This is a deliberate compatibility choice, not a claim that 1.8.20 is the newest GitHub tag. It avoids mixing independently released core and saver versions without proof of binary compatibility.

The root POM imports `org.bsc.langgraph4j:langgraph4j-bom:1.8.20`. Direct versions for LangGraph4j modules are intentionally omitted so the BOM controls the module set consistently. A future move to a later LTS artifact set requires confirming that the required core and saver artifacts are actually published, then rerunning the complete compatibility suite.

## SLF4J dependency convergence

The first CI build using the published LangGraph4j 1.8.20 artifacts reached Maven Enforcer and identified a transitive version conflict: LangGraph4j requested `slf4j-api:2.0.9`, while LangChain4j 1.23.0 requested `slf4j-api:2.0.18`. The root POM now imports the official `org.slf4j:slf4j-bom:2.0.20`, the current stable SLF4J 2.0 release as of 2026-11-17. This centrally manages the SLF4J family and resolves the conflicting transitive requests through Maven dependency management. The Enforcer `dependencyConvergence` rule remains enabled. The successful clean CI build passed Maven Enforcer `dependencyConvergence` with this managed graph; the selected versions are pinned pending a deliberate compatibility migration.

## Why LangChain4j stable core

LangChain4j is used for the model-facing boundary: `ChatModel`, AI Services, structured outputs, embeddings and provider adapters as required by later phases. The first integration calls a LangChain4j AI Service from a LangGraph4j node through an application-owned `RequestInterpreter` port. This keeps model integration replaceable and prevents framework types from leaking into the domain/core contracts.

The selected 1.23.0 stable core version is retained. The clean Java 21 Maven build and deterministic AI Service/graph tests passed in GitHub Actions on 2026-11-17. This establishes the tested integration seam; it does not yet prove all later graph subgraph/parallelism, process-restart recovery or production deployment behavior.

## Why not LangChain4j Agentic as the outer orchestrator

The `langchain4j-agentic` module is explicitly experimental. The application needs one clear owner of long-running workflow transitions, interruption and checkpoints. Adding Agentic as a second orchestrator risks split ownership of control flow and persistence.

## Alternatives considered

- **LangGraph4j 1.9.x:** newer feature line, but documented as an evolving line with experimental features preparing for 2.0; not selected for the initial production baseline.
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
7. The LangGraph4j BOM is the sole version authority for LangGraph4j modules; do not override an individual module version without a documented compatibility reason.

## Compatibility proof completed on 2026-11-17

GitHub Actions demonstrated:

- Clean Java 21 Maven build with the published LangGraph4j 1.8.20 BOM/core/PostgreSQL saver set and LangChain4j 1.23.0.
- Deterministic LangChain4j AI Service call using a fake `ChatModel`.
- LangGraph4j conditional routing and a bounded graph cycle around that AI Service.
- Interruption/checkpoint/resume using the in-memory saver.
- PostgreSQL checkpoint save, fresh saver/graph reload and resume.
- Application-owned pending-approval persistence is kept separate from graph checkpoint storage. The selected PostgreSQL saver V1 limitation must be respected: pending approvals remain authoritative application records and are independently revalidated before resuming a state-changing action.
- Dependency tree inspection confirms that the BOM resolves the intended core and PostgreSQL saver to 1.8.20 without duplicate/mismatched LangGraph4j versions.
- Dependency tree inspection confirms that SLF4J modules are consistently managed at 2.0.20 and Maven Enforcer `dependencyConvergence` passes.

## Consequences

- Only LangGraph4j owns application workflow state and lifecycle.
- LangChain4j is an adapter/integration layer.
- Agentic patterns from LangChain4j may inform design but do not appear as a competing runtime.
- The dependency baseline is pinned to published artifacts; upgrade is a deliberate migration, not an automatic dependency refresh.
