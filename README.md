# M8 Experiment Scenario Factory v1 — Change-only delivery

## Purpose

This changeset adds a deterministic, preregistered scenario-variant generator for the M8 GAP-01–GAP-05 experiment protocol. It creates simulator input fixtures for TRAIN, HOLDOUT and TRANSFER, records split/seed/profile provenance, and rejects malformed or overlapping split allocations.

It does **not** execute treatments, run the agent graph, produce outcome labels, train B1/B3/B6 artifacts, or authorize adaptive activation. It is one prerequisite for the later full-graph experiment adapter, not a confirmatory experiment runner.

## Apply

1. Use the repository state that already contains the previously delivered `M8_GAP01_GAP05_EXPERIMENT_PROTOCOL_AND_FREEZE_CHANGESET_ONLY_2026-11-21` package and the CI-green GAP-01–05 candidate (including CI Fix 1, Fix 2 and Fix 3).
2. Extract this ZIP at the repository root, preserving directory paths and overwriting the five listed existing files.
3. Review `CHANGE_MANIFEST.csv` and `SHA256SUMS.txt`.
4. Run the static checks and the complete Maven reactor command below.
5. Treat hosted GitHub Actions CI as the acceptance gate for the Java/JUnit qualification tests.

Do not apply this package to an older source tree that omits the preceding experiment protocol and split register.

## Change inventory

- 9 added files.
- 5 complete file replacements.
- 0 deletions.
- No production-domain, action authorization, runtime graph or PostgreSQL migration changes.

## Static checks

```bash
python scripts/verify-m8-experiment-scenario-register.py
python scripts/verify-m8-b5-architecture.py
python scripts/verify-m8-r2-evidence.py
python scripts/verify-m8-preb7-f03-f04.py
```

## Full qualification command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

The new JUnit suite is `M8ExperimentScenarioFactoryTest`. It builds all 770 fixtures in-memory for qualification and checks exact counts, distinct seeds and fingerprints, deterministic replay, profile invariants, and proposal-only authority. Java/JUnit tests were not executed in the packaging workspace because the pinned Maven distribution could not be downloaded due DNS resolution failures; a green hosted run is still required.

## Optional provenance-manifest CLI

After reactor dependencies are built/installed, emit one split's input manifest:

```bash
./mvnw -B -ntp -pl supply-chain-agent-evaluation \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.supplychain.agent.evaluation.app.M8ExperimentScenarioFactoryCli \
  -Dexec.args="--split TRAIN --output target/m8-experiment-scenarios/train.csv"
```

The CLI supports `TRAIN`, `HOLDOUT` and `TRANSFER`. It emits input provenance only and prints that no treatment or labels were produced. Follow the preregistered execution order; do not inspect/tune against confirmatory HOLDOUT outcomes.

## Important evidence limitations

The following dimensions are recorded but not fully modeled in v1: timestamped evidence ageing, duplicate-delivery event counts, approval lifetime, and quantitative route capacity (only a zero/nonzero alternative-route availability gate is represented). `MULTI_ALTERNATIVE` changes the planner branching-factor estimate; it does not create additional supplier entities. The profile-preserving overrides for outage, no-feasible-candidate, budget exhaustion and competing allocation are intentional. No conclusions may be attributed to a deferred parameter.

No treatment or empirical result is claimed. B1/B3 remain shadow/disabled; B6 remains `NOT_CALIBRATED`. The full-graph B1/B2/B3/B4/B6 experiment adapter, isolated offline replay, simulator-grounded label pipeline, negative controls and paired integrity analysis remain later gates.
