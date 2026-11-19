# M8-B3 — Experimental results clarification

**Result version:** `M8-B3-v1`  
**Execution:** deterministic Java 21 CLI over the accepted production mechanism classes  
**CI acceptance:** pending user-run GitHub Actions; Maven/JUnit were not run during this delivery

## 1. Correct denominators

| Quantity | Count | Meaning |
|---|---:|---|
| Studies | 8 | B1, B2, B3, B4, B6 and three selected interactions |
| Scenario definitions / experimental units | 19 | Unique deterministic scenario IDs |
| Primary arm observations | 45 | All study conditions across those scenarios |
| Paired contrasts | 26 | Treatment-minus-control comparisons within the same scenario |
| Deterministic replay rows | 18 | Re-execution of control/final-treatment arms in nine selected scenarios |
| Raw observation rows | 63 | 45 primary + 18 replay rows |
| Replay logical fingerprint matches | 18 / 18 | All selected replays matched their original logical fingerprints |

The 18 replay rows are repeatability checks, not additional experiments and not independent samples. The experimental unit is the scenario (19 units), and paired deltas must be interpreted within those units. The 26 contrasts may share a scenario where a study has more than one treatment arm. No significance test or population-level confidence interval is reported.

## 2. Results by mechanism

### B1 — retrieval selection, use edge and maintenance accounting

Across four fixtures, mean relevant recall / precision were:

| Condition | Recall | Precision | Downstream-use count (mean) | Estimated maintenance cost, all candidates (mean) | Estimated maintenance cost, selected (mean) |
|---|---:|---:|---:|---:|---:|
| Utility top-k, no B1 state | 1.0000 | 0.5625 | 4.0 | 0.0000 (not modeled in this arm) | 0.0000 |
| B1 selector, log only | 0.8333 | 0.4375 | 0.0 | 2.9950 | 2.0925 |
| B1 selector, use edge enabled | 0.8333 | 0.4375 | 4.0 | 2.9950 | 2.0925 |

Interpretation: on this small fixture set and the current B1 scoring policy, plain utility top-k selected more of the pre-labeled relevant memories. Turning on the B1 downstream-use edge changed whether selected candidates were actually used (0 versus 4) but did not change the selector's candidate ranking relative to the log-only arm. B1's cost estimate is visible, but it did not reduce the fixture's actual maintenance events: actual maintenance cost was the same in all arms (mean 0.8625), because the same events were replayed for the candidate population.

This is a useful negative/mixed result, not a general rejection of B1. It suggests this B1 parameterization needs calibration against richer relevance and downstream-outcome labels before it can claim net retrieval benefit. The implementation has no outcome-attributed adaptive selector-weight update, so the learning-update ablation is not evaluable.

### B2 — typed impasse consolidation

Each of the three fixtures supplies three signals, including one duplicate conflict. The generic retry control schedules three resolution units; typed B2 consolidation yields two typed substates/resolution units. This is one fewer resolution unit per fixture (33.3% in these designed duplicate-signal cases), while retaining the original signal count and leaving parent context untouched.

Interpretation: the probe supports typed grouping and reduced duplicate resolution work for these cases. It does not establish lower wall-clock time, better resolution quality, or reduced effort in a broader distribution.

### B3 — bounded workspace admission and broadcast

All three fixtures show the same aggregate result:

| Condition | Candidates admitted | Capacity units used | Required tags covered | Required tags missing | Broadcast accepted |
|---|---:|---:|---:|---:|---:|
| Utility top-k shared context | 3 | 7 | 2 / 3 | 1 | No broadcast |
| B3 bounded admission + broadcast | 3 | 7 | 3 / 3 | 0 | Yes |

Interpretation: the production B3 admission path covered supplier, inventory and route using the same seven capacity units and the same number of admitted candidates as the top-k control. The fixture consumer accepted the resulting broadcast in all three cases. This demonstrates the value of the bounded admission/coverage/broadcast boundary on these fixtures; it does not demonstrate outcome-attributed admission-policy learning.

### B4 — intention lifecycle and method reconsideration

In both B4 fixtures, plan-only has no intention transitions. The B4 arm performs two lifecycle transitions (reconsideration and method change), confirms the new method, and preserves the original objective fingerprint and protected constraints (1.0 in both cases).

Interpretation: the lifecycle service preserves the objective while changing the method in these cases. Its repository is in-memory in this probe, so the word “persistent” must not be read as a process-restart durability result. Restart/recovery remains M8-B4 work.

### B6 — uncertainty and resource-aware control

The same budget snapshot is supplied to both arms:

