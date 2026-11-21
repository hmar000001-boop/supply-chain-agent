# Phase 4 — Action, Execution and Authority Conformance Audit

**Project:** Supply-Chain Disruption Management Agent  
**Source baseline:** User-uploaded `supply-chain-agent-main (6).zip`  
**ZIP SHA-256:** `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`  
**Audit phase:** 4 of 6  
**Scope:** CoALA internal/external actions; M3 execution; authority and approval boundaries; idempotency/fencing; observation reconciliation; PostgreSQL adapters and associated tests.  
**Change class:** Audit only. No project source, test, dependency, migration or workflow was modified.

## 1. Executive assessment

The uploaded source has a **strong and explicit M3 action-authority boundary**. It is more than a planner emitting tool-call JSON: the full cognitive lifecycle turns the selected plan into typed commands, checks each command against a registered action descriptor, binds the plan to the active intention, obtains host-issued approval, revalidates the plan and approval immediately before dispatch, and then calls the M3 dispatcher. The dispatcher independently enforces action type, capability and resource scope, preconditions and idempotency. The batch and ledger layers distinguish completed, known-failed, partial and unknown outcomes. Unknown outcomes are routed to reconciliation rather than blind retry.

That is a strong engineering operationalization of CoALA external grounding actions and human-controlled execution. It is also a defensible book example of why planning, authorization, execution and observation should not be collapsed into one LLM call.

However, the following boundaries must remain explicit:

1. **There is no concrete production `CognitiveCycleAuthorityPort` implementation in the application source.** The PostgreSQL approval repository persists approval records, but authentication, approver authorization and issuance of the execution principal remain responsibilities of a trusted host adapter. Current concrete adapters found are test/evaluation adapters.
2. **The actual supply-chain world is simulator-backed.** `InMemorySupplyChainSimulationWorld` is the only implementation of `SupplyChainSimulationWorld` in main source, and `SnapshotSupplyChainPerceptionAdapter` reads immutable simulator snapshots. No live ERP, WMS, supplier, carrier or payment connector is present.
3. **Reconciliation is a mandatory domain port, not a supplied general-purpose external-system reconciler.** The core passes the persisted action batch to the host and refuses to redispatch an unknown/partial batch. Correct reconciliation against real external state remains domain-adapter work.
4. **The PostgreSQL fencing token fences ledger writes, not the external provider itself.** The stable operation ID is passed to the handler and ambiguous results become `UNKNOWN_OUTCOME`, but a live external connector must use provider-side idempotency and/or authoritative status lookup. A database lease alone cannot undo or fence an already-issued remote side effect.
5. **The full evaluation host is intentionally in-memory for working memory, action ledger, action plan, action batch and observation stores.** Durable PostgreSQL adapters exist and have integration tests, but the evaluation host does not instantiate the full durable production composition.

These are not reasons to redesign the core action model. They are integration limits and required source-label caveats for Part III.

## 2. CoALA mapping

### 2.1 Internal actions

The action catalog registers explicit internal handlers for retrieval, reasoning, working-memory update and episodic recording. These correspond to CoALA's internal retrieval, reasoning and learning action families, with important project-specific governance:

- Retrieval is read-only and scope-bound.
- Reasoning is read-only, returns typed findings/assumptions/hypotheses and rejects citations not present in the supplied evidence.
- Working-memory mutation requires a typed request, a memory-scope capability, an idempotency key, a canonical fingerprint and optimistic version/expiry checks.
- Episode recording requires a memory-scope capability and records an unverified episode; the action cannot verify or promote its own record.

The graph's successful external outcome path separately verifies objective satisfaction before recording an outcome episode. The episode remains `RECORDED_UNVERIFIED`. This is a governed learning record, not automatic policy learning.

### 2.2 External grounding and perception

Perception handlers are typed read-only actions. Their descriptors must use `ActionCategory.PERCEPTION` and `ActionEffect.READ_ONLY`. The snapshot adapter returns observations with entity, evidence reference, provenance, source version and read time.

Grounding handlers represent inventory reservation, reservation release, purchase-order placement and shipment rerouting. Their descriptors must use `ActionCategory.GROUNDING`, `ActionEffect.DOMAIN_WRITE`, a specific capability and mandatory idempotency. The lifecycle rejects state-changing candidates that attempt to bypass explicit approval.

