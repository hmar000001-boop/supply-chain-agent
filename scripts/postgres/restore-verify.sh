#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <backup.dump>" >&2
  exit 64
fi
: "${PGHOST:?PGHOST must be set}"
: "${PGDATABASE:?PGDATABASE must be set to an EMPTY isolated restore target}"
: "${PGUSER:?PGUSER must be set}"
: "${PGPASSWORD:?PGPASSWORD must be set}"
: "${PGRESTORE_TARGET_CONFIRM:?Set PGRESTORE_TARGET_CONFIRM to exactly PGDATABASE after verifying the target is isolated}"

if [[ "$PGRESTORE_TARGET_CONFIRM" != "$PGDATABASE" ]]; then
  echo "Refusing restore: PGRESTORE_TARGET_CONFIRM does not match PGDATABASE" >&2
  exit 65
fi
BACKUP="$1"
[[ -r "$BACKUP" ]] || { echo "Backup file is not readable: $BACKUP" >&2; exit 66; }
if [[ -r "${BACKUP}.sha256" ]]; then
  sha256sum --check "${BACKUP}.sha256"
fi
pg_restore --list "$BACKUP" >/dev/null
# Deliberately no --clean/--if-exists: restore only into a separately provisioned empty target.
pg_restore --exit-on-error --single-transaction --no-owner --no-acl --dbname "$PGDATABASE" "$BACKUP"
SCHEMA_VERSION="$(psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT COALESCE(MAX(version),0) FROM agent_schema_history")"
if [[ "$SCHEMA_VERSION" != "7" ]]; then
  echo "Restore verification failed: expected schema version 7, found $SCHEMA_VERSION" >&2
  exit 67
fi
psql --no-psqlrc --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT to_regclass('public.action_operation_ledger') AS operation_ledger, to_regclass('public.outbox_message') AS outbox_message, to_regclass('public.planner_session') AS planner_session"
echo "Restore verification passed for schema version $SCHEMA_VERSION on isolated target $PGDATABASE"
