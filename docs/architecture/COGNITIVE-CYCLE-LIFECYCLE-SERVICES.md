# Cognitive-cycle lifecycle services — M6-B

## Purpose and boundary

M6-A connected the decision side of the graph to the actual M2 retrieval, B1 selection, B3 workspace and M5 planning/evaluation services. M6-B completes the remaining service-backed lifecycle operations. It does not turn LangGraph4j nodes into memory stores, business repositories, planner algorithms or authorization authorities.

`CognitiveCycleServiceOperations` is the concrete `CognitiveCycleOperations` composition. It combines:

- `CognitiveCycleDecisionServices` from M6-A;
- `CognitiveCycleLifecycleServices` from M6-B;
- explicit domain and trusted-authority adapters supplied to the lifecycle services.

`CognitiveCycleApplicationGraph.compileFull(...)` is the complete graph assembly entry point. The older `compile(hostOperations, ...)` overload remains only as a deprecated migration seam and should not be used for the full application path.

## Service mapping

| Graph operation | Actual service / responsibility | Important invariant |
|---|---|---|
| Interpret | `RequestInterpreter` using a domain-produced typed interpretation request | No fabricated clarification or domain facts |
| Load context | M2 `WorkingMemoryService`; M4 `RecoveryIntentionService` | Stable execution-scoped IDs make repeated load/restart idempotent; both records must match the tenant/scope and protected objective |
| Accept external evidence | Explicit domain evidence adapter, then versioned M2 working-memory writes | Only accepted evidence with a persisted evidence-set reference enters the next cycle |
| Retrieve / B1 / B3 / M5 | M6-A `CognitiveCycleDecisionServices` | Evidence and workspace admission are completed before planner dispatch; the evaluator replays trusted actions |
| Open B2 impasse | M4 `ImpasseResolutionService` plus B4 `RecoveryIntentionService.delegate` | The child case and parent intention transition together; a failed start triggers compensating reject/return transitions |
| Investigate B2 child | `ImpasseResolutionService.recordChildAction` plus bounded domain read adapter | Every step must be inside the child case's delegated capability set and action budget |
| Validate B2 return | `ImpasseResolutionService.resolve` plus B4 `returnFromDelegation` | Parent scope, request, latest working-memory version and protected objective fingerprint must still match |
| Govern B4 intention | M4 `RecoveryIntentionService` | Method replacement must retain the immutable objective fingerprint; termination requires authority-port verification |
| Govern B6 meta-control | M4 `MetaControlGovernor` | Budget snapshot must belong to the same graph execution; each decision is recorded by the host budget ledger |
| Resolve human control | `CognitiveCycleAuthorityPort` and B4 intention service | Response reference and actor must match authenticated host-issued state; abort terminates only through the authorized path |
| Prepare / resolve approval | Trusted authority port plus registered M3 descriptors | State-changing plans cannot use the approval-free route; approval binds exact candidate set, candidate, intention version and action fingerprint |
| Revalidate | Fresh domain `PlanningRequest` plus M5 deterministic evaluator and current approval | The exact candidate is replayed against fresh authoritative state immediately before execution |
| Execute | M3 `ActionDispatcher`, `ActionRegistry`, `ActionPrincipal` and action ledger | Typed commands must map one-to-one to the selected M5 actions and trusted registered descriptors; principal identity is bound to the batch |
| Observe / reconcile | Explicit domain adapter and observation store | Ambiguous or partial outcomes are reconciled; the same plan is never blindly redispatched |
| Update working memory | M2 `WorkingMemoryService` | Optimistic version conflict returns to the graph for reload; the already-persisted observation is reused |
| Record outcome | M2 `EpisodicMemoryService` and M4 B4 intention service | Episode is recorded unverified and idempotently; B4 completion requires a domain proof tied to the exact protected objective and observation |

## The M3 command boundary

A selected M5 candidate contains planning action descriptors, not executable Java lambdas or raw HTTP commands. The domain adapter converts each selected action into one `ActionDispatchCommand<I,O>` with:

- the exact `planningActionId` and registered `ActionId`;
- explicit input and output classes;
- a typed input value.

Before persisting the authorization plan, the lifecycle service checks that command count and order match the selected candidate, the runtime registry descriptor equals the M5 request's trusted descriptor, the descriptor is a `GROUNDING` action with `DOMAIN_WRITE` effect, and the input can be fingerprinted by the registered handler. The plan fingerprint includes the candidate set, candidate, intention identity/version and canonical request fingerprints.

Approval is requested against that fingerprint. At dispatch time the implementation rebuilds/reloads the action plan, checks current approval validity, obtains an authenticated resource-scoped `ActionPrincipal`, replays the plan against a fresh world/evidence snapshot, and checks approval again. Only then does M3 `ActionDispatcher` execute the typed commands.

