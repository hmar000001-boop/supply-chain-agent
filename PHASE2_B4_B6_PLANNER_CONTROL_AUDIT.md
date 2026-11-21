# Phase 2 — B4/B6 Planner-Control Integration Audit

**Project:** Supply-Chain Disruption Management Agent  
**Audit baseline:** user-uploaded `supply-chain-agent-main (6).zip`  
**Baseline SHA-256:** `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`  
**Audit date:** 2026-11-21  
**Scope:** B4 intention/method authority; B6 uncertainty/resource meta-control; their interaction with M5 planner selection and the full cognitive graph.  
**Change status:** audit only; no application source or tests modified.

## 1. Executive conclusion

The source confirms that both B4 and B6 have substantive stateful implementations. The gaps are at the **control-to-planner integration boundary**, not at the initial state-transition layer.

- **B4:** intention lifecycle state is versioned, persisted and protected by an immutable objective fingerprint. A method-change transition can be performed and survives PostgreSQL repository replacement. However, the normal decision path does not consume the changed method as an authoritative planner/method selection. `CognitiveCycleDecisionServices` always invokes `PlannerRouter.plan(request)`, while `RecoveryMethodReference` remains an unconstrained pair of strings and there is no method registry/binding contract between the intention and planning request. This is a partial B4 realization, not an end-to-end demonstration that the changed method governs future reasoning.
- **B6:** hard resource accounting is stronger. One shared leased/fenced `ResourceBudgetExecutionGuard` is required across M5, M3 and B6, and the planner request's limits are reserved before invocation and actual measured use is committed. The meta-governor can select `REDUCE_COMPUTATION`, and the graph routes back to planning. But no typed downshift directive is returned or required by the service contracts; the next `PlanningRequest` is rebuilt by a separate port without a required adjustment input. The host callback is `void`, and the full-graph evaluation adapter's callback is a no-op. The governor test proves the decision is emitted, not that the next planner runs under a lower budget or cheaper method.

**Recommendation:** treat both as integration gaps worth correcting before claiming the full B4/B6 control loops. Do not change the immutable execution-wide `ResourceBudget` to implement a downshift. Preserve it as the hard ceiling; apply a separate bounded adjustment to the next `PlannerBudget` and/or validated planner-method binding.

## 2. Method and boundaries

The review followed the actual path through:

1. mechanism state and service transitions;
2. lifecycle service and graph state updates;
3. graph routing after B4/B6 decisions;
4. request construction and planner invocation;
5. persistence adapters and test coverage.

This is a source-level audit of the uploaded archive. It does not claim a new Maven run. The owner reports that this archive is the latest green-CI version. Source parsing and the two focused static guards were completed during Phase 1; Phase 2 did not alter source.

### Paper boundary

Fan and Lan's B4/B6 bundles are proposed mechanism-level hypotheses and analysis categories, not mandatory features for every CoALA agent. The correct question for this project is whether its own claims of B4/B6 realization are supported by the actual state, trigger, control authority, persistence, failure and learning/resource transitions. A partial adaptation is acceptable when described accurately.

## 3. B4 — persistent intention and method authority

### 3.1 What is genuinely implemented

**State and invariants**

- `RecoveryIntention` stores objective identity/fingerprint, protected constraints, current `RecoveryMethodReference`, status, version and lifecycle timestamps.
- `requestReconsideration` is required before `changeMethod`.
- `changeMethod` checks the supplied objective fingerprint against the protected fingerprint and returns the intention to `ACTIVE` with a version increment.
- Completion requires a time-valid `ObjectiveSatisfactionProof` for the same objective fingerprint. A method change cannot itself mark the objective complete.
- `RecoveryIntentionService` performs repository-backed versioned updates.

**Persistence**

`PostgresRecoveryIntentionRepository` persists the JSONB intention payload, stores a separate objective fingerprint/version/status header, performs compare-and-set updates, and appends immutable lifecycle history in the same transaction. The M8 reliability integration test replaces the repository/service instance, reloads the intention and demonstrates that its current method and objective fingerprint survive the replacement.

**Lifecycle integration**

