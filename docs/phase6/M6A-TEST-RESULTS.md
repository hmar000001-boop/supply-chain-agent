# M6-A test inventory and validation

**Status: 5 focused tests passed in a local Java 21 harness; Maven/Surefire/GitHub Actions validation pending.**

| Test | Service path | Assertion |
|---|---|---|
| `retrievalUsesM2BeforeB1AndPersistsTheSelectedEvidenceSet` | M2 retrieval → B1 candidate construction/selection → evidence persistence port | Empty authoritative memory is represented as a valid empty retrieval and the resulting evidence-set reference is persisted. |
| `workspaceMustBeAdmittedAndAcknowledgedByEveryConfiguredConsumer` | B3 admission → B3 broadcast | The service returns `ADMITTED` only after the configured consumer acknowledges the admitted candidates. |
| `broadcastFailureCannotBeReportedAsAnAdmittedWorkspace` | B3 admission → failed broadcast receipt | A failed consumer receipt returns `FAILED`; a workspace cannot be reported as ready for planning. |
| `plannerRouterRunsM5AndCandidateArtifactsAreReopenedForEvaluation` | M5 `PlannerRouter` → `PlanningArtifactStore` → candidate evaluation stage | The configured CoT strategy is invoked, its complete request/result are stored outside graph state, the candidate-set reference is resolved, and a no-action result is safely classified as no feasible candidate. |
| `deterministicEvaluatorSelectsOnlyTheSimulatorReplayedFeasibleCandidate` | M5 planner output → trusted world-model replay → common deterministic evaluator → selection | A proposed inventory reservation is re-resolved against canonical available actions, replayed through the M1 simulator and selected only after the common evaluator confirms feasibility. |

## Authoring-environment validation

- All M0–M5 main Java sources plus the M6 operation contract and new M6-A production classes compiled using `javac --release 21 -Xlint:all`.
- The new M6-A test source compiled against a minimal local JUnit API shim, then all five test methods were executed through a small local reflection harness. All five passed.
- The shim is not JUnit Platform and this is not a Maven/Surefire run. No Maven/GitHub Actions success is claimed for M6-A.
- The runtime decision-service package and its test source compile under Java 21. `CognitiveCycleApplicationGraph` was separately source-checked against the selected LangGraph4j API shape, but its actual Maven compilation must still be confirmed by the full reactor CI run.

## Required CI command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Require all previously green modules, the 14 corrected M6 graph tests, existing framework/PostgreSQL compatibility tests, the five M6-A tests, and the application-host module to pass.
