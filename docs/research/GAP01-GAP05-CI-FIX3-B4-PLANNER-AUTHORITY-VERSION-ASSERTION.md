# GAP-01–GAP-05 CI Fix 3 — B4 planner-authority version assertion

## CI finding

After CI Fix 2, `M8B4FullGraphWorkflowTest` completed all 17 tests without errors, but
`b4MethodChangeControlsTheNextFullGraphPlannerWithoutChangingTheCommittedObjective`
failed one assertion at the comparison between the replacement planner's
`PlannerSelectionOverride.intentionVersion()` and the graph's final
`INTENTION_VERSION`.

The CI values were expected `4`, actual `3`.

## Root cause

These values describe two different points in the B4 lifecycle:

1. The replacement planner is invoked under the current, method-changed intention
   (version 3). Its planner-selection override must retain that exact version, because
   it is the authority snapshot used when the candidate was produced.
2. After candidate selection, `CognitiveCycleLifecycleServices.governIntention` binds
   the selected candidate's plan reference to the still-active intention through
   `RecoveryIntentionService.bindActivePlan`.
3. `RecoveryIntention.bindActivePlan` deliberately increments the intention version
   once. The graph therefore reaches version 4 by the approval boundary.

The implementation's version advance is intentional and consistent with the
versioned B4 state model. The old test assertion incorrectly compared the planner's
pre-binding authority snapshot with the post-binding graph state.

## Correction

The test now asserts that the final graph intention version equals the planner
invocation's B4 intention version plus exactly one. It retains the checks that:

- the replacement planner is ToT bounded search;
- the override identifies the changed `tot` method and its exact candidate reference;
- the intention identity and committed objective remain unchanged;
- the prior planner session is not resumed after the method change.

No production implementation, authorization rule, candidate validation, or planner
session behavior is changed by this fix.

## Validation boundary

The source-level assertion and package integrity are checked locally. A full Maven
build and Surefire execution must still be confirmed by the repository's hosted CI;
this changeset does not claim the hosted build is green.