`CognitiveCycleLifecycleServices.governIntention` validates the current intention version, asks the trusted lifecycle domain port for a directive, and applies `RECONSIDER`, `CHANGE_METHOD`, `SUSPEND`, `TERMINATE`, or continuation semantics through the intention service. This is more than a log entry.

### 3.2 Where the control path stops

The method-change result does not flow into M5 as an enforced choice.

1. `CognitiveCycleLifecycleServices.governIntention` calls `intentions.changeMethod(...)`, but returns `IntentionGovernance(action, intentionId, intentionVersion, reason)`. The updated `currentMethod` is not part of that result.
2. `CognitiveCycleGraph` records the action, intention ID and version. For `RECONSIDER` or `CHANGE_METHOD`, it routes to `load-context` and starts the decision loop again.
3. `CognitiveCycleState` and `CycleContext` carry the intention ID/version but no method binding. That is sufficient to let a host look up the durable intention, but no core contract requires the planner request adapter to do so.
4. `CognitiveCycleDecisionPort.planningRequest(context, at)` has no method-resolution result or method-authority field in its contract.
5. `PlanningRequest` carries objective, world, working memory, evidence, scoring/risk/authority policy, complexity, budget and uncertainty reference—but no current intention method or validated method binding.
6. `CognitiveCycleDecisionServices.proposeCandidates` invokes `plannerRouter.plan(request)`, which chooses through the `PlannerSelectionPolicy`. It does not retrieve the intention's `currentMethod` or call `planUsing`.
7. `PlannerRouter.planUsing` exists and is independently unit-tested, but the runtime source has no call from the B4 lifecycle to this override path.

There is a second contract weakness: `RecoveryMethodReference.methodId` and `candidatePlanReference` are arbitrary nonblank strings. No registry resolves a method ID to a registered execution method/planning strategy, validates its version, or binds the candidate reference to the protected objective and current plan. A B4 method should not automatically be equated with `PlannerId`; the application may define a method that maps to a planner, a planning profile, or a domain-specific method. The missing part is a typed and validated binding, not necessarily a one-to-one enum conversion.

### 3.3 Test evidence and limitation

Positive evidence:

- `CognitiveCycleLifecycleServicesTest.b4LifecycleAcceptsMethodChangeOnlyAfterExplicitReconsiderationAndPreservesObjective` verifies lifecycle transition, versioning and objective/constraint preservation.
- Negative tests reject method change before reconsideration, a mismatched objective fingerprint and stale intention state.
- `M8B4ReliabilityIntegrationTest.persistentIntentionResumesAfterRepositoryAndServiceReplacementWithoutObjectiveDrift` verifies PostgreSQL persistence/reload and a subsequent method change.
- `PlannerRouterTest.explicitPlannerOverrideIsVisibleInResult` verifies the router's override API by itself.

Missing integration evidence:

- In `M8B4FullGraphWorkflowTest`, the full-graph `LifecycleAdapter.intentionDirective` always returns `CONTINUE`.
- The full-graph `DecisionAdapter.planningRequest` rebuilds the request from the fixture and does not consume the current intention method.
- There is no end-to-end test that changes the intention method, resumes the graph and asserts that the registered replacement method/planner is actually invoked while the objective fingerprint remains unchanged.

### 3.4 B4 assessment

**Assessment: partial/adapted realization.** The project has durable intention state, explicit reconsideration and protected-objective semantics. It has not yet demonstrated B4's central method-authority effect on future planning. It is accurate to claim persistent intention governance; it is premature to claim that a B4 method change controls the subsequent planning method.

## 4. B6 — uncertainty, resources, interruption and stopping

### 4.1 What is genuinely implemented

**Uncertainty state**

- `UncertaintyAssessment` is immutable and scoped to an execution and memory scope, has a versioned reference and validity interval, and requires evidence for every uncertainty dimension.
- Calibration is explicit. `NOT_CALIBRATED` is a first-class state; a numerical value in [0,1] is not automatically represented as a calibrated probability.
- `CognitiveCycleLifecycleServices` checks the exact assessment revision referenced by graph state before meta-control.

**Resource authority**

