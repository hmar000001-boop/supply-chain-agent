# Supply-Chain Disruption Management Agent

**Initial milestone: Phase 0 — Java framework compatibility proof**

This is the beginning of the complete Part III reference application. The target is one production-oriented, request-driven supply-chain disruption management agent. The first milestone deliberately verifies the framework seam before business-domain implementation begins.

## Selected Java stack

| Component | Version / role |
|---|---|
| Java | 21 |
| Maven Wrapper | Maven 3.9.16, Wrapper 3.3.4 |
| LangGraph4j | 1.8.20 LTS (official BOM) — primary graph orchestration |
| LangChain4j | 1.23.0 stable core — model integration and AI Services |
| PostgreSQL | 16.4 CI service for checkpoint integration test |
| JUnit | 5.12.2 |

`langchain4j-agentic` is intentionally **not** included. LangGraph4j is the sole workflow orchestrator. LangChain4j is used through its stable `ChatModel` and AI Services APIs. The project imports the official LangGraph4j 1.8.20 BOM so the core and PostgreSQL saver resolve to the same published release. The compatibility gate verifies this combination before domain features are added.

## Architecture boundary

- `supply-chain-agent-core`: framework-neutral application contracts.
- `supply-chain-agent-runtime`: LangGraph4j graph orchestration and LangChain4j model adapter.
- `supply-chain-agent-app`: composition root and deterministic local smoke application.

The Phase 0 graph demonstrates a conditional route and a bounded, checkpointed human-clarification cycle: the graph pauses before the clarification node, the caller updates the checkpoint with actual user input, and the graph resumes through the LangChain4j interpreter. A separate approval-graph test covers the generic interruption/update/resume contract with `MemorySaver`. A PostgreSQL integration test starts when `-Dphase0.postgres=true`; it creates a checkpoint, pauses, creates a new saver/graph instance, reloads the checkpoint and resumes. LangGraph4j PostgreSQL V1 does not persist interruption metadata, so the eventual application must store pending approval as an independent authoritative business record and revalidate it before resuming any external action.

## Requirements

- JDK 21
- Network access to Maven Central on first wrapper/build run
- Docker / PostgreSQL only for the optional local PostgreSQL integration test

## Build and test

```bash
./mvnw -B -ntp clean verify
```

Run the PostgreSQL checkpoint integration test locally when PostgreSQL is available:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Run the deterministic smoke application after the build:

```bash
bash scripts/run-smoke.sh
```

It uses a fixed `ChatModel` response and does not require an API key or external model provider.

## Current status

The project source and CI workflow are prepared. The authoring container has JDK 21 but does not have Maven installed and cannot resolve Maven Central DNS, so it cannot execute the Maven build here. **Phase 0 is not marked complete until the GitHub Actions workflow passes on a clean checkout.** The first CI run is the executable compatibility proof for the selected artifacts and APIs.

See:

- [`docs/adr/0001-framework-selection.md`](docs/adr/0001-framework-selection.md)
- [`docs/phase0/PHASE0-STATUS.md`](docs/phase0/PHASE0-STATUS.md)
- [`docs/phase0/COMPATIBILITY-GATE.md`](docs/phase0/COMPATIBILITY-GATE.md)
