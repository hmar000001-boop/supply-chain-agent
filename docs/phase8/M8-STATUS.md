# M8 status — Evaluation and application freeze

**M8 objective:** evaluate the accepted supply-chain agent across multiple independent request families; compare decision procedures under matched conditions; measure the contribution and interactions of CoALA/Fan & Lan mechanisms; validate end-to-end safety and recovery; then freeze a reproducible application checkpoint before Chapters 19 and 20 are written.

## Baseline

- **Baseline ID:** `M8-M7-ACCEPTED-2026-11-19`
- **Accepted M7 repository checkpoint:** `SUPPLY_CHAIN_AGENT_M7_ACCEPTED_BASELINE_2026-11-19.zip`
- **Checkpoint SHA-256:** `7bf42b69f3f3db48e944fef907f9aa805bacf60bfa8f9b24b95a0a4636cdec49`
- **Baseline source-tree manifest SHA-256:** `08e82ac250aab5ec01271ca9ccea624d8aa7c0ad7a13bad2c680393f31667a8c`
- **Project files:** 556
- **M7 status:** accepted and closed, based on the user's confirmation that the complete GitHub Actions workflow is green on 2026-11-19. The accepted workflow runs the Java 21 Maven reactor, PostgreSQL integration tests, and the conditional backup/isolated-restore smoke step.

## M8 delivery status

| Delivery | Responsibility | Status |
|---|---|---|
| M8-B0 | Baseline reconstruction, architecture audit, test inventory, evaluation protocol and scenario register | **Complete** |
| M8-B1 | 25-row corpus (11 request + 14 failure/adversarial fixtures), five proposal-only comparators, JSONL experiment harness with pruning/backtracking/resource counters, advancing fixture-clock budget enforcement and full-graph composition probe | **Accepted — user confirmed CI green** |
| M8-B2 | Compare CoT-style, ToT-inspired, deterministic/greedy, RAP-inspired MCTS and the hybrid router with matched analysis, paired uncertainty intervals and logical replay | **Accepted — user confirmed CI green** |
| M8-B3 | Run B1/B2/B3/B4/B6 ablations and selected interaction experiments; clarify descriptive results and non-evaluable edges | **Accepted — user confirmed CI green** |
| M8-B4 | End-to-end safety, failure, concurrency, restart and multi-request evaluation | **Accepted — B4-A/B/C/D; user confirmed complete B4-D CI green on 2026-11-20** |
| M8-B5 | Final quality, security and architecture review; reproducible experiment evidence | **CI accepted — user confirmed the complete workflow is green on 2026-11-20; B2 artifact-content review remains open** |
| M8-B6 | Freeze application source, evidence and full repository checkpoint | **Source checkpoint packaged on 2026-11-20; final evidence freeze awaits review of hosted B2/B3/R2 artifacts and source checkpoint refresh** |
| M8-B7 | Approve Chapter 19/20 outlines and write chapters | **Not started — gated on B6 freeze and outline approval** |

## M8-B1 implementation delivered and accepted — CI green

- Added `supply-chain-agent-evaluation` scenario catalog and resource register `M8-B1-v1`: 11 request-family rows and 14 failure/adversarial/recovery/concurrency rows.
- Added 25 independent immutable planning fixtures with stable request/world/corpus fingerprints, fixed per-scenario seeds, named budget profiles and explicit effect boundaries.
- Added five isolated planner comparators: CoT-style sequential (deterministic decision provider), ToT-inspired bounded search, evaluation-only deterministic/greedy, RAP-inspired MCTS and the hybrid router.
- All planner paths use the same `SimulatorBackedPlanningWorldModel`, pure `SupplyChainSimulator` and `DeterministicCandidateEvaluator`; the greedy comparator is not registered in the production router.
- Added JSONL batch/run records with baseline, corpus, world-model, evaluator and planner-configuration fingerprints, budgets, metrics and explicit proposal-only/no-dispatch indicators.
- Added a moving fixture clock offset to the static corpus timestamp so hard deadlines and max-runtime guards continue to advance; the greedy comparator explicitly checks those limits.
- Added `FullGraphEvaluationHost.compileFull(Ports)` using the actual application `CognitiveCycleApplicationGraph.compileFull(...)` entry point, explicit trusted adapter contracts, shared in-memory working/episodic repositories and no registered action handlers. Its default composition probe uses fail-closed ports and only verifies graph compilation.
- Added M8-B1 evaluation architecture documentation and tests. The 25 fixtures are planner inputs, not 25 completed lifecycle workflows; approval, external timeout, duplicate delivery, restart and concurrency workflows remain M8-B4.
- Maven/CI acceptance was confirmed green by the user after the `GreedyDeterministicComparator` scope fix and regression assertion.

