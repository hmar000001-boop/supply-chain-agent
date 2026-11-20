# M8-B4-D — Adversarial, fault-injection and B6 lifecycle verification

**Status:** Implemented in source; GitHub Actions CI validation pending. This slice is based on the user-confirmed green M8-B4-A/B/C repository. It does not reopen M7.

## Objective

Close the B4-D reliability and safety evidence gaps identified after B4-A/B/C: an actual JVM process boundary, shared durable B6 accounting across M5 planning and M3 reasoning, untrusted supplier-content handling, malformed model-output rejection, fail-closed dependency handling and explicit composition/authority checks.

## D1 — Independent JVM process replacement

`M8B4ForkedJvmProcessRestartIntegrationTest` starts two distinct child JVMs. The first JVM migrates PostgreSQL, writes an intention, exact approval binding and B6 reservation, checkpoints a LangGraph4j workflow, receives approval, executes a deterministic external-write test double and exits with the M3 operation in `UNKNOWN_OUTCOME`. The second JVM starts with a fresh class loader/process, creates a new pool, graph, saver and repository set, reloads the checkpoint/intention/approval/budget/M3 operation, blocks replay, verifies the external marker against the persisted operation ID, reconciles the provider-confirmed outcome and settles the persisted budget reservation.

**Evidence boundary:** this is an actual OS/JVM process boundary over a small LangGraph4j test graph using the real PostgreSQL checkpoint saver and persistence adapters. It is not a forked execution of the entire `CognitiveCycleApplicationGraph`, and the external provider is a file-backed deterministic test double, not a live ERP/WMS/carrier.

## D2 — Shared durable B6 accounting at M5/M3/B6 boundaries

`ResourceBudgetExecutionGuard` is the shared execution boundary. Hosts supply one instance backed by `PostgresRuntimePersistence.resourceBudgets()` and the immutable budget definition for each execution.

- M5 `CognitiveCycleDecisionServices.proposeCandidates` reserves its declared expansion/evaluation/model/rollout ceiling before invoking the selected planner and commits the planner's measured `PlanningStatistics` afterwards. A refused reservation becomes a typed `RESOURCE_BUDGET_EXHAUSTED` planner result; a ledger/database failure is not converted into an unbudgeted fallback.
- M3 `BudgetedReasoningProvider` reserves one model call and one reasoning evaluation before provider invocation. On a successful response it commits the measured fixed call counts. If invocation starts and fails before a trusted measurement is returned, it conservatively charges the reservation. A budget-store failure blocks the provider invocation.
- B6 lifecycle meta-control reads the same guard's durable snapshot and replaces caller-supplied consumption/reservation counters before evaluating the next step. A mismatched immutable budget definition is rejected.
- `CognitiveCycleServiceOperations` requires M5 decision services and lifecycle services to share the exact same guard instance. If the M3 registry contains `ReasoningAction`, lifecycle composition requires it to use that same guard.
- `FullGraphEvaluationHost.Ports` accepts an optional guard for full-graph scenarios. Production composition must wire it explicitly; compatibility constructors with no guard remain available for non-production/reference hosts.

**Accounting boundary:** model token counts are not guessed; the current provider contract exposes invocation/evaluation counts only. The B6 ledger governs computational resources, not stock, money, supplier capacity or external business-operation idempotency.

**Crash boundary:** a hard process kill in the middle of a guarded call can leave its reservation active. The ledger fails closed and retains capacity; this slice does not implement automatic reclamation of ambiguous orphan reservations. Such a reservation must be reconciled by an operator/host policy rather than released speculatively. M3's business-operation ledger remains separately authoritative for external side effects.

## D3 — Adversarial supplier evidence

`LangChain4jReasoningProvider` JSON-encodes caller-controlled task/context values. Quotes, backslashes, newlines, control characters and role-like text remain inside JSON string values. The AI service system message explicitly treats payload values and evidence labels as untrusted data, disallows tool/credential/authority requests, and requires findings to cite only caller-supplied evidence keys. The reasoning action remains `READ_ONLY` and requires only `REASONING_INVOKE`; it cannot create action handlers or dispatch a purchase/order action.

Tests cover a supplier message that attempts to override instructions, embed a fake system role and request an unauthorized purchase, plus a fabricated evidence key.

## D4 — Malformed or adversarial model output

The typed provider boundary rejects blank required text, invented evidence references and output exceeding the `ReasoningAssessment` item/length limits. Null list fields normalize to empty lists. Missing or unknown confidence labels degrade to `UNASSESSED`; they never become approval or certainty. The reasoning action independently verifies that all citations are members of the submitted request.

## D5 — Provider and persistence dependency failures

- A provider timeout after invocation begins returns a retryable read-only failure and conservatively charges the B6 reservation.
- A closed/unavailable PostgreSQL budget store blocks M3 reasoning before invoking the model provider; no in-memory budget fallback is used.
- Unknown external write outcomes continue to use the M3 reconciliation path from B4-A/B4-C; they are not converted into safe retries without authoritative `NOT_APPLIED` evidence.

## D6 — Authority and composition regression

Tests verify that a configured B6 guard rejects an unbudgeted registered `ReasoningAction` both at lifecycle construction and if a mutable startup registry receives a later unbudgeted registration before authorization; the complete operations composition also rejects distinct guard instances between M5 planning and B6/M3 lifecycle services. Existing B4-B/C checks continue to cover exact approval bindings, denial/revocation, action-definition drift, duplicate effects, intention CAS and unknown-outcome replay suppression.

## Acceptance evidence and limits

B4-D acceptance requires the full Java 21 Maven/PostgreSQL workflow to pass. The added forked-JVM and PostgreSQL budget tests are conditionally enabled by `-Dphase0.postgres=true`. The authoring environment does not have Maven 3.9.16 cached and cannot resolve the Maven distribution host, so no Maven/JUnit execution is claimed here.

The following remain outside this slice and must stay explicit in M8-B5/final documentation: live ERP/WMS/carrier/provider integrations; full OS-process restart of the entire application graph; automatic recovery/reclamation of an orphan B6 reservation after a hard process kill; provider token-level accounting; and deployment credentials, identity integration, PITR, tenant reconciliation and retention operations.