This is a strong structural correspondence to CoALA's grounding boundary, but the runtime target here is the deterministic supply-chain simulator. Do not describe these handlers as live ERP/WMS integrations.

### 2.3 Decision / execution separation

`CognitiveCycleGraph` sequences interpretation, context retrieval, planning, evaluation, intention governance, B6 meta-control, authorization, approval interruption, revalidation, execution, observation/reconciliation, working-memory update and outcome recording. These are runtime engineering stages; CoALA's conceptual loop is not being claimed to prescribe this exact graph topology.

## 3. Authority and dispatch trace

The effective state-changing path is:

1. M5 selects a candidate.
2. The lifecycle adapter translates each selected planning action into exactly one typed `ActionDispatchCommand` in plan order.
3. The lifecycle resolves the registered descriptor and compares it with the trusted descriptor in the `PlanningRequest`; only registered `GROUNDING` + `DOMAIN_WRITE` actions are eligible in this path.
4. An immutable authorized-action-plan fingerprint binds candidate set, candidate, intention identity/version and command fingerprints.
5. The authority port is asked for approval. A state-changing plan cannot receive `NO_APPROVAL_REQUIRED`.
6. On resume, the host-issued approval response is checked against approval ID, candidate, candidate-set and intention version, and approver identity.
7. The plan is revalidated against a fresh domain/evidence request and current working memory. Approval is checked again immediately before dispatch.
8. The authority port issues an `ActionPrincipal` with pre-issued capability grants.
9. A durable execution batch is created; each action is dispatched through M3 with a stable, plan-bound idempotency key.
10. The dispatcher validates input/output types, resolves requested resources, checks capability/resource scope, fingerprints input, inspects prior outcome, checks handler preconditions, acquires the idempotency lease/fence, executes through the shared B6 execution budget and writes an action receipt.
11. Each result is durably appended to the batch. Multi-step partial effects are not represented as clean success.
12. The graph calls the domain reconciliation port with the batch. It writes the reconciled observation, then writes a versioned evidence-linked summary to working memory. Only an `OBJECTIVE_SATISFIED` observation with a matching authoritative proof can complete the B4 intention and record an unverified outcome episode.

This chain is substantively integrated, not just a set of unrelated service classes.

## 4. Strengths confirmed in source

### 4.1 Descriptor and capability contract

`ActionDescriptor` prevents state-changing actions from omitting idempotency, prohibits writes in perception actions, and requires grounding actions to declare domain writes. `CapabilityGrant` represents either a named all-resource grant or a non-empty explicit allowlist. `CapabilityAuthorizer` requires a matching capability and sufficient resource scope. Each handler computes its requested resources from the typed input; for example, shipment rerouting requires both the shipment and target-route resources.

### 4.2 Dispatcher semantics

`ActionDispatcher` checks the registered action and exact Java input/output classes before invoking the handler. It checks authorization before idempotency lookup or mutable preconditions. It checks the ledger before mutable preconditions so that a previously completed duplicate remains idempotent even after the original effect changed the world. A reused idempotency key with a different fingerprint is rejected.

Known retryable failures can be retried. State-changing runtime failures with uncertain effect become `UNKNOWN_OUTCOME`; failure to persist a receipt after a handler returns is also treated as unknown. Unknown outcomes are explicitly non-retryable by the ordinary dispatcher path.

Current internal mutating handlers and grounding handlers override canonical request fingerprints. The default interface fingerprint uses `inputType + input.toString()`, so any future mutating handler must be required by review/test to provide a stable canonical fingerprint rather than relying on the fallback.

### 4.3 Approval is plan-bound

`ApprovalBinding` includes request, execution, memory scope, candidate set, candidate, intention ID/version and action-plan fingerprint. The lifecycle checks that the approval corresponds to the exact selected plan. It never permits a planner-generated claim that a domain write needs no approval.

`PostgresApprovalStore` persists a revisioned decision, requester principal, capability/resource scope snapshots, evidence reference, policy version, expiry and approver/response identity. It supports revocation and exact-binding/time validation. It explicitly does not authenticate the approver; that is a host responsibility.

### 4.4 Durable idempotency and crash semantics

