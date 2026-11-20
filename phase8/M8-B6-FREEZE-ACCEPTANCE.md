# M8-B6 Freeze Acceptance

- **Decision:** ACCEPT
- **Decision date:** 2026-11-20
- **Scope:** M8-B6 source and experimental-evidence freeze
- **Supersedes:** Any earlier *pending*, *conditional*, or *awaiting B2 evidence review* status recorded for M8-B6.

## 1. Acceptance basis

This acceptance records the project owner's completed review and manual verification communicated on 2026-11-20. The underlying GitHub Actions ZIP was not uploaded again in this conversation, so the numerical findings below are recorded from the owner's supplied audit; they are not represented as a new independent re-execution by the assistant.

The owner confirmed that the two remaining administrative checks are satisfactory:

1. The M8-B5 source/provenance relationship to baseline commit `c4bc1205` was manually verified and accepted. The M8-B5 change is accepted as documentation-only relative to that baseline.
2. The real dependency tree was manually inspected and accepted. A copy of that tree is not included in this acceptance addendum; consequently, this record does not claim that a dependency-tree artifact is attached to this patch.

The determinism report's lack of per-run fingerprints is treated as a recommendation for future evidence quality, not an M8-B6 acceptance blocker.

## 2. M8-B2 dataset and integrity findings

The supplied audit reports the following results:

- 25 scenarios and 5 planners produced the expected 125 scenario/planner runs.
- No scenario/planner pair is missing or duplicated.
- Each scenario uses one seed, one fixture fingerprint, and one budget profile shared across its five planner runs.
- All 170 aggregates and all 10 paired comparisons recompute exactly from the raw runs.
- Corpus and planner-configuration fingerprints match across the manifests.
- Checksums for all three result files verify.
- No execution errors or hard-constraint violations were recorded.
- All 125 runs have `plannerOnly=true`; no external dispatch was performed.

## 3. Planner comparison findings

The supplied audit reports:

- All five planners have identical feasibility and best-score results in 24 of 25 scenarios.
- `FAIL-10` (tight-budget scenario) is the only scenario in which the planners differ.
- CoT and Greedy find no feasible plan for `FAIL-10`.
- ToT, RAP-inspired MCTS, and Hybrid find a feasible plan there, with a score of `0.70675`.
- Overall feasibility is `0.88` for CoT and Greedy and `0.92` for ToT, RAP-inspired MCTS, and Hybrid. The `0.92` ceiling reflects `REQ-10` and `FAIL-11`, which are infeasible by design.
- The reported mean scores are `0.8992` for CoT/Greedy and `0.8908` for ToT/RAP/Hybrid. The lower latter mean results from including the additional, lower-scoring feasible `FAIL-10` result; across the 22 scenarios all planners solve, the mean-score difference is exactly zero.
- Mean runtime is approximately 3 ms for Greedy and CoT, 7 ms for ToT and Hybrid, and 22 ms for RAP-inspired MCTS.

**Permitted conclusion:** bounded search improves feasibility in the tight-budget case at additional compute cost. The supplied evidence does **not** establish a general score advantage or overall planner superiority.

## 4. Determinism findings and limitation

The supplied determinism summary reports 125/125 replays matching with no mismatches. The report is a single summary record and contains no per-run fingerprints, so the match cannot be independently reconstructed from that summary alone. The result covers one CI environment and does not establish cross-platform or cross-JVM equivalence.

This limitation is documented but is not an acceptance blocker. Per-run fingerprints and multi-environment replay remain recommended follow-up improvements, not prerequisites for M8-B6 acceptance.

## 5. Permitted use in Chapters 19 and 20

### Chapter 19 — architecture and implementation

The reported 340 passing Maven tests, including PostgreSQL integration and forked-JVM restart tests, support the statement that the implemented system has passed the reported unit and integration test suite. They do not establish production readiness, production disaster-recovery objectives, or end-to-end reliability with real external business adapters.

### Chapter 20 — evaluation and findings

The chapter may report the supplied evidence for:

- evaluation-corpus and run-matrix completeness;
- the recorded determinism outcome, with its summary-only and single-environment caveats;
- zero recorded hard-constraint violations in planner-only mode;
- the feasibility effect under the `FAIL-10` tight-budget scenario;
- the compute/runtime trade-off among planners.

The chapter must not use this B2 evidence to claim:

- general planner superiority or a score advantage;
- validated LLM-planner results beyond the actual planner configurations and runs represented in the evidence;
- mechanism-ablation conclusions (those require their own separately audited B3 evidence);
- real-world execution reliability, since all 125 B2 runs were planner-only and no external dispatch occurred.

The dependency tree was manually inspected and accepted by the project owner, but is not reproduced in this addendum. Any book discussion must distinguish that manual inspection from an archived, independently reproducible dependency report.

## 6. Final decision and scope boundary

**M8-B6 is ACCEPTED and the source/evidence freeze is closed**, based on the owner-supplied audit and manual verification described above.

This acceptance closes the M8-B6 evidence-freeze milestone. It does not certify the application as production-ready and does not close the separately tracked deployment, security-assurance, external-adapter, disaster-recovery, IAM, retention, or operational-readiness gates.

No executable application code, tests, Maven dependencies, database migrations, or CI workflow files are changed by this acceptance addendum.
