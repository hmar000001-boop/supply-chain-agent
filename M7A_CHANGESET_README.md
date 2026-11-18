# M7-A change-only delivery — installation and validation

## Source baseline

This package was produced from the user-uploaded `supply-chain-agent-main (1).zip` archive (root directory `supply-chain-agent-main/`). It contains only files added or modified relative to that uploaded source. It is not a replacement repository.

## Change summary

- Added: 25 files
- Modified: 10 files
- Deleted: 0 files
- Total project files: 35

## Apply

1. Preserve a copy/branch of the latest M6-green repository.
2. Extract this ZIP at the repository root, preserving paths.
3. Review `M7A_CHANGE_MANIFEST.csv` and `M7A_SHA256SUMS.txt`.
4. Commit the change set before beginning M7-B.

The package registers the new `supply-chain-agent-infrastructure-postgres` module and adds it to the application host dependency graph. It also updates the M2 in-memory episodic idempotency behavior so it matches the PostgreSQL adapter after later review metadata is attached.

## CI acceptance

Run in GitHub Actions with the existing PostgreSQL 16.4 service:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Acceptance requires all existing M0–M6 tests to remain green, the six non-database M7-A/M2 deterministic tests to pass, and four PostgreSQL integration tests to pass under Maven Surefire/JUnit Platform. In particular, verify migration repeatability, typed Working Memory round-trip and CAS, duplicate ordered collection preservation, idempotent Episode replay before and after review, conflicting payload rejection, historical verified queries, retention and immediate-expiry behavior.

## Local validation limitations

The authoring environment had Java 21 but no installed Maven binary, no cached Maven 3.9.16 distribution, no live PostgreSQL server and no cached pgJDBC/JUnit artifacts. Maven could not be bootstrapped because outbound DNS to Apache distribution hosts was unavailable. Production/test sources were source-compiled using temporary compile-only pgJDBC/JUnit API stubs, and standalone behavioral harnesses checked the migration parser, JSON TEXT_LIST codec, SSL configuration/credential redaction, and idempotency fingerprints. These checks are not a substitute for the real Maven/PostgreSQL CI run. M7-A is delivered but not CI-accepted.

## Next delivery

M7-B will add durable Semantic/Procedural memory, planner and cognitive-runtime artifacts/ledgers, approvals and action receipts, transaction outbox/inbox, restart recovery/reconciliation, concurrency/leases, bounded connection pooling and production operations guidance.