## M8-B2 implementation accepted — CI green

- User confirmed the complete M8-B2 GitHub Actions workflow is green. The 25-fixture × 5-procedure matched comparison harness and deterministic replay analysis are accepted.

## M8-B3 mechanism ablations — accepted; user confirmed CI green

- User confirmed the complete M8-B3 GitHub Actions workflow is green. M8-B3 is accepted and closed.

- Added eight focused studies: B1, B2, B3, B4, B6, B2+B4, B1+B3+B6 and B4+B6.
- Registered 19 scenario units, 45 primary condition observations, 26 within-scenario contrasts and 18 selected deterministic replay rows (63 raw rows total). Replay rows are excluded from primary counts and interpretation; scenario is the experimental unit.
- Executed the Java 21 ablation CLI against the accepted production mechanism services using in-memory fixtures; recorded raw JSONL and descriptive paired summaries.
- Main fixture-level observations: B2 consolidated 3 raw signals to 2 resolution units; B3 covered all 3 required tags with the same 7 capacity units and 3 admitted candidates as top-k; B4 preserved the objective while reconsidering the method; B6 interrupted one high-uncertainty case while allowing a low-uncertainty case to continue.
- B1 results are mixed/negative for the current selector parameters: utility top-k reached mean recall 1.0/precision 0.5625, versus B1 mean recall 0.8333/precision 0.4375. B1 also exposes estimated maintenance cost and the downstream-use edge. No B1 outcome-attributed learning update exists, so that ablation is not evaluable.
- Selected compositions B2+B4, B1+B3+B6 and B4+B6 are included. B4 restart durability, B6 durable budget authority, B3 outcome-attributed learning and full all-mechanism composition remain explicitly not evaluated.
- 18/18 selected logical replays matched. No external action was invoked and no PostgreSQL connection was used.
- Results are deterministic fixture-level descriptions, not population estimates or statistical significance claims. Maven/JUnit were not run during authoring; GitHub Actions is the acceptance gate.

## M8-B2 detail — matched decision-procedure analysis

- Added strict matched-matrix checks for the full 25-fixture × 5-comparator result matrix, including fixture/request/seed/budget parity, no duplicate or missing planner rows, planner-only boundary, and execution-order permutation.
- Added rotating comparator execution order across scenarios to reduce systematic order effects.
- Added overall and stratified aggregates by budget profile, scenario kind and fixture profile. Feasibility, best feasible quality, fulfillment, modeled cost/risk/robustness, candidate constraint violations, approval/deadline facts, termination reasons and resource-unit totals are separately reported.
- Added all ten scenario-paired procedure comparisons with feasibility-first win/tie/loss rules, paired mean differences and deterministic scenario-level bootstrap percentile intervals (2,000 resamples).
- Extended raw JSONL with compact candidate-evaluation detail and structured search trace nodes; no private reasoning or free-text rationale is recorded.
- Added logical replay verification that excludes wall-clock runtime and execution-order position, and reports mismatched scenario/procedure IDs.
- Added `scripts/run-m8-comparison.sh` to execute full reactor `clean install` with PostgreSQL phase-zero tests, then write raw, comparison and determinism JSONL artifacts.
- No production planner, routing, simulator, action, authority, PostgreSQL or graph behavior changes. Tests were not executed during patch authoring at the user's direction; GitHub Actions is the acceptance gate.

