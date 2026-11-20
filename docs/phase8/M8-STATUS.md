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
| M8-B4 | End-to-end safety, failure, concurrency, restart and multi-request evaluation | **In progress — B4-A assertion correction + B4-B full-graph workflows prepared; user CI rerun pending** |
| M8-B5 | Analyze results, perform final quality/security/architecture review and correct proven defects | Not started |
| M8-B6 | Final application freeze and full repository checkpoint | Not started |
| M8-B7 | Approve Chapter 19/20 outlines, then write against the frozen code | Not started |

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
- Audited all ten Maven modules, their production Java sources, test sources and annotated test methods/factories.
- Recorded the architecture-to-code traceability matrix and the M8 evaluation protocol.
- Registered the required request families and failure scenarios from the Part III pilot requirements.
- No production Java, POM, migration, simulator or workflow code was changed in M8-B0. Only project documentation and phase status were updated.

## Phase boundaries

M8 evaluates the application and freezes it; it does not reopen M7 persistence infrastructure. WAL/PITR activation, tenant reconciliation for `__legacy_unscoped__`, deployment retention approval, external system credentials/adapters and enterprise identity integration remain deployment-owned rollout items. They are not silently marked complete by a green CI run.

Chapters 19 and 20 remain gated on the M8-B6 freeze and approval of their detailed outlines.


## M8-B4 — end-to-end reliability and safety (in progress)

- Baseline is the user-uploaded accepted M8-B3 repository `supply-chain-agent-main (3).zip` (SHA-256 `84cc3ef2482235092189ed89a43752166de44afbef201836611dd29bf0c833f2`). No Git commit is claimed because the supplied archive does not contain Git history.
- B4-A adds a durable, scope-bound PostgreSQL implementation of the B6 computational `ResourceBudgetLedger`, exposed through `PostgresRuntimePersistence.resourceBudgets()`. Its account-row lock serializes reservations across hosts; actual usage overruns remain recorded; repeated commits are idempotent.
- B4-A adds forward-only migration V009. Existing M7 migrations V001-V008 are untouched; this additive M8 capability does not reopen M7.
- B4-A adds PostgreSQL-gated probes for budget restart/actual-usage persistence and multi-host oversubscription, reservation release/deadline, intention restart/objective preservation using stateless JSON codecs, approval binding/revocation/expiry, and unknown external outcomes with no blind redispatch/reconciliation. V009 parser coverage is also added. The first user CI run found one incorrect release-budget expectation (expected 2, actual/semantically correct 4); the cumulative patch corrects the assertion.
- B4-B adds ten full-graph test methods for twelve deterministic fixture executions across eleven distinct scenario IDs through `CognitiveCycleApplicationGraph.compileFull(...)`: exact approved write and observation, proposal/denial, quality-held partial fulfilment without false completion, route and conflicting-evidence provenance, denied/revoked/stale approval, demand and observation invalidation/replanning, and read-only/no-feasible fail-closed exits.
- B4-B tests use deterministic in-memory ports and explicit inventory-reservation handlers. They do not claim real ERP/WMS/carrier integration, route rerouting, typed read-only success, full OS-process restart, or automatic production lifecycle use of the PostgreSQL B6 budget adapter. Those limits and test-to-scenario mappings are recorded in `M8-B4-STATUS.md`, `M8-B4-RELIABILITY-TEST-PLAN.md` and `M8-B4-B-WORKFLOW-MATRIX.csv`.
- The user will validate the complete Java 21 Maven/PostgreSQL reactor in GitHub Actions. No Maven/JUnit run is asserted by the authoring delivery.
