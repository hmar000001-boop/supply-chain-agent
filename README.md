
## Current milestone status — M8 (2026-11-19)

M6 and M7 are accepted. The user confirmed that the complete Java 21/Maven/PostgreSQL GitHub Actions workflow is green after the final M7-B2 timestamp and batch-contract corrections. M7 is frozen and closed; no M7-B3 or further persistence phase is planned. M8-B0 froze the exact M7-accepted source baseline. M8-B1 adds the versioned evaluation corpus, deterministic planner-comparison harness, evaluation-only greedy comparator and a fail-closed full-graph composition probe; the user confirmed its CI is green. M8-B2 adds matched comparative analysis and the user confirmed its CI is green. M8-B3 adds mechanism ablations and a results clarification; its CI verification is pending.

# Supply-Chain Disruption Management Agent

**Current milestone: M8 — Evaluation and application freeze (M7 accepted and frozen; M8-B0 complete; M8-B1 and M8-B2 accepted; M8-B3 implementation/experiments delivered, CI pending)**

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
- `supply-chain-agent-infrastructure-postgres`: PostgreSQL migrations, durable JDBC repositories and database configuration; depends inward on memory contracts, never the reverse.
- `supply-chain-agent-app`: application composition root and complete cognitive-cycle graph assembly.
- `supply-chain-agent-evaluation`: versioned M8 scenario catalog, deterministic simulator-backed fixtures, planner comparators, full-graph composition probe and JSONL experiment output.

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

The memory module is framework-neutral. `MemoryRetrievalService` provides exact deterministic retrieval and does not implement B1 activation/ranking; B1 is a separate mechanism. Graph state/checkpoints are not CoALA memory. In-memory adapters remain useful for deterministic tests. Accepted M7 supplies PostgreSQL repositories for all four CoALA memory types, planner sessions and the durable application runtime records described in `docs/phase7/M7-STATUS.md`. M7 is frozen; M8 validates application behavior without reopening that infrastructure.

See [`docs/architecture/COALA-MEMORY-ARCHITECTURE.md`](docs/architecture/COALA-MEMORY-ARCHITECTURE.md), [`docs/adr/0002-coala-memory-boundaries.md`](docs/adr/0002-coala-memory-boundaries.md) and [`docs/phase2/M2-STATUS.md`](docs/phase2/M2-STATUS.md).

## M3 — CoALA internal and external actions (accepted by GitHub Actions CI; user reported green)

M3 adds the `supply-chain-agent-actions` module. It deliberately separates:

- **Internal actions:** deterministic cross-memory retrieval; scoped/versioned working-memory writes; append-only episode recording that cannot self-verify; and bounded structured reasoning through an injected `ReasoningProvider`.
- **Perception:** read-only, versioned observations of suppliers, inventory lots and availability, customer orders, shipments and lot-level quality disposition. Snapshot observations carry evidence and source provenance.
- **Grounding:** capability-authorized inventory reservation/release, purchase-order placement and shipment rerouting over the M1 deterministic simulator.

All actions use typed request/result contracts. The dispatcher checks action/input/output types, trusted capability plus resource scope, idempotency state and preconditions before execution. Known domain and memory failures have typed outcomes. Unexpected failures during state-changing execution are marked `UNKNOWN_OUTCOME` and must be reconciled rather than blindly retried. Duplicate requests are suppressed by an operation ledger; the delivered in-memory ledger is a deterministic test/local adapter, not the durable M7 implementation.

