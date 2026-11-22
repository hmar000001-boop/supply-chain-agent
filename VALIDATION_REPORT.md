# Validation report — M8 Experiment Scenario Factory v1

## Delivery identity

- Change set: `M8ExperimentScenarioFactory-v1`
- Protocol: `M8-GAP01-05-IEP-1.0`
- Added project files: 9
- Replaced project files: 5 (complete contents)
- Deleted project files: 0
- Production behavior/authorization changes: none outside deterministic evaluation fixture construction.

## Static validation — PASS

- Protocol split register and runtime classpath resource are byte-identical.
- Exactly 25 `DEVELOPMENT_ONLY` rows are excluded from the generator.
- Split register declares 280 TRAIN, 280 HOLDOUT and 210 TRANSFER units.
- Split seed bands are disjoint; family/profile counts are 6/6/3 families and 14/14/7 profiles.
- Every profile has 20/20/30 distinct effective parameter fingerprints.
- SHA-256 parameter-selection formula matches the preregistration.
- YAML experiment registry parses; all base scenario IDs listed in generated rows exist in the legacy scenario catalog.
- Architecture guard passes: 11 modules, acyclic internal dependency graph, declared imports and inventory/CI mirror.
- R2 evidence guard passes.
- F-03 and F-04 integration guards pass.
- Python static verifier compiles with `py_compile`.
- ZIP integrity, source-file checksums and sidecar checksum verified after packaging.

## Java test validation — NOT RUN LOCALLY

`M8ExperimentScenarioFactoryTest` was added and included in the evaluation-module inventory. It is designed to construct all 770 fixtures and exercise:

- exact split counts, seed bands and cross-split identity separation;
- deterministic parameter derivation and fixture replay;
- per-profile unique effective inputs;
- no-feasible-candidate, outage, quality-hold, read-only and competing-reservation invariants;
- action proposal scope and deferred parameter disclosure.

The pinned Maven Wrapper attempts to download Maven 3.9.16 from the configured Apache distribution hosts. DNS resolution for those hosts is unavailable in this execution environment. Therefore Java compilation, Surefire execution, and PostgreSQL-enabled Maven integration have **not** been verified locally. Do not infer a green build from static checks.

## Hosted CI acceptance

Run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Accept this generator only after the hosted CI run reports all reactor modules successful and `M8ExperimentScenarioFactoryTest` passes.

## Research status

- No treatment was executed.
- No outcome labels were generated.
- No B1/B3 learning artifact was trained or activated.
- No B6 profile was calibrated or activated.
- No confirmatory or transfer result is claimed.
- Full-graph experiment adapter, offline isolated replay, simulator-grounded labels, negative controls, paired analysis and raw-artifact integrity remain open.
