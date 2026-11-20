# M8 changelog

## 2026-11-19 — M8-B0 baseline and evaluation specification

- Frozen the exact M7-accepted source state as `M8-M7-ACCEPTED-2026-11-19`.
- Added a reproducible full-repository baseline checkpoint and sorted SHA-256 file manifest.
- Audited the ten-module Maven reactor, source/test inventory, runtime composition and simulator/reference-adapter boundary.
- Recorded the framework-scope reconciliation: the later accepted M6 LangGraph4j/LangChain4j implementation supersedes the earlier draft framework-free non-goal.
- Added the architecture-to-code traceability matrix, request/failure scenario register, evaluation protocol and M8 delivery status.
- Updated README and M7 status to reflect the user's confirmation that final M7 CI is green.
- No production Java, POM, SQL migration, workflow, simulator or application behavior changed in M8-B0.

## 2026-11-19 — M8-B1 evaluation foundation (implementation delivered)

- Added a versioned 25-row corpus with 11 request-family fixtures and 14 failure/adversarial/recovery/concurrency fixtures.
- Added deterministic domain snapshots and immutable planning requests with per-scenario seeds, named budget profiles and stable fingerprints.
- Added an evaluation-only deterministic/greedy comparator alongside CoT-style sequential, ToT-inspired bounded search, RAP-inspired MCTS and the custom hybrid router.
- Added batch manifest and JSONL result rows, including planner-configuration fingerprint, resource-unit/pruning/backtracking counters, termination, feasibility and explicit planner-only/no-dispatch boundary.
- Replaced frozen planner clocks with moving clocks offset onto the static fixture timeline; the greedy comparator now enforces hard deadline and runtime as well as expansion/evaluation ceilings.
- Added explicit full-graph adapter injection via `FullGraphEvaluationHost.Ports`; the compile probe uses fail-closed ports, shared in-memory working/episodic repositories and an empty action registry.
- Added focused evaluation tests and `docs/phase8/M8-B1-EVALUATION-ARCHITECTURE.md`.
- No production planning/router, M7 persistence or external integration behavior was changed. The user subsequently confirmed that M8-B1 GitHub Actions CI is green.


## M8-B1 CI correction — Greedy comparator evaluation scope

- Corrected `GreedyDeterministicComparator` so the final candidate evaluation is retained outside the search loop and updated only when the corresponding best state is committed.
- Added a regression assertion that REQ-01 retains its two sequential reservations.
- No production planner, M7 PostgreSQL infrastructure, migration, or external-action execution path changed.
- GitHub Actions reported the initial compile-time failure at `GreedyDeterministicComparator.java:121`; local Maven 3.9.16 bootstrap is unavailable in this environment, so the corrected patch must be re-run through the repository CI.


## M8-B1 acceptance — GitHub Actions green (2026-11-19)

- User confirmed the complete M8-B1 GitHub Actions workflow is green after the greedy comparator scope correction and sequential-reservation regression assertion.
- M8-B1 is accepted; the M7 baseline remains frozen.

## 2026-11-19 — M8-B2 matched comparative analysis (accepted; user-confirmed CI green)

- Added complete matched-matrix validation for all scenario/procedure pairs and rotating per-scenario comparator execution order.
- Added overall and stratified comparative aggregates and ten pairwise feasibility-first comparisons with deterministic scenario-level bootstrap intervals.
- Extended raw JSONL with compact candidate evaluation fields and structured planner trace nodes, excluding private reasoning.
- Added logical replay determinism verification excluding wall-clock runtime and execution-order position.
- Added `scripts/run-m8-comparison.sh` and the M8-B2 comparative analysis specification.
- No production planner, router, simulator, action, authority, PostgreSQL or graph behavior changed. Tests were intentionally not executed during authoring at the user's request; CI acceptance is pending.


## 2026-11-19 — M8-B3 mechanism ablations and results clarification (accepted; user-confirmed CI green)

