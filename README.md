# M8-PreB7 R2 CI Fix 1 — PostgreSQL backup/restore schema verification

## Purpose

The R2 delivery adds forward-only migration `V011__b2_procedure_drafts_and_b3_outcome_evidence.sql`. The existing PostgreSQL backup/restore verifier still expected R1-DEF schema version 10 and stopped after restoring a valid R2 backup whose schema history correctly reached version 11.

This changeset replaces the complete repository file:

- `scripts/postgres/restore-verify.sh`

## What changed

1. Derives the expected schema version from all checked-in module migration resources under `*/src/main/resources/db/migration/`. This deliberately includes V001, which is owned by the memory module, and V002–V011, which are owned by the PostgreSQL infrastructure module.
2. Requires migration filenames to form one contiguous sequence beginning at V001. A missing or duplicated version fails closed before restore.
3. Compares the restored `agent_schema_history` maximum, row count and distinct-version count against that derived sequence.
4. Verifies the R1-DEF B6 lease columns and the R2 B2/B3 evidence tables and critical columns after restore.
5. Preserves the existing isolated-empty-target guard, backup checksum verification, `pg_restore --list`, single-transaction restore, and no-clean/no-overwrite behavior.

No migration SQL, migration checksums, application code, workflow build mode, or production authorization behavior is changed.

## Apply

Extract this ZIP at the repository root, overwriting `scripts/postgres/restore-verify.sh`. This is an incremental correction to the already-applied R2 changeset; do not remove the R2 V011 migration or other R2 files.

Then run the normal hosted acceptance command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

The CI backup/restore step will back up the migrated database and restore it into a separate empty database. The restore verifier now expects the latest contiguous migration sequence present in the source tree, including V011.
