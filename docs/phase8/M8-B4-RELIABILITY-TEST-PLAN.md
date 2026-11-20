# M8-B4 — End-to-end reliability and safety evaluation

**Status:** In progress. B4-A and B4-C PostgreSQL suites passed in the latest user CI run. B4-B ran ten full-graph methods, eight passed and two exposed deterministic fixture defects; the fixture corrections are included in the latest patch and require another full CI run. This document is the controlled B4 test plan; the current delivery does not claim the complete B4 acceptance criteria have been met.

## Objective

Validate that the accepted supply-chain application preserves business objectives, authority boundaries, durable execution state and resource-governance invariants across full workflows, process replacement, interruption, external ambiguity, concurrency and dependency failure.

M8-B4 evaluates the application using deterministic domain fixtures and strict adapters. It does not assume production ERP, WMS, carrier, supplier or identity integrations. External action ports remain closed unless a scenario explicitly installs a test handler.

## B4 execution slices

| Slice | Scope | Delivery evidence |
|---|---|---|
| B4-A | Durable B6 computational-resource budget ledger; intention/approval/action-ledger restart probes; additive PostgreSQL migration V009 | `PostgresResourceBudgetLedger`, `M8B4ReliabilityIntegrationTest`, migration parser test; one budget-release expectation corrected from 2 to 4 after the first user CI run |
| B4-B | Full graph request-to-terminal workflows, proposal/approved-write/denied paths, approval revalidation, observation invalidation and safe impasse | `M8B4FullGraphWorkflowTest` (10 methods, 12 fixture executions across 11 distinct scenario IDs) using `FullGraphEvaluationHost.compileFull(Ports)` and narrowly registered deterministic inventory-reservation action handler |
| B4-C | Graph-host replacement, durable resume, timeout/unknown outcome reconciliation, safe retry after authoritative NOT_APPLIED, duplicate action dispatch and concurrent CAS/idempotency | `M8B4CRestartReconciliationIntegrationTest` (4 PostgreSQL-gated methods); a new pool/graph/adapters reload durable checkpoint and business state |
| B4-D | Hostile/untrusted evidence, malformed provider output, authority confusion, budget exhaustion, PostgreSQL/provider outages and regression matrix | Negative/security tests and auditable structured results |

B4-A, B4-B and B4-C are implementation-complete in source but remain pending user CI acceptance. B4-D remains open.

## Critical scenario matrix

| ID | Scenario | Required observation / invariant | B4 slice |
|---|---|---|---|
| E2E-01 | Supplier delay/outage | Replan only against current supplier evidence and capacity; do not silently relax commitments | B4-B |
| E2E-02 | Conflicting inventory evidence | Preserve provenance; reconcile authoritative state before a write | B4-B/B4-D |
| E2E-03 | Route blocked | Preserve shipment/route evidence and require host approval before any write; alternative-route execution is not covered by this fixture adapter | B4-B (evidence/gating only; reroute deferred) |
| E2E-04 | Quality failure after allocation | Reject stale/invalid lot allocations; no dispatch using quarantined stock | B4-B |
| E2E-05 | Demand change after plan approval | Old approval must not authorize the changed plan | B4-B/B4-C |
| E2E-06 | Competing disruptions share stock/capacity | Atomic authoritative reservation; no double allocation or lost update | B4-C |
| E2E-07 | Previously feasible plan becomes infeasible | Preserve objective/constraints and replan or stop safely | B4-B |
| E2E-08 | Provider times out after possible commit | Persist UNKNOWN outcome; reconcile before retry; no duplicate external effect | B4-A/B4-C |
| E2E-09 | Approval denied, expired, revoked or stale | Zero external dispatch | B4-A/B4-C |
| E2E-10 | Process stops while intention is suspended | Replacement graph host reloads intention, preserves objective fingerprint and resumes only after revalidation; OS process kill/relaunch remains unclaimed | B4-A/B4-C |
| E2E-11 | Computational budget exhausted | Stop/interrupt according to B6 policy; no unaccounted subsequent computation | B4-A/B4-D |
| E2E-12 | No feasible candidate / insufficient evidence | Typed impasse, evidence request or safe stop; never fabricate feasibility | B4-B/B4-D |
| E2E-13 | PostgreSQL unavailable during resume/approval check | Fail closed; do not dispatch using stale in-memory authority | B4-D |
| E2E-14 | Duplicate event/outbox delivery | Idempotent processing and at-most-one business effect; B4-C verifies duplicate action dispatch, while inbox/outbox envelope redelivery remains B4-D | B4-C/B4-D |
| E2E-15 | Malicious supplier text or malformed model output | Treat as untrusted data; it cannot change instructions, identity, policy or authority | B4-D |
| E2E-16 | Two runtime hosts reserve the same B6 budget | Transactional serialization prevents oversubscription | B4-A |

