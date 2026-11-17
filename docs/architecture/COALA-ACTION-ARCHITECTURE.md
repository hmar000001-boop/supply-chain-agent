# CoALA internal and external action architecture (M3)

## Purpose and boundaries

M3 adds an explicit action layer between CoALA memory/domain services and the later LangGraph4j orchestration. The action layer is framework-neutral. It does not define the outer decision procedure and it does not implement Fan & Lan B1–B6 mechanisms. In particular, memory retrieval delegates to the M2 exact deterministic lookup; activation scoring, downstream utility, latency estimation and maintenance accounting remain B1 work in M4.

The action layer separates three categories:

1. **Internal actions** operate on cognitive state or invoke reasoning: retrieval, working-memory update, episode recording and structured assessment.
2. **Perception actions** are read-only. They return versioned observations with evidence references and provenance.
3. **Grounding actions** request state transitions in the modeled supply-chain world. They require explicit capability grants, resource scope, preconditions and idempotency.

The reference perception/grounding adapters read or mutate the immutable M1 simulator snapshot. They are not production ERP/WMS/carrier integrations and must not be represented as real external execution.

## Module and dependency boundaries

`supply-chain-agent-actions` depends on the framework-neutral application/domain/memory/simulator modules. It has no LangGraph4j or LangChain4j imports. The runtime module depends on the action contracts and contains the `LangChain4jReasoningProvider` adapter. The domain and simulator remain independent of both agent frameworks.

The action catalog is assembled through `SupplyChainActionCatalog.create(...)`. It registers 14 actions:

| Category | Actions |
|---|---|
| Internal (4) | memory retrieval; working-memory update; episode record; structured reasoning assessment |
| Perception (6) | supplier; inventory lot; customer order; shipment; available inventory; lot-level quality disposition |
| Grounding (4) | reserve inventory; release reservation; place purchase order; reroute shipment |

The catalog has no LangGraph4j types. A later graph node will depend on the dispatcher/catalog boundary rather than owning business logic.

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

### Episode learning

`EpisodeLearningAction` appends an episode with provenance/evidence. It does not verify, promote or certify the episode. Verification remains a separate trusted review operation in the M2 memory lifecycle.

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

## Production limitations retained for later milestones

- `InMemoryActionExecutionLedger` is not durable and does not coordinate across JVM instances. M7 must implement a persistent ledger whose claim is atomic under concurrent transactions.
- `InMemorySupplyChainSimulationWorld` is a reference/test adapter, not a production repository or external connector.
- M2 memory repositories remain in-memory; durable PostgreSQL memory/action adapters are M7 work.
- The operation receipt stores a stable result reference, not the full result payload. A duplicate currently returns `ALREADY_COMPLETED` plus the receipt; callers resolve the authoritative result from its referenced domain snapshot/store. M7 must make that lookup durable.
- Approval records and human authorization workflows are not implemented in M3. Grounding capabilities are trusted pre-authorized grants; B4/M6/M7 will integrate plan-bound approval, checkpoint interruption and revalidation.
- B1 activation-based retrieval, B2 impasse management, B3 workspace control, B4 intention governance and B6 meta-control are not implemented by this action package; they remain M4 mechanisms.
