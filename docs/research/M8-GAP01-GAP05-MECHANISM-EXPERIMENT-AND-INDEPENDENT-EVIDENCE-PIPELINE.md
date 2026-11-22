# M8 GAP-01–GAP-05 Mechanism-Specific Experiment Harness and Independent Evidence Pipeline

**Implementation:** `M8MechanismSpecificExperimentPipeline-v1`  
**Protocol:** `M8-GAP01-05-IEP-1.1`  
**Scope:** B1, B2, B3, B4 and B6  
**Implementation status:** SOURCE IMPLEMENTED; HOST COMPOSITION AND HOSTED CI ACCEPTANCE PENDING  
**Evidence status:** No TRAIN artifact bundle has been built; no mechanism-specific HOLDOUT pair or batch has been executed; no empirical effectiveness result is claimed.

## 1. Purpose and boundary

This increment adds a mechanism-specific research path on top of the seeded scenario factory and the full cognitive-cycle graph adapter. It provides typed TRAIN artifact construction, content-derived fingerprints, independent artifact verification, matched mechanism-specific HOLDOUT execution, fresh simulator replay of the graph-selected plan, and immutable pair/batch evidence output.

It is an evaluation subsystem. It does not change operational activation policy, authorize external dispatch, or create real business outcomes. The shared `tenant:m8-experiment:shared-research-v2` scope is synthetic-only. Mutable repositories, action ports, graph objects and checkpoint namespaces must still be isolated per arm and graph session.

The 25 legacy fixtures remain development-only. The locked experiment populations remain 280 TRAIN, 280 HOLDOUT and 210 TRANSFER units. The current Java API implements the TRAIN artifact and HOLDOUT path; TRANSFER remains closed.

## 2. Components and contracts

### 2.1 TRAIN telemetry and trusted evidence collection

`FullGraphExperimentTrainingBatchRunner` accepts the exact preregistered 280-unit TRAIN set. It runs the full graph in matched isolated arms and writes telemetry only. Its output is explicitly `FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS` with `confirmatoryInferenceAllowed=false`.

That telemetry batch is not the source of a business outcome by itself. A domain-owned evidence collector must produce the typed source records below from the TRAIN graph runs and the independently verified simulator/consumer/approval facts. The source records are then passed to `MechanismTrainingArtifactBuilder`.

### 2.2 Typed artifact builder

`MechanismTrainingArtifactBuilder.build(candidateCodeFingerprint, inputs, frozenAt)` constructs one immutable `MechanismTrainingArtifactBundle`. The builder is fail-closed and calls `MechanismTrainingArtifactVerifier.verify(bundle)` before returning.

The typed input records are:

- **B1Input:** exact initial and final `B1MemoryFeatureState` snapshots, verifier-attributed `DownstreamOutcomeEvidence`, receipt evidence and verifier identity. All feature references must use the synthetic shared research scope. The initial/final key sets must match and outcome evidence must cover at least two distinct TRAIN units.
- **B2Input:** approved `ReusableRecoveryProcedure` artifacts keyed by condition and the exact TRAIN units supporting each condition. Every procedure/artifact must use the research scope; each declared source set must contain at least two TRAIN units.
- **B3Input:** verifier-minted `VerifiedWorkspaceCoalitionOutcomeEvidence`, receipt evidence, policy parameters and training time. The builder trains via `B3CoalitionFeedbackPolicyService` and rejects any result other than `Disposition.SHADOW`.
- **B6Input:** `B6SimulatorLabelEvidence`, trainer identity and independent label-verifier identity. Labels must be TRAIN-only, scoped to the research scope and target one exact calibration task. There must be at least 100 distinct source TRAIN units; the independent verifier additionally requires at least 100 samples per uncertainty dimension. The trainer must produce `Status.TRAINED`, never validated or activated.

The bundle binds the exact 280 TRAIN effective-input fingerprints. It cannot represent HOLDOUT or TRANSFER evidence. Each child artifact fingerprint is recomputed from its complete content, including evidence ledgers, state snapshots, source unit fingerprints, procedures, receipts, calibration observations and provenance. The enclosing bundle fingerprint binds the child fingerprints, protocol, code candidate, exact TRAIN set and freeze time.

### 2.3 Independent TRAIN artifact verifier

`MechanismTrainingArtifactVerificationServices` requires four explicit verifier dependencies. There are no permissive defaults:

- B1 downstream outcome verifier;
- B2 procedure/artifact verifier at a supplied verification time;
- B3 coalition outcome verifier;
- B6 simulator uncertainty-label verifier.

`MechanismTrainingArtifactVerifier` checks and reconstructs rather than trusting stored counters or fingerprints:

