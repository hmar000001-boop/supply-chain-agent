# M7-B1 CI failure correction — change-only package

This package corrects the three failures reported by the first GitHub Actions Maven/PostgreSQL run for M7-B1.

## Base and application

- Repository snapshot: user attachment `supply-chain-agent-main (1).zip`, archive comment/commit `a37e76555c03e15271036b054082cacaba43d277`.
- Required prior changes: M7-A PostgreSQL foundation and M7-B1 durable Semantic/Procedural/Planner persistence must already be applied.
- Package type: incremental change-only ZIP. It does not replace the repository.

Extract the archive at the repository root, preserving paths, and review the diff before committing.

## Corrections

1. `PostgresSemanticFactRepository.assertNoConflictingActiveFacts` no longer uses an untyped nullable SQL placeholder in `? IS NULL`. The `valid_from < ?` clause is included only for candidates with a finite `validUntil`; unbounded candidates omit the clause. The conflict overlap logic remains unchanged.
2. The procedural `findActiveAt` integration test now verifies selected version identity, historical activation (`knownActiveAt`), current persisted `RETIRED` status and retirement timestamp. It no longer compares the result with a stale pre-retirement record.
3. `ProceduralMemoryRepository.findActiveAt` documents the shared behavior of the in-memory and PostgreSQL adapters.

The package also updates M7-B1/M7 status, CI analysis, test results, changelog and root README.

## Local verification performed

- Java 21 `javac --release 21 -Xlint:all`: 320 production sources compiled across domain, memory, core, simulator, actions, mechanisms, planning and PostgreSQL infrastructure, using a temporary compile-only pgJDBC `PGSimpleDataSource` stub.
- 10 source files compiled for the five PostgreSQL infrastructure test classes plus temporary JUnit API stubs. These are compile checks, not JUnit test execution.
- An executable check using the actual `InMemoryProceduralMemoryRepository` confirmed that `findActiveAt` returns the retired lifecycle record and that `knownActiveAt` is true for the historical instant.
- Maven could not be bootstrapped: DNS resolution for `archive.apache.org` and `downloads.apache.org` failed. No local PostgreSQL server/client is installed.

No Maven Surefire or live PostgreSQL result is claimed. The GitHub Actions rerun remains the acceptance gate.

## Required CI command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

M7-B1 remains unaccepted until the full reactor, including the PostgreSQL integration tests and application-host module, is green.
