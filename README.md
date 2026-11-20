# M8-PreB7 R1-DEF — PostgreSQL CI Fix 3

This is a change-only package of three complete source-file replacements. Apply it on top of the already-applied `M8_PREB7_R1DEF_CI_FIX2_FULL_SOURCE_CHANGESET_ONLY_2026-11-20.zip` and the R1-DEF shared-budget/lease package.

## Fixes

1. `PostgresResourceBudgetLedger.ensureRegistered(...)` now compares persisted deadlines at PostgreSQL's supported microsecond precision. PostgreSQL timestamp round-trips truncate Java nanoseconds; without normalization, a host restart falsely reports that an identical persisted execution budget has been rebound. Resource limits and the canonical deadline must still match exactly. A genuinely different budget continues to fail closed with `SecurityException`. The lower-level `register(...)` behavior is unchanged.
2. `R1DefBudgetLeaseRecoveryIntegrationTest` now marks the second, still-live reservation's operation as started before committing it. This matches the production state machine and preserves the test's capacity-accounting purpose.
3. `PostgresM7B2CoreIntegrationTest` now expects schema version 10 because R1-DEF installs migration V010.

## Apply

Extract this ZIP at the repository root and overwrite the three listed files. Do not remove or replace any other files. Then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

## Validation in the authoring environment

- Java 21 focused production compilation passed for all B6 mechanism sources, `PostgresJdbc`, `PostgresPersistenceException`, and the modified `PostgresResourceBudgetLedger`.
- ZIP integrity, exact file inventory, and SHA-256 checksums verified.
- Full Maven/PostgreSQL integration execution was not available in this environment because the Maven distribution host could not be resolved. GitHub Actions remains the required acceptance run.
