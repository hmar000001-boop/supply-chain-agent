# M8 Mechanism-Specific Experiment Harness and Independent Evidence Pipeline v1

**Changeset type:** repository-root-relative, changes only  
**Parent source:** the owner-accepted protocol/freeze + Scenario Factory v1 + Full Graph Adapter v1 releases, based on the GAP-01–05 CI-green Fix3 source  
**Protocol:** `M8-GAP01-05-IEP-1.1`  
**Candidate:** `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1`  
**Code-tree SHA-256:** `f16e581e617047874a57da2118eb029be23004d786a35f6939ccd319e89e1c42`

## Apply

1. Preserve a clean working-tree backup or commit your current accepted state.
2. Extract this ZIP at the repository root, keeping the archive's relative paths. Allow the 28 modified files to be replaced and add the 39 new files.
3. Do not extract this changeset into a subfolder and do not combine it with an older full-source archive.
4. Run the full reactor command shown below and inspect the complete Maven reactor summary.

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

## What this increment provides

- Typed B1/B2/B3/B6 TRAIN artifact inputs and a fail-closed `MechanismTrainingArtifactBuilder`.
- Content-derived child and enclosing artifact fingerprints and independent artifact reconstruction/verifiers.
- B1/B2/B3/B4/B6 mechanism-specific baseline/treatment plans and full-graph HOLDOUT harness.
- Typed mechanism evidence capture from the actual graph and an independently created simulator replay of the selected plan.
- Target-specific B6 HOLDOUT label producer contract and independent label re-verification.
- Exact 280-unit HOLDOUT batch validation and atomic JSONL output.
- Protocol 1.1 shared synthetic scope, candidate fingerprint record, CI/static guard and implementation documentation.

## Critical composition boundary

The API deliberately requires the host to provide independent evidence verifiers, TRAIN evidence collection, a trusted full-graph session factory, fresh simulator/evaluator instances, and a target-specific B6 holdout label producer. There is no permissive default composition. The host must wire the supplied mechanism treatment plan into real graph ports and unique isolated repositories/checkpoints; merely echoing the plan fingerprint is invalid.

The TRAIN telemetry batch does not generate verified outcomes or trained artifacts. The typed builder only accepts independently verified, caller-collected TRAIN evidence. The pipeline has not run any experiment. The inferential 10,000-replicate clustered bootstrap/Holm report and TRANSFER runner remain unimplemented. Simulator outputs are not business outcomes. B1/B3 remain non-activated and B6 remains `NOT_CALIBRATED`.

## Validation status

Static source/architecture/evidence guards and a focused Java 21 type-check of the training artifact builder/dependency closure passed in the build workspace. The complete Maven/Surefire and PostgreSQL suite was not executed locally; hosted CI acceptance is pending. See `VALIDATION_REPORT.md` and the pipeline design document for the exact boundaries.
