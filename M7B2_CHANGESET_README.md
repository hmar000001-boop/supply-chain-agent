# M7-B2 CI correction and closure patch — change-only delivery

## Baseline

This overlay is based exclusively on the latest user-supplied `supply-chain-agent-main (2).zip`.

Baseline archive SHA-256:

`2fe8fb2efc95bb2081d6729c40aa1ed091e15ca3c97ef8849806d843aca8e596`

## Package contents

- 45 changed project files: 32 added and 13 modified.
- Includes the CI migration-count correction, forward-only V008 scope-key migration, PostgreSQL persistence hardening, runtime/recovery/operations changes, closure-matrix updates, and opt-in PostgreSQL integration tests for M7-B2.
- Does not include unchanged repository files, build output, or a full repository copy.

## Apply

1. Start from the exact baseline archive above (or a clean repository checkout matching it).
2. Extract this ZIP at the repository root, preserving paths and allowing the patch files to overwrite their baseline counterparts.
3. Review the resulting diff before committing.

## CI correction

The failing tests reported `expected: <6> but was: <7>` because two legacy test initializers hard-coded the B1 migration count. Both now compare with `PostgresMigrationRunner.supportedMigrationCount()`. V007 is not edited; V008 is a new forward-only migration for scope-bound operation-ledger and inbox/outbox keys.

## Validation boundary

No Maven build, test execution, or live PostgreSQL verification was run for this patch. The new integration-test sources are included, but they are not reported as passing. The corrected GitHub Actions run remains the acceptance gate. M7 is not declared complete until that run is green and deployment-specific PITR/WAL and multi-instance recovery checks are recorded.

## Documentation

See `docs/phase7/M7-STATUS.md`, `docs/phase7/M7B2-CI-FIX-ANALYSIS-2026-11-19.md`, and `docs/phase7/M7B2-OPERATIONS-RUNBOOK.md`.
