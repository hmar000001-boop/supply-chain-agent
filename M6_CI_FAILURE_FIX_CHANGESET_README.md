# M6 CI failure correction — change-only package

This package is an incremental correction for the M6 change-set already applied to the repository. Apply it at the repository root and overwrite the listed project files.

## Corrections

1. **B2 impasse reason propagation** — `B2ResolutionSubgraph.childContext` now places the incoming parent impasse reason in `CycleContext.lastReason`. Once the child lifecycle has produced a reason, the latest child reason takes precedence. The B2 test verifies that the original cause reaches `openImpasse`.
2. **LangGraph4j exception wrapping in tests** — approval-binding and human-control identity tests now inspect the exception cause chain for `SecurityException`. They still require the graph invocation to fail and still assert that no state-changing action executes. Production authorization behavior is unchanged.
3. **CI evidence** — the first full CI result and root-cause analysis are recorded in `docs/phase6`.

## Apply and verify

Extract this ZIP at the repository root, preserving directory paths. Then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

M6 remains unaccepted until this complete GitHub Actions workflow succeeds. The authoring environment could not bootstrap Maven 3.9.16 because the configured Apache distribution host was not resolvable; no corrected Maven/Surefire pass is claimed here.

## Project files in this package

- `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/B2ResolutionSubgraph.java`
- `supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/CognitiveCycleGraphTest.java`
- `docs/phase6/M6-CI-FAILURE-ANALYSIS-2026-11-17.md`
- `docs/phase6/M6-STATUS.md`
- `docs/phase6/M6-TEST-RESULTS.md`
- `CHANGELOG_M6.md`

`M6_CI_FIX_CHANGE_MANIFEST.csv` and `M6_CI_FIX_SHA256SUMS.txt` provide file-level integrity information for the package contents.
