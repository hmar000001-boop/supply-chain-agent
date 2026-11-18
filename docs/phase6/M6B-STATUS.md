# M6-B status — complete lifecycle service composition (delivery 2/2)

**Implementation delivered; full Maven/GitHub Actions acceptance pending.** M6-A was reported complete by the user. The corrected graph-core CI was previously confirmed green by the user. M6-B adds service-backed implementations for the remaining graph operations, while keeping business/domain and trusted-authority integrations explicit.

## Implementation delivered

- `CognitiveCycleServiceOperations` is the complete graph-facing composition of M6-A decision services and M6-B lifecycle services. It implements every `CognitiveCycleOperations` operation without a catch-all host delegate.
- `CognitiveCycleLifecycleServices` calls the actual M2 `WorkingMemoryService` and `EpisodicMemoryService`; M4 `ImpasseResolutionService`, `RecoveryIntentionService` and `MetaControlGovernor`; M3 `ActionRegistry` and `ActionDispatcher`; and the M6-A decision service.
- `CognitiveCycleLifecyclePort` carries typed business/domain adapter contracts. `CognitiveCycleAuthorityPort` carries authenticated approval, human-control, termination and execution-principal contracts. Neither has permissive fallback behavior.
- `CognitiveCycleApplicationGraph.compileFull(...)` is the service-backed application graph composition. The old `compile(hostOperations, ...)` is retained but deprecated as a migration seam.

## Lifecycle behavior and safeguards

- Stable execution-scoped working-memory and intention identifiers make repeated context initialization idempotent. Existing records must match scope and the protected objective.
- Accepted external evidence is translated to provenance-bearing versioned working-memory facts by the domain adapter.
- B2 impasses are opened through the mechanism service and paired with B4 delegation. A failed B2 start triggers compensating reject/return transitions; any compensation failure is preserved as a suppressed exception.
- B2 child plans are declared before execution. Each read step is checked against the case's delegated child capabilities and B2 action budget. Parent return requires the latest parent scope, request, working-memory version and objective fingerprint to match.
- B4 method changes cannot alter the protected objective fingerprint. Termination requires a trusted authority-port decision for the actor.
- B6 receives an execution-bound resource snapshot and explicit uncertainty; every decision is persisted through the domain adapter.
- M3 commands are typed, one-to-one and in the same order as the selected M5 candidate. Action definitions must match the trusted planner and runtime registry descriptors and be grounding/domain-write actions.
- Approval binds the exact request, execution, scope, candidate set, candidate, B4 intention/version and canonical typed-action fingerprint. Approval and plan feasibility are revalidated immediately before dispatch.
- M3 dispatch uses an authenticated resource-scoped principal, a plan-level batch and deterministic per-step idempotency keys. The batch is bound to the principal identity. Partial/unknown/rejected/failed attempts do not automatically replay the same plan.
- External observation/reconciliation is a separate domain adapter. An unknown outcome is reconciled, never blindly redispatched.
- Working-memory updates use optimistic versions. Conflict recovery reloads current memory and reapplies the already-persisted observation, without repeating external work.
- Outcome episodes are idempotent by `(scope, observation evidence reference)`. Reusing the same key with a different result payload is rejected. Episodes remain recorded/unverified. B4 completion separately requires an authoritative proof matching the protected objective fingerprint and exact observation evidence.

## Test inventory

Twelve focused tests exercise the actual M2/M3/B2/B4/B6 service classes and in-memory repositories/ledgers. Test doubles exist only for the explicit domain and trusted-authority ports.

See [`M6B-TEST-RESULTS.md`](M6B-TEST-RESULTS.md) for names and results.

## Persistence boundary

The M6-B action-plan, child-artifact, action-batch and observation stores are thread-safe in-memory reference adapters. M2 memory, B2/B4 mechanisms and M3 action ledger are also using the existing in-memory implementations in local tests. These are not crash-durable and must be replaced by M7 transactional PostgreSQL repositories before claiming process-restart recovery.

A production host must provide real implementations of `CognitiveCycleLifecyclePort` and `CognitiveCycleAuthorityPort`. In particular, those adapters must use authoritative supply-chain records, authenticated human identity, durable approval records and external-system reconciliation. The repository currently contains a deterministic simulator/reference environment, not a connected ERP/WMS/supplier/carrier deployment.

B2 child read steps also need host-level `(executionId, caseId, stepId)` idempotency or durable receipts before process-restart resumption is enabled; M6-B currently persists the resulting child artifact, not each external step receipt.

## Validation status

- Java 21 production-source compilation of the M6-B changed classes and their actual project source dependencies: passed with `--release 21 -Xlint:all`.
- Twelve focused M6-B tests: passed using a lightweight local JUnit-compatible harness against actual service classes. This is not Maven Surefire/JUnit Platform.
- Full Maven reactor, actual LangGraph4j compilation of the application host and GitHub Actions run: pending.

Required acceptance command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

M6 is not accepted until GitHub Actions validates the full reactor, including the earlier 14 graph tests, M6-A tests, M6-B tests and application-host compilation. M8 remains responsible for complete business evaluation and application freeze.
