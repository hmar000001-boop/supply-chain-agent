# M8 Experimental Integrity, TRAIN Freeze, Confirmatory Analysis and Readiness Ledger v1

**Status:** implementation contracts and deterministic tests added; hosted CI and host-specific qualification pending. No TRAIN/HOLDOUT/TRANSFER experiment is claimed to have run.

## 1. Scope

This increment consolidates the remaining implementable pieces of the M8 research pipeline:

- exact TRAIN telemetry integrity re-audit;
- deterministic paired statistical inference and multiple-testing correction;
- independent endpoint-evidence admission for HOLDOUT and TRANSFER;
- content-addressed complete typed TRAIN-bundle archive with codec read-back;
- an immutable TRAIN freeze manifest bound to code, Git, hosted CI, dependencies and evidence;
- a dependency-ordered readiness ledger that emits concrete reminders without opening a split or asserting work is complete.

It does not create missing outcomes, generate labels from telemetry, start a batch, open HOLDOUT, activate B1/B3/B6, or turn simulator outcomes into real operational outcomes.

## 2. TRAIN telemetry integrity

`M8ExperimentArtifactIntegrityAuditor` re-reads the exact five per-mechanism JSONL files and verifies:

1. exact TRAIN split and 280 episode records per mechanism;
2. exact protocol and scenario-generator IDs;
3. exact sorted episode-ID set;
4. exact episode-ID to effective-input-fingerprint mapping;
5. the byte digest of the full file and the body digest of pair records;
6. the runner's canonical unit-set digest (SHA-256 of sorted `experimentUnitId` values);
7. the telemetry-only evidence kind and `confirmatoryInferenceAllowed=false` marker;
8. no unfinished `.pending` output in the artifact directory;
9. distinct output paths for the five mechanisms.

The audit receipt itself is revalidated by recomputing the combined audit fingerprint and comparing current byte counts and digests with the manifest. The telemetry artifacts remain telemetry; they are not outcome-label evidence.

## 3. Confirmatory analysis

`M8ConfirmatoryAnalysisEngine` admits only exact registered HOLDOUT or TRANSFER unit sets. B1/B2/B3/B4 endpoint records are supplied as typed `M8VerifiedEndpointEvidence` and must pass a separately injected `M8VerifiedEndpointEvidenceVerifier`. Each record binds the split, unit ID, effective-input fingerprint, profile, family, baseline/treatment values, verifier identity and source-evidence fingerprint.

The engine does not infer those endpoints from graph telemetry, planner scores, a scripted reasoning response or an LLM judge. B6 labels are handled separately through the existing `SimulatorUncertaintyLabelVerifier` and the existing per-dimension B6 holdout/transfer report builders.

### 3.1 Statistical procedure

`M8PairedStatisticalEngine` is custom Java code, dependency-free. It uses:

- 10,000 deterministic profile-stratified paired bootstrap resamples for HOLDOUT;
- 10,000 deterministic hierarchical bootstrap replicates for TRANSFER: sample held-out scenario-family clusters with replacement, then resample episode units within each fixture-profile stratum of each selected family;
- 95% percentile confidence intervals;
- mean and median paired effects and paired Cohen's dz;
- continuous paired sign-flip Monte Carlo inference with 10,000 draws;
- exact two-sided McNemar/sign inference for binary paired endpoints;
- deterministic seeds derived from the preregistered protocol/split/hypothesis/version.

HOLDOUT applies Holm step-down correction across H1-H5. TRANSFER results are explicitly diagnostic and do not receive confirmatory/Holm language. The report records the implementation ID, resample count, deterministic seed, effect sizes, intervals, raw/adjusted p-values and inference method. The statistical kernel is custom Java and dependency-free; it does not call an LLM judge or rely on Python/R statistical execution.

### 3.2 Endpoint definitions and MPE

The preregistered primary endpoints remain:

- H1: verifier-attributed useful retrieval per fixed budget;
- H2: feasible recovery success, with median search cost as the key secondary endpoint;
- H3: verified useful consumption per workspace resource unit;
- H4: objective satisfaction, with objective score and total resource use as secondary endpoints;
- H5: B6 calibration quality, including ECE by uncertainty dimension and Brier score.

H2's secondary cost comparison uses median baseline/treatment search cost, not the mean. MPE thresholds remain those in the frozen protocol; they are engineering thresholds, not claims made by CoALA or Fan & Lan.

### 3.3 Independent safety evidence (zero-tolerance gate)

`M8ExperimentSafetyEvidence` records a separate baseline/treatment counter set for each of the five mechanisms and the exact split episode count. Ten counters are independently reconstructed: unauthorized external dispatch, approval bypass, objective drift, hard-constraint violations, stale-procedure acceptance, cross-scope feature/outcome writes, duplicate business effects, unattributed coalition outcomes, B6 label/evidence overlap, and unapproved adaptive activation. Every counter must be zero. Negative values, missing mechanism/arm entries, incorrect split counts, an unknown verifier identity or a non-reproducing evidence fingerprint are rejected.

