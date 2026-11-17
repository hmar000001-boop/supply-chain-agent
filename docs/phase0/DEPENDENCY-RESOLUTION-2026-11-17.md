# Phase 0 dependency resolution — 2026-11-17

## CI evidence

The Maven bootstrap succeeded and the reactor reached `supply-chain-agent-runtime`. Resolution then failed for both declared LangGraph4j coordinates:

- `org.bsc.langgraph4j:langgraph4j-core:1.8.28`
- `org.bsc.langgraph4j:langgraph4j-postgres-saver:1.8.28`

The failure was not caused by Maven bootstrap, Java compilation, PostgreSQL connectivity or LangChain4j. Maven reported that the requested LangGraph4j artifacts could not be found in Maven Central.

## Repository evidence checked

As of 2026-11-17:

- GitHub releases lists a 1.8.28 release, but that does not prove the Maven artifacts for every module were published to Central.
- Maven Central lists `langgraph4j-core:1.8.20` and the `langgraph4j-bom:1.8.20`.
- The PostgreSQL saver 1.8.x artifact is available at `langgraph4j-postgres-saver:1.8.20`; the published saver line did not match the 1.8.28 GitHub tag.
- The official LangGraph4j 1.8 getting-started page uses 1.8.20 as its stable dependency/BOM version.

## Resolution

Use the aligned 1.8.20 artifacts and import the official BOM:

```xml
<properties>
    <langgraph4j.version>1.8.20</langgraph4j.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.bsc.langgraph4j</groupId>
            <artifactId>langgraph4j-bom</artifactId>
            <version>${langgraph4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The runtime module continues to declare `langgraph4j-core` and `langgraph4j-postgres-saver` without local versions. The BOM controls both versions. This prevents accidental drift between the core and saver modules.

## Why not mix core 1.8.27 with saver 1.8.20?

Although core 1.8.27 is published, selecting it alongside saver 1.8.20 would create a cross-version pair whose compatibility has not been established by this project. The Phase 0 goal is to prove a reproducible framework/saver combination. The conservative choice is the latest common published module version rather than a newer core paired with an older saver.

## Why not move directly to 1.9.x?

The 1.9 line includes newer features, but the maintainers describe it as an evolving line with experimental changes preparing for 2.0. The initial Phase 0 decision favored the maintained 1.8 LTS branch. We should not change release lines merely to bypass an unpublished artifact set.

## Remaining verification

This is a dependency-coordinate correction only. GitHub Actions must rerun:

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Dphase0.postgres=true clean verify
./mvnw -B -ntp dependency:tree -DoutputFile=target/dependency-tree.txt
```

Phase 0 remains open until compilation, deterministic tests, PostgreSQL checkpoint reload/resume and dependency-tree inspection all pass.
