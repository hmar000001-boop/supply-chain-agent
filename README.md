# M8-PreB7 R2-02 CI Fix 2 — JUnit assertion imports

## Apply
Extract this ZIP at the repository root, overwriting the matching file. Apply on top of:
- accepted R1-DEF baseline;
- R2 integrated delivery;
- R2-02 full graph integration/holdout provenance delivery;
- R2-02 CI Fix 1 (stale working-memory adapter and empty-corpus shadow candidate).

## Change
One complete source-file replacement:
`supply-chain-agent-evaluation/src/test/java/dev/supplychain/agent/evaluation/M8B4FullGraphWorkflowTest.java`

Adds the two missing JUnit 5 static imports used by assertions inside the nested `ScenarioRuntime` helper:
- `Assertions.assertNotNull`
- `Assertions.assertInstanceOf`

No runtime behavior, graph logic, fixtures, production code, or test expectations are changed.

## Validation
- Confirmed the two methods are explicitly imported and used in the full source file.
- Compiled a Java 21 nested-class/static-import fixture against API-compatible JUnit 5 assertion stubs; compilation passed.
- Verified the ZIP contains the complete source replacement at its repository-relative path.
- Full Maven compilation and Surefire execution are not claimed here; hosted CI remains the authoritative check.
