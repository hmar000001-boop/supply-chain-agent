# M8-B4 status — End-to-end reliability and safety evaluation

**Overall status:** B4-A, B4-B, B4-C and B4-D are accepted. The user confirmed the complete GitHub Actions CI workflow is green on 2026-11-20 after the latest-repository correction. This is user-reported hosted CI evidence; no local Maven execution is claimed by the authoring environment.

## Baseline

- **B4-A/B/C baseline:** user-uploaded `supply-chain-agent-main (3).zip`, accepted M8-B3 repository after user-confirmed green CI.
- **B4-A/B/C source ZIP SHA-256:** `84cc3ef2482235092189ed89a43752166de44afbef201836611dd29bf0c833f2`.
- **B4-D latest-repository correction:** based on the user's subsequently uploaded `supply-chain-agent-main (4).zip`; the latest correction was accepted after the user confirmed complete CI green on 2026-11-20. The B4-D correction changes only the nested type references and the active CI diagnostic/workflow source; it does not change the D1-D6 behavior.
- **Git metadata:** the supplied archive does not provide Git history; source identity is recorded by the uploaded archive hash and per-file manifests.
- **M7:** remains frozen and accepted. M8 adds only migration V009; M7 migrations V001-V008 are byte-for-byte unchanged.

## B4 slices

| Slice | Scope | Status |
|---|---|---|
| B4-A | Durable B6 computational budget store; PostgreSQL restart/authority/unknown-outcome probes; additive V009 | **Accepted — user confirmed cumulative CI green** |
| B4-B | Full-graph request-to-terminal workflows using deterministic simulator and strict adapters | **Accepted — user confirmed cumulative CI green** |
| B4-C | Graph-host replacement, durable resume, ambiguous external outcome reconciliation, safe retry after authoritative NOT_APPLIED, duplicate replay, independent-pool idempotency race and intention CAS | **Accepted — user confirmed cumulative CI green** |
| B4-D | Actual JVM process boundary, shared M5/M3/B6 budget guard, adversarial reasoning evidence, malformed model output, dependency failure and composition/security regression | **Accepted — user confirmed complete CI green 2026-11-20** |

## B4-A — Durable B6 budget persistence

- Added `PostgresResourceBudgetLedger` implementing `ResourceBudgetLedger`, scoped by execution and scope/tenant. Account-row locking serializes independent-host reservations so they cannot oversubscribe one execution budget.
- Persisted immutable limits/deadlines, reservations, actual consumption, reservation states and actual amounts. Actual usage overrun remains recorded; repeated commit is idempotent and contradictory reuse is rejected.
- Exposed the adapter through `PostgresRuntimePersistence.resourceBudgets()`.
- Added additive migration V009 with six B6 tables. V001-V008 remain untouched. Backup/restore verification now checks V009 and the six B6 tables; the workflow invokes PostgreSQL scripts through Bash and preserves executable modes.
- Added seven PostgreSQL reliability tests for budget persistence/concurrency/release/deadline, intention/objective restore, approval binding/revocation/expiry and unknown external outcome/reconciliation.
- Corrected the release assertion from two to four remaining units: releasing a two-unit reservation against a four-unit budget restores all four units.

## B4-B — Full cognitive-cycle workflows

`M8B4FullGraphWorkflowTest` drives the actual `CognitiveCycleApplicationGraph.compileFull(...)` through `FullGraphEvaluationHost.compileFull(Ports)`. Ten JUnit methods cover twelve fixture executions and eleven scenario IDs. Coverage includes an approved write and verified observation, proposal/denial, quality-held partial fulfilment without false completion, route and conflicting-evidence provenance, denied/revoked/stale approvals, action-definition drift, demand change and quality-observation replanning, and safe fail-closed paths.

The accepted CI fixes corrected test fixtures without weakening production safety: the demand scenario updates the existing customer order with `replaceCustomerOrder`, and the changed-action scenario updates both the trusted M5 definition and corresponding M3 test handler under the same action ID. Checked exceptions from LangGraph4j state updates are declared by their test helpers.

B4-B still uses deterministic test ports and explicit test handlers. It does not claim live supplier/carrier/ERP/WMS integration, a successful typed read-only terminal (the graph currently escalates/fails closed), route rerouting, or authoritative fact reconciliation beyond the evidence references explicitly asserted by the fixtures.

## B4-C — Host replacement and external-outcome reconciliation

`M8B4CRestartReconciliationIntegrationTest` adds four PostgreSQL-gated tests. The main scenario interrupts a LangGraph4j graph backed by the PostgreSQL checkpoint saver, closes the first Hikari pool, creates a replacement pool/graph/repository set, reloads checkpoint/intention/approval/B6/M3 state, blocks replay while M3 is `UNKNOWN_OUTCOME`, reconciles the provider-confirmed commit and verifies one effect and one budget settlement. Additional tests cover authoritative `NOT_APPLIED` followed by a single safe retry, independent-pool idempotency concurrency and intention-version CAS.

