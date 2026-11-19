# M7-B2 PostgreSQL timestamp precision CI fix

This is an incremental patch. Apply it at the repository root **after** the previously delivered
`M7B2_PGJDBC_INSTANT_BINDING_CI_FIX_CHANGESET_ONLY.zip` (and the earlier M7-B2 cumulative patches).
It is not a complete repository archive.

## Included project files

- `PostgresJdbc.java`
- `PostgresObservationStore.java`
- `PostgresM7B2CoreIntegrationTest.java`

The patch fixes PostgreSQL `timestamptz` microsecond precision comparison and aligns the unknown
batch exception assertion with the in-memory reference implementation. It does not modify V007,
V008, or any other migration.

## CI acceptance

Run from the repository root:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

This environment could not bootstrap Maven 3.9.16 because DNS resolution for the Apache distribution
hosts failed. Consequently, no Maven build or PostgreSQL integration-test pass is claimed here.
The supplied GitHub Actions PostgreSQL run remains authoritative.
