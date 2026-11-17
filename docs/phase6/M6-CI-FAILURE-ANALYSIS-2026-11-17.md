# M6 CI failure analysis and correction — 2026-11-17

## CI run examined

Command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The user-provided GitHub Actions log shows that Java 21/Maven dependency convergence passed and all preceding M0–M5 reactor tests passed. The runtime module then ran 21 tests: 14 cognitive-cycle graph tests, one checkpoint-resume compatibility test, five framework compatibility tests and one PostgreSQL checkpoint compatibility test.

The three failures were confined to `CognitiveCycleGraphTest`:

- One B2 subgraph error: `ImpasseOpened` rejected a blank reason.
- Two assertion failures: tests expected a direct `SecurityException`, but LangGraph4j's asynchronous graph runner surfaced the node exception through `CompletionException`/`GraphRunnerException`/`ExecutionException` wrappers.

The other seven runtime compatibility tests passed, including the PostgreSQL checkpoint compatibility test. The application-host module was skipped because the runtime module failed.

## Root cause 1 — B2 context field mapping

`B2ResolutionSubgraph.invoke` correctly supplied the parent impasse cause as `IMPASSE_REASON`. However, `childContext` mapped that value to `CycleContext.summary` and populated `CycleContext.lastReason` from the child `REASON` field. At the beginning of the child lifecycle, `REASON` is blank. The existing `openImpasse` operation contract reads `lastReason`, so the `ImpasseOpened` constructor correctly rejected it.

Correction:

- The child context now maps the original `IMPASSE_REASON` to `CycleContext.lastReason` before the child has its own reason.
- After the open/investigate nodes write a child `REASON`, that more recent reason is used instead.
- `CycleContext.summary` is no longer overloaded with the impasse cause.
- The B2 graph test explicitly asserts that `openImpasse` receives the original reason.

This fixes the state mapping at its source rather than weakening the `ImpasseOpened` nonblank invariant.

## Root cause 2 — wrapped graph-node failures

The two security checks behaved as intended. The graph rejected:

- an approval record not bound to the selected candidate/candidate set/intention version; and
- a human-control response whose actor did not match the authenticated host actor.

The graph runner exposed these node failures through asynchronous wrapper exceptions. The tests incorrectly asserted that the top-level exception was exactly `SecurityException`.

Correction:

- The tests now assert that `SecurityException` exists in the thrown exception's cause chain.
- The tests still require the operation to fail and still assert that no external action was executed.
- No authorization check, exception behavior or production security boundary was relaxed.

## Files changed by this correction

- `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/B2ResolutionSubgraph.java`
- `supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/CognitiveCycleGraphTest.java`
- `docs/phase6/M6-STATUS.md`
- `docs/phase6/M6-TEST-RESULTS.md`
- `CHANGELOG_M6.md`

## Validation status

The correction has been inspected against the exact failure paths in the supplied CI log. The authoring environment does not have Maven 3.9.16 installed, and wrapper bootstrap cannot resolve the configured Apache distribution host, so the corrected Maven/Surefire tests could not be rerun here.

Do not mark M6 accepted until GitHub Actions reruns the complete reactor successfully with:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```
