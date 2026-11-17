# Applying this change set

Extract this ZIP at the repository root, preserving the relative paths.

The package contains one modified Java source file and two added documentation files. It does not contain the complete repository or unchanged files.

After applying it, run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The patch is based on the CI state where all seven compatibility tests passed and only the application-host compilation failed on `Phase0Application.java`'s obsolete `graph.execute(...)` call.
