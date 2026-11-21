# GAP-01–GAP-05 CI Fix 2 — B4 plan-binding session preservation

**Date:** 2026-11-21  
**Scope:** correction to the GAP-01–GAP-05 candidate layered over F-01–F-04 and CI Fix 1.

## CI failure

The runtime and PostgreSQL modules passed. The evaluation module then failed in 14 full-graph scenarios at `CognitiveCycleDecisionServices.selectedCandidate`, reporting that the planner identity/session did not match the active graph state.

## Root cause

`CognitiveCycleLifecycleServices.governIntention` binds the selected M5 plan to an otherwise unchanged active B4 intention. That is a legitimate, versioned intention transition: the intention version advances because `activePlanReference` is being recorded. It does not change the authoritative method, its candidate-plan reference, the intention identity, or the committed objective.

`CognitiveCycleGraph` previously treated every intention-version increment as a planner-binding change and cleared `PLANNER_SESSION_ID`. The same graph then proceeded from `CONTINUE` through B6 meta-control to authorization with the original `CANDIDATE_SET_ID`, but without the session identity attached to that candidate set. The fail-closed candidate provenance check correctly rejected the inconsistent state.

## Correction

At B4 governance, the graph now discards a planner session when:

- intention identity changes;
- the authoritative method identifier changes;
- the method's candidate-plan reference changes;
- the objective fingerprint changes;
- the action explicitly requests `RECONSIDER` or `CHANGE_METHOD` (even if the selected method string remains the same).

It does **not** discard the session solely because the B4 intention version increments while binding the just-selected active plan. That preserves the exact planner/session provenance required to resolve the candidate set during authorization.

The independent `loadContext` guard remains version-sensitive. A version change discovered while reloading authoritative persisted context still invalidates the previous session. Method changes and explicit reconsideration continue to discard it before re-planning.

## Regression coverage

`M8B4FullGraphWorkflowTest.authorizedWriteCompletesTheFullGraphOnlyAfterExactApprovalAndObservation` now asserts that the planner session remains present at the approval breakpoint. The full-graph workflow suite also covers the candidate-set resolution and approval path. Existing F-01 tests continue to assert that an actual method change starts the replacement planner without resuming the previous method's session.

## Validation status

- Source-level root-cause inspection: PASS.
- Static invariant checks for the new session-retention rule and preserved fail-closed candidate-set check: PASS.
- Repository architecture/R2/F-03/F-04 static guards: to be reported with the delivery validation report.
- Full Maven/Surefire and live PostgreSQL integration: **not run locally**; hosted CI on the exact combined source is required.

This fix does not relax the candidate-set security check, execution approval, candidate/objective binding, action descriptor revalidation, or shared B6 resource guard.
