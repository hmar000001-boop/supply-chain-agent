# Durable cognitive application host (GAP-07 / GAP-08)

## Purpose and implementation boundary

`DurableCognitiveApplicationHost` is the application composition root for the complete bounded cognitive cycle. It is distinct from `Phase0Application` (the small intake smoke graph) and from `FullGraphEvaluationHost` (an evaluation composition that can use in-memory ports).

The durable host constructs persistent repositories itself from one `PostgresAgentPersistence` and one shared `DataSource`. The application supplies trusted domain, identity, approval, evidence-verification, JSON-codec, simulator and model-provider adapters. It cannot inject substitute in-memory repositories into the host.

One host instance is bound to exactly one `MemoryScopeId`. Deployments serving multiple scopes create isolated host instances with scope-bound runtime stores and credentials. Cross-scope `start`, `resume`, action dispatch and learning operations fail closed.

## Durable composition

The host applies the versioned PostgreSQL migration chain and composes:

| Concern | Composition |
|---|---|
| CoALA working memory | PostgreSQL working-memory repository and `WorkingMemoryService` |
| Episodic memory | PostgreSQL episode repository and `EpisodicMemoryService` |
| Semantic memory | PostgreSQL semantic-fact repository and conservative `SemanticMemoryService` |
| Procedural memory | PostgreSQL procedure repository and `ProceduralMemoryService` |
| Retrieval | `MemoryRetrievalService` over the four PostgreSQL repositories |
| B1 | PostgreSQL per-scope memory feature state and governed learning service |
| B2 | PostgreSQL impasse/recovery-draft stores, typed draft service and governed promotion to procedural memory |
| B3 | Cycle-scoped workspace/admission/broadcast, PostgreSQL consumer receipts and verified outcome evidence |
| B4 | PostgreSQL recovery-intention and lifecycle stores |
| B6 | PostgreSQL leased resource budget ledger and one shared `ResourceBudgetExecutionGuard` |
| M3 | Complete typed action catalog, PostgreSQL idempotency/operation ledger, approval and observation stores |
| M5 | PostgreSQL planning artifacts and planner sessions; sequential, bounded ToT and MCTS strategies supplied by the host |
| Orchestration | Full `CognitiveCycleApplicationGraph` compiled with `PostgresLangGraph4jCheckpointSaverFactory` using the same `DataSource` |

The B3 workspace snapshot is intentionally in-memory, immutable and cycle-scoped. It is not durable global state. Its receipts and verified downstream outcome evidence are durable. This is an explicit lifetime boundary, not an accidental fallback.

The host creates one B6 guard and passes that exact object to the M5 decision services, the budgeted M3 reasoning provider factory and the B6/M3 lifecycle dispatcher. The lifecycle composition validates identity equality; a provider factory that wraps a different guard is rejected.

## Startup contract

`DurableCognitiveApplicationHost.open(environment, ports)` creates the pooled PostgreSQL persistence boundary, applies migrations and assembles the graph. `assemble(persistence, ports)` is available when the caller owns an existing `PostgresAgentPersistence`; closing an assembled host does not close caller-owned persistence.

`Ports` is intentionally explicit. Required host-owned dependencies include:

- trusted decision-domain and lifecycle-domain adapters;
- approval/identity authority adapter;
- request interpreter, world model, candidate evaluator and exactly one implementation for each `PlannerId`;
- `Function<ResourceBudgetExecutionGuard, ReasoningProvider>` so the provider is bound to the exact guard created by the host;
- perception and simulator-world adapters;
- authoritative budget provider and workspace consumers;
- explicit codecs for every PostgreSQL JSON payload;
- B2 evidence verifier, approval authority and draft request factory;
- B3 downstream outcome verifier and policy version/issuer identifiers.

The host does not supply permissive verifier defaults or fabricate approval principals. A codec is a serialization contract, not an evidence verifier. Trusted ports must be built from the deployment's authentication, approval and evidence-verification boundaries.

Database settings use the `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE`, `PGCONNECT_TIMEOUT`, `PGSOCKET_TIMEOUT` and `PGAPPNAME` environment keys understood by `PostgresDatabaseConfig`. Secrets must be injected by the deployment environment, never committed to the repository or written to logs.

## Repeated-cycle and restart contract

- `start(CycleRequest)` begins a distinct top-level request. `executionId` identifies its durable checkpoint thread and must be unique for a new cycle within the scope. It returns the last executed node, current checkpoint state and an explicit interruption flag rather than treating an intentional graph interrupt as an empty/failed invocation.
- `resume(scopeId, executionId, response)` resumes that exact interrupted/checkpointed execution; it does not create a new execution identity. The response is one of four sealed types: `ClarificationReply`, `EvidenceReply`, `HumanControlReply` or `ApprovalReply`. Identity-bearing values must be produced by the trusted ingress/authentication adapter; the graph re-resolves them through its authority/evidence ports. Callers cannot send arbitrary checkpoint maps or overwrite scope, plan selection, budget, authorization decisions, capability principal or execution state. The result also reports a subsequent intentional interruption.
- `threadId(scopeId, executionId)` exposes the stable deterministic checkpoint identity for operational correlation.
- A subsequent observation/new request is a new `CycleRequest` with a new `executionId`; it reuses the same scope-bound durable memories and runtime stores but receives a distinct graph checkpoint thread.
- User/host checkpoint updates are inputs from the authenticated UI/API boundary. Model-authored text cannot manufacture a reviewer, principal, approval or capability.