`M8ExperimentSafetyEvidenceVerifier` is a mandatory host-supplied independent verification port. The confirmatory HOLDOUT/TRANSFER analyzer invokes it and checks the zero-tolerance matrix before calculating any hypothesis. The TRAIN freeze service performs the same check before archiving the typed bundle and includes the safety evidence fingerprint and verifier identity in the immutable freeze manifest. Safety counters cannot be averaged across mechanisms, arms or episodes; any non-zero counter blocks the relevant gate.

## 4. TRAIN artifact freeze

`M8TrainingArtifactFreezeService` requires all of the following:

- a complete and revalidated five-mechanism TRAIN telemetry audit;
- independently verified TRAIN safety evidence with all ten counters at zero for both arms of all five mechanisms;
- a typed `MechanismTrainingArtifactBundle` bound to the exact TRAIN fingerprints and candidate code fingerprint;
- independent `MechanismTrainingArtifactVerifier` verification before and after archive round-trip;
- a content-addressed archive whose host-supplied, versioned codec can encode and decode the complete typed bundle;
- a successful hosted CI result, exact commit and candidate source-tree fingerprint, clean Git working tree, dependency manifest fingerprint and stable workflow/run identity;
- a trusted `M8FreezePrerequisiteVerifier` which independently attests to the Git/CI/source/dependency facts;
- a stable named bundle-verifier identity.

The `FileSystemM8TrainingArtifactArchive` refuses content-address collisions and checks byte count, SHA-256 and decoded bundle fingerprint. The codec is deliberately an explicit host integration port: this project does not silently use Java native serialization, and an implementation must be versioned and tested against every nested typed artifact.

The freeze manifest is create-new/atomic and content-addressed. Its digest binds the complete canonical per-mechanism artifact records, safety evidence fingerprint and verifier, bundle archive identity and all source/CI/dependency provenance fields. It records five telemetry artifact hashes, audit digest, candidate and commit fingerprints, CI workflow/run, dependency fingerprint, archive hash/codec, verifier IDs and freeze timestamp. It hard-codes both `holdoutAccessOpened=false` and `transferAccessOpened=false`. Freeze does not grant access to either split.

## 5. Readiness ledger and reminders

`M8ExperimentReadinessLedger` is a local, dependency-ordered status ledger. A gate is COMPLETE only when a caller supplies a typed immutable receipt (fingerprint, verifier identity and verification time). Receipts supplied before prerequisite gates, or timestamped before prerequisite verification, are rejected. The ledger emits the next actionable reminder and serializes a snapshot for archival.

The ledger is not a background scheduler and does not run tasks automatically. It cannot turn a source file or configuration flag into proof that an experiment has executed.

## 6. Remaining host integration

The following must be supplied and qualified by the application/research host before the pipeline is runnable end-to-end:

1. A reusable concrete `DeterministicTrainPortsFactory` extracted from the current test-only scenario ports and wired to per-arm isolated repositories, simulator and graph configuration.
2. Trusted B1/B2/B3/B4 endpoint verifiers and a host endpoint extractor that emits exactly one record for every required endpoint and registered unit.
3. Independent B6 simulator uncertainty-label production and verification for the exact split units and all uncertainty dimensions.
4. A versioned full-content implementation of `M8TrainingArtifactBundleCodec`.
5. A trusted `M8FreezePrerequisiteVerifier` tied to actual Git and hosted CI metadata.
6. A trusted `M8ExperimentSafetyEvidenceVerifier` tied to independently reconstructed graph, action-dispatch, world-state, memory/workspace and B6 label/evidence audit sources for every split and arm.
7. A run controller that executes the five complete TRAIN batches, checks all safety counters, gathers mechanism-specific outcomes, builds the typed bundle and emits independent receipts.

Until these are present, the readiness ledger correctly reports downstream stages as blocked.

## 7. Evidence boundary and current status

- No experiment has been executed by this increment.
- The 280 TRAIN telemetry batches are not equivalent to verified outcome data.
- H1-H4 analysis cannot run without independently verified endpoint records for every registered episode.
- HOLDOUT/TRANSFER analysis cannot run without a separately verified zero-violation safety matrix for all five mechanisms and both arms. TRAIN freezing has the same safety requirement.
- H5 cannot run without independently verified B6 simulator labels for every episode and every dimension.
- Simulator-derived outcomes remain `SIMULATOR_ONLY_NOT_REAL_WORLD_OUTCOME_EVIDENCE`.
- No verified operational supply-chain outcome corpus is currently available.
- B1/B3 remain SHADOW/non-activated. B6 remains NOT_CALIBRATED. No activation authority is added.
- This increment does not claim total CoALA or Fan & Lan paper conformance; GAP-06 through GAP-10 remain separately tracked.

## 8. Validation record

The following standard-library/static checks are required and were run for this source increment:

- M8 architecture, dependency direction, source inventory and CI mirror guard;
- R2 evidence guard;
- F-03/F-04 guards;
- M8 mechanism evidence and scenario split guard;
- protocol registry/split metadata alignment;
- archive/source package hashes.

Java 21 API-stub compilation and focused smoke tests passed for the research contracts. The full Maven/Surefire/PostgreSQL reactor was not run locally because the Maven Wrapper distribution host could not be resolved from this environment. Hosted CI must compile and execute the JUnit additions before this increment is accepted.
