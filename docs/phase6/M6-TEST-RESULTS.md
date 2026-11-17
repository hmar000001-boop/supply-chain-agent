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


## First full GitHub Actions run — 2026-11-17

The user supplied the output of the complete `clean verify` workflow. The results relevant to M6 were:

| Scope | Tests | Failures | Errors | Result |
|---|---:|---:|---:|---|
| M0–M5 modules | 208 | 0 | 0 | Passed |
| M6 cognitive-cycle graph tests | 14 | 2 | 1 | Failed before correction |
| Runtime compatibility tests (checkpoint resume, framework, PostgreSQL saver) | 7 | 0 | 0 | Passed |

The runtime module total was 21 tests, with 18 successful and three unsuccessful. The application-host module was skipped after the runtime failure. The PostgreSQL checkpoint compatibility test passed in this run.

### Corrected failure causes

1. **B2 blank reason:** `childContext` placed the parent impasse reason in `summary`, but the B2 operation contract reads `lastReason`. The correction now maps the initial `IMPASSE_REASON` to `lastReason`, then prefers a nonblank child `REASON` as the child lifecycle advances. The test explicitly verifies the original reason is passed to `openImpasse`.
2. **Security exception wrappers:** LangGraph4j's asynchronous execution wraps node exceptions. The tests now search the cause chain for `SecurityException`; they retain the assertion that execution is never reached. The authorization and actor checks themselves are unchanged.

### Status after correction

The code and tests have been corrected, but **the corrected 14-test result is not yet verified**. Maven 3.9.16 could not be bootstrapped in the authoring environment because the configured Apache distribution host was not resolvable. Run the full command in GitHub Actions and require the entire reactor to pass before accepting M6.

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

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
