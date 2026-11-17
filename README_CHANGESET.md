# M2 change-only package

Apply this archive at the root of the existing `supply-chain-agent` repository, preserving its directory structure. It contains only files added or modified for M2; it does not contain the full repository or unchanged M0/M1 files.

- `Added`: the new `supply-chain-agent-memory` module, CoALA memory architecture/ADR/status/test documentation, changelog and manifest.
- `Modified`: root Maven reactor POM, root README and M1 status document (to record M1 acceptance and describe the current M2 milestone).
- `Deleted`: none.

After extraction, run from the repository root:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The memory module is framework-neutral. The included PostgreSQL SQL file is a schema contract only; M2 does not run it. Durable memory JDBC adapters and migration execution remain M7 scope.