The scenario IDs are evaluation work items and are not claims that all are implemented in B4-A.

## Hard acceptance invariants

The following are zero-tolerance test assertions over the controlled scenarios:

- Hard supply-chain constraint violations: **0**.
- Unauthorized state-changing dispatches: **0**.
- Dispatches with denied, expired, revoked, stale or mismatched approval: **0**.
- Duplicate external/business effects after ambiguous outcomes or replay: **0**.
- Double allocation of inventory or constrained supplier capacity: **0**.
- Lost updates under concurrent reservation: **0**.
- Unauthorized objective or protected-constraint changes: **0**.
- Incorrect intention or graph resume after restart: **0**.

A negative result is a test failure, not an average that can be offset by successful cases.

## B4-A implementation notes

### Durable B6 resource-budget authority

`PostgresResourceBudgetLedger` implements the framework-neutral `ResourceBudgetLedger` contract. It stores execution deadline, configured per-unit limits, active/released/committed reservation identity, reservation estimates, actual consumption and execution scope.

Every mutating operation locks the `(scope_id, execution_id)` account row with `FOR UPDATE`. Concurrent reservations against the same execution therefore serialize before available capacity is calculated. This is the authority boundary for B6 computational resources. Inventory, money, supplier capacity and other domain resources remain in their domain adapters and are not represented by this ledger.

Committed actual consumption is recorded even when it exceeds the estimate or configured limit. Such overruns remain visible and prevent subsequent work from reusing already-consumed resources. Replaying the same commit is idempotent; charging a different actual amount under the same reservation ID is rejected.

The adapter is exposed from `PostgresRuntimePersistence.resourceBudgets()`. M8-B4 adds the forward-only PostgreSQL migration `V009__durable_b6_resource_budget_ledger.sql`. Existing M7 migrations V001-V008 are unchanged; this is an additive M8 reliability capability, not a reopening of M7. The isolated backup/restore verifier now requires a contiguous V001-V009 history and checks the B6 budget tables as well as the existing runtime tables.

### Restart and authority probes

B4-A integration tests replace repository/dispatcher instances and use stateless JSON test codecs for intention and action payloads. They check objective fingerprint and protected constraints through intention resume/method reconsideration, exact approval binding and revocation/expiry, and no blind redispatch after an ambiguous external outcome. The provider handler increments a controlled effect counter before returning an ambiguous failure; the replacement dispatcher must not invoke it again. A reconciled completion must return the recorded receipt.

These are adapter-level PostgreSQL restart probes; B4-C adds a focused LangGraph4j graph-host replacement workflow with a new checkpoint saver, pool and business adapters.

## B4-B full-graph workflow evidence and boundaries

The B4-B workflow suite invokes the actual `CognitiveCycleApplicationGraph.compileFull(...)` composition through `FullGraphEvaluationHost.compileFull(Ports)`. The graph still uses the real B1 retrieval/selection, B3 admission/broadcast, planner router and common deterministic evaluator, B2 impasse service, B4 intention service, B6 governor and M3 action dispatcher. Scenario adapters are deterministic, in-memory and scoped to the disposable test; the action registry only contains explicit inventory-reservation handlers for the two fixture lot IDs.

The ten test methods execute twelve fixture runs across eleven distinct scenario IDs (REQ-08 is deliberately executed twice with different invalidation modes). Coverage includes: exact action-plan approval binding and successful observation; supplier-delay proposal with denied approval; quality-held lot exclusion with only qualified partial stock reserved and no false objective completion; blocked-route/shipment evidence preserved without a write; conflicting inventory observations kept as distinct references and denied without a write; approval denied and revoked immediately before dispatch; changed trusted action definition causing stale-plan rejection and a second approval; demand change causing fresh-world replanning and a second approval; post-approval quality observation invalidating all candidate actions and forcing replan/impasse/escalation; read-only and no-feasible scenarios terminating without a state-changing dispatch. See `M8-B4-B-WORKFLOW-MATRIX.csv` for test-to-fixture mapping.

