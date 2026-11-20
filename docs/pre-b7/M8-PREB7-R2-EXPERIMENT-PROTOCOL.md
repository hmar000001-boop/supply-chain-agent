# M8-PreB7 R2 — Outcome-feedback evaluation protocol

**Protocol ID:** `m8-r2-preregistered-holdout-v1`  
**Status:** Frozen protocol definition; empirical execution unavailable until a verified outcome corpus and disjoint holdout corpus exist.

## 1. Question

Does using independently verified downstream outcomes to adjust B1 retrieval ranking and B3 admission ranking improve decision utility over the frozen simpler baselines without increasing safety regressions or resource cost?

The test is not whether the policy can change a score. The test is whether an evidence-attributed update generalizes to independent scenarios and beats the baseline on preregistered metrics.

## 2. Baseline

- B1 baseline: `b1-topk-v1` (M8-B3 fixture evidence is retained, including its negative/mixed comparison; no post-hoc tuning is allowed).
- B3 baseline: `b3-required-aware-v1` (existing bounded required-tag/admission behavior).
- Combined baseline version recorded by the status CLI: `b1-topk-v1+b3-required-aware-v1`.
- No adaptive adjustment is enabled by default. Existing constructors inject a neutral adjustment function.

## 3. Evidence and unit of analysis

- Training records must be `VerifiedDownstreamOutcomeEvidence` minted by `B3OutcomeEvidenceService` after receipt linkage and evidence re-verification.
- Each outcome must bind scope, accepted consumer receipt, actual consumed-candidate fingerprint, cycle, workspace, consumer, admission-policy version, verifier identity, evidence reference and verified timestamp.
- Cross-scope mixing, duplicate idempotency IDs, unverifiable evidence, rejected receipts and outcomes for candidates not consumed are excluded by failure (not silently counted as neutral).
- The experimental unit is an independent scenario, not an outcome row, receipt, candidate, or replay.
- Holdout scenario IDs must be unique and disjoint from all training cycle IDs. Repeated/replayed rows do not increase the primary sample size.

## 4. Candidate construction

- Training protocol uses at least 5 verified outcomes per candidate before emitting a non-zero adjustment.
- Adjustment magnitude is bounded to `[-1.0, +1.0]` in the R2 reference evidence path; the trainer's hard defensive ceiling is 5.0.
- The candidate is always `SHADOW` when trained. Training never activates it.
- The training source fingerprint includes exact ordered evidence payloads, IDs/cycles, scope, baseline version, candidate adjustment map, policy/version, observation minimum, maximum adjustment and training timestamp.

## 5. Preregistered holdout criteria

All criteria must pass together:

| Criterion | Threshold |
|---|---:|
| Independent holdout scenarios | At least 12 |
| Lower 95% confidence bound for mean paired utility gain | At least +0.10 |
| Safety regressions | 0 |
| Mean resource-cost delta | Less than or equal to 0 |
| Scope and training/holdout overlap | 0 mismatches / 0 overlaps |

Use paired per-scenario deltas. The report records mean utility delta, lower confidence bound, safety regression count, resource delta, protocol ID, sample size and an evaluation fingerprint over the exact candidate and ordered holdout payload. Do not use replay rows as independent observations.

A report below the minimum sample size remains `SHADOW`. A report exceeding safety or resource limits or failing the lower confidence bound is `REJECTED`. Passing makes a candidate only eligible for a distinct host-authorized activation transition.

## 6. Activation authority

Activation requires a separate host approval bound to the exact scope, candidate policy ID/version and evaluation fingerprint. The host authority receives the complete candidate and report and must validate:

- the protocol ID and frozen threshold definition;
- the provenance and disjointness of the training/holdout dataset;
- the source fingerprint and candidate payload;
- the approver's scope and authority;
- that the report is no older than the approval and that the candidate/baseline version remains current.

The active policy is immutable and scope-bound. It is opt-in; callers in any other scope get zero adjustment. A candidate may not bypass this authority by setting its own disposition.

## 7. Ablations

The hosted R2 run regenerates the existing M8-B3 ablation set and keeps its previous results as baseline evidence. The R2 mechanism suite adds a controlled policy-off/policy-on interaction test for B1 and B3, including a foreign-scope control. Because no real verified outcome/holdout corpus is provided, this interaction test proves the integration/control boundary only; it is not included as an empirical effect size.

Future corpus-backed evaluation must isolate at least:

1. B1 baseline vs B1 selector without outcome adjustment;
2. B3 baseline vs B3 admission without outcome adjustment;
3. B1/B3 with the exact same trained feedback policy;
4. policy-off vs policy-on with identical scenarios, world snapshots, budgets, simulator/evaluator versions and run order controls.

## 8. Current empirical disposition

The current CI input set has zero verified downstream outcomes and zero independent holdout scenarios. The evidence CLI emits `NO_VERIFIED_OUTCOME_CORPUS`, a zero-sample `SHADOW` report with finite metrics, `activationStatus=DISABLED`, and `baselinePreserved=true`. This is an honest negative-evidence state, not a claim that the adaptive method passed or failed a populated experiment. A corpus-backed evaluation remains open.
