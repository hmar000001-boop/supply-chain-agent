# Apply this change set

Extract this ZIP at the repository root, preserving paths, and commit the resulting change.

The patch changes only `PostgresCheckpointCompatibilityTest.java` so its helper explicitly propagates the checked `SQLException` thrown by the LangGraph4j 1.8.20 PostgreSQL saver builder.

Then rerun:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Do not disable the PostgreSQL test or Maven Enforcer rules to work around this compilation error.