The first complete CI execution of this suite reported ten methods, eight passing and two errors. The demand-change fixture now replaces the existing customer-order entity rather than trying to insert a duplicate ID. The trusted-action-definition fixture now updates both the M5 trusted descriptor and its M3 registered handler descriptor under the same action ID, preserving the runtime security check that rejects mismatched definitions. These are test-fixture corrections; production validation remains strict. The PostgreSQL B4-A and B4-C suites passed in that same CI execution. The full reactor must be rerun before B4-A/B/C acceptance.

This slice intentionally does not claim more than its executable adapters prove:

- `REQ-10` read-only has no typed success terminal in the current graph. The expected safe result is escalation with zero writes, not a completed read-only assessment.
- `REQ-04` verifies route/shipment evidence provenance and proposal/approval gating. It does not test an alternative route action because the fixture world-model adapter currently exposes inventory reservation actions only.
- `REQ-03` verifies preservation of both stale and current inventory evidence references. It does not claim that the graph reconciles those references to a new authoritative inventory fact.
- `REQ-05` may reserve only currently qualified secondary stock under an explicit approval; the objective remains unfulfilled and no successful outcome episode is recorded.
- These tests use in-memory intention, checkpoint, action-batch and observation adapters; B4-B itself is not a process-restart test.
- B6 receives a deterministic snapshot in the B4-B test port. The PostgreSQL B6 ledger is not automatically wired into production lifecycle reserve/commit calls by B4-B; B4-C explicitly exercises its register/reserve/commit contract across host replacement without claiming automatic full-application lifecycle wiring.


## B4-C graph-host replacement and external reconciliation

The focused `M8B4CRestartReconciliationIntegrationTest` uses a real PostgreSQL LangGraph4j saver and the production PostgreSQL intention, approval, B6 budget and M3 operation-ledger adapters. The first graph host stops at explicit approval and reconciliation boundaries; its Hikari pool is closed. A replacement host creates a new pool, graph, saver and repositories, reloads the persisted checkpoint and business records, and resumes.

The main workflow simulates a provider commit followed by a lost response. The replacement host must observe the persisted M3 `UNKNOWN_OUTCOME`, must not dispatch again, must match the external provider's authoritative operation ID and input, and may then record a completed receipt. Replaying after reconciliation must return `ALREADY_COMPLETED`; the provider effect count remains one; the B6 reservation is committed exactly once.

Three further tests cover: a retry permitted only after a provider confirms NOT_APPLIED and that fact is persisted; two dispatchers using separate PostgreSQL pools racing one idempotency key with one external effect; and two intention repositories using separate pools racing a version CAS while preserving objective fingerprint/protected constraints.

The safe-retry flow exposed and fixes an M3 JDBC adapter defect: `PostgresActionExecutionLedger.inspect` now returns empty for `FAILED + retryable + !outcome_unknown`, matching `ActionExecutionLedger` and `InMemoryActionExecutionLedger`; `UNKNOWN_OUTCOME` remains visible and blocks retry. This is a scoped contract correction in the adapter, not a change to M7 schema/migrations.

**Evidence limits:** the graph-host replacement occurs inside one JUnit process with the first Hikari pool closed and a new graph/adapter set constructed. It is not an OS-level process kill/relaunch. The provider state is a deterministic remote-system test double, not a real ERP/WMS/carrier integration. The focused B4-C graph is not `CognitiveCycleApplicationGraph.compileFull`; B4-B already covers the full application graph using deterministic adapters. Automatic production B6 register/reserve/commit wiring remains open. These constraints are tracked for B4-D/B5 and must appear in final reporting.

## Result reporting

Each reliability scenario should record:

- scenario ID and fixture version/fingerprint;
- initial state/evidence and scope;
- runtime/host identity and restart boundary;
- operation, intention, approval and reservation identifiers (non-secret);
- expected versus observed terminal state;
- external-effect count and reconciliation result;
- resource budget/actual usage where applicable;
- test outcome and structured failure classification.

Never capture private chain-of-thought. Record the admitted evidence references, structured decisions, constraints, authority outcomes and action reasons instead.

## Out of scope

- Reopening M7 or changing V001-V008.
- Building production ERP/WMS/TMS/carrier or identity integrations.
- Treating the LangGraph4j checkpoint as authoritative proof of external business completion.
- Treating B6 computational budgets as inventory, cash or supplier-capacity ledgers.
- Claiming B1/B3 outcome-attributed learning, B5/GraSP implementation, or full B1+B2+B3+B4+B6 composition.
- Claiming end-to-end reliability from isolated adapter tests alone.
