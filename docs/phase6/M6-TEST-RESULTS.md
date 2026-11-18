# M6 test inventory and validation

**Current status:** the user confirmed the corrected M6 graph-core CI run green and subsequently marked M6-A complete. The specific M6-A CI output was not included. M6-B adds 12 focused service-integration tests that passed in a local Java 21 lightweight JUnit-compatible harness. The full post-M6-B Maven/Surefire/GitHub Actions result remains pending.

## M6 graph-core tests — 14

The tests live in `supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/CognitiveCycleGraphTest.java`. They use the actual LangGraph4j graph definition, `MemorySaver` and deterministic scripted `CognitiveCycleOperations` implementations. They test graph routing/interrupt behavior; they do not call real ERP or identity providers.

| # | Test | Invariant exercised |
|---:|---|---|
| 1 | `readOnlyCycleRunsThroughCoalaPlannersAndObservationWithoutApproval` | Complete read-only route through retrieval, workspace, planning/evaluation, observation and episodic recording without an approval interrupt. |
| 2 | `clarificationIsSuppliedByHostBeforeTheInterpretationCycleResumes` | Host-supplied clarification is required before reinterpretation resumes. |
| 3 | `stateChangingCandidateInterruptsAndResumesOnlyAfterBoundApproval` | State-changing candidate pauses for bound approval and resumes through revalidation. |
| 4 | `deniedApprovalNeverExecutesCandidate` | Denial routes to escalation; execution count remains zero. |
| 5 | `externalEvidenceIsARealInterruptAndIsAcceptedBeforeRetrievalRepeats` | External evidence reference is required and accepted before retrieval repeats. |
| 6 | `b2ImpasseIsResolvedInItsOwnBoundedSubgraphBeforeParentReconsiders` | Bounded B2 child result returns to the parent through the typed contract. |
| 7 | `b6HumanControlInterruptRequiresAHostIssuedResponse` | Human-control response and actor references are required. |
| 8 | `modelOrPlannerCannotMarkAStateChangingCandidateAsApprovalFree` | Typed authorization rejects the approval-free path for a state-changing candidate. |
| 9 | `b4SuspensionUsesTheHumanControlInterruptionAndThenResumes` | B4 suspension is connected to human interruption and controlled resumption. |
| 10 | `approvalMustMatchExactCandidateSetAndIntentionVersion` | Approval for a different candidate/version is rejected. |
| 11 | `spoofedHumanControlActorIsRejected` | Returned actor must match the host-authenticated actor. |
| 12 | `unknownExternalOutcomeIsReconciledAndNeverBlindlyRetried` | Unknown outcome routes to observation; graph does not redispatch. |
| 13 | `workingMemoryVersionConflictForcesReloadAndReconsideration` | Version conflict reloads memory and retries the stored observation, not the external action. |
| 14 | `metaControlStopNeverReachesAuthorizationOrExecution` | B6 stop prevents authorization/execution. |

The first CI run on 2026-11-17 showed 11/14 graph tests passing (two failures, one error). The cause was B2 reason propagation (`summary` vs `lastReason`) and tests expecting unwrapped exceptions instead of LangGraph4j's async cause chain. Both were corrected. The user confirmed the corrected graph CI green; the corrected run log was not supplied, so no post-correction test count is asserted independently.

## M6-A decision-service tests — 5

See `M6A-TEST-RESULTS.md`. They cover M2 retrieval/B1 selection, B3 admission/broadcast success and failure, M5 planner routing/artifact retrieval, and deterministic simulator-replayed feasible candidate selection. The user subsequently confirmed that M6-A is done and CI green.

## M6-B lifecycle-service tests — 12

See `M6B-TEST-RESULTS.md`. They exercise actual M2 working/episodic memory, M3 action dispatch and ledger, B2 impasse child restrictions/return, B4 objective/method/termination governance, and B6 execution-bound budget handling. Test doubles are used only at the explicit domain and trusted-authority adapter interfaces.

## M6-B authoring-environment validation

- Changed M6-B production sources and their transitive project dependencies compiled using `javac --release 21 -Xlint:all` against actual M2/M3/B2/B4/B6 project sources.
- The 12 M6-B test methods compiled and executed with a local lightweight JUnit-compatible shim/reflection harness. All 12 passed.
- This is not JUnit Platform/Surefire and does not claim the full Maven reactor passed. LangGraph4j application-host compilation, complete graph execution with the real lifecycle service composition, PostgreSQL persistence and external provider integration must be confirmed in CI/M7/M8.

## Full M6 acceptance command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Require all M0–M5 regression tests, 14 graph tests, five M6-A tests, 12 M6-B tests, framework/PostgreSQL checkpoint compatibility and application-host compilation to pass. M6 is not fully accepted until this complete post-M6-B run is green.
