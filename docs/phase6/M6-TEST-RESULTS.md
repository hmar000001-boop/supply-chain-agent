# M6 test inventory and validation results

**Status: 14 tests added; execution under Maven Surefire/GitHub Actions pending.**

The tests are in `supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/CognitiveCycleGraphTest.java`. They use `MemorySaver` and deterministic scripted `CognitiveCycleOperations` implementations. They exercise the actual graph definition and host resume protocol; they do not call real ERP/provider services.

| # | Test | Invariant exercised |
|---:|---|---|
| 1 | `readOnlyCycleRunsThroughCoalaPlannersAndObservationWithoutApproval` | Complete read-only cycle calls retrieval, workspace, M5 planning/evaluation, execution/observation boundary and episodic recording without an approval interrupt. |
| 2 | `clarificationIsSuppliedByHostBeforeTheInterpretationCycleResumes` | The graph interrupts before clarification; only host-supplied text is accepted; clarification round is recorded. |
| 3 | `stateChangingCandidateInterruptsAndResumesOnlyAfterBoundApproval` | A state-changing candidate pauses, binds to a durable approval reference, then resumes through revalidation before execution. |
| 4 | `deniedApprovalNeverExecutesCandidate` | Denied approval routes to escalation; execution count remains zero. |
| 5 | `externalEvidenceIsARealInterruptAndIsAcceptedBeforeRetrievalRepeats` | External evidence response reference is required and accepted before retrieval/decision resumes. |
| 6 | `b2ImpasseIsResolvedInItsOwnBoundedSubgraphBeforeParentReconsiders` | The parent invokes a bounded B2 child lifecycle and receives a validated typed result. |
| 7 | `b6HumanControlInterruptRequiresAHostIssuedResponse` | B6 human interruption requires both a response reference and actor identity before continuing. |
| 8 | `modelOrPlannerCannotMarkAStateChangingCandidateAsApprovalFree` | Typed authorization contract rejects `stateChanging=true` with `NO_APPROVAL_REQUIRED`. |
| 9 | `b4SuspensionUsesTheHumanControlInterruptionAndThenResumes` | B4 `SUSPEND` is connected to the host human-control interrupt and can resume. |
| 10 | `approvalMustMatchExactCandidateSetAndIntentionVersion` | An approval record referring to another candidate is rejected; execution is not reached. |
| 11 | `spoofedHumanControlActorIsRejected` | Returned actor identity must match the authenticated host actor. |
| 12 | `unknownExternalOutcomeIsReconciledAndNeverBlindlyRetried` | `UNKNOWN_OUTCOME` routes to observation; external action is dispatched only once. |
| 13 | `workingMemoryVersionConflictForcesReloadAndReconsideration` | A memory version conflict reloads the latest version and retries the persisted observation; the external action is not executed again. |
| 14 | `metaControlStopNeverReachesAuthorizationOrExecution` | B6 stop decision terminates through escalation without authorizing or executing a candidate. |

## Authoring-environment checks

- The new M6 production and test source files compiled with Java 21 `javac --release 21 -Xlint:all` against temporary minimal LangGraph4j/JUnit API stubs. No errors were reported; the only warning was a missing `serialVersionUID` in the temporary stub exception, not in project source.
- This is a source syntax/type-shape check only. It does not establish binary compatibility with the real LangGraph4j 1.8.20 or execute the graph.
- The Maven Wrapper was invoked with `-pl supply-chain-agent-runtime -am -DskipTests compile`, but the wrapper could not download Maven 3.9.16 because the authoring environment could not resolve the configured Apache distribution hosts. Therefore no Maven/Surefire results are claimed.

## CI acceptance gate

Run from the repository root after applying the changes:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

All previously accepted M0–M5 tests must remain green, the 14 M6 tests must pass under JUnit Platform/Surefire, and the existing PostgreSQL checkpoint compatibility test must remain green. M6 must not be marked accepted until that CI run succeeds.
