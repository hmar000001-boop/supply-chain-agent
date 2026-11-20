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

# Derive the expected schema version from the checked-in migration files instead of
# pinning an old maximum here. This keeps backup/restore verification synchronized
# when a forward-only migration is added (for example, R2 V011).
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
MIGRATION_SEARCH_ROOT="$REPOSITORY_ROOT"
mapfile -t MIGRATION_VERSIONS < <(
  find "$MIGRATION_SEARCH_ROOT" -type f -path '*/src/main/resources/db/migration/V[0-9]*__*.sql' -printf '%f\n' \
    | sed -E 's/^V([0-9]+)__.*/\1/' \
    | sort -n
)
if [[ ${#MIGRATION_VERSIONS[@]} -eq 0 ]]; then
  echo "Restore verification failed: no versioned SQL migrations found under $MIGRATION_SEARCH_ROOT" >&2
  exit 67
fi
for index in "${!MIGRATION_VERSIONS[@]}"; do
  EXPECTED_SEQUENCE_VERSION=$((index + 1))
  ACTUAL_SEQUENCE_VERSION=$((10#${MIGRATION_VERSIONS[$index]}))
  if [[ "$ACTUAL_SEQUENCE_VERSION" != "$EXPECTED_SEQUENCE_VERSION" ]]; then
    echo "Restore verification failed: migration files are not contiguous; expected V$(printf '%03d' "$EXPECTED_SEQUENCE_VERSION"), found V$(printf '%03d' "$ACTUAL_SEQUENCE_VERSION")" >&2
    exit 67
  fi
done
EXPECTED_SCHEMA_VERSION=$((10#${MIGRATION_VERSIONS[-1]}))
EXPECTED_MIGRATION_COUNT="${#MIGRATION_VERSIONS[@]}"

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
if [[ "$SCHEMA_VERSION" != "$EXPECTED_SCHEMA_VERSION" ]]; then
  echo "Restore verification failed: expected schema version $EXPECTED_SCHEMA_VERSION (latest checked-in migration V$(printf '%03d' "$EXPECTED_SCHEMA_VERSION")), found $SCHEMA_VERSION" >&2
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
MISSING_R2_TABLES="$(psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT COUNT(*) FROM (VALUES ('b2_recovery_procedure_draft'),('b2_recovery_procedure_draft_history'),('b3_workspace_consumer_receipt'),('b3_downstream_outcome')) AS required(name) WHERE to_regclass('public.' || name) IS NULL")"
if [[ "$MISSING_R2_TABLES" != "0" ]]; then
  echo "Restore verification failed: $MISSING_R2_TABLES required R2 B2/B3 evidence tables are missing" >&2
  exit 71
fi
MISSING_R2_COLUMNS="$(psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT COUNT(*) FROM (VALUES ('b2_recovery_procedure_draft','scope_id'),('b2_recovery_procedure_draft','draft_id'),('b2_recovery_procedure_draft','source_fingerprint'),('b2_recovery_procedure_draft','payload'),('b2_recovery_procedure_draft_history','version'),('b2_recovery_procedure_draft_history','recorded_at'),('b3_workspace_consumer_receipt','receipt_id'),('b3_workspace_consumer_receipt','consumer_id'),('b3_workspace_consumer_receipt','payload'),('b3_downstream_outcome','idempotency_key'),('b3_downstream_outcome','candidate_id'),('b3_downstream_outcome','signal'),('b3_downstream_outcome','verified_at')) AS required(table_name,column_name) WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='public' AND c.table_name=required.table_name AND c.column_name=required.column_name)")"
if [[ "$MISSING_R2_COLUMNS" != "0" ]]; then
  echo "Restore verification failed: $MISSING_R2_COLUMNS required R2 B2/B3 evidence columns are missing" >&2
  exit 72
fi
MIGRATION_HISTORY_OK="$(psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --dbname "$PGDATABASE" \
  --command "SELECT CASE WHEN COUNT(*)=$EXPECTED_MIGRATION_COUNT AND COUNT(DISTINCT version)=$EXPECTED_MIGRATION_COUNT AND MIN(version)=1 AND MAX(version)=$EXPECTED_SCHEMA_VERSION THEN 'ok' ELSE 'bad' END FROM agent_schema_history")"
if [[ "$MIGRATION_HISTORY_OK" != "ok" ]]; then
  echo "Restore verification failed: migration history is incomplete or non-contiguous (expected versions 1..$EXPECTED_SCHEMA_VERSION)" >&2
  exit 70
fi
echo "Restore verification passed for schema version $SCHEMA_VERSION (V$(printf '%03d' "$EXPECTED_SCHEMA_VERSION")), required runtime tables, R1-DEF B6 lease columns and R2 B2/B3 evidence schema on isolated target $PGDATABASE"
