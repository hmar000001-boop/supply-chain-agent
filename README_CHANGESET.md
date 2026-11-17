# Change set — LangGraph4j invocation API correction

Apply this ZIP at the root of the `supply-chain-agent` repository, preserving the paths inside the archive.

This is a change-only package. It contains one modified test source file and three added documentation files. It does not contain the full repository.

After applying it, run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The correction uses the official LangGraph4j 1.8.20 `CompiledGraph.invoke(GraphInput, RunnableConfig)` API. CI remains the authority for build and integration-test success.