- B1 receipt/outcome references, distinct source TRAIN units, exact initial-to-final feature state transition and child content fingerprint;
- B2 approved active procedure state, scope, objective/evidence provenance, exact declared source TRAIN units and child fingerprint;
- B3 accepted receipt linkage, selected coalition identity and member fingerprints, verifier-minted outcome capability, candidate source IDs/order, recomputed preference adjustments/source fingerprint, and SHADOW disposition;
- B6 label verifier result, unique sample/evidence identities, exact TRAIN split, target/scope, training dataset fingerprint, profile sample/evidence hashes, minimum samples and independently reconstructed calibration bins. A TRAINED profile must have no validation/activation metadata.

The builder cannot create a valid bundle from empty placeholders, caller-supplied hashes alone, non-TRAIN evidence, or unverified outcome records.

### 2.4 Mechanism-specific matched HOLDOUT harness

`MechanismSpecificExperimentHarness.runHoldoutPair(unit, mechanism)` accepts HOLDOUT units only. Before it creates either graph session, it:

1. checks the protocol and split;
2. verifies the candidate code fingerprint against the frozen bundle;
3. independently re-verifies the entire TRAIN bundle;
4. builds the exact baseline/treatment plan for the selected mechanism;
5. creates separate full graph sessions for the two arms;
6. verifies the session factory bound the actual graph condition to the plan fingerprint, correct mechanism, arm and HOLDOUT input.

`MechanismExperimentPlanFactory` maps the frozen artifact bundle to the preregistered treatment. The host-provided `MechanismSpecificExperimentSessionFactory` must wire that plan into the actual graph services/repositories; it must not merely echo a fingerprint. The adapter checks isolation and matched exogenous inputs, but cannot introspect arbitrary behavior hidden inside caller-supplied ports. Therefore the session factory is a trusted composition boundary, and its condition fingerprint must bind every treatment/control setting.

Mechanism interventions are:

- **B1:** baseline starts without TRAIN-derived feature states; treatment starts from the exact frozen B1 feature snapshot. The evidence verifier confirms those exact initial state fingerprints and the frozen source ledger.
- **B2:** baseline receives no active reusable procedures or reusable action hints; treatment receives the exact approved TRAIN procedure set. The collector records the real `PlanningRequest.reusableRecoveryProcedures()` and the proposed reusable action descriptors, not just the experiment bundle.
- **B3:** baseline has no learned coalition overlay; treatment receives the exact frozen candidate as an offline SHADOW preference overlay. The run verifier recomputes each configured/applied adjustment and confirms no activation.
- **B4:** baseline is the fixed initial method; treatment uses the governed reconsideration/method-authority path. Both arms must keep the same objective fingerprint and protected constraints. Every transition must form a contiguous, attributed, append-only version sequence, with no objective drift.
- **B6:** baseline exposes raw uncertainty; treatment applies the exact frozen TRAINED profile only as a shadow transformation. Both arms must have identical raw uncertainty values. The run verifier reconstructs treatment values and requires `NOT_CALIBRATED` status.

The adapter stops at the first terminal/interruption boundary. It never resumes an approval or clarification, supplies no approval response, and dispatches no state-changing action.

### 2.5 Independent simulator replay

After each graph run, `IndependentSimulatorPlanVerifier` retrieves the selected planning artifact and replays its proposed actions using a newly created `PlanningWorldModel` and `CandidateEvaluator` from `IndependentSimulatorReplayFactory`. These are separate instances from the graph session's world/evaluator.

The replay checks the plan against simulator facts independently of the graph's metric collector. A replay record is classified as `SIMULATOR_ONLY_NOT_BUSINESS_OUTCOME`. It is not evidence of actual downstream business effectiveness.

### 2.6 B6 HOLDOUT labels

For B6 only, `B6HoldoutLabelProducer` receives the HOLDOUT unit, the baseline raw uncertainty prediction and the baseline independent simulator replay. It must derive labels without consulting treatment outputs. The harness then re-verifies every emitted `B6SimulatorLabelEvidence` through the independent `SimulatorUncertaintyLabelVerifier`, checks the exact HOLDOUT unit/target/dimension and disjoint sample/evidence identities, and only then attaches labels to the pair.

The current implementation does not include a universal label producer: the correct label semantics depend on the target task and simulator ground truth. The host must supply and own this adapter. A missing or invalid producer blocks B6.

### 2.7 Pair and batch output

`MechanismSpecificExperimentPair` stores the graph pair, typed B1/B2/B3/B4/B6 evidence, independent simulator evidence, optional B6 labels and training artifact fingerprint.

