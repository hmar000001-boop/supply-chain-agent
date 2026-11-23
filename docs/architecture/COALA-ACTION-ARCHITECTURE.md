# CoALA internal and external action architecture (M3)

## Purpose and boundaries

M3 adds an explicit action layer between CoALA memory/domain services and the later LangGraph4j orchestration. The action layer is framework-neutral. It does not define the outer decision procedure and it does not implement Fan & Lan B1–B6 mechanisms. In particular, memory retrieval delegates to the M2 exact deterministic lookup; activation scoring, downstream utility, latency estimation and maintenance accounting remain B1 work in M4.

The action layer separates three categories:

1. **Internal actions** read/update active cognitive context and invoke structured reasoning.
2. **Learning actions** expose versioned, scope-bound CoALA episodic, semantic and procedural memory operations. Recording/staging, review/verification and activation are separate actions with separate capabilities.
3. **Perception actions** are read-only. They return versioned observations with evidence references and provenance.
4. **Grounding actions** request state transitions in the modeled supply-chain world. They require explicit capability grants, resource scope, preconditions and idempotency.

The reference perception/grounding adapters read or mutate the immutable M1 simulator snapshot. They are not production ERP/WMS/carrier integrations and must not be represented as real external execution.

## Module and dependency boundaries

`supply-chain-agent-actions` depends on the framework-neutral application/domain/memory/simulator modules. It has no LangGraph4j or LangChain4j imports. The runtime module depends on the action contracts and contains the `LangChain4jReasoningProvider` adapter. The domain and simulator remain independent of both agent frameworks.

`SupplyChainActionCatalog.create(...)` retains a backward-compatible compact fixture overload. The complete durable application overload registers 23 actions:

| Category | Count | Actions |
|---|---:|---|
| Internal | 3 | memory retrieval; working-memory update; structured reasoning assessment |
| Learning | 10 | episodic record/verify/reject; semantic propose/approve/reject; procedural stage/validate/activate/reject |
| Perception | 6 | supplier; inventory lot; customer order; shipment; available inventory; lot-level quality disposition |
| Grounding | 4 | reserve inventory; release reservation; place purchase order; reroute shipment |

The ten learning actions use contract version 1, typed inputs/outputs, explicit capabilities, resource scope, idempotency and stable result references. The durable catalog binds them to one host `MemoryScopeId`, and the handler checks that binding even when an action principal holds an all-resource grant. Episodes are recorded unverified; model-derived episodes require caller-supplied evidence. Semantic facts remain PROPOSED until separate review. Procedures move DRAFT → VALIDATED → ACTIVE with distinct author, validator and approver identities. Procedure activation does not grant a domain-action capability.

The `Episode` and `SemanticFact` records do not persist the proposer identity. Their reviewer capabilities must therefore be separately issued by trusted authorization policy; their data models do not independently prove proposer/reviewer separation. The procedure lifecycle does persist and enforce this separation. Automatic model-weight adaptation and autonomous source-code modification are outside the learning-action contract.

The complete PostgreSQL/LangGraph4j application composition is `DurableCognitiveApplicationHost`; `Phase0Application` remains a small intake smoke app. The durable host owns PostgreSQL repositories and checkpoints, while B3's immutable workspace is deliberately ephemeral and cycle-scoped. See `docs/application/DURABLE-APPLICATION-HOST.md` for the startup, scope, persistence, learning and repeat-cycle contracts.

The action catalog has no LangGraph4j types. Graph services depend on the dispatcher/catalog boundary rather than embedding domain logic in graph nodes.

## Typed action contract

Every registered handler declares an `ActionDescriptor`, input class, output class, and execution function. Optional handler contracts declare the target resources, preconditions, a canonical request fingerprint and a stable result reference.

The dispatcher checks, in order:

1. Action ID is registered.
2. The runtime input and output classes exactly match the descriptor's handler contract.
3. The handler's target-resource set can be evaluated.
4. The trusted principal has the required capability and the grant covers every requested resource.
5. For a state-changing action, an idempotency key exists and a read-only ledger inspection identifies a previously completed/in-progress/unknown/reused operation before mutable preconditions run.
6. Mutable preconditions are checked against current memory/world state. A rejected precondition does not consume an idempotency key.
7. The ledger atomically claims the operation. This second check handles races after the earlier read-only inspection.
8. The handler executes. A successful receipt is committed to the ledger; failures are classified.

The early read-only inspection allows an exact duplicate to be acknowledged after a successful operation has changed the world or memory version. The later atomic claim is still authoritative for concurrent requests. A production ledger must implement inspect/claim/complete/fail with durable transactional semantics; the in-memory adapter is only a local/test implementation.

## Authorization and effect semantics

`ActionPrincipal` and its `CapabilityGrant` objects are trusted application inputs. A model does not create or widen them. A grant is either all-resources for one named capability or an explicit set of resource references. Scoped grants cannot be empty. The authorizer requires the grant to cover the complete resource set returned by the handler.

Perception descriptors must be read-only. Grounding descriptors must be domain writes and must require idempotency. Memory writes also require idempotency. The model-facing reasoning action is classified as read-only with respect to domain/memory state; it can still consume provider tokens/cost, which the later B6 resource governor must budget separately.

