# M8-PreB7 R1-DEF — Shared budget authority, reservation recovery and full-graph restart

**Status:** Implementation prepared; full hosted CI acceptance pending  
**Baseline:** User-confirmed green R1-C source line, including predecessor-scoped uncertainty idempotency  
**Delivery model:** One cumulative change-only ZIP; one complete Java 21/PostgreSQL CI gate  
**Scope:** R1-D, R1-E and R1-F are internal workstreams of this single integrated delivery.

## 1. Purpose

R1-DEF closes the remaining core B6 reliability gaps identified before Pre-B7. The work is deliberately integrated because the shared guard defines budget authority, the lease ledger makes that authority safe across owners and process failures, and full-graph restart tests verify that the application composition can use the durable boundary. The three workstreams retain separate tests and evidence labels, but are not separate default ZIP/CI increments.

## 2. D — One shared durable guard at the application boundary

`PostgresRuntimePersistence` exposes the scope-bound `LeasedResourceBudgetLedger` and creates the `ResourceBudgetExecutionGuard` from an authoritative execution-budget provider and clock. The application composition has explicit `buildFull`/`compileFull` overloads that require the same exact guard object used by:

- M5 planning through `CognitiveCycleDecisionServices`;
- M3 registered reasoning through `BudgetedReasoningProvider`/`ReasoningAction`;
- M3 external action-handler invocation through `ActionDispatcher` and `ResourceBudgetedActionExecution`, charged to `ResourceUnit.ACTION_DISPATCHES`; and
- B6 lifecycle/meta-control through `CognitiveCycleLifecycleServices`.

`CognitiveCycleServiceOperations` rejects divergent decision/lifecycle guard identities. The lifecycle composition now rejects an M3 `ActionDispatcher` that is unbudgeted or bound to a different guard whenever a shared B6 guard is configured, and continues to check that every registered `ReasoningAction` is bound to that exact guard. The authorization boundary repeats these checks after the mutable registry may have changed. Compatibility/test overloads remain available for deterministic tests and must not be represented as production deployment wiring.

For idempotent M3 actions, the B6 reservation identity is derived deterministically from the same principal/action/idempotency-key tuple as the M3 ledger, rather than the process-local claim UUID. A replacement M3 ledger therefore cannot accidentally generate a new B6 reservation for a previously committed logical action. A regression test simulates that replacement and proves the B6 lease blocks redispatch. M3 retries acknowledged by its own completed ledger return before B6 accounting, so an ordinary duplicate acknowledgment does not consume another dispatch unit. If an action handler returns successfully but the subsequent B6 usage commit fails or is fenced, the adapter records `UNKNOWN_OUTCOME`; an M3 retry is blocked and must be reconciled rather than being reported as an authorization rejection. The same post-effect rule applies if constructing or committing the final M3 receipt fails after the handler returned: `ActionDispatcher` records an unknown outcome where its ledger remains writable, and it never maps a stale M3 receipt fence to `AUTHORIZATION_DENIED`.

The application-level checkpoint saver is selected only after the full graph builder exists. This lets a PostgreSQL saver derive its serializer from the exact graph schema that is subsequently compiled.

## 3. E — Fenced leases and crash reconciliation

V010 is forward-only and adds `owner_id`, `lease_expires_at`, `fencing_token`, `started_at` and `recovery_outcome` to the existing B6 reservation. A database check constraint rejects an operation-start marker without lease ownership. V001–V009 are unchanged. The reservation row remains the durable identity; the new fields add an owner/lease lifecycle without replacing the existing budget account and item tables.

The production guard performs these transitions:

1. Acquire a deterministic reservation identity for `(executionId, operationId)` under a process-local owner ID and monotonically increasing fencing token.
2. Persist the operation-start marker **before** invoking the provider.
3. Renew the lease while the operation is active.
4. Commit measured usage on success. If the provider or usage measurement throws while the process remains alive, commit the reserved estimate before rethrowing.
5. On process recovery, an expired reservation with no start marker is safe to release and reacquire under a higher fencing token. An expired reservation with a start marker is treated as unknown outcome, fenced and committed without retry.

Expired reservations that never crossed the operation-start marker are recovered before started unknown-outcome work is conservatively charged. PostgreSQL and the in-memory reference ledger both prioritize those safe releases; ordering regression tests deliberately make reservation IDs sort in the opposite order.

For a started unknown-outcome reservation, recovery charges the remaining capacity in each **finite** budget dimension after preserving reservations held by other active owners. This intentionally may exhaust the remaining finite budget. It is not a claim that the system has measured the provider's actual use.

A dimension configured as `Long.MAX_VALUE` has no finite capacity ceiling. Its unknown usage cannot be conservatively upper-bounded by this ledger; recovery retains the reserved estimate for that dimension and the release-readiness record explicitly calls out the limitation. Production deployments requiring a hard usage ceiling must configure finite limits for those resource dimensions and supply authoritative usage measurements where available.

