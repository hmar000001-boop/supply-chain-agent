# M6-A change-only package — decision-service composition (delivery 1/2)

This ZIP is an incremental overlay for the repository root.

## Apply

1. Start from the repository state where the M6 CI-failure correction has been applied and the user-confirmed CI run is green.
2. Keep a clean Git working tree or commit/stash existing changes.
3. Extract this ZIP at the repository root, preserving all paths and allowing listed modified files to be overwritten.
4. Review the diff.
5. Run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

## Scope

M6-A connects the M2/B1/B3/M5 decision path to the real Java services behind the LangGraph4j graph. It adds a graph-facing decorator and application graph factory. It does not yet implement the remaining B2/B4/B6/M3 control/grounding operations; those are deliberately assigned to M6-B.

The domain port builds typed, authoritative supply-chain inputs and persists evidence/receipt metadata. `InMemoryPlanningArtifactStore` is a reference implementation only; durable artifacts are M7 work.

## Validation

The M0–M5 and M6-A production source set compiled with Java 21 `javac --release 21 -Xlint:all`. Five focused M6-A service-composition tests passed in a local harness. This harness is not Maven/Surefire/JUnit Platform. Require the full GitHub Actions `clean verify` to pass before accepting M6-A.

See `M6A_CHANGE_MANIFEST.csv` and `M6A_SHA256SUMS.txt` for payload inventory and checksums.