## M8-B0 acceptance

- Reconstructed the accepted M7 source from the exact uploaded repository archive and the three accepted M7-B2 overlays.
- Verified the reconstructed 556-file project tree byte-for-byte against the staged accepted repository tree.
- Created a full M7-accepted baseline ZIP and sorted per-file SHA-256 manifest.
- Audited all eleven Maven modules, their production Java sources, test sources and annotated test methods/factories.
- Recorded the architecture-to-code traceability matrix and the M8 evaluation protocol.
- Registered the required request families and failure scenarios from the Part III pilot requirements.
- No production Java, POM, migration, simulator or workflow code was changed in M8-B0. Only project documentation and phase status were updated.

## M8-B4-D — adversarial, fault-injection and B6 lifecycle (accepted; user-confirmed CI green 2026-11-20)

- Added a two-child-JVM process-boundary integration test. Process 1 checkpoints an interrupted LangGraph4j workflow after an externally committed but unacknowledged write; process 2 rebuilds the pool/graph/saver/repositories, restores intention/approval/B6/M3 state, blocks replay, verifies provider evidence and reconciles without a duplicate effect.
- Added `ResourceBudgetExecutionGuard` as a shared host-owned identity for M5 planning, M3 reasoning and B6 meta-control. M5 reservations are bounded by planner request ceilings and committed from measured statistics. `BudgetedReasoningProvider` reserves model/evaluation usage before invocation, commits observable counts and conservatively charges a started failed invocation.
- B6 meta-control reads the durable ledger snapshot. Complete operations composition requires the M5 decision and lifecycle services to share the same guard; a registered reasoning action must be bound to that guard. The evaluation full-graph host accepts the guard through explicit ports.
- Added PostgreSQL integration coverage for shared M5/M3 accounting and for budget-store outage fail-closed behavior. Added tests for real supplier-message prompt injection, fake role labels, invented evidence citations, malformed/oversized model responses, unknown confidence, unbudgeted reasoning-action registration and divergent guard identities.
- Important boundaries: the forked-JVM test uses a small LangGraph4j test graph, not the entire application graph; its external provider is a deterministic marker-file double. Provider tokens are not inferred. A hard process kill between B6 reserve and commit conservatively leaves an active reservation; automatic orphan reservation reclamation is not implemented. The current `Phase0Application` remains an intake smoke entry point; production full-graph hosts must wire the single guard explicitly as documented in `M8-B4-D-PRODUCTION-B6-COMPOSITION.md`.
- Acceptance: the user confirmed the complete GitHub Actions CI workflow is green on 2026-11-20. This confirms the configured build/test workflow, including the two-process PostgreSQL integration path; it does not establish the live external integrations or deployment-owned controls listed below.



## Phase boundaries

M8 evaluates the application and freezes it; it does not reopen M7 persistence infrastructure. WAL/PITR activation, tenant reconciliation for `__legacy_unscoped__`, deployment retention approval, external system credentials/adapters and enterprise identity integration remain deployment-owned rollout items. They are not silently marked complete by a green CI run.

Chapters 19 and 20 remain gated on the M8-B6 freeze and approval of their detailed outlines.


## M8-B5 — final quality, security and architecture review (CI accepted; evidence interpretation outstanding)

