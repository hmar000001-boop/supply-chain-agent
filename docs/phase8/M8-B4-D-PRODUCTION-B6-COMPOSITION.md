# Production B6 composition contract (M8-B4-D)

This document defines the required host composition. It is not a claim that `Phase0Application` currently starts the full cognitive-cycle application; that entry point remains a deterministic intake smoke application.

## Single guard identity

For one full application host, construct one `ResourceBudgetExecutionGuard` from the durable adapter and an authoritative immutable budget provider:

```java
var resourceBudgetLedger = postgresRuntimePersistence.resourceBudgets();
var budgetGuard = new ResourceBudgetExecutionGuard(
        resourceBudgetLedger,
        executionId -> budgetCatalog.forExecution(executionId),
        clock);
```

The `budgetCatalog` is a trusted host adapter. It must return the same immutable `ResourceBudget` for an existing execution. Rebinding an execution to a changed limit/deadline is rejected by the ledger/guard.

Use that exact same `budgetGuard` reference at all three boundaries:

1. **M5 planning:** supply it to the production `CognitiveCycleDecisionServices` constructor. Planner request limits become pre-invocation reservations; actual `PlanningStatistics` are committed after planning.
2. **M3 reasoning:** wrap the trusted `ReasoningProvider` with `new BudgetedReasoningProvider(provider, budgetGuard)`, register the resulting `ReasoningAction` in the host's final `ActionRegistry`, then pass that registry to lifecycle services. If B6 is enabled and a `ReasoningAction` is registered, lifecycle composition rejects a provider bound to a different guard.
3. **B6 lifecycle/meta-control:** supply the same reference to `CognitiveCycleLifecycleServices`. `CognitiveCycleServiceOperations` rejects different references between decision/lifecycle service instances. `governMetaControl` replaces host-supplied counters with the durable snapshot before evaluating the next action.

Finally compile with `CognitiveCycleApplicationGraph.compileFull(decisionServices, lifecycleServices, checkpointSaver)`. Do not register further action handlers after lifecycle-service construction; the registry is a mutable composition object, so host setup must be completed before the lifecycle service validates the B6 reasoning binding.

## Accounting semantics

- M5 reserves the request's declared computation ceiling and commits measured expansion/evaluation/model-call/rollout statistics.
- M3 reasoning currently charges one observable model invocation and one reasoning evaluation. It does not infer tokens or provider cost from elapsed time.
- A provider failure after invocation begins is conservatively charged at its reservation if no trusted measurement is available.
- A failure of the durable budget store before invoking the provider is fail-closed; no in-memory fallback is allowed for a B6-enabled host.
- A commit write failure leaves its reservation active, preventing later work from spending uncertain capacity.

## Explicit limitation

A hard process kill between reservation and commit can leave an active reservation. The durable ledger preserves it and meta-control sees the held capacity. B4-D does not implement automatic orphan-reservation reclamation, because releasing an uncertain reservation could permit overspending. A future recovery policy must reconcile the operation state first and then either commit the conservative estimate or release only with evidence that work never started.

The M3 external-operation ledger remains authoritative for external business side effects; B6 counts computational resources only. Stock, procurement money, supplier capacity, contracts and shipment capacity remain governed by their domain constraints and authorization/approval controls.