- `ResourceBudget` is immutable and defines execution-wide resource ceilings and a deadline.
- The PostgreSQL ledger locks the budget/account, persists reservations/usage and implements the leased/fenced reservation lifecycle.
- `ResourceBudgetExecutionGuard` commits the operation-start state before invocation, renews leases, conservatively accounts uncertain started work, and refuses duplicate invocation when a reservation has already committed or been reconciled.
- `CognitiveCycleServiceOperations` requires the exact same guard instance in decision and lifecycle services. `CognitiveCycleApplicationGraph` has a production builder that requires the shared guard across M5, M3 and B6.
- `CognitiveCycleDecisionServices.proposeCandidates` reserves the request's declared planner limits before invoking M5 and commits the planner's reported actual expansions/evaluations/model calls/rollouts afterward.

**Meta-control policy**

`MetaControlGovernor` evaluates over-budget state, deadline, declared next-step resource requirements, uncertainty thresholds and the low-resource remaining fraction. It can stop, interrupt for human control, request evidence, reduce computation or continue. The graph has explicit routing for stop and human-interruption outcomes.

### 4.2 What `REDUCE_COMPUTATION` currently does

The governor's low-margin branch returns a `MetaControlDecision` containing `action=REDUCE_COMPUTATION`, reason, explanation and maximum uncertainty. It does not return a reduced `PlannerBudget`, selected lower-cost planner, resource vector, or bounded downshift policy.

The lifecycle calls `domain.recordMetaControlDecision(context, inputs, decision, at)`. This is declared `void`. Its Javadoc says the host records the decision and any budget reduction, but the contract does not return an authoritative adjustment reference or require an adjustment to be applied.

The graph stores the action as `DECISION_STATUS`, maximum uncertainty and explanation, then routes `REDUCE_COMPUTATION` back to `propose`. `CycleContext` does not carry a typed downshift directive. `CognitiveCycleDecisionPort.planningRequest(context, at)` is not required to consume a B6 adjustment. The decision service then takes the budget from the newly returned request and plans normally.

A host could implement an external side channel in `recordMetaControlDecision` and make its later `planningRequest` return a reduced `PlannerBudget`. The current core contract does not require or validate that behavior, however, and the green full-graph evaluation implementation does not do it:

- `FullGraphEvaluationHost` creates in-memory working, episodic, semantic, procedural, workspace, planning-artifact, intention and action-ledger stores; it is an evaluation host, not a durable production deployment composition.
- The evaluation `LifecycleAdapter.metaControlInputs` supplies an empty `requiredForNextStep` resource vector.
- Its `recordMetaControlDecision` implementation is empty.
- The evaluation `DecisionAdapter.planningRequest` copies the fixture's original budget.

Consequently, the full-graph evaluation host does not exercise a low-resource downshift. The unit test `MetaControlGovernorTest.lowResourceMarginSelectsReducedComputation` proves only that the governor emits the action. The graph tests cover `STOP` and human interruption, but do not assert the REDUCE → smaller subsequent planning request → smaller planner invocation chain.

### 4.3 Important architectural distinction: hard ceiling vs planning quota

Do not solve this by mutating the execution-wide `ResourceBudget` in the durable ledger. The ledger deliberately binds one execution to one immutable budget; changing it would break the current safety/accounting model.

The correct extension point is a separate, bounded **per-planning downshift** applied to `PlannerBudget` and/or planner selection:

- The original `ResourceBudget` remains the absolute ceiling across M5/M3/B6.
- The downshift can reduce `maxExpansions`, `maxEvaluations`, `maxModelCalls`, `maxRollouts` and possibly `maxRuntime` for the next planning request.
- A validated planner override can select a lower-cost registered method when the policy requires more than reduced quotas.
- The existing `PlannerExecutionContext` already reads these `PlannerBudget` fields to stop planner work at the declared per-request limits.
- `PlanningRequestFingerprint` intentionally excludes consumable resource ceilings but retains structural bounds such as depth and result cardinality. Thus resource quotas can be reduced/replenished without silently changing the planner-session identity, while structural changes remain fingerprint-significant.