`PostgresActionExecutionLedger` uses scope/principal/action/idempotency key, a request fingerprint, operation ID, state, lease owner/expiry and monotonically increasing fencing token. Stale or expired leases become unknown outcomes, not redispatch permission. Completion/failure writes require the current token and active lease. A host can reconcile `UNKNOWN_OUTCOME` as completed only with a bound receipt, or as not-applied only with evidence supporting a safe retry.

The full-graph lifecycle maintains a separate batch record and ordered per-step receipts. If the graph restarts after a handler succeeded but before the batch step was appended, the deterministic idempotency key lets the dispatcher recover the completed ledger result without re-running the effect. If the prior action has unknown outcome, the batch is stopped and the graph routes to observation/reconciliation.

### 4.5 Observation and learning safeguards

The graph does not accept model output as authoritative proof of task completion. `recordOutcomeEpisode` requires an `OBJECTIVE_SATISFIED` observation and a host-issued `ObjectiveSatisfactionProof` whose objective fingerprint matches the protected intention and whose evidence includes the observation. Provenance must come from an approved source category. Episode creation is idempotent by observation reference and remains unverified.

## 5. Findings and recommendations

| ID | Priority | Finding | Consequence | Recommendation |
|---|---|---|---|---|
| F4-01 | High, host-integration boundary | No concrete production `CognitiveCycleAuthorityPort` implementation exists in main/app source. `PostgresApprovalStore` is a repository, not an authenticator, approval policy engine or principal issuer. | The core graph has the correct seam, but no complete trust adapter is delivered for a live host. | Keep the port explicit. In the book, show a trusted host adapter contract and label authentication/approver policy as external. A deterministic test adapter is not production IAM. |
| F4-02 | High for deployment; non-blocking for the book | Only simulator-backed grounding and snapshot perception adapters exist in main source. | The project demonstrates domain action control but not real-world provider integration. | Preserve the simulator as the reference world; add separate ERP/WMS/carrier adapter examples only if the book requires them. Do not call the current implementation production-integrated. |
| F4-03 | Medium-high | Reconciliation is delegated to `CognitiveCycleLifecyclePort.observeAndReconcile`. The lifecycle checks the batch belongs to the execution and does not redispatch, but the `ExternalObservation` value itself is only disposition, observation ID, evidence-set ID and reason. The `ObservationProvenanceResolver` attaches execution/principal/source/time but is host-supplied and receives only that observation. | The core enforces the reconciliation step's existence, but does not itself prove that a returned observation is bound to every operation in the exact batch. | For a stronger generic integration contract, add a typed reconciliation result/proof binding batch ID, plan fingerprint, per-operation references, source version and verifier identity; validate it before persistence/WM update. Treat this as a book-level engineering extension, not a CoALA/Fan & Lan requirement. |
| F4-04 | Medium-high | PostgreSQL fencing tokens fence ledger state writes. They are not propagated as fencing generations to the external provider. | A handler may still complete a remote effect after lease expiry; its receipt is rejected and the operation remains unknown. | Require downstream provider idempotency using `ActionContext.operationId`, and an authoritative query/reconciliation path. Do not describe database fencing as remote-side-effect fencing. |
| F4-05 | Medium | `ActionHandler.requestFingerprint` has a generic `toString()` fallback. Current mutating handlers override it, but the interface does not force future mutating handlers to do so. | A future handler can accidentally define unstable/non-canonical idempotency semantics. | Add a contract test or require canonical fingerprint override for `MEMORY_WRITE` and `DOMAIN_WRITE` handlers. This is a guardrail, not a current-handler defect. |
| F4-06 | Medium | `PostgresObservationStore` relies on an injected `ObservationProvenanceResolver`; there is no main-source adapter tying its source reference to the precise batch/receipt and authenticated actor. | Provenance integrity is only as strong as the trusted host adapter. | Implement/describe the resolver together with the reconciliation proof in F4-03. |
| F4-07 | Low, documentation | Evaluation composition uses in-memory working-memory, episode, plan, batch, observation and action-ledger stores. Durable adapters exist independently. | Full-graph test evidence does not demonstrate every durable adapter is used together in one application composition. | Keep this distinction in the chapters and add a durable composition test only if that integration is a teaching objective. |

### What is not a finding