- High-uncertainty `B6-01`: fixed-budget control continues because three reasoning evaluations remain and the next step needs two. B6 instead returns `INTERRUPT_FOR_HUMAN` with reason `UNCERTAINTY_REQUIRES_HUMAN_REVIEW`.
- Low-uncertainty `B6-02`: both fixed-budget and B6 arms continue.

Thus B6 escalates in one of the two deliberately contrasting cases (50% of this two-case probe). This is not an estimated real-world escalation rate; it shows the expected branch difference under the selected thresholds and fixture inputs.

### B2+B4 — typed child resolution with intention delegation

Both fixtures consolidate three raw signals into two typed groups, record one bounded read-only child action (`INVENTORY_READ`), and exercise B4 delegation and return (two intention version transitions). The case is escalated because no authoritative confirmation is available in the probe; the parent intention returns to `ACTIVE`, and its objective fingerprint/protected constraints remain unchanged.

Interpretation: the tested composition preserves the parent objective and returns control after bounded child work. It deliberately does not claim a successful supply-chain resolution: escalation is the correct result when the read-only child path cannot establish authoritative evidence.

### B1+B3+B6 — retrieval to workspace to meta-control

Across both fixtures:

- Utility top-k selects three candidates, covers two required workspace tags and leaves one tag missing.
- B1-selected memories mapped into B3 produce four workspace candidates and cover all three required tags with no missing tag.
- Adding B6 leaves B1/B3 coverage unchanged, but the governor changes the decision from fixed continuation to `REQUEST_EVIDENCE` in both fixtures.

Interpretation: the selected composition shows a measurable staged path (B1 selection → B3 bounded admission → B6 control) and demonstrates B6's ability to block automatic continuation while evidence is requested. The B1-to-B3 candidate/tag mapping is a deterministic evaluation adapter, not a generally learned semantic mapping.

### B4+B6 — interruption propagated to intention state

In the single registered fixture, the B4-only condition advances the intention lifecycle once. When the high-uncertainty B6 governor returns `INTERRUPT_FOR_HUMAN`, the B4 service transitions the intention to `SUSPENDED`, preserving the objective fingerprint.

Interpretation: this confirms the intended local control edge between B6 interruption and B4 intention suspension for one case. One case is insufficient to establish reliability across interruption types.

## 3. Cross-study conclusions

1. **No overall winning mechanism is declared.** Each study tests a distinct function on a small, designed fixture set; their metrics are not interchangeable.
2. **B1 is mixed/negative for current retrieval ranking.** The top-k baseline wins on fixture recall/precision; B1 adds cost accounting and an explicit downstream-use edge, but needs tuning and a real outcome-attributed update before adaptive claims.
3. **B2 reduces duplicate resolution units in its designed cases.** This is a narrow efficiency signal, not an end-to-end success measure.
4. **B3 improves required-tag coverage at equal candidate count and capacity.** Broadcast acceptance is observed, but acceptance is not outcome learning.
5. **B4 and B6 show control/lifecycle behavior, not persistence or external workflow completion.** The B4+B6 case is particularly small (n=1).
6. **Selected compositions behave coherently under the tested state transitions.** They do not amount to a full all-mechanism composition experiment.

## 4. Explicitly not evaluated / next milestone

- B1 outcome-attributed selector learning update: not present in the accepted implementation.
- B3 outcome-attributed admission-policy update: not present in the accepted implementation.
- Three-way B1 ablation of the learning update: not evaluable until an update exists. State/object and authoritative-use edge contrasts are included.
- B3 learning-edge ablation: not evaluable until an update exists.
- B4 process restart, cross-process continuation and durable recovery: M8-B4.
- B6 durable budget ledger, multi-instance ownership and restart: not covered by this in-memory probe; M8-B4/rollout review.
- Real supplier/WMS/carrier calls, procurement dispatch and customer/financial effects: not included; no external effects were attempted.
- B5/GraSP: remains the closed negative case.
- All-mechanism full composition: not part of these selected interactions.

## 5. Reproduction and files

The delivered patch includes the deterministic runner and the exact raw and derived JSONL outputs under `docs/phase8/results/`, plus `M8-B3-ABLATION-REGISTER.csv` and `M8-B3-RESULTS-SUMMARY.csv`. After CI accepts the patch, regenerate them with:

```bash
./scripts/run-m8-ablations.sh
```

The authoring execution compiled the relevant Java 21 production mechanism dependencies and the evaluation CLI directly, then ran the CLI to generate the 63 raw rows and 26 paired contrasts. Maven and JUnit were not run. GitHub Actions remains the build/test acceptance gate.
