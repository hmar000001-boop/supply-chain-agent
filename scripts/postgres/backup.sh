#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <destination.dump>" >&2
  exit 64
fi
: "${PGHOST:?PGHOST must be set}"
: "${PGDATABASE:?PGDATABASE must be set}"
: "${PGUSER:?PGUSER must be set}"
: "${PGPASSWORD:?PGPASSWORD must be set}"

DEST="$1"
mkdir -p "$(dirname "$DEST")"
umask 077
TMP="${DEST}.tmp.$$"
trap 'rm -f "$TMP"' EXIT

pg_dump --format=custom --no-owner --no-acl --file "$TMP"
pg_restore --list "$TMP" >/dev/null
mv "$TMP" "$DEST"
sha256sum "$DEST" > "${DEST}.sha256"
chmod 600 "$DEST" "${DEST}.sha256"
echo "Backup completed: $DEST"
echo "SHA-256 written: ${DEST}.sha256"
