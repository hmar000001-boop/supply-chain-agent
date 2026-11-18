# M7-B1 test and validation results

## Added PostgreSQL integration tests

`PostgresM7B1PersistenceIntegrationTest` defines four scenarios gated by `-Dphase0.postgres=true`:

1. **Semantic bitemporal lifecycle:** stage and activate a fact, query at valid/known times, replace its validity interval with two contiguous segments, verify old-fact supersession and historical lookup.
2. **Semantic conflict and CAS:** reject an activation whose differing value overlaps an existing active fact, confirm the candidate remains PROPOSED, and verify stale-version rejection.
3. **Procedural version history:** append v1 and v2, validate/activate them, ensure v1 is retired, verify active-at-time and newest-first history, and reject stale lifecycle revisions.
4. **Planner session durability:** store a complete ordered frontier/tree checkpoint, load through another repository instance, update by expected revision and reject a stale writer.

Existing M7-A PostgreSQL integration tests now expect the six-migration chain.

## Local source checks

| Check | Result |
|---|---|
| 320 Java 21 production sources with `--release 21 -Xlint:all` | Passed using temporary compile-only pgJDBC API stub |
| Five PostgreSQL infrastructure test sources (plus five JUnit API stubs) | Compiled using temporary JUnit API stubs |
| SQL statement splitting V001–V006 | 49 statements parsed successfully |
| Maven Surefire/JUnit Platform execution | Not run locally |
| Live PostgreSQL integration | Not run locally |

The local compile proves Java source-level integration with the actual project interfaces; it does not prove actual pgJDBC linkage, SQL execution, transaction isolation or PostgreSQL concurrency semantics. Those are explicitly gated by the repository's GitHub Actions PostgreSQL job.


## First GitHub Actions PostgreSQL run — 2026-11-18

The first real Maven/Surefire run after M7-B1 reported three defects in `PostgresM7B1PersistenceIntegrationTest` (14 infrastructure tests total):

1. `semanticFactsPreserveBitemporalLifecycleAndAtomicTimelineReplacement` errored because PostgreSQL could not infer the type of parameter `$6` in the nullable predicate `? IS NULL`.
2. `semanticConflictIsRejectedWithoutActivatingCandidate` hit the same PostgreSQL Parse-time type-inference error.
3. `proceduralVersionsAreAppendOnlyAndActivationRetiresPreviousVersionAtomically` compared the complete pre-retirement `ACTIVE` value with `findActiveAt`, although the repository contract returns the immutable persisted lifecycle value (`RETIRED` after a later retirement) when its activation interval contains the requested historical time.

The other 11 infrastructure tests passed in that run. All preceding reactor modules passed; the application-host module was skipped because the infrastructure module failed.

## Corrective change set — 2026-11-18

- `PostgresSemanticFactRepository.assertNoConflictingActiveFacts` now appends `AND valid_from < ?` only when the candidate has a finite `validUntil`; an unbounded candidate has no upper-bound predicate. This removes the untyped nullable placeholder and retains the same interval-overlap logic. The finite and unbounded cases are both exercised by the two semantic integration scenarios.
- The procedural historical lookup integration assertion now verifies the selected version ID, `knownActiveAt(knownAt)`, current `RETIRED` lifecycle status and retirement timestamp. It no longer expects a fabricated historical `ACTIVE` snapshot.
- `ProceduralMemoryRepository.findActiveAt` now documents this contract explicitly; it matches `InMemoryProceduralMemoryRepository` and `PostgresProceduralMemoryRepository`.

The corrected production and test sources compile under Java 21 with `-Xlint:all` in the authoring environment (320 production sources and 10 infrastructure test/stub sources). The authoring environment cannot resolve the Apache Maven distribution host and has no local PostgreSQL service, so this is not a substitute for rerunning the full GitHub Actions Maven/PostgreSQL job. M7-B1 remains unaccepted until that rerun is green.
