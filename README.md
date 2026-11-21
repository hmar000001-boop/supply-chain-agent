# GAP-01–GAP-05 CI Fix 1 — Initial B6 assessment ordering

**Apply this changeset at the repository root, over the GAP-01–GAP-05 candidate already applied to the F-01–F-04 accepted stack.** It contains complete replacements for two Java files and one validation-document correction.

## Root cause

The GAP-01–GAP-05 implementation correctly made B1 retrieval require the exact current, versioned B6 uncertainty assessment. `CognitiveCycleDecisionServices.retrieveEvidence(...)` uses that assessment to create the B6 governance directive for the B1 selection request.

The graph's initial control order did not satisfy that contract: `load-context -> retrieve-evidence -> assess-uncertainty-after-retrieval`. Therefore the first retrieval failed before the graph could create the first assessment. The evaluation failures all surfaced the same missing-reference exception; the forked-JVM resume failure was the same defect on the resumed graph path.

## Fix

The graph now runs:

`load-context -> assess-uncertainty-before-retrieval -> retrieve-evidence -> assess-uncertainty-after-retrieval`

The new pre-retrieval assessment is execution/scope-bound and uses a deterministic trigger containing working-memory identity/version, intention identity/version, selected method, retrieval/planning/clarification counters and the predecessor assessment revision. The existing lifecycle service's idempotency key also binds the trigger to the exact predecessor. The graph still fails closed if the reference is absent.

The existing post-retrieval assessment remains in place so it can refresh uncertainty using the newly selected evidence. Impasse handling still performs its additional pre-child assessment, and the B2 subgraph still refreshes after child investigation.

## Regression coverage

`CognitiveCycleGraphTest` now asserts that the first retrieval receives uncertainty revision 1, that B2 opens under revision 2 and validates under revision 3, and that the resumed B6 human-control path consumes revision 4 after the added initial assessment.

## Scope

- Project source files modified: 2
- Project documentation files modified: 1
- Project files added: 0
- Project files deleted: 0
- Database migrations: unchanged
- POM/dependency changes: none
- B1/B2/B3/B4/B6 policies: no weakening or fail-open behavior introduced

## Apply and validate

Extract at the repository root, then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Send the complete CI result if any failure remains.
