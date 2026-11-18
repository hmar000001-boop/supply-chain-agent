# M7-B1 CI failure analysis and correction — 2026-11-18

## Run result

The first real GitHub Actions run completed every module through `supply-chain-agent-runtime`. The PostgreSQL infrastructure module compiled and ran 14 tests. Eleven passed; three test outcomes failed (two errors and one assertion failure). The application host was skipped because the reactor stopped at the infrastructure failure.

## Failures 1 and 2 — PostgreSQL parameter type inference

`PostgresSemanticFactRepository.assertNoConflictingActiveFacts` used this predicate:

```sql
AND (? IS NULL OR valid_from < ?)
```

The first placeholder is not constrained by a typed SQL expression. PostgreSQL parses/prepares the statement before JDBC parameter values can provide a type, so pgJDBC reports `could not determine data type of parameter $6`. The failure occurs even when the application intends to bind a timestamp.

Correction: dynamically include `AND valid_from < ?` only when the candidate's `validUntil` is finite. For an unbounded candidate, omit the predicate entirely. This preserves interval-overlap semantics while ensuring every placeholder has an inferable type. The existing semantic lifecycle test exercises a finite candidate end; the conflict test exercises an unbounded candidate.

## Failure 3 — historical procedure lookup assertion

`ProceduralMemoryRepository.findActiveAt` returns versions whose activation interval contains `knownAt`. If a version was active at that time but retired later, the immutable returned object retains its current persisted status `RETIRED`; it is not a synthesized historical object with status `ACTIVE`. This is also the behavior of `InMemoryProceduralMemoryRepository` through `ProcedureVersion.knownActiveAt`.

The original test incorrectly compared the returned value with the earlier `active1` object, which had status `ACTIVE` and an earlier lifecycle revision. Correction: assert the same version identity, `knownActiveAt(historicalKnowledgeTime)`, current `RETIRED` status and retirement timestamp. The repository contract now documents this explicitly. No production lifecycle state or historical query behavior was weakened.

## Files changed

- `supply-chain-agent-infrastructure-postgres/src/main/java/dev/supplychain/agent/infrastructure/postgres/memory/PostgresSemanticFactRepository.java`
- `supply-chain-agent-infrastructure-postgres/src/test/java/dev/supplychain/agent/infrastructure/postgres/PostgresM7B1PersistenceIntegrationTest.java`
- `supply-chain-agent-memory/src/main/java/dev/supplychain/agent/memory/procedural/ProceduralMemoryRepository.java`
- M7-B1 status, test-result, overall M7 status, changelog and root README records.

## Local verification and remaining acceptance

Java 21 compilation with `--release 21 -Xlint:all` passed for 320 production source files across the relevant modules. Ten infrastructure test/stub source files compiled. The authoring environment could not download Maven 3.9.16 because `archive.apache.org` and `downloads.apache.org` did not resolve, and it has no local PostgreSQL server. Therefore the correction is not represented as Maven/Surefire or live PostgreSQL-tested.

Rerun the full repository command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

M7-B1 remains unaccepted until the real GitHub Actions Maven/PostgreSQL run is green.
