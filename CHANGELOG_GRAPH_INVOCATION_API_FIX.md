# LangGraph4j graph invocation API correction

## Problem

The compatibility test called `CompiledGraph.execute(...)`. The resolved, published LangGraph4j 1.8.20 API does not declare that method. The preceding change that only made the `Map` generic explicit therefore could not solve the compilation error.

## Correct API for the pinned 1.8.20 dependency

The version-specific LangGraph4j 1.8.20 Javadoc declares:

- `CompiledGraph.invoke(GraphInput, RunnableConfig)` returning `Optional<State>`;
- `CompiledGraph.invokeFinal(GraphInput, RunnableConfig)` returning `Optional<NodeOutput<State>>`;
- `GraphInput.args(Map<String, Object>)` for a new graph execution;
- `GraphInput.resume()` for resuming a checkpointed execution.

The test now calls `invoke(GraphInput.args(...), config)` for its synchronous, non-interrupting path. It unwraps the returned `Optional` with an explicit assertion message and asserts through the typed `SupplyChainGraphState` accessors rather than treating the result as a raw map.

The two initial streaming tests now also use `GraphInput.args(...)`; the existing resume calls continue to use `GraphInput.resume()`. This makes the distinction between a new execution and a checkpoint resume explicit and consistent.

## Why this is the correct fix

- It uses an API actually present in the pinned 1.8.20 artifact.
- It uses the version's typed `GraphInput` execution contract rather than an unsupported `execute` method.
- It retains the intended synchronous graph-completion test.
- It does not change production graph behavior, dependencies, test scope, or Enforcer rules.
- It verifies that the graph reaches its completion node by asserting the `intake-finished` event.

## Validation boundary

The method signatures were checked against the official LangGraph4j 1.8.20 Javadocs. A full Maven/CI build is not claimed here; it must be confirmed by the next repository CI run.
