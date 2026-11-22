# Validation report — CI Fix 3

## Findings
- CI Fix 2 removed the prior 14 full-graph errors; the supplied log now shows 17 full-graph tests with one assertion failure and zero errors.
- The sole failure compares the replacement planner's B4 version snapshot (3) with the graph's later version (4).
- `RecoveryIntention.bindActivePlan` advances the B4 intention version by exactly one after candidate selection. The test now asserts that lifecycle relationship instead of equality across the two moments.

## Local checks
- Confirmed the planner invocation still asserts the ToT planner, `tot` method ID, exact candidate-plan reference, stable intention ID, unchanged objective fingerprint, and absence of the previous planner resume session.
- Confirmed the `bindActivePlan` implementation increments the intention version exactly once.
- Confirmed the modified test and research note are the only project files in this changeset.
- ZIP integrity, manifest paths, and SHA-256 checksums are verified.

## Not run / not claimed
- Maven is not installed in this container. The Maven Wrapper attempt did not complete because it could not obtain the Maven distribution in the available environment.
- No local Surefire/PostgreSQL test execution is claimed.
- The full hosted CI command must be rerun after applying this changeset.
