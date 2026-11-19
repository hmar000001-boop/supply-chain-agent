# M7-B2 PostgreSQL timestamp binding CI correction

## CI failure addressed

The 2026-11-19 GitHub Actions run reached the PostgreSQL integration-test module and exposed two independent defects:

1. pgJDBC cannot infer a PostgreSQL parameter type from `java.time.Instant` passed directly to `PreparedStatement.setObject`.
2. The V008 migration parsing test asserted 27 statements although `PostgresMigrationRunner.splitStatements` returns 24 for the committed V008 script.

## Implementation

- Added package-private `PostgresJdbc.setInstant` as the single JDBC binding boundary for `Instant` values written to PostgreSQL `timestamptz` columns.
- The helper binds non-null values as `OffsetDateTime` at UTC, the JDBC 4.2 representation supported by pgJDBC for `TIMESTAMP WITH TIME ZONE`.
- Nullable timestamp values use `Types.TIMESTAMP_WITH_TIMEZONE` for SQL NULL.
- Replaced every direct `setObject(Instant)` binding in the M7-B2 runtime repositories (36 call sites across ten repository classes).
- Corrected the V008 parser expectation from 27 to 24. The migration SQL and its checksum are unchanged.

## Scope

This is a follow-up overlay for the previously delivered `M7B2_FINAL_CI_FIX_AND_CLOSURE_CHANGESET_ONLY.zip`. It does not repeat the full M7-B2 implementation patch and does not modify V001–V008.

## Validation status

The supplied CI log is the source of the failing assertions and exceptions. The correction was checked for complete removal of direct `setObject(Instant)` usage in the runtime repository package and for consistency with the V007/V008 `timestamptz` schema. A Maven build and live PostgreSQL integration run were not executed in the authoring environment; GitHub Actions must confirm the fix.