A production ingress adapter should persist its own request/event identity and delivery state in its authoritative source/outbox before calling `start`; the host's `executionId` is the graph correlation key, not a substitute for an upstream message broker's delivery ledger.

## GAP-07: versioned CoALA learning actions

The durable action catalog registers ten `ActionCategory.LEARNING` actions. Every command carries `contractVersion = 1`, a memory scope, a typed input, an idempotency key, an explicit capability and a result reference.

| Action | Effect on memory | Governance boundary |
|---|---|---|
| `memory.episodic.record.v1` | Records an unverified experience | Recording never verifies it; model-derived content must carry evidence in the action input |
| `memory.episodic.verify.v1` | Verifies a recorded episode | Separate verification capability and review evidence |
| `memory.episodic.reject.v1` | Rejects a recorded episode | Separate rejection capability and review evidence |
| `memory.semantic.propose.v1` | Creates a PROPOSED semantic fact | Not eligible as active trusted fact until review |
| `memory.semantic.approve.v1` | Activates a proposed fact | Separate review capability and activation evidence |
| `memory.semantic.reject.v1` | Rejects a proposed fact | Separate review capability and rejection evidence |
| `memory.procedural.stage.v1` | Creates an immutable DRAFT procedure artifact | Content-addressed artifact identity; no execution capability |
| `memory.procedural.validate.v1` | Validates a DRAFT | Validator must differ from author |
| `memory.procedural.activate.v1` | Activates a VALIDATED procedure | Approver must differ from both author and validator; activation does not authorize a domain action |
| `memory.procedural.reject.v1` | Rejects a DRAFT | Reviewer must differ from author |

Learning handlers are registered against the host's fixed `MemoryScopeId` and reject a payload that attempts to address another scope, even where a principal has an all-resource capability. Trusted host code issues capabilities; the model only proposes typed data and cannot manufacture authority.

The Episode and SemanticFact domain records currently preserve the reviewing/activation actor and review evidence but do not persist their proposing principal. Their reviewer capabilities therefore need to be separately issued by trusted authorization policy; the record model does not independently prove proposer/reviewer identity separation. Procedural memory does persist author, validator and approver identities and enforces their separation in the action handler. Do not claim stronger episode/semantic separation than that contract provides.

Every activated semantic fact and procedure is available to subsequent scoped retrieval; approved recovery procedures are passed through the B2 promotion/revalidation boundary before future planning. An episodic record is only experience evidence; it is not trusted outcome evidence until separately verified. Verified B3 outcome evidence remains under its own evidence-verifier and shadow/activation policy. B1/B3 feedback stays neutral/shadow unless a separately approved policy is supplied. B6 remains `NOT_CALIBRATED` when no calibrated profile codec/service is supplied.

Automatic model-weight adaptation and autonomous source-code modification are explicitly outside this application's learning-action contract.

## Operational and research boundaries

The application executes typed modeled-world transitions through the deterministic simulator. It does not connect to live ERP/WMS/TMS/suppliers/carriers. LLM output and planner candidates remain proposals; the trusted authority supplies approval and execution principals; the graph revalidates the exact selected plan before simulator dispatch; uncertain effects require reconciliation.

The app-module PostgreSQL integration test opens this exact host, verifies the 23-action catalog and shared B6 reasoning guard, writes and reloads CoALA working memory across host close/reopen, starts a cycle that pauses at the clarification boundary, resumes that exact checkpoint after host restart, and starts a second independent top-level cycle in the same bound memory scope. It deliberately keeps the test at the clarification boundary so it does not fabricate downstream domain facts or approval.

The remaining final GAP-08 acceptance test must drive this durable host through a fully configured domain fixture: context load, retrieval/workspace/planning, plan-bound human approval, post-approval revalidation, simulator dispatch, outcome reconciliation, process restart at the effect boundary, and a subsequent top-level cycle that consumes the persisted observation. Existing full-graph/effect-boundary PostgreSQL tests cover those runtime semantics through the evaluation composition, but do not yet combine them with this new `DurableCognitiveApplicationHost`. Therefore this changeset closes the durable application composition and basic restart/repeated-ingress path; it does not claim the complete host-level effect-boundary journey is already tested.

No claim of empirical B1/B2/B3/B4/B6 superiority, calibrated uncertainty, or real external supply-chain execution follows from a green build.
