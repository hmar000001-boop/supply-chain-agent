# M8-GAP01–GAP05 — Seeded scenario generator implementation and qualification

**Generator:** `M8ExperimentScenarioFactory-v2`  
**Protocol:** `M8-GAP01-05-IEP-1.1`  
**Status:** IMPLEMENTED — AWAITING HOSTED CI TEST ACCEPTANCE  
**Evidence status:** No treatment, training, holdout evaluation, transfer evaluation, or outcome-label generation has run.

## Scope

The evaluation module now has a deterministic Java factory that reads the preregistered split register from its runtime classpath resource and constructs real `ScenarioFixture` instances through the existing M8 fixture builder. It does not relabel the legacy scenarios as replications, run planners, invoke the full agent graph, create labels, train artifacts, or dispatch actions.

`DEVELOPMENT_ONLY` rows are counted and required to remain exactly 25 in the register, but are excluded from generated inferential splits. The existing `m8-scenario-register.csv` remains the regression/development corpus.

## Split qualification enforced by code

| Split | Families | Profiles | Units | Variants/profile | Seed band |
|---|---:|---:|---:|---:|---|
| TRAIN | 6 | 14 | 280 | 20 | 1,000,000–1,999,999 |
| HOLDOUT | 6 | 14 | 280 | 20 | 2,000,000–2,999,999 |
| TRANSFER | 3 | 7 | 210 | 30 | 3,000,000–3,999,999 |

The factory rejects wrong split totals, profile/family cardinality mismatches, wrong per-profile counts, repeated unit IDs, repeated fixture fingerprints, repeated effective input parameter fingerprints, repeated seeds within a split, and seed overlap across splits. It verifies that every base scenario ID exists in the versioned 25-scenario catalog and that `allowed_use` matches the split role.

Cross-split qualification constructs simulator fixtures only. It produces no scenario outcome, verifier label, learning update, or treatment output. Actual experiment execution must still use separate TRAIN/HOLDOUT/TRANSFER processes and honor the training-artifact freeze before HOLDOUT is opened.

## Deterministic parameter selection

Every parameter is derived independently using:

```text
SHA-256(protocolId|split|fixtureProfile|seed|parameterName)
```

The unsigned digest is reduced modulo the number of legal stepped values. No ambient clock, default `Random`, global random state, or execution-order state participates in parameter selection. Every unit records the full parameter vector, the split-register profile ordinal, its within-profile variant ordinal, the seed, unit ID, fixture fingerprint, initial-world fingerprint, and effective-input fingerprint.

## Actual fixture changes versus recorded-but-deferred dimensions

The parameter vector is fully recorded, but the current planning-fixure domain model does not represent every preregistered numeric dimension. The CLI therefore emits explicit `applied_parameters` and `deferred_parameters`; the presence of a value in the parameter vector must not be treated as proof that the value influenced the simulator input.

| Dimension | v1 behavior |
|---|---|
| Demand quantity | Applied to the customer-order line; demand-surge profile additionally applies its multiplier |
| Inventory capacity ratio | Applied to lot quantities; `NO_FEASIBLE_CANDIDATE` keeps zero inventory; `COMPETING_ALLOCATION` floors effective stock at 0.8 to preserve its physical reservation invariant |
| Supplier capacity ratio | Applied to supplier product capacity; supplier outage keeps suspended status and zero capacity |
| Supplier/route delay | Applied to supplier lead time or blocked-route transit time; outage delay remains fixed by profile invariant |
| Budget remaining percent | Scales depth, expansion, evaluation and rollout limits; `BUDGET_EXHAUSTION` retains its one-unit hard ceiling |
| Demand-surge multiplier | Applied only to `DEMAND_SURGE` |
| Multi-alternative count | Applied to the M5 branching-factor complexity estimate only for `MULTI_ALTERNATIVE`; it does not create that many distinct supplier entities |
| Competing reservation count | Applied only to `COMPETING_ALLOCATION`; each competing reservation is 3 units and the generated inventory floor preserves total active reservations at or below on-hand stock |
| Route capacity ratio | Only a coarse zero/nonzero alternative-route availability gate is represented; the numeric ratio itself is deferred |
| Evidence age hours | Recorded, deferred; the current fixture evidence references have no timestamp-bearing freshness model |
| Duplicate delivery count | Recorded, deferred; this fixture model has no duplicate-delivery event stream |
| Approval lifetime hours | Recorded, deferred; this planning fixture has no approval-expiry state |

No result may be attributed to a deferred dimension. Quantitative route capacity, timestamped evidence ageing, duplicate event delivery and approval lifetime must be modeled and qualified in the later full-graph experiment adapter before they can be used as experimental factors.

## Preserved profile invariants

- `NO_FEASIBLE_CANDIDATE`: inventory remains zero regardless of generated inventory ratio.
- `SUPPLIER_OUTAGE`: supplier remains `SUSPENDED`, capacity remains zero, and lead time remains 30 days.
- `QUALITY_HOLD`: at least the designated primary lot remains in `QUALITY_HOLD`.
- `READ_ONLY`: no planning action descriptors are introduced.
- `BUDGET_EXHAUSTION`: all effective planning budget ceilings remain at one.
- `COMPETING_ALLOCATION`: generated active reservations do not exceed the primary lot's on-hand units and each competing order's reservation remains below its demand.
- All proposals remain within the existing authority policy; the generator never grants execution authority.

## Runtime command

After the evaluation module and its reactor dependencies have been built/installed, generate exactly one split manifest:

```bash
./mvnw -B -ntp -pl supply-chain-agent-evaluation \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.supplychain.agent.evaluation.app.M8ExperimentScenarioFactoryCli \
  -Dexec.args="--split TRAIN --output target/m8-experiment-scenarios/train.csv"
```

Change `TRAIN` to `HOLDOUT` or `TRANSFER` only when authorized by the preregistered execution sequence. The CLI writes input provenance only; it explicitly reports that no treatment or labels were produced.

## Qualification commands

```bash
python scripts/verify-m8-experiment-scenario-register.py
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

The Python preflight checks byte identity between the protocol and classpath split registers, the 25 development-only rows, split counts, family counts, disjoint seed sets, deterministic hash mapping, legal parameter bounds, and per-profile effective-input uniqueness.

The JUnit tests additionally exercise actual fixture construction, world/fingerprint replay, split disjointness, profile invariants, reservation balances and proposal-only authority. **These JUnit tests have not been executed in this workspace** because the pinned Maven 3.9.16 distribution could not be downloaded: DNS resolution failed for both configured Apache distribution hosts. Hosted GitHub Actions must pass before this generator is considered qualified.

## Remaining execution gates

The generator alone does not make H1–H5 executable. Still required:

1. Full-graph B1–B4/B6 experiment adapter using the actual mechanism services, repositories, authorities and verifiers.
2. Isolated repository reset and offline shadow replay, including PostgreSQL state isolation.
3. Simulator-grounded uncertainty/outcome label pipeline with independently attributable labels.
4. Negative-control and deterministic replay qualification of the full experiment adapter.
5. Immutable TRAIN artifact capture and freeze before confirmatory HOLDOUT is opened.
6. Frozen-artifact TRANSFER evaluation and paired analysis/report integrity.

Until these gates pass, no experiment result is claimed, B1/B3 remain non-activated, B6 remains `NOT_CALIBRATED`, and no adaptive activation is authorized.