The implementation still needs a durable, typed, single-use/round-bound downshift record and a check that the request actually applies it. The exact reduction factor/strategy is an engineering policy and should be evaluated; it is not prescribed numerically by the papers.

### 4.4 Test evidence and limitation

Positive evidence:

- `MetaControlGovernorTest.lowResourceMarginSelectsReducedComputation` demonstrates the low-resource branch.
- `CognitiveCycleLifecycleServicesTest.b6MetaControlUsesTheSharedDurableLedgerSnapshotRatherThanHostSuppliedCounters` demonstrates that, when B6 is wired, meta-control reads the durable budget snapshot rather than stale host counters.
- `CognitiveCycleGraphTest.metaControlStopNeverReachesAuthorizationOrExecution` proves stop authority is respected.
- `CognitiveCycleGraphTest.b6HumanControlInterruptRequiresAHostIssuedResponse` verifies a real interruption/resume path and refreshed uncertainty revision.
- The R1-DEF budget lease/recovery integration tests verify durable accounting and recovery boundaries.

Missing integration evidence:

- No full-graph test asserts `REDUCE_COMPUTATION` leads to a strictly lower next `PlannerBudget` and a corresponding bounded planner call.
- No test asserts the next planner selection is a lower-cost registered strategy when the policy requires an algorithm change.
- The evaluation host's `requiredForNextStep` is empty and its decision-recording callback is a no-op, so its successful full-graph test cannot be cited as evidence for downshift behavior.

### 4.5 B6 assessment

**Assessment: strong resource-accounting and interruption foundation; partial adaptive resource-control realization.** Hard shared accounting, durable reservations and stop/interruption semantics are real. The adaptive downshift decision exists, but its application to the next planning operation is not a guaranteed or tested control transition. Uncertainty remains explicitly uncalibrated unless a separate validation profile is supplied.

## 5. Cross-cutting full-graph findings

| Boundary | Verified behavior | Limitation |
|---|---|---|
| B4 lifecycle → graph | Intention state transitions are persisted and graph checkpoints retain ID/version. | The changed method is not returned as an authoritative planning binding. |
| B4 graph → M5 | `CHANGE_METHOD` restarts the decision path through context load. | M5 has no required method-resolution contract; the standard route is still `PlannerRouter.plan`. |
| B6 governor → graph | The decision is recorded as graph status/reason and routing occurs. | Only a string status/reason survives; no typed downshift adjustment is propagated. |
| B6 graph → M5 | REDUCE routes back to proposal. | The next request is not required to carry reduced quotas or a selected lower-cost method. |
| M5 → durable resource guard | Declared planner ceilings are reserved and actual use is committed. | This enforces an immutable global cap; it does not itself choose a lower per-request cap. |
| Evaluation host | Exercises the complete graph and R2 paths. | Uses in-memory reference stores and no-op B4/B6 host decisions for these branches; not a production durable composition. |

## 6. Prioritized correction design

No code change is made in this phase. The following is the recommended implementation design if these findings are accepted.

### F-01 — B4 method binding and planner effect (high)

1. Define a trusted, versioned method registry. Do not treat arbitrary strings as executable methods. A registry entry should resolve a `RecoveryMethodReference` to a specific planner strategy/profile or domain method implementation, with scope/objective compatibility and an immutable version/fingerprint.
2. Make the active method observable at the planning boundary. One option is to have the authoritative intention loader return the current method reference with its intention ID/version; another is a decision-side service that resolves the exact intention repository record using `CycleContext.intentionId` and checks the version. Avoid a second source of truth.
3. Add a typed planner-method binding to the planning request or an explicit planning directive. `CognitiveCycleDecisionServices` should validate the binding and invoke `PlannerRouter.planUsing` (or a registered method orchestrator) when a B4 method override is active; otherwise use the ordinary selection policy.
4. Persist the selected method/strategy and its intention version in the planning artifact/result trace. Ensure stale method revisions cannot be used after reconsideration.
5. Add one full-graph test that performs `RECONSIDER` → `CHANGE_METHOD` → reload → planning; asserts the replacement method actually runs and objective identity/fingerprint/hard constraints remain unchanged. Include an invalid/unregistered method rejection test and a stale-version test.