An explicit pre-start release is terminal (`EXPLICITLY_RELEASED`) and cannot be reclaimed by the automatic expiry path. Reclamation is restricted to `EXPIRED_BEFORE_START`. Every reclaim advances the fence. A stale owner/token cannot mark start, renew, commit or release after fencing.

## 4. F — Full application graph checkpoint recovery in a fresh JVM

`M8B4FullGraphWorkflowTest` launches separate child JVMs. Both build the complete application graph through `FullGraphEvaluationHost` and `CognitiveCycleApplicationGraph.compileFull`. The checkpoint saver is PostgreSQL-backed and uses the graph builder supplied before compilation.

The first process runs the graph to a host clarification interrupt, verifies that no external effect occurred, then calls `Runtime.halt(0)` to avoid normal JVM shutdown. A second JVM rebuilds the graph and PostgreSQL saver using the same deterministic scoped execution/thread identity, restores the checkpoint, supplies the host clarification and reaches a terminal result. It additionally verifies that the resumed full graph committed an M5 reservation in the durable PostgreSQL B6 ledger.

**Precise evidence boundary:** the full-graph halt occurs at the safe clarification boundary before domain effects. The evaluation host's memory, intention, authority, approval, observation and business adapters in this test are deterministic and primarily in-memory. The test demonstrates checkpoint restoration and continuation of the complete graph in a fresh JVM; it does not prove that every production adapter/store is durable, nor does it demonstrate a full-graph process kill after an arbitrary external business effect. The independent R1-DEF PostgreSQL tests exercise abrupt child-JVM death immediately before and after the B6 operation-start marker and verify the distinct recovery rules. Existing B4 tests continue to cover external-effect reconciliation through the action ledger at their previously accepted boundary.

## 5. Validation matrix

| Area | Evidence added or retained | Required hosted validation |
|---|---|---|
| Shared D guard | strict app builder/compiler overloads, operations composition identity check, M3 reasoning and external action-dispatch binding, stable B6 operation identity across M3 ledger replacement | runtime, app and evaluation module compilation/tests |
| E lease before start | expiry releases unstarted reservation; a later owner reclaims it with a higher token | PostgreSQL integration, including real two-process state |
| E lease after start | no replay; remaining finite capacity charged while preserving other active reservations | PostgreSQL integration and stale-owner rejection |
| E recovery order | expired unstarted reservations are released before unknown started usage is charged | in-memory and PostgreSQL ordering regressions with reverse-sorted reservation IDs |
| D M3 post-effect accounting failure | a handler success followed by a fenced/failed B6 commit becomes `UNKNOWN_OUTCOME`; retries are blocked | `ActionDispatchCommandTest.successfulExternalEffectWithFailedB6CommitIsReportedAsUnknownOutcome` |
| D M3 post-effect receipt failure | receipt creation or M3 ledger completion fails after handler return; outcome is unknown and retries remain blocked | `ActionDispatchCommandTest.successfulExternalEffectWithFailedM3ReceiptCommitIsReportedAsUnknownOutcome` |
| E concurrency | active owner prevents a second owner from acquiring the same operation | PostgreSQL concurrent/ownership tests |
| E explicit release | released-before-start reservation cannot be automatically reclaimed | in-memory guard regression and PostgreSQL constraint/state checks |
| F full graph | complete application graph checkpoint restored in a new JVM; post-resume M5 reservation is durable | PostgreSQL checkpoint integration and both child JVMs |
| Cross-cutting | all R1-A/B/C tests, B6-budgeted M3 dispatch/replay regressions, architecture verifier, source/test inventory | complete `./mvnw -B -ntp -Dphase0.postgres=true clean install` |

## 6. What this does not close

- Statistical calibration of uncertainty; R1-C assessments remain `NOT_CALIBRATED`.
- Trusted provider token-level usage where providers do not expose authoritative counts.
- Mathematical upper bounds for unknown work in resource dimensions configured as unbounded.
- A complete production graph with live durable ERP/WMS/supplier/carrier, IAM, memory, approval, intention, action and observation adapters.
- Full-graph post-effect process-kill recovery across every possible boundary.
- WAL/PITR, measured RPO/RTO, production tenant reconciliation, retention/legal holds, independent threat-model signoff or penetration testing.

A green CI run will accept the implemented R1-DEF contracts and their tested integration boundaries. It will not convert these remaining rollout gates into completed production controls.

## 7. Acceptance

R1-DEF is accepted only after the user's complete GitHub Actions workflow is green on the commit containing this combined delivery, including the architecture verifier, Java 21 Maven reactor, PostgreSQL migration/integration tests, B6 lease fault-injection tests and full application graph fresh-JVM checkpoint test. The source inventory is a static count, not a CI test-run count.
