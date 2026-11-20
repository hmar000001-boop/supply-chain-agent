# M8-B4-C — Host replacement, durable resume and reconciliation

**Status:** Implementation prepared; user GitHub Actions CI pending. Maven/JUnit/PostgreSQL test execution is not claimed by this delivery.

## Purpose

B4-C extends B4-A's durable adapters and B4-B's full-application workflow coverage with targeted interruption/replacement tests. It verifies that a newly constructed graph host can recover its checkpoint and reload the separate authoritative intention, approval, budget and operation-ledger records before continuing.

The tests deliberately distinguish three different authorities:

- LangGraph4j PostgreSQL checkpoint: graph continuation state only.
- PostgreSQL business/runtime repositories: recovery intention, exact approval binding, B6 computational budget and M3 operation outcome.
- External provider state: a separately simulated provider system that survives replacement of the application host and is queried during reconciliation.

A graph checkpoint alone is never treated as proof that an external operation committed.

## Implemented test scenarios

`M8B4CRestartReconciliationIntegrationTest` is gated by `-Dphase0.postgres=true` and contains four PostgreSQL integration methods.

| Test | Scenario | Expected invariant |
|---|---|---|
| `interruptedWorkflowReloadsApprovalIntentionBudgetAndReconcilesAmbiguousWriteAfterPoolReplacement` | Pause before approval; persist human approval; dispatch a state-changing action; provider commits but the response times out; pause before reconciliation; close the first Hikari pool; construct a new pool, saver, graph, intention service, approval store, B6 ledger and M3 ledger; resume and reconcile | Objective fingerprint, protected constraints, exact approval binding, graph checkpoint and reserved B6 capacity survive host replacement; the UNKNOWN_OUTCOME is not blindly dispatched; the external effect count remains one; a provider-confirmed receipt moves M3 to COMPLETED; B6 usage is committed once |
| `authoritativeNotAppliedReconciliationAllowsOneSafeRetryAfterHostReplacement` | First provider attempt returns UNKNOWN_OUTCOME without applying an effect; authoritative provider read confirms NOT_APPLIED; persist that reconciliation; close the first pool; construct a new pool/ledger/dispatcher; retry and replay | Retry is allowed only after durable NOT_APPLIED evidence; exactly one later business effect occurs; duplicate replay returns ALREADY_COMPLETED |
| `independentDispatchersWithSameIdempotencyKeyProduceOneConcurrentExternalEffect` | Two dispatchers use independent PostgreSQL pools and race the same principal/action/idempotency key | One handler executes; the other observes IN_PROGRESS; replay observes ALREADY_COMPLETED; one external effect total |
| `concurrentIntentionUpdatesUseVersionCasAndPreserveTheProtectedObjective` | Two repositories on independent pools update the same intention from the same version | Exactly one CAS succeeds; version advances once; objective fingerprint and protected constraints remain unchanged; append-only history contains the initial and winning version |

Test timestamps are derived from the execution clock and truncated to PostgreSQL microsecond precision. They are not pinned to a historical CI timestamp.

## Corrective M3 contract change

The M8-B4-C safe-retry scenario exposed a mismatch in `PostgresActionExecutionLedger.inspect(...)`. The `ActionExecutionLedger` contract and `InMemoryActionExecutionLedger` define a definitively failed, retryable, known-NOT-APPLIED operation as absent during inspection. The PostgreSQL adapter had returned that FAILED row as a present claim, preventing `ActionDispatcher` from proceeding to its fenced retry acquisition.

The PostgreSQL adapter now returns `Optional.empty()` only when `state='FAILED'`, `retryable=true` and `outcome_unknown=false`. `claimFenced(...)` still performs the transactional state/fencing check before reacquiring the same logical operation. `UNKNOWN_OUTCOME`, non-retryable failure, completed, in-progress and key-reuse results remain visible and block blind dispatch.

This aligns the JDBC adapter with the existing interface and in-memory reference semantics; it does not weaken the UNKNOWN_OUTCOME safety boundary.

## B4-A CI correction retained

The cumulative patch also retains the prior B4-A assertion correction. Releasing a two-unit reservation against an initial four-unit budget restores all four units of available capacity. The integration test therefore expects `remaining(MODEL_CALLS) == 4`, not `2`.

## Evidence boundary and limitations

These are targeted graph-host replacement and adapter integration tests, not a claim that the entire application was killed by the operating system and relaunched as a separate OS process. The first pool is closed and a newly compiled LangGraph4j graph plus new persistence adapters are constructed against a second pool. PostgreSQL holds the durable state across that boundary.

The provider is a deterministic test double whose state is retained outside both application hosts to represent the remote provider's independently durable system of record. It is not a real supplier, ERP, WMS, carrier or payment integration.

The B4-C graph is a focused LangGraph4j workflow used to place explicit interruption boundaries around approval, dispatch and reconciliation. B4-B separately exercises `CognitiveCycleApplicationGraph.compileFull(...)`; B4-C does not claim that every production lifecycle port or `PostgresResourceBudgetLedger` operation is automatically wired into the full application lifecycle. Automatic production B6 register/reserve/commit wiring remains an open engineering requirement and must be resolved before claiming complete production budget governance.

The CAS test proves repository-level intention concurrency. The dispatch race proves M3 idempotency/fencing across independent pools. They do not yet prove inventory reservation concurrency against a real authoritative ERP/WMS adapter.

B4-D remains responsible for malicious/untrusted evidence, malformed model/provider output, PostgreSQL/provider outage and recovery, expiry/revocation races under injected failures, resource exhaustion/interrupt/resume regression, duplicate inbox/outbox/event delivery where not already covered by the M3 duplicate-dispatch test, and consolidated adversarial safety tests.

## Acceptance

- Apply the cumulative B4-A+B4-B+B4-C change-only ZIP over the user-accepted M8-B3 repository.
- Run `./mvnw -B -ntp -Dphase0.postgres=true clean verify` in GitHub Actions with the configured PostgreSQL service.
- Confirm the B4-A release-capacity assertion, V009 migration/restore checks, B4-B full-graph tests and all four B4-C PostgreSQL integration methods pass.
- Only the user's green CI confirmation accepts the cumulative B4-A/B/C code. B4-D remains open.


## CI correction — sparse B6 snapshot maps

The first GitHub Actions run of the cumulative B4-A+B4-B+B4-C patch passed all seven `M8B4ReliabilityIntegrationTest` methods and three of four `M8B4CRestartReconciliationIntegrationTest` methods. The remaining test errored in its final verification because `ResourceBudgetSnapshot.reserved()` intentionally omits resource units whose amount is zero; directly unboxing `reserved().get(MODEL_CALLS)` therefore caused a `NullPointerException`. The snapshot's `remaining(...)` implementation already defines absent amounts as zero through `getOrDefault`. The test now uses a small `amount(map, unit)` projection with `getOrDefault(unit, 0L)` for all pending/consumed/reserved assertions. This is a test-only correction; no ledger implementation or schema change was needed.
