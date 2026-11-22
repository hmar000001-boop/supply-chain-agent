# M8 GAP-01–GAP-05 Independent Experimental Protocol

**Protocol ID:** `M8-GAP01-05-IEP-1.0`  
**Protocol status:** PRE-REGISTERED — locked before confirmatory experiments  
**Protocol date:** 2026-11-21  
**Candidate:** `M8-GAP01-GAP05-CI-GREEN-FIX3`  
**Scope:** B1, B2, B3, B4 and B6; simulator-grounded reference implementation  
**Evidence boundary:** deterministic simulator evidence only; no real operational outcome corpus is available.

## 1. Purpose and claims

This protocol evaluates whether the implemented mechanisms change the outcomes they are intended to affect, under matched conditions and independent scenario units. Passing CI establishes that the tested source compiles and its tests pass; it does not establish mechanism effectiveness. The protocol therefore separates implementation validity, experimental validity, and any later activation decision.

The confirmatory claims are restricted to the deterministic supply-chain simulator. Results must not be generalized to real supply-chain operations, human decision quality, production service levels, or real-world calibration without a separately collected and independently verified operational corpus.

### Primary hypotheses

| ID | Mechanism | Treatment | Primary control | Primary endpoint | Minimum practically relevant effect (MPE) |
|---|---|---|---|---|---|
| H1 | B1 memory learning | B1 selector enriched with access and verifier-attributed verified-use/outcome features learned from training units | Same selector and candidate set with an empty/untrained feature repository; top-K and recency-only are secondary controls | Mean verified downstream usefulness per fixed retrieval budget on holdout | ≥5% relative improvement over the strongest pre-registered control; 95% paired CI lower bound above 0; no >10% increase in retrieval/maintenance cost |
| H2 | B2 reusable recovery | Approved, independently validated and activated procedure retrieved in a later cycle; exact scope/condition/evidence/action descriptors revalidated | Same planner, scenario and resources with no active procedure | Successful feasible recovery plan rate; search cost to first feasible plan is key secondary endpoint | ≥5 percentage-point success improvement OR ≥10% lower median search cost with non-inferior success; stale/unsafe procedure acceptance must remain 0 |
| H3 | B3 coalition feedback | Offline shadow-scored coalition preference candidate trained only on verified consumer-receipt/outcome evidence from training units | Same candidates/workspace with no learned preference; top-K and singleton/global-workspace comparators are secondary controls | Verified useful-consumption rate per workspace resource unit | ≥5 percentage-point improvement; ≤10% mean workspace/resource increase; no increase in unsafe or unsupported evidence admission |
| H4 | B4 intention/method authority | Full-graph governed reconsideration and authorized method change in a multi-step episode | Same episodes using the fixed initial planner/method | Objective-satisfaction rate at episode end | ≥5 percentage-point improvement OR ≥5% better objective score; objective drift and unauthorized method changes must remain 0; total resource-use increase must be ≤15% |
| H5 | B6 uncertainty calibration | Target-specific profile trained on training labels and evaluated as a shadow calibration/directive | Uncalibrated assessment, with the same downstream scenario and budget | Holdout Brier score and ECE by EVIDENCE, HYPOTHESIS and PLAN dimensions | Aggregate holdout ECE ≤0.15 and ≥0.02 absolute improvement vs uncalibrated; Brier improves ≥5% relative overall and no dimension worsens by >0.02 |

The MPEs are application-level engineering thresholds chosen for this evaluation; they are **not** values prescribed by CoALA or Fan & Lan. If an MPE is not met, report the result as no practically meaningful effect even when a statistical test is significant.

## 2. Frozen candidate and baseline rules

The candidate is the combined GAP-01–GAP-05 source checkpoint reconstructed from the complete source snapshot and the three CI corrections (Fix 1 initial B6 assessment ordering, Fix 2 B4 planner-session preservation, Fix 3 B4 intention-version assertion). The owner has reported hosted CI green after Fix 3. A workflow URL and Git commit SHA were not present in the supplied confirmation; the freeze record explicitly marks these provenance fields unavailable rather than inventing them.

The candidate must be frozen before generating training or confirmatory outcomes. Record the following for every experiment batch:

