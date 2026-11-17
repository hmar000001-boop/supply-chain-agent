# Phase 0 status — 2026-11-16

## Source prepared

- Java 21 Maven reactor with three modules: framework-neutral core contracts, agent runtime and application host.
- LangGraph4j 1.8.28 LTS selected as sole graph/workflow orchestrator.
- LangChain4j 1.23.0 stable core selected for model-facing AI Services.
- Experimental `langchain4j-agentic` excluded from the reference application.
- Deterministic fake `ChatModel` used in tests; no live model credentials required.
- LangGraph4j graph includes conditional routing and a bounded, checkpointed human-clarification interrupt/resume cycle. The test supplies actual clarification text through `updateState()` before resuming.
- In-memory checkpoint interrupt/update/resume test added.
- PostgreSQL saver integration test added for a fresh-saver checkpoint reload/resume path.
- Recorded the LangGraph4j PostgreSQL V1 limitation: interruption metadata is not persisted by V1. The production application must store pending approval state separately as authoritative business data and must not rely on a graph checkpoint as an approval ledger.
- GitHub Actions workflow uses Java 21 and PostgreSQL 16.4.
- Framework selection ADR and compatibility gate recorded.

## Environment observed

- OpenJDK 21.0.12.1 is available.
- Maven is not installed globally; the Maven Wrapper is pinned to Maven 3.9.16 and Wrapper 3.3.4.
- Maven Central DNS resolution fails in this authoring container, so the wrapper cannot download its bootstrap JAR or Maven distribution.

## Execution status

**Not build-verified. Phase 0 is not complete.** POM structure and Java source have been authored, but no successful Maven compilation or test run is claimed from this container. The PostgreSQL compatibility test is specifically intended to discover whether the V1 checkpoint sequence resumes across a fresh saver/graph instance; it does not prove durable interruption metadata, which V1 does not implement. GitHub Actions is the required clean-checkout execution environment.

## Next action

Upload this project ZIP to its GitHub repository and run the `Java CI` workflow. Use the Surefire reports and dependency-tree artifact to resolve any actual API or dependency incompatibility before starting the supply-chain domain.
