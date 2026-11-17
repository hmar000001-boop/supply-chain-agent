# M5 test results

## Local framework-neutral regression

**Result: 208 passed, 0 failed.**

| Module/test area | Methods |
|---|---:|
| Domain | 14 |
| CoALA memory | 43 |
| Simulator | 20 |
| Actions | 25 |
| Fan & Lan mechanisms | 70 |
| Planning (M5) | 36 |
| **Total** | **208** |

M5 test breakdown:

| Test class | Methods |
|---|---:|
| `PlanningRequestFingerprintTest` | 6 |
| `DeterministicCandidateEvaluatorTest` | 6 |
| `SequentialReasoningPlannerTest` | 6 |
| `ThoughtSearchPlannerTest` | 4 |
| `MctsSearchPlannerTest` | 4 |
| `PlannerRouterTest` | 7 |
| `PlannerSessionStoreTest` | 3 |
| **Total M5** | **36** |

## What the tests cover

- Stable semantic request fingerprints and changed-world detection, including content changes hidden behind an unchanged snapshot version.
- Working-memory semantic identity independent of regenerated internal storage UUIDs.
- Rebudgetable compute ceilings/resume identity and rejection of changed search-space/configuration fingerprints.
- Deterministic replay of every proposed action against canonical action definitions and the M1 simulator.
- Hard risk/deadline/constraint checks, fail-closed behavior for unrepresentable protected commitments, currency and scoring behavior.
- Sequential planner action/evidence validation, stop behavior, model-call accounting and non-resumable semantics.
- ToT branching, expansion/ranking, checkpoint interval, mid-layer budget exhaustion, frontier retention and safe resume.
- MCTS seeded repeatability, bounded rollouts, early no-action behavior, best-candidate/tree retention and resume.
- Explainable planner routing, low/moderate/high-complexity choices, fallback when rollout budget is unavailable, explicit override and strategy registration validation.
- Planner-session revision progression and stale/concurrent checkpoint write rejection.

## Execution method and limits

The local authoring environment did not have the full Maven dependency cache/network access needed to execute the complete Maven reactor. Framework-neutral production sources and tests were compiled with JDK 21. Tests were executed by a local JUnit-compatible harness with a fresh test instance per test method. This mirrors the default JUnit Jupiter `PER_METHOD` lifecycle but is **not** Maven Surefire/JUnit Platform and is not a substitute for GitHub Actions.

The local run did not compile or execute the M0 framework-dependent runtime/app modules. The PostgreSQL checkpoint integration test was not rerun locally. CI must execute:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

A green CI result is required to accept M5.
