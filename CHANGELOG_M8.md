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

## 2026-11-20 — M8-B4-D adversarial, fault-injection and durable budget lifecycle (implemented; CI pending)

- Added a two-child-JVM integration scenario over the PostgreSQL LangGraph4j saver: the first process writes an intention, exact approval binding, B6 reservation and M3 unknown-outcome state after a deterministic external effect; the replacement process restores from PostgreSQL, blocks blind replay, verifies the external marker, reconciles the result and settles the reservation.
- Added one shared `ResourceBudgetExecutionGuard` identity across M5 planning, M3 `ReasoningAction` provider calls and B6 meta-control. Reservations precede work; measured planner statistics and observable model-call/evaluation counts are committed; unknown measurement/provider failures are charged conservatively; ledger failures fail closed.
- Added PostgreSQL integration probes proving M5 and M3 usage consumes one durable account and that an unavailable PostgreSQL budget store prevents provider invocation.
- Added action-composition checks for a shared guard, including revalidation at authorization/action-plan boundaries to prevent a later mutable `ActionRegistry` registration from bypassing the B6 guard; added a regression test for a post-construction unbudgeted registration.
- JSON-encoded caller-controlled reasoning task/context values; strengthened system instructions to treat supplier evidence as untrusted data; added fabricated-citation, prompt-injection, malformed/oversized output and confidence-fallback tests.
- Documented exact process/provider boundaries, lack of token-level provider usage, explicit production-host guard wiring, and the conservative active-reservation state left by a hard process kill. Automatic orphan reclamation is not implemented.
- Updated M8 status, test inventory, traceability, README and B4-D scenario/production-composition documents. M7 migrations V001-V008 remain unchanged; B4-D adds no migration.
- Java 21 parser-only validation covered all 465 Java source files with zero syntax errors after correcting an escaped-quote assertion found by that check. Maven 3.9.16 is not cached and the distribution host is unreachable in this authoring environment; no Maven/JUnit execution is claimed. GitHub Actions CI remains the acceptance gate.


## 2026-11-20 — M8-B4-D accepted; M8-B5 final quality review prepared

- The user confirmed the complete B4-D GitHub Actions CI workflow is green after the latest-repository correction. B4-A/B/C/D are now accepted; the B4-D test still represents a small LangGraph4j graph across two child JVMs with PostgreSQL adapters and a deterministic provider double, not a complete production application restart.
- B5 source review found direct LangGraph4j imports in app, evaluation and PostgreSQL infrastructure without direct module-local POM dependencies. Added those dependencies and a direct PostgreSQL saver declaration to prevent reliance on transitive dependency leakage.
- Corrected architecture documentation to 11 Maven modules and added `scripts/verify-m8-b5-architecture.py`, covering reactor DAG acyclicity, direct framework dependencies, framework-neutral boundaries, module test-inventory consistency and active/mirror workflow parity.
- Removed the temporary B4-D troubleshooting check from the active workflow and synchronized `workflows/ci.yml` with `.github/workflows/ci.yml`.
- The latest source archive contained the B2 runner but not its actual three JSONL result files. CI is now configured to clean-install the exact reactor, run the B2 CLI and upload raw runs, comparison analysis and determinism output with SHA-256 and source/run provenance for review. No empirical output is fabricated; B5 acceptance awaits the hosted artifact inspection.
- Updated M8/B4 status, decisions, traceability and README. Deployment limitations (live ERP/WMS/carrier/IAM, production graph host, orphan B6 reservation recovery, trusted token accounting, PITR/RPO/RTO, retention and tenant reconciliation) remain open and are not implied complete by green CI.
- Local checks for the B5 patch: Python guard execution, XML parsing, workflow YAML parsing, shell syntax and package overlay/integrity checks. Maven/JUnit/CI were not run locally; the next hosted GitHub Actions run is the acceptance gate.


## 2026-11-20 — M8-B5 CI accepted; M8-B6 source checkpoint packaged

- The user confirmed the complete M8-B5 GitHub Actions workflow is green. The architecture guard, Java 21/PostgreSQL `clean install`, M8-B2 CLI, required result-file checks, checksums/provenance, PostgreSQL backup/isolated-restore smoke, dependency-tree capture and artifact-upload step therefore passed in the hosted run.
- Marked B5's CI/build/architecture gate accepted. The actual hosted B2 JSONL payload was not provided to this workspace; its content-level matrix, paired-comparison and replay interpretation remains open and no result values are claimed.
- Added the M8-B6 freeze record, source-checkpoint metadata, source-file SHA-256 manifest and release-readiness register. Produced a complete source ZIP from the B5-accepted tree.
- M8-B6 packaging is documentation/checkpoint work only; no Java source, Maven POM, SQL migration, shell script or CI workflow was changed after the green B5 run. No additional Maven/JUnit execution is claimed for the checkpoint packaging step.
- Preserved open rollout gates: complete production application host/guard wiring, production-graph process restart, orphan B6 reservation recovery, trusted token-level usage, live ERP/WMS/supplier/carrier/provider adapters, production IAM, WAL/PITR RPO/RTO, tenant reconciliation, retention/legal-hold operation and formal SAST/SCA/penetration testing.

## 2026-11-20 — M8-PreB7 R1-DEF integrated implementation prepared (hosted CI pending)

- Grouped the remaining R1-D/R1-E/R1-F work into one cumulative delivery: strict shared durable B6 guard composition, PostgreSQL V010 fenced reservation leases/recovery, and full application-graph PostgreSQL checkpoint restoration in a fresh JVM.
- Added owner/lease/expiry/fencing/start/recovery state; expired unstarted work can be reclaimed with a higher fence, while started unknown work is not replayed and consumes remaining finite budget capacity after preserving other active holds.
- Explicitly preserve the limitation for unbounded resource dimensions: without a finite ceiling, this ledger cannot conservatively upper-bound unknown consumption.
- Added child-JVM fault probes for reservation expiry before and after the operation-start marker, stale-owner/concurrency cases, explicit-release fencing, fatal provider-error accounting and full `compileFull` graph checkpoint pause/restart/resume.
- Added the scope-bound PostgreSQL guard factory and strict application composition overloads for the exact same M5/M3/B6 guard identity.
- Extended the same guard through every M3 `ActionDispatcher` handler invocation using the new `ACTION_DISPATCHES` resource unit; lifecycle composition rejects missing or divergent dispatcher bindings.
- Made the B6 reservation identity stable across replacement of M3's process-local ledger by deriving it from the M3 principal/action/idempotency-key tuple; regression tests prove ordinary duplicate acknowledgment is not double-charged and a restarted M3 ledger cannot replay a committed external action.
- Updated the architecture/test inventory and Pre-B7 acceptance/release-readiness records. Static architecture verification passes at 409 production Java files, 64 test source files and 377 test annotations/factories.
- Hardened the M3 post-effect receipt boundary: a fenced or failed receipt completion after a handler returns is persisted/reported as `UNKNOWN_OUTCOME`, never `AUTHORIZATION_DENIED`; added a regression proving the action is not invoked again.
- Maven/PostgreSQL tests have not been run in this authoring environment. R1-DEF remains pending the user's full GitHub Actions run. The full-graph restart test is bounded to a safe pre-effect clarification boundary with deterministic adapters.
