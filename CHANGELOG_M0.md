# Milestone 0 — Java framework compatibility foundation

## Added

- Java 21 Maven multi-module repository (`supply-chain-agent-core`, `supply-chain-agent-runtime`, `supply-chain-agent-app`).
- LangGraph4j 1.8.28 LTS as the sole graph/workflow orchestrator.
- LangChain4j 1.23.0 stable core through AI Services and an application-owned `RequestInterpreter` port.
- Deterministic fake `ChatModel` tests; no provider credentials are required.
- Conditional intake graph with a real checkpointed clarification interrupt, caller-supplied state update and resume.
- Bounded clarification-cycle test, in-memory checkpoint/resume test and PostgreSQL checkpoint reload/resume integration test.
- Runnable deterministic smoke application via Maven Exec Plugin and `scripts/run-smoke.sh`.
- GitHub Actions workflow using Java 21 and PostgreSQL 16.4.
- Framework-selection ADR, compatibility gate, source references and architecture-boundary notes.
- Maven Wrapper pinned to Maven 3.9.16 / Wrapper 3.3.4.

## Modified

None. This is the initial application repository baseline.

## Deleted

None.

## Verification status

Static checks completed: all Maven POMs parse as XML, shell scripts pass syntax checks, and the framework-neutral core module compiles with `javac --release 21 -Xlint:all`.

The authoring environment has Java 21 but no Maven installation, and DNS resolution for `repo.maven.apache.org` fails. Consequently no Maven reactor compile, LangGraph4j/LangChain4j tests or PostgreSQL integration test is claimed as passing. The GitHub Actions workflow is the required compatibility acceptance gate.

## Framework limitation recorded

The LangGraph4j PostgreSQL V1 saver persists checkpoints but does not persist interruption metadata. Pending approvals must be authoritative application records stored independently, with explicit revalidation before any resumed state-changing action. The Phase 0 PostgreSQL test verifies checkpoint reload/resume only.