The operation batch is bound to execution, candidate set, candidate, plan fingerprint and authenticated principal. Per-step idempotency keys are deterministic. `UNKNOWN_OUTCOME`, `PARTIAL`, `REJECTED` and `FAILED` batches are terminal for that exact plan in the lifecycle path; they require reconciliation or a newly selected and approved plan, not an automatic replay.

## B2 child restrictions and parent return

The domain adapter first declares a finite child-investigation plan. Each step is separately checked against the B2 case's delegated child capability set; the B2 service records the child action and enforces its budget before the adapter is called. The adapter executes one read-only step and returns evidence-bearing `ResolutionFinding` values. The result cannot contain a command that mutates the parent domain.

Before the parent accepts the result, the lifecycle service re-reads the parent working memory and asks the domain adapter for an authoritative parent snapshot. B2 validates the original parent version and objective fingerprint. Only a valid result is followed by B4 `returnFromDelegation`.

If opening/delegating the case succeeds but starting the child fails, M6-B attempts compensating transitions: reject the still-open child case and return the delegated intention with `NO_METHOD_CHANGE`. Compensation failures are attached as suppressed exceptions and are not hidden.

## CoALA memory write and verification semantics

### Working memory

The host must supply a stable working-memory ID for the execution. `WorkingMemoryService.open(id, scope, time)` is idempotent and rejects reuse across scopes. Context facts carry evidence references; writes use optimistic versions. An observation that has already been persisted is applied idempotently after the graph reloads the latest version.

### Episodic memory

`EpisodicMemoryService.recordIdempotent` uses an observation evidence reference as the idempotency key. The in-memory repository atomically returns an existing episode for the same scope/key only if the outcome payload also matches; conflicting reuse raises `MemoryConflictException`. The episode remains `RECORDED` / unverified. This lifecycle path does not self-verify an episode.

B4 objective completion is a separate state transition. Before it occurs, the domain adapter must provide an `ObjectiveSatisfactionProof` whose objective fingerprint equals the active intention's protected fingerprint and whose evidence includes the exact reconciled observation. A model narrative or a planner score cannot provide this proof.

## Explicit host adapter contracts

`CognitiveCycleLifecyclePort` is the supply-chain/domain adapter boundary. It must provide authoritative scope and stable IDs; typed interpretation inputs; accepted evidence and provenance; recovery objectives and protected fingerprints; bounded B2 read plans; current parent snapshots; B4 directives; B6 budget/uncertainty inputs and decision persistence; fresh planning requests; exact typed M3 command construction; external observation/reconciliation; outcome drafts; verified objective-satisfaction proofs; and escalation records.

`CognitiveCycleAuthorityPort` is separate. It must resolve approval records, verify exact binding, expiry and revocation, resolve host-authenticated human responses, authorize B4 termination, and issue authenticated resource-scoped M3 principals. It has no permissive default implementation. Graph state, model output and planner output cannot manufacture authority.

These ports are deliberate: ERP/WMS/supplier/carrier interfaces and the application's identity/approval provider are environment-specific. The repository currently contains a deterministic simulator and in-memory reference adapters, not a real ERP, WMS or enterprise identity deployment. A production host must supply and integration-test concrete adapters before enabling external writes.

## Persistence and restart limits

The current M6-B artifact and lifecycle stores (`InMemoryAuthorizedActionPlanStore`, `InMemoryImpasseChildArtifactStore`, `InMemoryActionExecutionBatchStore`, and `InMemoryObservationStore`) are thread-safe reference implementations, not crash-durable stores. M2 working-memory/episodic repositories, B2/B4 repositories, M3 action ledger and M6-A planner-artifact store also use the current in-memory reference adapters unless the host supplies durable implementations.

M7 must replace these with transactional PostgreSQL-backed repositories and define unique constraints for:

- scope + stable working-memory identity;
- scope + episodic idempotency evidence reference;
- B2 case and child-step receipts;
- intention identity/version;
- execution + candidate + plan fingerprint;
- action batch + step index + authenticated principal;
- observation identity;
- M3 action idempotency key and operation ledger.

The B2 child-step adapter must make each declared read operation idempotent by `(executionId, caseId, stepId)` or persist a receipt before a restart is enabled. The in-memory child-artifact store currently retains the final typed result/return, not a crash-durable receipt for each external child read.

M7 also owns transactional outbox/inbox, approval persistence, process-restart reconciliation, database migrations/backups and operational audit storage. M8 owns multi-request/concurrency/fault-injection evaluation and application freeze.

## Test boundary

M6-B tests exercise the actual M2/M3/B2/B4/B6 service classes and in-memory repositories/ledgers, with test doubles only at the explicit domain and authority ports. They are focused service-integration tests, not a claim that a real ERP/identity deployment or a crash-durable PostgreSQL runtime has been tested. The 14 LangGraph4j graph tests from the previous M6 delivery continue to cover graph control/interrupt behavior with deterministic scripted operations. The full Maven/Surefire run must validate all modules and both test groups in GitHub Actions.
