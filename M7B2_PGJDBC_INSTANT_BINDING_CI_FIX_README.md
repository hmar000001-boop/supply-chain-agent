# M7-B2 pgJDBC Instant-binding CI follow-up

## Applies on top of

`M7B2_FINAL_CI_FIX_AND_CLOSURE_CHANGESET_ONLY.zip` (the immediately preceding M7-B2 patch), which itself was based on the latest user-provided `supply-chain-agent-main (2).zip`.

This is a small overlay, not a repository snapshot. Extract it at the repository root and allow the included files to overwrite existing files.

## Included correction

- Adds `PostgresJdbc`, a JDBC 4.2 helper that binds Java `Instant` values as UTC `OffsetDateTime` for PostgreSQL `timestamptz` parameters, and uses a typed SQL NULL when the value is null.
- Replaces all 36 direct timestamp `setObject` calls in ten M7-B2 PostgreSQL runtime repositories.
- Updates the V008 migration parsing test's expected statement count from 27 to 24. V008 SQL is unchanged.
- Includes a changelog and file manifest.

## CI

Run the same authoritative workflow:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

This authoring environment did not have Maven or a PostgreSQL server installed, so no successful Maven or live database verification is claimed. M7 closure remains pending until the GitHub Actions workflow is green and the deployment-specific recovery checks in `docs/phase7/M7-STATUS.md` are satisfied.
