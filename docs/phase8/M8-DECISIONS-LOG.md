# M8 decisions log — baseline and evaluation rules

| ID | Decision | Basis | Status |
|---|---|---|---|
| M8-D001 | Freeze `M8-M7-ACCEPTED-2026-11-19` as the baseline; no M7 overlays are to be reapplied to it | Exact source/overlay hashes and tree manifest in `M8-B0-BASELINE-AUDIT.md` | Accepted |
| M8-D002 | M7 is closed; no M7-B3 or additional persistence phase is created | User-confirmed full GitHub Actions CI green after M7-B2 corrections | Accepted |
| M8-D003 | Part III remains one domain-bounded, request-driven supply-chain disruption/fulfillment system | Part III pilot purpose and supported request families | Accepted |
| M8-D004 | CoALA and Fan & Lan remain the two theoretical foundations; CoT/ToT/RAP-inspired MCTS are method comparators | Part III pilot plan | Accepted |
| M8-D005 | Label the MCTS implementation RAP-inspired/domain-grounded; the structured simulator, not an LLM world model, supplies authoritative modeled transitions | `MctsSearchPlanner`, `SimulatorBackedPlanningWorldModel`, pilot plan | Accepted |
| M8-D006 | Deterministic hard constraints and authority checks are mandatory across all planners; a planner score never authorizes execution | `DeterministicCandidateEvaluator`, `PlanningAuthorityPolicy`, M6/M7 contracts | Accepted |
| M8-D007 | LangGraph4j/LangChain4j are part of the accepted M6 implementation despite an earlier draft's framework-free non-goal; the M6 decision supersedes that draft | User accepted M6 and its CI; actual POM/runtime composition | Accepted; document explicitly in chapters |
| M8-D008 | Use deterministic simulator and strict test adapters for the pilot; do not invent live ERP/WMS/carrier/IAM integrations | Accepted lifecycle/authority ports and M6 status | Accepted |
| M8-D009 | Add a deterministic/greedy planning comparator as an evaluation-only baseline before algorithm comparison; do not insert it into production routing unless evaluation justifies it | `GreedyDeterministicComparator`; shared simulator and hard-constraint evaluator | Implemented in M8-B1; comparative results M8-B2 |
| M8-D010 | Lock scenario fixtures, evaluator/scoring version, seeds and budgets before comparative runs | `M8-B1-v1` register, fixture/request/world/evaluator/planner fingerprints and named budget profiles | Implemented for M8-B1 baseline; user-confirmed CI green |
| M8-D011 | Zero hard constraint violations, unauthorized dispatches, stale-approval dispatches and duplicate external effects are hard test gates; comparative performance thresholds are set before M8-B2 runs | Part III security and testing requirements | Required |
| M8-D012 | Do not claim learning or mechanism contribution from presence/logging alone; require outcome attribution and a controlled ablation | Part III B1/B3/Fan & Lan traceability | Required |
| M8-D013 | Chapters 19 and 20 cannot be drafted until M8-B6 freezes the application and the user approves their detailed outlines | Book chapter-writing gate | Required |

| M8-D014 | Keep planner-only comparisons separate from full lifecycle graph execution; compile the complete graph with explicit ports, but fail closed by default and do not register external action handlers | `FullGraphEvaluationHost`; `CognitiveCycleApplicationGraph.compileFull(...)` | Accepted for M8-B1; lifecycle execution M8-B4 |
| M8-D015 | Compare paired procedures scenario-by-scenario; determine wins feasibility-first and calculate score deltas only where both procedures return a feasible candidate | `MatchedComparisonAnalyzer`; scenario-level resampling with fixed seed material | Implemented in M8-B2; CI pending |
| M8-D016 | Keep measured runtime descriptive; do not rank planner quality by elapsed wall-clock time from this finite local corpus | M8-B2 execution-order rotation and comparison protocol | Accepted for M8-B2 |

## Explicit non-decisions

- M8-B1 planner comparisons use a deterministic decision provider and zero model calls. They do not measure model/prompt quality. No production LLM provider is selected; the runtime uses a LangChain4j `ChatModel` boundary and the smoke application uses a fixed-response test model.
- No numeric quality/performance target is invented for service level, recovery time or cost. M8-B1 must produce a baseline and M8-B2 thresholds must be approved before comparisons are run.
- No new real-world supplier, ERP, WMS, carrier or identity endpoint is assumed.
- No B5 residual mechanism is claimed; B5/GraSP remains a closed negative case unless a narrowly justified closest-baseline comparison is included.