- Added eight ablation studies across B1, B2, B3, B4, B6 and the B2+B4, B1+B3+B6 and B4+B6 interactions.
- Registered 19 scenario units and generated 45 primary arm observations, 26 within-scenario paired contrasts and 18 deterministic replay rows (63 raw rows total). Replay rows are excluded from inference; all reported averages/deltas are descriptive.
- Exercised the accepted production mechanism services with fixed in-memory fixtures. No production mechanism, planner, action, authority, simulator, PostgreSQL or migration code was changed.
- Clarified the mixed B1 retrieval result, B2 duplicate consolidation, B3 workspace coverage, B4 objective-preserving method reconsideration, B6 uncertainty interruption, and the three selected compositions in `docs/phase8/M8-B3-RESULTS-CLARIFICATION.md`.
- Marked B1/B3 outcome-attributed learning updates, B4 restart durability, B6 durable budget authority and full all-mechanism composition as not evaluated; B5/GraSP remains closed.
- Executed the standalone Java 21 ablation CLI against production mechanism classes; 18/18 deterministic replay fingerprints matched. Maven/JUnit were not run; GitHub Actions remains the CI acceptance gate.


## 2026-11-19 — M8-B4-A durable B6 budget ledger and reliability probes (CI pending)

- Started M8-B4 from the exact user-uploaded M8-B3 source archive (`supply-chain-agent-main (3).zip`; SHA-256 `84cc3ef2482235092189ed89a43752166de44afbef201836611dd29bf0c833f2`). The archive does not include Git history; source identity is recorded by archive hash and a per-file manifest.
- Added `PostgresResourceBudgetLedger` implementing the existing B6 `ResourceBudgetLedger` contract. Budget account mutations lock `(scope_id, execution_id)` to serialize cross-host reservations; committed actual usage, including overruns, is durable; commit replay is idempotent and conflicting reuse is rejected.
- Exposed the durable budget adapter through `PostgresRuntimePersistence.resourceBudgets()`. Clarified that `InMemoryResourceBudgetLedger` is a synchronized reference/test adapter, not production durable accounting.
- Added additive migration V009 for budget accounts, limits, actual consumption, reservation identities, estimates and actual amounts. M7 migrations V001-V008 remain unchanged.
- Added PostgreSQL integration probes for budget restart/concurrency/lifecycle and visible actual-usage overruns, intention/objective restore with stateless codecs, approval binding/revocation/expiry after host replacement, and unknown external outcomes with no blind redispatch and explicit reconciliation. Added V009 migration-parser coverage.
- Updated the isolated backup/restore verifier to require contiguous V001-V009 history and assert that the six durable B6 tables are present after restore; M7 SQL migrations remain immutable.
- Added the M8-B4 reliability matrix and slice plan. B4-A does not claim complete application-graph workflows or full process-kill/restart; those remain subsequent B4 work.
- Maven/JUnit/CI were not run in the authoring environment. GitHub Actions remains the acceptance gate.


## 2026-11-20 — M8-B4-A CI correction and M8-B4-B full-graph workflow suite (prepared; user CI pending)

- Corrected the B4-A reservation-release assertion: releasing a two-unit hold against a four-unit budget restores four available units; the prior expectation of two was incorrect.
- Added ten full-graph test methods covering twelve fixture executions across eleven distinct scenario IDs via `FullGraphEvaluationHost.compileFull(Ports)` and the real `CognitiveCycleApplicationGraph.compileFull(...)` composition.
- Added exact approval-binding assertions, distinct approval identities after stale-plan/demand-change replanning, and assertions that replanning uses updated trusted definitions and authoritative world version.
- Preserved the boundaries of the test evidence: route evidence is preserved but rerouting is not tested; stale/current inventory evidence references are preserved but not reconciled into new facts; read-only/no-feasible paths fail closed because the graph has no typed read-only success terminal; process-kill/restart and B6 lifecycle wiring remain open.
- Added the B4-B workflow matrix and updated M8 status, decisions, traceability and module test inventory.
- No Maven, JUnit or GitHub Actions execution is claimed for this authoring delivery. The cumulative ZIP must be validated with `./mvnw -B -ntp -Dphase0.postgres=true clean verify`.


