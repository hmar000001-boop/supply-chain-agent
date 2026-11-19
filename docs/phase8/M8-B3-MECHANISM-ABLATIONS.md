# M8-B3 — Mechanism ablations and selected compositions

## Purpose

M8-B3 measures the observable contribution of the accepted B1, B2, B3, B4 and B6 implementations, then tests three selected interactions. The probes use the production mechanism services from the accepted repository with deterministic in-memory fixtures. They are isolated from the production runtime and have no external action adapter.

This is an engineering evaluation of the code that exists. It is not a claim that the implementation instantiates every learning or adaptation edge proposed by Fan and Lan.

## Registered design

Eight studies cover 19 deterministic scenario definitions and 45 primary observations:

| Study | Scenarios | Arms | Primary observations | Paired contrasts | Main isolation |
|---|---:|---|---:|---:|---|
| B1 | 4 | utility top-k; B1 selector/log only; B1 selector with downstream-use edge | 12 | 8 | state/selection object and authoritative downstream-use edge |
| B2 | 3 | generic per-signal retry; typed impasse consolidation | 6 | 3 | typed grouping and bounded impasse handling |
| B3 | 3 | top-k shared context; bounded workspace/admission/broadcast | 6 | 3 | bounded admission and broadcast boundary |
| B4 | 2 | plan reference only; intention lifecycle | 4 | 2 | reconsideration, method replacement and protected objective |
| B6 | 2 | fixed budget continuation; uncertainty/resource governor | 4 | 2 | uncertainty-aware interrupt/evidence/stop decisions |
| B2+B4 | 2 | generic retry/plan only; typed impasse with bounded child delegation and intention return | 4 | 2 | B2 child case and B4 delegation/return |
| B1+B3+B6 | 2 | top-k/fixed; B1→B3/fixed; B1→B3→B6 | 6 | 4 | retrieval-to-workspace composition and B6 control edge |
| B4+B6 | 1 | plan only; intention only; B6-governed intention suspension | 3 | 2 | B6 interruption propagated to B4 intention lifecycle |
| **Total** | **19** | | **45** | **26** | |

Each scenario uses a stable seed derived from `20261119 + scenarioIndex`. For nine preselected scenarios, the control and final treatment are executed again as deterministic logical replays. This creates 18 replay rows, for 63 total raw observation rows. Replays are excluded from all primary counts and paired contrasts.

The experimental unit is the scenario, not an individual observation row. The 45 primary observations are arm-level observations nested within 19 scenarios; they are not 45 independent samples. The 26 paired contrasts are descriptive treatment-minus-control deltas within a shared scenario. No p-values, population confidence intervals or claims of statistical significance are calculated.

## Production mechanisms exercised

- **B1:** `B1RetrievalSelector`, activation and latency models, `MemoryMaintenanceAccountingService` and an in-memory maintenance ledger. The fixtures define relevance in advance as expected downstream utility ≥ 7.0. Top-k and B1 use the same candidate population and limit. The log-only arm disables downstream use while retaining the selector and its cost estimates.
- **B2:** `DeterministicImpasseDetector` consolidates typed signals; B2+B4 also executes the production `ImpasseResolutionService` with a scoped read-only child capability, records a child action, escalates when authoritative evidence is absent, and returns control to the parent intention.
- **B3:** `WorkspaceAdmissionService`, `WorkspaceRepository` in memory and `WorkspaceBroadcastService`. Required tags are exactly supplier, inventory and route. The comparator uses top-k utility selection with the same three-candidate and seven-unit budget.
- **B4:** `RecoveryIntentionService` and `RecoveryIntentionRepository` in memory. The probe performs reconsideration and method replacement while checking that the objective fingerprint and protected constraints are unchanged.
- **B6:** `MetaControlGovernor`, `UncertaintyAssessment` and a fixture `ResourceBudgetSnapshot`. The fixed-budget comparator ignores uncertainty but sees the same resource balance.

The B1→B3 composition maps selected B1 memory candidates into typed B3 workspace candidates via a deterministic, explicitly documented evaluation mapping. It does not claim a generally trained semantic mapper. The composed B6 arm consumes the same budget and uncertainty profile as its B1+B3 ablation.

## Outcome schema

Raw JSONL stores the study/scenario/condition identity, arm role, primary/replay kind, fixed seed, numeric metrics, short configuration labels and a logical SHA-256 fingerprint. The paired-analysis JSONL includes:

- the 26 per-scenario contrasts;
- a study count summary;
- per-condition means over primary scenarios only;
- per-contrast mean/min/max deltas and scenario counts.

All aggregates are marked `DESCRIPTIVE_ONLY`. Wall-clock time, private reasoning text and free-form model traces are not collected.

## Scope boundaries and non-evaluable edges

1. The accepted B1 code does not contain an outcome-attributed adaptive selector-weight update. B1 state-object and authoritative-use-edge contrasts are present; the learning-update-removal experiment is **not evaluable** until that update exists.
2. The accepted B3 code does not contain an outcome-attributed admission-policy learning update. Consumer acceptance receipts measure delivery/consumption only, not learning. No learning-update effect is claimed.
3. The B4 probes use an in-memory intention repository. They show lifecycle and objective-preservation behavior, not durable process-restart recovery. M8-B4 owns restart and cross-process recovery workflows.
4. The B6 budget snapshot is fixture-owned/in-memory. This is not evidence of an authoritative durable multi-instance budget ledger. No M7 or PostgreSQL change is made.
5. B2+B4 is deliberately bounded and escalates when authoritative supplier/inventory confirmation is not available. The child capability is `INVENTORY_READ`; no procurement or dispatch is performed.
6. B5/GraSP remains the closed negative case. It is not implemented or counted as another mechanism.
7. These are selected pairwise/compositional experiments, not a full all-mechanism composition. M8-B4 will test integrated lifecycle, authority, persistence, restart, timeout and concurrency behavior.

## Safety and isolation

- No external action handler is registered or invoked.
- No inventory, order, supplier or carrier state outside a fixture is changed.
- No PostgreSQL connection or migration is used.
- All repositories in the probes are in-memory.
- B6 decisions affect only the measured evaluation branch; no external action is dispatched.

## Reproduction

From the repository root, after CI has verified the patch:

```bash
./scripts/run-m8-ablations.sh
```

The script compiles the evaluation module and its reactor dependencies with tests skipped, then invokes `M8AblationCli`. It writes `m8-b3-raw-ablations.jsonl` and `m8-b3-paired-analysis.jsonl` below `target/m8-b3-results/`. Set `M8_B3_OUTPUT_DIR` to override the output directory.

See `M8-B3-ABLATION-REGISTER.csv` and `M8-B3-RESULTS-CLARIFICATION.md` for the registered scenarios and the observed fixture-level results.
