# LangGraph4j resume-test assertion fix

The previous test failures were caused by reading a historical checkpoint, not by a demonstrated failure to resume.

`CompiledGraph.updateState(config, values, null)` creates a new checkpoint for the updated state and returns a `RunnableConfig` containing that checkpoint's ID. That config is necessary to resume from the updated checkpoint. It remains pinned to the same checkpoint afterward. Consequently, `getState(updateConfig)` returns the updated pre-resume checkpoint even after later graph nodes have executed.

The corrected tests use:

```java
var resumed = graph.invokeFinal(GraphInput.resume(), updateConfig)
        .orElseThrow(() -> new AssertionError("Resumed graph produced no terminal output"));
assertEquals(END, resumed.node());
var completed = resumed.state();
```

`invokeFinal` returns the terminal `NodeOutput`; asserting `END` is the explicit proof that the resumed run finished rather than interrupting again. The resulting state is then asserted directly.

The PostgreSQL integration test continues to create a second saver instance and load the checkpoint from PostgreSQL before resuming. No production graph behavior or framework dependency was changed.
