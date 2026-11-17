# Change-only patch — LangGraph4j execute test generic fix

Apply the contents of this ZIP at the repository root, preserving relative paths.

This patch changes one test input from inferred `Map<String, String>` to explicit `Map<String, Object>`, which matches the LangGraph4j 1.8 `CompiledGraph.execute(Map<String, Object>, RunnableConfig)` API.

No production Java code, dependency versions, Maven Enforcer rules, or test assertions are changed.

See `CHANGELOG_GRAPH_EXECUTE_TEST_FIX.md` for details. `CHANGE_MANIFEST.csv` lists the complete file inventory and SHA-256 hashes.