`MechanismSpecificExperimentBatchRunner` accepts exactly the 280 preregistered HOLDOUT IDs, checks every unit's protocol, split, seed, parameters, fixture and effective-input fingerprint, sorts by stable unit ID, executes each pair, and atomically writes a new JSONL artifact without overwriting an existing path. The envelope binds protocol/generator, mechanism, unit-set fingerprint, candidate fingerprint and frozen TRAIN artifact fingerprint. It explicitly sets `realWorldOutcomeClaimsAllowed=false`.

The current batch API runs one mechanism at a time. It does not run TRANSFER and does not compute the inferential H1–H5 analysis.

## 3. Required host composition

The research pipeline intentionally has no default or permissive application host. Before execution, the owner must provide:

1. `MechanismTrainingArtifactVerificationServices` backed by independent B1/B2/B3/B6 verification implementations;
2. source collection adapters that derive `MechanismTrainingArtifactBuilder.Inputs` from TRAIN execution traces, real typed receipts, approved procedures and separately verified simulator labels;
3. a `MechanismSpecificExperimentSessionFactory` that creates new `FullGraphExperimentSession` instances, injects the requested B1/B2/B3/B4/B6 plan into the actual graph, and uses unique per-arm/session repositories and checkpoint namespaces;
4. an `IndependentSimulatorReplayFactory` that creates a fresh simulator world/evaluator for every replay;
5. a B6 `SimulatorUncertaintyLabelVerifier` and target-specific `B6HoldoutLabelProducer`;
6. a sandbox attestation and audit instrumentation proving no external connector, production credential, network call or state-changing action dispatch is reachable.

Do not implement a test adapter as if it were an evidentiary verifier. In-memory fixtures may be used for unit tests, but confirmatory execution requires independently operated verifiers and trusted domain adapters whose identities and versions are recorded in the run manifest.

## 4. Required execution sequence

1. Apply the changeset and run the full Maven/CI suite.
2. Record the actual repository Git SHA, clean/dirty state, CI workflow/run ID and exact source candidate fingerprint. The hosted CI result must correspond to that Git SHA.
3. Qualify the scenario generator and full graph adapter with deterministic replay and negative controls.
4. Execute the exact 280-unit TRAIN telemetry run for each required mechanism/condition.
5. Collect typed TRAIN evidence from trusted host adapters; independently verify source records; build the single exact TRAIN artifact bundle.
6. Freeze and checksum the full bundle. Re-run bundle verification from persisted artifacts. Do not use HOLDOUT/TRANSFER evidence to build, tune or repair this bundle.
7. Only then instantiate the HOLDOUT harness and execute the exact 280-unit batch for each mechanism.
8. Independently verify pair artifacts and the full batch integrity before computing endpoints.
9. TRANSFER remains blocked until a separate frozen-artifact transfer runner and acceptance gate exist.

Any source, adapter, protocol, label semantics or treatment change after the TRAIN artifact freeze requires a new candidate ID and a protocol amendment. If HOLDOUT has been opened, do not reuse it after a change; produce a new independent holdout set/version.

## 5. Evidence and acceptance boundaries

The following are implementation capabilities, not completed experiments:

- typed TRAIN builder and content fingerprints: implemented;
- independent B1/B2/B3/B6 TRAIN artifact verification: implemented with caller-supplied verifier implementations;
- B1/B2/B3/B4/B6 matched full-graph HOLDOUT harness: implemented with host-supplied treatment/session ports;
- independent simulator plan replay: implemented with a caller-supplied fresh simulator factory;
- B6 independent HOLDOUT label path: implemented with a target-specific caller-supplied producer/verifier;
- exact 280-unit HOLDOUT batch writer: implemented;
- inferential 10,000-resample profile-stratified bootstrap, paired hypothesis tests and Holm correction: NOT IMPLEMENTED;
- TRANSFER execution and acceptance: NOT IMPLEMENTED;
- trusted host composition and actual run manifests: NOT PROVIDED;
- actual training bundle, HOLDOUT pair output and real-world outcome corpus: ABSENT.

No experiment has been run by this increment. There are no outcome labels or effectiveness estimates to report. Keep B1/B3 non-activated and B6 NOT_CALIBRATED. Even successful simulator studies do not authorize activation or support real-world business performance claims.

## 6. Validation status of this delivery

Static guards cover the mechanism plan, source-unit restrictions, content fingerprints, receipt/outcome attribution, calibration label provenance, HOLDOUT admission order, no graph resume/dispatch, simulator replay isolation, and protocol metadata. A focused Java 21 `javac` type-check is available for the training builder and its internal source dependencies. The complete Maven reactor and JUnit/Surefire suite still require hosted CI. No CI-green claim is made for this increment until the repository owner reports the hosted run result.