**Acceptance:** a B4 method change can be shown to cause an observable, validated change in the next planning method without changing the committed objective.

### F-02 — B6 applied planning downshift (high)

1. Add a typed `PlanningDownshiftDirective` or equivalent policy record with execution ID, source meta-control decision/assessment reference, applicable planning round, bounded resource reductions, optional validated lower-cost planner/method, reason and version/idempotency key.
2. Have B6's host/lifecycle boundary return or persist an authoritative directive reference. Replace the ambiguous `record...`-only seam with an explicit apply/resolve contract; keep an audit event as well.
3. Propagate the directive reference through graph state/context or resolve it from a shared durable store. It must be scoped to the same execution and consumed only by the intended next planning operation.
4. Before planner invocation, apply the directive to the baseline `PlannerBudget`, validate that no limit increases, and record both original and effective budgets. Keep the immutable execution `ResourceBudget` as the hard cap.
5. Use the existing `PlannerExecutionContext` and shared `ResourceBudgetExecutionGuard` for per-request and global enforcement. If an algorithm change is requested, resolve it through the same trusted registry principle as F-01.
6. Add a full-graph test with a nonempty `requiredForNextStep`, low remaining margin and a `REDUCE_COMPUTATION` decision. Assert the next request has strictly lower applicable quotas, the chosen planner/strategy matches policy, actual execution obeys both limits, and checkpoint replay does not apply the reduction twice. Add a counter-test where the next-step resource vector is empty and no downshift is emitted.

**Acceptance:** the graph cannot report `REDUCE_COMPUTATION` and then silently invoke the same planner with the same planning budget.

### F-03 — Full-graph evidence for the two control loops (medium)

Extend the evaluation host with two explicit scenarios, rather than changing every ordinary fixture:

- a B4 fixture whose trusted directive changes method after authoritative evidence invalidates the current method;
- a B6 fixture whose remaining resource margin crosses the threshold and whose next-step demand is non-empty.

These fixtures should expose the active method, original/effective planning budgets, planner identity, method/directive version, actual usage, objective fingerprint and meta-control decision in the evaluation trace. Preserve the evaluation host's in-memory status in documentation; the tests are integration evidence, not production persistence evidence.

## 7. Proposed book claims after Phase 2

| Topic | Defensible claim today | Claim to avoid until F-01/F-02 are closed |
|---|---|---|
| B4 | The code persists a versioned intention and enforces reconsideration before method replacement while protecting objective identity/constraints. | “A B4 method change switches the planner/method used by the next reasoning cycle.” |
| B6 | The code integrates uncertainty-aware meta-control with a shared durable hard resource guard, interruption and stop paths. | “A REDUCE_COMPUTATION decision automatically reduces the next planning budget or changes to a cheaper planner.” |
| Combined B4/B6 | The services are composed in the full cognitive-cycle graph and have distinct, testable lifecycle boundaries. | “All B4/B6 control actions are end-to-end enforced across subsequent planning.” |

The project should retain the distinction between paper mechanisms, this application's adaptation, and engineering additions. It need not implement every historical architecture detail or every possible bundle in order to be a useful educational reference.

## 8. Phase 2 disposition

- **Completed:** targeted source inspection of the B4/B6 mechanism models/services, graph state/routing, M5 planning request/router, budget enforcement, PostgreSQL intention/budget adapters, evaluation host and relevant tests.
- **Confirmed:** F-01 (B4 method-to-planner control link) and F-02 (B6 downshift-to-next-planning enforcement) remain valid integration gaps.
- **Positive evidence retained:** objective fingerprint protection, intention CAS/history persistence, shared resource guard identity, leased/fenced accounting, uncertainty-version checks, and planner per-request budget enforcement.
- **Not done:** no source modifications, no new Maven test run, no migration change, no CI claim beyond the user's stated green archive.
- **Next audit phase:** Phase 3 — B1/B2/B3 memory, evidence, recovery-draft, consumer-receipt, outcome-feedback and procedural-memory lifecycle integration.
