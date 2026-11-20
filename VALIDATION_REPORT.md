# Validation report — R2 PostgreSQL restore-verifier correction

## Root cause

The R1-DEF restore verifier hardcoded schema version 10 and required exactly ten migration-history records. R2 correctly adds V011, so backup creation and SHA-256 validation succeeded, restore itself completed, but verification rejected the restored schema as newer than the stale R1-DEF expectation.

## Changes

One complete source replacement: `scripts/postgres/restore-verify.sh`.

Expected schema version is derived from all repository module migration resource paths. V001 is in `supply-chain-agent-memory`; later migrations are in `supply-chain-agent-infrastructure-postgres`. Migration filenames must form a contiguous version sequence. Restored schema version and migration-history count must match it. R2 evidence tables and required columns are explicitly checked.

## Checks executed

- `bash -n scripts/postgres/restore-verify.sh`: passed.
- Simulated restore with schema V011 and all expected tables/columns: passed.
- Simulated stale restored schema V010 while source contains V011: correctly failed with exit 67.
- Simulated missing R2 evidence table: correctly failed with exit 71.
- Simulated missing R2 evidence column: correctly failed with exit 72.
- Simulated non-contiguous migration files (V010 absent): correctly failed with exit 67.
- Simulated incomplete migration history: correctly failed with exit 70.
- Backup SHA-256 verification path: passed using a test backup and generated checksum.
- `unzip -t` and packaged source SHA-256: passed.

The restore tests used local command stubs for `psql` and `pg_restore`. No live PostgreSQL backup/restore was run locally. GitHub Actions remains the authoritative end-to-end acceptance for the actual PostgreSQL server, backup, restore and workflow.

## Safety properties retained

- Restore target must be explicitly confirmed and empty.
- Backup checksum is checked when its `.sha256` sidecar exists.
- `pg_restore --list` is checked before restoration.
- Restore remains single-transaction and uses neither `--clean` nor `--if-exists`.
- Existing runtime and B6 lease checks remain.
