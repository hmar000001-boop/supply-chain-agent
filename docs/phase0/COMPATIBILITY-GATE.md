# Phase 0 — compatibility gate (completed 2026-11-17)

## Implemented tests

| Test | Intended evidence |
|---|---|
| `langchain4jAiServiceAdapterMapsOnlyKnownOutcomes` | LangChain4j 1.23.0 AI Services and `ChatModel` integration work with a deterministic fake model. |
| `unknownModelClassificationFailsClosed` | Unrecognized model output maps to `UNKNOWN`; it does not become an action or authorization. |
| `langGraph4jRoutesToRealClarificationInterruptAndResumesThroughLangChain4jService` | LangGraph4j 1.8.20 calls the LangChain4j adapter from a node, takes a conditional branch, loops through clarification and terminates. |
| `graphCanPauseUpdateStateAndResumeUsingMemorySaver` | LangGraph4j can checkpoint, interrupt before approval, accept a state update and resume. |
| `clarificationCycleIsBoundedToOneRound` | A repeated clarification request terminates after the configured limit instead of looping indefinitely. |
| `localIntakeGraphCanCompleteWithoutClarification` | The synchronous `invoke(GraphInput.args(...))` path returns the expected typed state for a READY request. |
| `postgresCheckpointCanBeReloadedByANewSaverAndResumed` | With PostgreSQL enabled, a new saver/graph instance reloads the checkpoint from PostgreSQL and resumes. This simulates process replacement; it is not an OS-level process-kill test. |

## Commands

Basic deterministic build:

```bash
./mvnw -B -ntp clean verify
```

PostgreSQL checkpoint proof:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Dependency inspection:

```bash
./mvnw -B -ntp dependency:tree -DoutputFile=target/dependency-tree.txt
```

## Exit criteria — passed in GitHub Actions on 2026-11-17

- All reactor modules compile using `--release 21`.
- All deterministic tests pass without external provider credentials.
- The graph executes a conditional path and a bounded, checkpointed human-clarification cycle. The test must demonstrate that user input is supplied externally rather than fabricated by a retry node.
- Checkpoint interruption/resumption passes.
- The PostgreSQL test can load a checkpoint using a newly created saver and compiled graph.
- V1 PostgreSQL saver limitation is respected: V1 does not persist interruption metadata. Pending approvals and authorization decisions must therefore be authoritative application records, not inferred from the checkpoint saver alone. The production database design must persist approval status independently and revalidate it before resuming an action.
- The resolved dependency tree contains LangGraph4j core and PostgreSQL saver 1.8.20 as managed by the official 1.8.20 BOM, plus LangChain4j 1.23.0, without unexpected duplicate/mismatched LangGraph4j artifacts.
- The resolved SLF4J dependency family is aligned to 2.0.20 by the official SLF4J BOM; Maven Enforcer `dependencyConvergence` remains enabled and passes.
- GitHub Actions succeeded from a clean checkout. The latest successful run reported seven runtime tests passing (five in `FrameworkCompatibilityTest`, one in-memory checkpoint test and one PostgreSQL checkpoint test), all Maven reactor modules compiling, and Maven Enforcer dependency convergence passing.

M0 is closed. M1 begins the domain implementation; the seven compatibility tests remain mandatory in subsequent CI runs.