- Approval interruption and approval-before-write are not merely documented; the graph and lifecycle execute them.
- `UNKNOWN_OUTCOME` is not blindly retried; it is deliberately routed to reconciliation.
- `ActionReceipt` does not claim atomic external commit. This is correct and should be preserved.
- The use of an in-memory simulator is intentional for the reference world. It is a deployment limitation, not a failure of CoALA correspondence.
- No production IAM implementation does not make the authority boundary conceptually wrong; it means the trusted host remains a required adapter.

## 6. Test evidence reviewed (source inspection only)

The source tree contains direct tests for the principal invariants:

- `ActionDispatcherTest`: duplicate success, key reuse with different input, authorization before precondition/claim, precondition rejection does not consume the key, retryable known failure, registry registration.
- `SupplyChainExternalActionsTest`: snapshot observations with provenance, scoped domain writes, duplicate reservation release/purchase-order/reroute, denied resource scopes and modeled-world preconditions.
- `CognitiveCycleGraphTest`: state-changing action interrupts for approval; no execution before approval; denied approval never executes; approval binding to exact candidate/intention; unknown external outcome reconciles and is never blindly retried; explicit human-control boundaries.
- `CognitiveCycleLifecycleServicesTest`: working-memory observation retries/idempotency; objective completion proof; B4 intention protection; B6 shared durable guard composition.
- `ActionDispatchCommandTest`: shared B6 dispatch guard, stable idempotency/operation identity, known and unknown failure behavior, receipt failure and budget reservation lifecycle.
- `M8B4CRestartReconciliationIntegrationTest` and `M8B4ReliabilityIntegrationTest`: PostgreSQL unknown-outcome persistence, host replacement, no blind redispatch, authoritative reconcile-as-completed, and safe retry only after authoritative not-applied evidence.
- `M8B4FullGraphWorkflowTest`: end-to-end simulator/host adapter graph paths for selected planning actions and R2 graph integrations.

These are test-source claims, not new test executions. The user reports that the uploaded source ZIP passed CI. This audit did not rerun Maven.

## 7. Fan & Lan mapping: keep attribution precise

The M3 dispatch/approval/idempotency architecture is primarily an **application engineering authority layer**. It supports the wider mechanism system and intersects with B4's distinction between goal authority and execution-method authority, and B6's resource governance, but it is not itself one of Fan & Lan's B1/B2/B3/B4/B6 bundles.

- **B4 connection:** the action plan is bound to a persistent intention ID/version and protected objective; approval cannot silently change the goal. However the separate B4 method-to-next-planner gap remains from Phase 2.
- **B6 connection:** M3 reasoning and grounding dispatch can be required to use the exact shared `ResourceBudgetExecutionGuard`; durable leases/fencing and unknown-outcome handling are resource/reliability controls. This does not close the Phase 2 B6 `REDUCE_COMPUTATION` downshift gap.
- **CoALA connection:** action registry/dispatcher and typed internal/perception/grounding handlers make the action space explicit; the decision graph keeps planning distinct from external execution and returns observations to the next cycle.

Do not assign an E-code or D-code globally to the action module. E1–E4 are evidence relations for a specific historical mechanism mapping and D0–D4 are migration depth; they are not code-quality ratings.

## 8. Recommended work after the audit

No source change is recommended solely to make this Phase 4 report look more complete. For educational fidelity and claim accuracy, the next highest-value work is:

1. Continue Phase 5: inspect every PostgreSQL repository, V001–V011 migration, checkpoint integration and process-restart boundary as one coherent durability model.
2. Continue Phase 6: consolidate all per-class records and CoALA/Fan & Lan conformance, ensuring the authority, reconciliation and live-integration caveats are accurately represented.
3. After the audit is accepted, decide whether a small code increment is warranted for F4-03 (typed reconciliation proof). Keep IAM and live connector implementations explicitly out of scope unless needed for the reference case study.

## 9. Review depth and limitations

This phase targeted 55 source files/classes/contracts across actions, runtime lifecycle, graph, app composition, PostgreSQL adapters and tests. It read the full dispatcher and key contract/service implementations, mapped the graph routes, inspected the database-ledger and approval algorithms, and examined focused tests. The register marks test and adapter artifacts separately from core production types.

No Maven command, PostgreSQL container, live ERP/WMS adapter, external provider, security review or production deployment was executed. The audit does not claim production readiness.
