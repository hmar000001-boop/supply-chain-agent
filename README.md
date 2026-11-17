# Supply-Chain Disruption Management Agent

**Current milestone: M6 — LangGraph4j cognitive orchestration (implementation package delivered; CI pending)**

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
- `supply-chain-agent-actions`: framework-neutral CoALA internal actions, read-only perception, simulator-backed grounding, capability/resource authorization, typed results and idempotency contracts.
- `supply-chain-agent-mechanisms`: framework-neutral engineering realizations of Fan & Lan B1/B2/B3/B4/B6, with explicit S/C/T/P/F/L/R contracts; no agent-framework or database dependency.
- `supply-chain-agent-planning`: independent CoT-style sequential, ToT-inspired bounded search, and RAP-inspired domain-grounded MCTS strategies behind one planner contract; shared deterministic evaluator, explainable router, B6 budget integration and in-memory resumable planner sessions.
- `supply-chain-agent-runtime`: LangGraph4j orchestration and LangChain4j model adapters; this is the only module that depends on model/framework APIs.
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

## M3 — CoALA internal and external actions (accepted by GitHub Actions CI; user reported green)

M3 adds the `supply-chain-agent-actions` module. It deliberately separates:

- **Internal actions:** deterministic cross-memory retrieval; scoped/versioned working-memory writes; append-only episode recording that cannot self-verify; and bounded structured reasoning through an injected `ReasoningProvider`.
- **Perception:** read-only, versioned observations of suppliers, inventory lots and availability, customer orders, shipments and lot-level quality disposition. Snapshot observations carry evidence and source provenance.
- **Grounding:** capability-authorized inventory reservation/release, purchase-order placement and shipment rerouting over the M1 deterministic simulator.

All actions use typed request/result contracts. The dispatcher checks action/input/output types, trusted capability plus resource scope, idempotency state and preconditions before execution. Known domain and memory failures have typed outcomes. Unexpected failures during state-changing execution are marked `UNKNOWN_OUTCOME` and must be reconciled rather than blindly retried. Duplicate requests are suppressed by an operation ledger; the delivered in-memory ledger is a deterministic test/local adapter, not the durable M7 implementation.