## 2026-11-20 — M8-B4-C graph-host replacement and external reconciliation (prepared; user CI pending)

- Retained the B4-A CI correction in the cumulative delivery: releasing a two-unit B6 reservation against a four-unit budget restores four available units; the prior expected value of two was incorrect.
- Added `M8B4CRestartReconciliationIntegrationTest` with four PostgreSQL-gated methods. The main test pauses a real LangGraph4j graph at approval and reconciliation boundaries, closes the first Hikari pool, constructs a replacement pool/graph/saver/intention/approval/B6/M3 adapter set, reloads persisted state, blocks blind UNKNOWN_OUTCOME replay, reconciles a provider-confirmed commit and verifies one external effect plus one B6 usage commit.
- Added a second host-replacement test in which a retry is permitted only after authoritative NOT_APPLIED evidence is durably recorded; a new pool/ledger then performs one safe retry and duplicate replay returns ALREADY_COMPLETED.
- Added independent-pool concurrent dispatch/idempotency and recovery-intention version-CAS tests. Exactly one action effect and one intention CAS winner are asserted; protected objective fingerprint and constraints must remain unchanged.
- Corrected `PostgresActionExecutionLedger.inspect(...)` to match `ActionExecutionLedger` and `InMemoryActionExecutionLedger`: a definitively FAILED + retryable + !outcome_unknown record is absent for inspection so `ActionDispatcher` can perform the fenced retry claim. UNKNOWN_OUTCOME remains present and cannot be blindly retried.
- Replaced a fixed historical B4-C test timestamp with an execution-relative UTC timestamp to prevent approval/budget expiry as CI dates advance.
- Documented evidence limits: host/pool/graph replacement is exercised inside one test process, not an OS process kill/relaunch; external provider state is a deterministic remote-system double; B4-B still owns full `CognitiveCycleApplicationGraph.compileFull(...)` workflow evidence; automatic production B6 lifecycle wiring remains open.
- No Maven, JUnit or PostgreSQL test execution is claimed by the authoring delivery. User CI remains the acceptance gate.


## 2026-11-20 — M8-B4-C CI correction — sparse resource snapshot maps

- Corrected the restart/reconciliation integration test to treat absent `ResourceBudgetSnapshot` map entries as zero, matching the production snapshot contract (`remaining(...)` already uses `getOrDefault`).
- Applied the same sparse-map projection to pending, committed and settled B6 budget assertions so zero reservations are not unboxed from `null`.
- No production B6 ledger, schema, migration, action-ledger behavior, or application runtime code changed in this correction.
- The CI log showed all seven B4-A reliability integration tests passing and three of four B4-C tests passing; the remaining error was a test assertion NPE at `M8B4CRestartReconciliationIntegrationTest.java:512`, not a PostgreSQL or graph-resume failure. Maven/JUnit were not re-run by the authoring session.


## 2026-11-20 — M8-B4-B full-graph fixture corrections (prepared; user CI rerun pending)

- Corrected the demand-change scenario to replace the existing customer order via `SupplyChainSnapshot.Builder.replaceCustomerOrder(...)`, avoiding the intentional duplicate-ID rejection of the insert-only builder method.
- Corrected the trusted action-definition scenario to refresh both the M5 trusted descriptor and the matching registered M3 test handler descriptor under the same action ID. The production M5↔M3 descriptor equality check remains unchanged and active.
- Latest CI evidence: all B4-A PostgreSQL probes (7) and B4-C restart/reconciliation probes (4) passed; eight of ten B4-B full-graph methods passed, while the two fixture errors above were reported. The patch contains test-only corrections and updated status documentation. No Maven/JUnit execution is claimed after these edits.
