# Phase 0 application entry-point API correction

## Cause

The CI run confirms that all seven LangGraph4j compatibility tests pass, including the PostgreSQL checkpoint test. The remaining compilation failure is in the application host: `Phase0Application` still invokes `CompiledGraph.execute(Map, RunnableConfig)`, an API that is not present on the resolved LangGraph4j 1.8.20 `CompiledGraph` class.

## Correction

- Replaced `execute(...)` with the documented LangGraph4j 1.8.20 `invokeFinal(GraphInput, RunnableConfig)` API.
- Used `GraphInput.args(Map<String, Object>)` to identify this as a new graph execution.
- Used an explicit `Map.<String, Object>of(...)` type witness for the graph input.
- Required a final output and checked `NodeOutput.isEND()` before treating the smoke run as complete. An interrupted graph now fails clearly rather than being reported as a completed run.
- Read the strongly typed `SupplyChainGraphState` via `NodeOutput.state()` and its accessors instead of treating graph state as a raw map.

## Deliberately unchanged

- All seven framework compatibility tests remain enabled.
- The PostgreSQL integration test remains enabled.
- No framework versions, dependency management, Enforcer rules, graph nodes, graph state, or CI steps were changed.

## API references

- LangGraph4j 1.8.20 `CompiledGraph` Javadoc: https://langgraph4j.github.io/langgraph4j/1.8/apidocs/org/bsc/langgraph4j/CompiledGraph.html
- LangGraph4j 1.8.20 `GraphInput` Javadoc: https://langgraph4j.github.io/langgraph4j/1.8/apidocs/org/bsc/langgraph4j/GraphInput.html
- LangGraph4j 1.8.20 `NodeOutput` Javadoc: https://langgraph4j.github.io/langgraph4j/1.8/apidocs/org/bsc/langgraph4j/NodeOutput.html