`LangChain4jReasoningProvider` is an adapter in the runtime module. Its AI Service returns a typed assessment, has no tools, and cannot authorize or execute business actions. The current AI Service uses typed POJO parsing; provider-specific JSON Schema enforcement is not assumed unless the configured `ChatModel` supports and explicitly enables it. The adapter validates evidence citations against only the evidence references supplied by the caller. See the official [LangChain4j structured-output documentation](https://docs.langchain4j.dev/tutorials/structured-outputs/) and [AI Services documentation](https://docs.langchain4j.dev/tutorials/ai-services/).

M3's perception and grounding adapters are reference implementations over immutable snapshots and an in-memory simulator. They do not connect to a real ERP, WMS, supplier, carrier or procurement system. The SQL-backed durable operation ledger, production external adapters, outbox/inbox, and durable memory repositories are deferred to M7. M3 exposes deterministic cross-memory lookup from M2; M4 adds B1 activation/accessibility, latency, downstream-utility and maintenance-aware selection on top of those contracts.

See [`docs/architecture/COALA-ACTION-ARCHITECTURE.md`](docs/architecture/COALA-ACTION-ARCHITECTURE.md), [`docs/adr/0003-coala-action-and-authority-boundaries.md`](docs/adr/0003-coala-action-and-authority-boundaries.md), and [`docs/phase3/M3-STATUS.md`](docs/phase3/M3-STATUS.md).

## M4 — Fan & Lan mechanism realizations (accepted by GitHub Actions CI; user reported green)

M4 adds the framework-neutral `supply-chain-agent-mechanisms` module and implements the applicable residual bundles as independently testable capabilities:

- **B1:** M2 retrieval-result adapter for all four memory types; configurable activation/accessibility estimate; separate predicted cognitive and measured infrastructure latency; downstream utility, context cost and maintenance accounting.
- **B2:** typed impasse consolidation and bounded child-resolution cases, with parent version/objective protection, read/reasoning-only child capabilities, deadlines and action budgets. Child results contain evidence-backed findings only; parent authority is preserved.
- **B3:** mandatory/optional workspace admission, utility/reliability/risk and marginal coverage scoring, symmetric conflicts, explicit coverage coalitions, immutable snapshots and scoped synchronous consumer receipts.
- **B4:** versioned recovery intentions, protected objective fingerprint/constraints, reconsideration-gated method changes, suspension/resumption, bounded delegation and evidence-bearing completion.
- **B6:** explicit evidence/hypothesis/plan uncertainty, computational resource reservations/consumption, per-next-step budget checks, deadlines, reduced computation, human/budget interruption and safe stopping.

The scoring models are engineering realizations and are not claimed to be paper-exact equations. B5/GraSP remains the previously reviewed closed-candidate boundary; it is not implemented as a new residual mechanism. The mechanism module does not implement CoT/ToT/MCTS (M5) or LangGraph4j graph orchestration (M6). Its repositories/ledgers are in-memory reference adapters; M7 supplies durable transactional implementations.

See [`docs/architecture/FAN_LAN-MECHANISM-ARCHITECTURE.md`](docs/architecture/FAN_LAN-MECHANISM-ARCHITECTURE.md), [`docs/adr/0004-fan-lan-mechanism-realizations.md`](docs/adr/0004-fan-lan-mechanism-realizations.md), and [`docs/phase4/M4-STATUS.md`](docs/phase4/M4-STATUS.md).

## M5 — independent planning strategies (accepted by GitHub Actions CI; green reported by the user on 2026-11-17)

M5 adds `supply-chain-agent-planning`. It keeps the planning algorithms separate from the LangGraph4j execution graph: CoT-style sequential reasoning is a structured one-path baseline; ToT-inspired search explores/prunes structured partial action paths; and RAP-inspired MCTS uses UCT-style selection, expansion, deterministic simulator-backed rollouts and reward backpropagation. All strategies return proposal-only candidates through one common evaluator that replays actions against the trusted M1 simulator and enforces hard constraints independently of weighted scores. An explainable router chooses a strategy from initial complexity/budget heuristics, and M4 B6 resource accounting tracks compute usage. Request fingerprints include semantic world/memory content and algorithm configuration so changed inputs cannot silently resume an old search.

The names are intentionally qualified: ToT is an inspired structured action-path search, not a literal textual-thought reproduction; MCTS is RAP-inspired with a deterministic supply-chain world model, not faithful RAP. The default routing thresholds require M8 evaluation. Planner sessions currently use an in-memory reference store; durable transactional persistence and process-restart recovery are M7. LangGraph4j orchestration and LangChain4j provider wiring remain M6. No planner can authorize or execute an action.

See [`docs/architecture/PLANNING-ARCHITECTURE.md`](docs/architecture/PLANNING-ARCHITECTURE.md), [`docs/adr/0005-independent-planning-strategies.md`](docs/adr/0005-independent-planning-strategies.md), [`docs/phase5/M5-STATUS.md`](docs/phase5/M5-STATUS.md), and [`docs/phase5/M5-TRACEABILITY.md`](docs/phase5/M5-TRACEABILITY.md). The supplied authoring report records 208/208 local framework-neutral tests; the user subsequently confirmed that GitHub Actions CI is green. The CI run log was not part of the uploaded archive.

## M6 — LangGraph4j cognitive orchestration (implementation delivered; CI pending)

M6 adds the executable outer cognitive-cycle controller in `supply-chain-agent-runtime`. `CognitiveCycleGraph` connects request interpretation, CoALA context loading and cross-memory retrieval, B1/B3 evidence selection and workspace admission, M5 planner dispatch and common candidate evaluation, B2 bounded impasse resolution, B4 intention governance, B6 meta-control, capability/approval checks, execution, observation/reconciliation and learning. `CognitiveCycleOperations` is the application-owned integration boundary through which the composition root delegates to M2/M3/M4/M5 services and repositories without moving their logic into graph nodes. The graph stores only scalar routing values and stable record references, not the memory corpus, candidate payloads, domain snapshots or search trees.

Four explicit `interruptBefore` boundaries cover clarification, external evidence, human meta-control and approval. Resumption requires host-supplied references; identity and exact candidate/intent binding are rechecked. A state-changing candidate cannot use the approval-free branch. An uncertain external execution outcome is always observed/reconciled and is never blindly retried. Observations are written back to working memory with optimistic version handling; on conflict the graph reloads the latest version and retries the already-persisted observation, never re-executing the external action. B2 runs as a bounded child graph while retaining parent objective/version control.

M6 includes 14 graph-level tests covering normal read-only execution, approval and denial, external-evidence interruption, B2, B4/B6 human interruption, identity/binding mismatches, unknown external outcomes, working-memory conflicts and stop behavior. These tests are added but have not yet been run under Maven/JUnit Platform in this authoring environment. The current archive does not include a concrete production composition-root implementation for `CognitiveCycleOperations`; M6 defines and tests the orchestration contract. Concrete M2/M3/M4/M5 service binding and real-service integration tests remain an explicit M6 acceptance gate. Durable approval/operation/memory stores and crash/restart reconciliation are M7/M8 work. See [`docs/architecture/COGNITIVE-CYCLE-ORCHESTRATION.md`](docs/architecture/COGNITIVE-CYCLE-ORCHESTRATION.md), [`docs/adr/0006-cognitive-cycle-graph-orchestration.md`](docs/adr/0006-cognitive-cycle-graph-orchestration.md), and [`docs/phase6/M6-STATUS.md`](docs/phase6/M6-STATUS.md) plus [`docs/phase6/M6-TEST-RESULTS.md`](docs/phase6/M6-TEST-RESULTS.md).

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

M0 framework compatibility, M1 domain/simulator, M2 CoALA memory, M3 internal/external actions, M4 Fan & Lan mechanisms, and M5 independent CoT/ToT/RAP-inspired MCTS planners are accepted by GitHub Actions CI (M5 green status reported by the user on 2026-11-17). M6 LangGraph4j graph orchestration and its scripted graph tests are implemented but await CI and the concrete service-composition/integration gate. M7 adds durable production persistence/governance; M8 performs full integration/evaluation and freezes the application. Chapters 19 and 20 are written only after the complete application passes its acceptance gate.
