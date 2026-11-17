# Official Java source references

Research and implementation references are restricted to Java documentation, tagged Java source and Maven metadata.

## LangGraph4j 1.8 LTS

- Releases and LTS branch: https://github.com/langgraph4j/langgraph4j/releases
- 1.8 Core Library: https://langgraph4j.github.io/langgraph4j/1.8/core/core-library/
- 1.8 Wait for User Input: https://langgraph4j.github.io/langgraph4j/1.8/how-tos/wait-user-input/
- 1.8 Persistence: https://langgraph4j.github.io/langgraph4j/1.8/how-tos/persistence/
- 1.8 PostgreSQL Checkpoint: https://langgraph4j.github.io/langgraph4j/1.8/core/checkpoint-postgres/
- Official 1.8 getting started and published stable version: https://langgraph4j.github.io/langgraph4j/1.8/getting-started/
- Maven Central BOM 1.8.20: https://central.sonatype.com/artifact/org.bsc.langgraph4j/langgraph4j-bom/1.8.20
- Maven Central core 1.8.20: https://central.sonatype.com/artifact/org.bsc.langgraph4j/langgraph4j-core/1.8.20
- Maven Central PostgreSQL saver 1.8.20: https://mvnrepository.com/artifact/org.bsc.langgraph4j/langgraph4j-postgres-saver/1.8.20 (repository listing identifies Central as the publishing repository)
- Tagged source: https://github.com/langgraph4j/langgraph4j/tree/support/1.8.x

## LangChain4j stable core

- Java getting started and stable version: https://docs.langchain4j.dev/get-started/
- AI Services: https://docs.langchain4j.dev/tutorials/ai-services/
- Chat models: https://docs.langchain4j.dev/tutorials/chat-and-language-models/
- Tools: https://docs.langchain4j.dev/tutorials/tools/
- Structured outputs: https://docs.langchain4j.dev/tutorials/structured-outputs/
- Release history: https://github.com/langchain4j/langchain4j/releases

## Version observations on 2026-11-17

- LangGraph4j GitHub lists a 1.8.28 release dated 2026-11-13, but the Phase 0 CI build could not resolve `langgraph4j-core:1.8.28` or `langgraph4j-postgres-saver:1.8.28` from Maven Central.
- Maven Central metadata showed core 1.8.27 as the latest published 1.8.x core artifact, while the PostgreSQL saver 1.8.x series was published through 1.8.20. The published 1.8.20 BOM, core and saver form an aligned artifact set.
- The official LangGraph4j 1.8 getting-started guide identifies 1.8.20 as the latest stable example version. The project therefore pins the 1.8.20 BOM/core/saver set until a later aligned release is published and verified.
- LangGraph4j 1.9.9 is the current 1.9 release, but the maintainers describe 1.9.x as an evolving line with experimental features preparing for 2.0.
- LangChain4j stable core is 1.23.0; the Agentic module is on a separate experimental/beta line and is excluded from this project's runtime.

These are dependency-selection inputs, not proof of compatibility. The Maven build, tests and dependency tree must prove the combination used here.