The `ActionResult` distinguishes success, already-completed duplicate, in-progress operation, rejected request and failed request. A failed state-changing action whose side-effect outcome is not known is marked `UNKNOWN_OUTCOME`; the caller must reconcile before retrying. The dispatcher does not treat an unknown external outcome as safely retryable.

Known domain failures are translated to typed failure codes. Authorization failures are rejected before preconditions and before the idempotency claim. No handler executes an action merely because an LLM proposed it.

## Internal actions

### Retrieval

`MemoryRetrievalAction` delegates to M2's `MemoryRetrievalService`. It is deterministic scoped lookup across working, verified episodic, active semantic and active procedural memory. It does **not** implement B1 activation or ranking.

### Working-memory update

`WorkingMemoryUpdateAction` requires a scope reference and expected version. Scope, version and expiry are checked before the idempotency claim; the M2 service performs the authoritative optimistic update. A stale or cross-scope request is rejected without consuming its idempotency key.

### CoALA learning actions

The durable catalog registers ten versioned actions. Episodic experience is recorded as `RECORDED` and remains excluded from verified retrieval until a separate verify/reject action with review evidence. Semantic facts are staged as `PROPOSED` and only become active after a separately authorized approve/reject action. Immutable procedural artifacts are staged as `DRAFT`, independently validated, and separately activated or rejected; author, validator and approver must be distinct. Activation makes a procedure available to governed future retrieval, but does not grant permission to execute its actions.

All learning payloads carry a version and memory scope; action dispatch requires capability, scope coverage, preconditions and idempotency. The durable catalog additionally binds every learning handler to the host's fixed scope. Episodic and semantic reviewer identities are recorded, but their current domain records do not store their original proposer identity, so separation depends on trusted capability issuance rather than a model-level author/reviewer comparison. Procedure records persist all three actors and enforce separation.

The older `EpisodeLearningAction` remains only in the backwards-compatible compact fixture catalog; it is not registered alongside the ten governed actions by the full durable catalog.

### Reasoning

`ReasoningAction` calls a `ReasoningProvider` port and returns a bounded `ReasoningAssessment`: findings, hypotheses, assumptions, evidence references, information gaps, confidence and concise rationale. It does not persist a chain-of-thought transcript. The action validates that every cited evidence reference came from the request and enforces output size bounds.

The LangChain4j adapter is outside this module. Its AI Service exposes a typed POJO return and no tool methods. The system prompt treats supplied context as untrusted data, asks for evidence-bounded findings and prohibits authorization or execution. The adapter maps citations only to the exact evidence keys provided by the caller. A provider/model must explicitly support and enable JSON Schema response formatting for that stronger constraint; otherwise LangChain4j AI Services may use prompt-based structured parsing. The adapter does not claim provider-independent JSON Schema enforcement.

## Perception and provenance

A perception result is an `Observation<T>` containing:

- the observed value;
- an entity reference;
- an evidence reference;
- provenance (source, source ID, captured/read times and source version);
- the source snapshot version.

The snapshot adapter refuses a read time earlier than the snapshot capture time and reports missing entities as not found. The lot-quality action exposes the current lot-level quality disposition and hold metadata. It is not a substitute for an inspection report or trusted quality-event ingestion.

The current read actions cover suppliers, inventory lots, available inventory, customer orders, shipments and lot-level quality disposition. Future external adapters can implement `SupplyChainPerceptionPort` without changing the action contracts.

## Grounding and simulator transitions

Grounding handlers delegate to `SupplyChainSimulationWorld`. M3 includes inventory reservation/release, purchase-order placement and shipment rerouting. The in-memory adapter uses an atomic compare-and-set over immutable snapshots and checks the expected world version. M1 simulator invariants remain the authority for modeled inventory, supplier, route, carrier, order and shipment constraints.

Shipment rerouting is restricted to planned or delayed shipments, an active target route, an active carrier that supports the route, a route different from the current route, and an ETA no earlier than the modeled transit time. A reroute emits an explicit effect identifying affected orders. In-transit shipments cannot be rerouted by this command.

These are modeled effects only. A future real carrier/ERP adapter must implement provider-specific idempotency, transaction/outbox/reconciliation and authorization semantics before it is enabled for production.

## Runtime/application limitations

- `InMemoryActionExecutionLedger` and `InMemorySupplyChainSimulationWorld` remain reference/test adapters. The durable application uses the PostgreSQL operation ledger and the supplied deterministic world adapter; live ERP/WMS/TMS/supplier integrations are outside this reference system.
- The durable full-graph host is `DurableCognitiveApplicationHost`. A deployment supplies authenticated domain/lifecycle/approval/evidence-verification ports and JSON codecs; these are not permissive defaults and are not created from LLM output.
- B3 workspace snapshots are intentionally ephemeral and per-cycle; consumer receipts and separately verified outcomes are durable.
- A green CI run alone does not establish empirical B1/B3 policy advantage, B6 calibration or B2 transfer. Keep B1/B3 feedback neutral/shadow and B6 `NOT_CALIBRATED` until independent evidence and approval exist.