1. Git commit SHA (when run from the owner's repository), dirty/clean status, code-tree SHA-256, Maven wrapper/distribution and Java version.
2. Exact scenario-register and experiment-split fingerprints, fixture generator version, simulator/world-model fingerprint and evaluator fingerprint.
3. Dependency tree or resolved dependency versions, PostgreSQL version/migration head where durable repositories are exercised, and all experiment configuration values.
4. Candidate, control and treatment IDs; deterministic seeds; condition execution order; and raw result artifact hashes.

The baseline is a **test-only mechanism ablation of this same frozen candidate**, not a different historical codebase. Every pair must share the same scenario unit, world state, objective, action registry, evidence set, budget, simulator version and evaluator. Only the named mechanism state/control edge may differ. If a clean ablation cannot be made without changing other mechanisms, the pair is invalid and must not be analysed as causal evidence.

Never use the active production-policy state to run this study. B1/B3 candidates remain shadow/disabled; B6 remains `NOT_CALIBRATED` in the operational graph. Experimental replay may evaluate shadow outputs in isolated repositories and must not persist an activation approval or dispatch an external action.

## 3. Experimental units, scenarios and split policy

### 3.1 Unit of analysis

The independent unit is one generated **scenario episode** identified by:

`experimentUnitId = M8GAP01-05:<split>:<fixtureProfile>:<seed>`.

A scenario episode includes its generated world state, objective, initial evidence, action-descriptor set, resource budget, event timeline and deterministic seed. Candidate plans, memory candidates, coalition members, evidence rows, repeated graph nodes and individual calibration dimensions are not independent experimental units. For paired inference, treatment and control are run on the same unit in isolated/reset state.

The generator must reject duplicate `experimentUnitId`, duplicate seed within a split, duplicate immutable unit fingerprint, or identical fixture fingerprints across supposedly distinct seed variants. A seed is not an independent replicate unless the generated world state actually changes. The present `ScenarioFixtureFactory` creates fixed fixture-profile values; it cannot simply be invoked repeatedly with new seed labels and passed off as new data.

### 3.2 Legacy development set

The 25 rows in `scenarios/m8-scenario-register.csv` (seed range 88001–88025) are `DEVELOPMENT_ONLY`. They have already informed implementation and earlier exploratory runs. They may be used for smoke tests, debugging and regression tests, but **must not appear in training, confirmatory holdout, calibration validation, or transfer statistics** for this protocol.

### 3.3 Training, confirmatory holdout and family-transfer sets

The preregistered split is in `M8-GAP01-GAP05-EXPERIMENT-SPLIT-REGISTER.csv`. Sample sizes are fixed before confirmatory execution; they are not a guarantee of power for every endpoint. There is no optional stopping. If confidence intervals are too wide, report inconclusive rather than increasing the sample after inspecting holdout outcomes.

- **TRAIN:** 14 in-domain fixture profiles × 20 variants = 280 independent scenario episodes. These units may be used to construct B1/B3 shadow learning state, B2 approved-procedure artifacts, and B6 calibration profiles. Any hyperparameter selection must use TRAIN only.
- **HOLDOUT:** the same 14 in-domain fixture profiles × 20 different variants = 280 independent scenario episodes. These are confirmatory only. Do not inspect holdout outcomes while choosing thresholds, weights, policies, or model parameters. Each treatment/control pair uses the same holdout unit.
- **TRANSFER:** seven profiles from three entirely held-out scenario families × 30 variants = 210 independent scenario episodes. These families are excluded from B1/B3/B6 training and all tuning. They test family-level distribution shift, safe abstention and calibration/procedure transfer.
- **B6 counts:** every scenario episode may contribute at most one independently sourced label per uncertainty dimension. The scenario counts (280 TRAIN, 280 HOLDOUT and 210 TRANSFER) are upper bounds per dimension, not promised label counts. The preregistered minimum study targets are **at least 100 valid TRAIN labels, 100 valid HOLDOUT labels and 60 valid TRANSFER labels per dimension** (EVIDENCE, HYPOTHESIS, PLAN). A row is counted only when its simulator-grounded label and evidence reference pass validation. The hard service minimum (20 per dimension) is a code precondition, not a sufficient experimental sample target. If one or more dimensions miss the study target, the B6 result is underpowered/inconclusive; do not lower the target after observing results.

### 3.4 Family assignment

In-domain families: `SUPPLY_AVAILABILITY`, `EVIDENCE_QUALITY`, `LOGISTICS`, `DEMAND_PRIORITY`, `PLANNING_COMPLEXITY`, `GOVERNANCE`.

Held-out transfer families: `CONCURRENCY_DELIVERY`, `RESOURCE_MODEL`, `DURABLE_RECOVERY`.

The split register assigns a disjoint seed band per split and fixture profile. Seeds are deterministic identifiers, not a substitute for variant diversity. The implementation of `M8ExperimentScenarioFactory-v1` must vary meaningful domain parameters (e.g. demand/stock/capacity/delay/route/evidence age/confidence/competing reservations) using the seed while preserving each profile's semantic invariants. A fixture fingerprint must therefore change across variants. The variant generator is deterministic and profile-preserving. For each profile, each numeric or categorical parameter is derived from `SHA-256(protocolId|split|fixtureProfile|seed|parameterName)`; no ambient clock, random global state or external service may influence a fixture. Use these bounded parameter domains where the profile applies: demand quantity 40–240 units in steps of 10; inventory/supplier/route capacity ratios 0.0–1.5 of demand in steps of 0.1; supplier/route delay 0–30 days; evidence age 0–72 hours; demand multiplier 1.2–2.0 in steps of 0.1 for `DEMAND_SURGE`; alternative count 2–5 for `MULTI_ALTERNATIVE`; competing reservation count 2–5 for `COMPETING_ALLOCATION`; duplicate delivery count 2–4 for `DUPLICATE_DELIVERY`; remaining budget 10–100% of the unconstrained profile budget for `BUDGET_EXHAUSTION`; and approval lifetime 0–48 hours for approval-expiry variants. Categorical variants (quality state, route availability, provider availability, restart point, approval outcome and evidence conflict pattern) must be sampled from explicit profile-specific allowed sets. Profile invariants take precedence over generic ranges: e.g. an outage remains zero capacity, a quality-hold profile retains at least one held lot, a blocked-route profile retains an unavailable route, and `NO_FEASIBLE_CANDIDATE` remains infeasible. The implementation must place the exact selected values in the fixture manifest. These ranges are protocol engineering choices, not values specified by either paper.

### 3.5 Leakage and independence controls

- No `experimentUnitId`, outcome-evidence ID, B3 receipt ID, B2 validation evidence ID or B6 label-evidence ID may occur in more than one split.
- The B6 trainer's sample-ID and evidence-ID disjointness checks must pass; additionally, profile/family assignments must be disjoint as above.
- B1 may intentionally carry the same exact memory-item revision/reference from training into holdout because the treatment is testing learned value for a persistent memory item. However, train and holdout query episodes, verified outcome events, idempotency keys and their evidence IDs must be disjoint; only the memory object identity is shared. Report this as a planned, mechanism-required linkage, not leakage.
- B3 may intentionally share the registered preference-key vocabulary and candidate schema across splits. Coalition/proposal/receipt/evidence/experiment-unit instances and downstream labels must be disjoint.
- B2's procedure must be approved/staged/validated/activated using training-only cases. Holdout and transfer are retrieval/revalidation/application only; no procedure may be revised in response to a holdout failure.
- Transfer families may not be used to alter the procedure, candidate scorer, calibration profile, activation thresholds or analysis plan.

## 4. Mechanism-specific designs

### B1 — memory accessibility, verified use and utility

Construct one controlled pool of versioned semantic/episodic/procedural memory candidates per experiment episode. Training runs use the actual B3 receipt/outcome verification path and `B1MemoryFeatureLearningService.applyVerifiedOutcome`; the label must be tied to the exact memory revision and a verified downstream-use event. Treatment holdout runs call `enrich` using only TRAIN-updated feature state. Control runs use the identical candidate set and selector with empty feature state; secondary controls are utility-only top-K and recency-only selection. Keep candidate count, retrieval budget, query cues and context-unit budget matched.

The training update uses the mapping already implemented by `B1MemoryFeatureState.applyVerifiedOutcome`: `USEFUL = 1.0`, `NEUTRAL = 0.5`, `HARMFUL = 0.0`. Its expected-utility update is the first-observation reward followed by a 0.2 EWMA (`0.8 × prior + 0.2 × reward`); cue associations move by +0.1 for USEFUL, −0.1 for HARMFUL and 0.0 for NEUTRAL, clamped to [0,1]. The experimental adapter must not change these update constants. This is the learner's training signal, not the independent test endpoint.

For the holdout endpoint, an independent verifier assigns exactly one of the same three signals to each selected, exact-revision memory candidate that has a validated downstream-use attribution. Convert those labels using the same fixed mapping above. For episode `e`, calculate `verifiedUtilityPerBudget_e = Σ(score_i × contextUnits_i) / configuredContextBudget_e`; the candidate set, context budget and verifier rules are identical across paired conditions. The H1 primary estimate is the macro-average of this episode-level quantity across holdout units, with paired treatment-control inference at the episode level. A selected item without a verified attribution is not assigned a positive score; it remains unverified and is excluded from the verified numerator while its consumed context units remain in the budget denominator. Also report the strict useful-only rate (`USEFUL` / all independently verified selected items), neutral and harmful rates, and attribution coverage so a high score cannot be obtained by suppressing verification. Secondary metrics: precision/recall of useful memories, relevant coverage, retrieval latency, token/context cost, maintenance cost, wrong-scope/stale-revision rejection, idempotent replay behavior and forgetting/decay behavior. Do not count selection itself as verified use.

### B2 — approved recovery-procedure reuse and transfer

Create approved procedure drafts from TRAIN cases only. Promotion requires source approval, independent procedural-memory validation, a different independent activation approver, current evidence and exact registered action-descriptor fingerprints. Holdout/transfer episodes retrieve by trusted condition key and revalidate scope, evidence freshness and live action descriptors before passing typed non-executable planning knowledge to the planner. The procedure is a soft planning priority, never a command or capability.

Control is an otherwise identical planning request with no active procedure. Add negative controls for wrong scope, mismatched condition, stale evidence, expired artifact, changed action descriptor, rejected/revoked procedure and no feasible candidate. The expected response for each negative control is safe rejection/abstention, not forced reuse.

Primary metrics: feasible objective-preserving recovery success; secondary metrics: expansions/evaluations/time to first feasible candidate, total resource budget, objective score, plan churn, transfer success and safe rejection rate. Count any stale/unauthorized procedure reuse or hard constraint violation as a safety failure.

### B3 — coalition outcome learning

Create a bounded workspace and at least three comparable candidate coalitions per unit where the scenario supports meaningful competition. Capture exact selected proposal fingerprint, member set, workspace version, broadcast receipt and consumer receipt. A downstream outcome is eligible only when the consumer accepted/consumed the exact coalition and an independent verifier confirms evidence, scope, experiment unit and outcome attribution. TRAIN evidence creates a `SHADOW` policy candidate only.

In HOLDOUT, compare offline shadow ranking/admission decisions against: (1) no learned preference, (2) utility-only top-K, (3) singleton candidates, and (4) a global-workspace comparator where feasible. Do not activate the policy in production. Include rare-evidence and synergy cases; report coverage by evidence class as well as aggregate outcomes.

Primary metric: independently verified useful-consumption rate per workspace resource unit. Secondary metrics: consumer acceptance, missing-coverage rate, diversity/coverage, workspace capacity, preference stability, outcome-attribution rejection rate and resource overhead. No-feedback/empty-corpus must remain fail-closed.

### B4 — intention lifecycle and method authority

Use full graph episodes with a fixed committed objective and a sequence of observations that includes at least one planned reconsideration trigger and one case where method change is authorized. Pair a governed dynamic-method episode against a fixed-method episode with identical observations and budgets. Keep objective ID, objective fingerprint and protected constraints immutable in both conditions.

Primary metric: objective satisfaction by episode completion. Secondary metrics: feasible-plan quality, recovery latency, resource use, unnecessary method changes, reconsideration count, persistence/resume correctness and termination quality. Safety invariants: objective drift = 0; unauthorized method changes = 0; stale candidate/session use = 0; authority mismatch = 0. A B4 test that only calls `RecoveryIntentionService` without the full graph is a component test, not a confirmatory B4 experiment.

### B6 — calibration, decision quality and transfer

Collect one independently verified binary realized-error label for each dimension per episode where the dimension is observable:

- `EVIDENCE`: evidence assessment is materially wrong/incomplete relative to simulator-verified state.
- `HYPOTHESIS`: leading causal hypothesis differs from the simulator's verified cause.
- `PLAN`: selected plan fails its stated objective or violates a hard constraint when evaluated against the simulator outcome.

The label and the predicted uncertainty must be recorded before revealing the outcome. Train the profile from TRAIN only (target ID fixed as `supply-chain-recovery-v1`); validate against HOLDOUT only. Report per-dimension and aggregate ECE, reliability tables/plots, Brier score, calibration slope/intercept where estimable, sample counts and bin occupancy. Also replay decisions with uncalibrated vs calibrated shadow directives and measure verification burden, interruption/downshift/stop decisions, plan quality and resource consumption. Finally evaluate the same frozen profile on TRANSFER without retraining.

The current code's 20-per-dimension minimum and ECE ≤0.15 validation gate are retained as hard software gates. This protocol adds a stricter study target: ≥100 valid samples per dimension in TRAIN and HOLDOUT and ≥60 per dimension in TRANSFER. If any dimension falls below the target, report underpowered/inconclusive and do not lower the threshold after seeing results. Calibration stays `NOT_CALIBRATED` in the operational runtime; no active-profile approval is granted by this experiment.

## 5. Metrics, safety invariants and analysis

### Safety gates (zero tolerance)

All conditions must report: unauthorized external dispatch = 0; approval bypass = 0; objective drift = 0; hard constraint violations = 0; stale B2 procedure acceptance = 0; cross-scope feature/outcome writes = 0; duplicate business effects = 0; B3 outcome attribution without exact accepted receipt = 0; B6 label/evidence overlap = 0; unapproved adaptive activation = 0. Any nonzero count fails that experiment's safety gate; do not average safety failures away.

### Statistical analysis

- Analyse paired treatment-control differences at the scenario-episode level. Candidates, evidence rows, planner nodes and per-dimension labels are nested observations, not separate independent samples.
- Primary continuous endpoints: paired mean difference, standardized paired effect, 95% confidence interval and paired randomization/permutation p-value. Binary endpoints: paired risk difference and exact McNemar/sign test with confidence interval.
- Use 10,000 deterministic cluster/stratified bootstrap resamples, resampling episode units within fixture profile; for transfer, resample within held-out family then profile. Publish bootstrap seed and implementation version.
- Apply Holm correction across the five primary hypotheses. Report unadjusted and adjusted p-values, effect sizes and confidence intervals. No significance-only conclusions.
- Report per-profile and per-family outcomes; include all failures, exclusions and missing labels with pre-registered reasons. No post-hoc exclusion because a result is unfavorable.
- Runtime latency is a secondary metric and must be measured with warm-up, rotating condition order, fixed JVM/environment and separate fresh-run replication. Do not use wall-clock latency from a single CI run as a performance result.
- For calibration, report ECE and Brier by uncertainty dimension and by transfer family. ECE must not be the only measure; the Brier score is a proper scoring rule and reliability counts must be shown. Empty bins remain explicit.

### Missingness and early stopping

Missing/failed labels are reported; no imputation is allowed for primary endpoints. Stop a run for any safety invariant failure, broken determinism, artifact mismatch, training/holdout overlap, generator duplicate, or candidate fingerprint mismatch. Fixes require a protocol amendment with a new version and fresh holdout; never resume a partially inspected holdout after changing the treatment.

## 6. Execution order and preregistered run gates

The sequence is fixed:

1. Verify the candidate code-tree fingerprint against the freeze record; record Git commit and clean status from the owner's repository.
2. Implement and test `M8ExperimentScenarioFactory-v1`; generate the split manifest; validate counts, seed disjointness, profile assignment, non-identical world fingerprints and parameter-range checks.
3. Implement a full-graph mechanism experiment adapter for B1–B4/B6. It must use real services/repositories/verifiers; no simplified metric-only imitation of the mechanisms.
4. Implement test-only isolation/reset for in-memory and PostgreSQL repositories, plus deterministic condition-order randomization and full raw artifact provenance.
5. Run determinism/replay and negative-control qualification. No inference runs before all gates pass.
6. Generate TRAIN artifacts only; train B1/B3/B2/B6 artifacts from TRAIN; freeze their fingerprints before HOLDOUT is opened.
7. Run one confirmatory HOLDOUT batch for H1–H5. Freeze raw outputs immediately and validate all integrity gates.
8. Run the TRANSFER batch using frozen training artifacts and no tuning.
9. Produce report and machine-readable result manifest. Do not activate any adaptive policy.

### Existing commands (exploratory/regression only)

```bash
# Full reactor/CI-equivalent verification
./mvnw -B -ntp -Dphase0.postgres=true clean install

# Existing planner-only benchmark (not a B1/B2/B3/B4/B6 confirmatory study)
./scripts/run-m8-comparison.sh

# Existing deterministic mechanism probes (exploratory/component-level only)
./scripts/run-m8-ablations.sh
```

The current `M8EvaluationRunner` evaluates five planners over 25 fixed fixtures. The current `MechanismAblationRunner` emits deterministic probes; among other limitations, its B1 treatment does not execute the new verifier-attributed feature-learning update, its B3 treatment is not outcome-attributed coalition policy learning, its B2 probe is not future-cycle activated procedure transfer, its B4 probe is not the full cognitive graph, and its B6 probe is not calibration. Their outputs remain useful regression/descriptive artifacts but must not be cited as results for H1–H5.

No confirmatory execution command is declared yet because the full experiment adapter and seeded scenario-variant generator are not implemented. This is an explicit GAP-09 implementation prerequisite, not an instruction to run existing scripts and relabel the output.


### Implementation status addendum — seeded scenario generator

`M8ExperimentScenarioFactory-v1` has now been implemented in the M8 evaluation module. It reads the split register from the module classpath, creates deterministic parameterized simulator fixtures, records full parameter provenance plus applied/deferred dimensions, and enforces the preregistered split/profile/family counts and disjoint seed/identity domains. The old 25 scenarios remain development-only. See `M8-GAP01-GAP05-SCENARIO-GENERATOR-IMPLEMENTATION-AND-QUALIFICATION.md` for exact behavior and limitations.

This is an implementation update, not a protocol change and not an experiment result. The JUnit qualification suite is awaiting hosted CI execution. Several registered parameters are explicitly deferred because the current fixture model does not represent evidence timestamps, duplicate-delivery streams, approval expiry, or quantitative route capacity. Their values are recorded but not applied; they cannot support inferences. The full-graph mechanism adapter, label pipeline, isolated replay and training-artifact freeze remain execution blockers.

## 7. Required result artifacts

Each study run must produce:

- `experiment-manifest.json`: protocol version, code-tree SHA, Git SHA/dirty state, CI run URL/ID if available, Java/Maven/dependencies, PostgreSQL/migration head, generator/world/evaluator fingerprints, split and seeds, treatment/control IDs and exact configs.
- `scenario-units.jsonl`: generated parameter values, unit IDs, split, seed, fixture and objective fingerprints, simulator truth labels only after outcome capture.
- `raw-observations.jsonl`: one row per condition and experimental unit; full metric values, safety counters, resource measures, execution order and trace references.
- `verified-evidence-ledger.jsonl`: evidence ID, source type, verifier, scope, exact outcome/receipt/procedure/memory attribution and validation result. No secret/customer data.
- `trained-artifacts.json`: B1/B3/B2/B6 artifact IDs/fingerprints derived from TRAIN only; HOLDOUT and TRANSFER must include these exact fingerprints.
- `paired-analysis.json`: paired endpoints, effect sizes, intervals, adjusted p-values, exclusions and missingness.
- `integrity-report.json`: split overlap, duplicate IDs, duplicate fingerprints, determinism replay, safety invariants and artifact hashes.
- `experiment-report.md`: methods, results, limitations, negative findings and claims allowed/prohibited.

All artifacts are immutable after generation and SHA-256 checksummed. Raw data must be retained alongside aggregates so a reviewer can independently recompute every reported result.

## 8. Acceptance and activation remain separate

A study is accepted only when the relevant source/tests, preregistration, raw output, split/evidence integrity, safety gates and statistical analysis all pass. Effectiveness acceptance is mechanism-specific; no result for one mechanism closes another mechanism's gap.

Even a successful simulator study does not authorize adaptive policy activation. B1/B3 activation still requires its separate evidence review and approval flow; B6 profile activation still requires independent reviewer/approver authorization and target/domain scope. Real-world activation claims require independently verified operational outcomes and a separate validation study.

**Current status:** protocol and split plan are locked; candidate snapshot is frozen at the recorded code-tree fingerprint; confirmatory experiment execution is BLOCKED pending implementation and qualification of the variant generator and full mechanism experiment adapter. GAP-09 remains open until raw independent results have been run, audited and accepted.