- B4-D is accepted based on the user's confirmation that the complete GitHub Actions workflow is green on 2026-11-20. B4-A/B/C/D are closed.
- Source audit found missing direct LangGraph4j dependency declarations in the app, evaluation and PostgreSQL infrastructure modules; B5 adds those explicit dependencies and a static guardrail for direct framework dependencies and the framework-neutral module boundary.
- Corrected the reactor count from ten to eleven modules and verified the internal module dependency graph is acyclic. The test inventory matches 404 production Java files, 62 test sources and 334 test annotation occurrences (static counts, not CI execution totals).
- Removed the temporary B4-D troubleshooting step from the active workflow and made `workflows/ci.yml` byte-identical to `.github/workflows/ci.yml`.
- B2's comparison runner was present, but its three JSONL result outputs were absent from the latest repository archive. B5 makes CI clean-install the tested reactor, execute `M8ComparisonCli` against those installed artifacts, and upload raw runs, comparison report, replay-check JSONL, SHA-256 checksums and CI source/run provenance. The next CI artifact must be inspected before B5 empirical interpretation is closed.
- Static security review found no obvious unsafe Java deserialization, production process execution, or hard-coded secret patterns in the inspected source. This is not a SAST/SCA scan or penetration test. See `M8-B5-FINAL-QUALITY-REVIEW.md` for detailed scope and limits.
- The user confirmed that the complete B5 GitHub Actions workflow is green on 2026-11-20. This accepts the architecture guard, full Java 21/PostgreSQL `clean install`, comparison CLI execution, non-empty result-file checks, checksum/provenance generation, backup/restore smoke and artifact upload path. The raw hosted JSONL contents were not included in the source archive supplied for M8-B6; matrix/result/replay content interpretation remains an explicit evidence-only task and must not be inferred from the green status.

## M8-B6 — application source freeze and repository checkpoint (source package prepared)

- Created the full source checkpoint `SUPPLY_CHAIN_AGENT_M8_B6_FROZEN_SOURCE_2026-11-20.zip` from the B5-reviewed repository tree after the user-confirmed green B5 CI. The ZIP includes source, tests, Maven wrapper/configuration, migrations, scripts, GitHub Actions workflow, architecture/phase documentation and this freeze record; it intentionally excludes `.git`, `target/`, local `.env` files and transient IDE/log output.
- The B6 documentation-only changes do not alter Java, Maven POMs, SQL migrations, scripts, or CI workflow behavior. The B5-accepted source/CI gate therefore remains the latest executable-code validation. No new Maven/JUnit run is claimed for this packaging step.
- A sorted SHA-256 file manifest and checkpoint metadata bind the exact source tree. The project archive does not contain Git history; no commit SHA or GitHub Actions run ID was supplied, so the source archive digest and user-confirmed workflow state are the checkpoint identity available here.
- The B5 workflow's green run confirms its B2 CLI and non-empty result-file/provenance steps completed. The actual three JSONL files and GitHub Actions artifact payload were not supplied to this workspace; their record counts, ten paired comparisons and 125 replay outcomes are therefore not content-verified here. Archive that CI artifact beside the source checkpoint and review it before stating planner-result conclusions in Chapters 19/20.
- The readiness register preserves open production/deployment gates: production `compileFull` host wiring, full production-graph OS restart, orphan B6 reservation recovery, trusted token-level usage, ERP/WMS/supplier/carrier adapters, production IAM, PITR/RPO/RTO, tenant reconciliation, retention/legal-hold operations and formal SAST/SCA/penetration testing.
- M8-B6 does not convert a source archive into a production deployment certification. The source checkpoint is packaged; final evidence freeze is conditional on the companion B2 experiment artifact being archived/reviewed and no further source edits being introduced without a new checkpoint version.

## M8-PreB7 — R1 accepted and grouped R2 implementation prepared

