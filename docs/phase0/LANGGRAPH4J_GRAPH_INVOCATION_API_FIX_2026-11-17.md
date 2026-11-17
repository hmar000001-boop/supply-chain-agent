# LangGraph4j 1.8.20 invocation API decision

The compatibility test must use the API present in the exact artifact pinned by the project.

For synchronous, non-interrupting execution:

```java
Optional<State> result = graph.invoke(GraphInput.args(inputs), config);
```

For an execution where the final `NodeOutput` and last node ID are needed, use `invokeFinal(GraphInput, RunnableConfig)`. For node-by-node observation or interruption handling, use `stream(GraphInput, RunnableConfig)` and inspect the generator's `GraphResult`. For resumption, use `GraphInput.resume()` (or `GraphInput.resume(Map)` when applying an update as part of the resume input).

Do not call `CompiledGraph.execute(...)`: that method is not part of the published LangGraph4j 1.8.20 `CompiledGraph` API. Do not try to fix that missing method by changing only the generic type of the input map.

Version-specific API references:

- `CompiledGraph` 1.8.20: https://langgraph4j.github.io/langgraph4j/1.8/apidocs/org/bsc/langgraph4j/CompiledGraph.html
- `GraphInput` 1.8.20: https://langgraph4j.github.io/langgraph4j/1.8/apidocs/org/bsc/langgraph4j/GraphInput.html
