# M2 local validation results

**Date:** 2026-11-17  
**Toolchain:** OpenJDK 21, `javac --release 21 -Xlint:all`  
**Status:** local compilation and test harness passed; full Maven CI pending.

| Suite | Test methods | Result |
|---|---:|---|
| M1 domain and simulator | 31 | 31 passed, 0 failed |
| M2 CoALA memory | 43 | 43 passed, 0 failed |
| **Combined** | **74** | **74 passed, 0 failed** |

All M1 and M2 production Java sources were compiled with Java 21 and `-Xlint:all`. All M1 and M2 test sources were compiled against a small local JUnit API compatibility shim. A local reflection runner executed every method annotated `@Test` and reported 74 passed, zero failed.

## What this establishes

- Java 21 source compatibility and lint compilation for M1/M2 production code.
- Compilation of all M1/M2 test source files against the JUnit assertion methods used by the tests.
- Passing deterministic domain, simulator and memory lifecycle behavior under the local harness.
- No LangGraph4j, LangChain4j, JDBC or PostgreSQL driver imports in M2 production sources.

## What this does not establish

- It is not a Maven reactor build or Maven Surefire/JUnit Platform run.
- It does not prove dependency resolution on GitHub Actions.
- It does not execute the PostgreSQL schema file or validate it against a running PostgreSQL server.
- It does not provide durable PostgreSQL memory adapters; those are M7.
- It does not replace the seven M0 framework/checkpoint compatibility tests, which remain unchanged and must continue to pass in CI.

## CI acceptance command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```
