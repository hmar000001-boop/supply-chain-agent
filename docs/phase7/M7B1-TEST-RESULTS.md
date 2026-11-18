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
| 316 Java 21 production sources with `--release 21 -Xlint:all` | Passed using temporary compile-only pgJDBC API stub |
| Seven relevant test source files | Compiled using temporary JUnit API stubs |
| SQL statement splitting V001–V006 | 49 statements parsed successfully |
| Maven Surefire/JUnit Platform execution | Not run locally |
| Live PostgreSQL integration | Not run locally |

The local compile proves Java source-level integration with the actual project interfaces; it does not prove actual pgJDBC linkage, SQL execution, transaction isolation or PostgreSQL concurrency semantics. Those are explicitly gated by the repository's GitHub Actions PostgreSQL job.
