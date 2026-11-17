# Supply-Chain Disruption Management Agent

**Current milestone: M2 — complete CoALA memory architecture**

This is the reference application for Part III of *From Cognitive Architectures to Production Agentic Systems*. The scope is one request-driven enterprise supply-chain disruption management agent. The complete production-oriented Java application will be developed, tested and frozen before Chapters 19 and 20 are written. The chapters will explain selected architecture and implementation blocks and link to the complete source repository; they will not reproduce every source file.

## Selected Java stack

| Component | Version / role |
|---|---|
| Java | 21 |
| Maven Wrapper | Maven 3.9.16, Wrapper 3.3.4 |
| LangGraph4j | 1.8.20 LTS (official BOM) — primary graph orchestration |
| LangChain4j | 1.23.0 stable core — model integration and AI Services |
| SLF4J | 2.0.20 BOM — transitive logging dependency alignment |
| PostgreSQL | 16.4 CI service for checkpoint integration tests |
| JUnit | 5.12.2 |

`langchain4j-agentic` is intentionally **not** included. LangGraph4j is the sole workflow orchestrator. LangChain4j stable core is used for model-facing integration. Maven Enforcer dependency convergence remains enabled.

## Architecture and dependency boundaries

- `supply-chain-agent-domain`: framework-independent business entities, value objects, cross-aggregate invariants and immutable supply-chain snapshots.
- `supply-chain-agent-memory`: framework-neutral CoALA working, episodic, semantic and procedural memory models, lifecycle services, repository ports and exact cross-memory retrieval.
- `supply-chain-agent-core`: framework-neutral application contracts; it does not own memory implementations.
- `supply-chain-agent-simulator`: deterministic, side-effect-free transitions over domain snapshots; depends only on the domain module.
- `supply-chain-agent-runtime`: LangGraph4j orchestration and LangChain4j adapters.
- `supply-chain-agent-app`: composition root.

The domain must not depend on LangGraph4j, LangChain4j, PostgreSQL or provider classes. The simulator performs no network or database writes and does not authorize real operational actions. Graph state and graph checkpoints are not substitutes for CoALA's separate memory stores or the application's authoritative business records.

## M0 — framework compatibility: completed 2026-11-17

GitHub Actions passed the clean Java 21 Maven build, dependency convergence and all seven graph/checkpoint compatibility tests, including a PostgreSQL checkpoint reload through a newly created saver/graph instance. This verifies the initial LangGraph4j 1.8.20 and LangChain4j 1.23.0 integration seam.

M0 does not prove OS-process kill/restart recovery, persistence of all interruption metadata in PostgreSQL Saver V1, parallel graph execution or every compiled-subgraph behavior. Pending approvals must remain separate durable application records and must be revalidated before external actions. Those are later production acceptance requirements.

## M1 — domain foundation and deterministic world simulator

M1 introduces:

- Products and approved, time-bounded product-substitution rules.
- Suppliers, product qualifications, planning-horizon capacity and lead times.
- Inventory locations, lots, quality holds and reservations.
- Customer orders, order lines and demand changes.
- Purchase orders, receipt quantities and currency-safe unit prices.
- Carriers, routes, shipments and in-flight shipment commitments.
- Disruption records and typed recovery objectives/candidates.
- Unit-aware `BigDecimal` quantities and currency-aware money values.
- Immutable versioned snapshots with cross-reference and allocation invariants.
- Pure transitions for supplier capacity reduction, purchase-order placement, quality holds, shipment delays, inventory reservation/release, order-demand change and disruption recording.

The initial supplier-capacity model represents one planning horizon per supplier/product capability. Time-phased capacity buckets, carrier throughput, richer temporal constraints and external execution adapters are intentionally deferred until later implementation phases require them. The simulator is a deterministic world model, not an ERP, authorization service or transaction manager.

See [`docs/architecture/M1-DOMAIN-MODEL.md`](docs/architecture/M1-DOMAIN-MODEL.md) and [`docs/phase1/M1-STATUS.md`](docs/phase1/M1-STATUS.md). M1 passed the full GitHub Actions CI gate on 2026-11-17.

## M2 — CoALA memory architecture

M2 introduces four distinct memory responsibilities:

- **Working memory:** immutable, versioned active context with typed slots, per-slot evidence and provisional hypotheses.
- **Episodic memory:** structured experience and outcomes; only explicitly verified episodes are retrievable as trusted experience.
- **Semantic memory:** typed, provenance-controlled facts with separate valid-time and knowledge-time queries.
- **Procedural memory:** immutable artifact references and append-only procedure versions with explicit validation and activation.

The memory module is framework-neutral. `MemoryRetrievalService` provides exact deterministic retrieval and does not implement B1 activation/ranking; B1 is a later mechanism. Graph state/checkpoints are not CoALA memory. In-memory adapters support local tests only. An initial PostgreSQL schema contract is included, but migration execution and durable PostgreSQL memory adapters are deferred to M7.

See [`docs/architecture/COALA-MEMORY-ARCHITECTURE.md`](docs/architecture/COALA-MEMORY-ARCHITECTURE.md), [`docs/adr/0002-coala-memory-boundaries.md`](docs/adr/0002-coala-memory-boundaries.md) and [`docs/phase2/M2-STATUS.md`](docs/phase2/M2-STATUS.md).

## Build and test

Basic build and all deterministic tests:

```bash
./mvnw -B -ntp clean verify
```

Run the PostgreSQL checkpoint integration test when PostgreSQL is available:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Run the deterministic local framework smoke application:

```bash
bash scripts/run-smoke.sh
```

It uses a fixed `ChatModel` response and does not require provider credentials.

## Requirements

- JDK 21
- Network access to Maven Central on first dependency-resolution run
- Docker/PostgreSQL only for the PostgreSQL integration test

## Development sequence

M0 framework compatibility and M1 domain/simulator are accepted. M2 implements CoALA memory and is awaiting CI. Subsequent milestones implement internal/external action systems, Fan & Lan B1/B2/B3/B4/B6 mechanisms, independent CoT/ToT/RAP-inspired MCTS planners, the complete LangGraph4j cognitive lifecycle, production persistence/governance, and full integration/evaluation. Chapters 19 and 20 are written only after the complete application passes its acceptance gate.
