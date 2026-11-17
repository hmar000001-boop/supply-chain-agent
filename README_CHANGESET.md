# M5 change-only package application

Apply this archive at the root of the M4-accepted repository, preserving paths and replacing the three modified files. It contains the complete new `supply-chain-agent-planning` module and M5 documentation only; it does not contain unchanged M0–M4 files.

After applying, run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The existing M0 PostgreSQL checkpoint integration test and all M1–M4 tests must remain enabled. GitHub Actions CI is the acceptance gate. The local 208-test result documented in `docs/phase5/M5-TEST-RESULTS.md` used a JUnit-compatible authoring harness, not Maven Surefire.
