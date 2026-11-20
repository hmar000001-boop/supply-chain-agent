# PostgreSQL helper script permission fix

## Purpose

The GitHub Actions restore smoke-test failed with exit code 126 (`Permission denied`) when it directly executed `scripts/postgres/restore-verify.sh`. The script content is valid; the checked-out file mode in the current cumulative ZIP was not executable.

## Changes

- The CI workflow now invokes both PostgreSQL helper scripts explicitly through `bash`, so execution does not depend on executable-bit preservation by ZIP extraction or the host filesystem.
- Both `scripts/postgres/backup.sh` and `scripts/postgres/restore-verify.sh` are also packaged with Unix mode `100755` to restore the expected repository file mode.
- No SQL migration, Java source, PostgreSQL schema, or runtime behaviour is changed.

## Apply

Apply this small patch on top of the current M8-B4-A/B/C cumulative V3 checkout. No earlier M8 or M7 package should be reapplied.

## Validation boundary

This delivery was validated structurally (ZIP integrity, file hashes, workflow command assertions, and Unix mode metadata). Maven and the PostgreSQL backup/restore workflow were not executed in this environment. Re-run GitHub Actions to verify the end-to-end backup/restore step.
