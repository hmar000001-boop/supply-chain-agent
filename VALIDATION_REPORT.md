# Validation report — GAP-01–GAP-05 CI Fix 1

**Status:** Source-level correction prepared; hosted Maven CI required.

## Failure analyzed

The supplied 2026-11-21 GitHub Actions log shows modules 1–11 succeeding and the evaluation module failing in `M8B4FullGraphWorkflowTest` with 16 errors and one failure. All listed graph errors share the root cause:

`IllegalStateException: the current cognitive-cycle stage requires a versioned uncertainty assessment reference`

The stack points to `CognitiveCycleDecisionServices.retrieveEvidence(...)`, reached before the graph's existing post-retrieval assessment node. The forked-JVM restart failure reaches the same path and has the same underlying cause.

## Changes

- Added `assess-uncertainty-before-retrieval` to the cognitive graph.
- Rewired the initial/repeat context path to assess before calling B1 retrieval.
- Kept the existing post-retrieval assessment, pre-impasse assessment and post-B2 investigation refresh.
- Kept the required-reference check fail-closed.
- Updated regression expectations for the now-explicit first assessment revision.

## Checks performed here

| Check | Result |
|---|---|
| Source lexical delimiter balance for both changed Java files | PASS |
| Static graph-order guard (load → assess → retrieve) | PASS |
| Static fail-closed guard (retrieve still requires reference) | PASS |
| Regression assertion guard (initial/B2/human-control revisions) | PASS |
| Changeset manifest scope | 3 replacements (2 Java source, 1 documentation), 0 additions, 0 deletions |
| ZIP integrity and SHA-256 | Verified during packaging |

## Not performed

The full Maven reactor and the actual LangGraph4j/Surefire test execution were not run locally. The Maven wrapper could not download the configured Maven distribution because external DNS/network access was unavailable. Static source checks are not represented as compilation or test execution.

The required confirmation is hosted CI using:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Expected primary regression checks are the complete `M8B4FullGraphWorkflowTest` class (including the authorized B1/B3 feedback run, B4/B6 combined planning, all approval/evidence flows, B2 draft path and forked-JVM checkpoint resume) and `CognitiveCycleGraphTest`.
