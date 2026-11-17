# LangGraph4j 1.8.20 test compilation fix

## Problem

`FrameworkCompatibilityTest.localIntakeGraphCanCompleteWithoutClarification` called `CompiledGraph.execute` with `Map.of(...)`. Because the only value was a `String`, Java inferred `Map<String, String>`. The LangGraph4j 1.8 execution API accepts `Map<String, Object>`, and Java generic types are invariant, so the call did not compile.

## Change

The test now explicitly constructs `Map.<String, Object>of(...)`. This preserves the synchronous `graph.execute(inputs, config)` invocation and the test's intended assertions. No framework dependency, runtime behavior, test coverage, or Enforcer rule is weakened.

## Scope

- Modified: `supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/FrameworkCompatibilityTest.java`
- Added: this changelog, `README_CHANGESET.md`, and `CHANGE_MANIFEST.csv`
- Deleted: none

## Validation status

The correction is based on the reported compiler diagnostic and the LangGraph4j 1.8 API signature. A full Maven/CI run has not been performed here; the user's CI remains the authoritative integration verification.
