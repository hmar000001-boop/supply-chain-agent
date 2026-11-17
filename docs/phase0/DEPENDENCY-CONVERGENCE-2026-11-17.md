# Phase 0 dependency convergence — SLF4J alignment (2026-11-17)

## CI evidence

The Maven wrapper and LangGraph4j artifact resolution now succeed. The next CI run reaches the Enforcer `dependencyConvergence` rule in `supply-chain-agent-runtime` and reports two transitive SLF4J versions:

- LangGraph4j 1.8.20 requests `org.slf4j:slf4j-api:2.0.9`.
- LangChain4j 1.23.0 and its dependencies request `org.slf4j:slf4j-api:2.0.18`.

The failure is a genuine dependency-graph inconsistency. It is not a Java compiler or PostgreSQL error.

## Resolution

Import the official SLF4J BOM in the root project's `dependencyManagement` and set:

```xml
<slf4j.version>2.0.20</slf4j.version>
```

The BOM manages SLF4J artifacts consistently and Maven dependency management overrides the transitive soft versions requested by the two framework paths. The selected version is the current stable SLF4J 2.0 line as of 2026-11-17.

Do not remove or weaken Maven Enforcer's `dependencyConvergence` rule. Its purpose is to catch this class of conflict before the application is released.

## Why 2.0.20

SLF4J 2.0.20 is published on Maven Central and is identified by the SLF4J project as the current stable release. Both incoming versions (2.0.9 and 2.0.18) are in the same SLF4J 2.0 API line. Managing the family through its BOM is preferable to excluding one framework's logging API or pinning only one transitive edge.

## Expected verification

Run from a clean checkout:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
./mvnw -B -ntp dependency:tree -Dincludes=org.slf4j -DoutputFile=target/slf4j-dependency-tree.txt
```

The dependency tree should show a single resolved SLF4J version, 2.0.20, for all SLF4J artifacts managed by the BOM. The Enforcer convergence rule remains enabled.

This correction has not been validated by a complete Maven build in the authoring environment. The next GitHub Actions run remains the integration proof.