`LangChain4jReasoningProvider` is an adapter in the runtime module. Its AI Service returns a typed assessment, has no tools, and cannot authorize or execute business actions. The current AI Service uses typed POJO parsing; provider-specific JSON Schema enforcement is not assumed unless the configured `ChatModel` supports and explicitly enables it. The adapter validates evidence citations against only the evidence references supplied by the caller. See the official [LangChain4j structured-output documentation](https://docs.langchain4j.dev/tutorials/structured-outputs/) and [AI Services documentation](https://docs.langchain4j.dev/tutorials/ai-services/).

M3's perception and grounding adapters are reference implementations over immutable snapshots and an in-memory simulator. They do not connect to a real ERP, WMS, supplier, carrier or procurement system. Accepted M7 adds the SQL-backed operation ledger, transactional outbox/inbox and durable runtime stores, but it does not invent production external adapters. M3 exposes deterministic cross-memory lookup from M2; M4 adds B1 activation/accessibility, latency, downstream-utility and maintenance-aware selection on top of those contracts.

See [`docs/architecture/COALA-ACTION-ARCHITECTURE.md`](docs/architecture/COALA-ACTION-ARCHITECTURE.md), [`docs/adr/0003-coala-action-and-authority-boundaries.md`](docs/adr/0003-coala-action-and-authority-boundaries.md), and [`docs/phase3/M3-STATUS.md`](docs/phase3/M3-STATUS.md).

## M4 — Fan & Lan mechanism realizations (accepted by GitHub Actions CI; user reported green)

M4 adds the framework-neutral `supply-chain-agent-mechanisms` module and implements the applicable residual bundles as independently testable capabilities:

- **B1:** M2 retrieval-result adapter for all four memory types; configurable activation/accessibility estimate; separate predicted cognitive and measured infrastructure latency; downstream utility, context cost and maintenance accounting.
- **B2:** typed impasse consolidation and bounded child-resolution cases, with parent version/objective protection, read/reasoning-only child capabilities, deadlines and action budgets. Child results contain evidence-backed findings only; parent authority is preserved.
- **B3:** mandatory/optional workspace admission, utility/reliability/risk and marginal coverage scoring, symmetric conflicts, explicit coverage coalitions, immutable snapshots and scoped synchronous consumer receipts.
- **B4:** versioned recovery intentions, protected objective fingerprint/constraints, reconsideration-gated method changes, suspension/resumption, bounded delegation and evidence-bearing completion.
- **B6:** explicit evidence/hypothesis/plan uncertainty, computational resource reservations/consumption, per-next-step budget checks, deadlines, reduced computation, human/budget interruption and safe stopping.

The scoring models are engineering realizations and are not claimed to be paper-exact equations. B5/GraSP remains the previously reviewed closed-candidate boundary; it is not implemented as a new residual mechanism. The mechanism module does not implement CoT/ToT/MCTS (M5) or LangGraph4j graph orchestration (M6). Its repositories/ledgers remain in-memory reference adapters; M7 supplies durable transactional implementations.

See [`docs/architecture/FAN_LAN-MECHANISM-ARCHITECTURE.md`](docs/architecture/FAN_LAN-MECHANISM-ARCHITECTURE.md), [`docs/adr/0004-fan-lan-mechanism-realizations.md`](docs/adr/0004-fan-lan-mechanism-realizations.md), and [`docs/phase4/M4-STATUS.md`](docs/phase4/M4-STATUS.md).

## M5 — independent planning strategies (accepted by GitHub Actions CI; green reported by the user on 2026-11-17)

M5 adds `supply-chain-agent-planning`. It keeps the planning algorithms separate from the LangGraph4j execution graph: CoT-style sequential reasoning is a structured one-path baseline; ToT-inspired search explores/prunes structured partial action paths; and RAP-inspired MCTS uses UCT-style selection, expansion, deterministic simulator-backed rollouts and reward backpropagation. All strategies return proposal-only candidates through one common evaluator that replays actions against the trusted M1 simulator and enforces hard constraints independently of weighted scores. An explainable router chooses a strategy from initial complexity/budget heuristics, and M4 B6 resource accounting tracks compute usage. Request fingerprints include semantic world/memory content and algorithm configuration so changed inputs cannot silently resume an old search.

The names are intentionally qualified: ToT is an inspired structured action-path search, not a literal textual-thought reproduction; MCTS is RAP-inspired with a deterministic supply-chain world model, not faithful RAP. The default routing thresholds require M8 evaluation. Planner sessions and complete planning/runtime artifacts are durable through accepted M7-B1/M7-B2 PostgreSQL adapters; M7 is closed with user-confirmed green CI. M8 evaluates decision thresholds, comparative quality and mechanism contribution. LangGraph4j orchestration and LangChain4j provider wiring remain M6. No planner can authorize or execute an action.

See [`docs/architecture/PLANNING-ARCHITECTURE.md`](docs/architecture/PLANNING-ARCHITECTURE.md), [`docs/adr/0005-independent-planning-strategies.md`](docs/adr/0005-independent-planning-strategies.md), [`docs/phase5/M5-STATUS.md`](docs/phase5/M5-STATUS.md), and [`docs/phase5/M5-TRACEABILITY.md`](docs/phase5/M5-TRACEABILITY.md). The supplied authoring report records 208/208 local framework-neutral tests; the user subsequently confirmed that GitHub Actions CI is green. The CI run log was not part of the uploaded archive.

## M6 — LangGraph4j cognitive orchestration (accepted by GitHub Actions CI; user-confirmed green after M6-A and M6-B)

M6 provides the actual LangGraph4j outer cognitive-cycle graph and connects it to the previously implemented CoALA, Fan & Lan and planning services. The user confirmed that the complete CI is green after both service-composition deliveries, so M6 is accepted. The graph controls request interpretation, evidence, workspace, planning/evaluation, B2 impasse subgraph, B4 intention governance, B6 meta-control, approval, execution, observation/reconciliation and learning. It does not implement CoT/ToT/MCTS search, CoALA memory stores or business-domain truth.

The graph-core correction was confirmed green by the user. M6 is split into two service-composition deliveries:

- **M6-A — decision services (user-confirmed complete/CI green):** actual M2 `MemoryRetrievalService`, B1 activation/utility selection, B3 workspace admission/broadcast, M5 `PlannerRouter`, world-model replay and deterministic candidate evaluation. Complete planner artifacts remain outside graph state.
- **M6-B — lifecycle services (complete; user-confirmed full M6 CI green):** `CognitiveCycleServiceOperations` composes M6-A with actual M2 `WorkingMemoryService`/`EpisodicMemoryService`, B2 `ImpasseResolutionService`, B4 `RecoveryIntentionService`, B6 `MetaControlGovernor`, and M3 `ActionDispatcher`/`ActionRegistry`. `CognitiveCycleApplicationGraph.compileFull(...)` is the complete composition entry point.

M6-B enforces stable scoped memory/intention identity; B2 delegated child capabilities and parent return validation; B4 protected objective/method governance and trusted termination; B6 execution-bound budget records; one-to-one typed M5-candidate/M3-command mapping; exact approval fingerprint binding and revalidation; authenticated action principal; idempotent action batches; reconciliation of unknown outcomes; optimistic working-memory observation writes; and idempotent unverified episodic recording. B4 objective completion requires a domain-supplied proof matching both the protected objective fingerprint and the reconciled observation.

The supply-chain/domain and trusted-authority adapters remain explicit dependencies (`CognitiveCycleLifecyclePort`, `CognitiveCycleAuthorityPort`). They must provide authoritative records, M3 command translation, ERP/WMS/carrier reconciliation, human identity, durable approvals and scoped principals. No permissive adapter is invented. The repository currently has deterministic simulator and in-memory reference adapters, not a connected real ERP/WMS/supplier/carrier or enterprise identity deployment.

M6-B's twelve focused lifecycle tests were supplemented by the fourteen graph tests and five M6-A integration tests. The user subsequently confirmed that the complete GitHub Actions run after M6-B is green; M6 is accepted. M7 subsequently replaced reference stores with transactional PostgreSQL repositories and defined restart reconciliation. M8 performs complete application evaluation and freeze. Chapters 19 and 20 remain unwritten until that freeze.

See [`docs/architecture/COGNITIVE-CYCLE-ORCHESTRATION.md`](docs/architecture/COGNITIVE-CYCLE-ORCHESTRATION.md), [`docs/architecture/COGNITIVE-CYCLE-DECISION-SERVICES.md`](docs/architecture/COGNITIVE-CYCLE-DECISION-SERVICES.md), [`docs/architecture/COGNITIVE-CYCLE-LIFECYCLE-SERVICES.md`](docs/architecture/COGNITIVE-CYCLE-LIFECYCLE-SERVICES.md), [`docs/phase6/M6-STATUS.md`](docs/phase6/M6-STATUS.md), [`docs/phase6/M6A-STATUS.md`](docs/phase6/M6A-STATUS.md), and [`docs/phase6/M6B-STATUS.md`](docs/phase6/M6B-STATUS.md).
## Build and test

Basic build and all deterministic tests:

```bash
./mvnw -B -ntp clean verify
```

Run PostgreSQL checkpoint, schema-migration and durable-memory integration tests when PostgreSQL is available:

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

M0–M7 are accepted. M8-B0 froze the accepted M7 baseline. M8-B1 delivers the 25-row scenario catalog (11 request families and 14 failure/adversarial cases), planner-only comparison harness, five comparators, moving fixture-clock budget enforcement, JSONL output and fail-closed full-graph composition probe; the user confirmed its CI is green. M8-B2 adds matched analysis, stratified aggregates, ten paired comparisons, deterministic bootstrap intervals, structured candidate/trace output and logical replay verification; its CI is user-confirmed green. M8-B3 adds eight mechanism-ablation studies, 19 scenario units, 45 primary observations, 26 paired contrasts and 18 deterministic replay rows (excluded from primary analysis). The result clarification marks small-sample outcomes as descriptive only and identifies unimplemented learning/durability edges rather than claiming them. Chapters 19 and 20 are written only after M8-B6 and outline approval.


## Running the M8 evaluation harness

After applying the M8-B1 patch and confirming its CI run is green, generate the full planner-only baseline as JSONL:

```bash
./scripts/run-m8-evaluation.sh > m8-evaluation-results.jsonl
```

The script runs the Java 21 reactor tests/build before invoking the `M8EvaluationCli`; Maven logs go to standard error, while standard output contains the batch manifest and one result row per scenario/planner pair. Use `./scripts/run-m8-evaluation.sh --list` to list the versioned fixture IDs without running comparisons. See [`docs/phase8/M8-B1-EVALUATION-ARCHITECTURE.md`](docs/phase8/M8-B1-EVALUATION-ARCHITECTURE.md) for scope and reproducibility limits.

## M8 — Evaluation and application freeze

M8-B0 freezes the M7-accepted source baseline and pre-registers the evaluation protocol. M8-B1 builds versioned simulator-backed fixtures and a planner-only harness for CoT-style sequential, ToT-inspired bounded search, deterministic/greedy, RAP-inspired MCTS and the hybrid router. The actual lifecycle workflows and failure injection remain M8-B4; the planner-only corpus must not be confused with end-to-end coverage. See [`docs/phase8/M8-STATUS.md`](docs/phase8/M8-STATUS.md), [`docs/phase8/M8-B0-BASELINE-AUDIT.md`](docs/phase8/M8-B0-BASELINE-AUDIT.md), [`docs/phase8/M8-B1-EVALUATION-ARCHITECTURE.md`](docs/phase8/M8-B1-EVALUATION-ARCHITECTURE.md), and [`docs/phase8/M8-EVALUATION-PROTOCOL.md`](docs/phase8/M8-EVALUATION-PROTOCOL.md).


### M8-B3 mechanism ablations

After applying the M8-B3 change-only patch and confirming GitHub Actions CI is green, reproduce the ablation run with:

```bash
./scripts/run-m8-ablations.sh
```

This script installs the evaluation reactor with tests skipped, then invokes the deterministic ablation CLI. It writes raw and paired-analysis JSONL beneath `target/m8-b3-results/`; set `M8_B3_OUTPUT_DIR` to override the output path. The runner does not invoke external actions or PostgreSQL. See [`docs/phase8/M8-B3-MECHANISM-ABLATIONS.md`](docs/phase8/M8-B3-MECHANISM-ABLATIONS.md), [`docs/phase8/M8-B3-RESULTS-CLARIFICATION.md`](docs/phase8/M8-B3-RESULTS-CLARIFICATION.md) and [`docs/phase8/M8-B3-ABLATION-REGISTER.csv`](docs/phase8/M8-B3-ABLATION-REGISTER.csv).
