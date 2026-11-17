# Application host graph invocation (2026-11-17)

The application entry point now uses the LangGraph4j 1.8.20 documented API:

1. `GraphInput.args(...)` starts a new execution.
2. `CompiledGraph.invokeFinal(input, config)` runs synchronously and returns the terminal `NodeOutput` when available.
3. `NodeOutput.isEND()` distinguishes normal graph completion from an interruption.
4. `NodeOutput.state()` returns the graph's typed `SupplyChainGraphState`.

This is application-host usage, not a replacement for the compatibility tests. The tests passed in the supplied CI run and remain part of the Phase 0 compatibility gate.
