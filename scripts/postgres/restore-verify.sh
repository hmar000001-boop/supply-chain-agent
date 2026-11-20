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
# Fail closed if the nominated restore database is not empty. This intentionally checks
# user relations only; system catalogs and installed extensions are not treated as payload.
USER_RELATIONS="$(psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND c.relkind IN ('r','p','v','m','S','f')")"
if [[ "$USER_RELATIONS" != "0" ]]; then
  echo "Refusing restore: target database $PGDATABASE contains $USER_RELATIONS user relations" >&2
  exit 66
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
if [[ "$SCHEMA_VERSION" != "10" ]]; then
  echo "Restore verification failed: expected schema version 10 (including R1-DEF V010), found $SCHEMA_VERSION" >&2
  exit 67
fi
MISSING_RUNTIME_TABLES="$(psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT COUNT(*) FROM (VALUES ('action_operation_ledger'),('outbox_message'),('inbox_message'),('planner_session'),('runtime_approval'),('action_execution_batch'),('b6_resource_budget'),('b6_resource_budget_limit'),('b6_resource_budget_consumption'),('b6_resource_budget_reservation'),('b6_resource_budget_reservation_item'),('b6_resource_budget_actual_item')) AS required(name) WHERE to_regclass('public.' || name) IS NULL")"
if [[ "$MISSING_RUNTIME_TABLES" != "0" ]]; then
  echo "Restore verification failed: $MISSING_RUNTIME_TABLES required runtime tables (M7 plus M8-B4 B6 budget) are missing" >&2
  exit 68
fi
MISSING_B6_LEASE_COLUMNS="$(psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT COUNT(*) FROM (VALUES ('owner_id'),('lease_expires_at'),('fencing_token'),('started_at'),('recovery_outcome')) AS required(column_name) WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name='b6_resource_budget_reservation' AND c.column_name=required.column_name)")"
if [[ "$MISSING_B6_LEASE_COLUMNS" != "0" ]]; then
  echo "Restore verification failed: $MISSING_B6_LEASE_COLUMNS R1-DEF reservation lease columns are missing" >&2
  exit 69
fi
MIGRATION_HISTORY_OK="$(psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT CASE WHEN COUNT(*)=10 AND COUNT(DISTINCT version)=10 AND MIN(version)=1 AND MAX(version)=10 THEN 'ok' ELSE 'bad' END FROM agent_schema_history")"
if [[ "$MIGRATION_HISTORY_OK" != "ok" ]]; then
  echo "Restore verification failed: migration history is incomplete or non-contiguous" >&2
  exit 70
fi
echo "Restore verification passed for schema version $SCHEMA_VERSION, required runtime tables and R1-DEF B6 lease columns on isolated target $PGDATABASE"