The safe-retry correction in `PostgresActionExecutionLedger.inspect(...)` keeps `UNKNOWN_OUTCOME` visible/non-retriable while making definitively failed, retryable and not-unknown records eligible for a fenced retry. Sparse budget snapshot maps are read with absent units treated as zero.

## B4-D — Adversarial, fault-injection and lifecycle work

### D1 — Independent JVM process restart

`M8B4ForkedJvmProcessRestartIntegrationTest` launches two distinct Java processes. Process 1 creates the persisted intention, approval, B6 reservation, PostgreSQL checkpoint and an M3 `UNKNOWN_OUTCOME` after a deterministic provider writes an external marker and loses its response. It exits. Process 2 builds new pools, graph, saver and repositories, restores the state, blocks redispatch, verifies the marker against the operation ID, reconciles the confirmed effect and settles the B6 reservation.

**Boundary:** this is an OS/JVM process boundary over a small LangGraph4j test graph using the real PostgreSQL saver and persistence adapters. It is not a forked execution of the entire production application graph. The remote provider is deterministic/file-backed, not live.

### D2 — Shared durable B6 guard at M5/M3/B6 boundaries

Added `ResourceBudgetExecutionGuard` as one host-owned identity shared by M5 planning, M3 reasoning and B6 meta-control:

- M5 reserves the request's declared search/evaluation/model/rollout ceiling before planner invocation and commits actual `PlanningStatistics`. A denied reservation creates a typed `RESOURCE_BUDGET_EXHAUSTED` planning result; an infrastructure failure does not trigger an unbudgeted fallback.
- M3 `BudgetedReasoningProvider` reserves one model call and one reasoning evaluation before invoking the provider. It commits fixed observable call counts, does not guess token usage, and conservatively charges the reservation if an invoked provider fails before a usage measurement is returned.
- B6 meta-control reads the durable snapshot through the same guard and rejects a different immutable budget definition.
- `CognitiveCycleServiceOperations` rejects distinct guard instances between decision and lifecycle services. If the M3 registry contains `ReasoningAction`, lifecycle composition requires it to use the exact same guard.
- `FullGraphEvaluationHost.Ports` accepts a shared guard for evaluation composition.
- PostgreSQL integration tests exercise M5-planning and M3-reasoning accounting against the same `PostgresResourceBudgetLedger`, and prove that a closed budget store blocks provider invocation.

**Boundary:** provider tokens are not counted because the current provider contract has no trusted token-usage metric. The B6 ledger accounts for computational resources, not inventory/money/business side effects. A hard process kill in the middle of a guarded call can leave an active reservation; it remains held conservatively. Automatic reclamation of such an orphan reservation is not implemented and must not be claimed.

### D3/D4 — Untrusted evidence and malformed structured output

Caller-controlled task/context data is JSON-string encoded before insertion into the reasoning prompt. System instructions explicitly treat those strings/evidence labels as untrusted data, forbid tool/credential/authority requests and limit citations to exact supplied evidence keys. `ReasoningAction` remains read-only and cannot add handlers or operational capabilities.

Typed output checks reject blank required fields, invented citation keys and oversized findings/assumptions/gaps. Missing or unknown confidence labels degrade to `UNASSESSED`, never approval or certainty. Tests cover prompt injection, fake role labels, fabricated citations, malformed/oversized assessments and confidence fallback.

### D5/D6 — Dependency failure and authority composition

Tests cover provider failure after invocation (conservative charge), PostgreSQL budget-store outage (fail closed before provider call), refusal to compose an unbudgeted reasoning action with B6 enabled, and refusal to compose M5/lifecycle services with different B6 guard identities. Existing B4-B/C tests continue to cover approval denial/revocation/staleness, duplicate effects, intention CAS and unknown-outcome replay suppression.

## Production composition requirement

A production host must obtain `ResourceBudgetLedger` from `PostgresRuntimePersistence.resourceBudgets()`, construct one `ResourceBudgetExecutionGuard` for the execution budget provider and pass that *same object* to `CognitiveCycleDecisionServices`, `CognitiveCycleLifecycleServices`, and `BudgetedReasoningProvider` used by the registered `ReasoningAction`. The current `Phase0Application` remains a deterministic intake smoke entry point; it is not a production `compileFull` deployment host. Hosts using legacy constructors with a null guard do not receive durable B6 enforcement.

## Acceptance evidence for B4-D

The user confirmed that the complete Java 21 Maven/PostgreSQL GitHub Actions workflow is green on 2026-11-20, after applying the correction against the latest uploaded repository. This is the acceptance gate for the test paths listed above. Preserve the evidence boundaries: the forked-JVM scenario uses a small LangGraph4j test graph and a deterministic marker-file provider; CI does not establish a live ERP/provider deployment or production PITR/identity/retention configuration. The local authoring environment did not execute Maven/JUnit.
