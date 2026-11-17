# M6 change-only package

This archive is an overlay for the existing `supply-chain-agent` repository at its root. It contains only files added or changed for the M6 LangGraph4j orchestration milestone and its documentation.

## Apply

1. Keep a clean Git working tree or commit/stash your current work.
2. Extract this ZIP directly into the existing repository root, preserving directories and allowing the listed modified files to be overwritten.
3. Review the resulting diff before committing.
4. Run the full acceptance command from the repository root:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

M6 requires Maven Surefire/JUnit Platform execution in GitHub Actions. The authoring environment could not bootstrap Maven because network/DNS access to the Maven distribution host was unavailable. The added graph-level tests have therefore **not** been reported as passing.

## Scope and status

The changes add the LangGraph4j cognitive-cycle graph, the bounded B2 resolution subgraph, checkpoint-friendly state, typed `CognitiveCycleOperations` integration contracts, 14 deterministic graph tests, and architecture/traceability documentation. The package also updates M5 status based on the user's report that M5 GitHub Actions CI is green.

**Important:** the concrete composition-root implementation that binds every `CognitiveCycleOperations` method to the real M2/M3/M4/M5 services, along with real-service end-to-end integration tests, remains an explicit M6 acceptance gate. This ZIP does not claim a fully wired production application. M7 remains responsible for durable PostgreSQL stores/ledgers and M8 for complete system evaluation and freeze.

See `M6_CHANGE_MANIFEST.csv` for each payload path, status, byte size and SHA-256. `M6_SHA256SUMS.txt` contains payload checksums; the manifest and checksum file are excluded from their own checksum list.