- R0 is accepted.
- R1-A, R1-B and R1-C are accepted on project-owner-confirmed green CI.
- R1-DEF is accepted: the project owner confirmed the complete 12-module Java 21/PostgreSQL CI green on 2026-11-20 after cumulative CI Fixes 1–5. The acceptance is limited to the tests and adapters exercised by that workflow; deployment-owned production controls remain open.
- The grouped R2 delivery is implemented in one cumulative change-only package and is pending its single hosted CI acceptance cycle.
- B2 now creates typed, scope/objective/version/evidence-bound procedure drafts from resolved child cases and registered action descriptors. Draft approval is a separate review-state transition, not an executable action plan or dispatch authorization.
- B3 persists accepted consumer receipts and separately verified downstream outcomes through PostgreSQL V011. The live B3 workspace remains cycle-bounded and is not persisted. Repository replacement integration tests cover receipt/outcome reload, scope isolation and idempotent replay.
- B1/B3 feedback is opt-in and defaults to neutral scoring. Training accepts only B3-minted verified-evidence capabilities, candidates are SHADOW-only, holdout scenarios must be disjoint from training cycles, and activation requires an exact report fingerprint plus separate host authorization. An active overlay is scope-bound.
- The accepted inputs do not include a verified downstream-outcome corpus or an independent holdout dataset. The R2 evidence CLI deliberately records `NO_VERIFIED_OUTCOME_CORPUS`, zero holdout scenarios, `SHADOW`, `DISABLED`, and `baselinePreserved=true`. Unit-test observations verify control logic only; they are not empirical evidence. No adaptive advantage is claimed and the previous B1/top-k baseline remains unchanged.
- Hosted CI now regenerates the existing M8-B3 mechanism ablation JSONL artifacts, emits the R2 evidence-status artifact with run/source provenance and checksums, and produces CodeQL and Trivy SARIF plus tool-version evidence. Findings must be reviewed and triaged before R2 acceptance.
- Current static inventory: 444 production Java files, 69 test Java sources, 402 test annotation/factory occurrences, 63 annotated test sources and 6 helper sources. These are repository source counts, not executed test totals.
- R1-DEF's full-graph restart proof remains bounded to a safe pre-effect clarification boundary with deterministic test adapters. It does not certify durability of all live business/authority adapters or post-effect full-graph restart.


## M8-PreB7 — F-03/F-04 candidate implementation (hosted CI pending)

- Current static inventory for this candidate: **461 production Java files, 71 test Java sources, 429 test/factory annotations, 65 annotated test sources and 6 support/helper sources**. The architecture guard and `M8-TEST-INVENTORY.csv` were synchronized to these counts; they are not test execution totals.


- F-03 bridges an approved, evidence-bound B2 recovery draft into CoALA procedural memory as a `DRAFT`; the B2 approver cannot also perform procedural validation or activation. The immutable artifact contains registered action references, opaque condition keys and evidence, not executable commands or generated free-form steps.
- Active recovery procedures return through the shared CoALA `MemoryRetrievalService` and B1 candidate factory. Exact artifact dereference revalidates scope, evidence and current action-descriptor fingerprints. No automatic activation or dispatcher execution is added.
- PostgreSQL V012 adds content-addressed immutable recovery-artifact payload storage; migrations V001-V011 remain unchanged. The PG adapter and repository-replacement test are present; the full evaluation host still uses in-memory procedural/artifact stores.
- F-04 changes B3 optional admission from post-hoc aggregation to bounded comparison of singleton options and pair/triple content coalitions. Proposal traces record membership, joint coverage, score and winner. Mandatory content remains protected; conflicts are excluded; a truncated search cannot win from incomplete alternatives and falls back to singleton admission.
- B3 workspace snapshots remain ephemeral. Receipt/outcome evidence and R2 shadow/disabled feedback gates are unchanged. Coalition competition is an engineering extension, not a claim of full LIDA reproduction.
- Added `scripts/verify-m8-preb7-f03-f04.py` to both identical CI workflow copies. The new increment depends on F-01/F-02 being accepted first. Focused Java 21 compilation and 35 targeted tests passed locally; the Maven wrapper could not download Maven in this environment, so hosted Java 21/PostgreSQL CI remains the acceptance gate.
