# M7-B2 final incremental change set

## Baseline and scope

This is a cumulative **change-only overlay** for the exact user-supplied source archive `supply-chain-agent-main (2).zip`.

- Baseline SHA-256: `2fe8fb2efc95bb2081d6729c40aa1ed091e15ca3c97ef8849806d843aca8e596`
- Project files: **45** (32 added, 13 modified, 0 deleted)
- The ZIP does **not** contain the complete repository.
- It includes the cumulative M7-B2 source changes, including the earlier B2 runtime/operations work and this follow-up CI correction.

Extract the ZIP at the repository root, preserving paths. Do not extract it into an extra wrapper directory.

## What this patch addresses

1. Removes imported `langgraph4j-bom` and `slf4j-bom` POM descriptors from the parent model. The consumed `langgraph4j-core` and `langgraph4j-postgres-saver` modules remain explicitly pinned to `${langgraph4j.version}`; `slf4j-api` remains pinned to `${slf4j.version}`. Existing versions are unchanged (`1.8.20` and `2.0.20`).
2. Keeps the stale B1 migration-count assertions tied to `PostgresMigrationRunner.supportedMigrationCount()` rather than a hard-coded historical count.
3. Adds V007 and V008 as forward-only migrations; previously applied V001–V006 are not edited.
4. Adds M7-B2 durable planning/runtime/approval/operation-ledger/inbox-outbox/checkpoint/pool/health/metrics and recovery/operations implementation.
5. Adds PostgreSQL integration coverage for runtime persistence, separate Hikari pools contending for one fenced M3 lease, checkpoint reload through a replacement pool, and the CI backup/restore smoke step.

## Verification status — important

No Maven build, compilation, unit test, integration test, or live PostgreSQL test was executed in the authoring environment. The environment does not have Maven installed and external Maven distribution/repository DNS is unavailable. All 11 POM files were parsed as XML successfully; this is **not** equivalent to a Maven build.

Run the authoritative CI command after applying the patch:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The GitHub Actions workflow then runs the PostgreSQL backup and isolated restore verification step. M7 must not be marked accepted until the complete Java 21 Maven/PostgreSQL job and this restore smoke step are green.

## Remaining production rollout inputs (not safe to invent in source)

- Enable and validate WAL archiving/PITR against the target deployment and its recovery objectives.
- Approve retention durations and legal holds with the data owner; the code does not invent a universal deletion schedule.
- Supply an authoritative tenant mapping before reassigning any pre-scope data. V008 quarantines legacy unscoped records under `__legacy_unscoped__`; the code deliberately fails closed instead of guessing a tenant.

These are deployment/business configuration gates, not a request for a third M7 development phase. There is no further planned M7 source phase; formal M7 acceptance remains conditional on CI and these deployment-owned controls.

## Contents

- `M7B2_FINAL_CHANGE_MANIFEST.csv`: exact project paths, status, file size, SHA-256.
- `M7B2_FINAL_CHANGESET_SHA256SUMS.txt`: checksums for every project file in this patch.
