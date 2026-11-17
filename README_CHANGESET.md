# M4 change-only package

Apply this archive at the repository root of the M3-accepted supply-chain-agent project.

- It contains only M4 additions and the small set of files updated for the M4 milestone.
- It does not contain the full repository or unchanged files.
- No files are deleted.
- Keep the M0/M1/M2/M3 modules and tests intact.

After applying the archive, run the same clean acceptance command used for previous milestones:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The Maven/Surefire GitHub Actions run is authoritative. The supplemental local 172-test harness is documented in `docs/phase4/M4-TEST-RESULTS.md`; it is not represented as a full Maven reactor run.

The exact M4 file inventory and SHA-256 values are in `M4_CHANGE_MANIFEST.csv`.
