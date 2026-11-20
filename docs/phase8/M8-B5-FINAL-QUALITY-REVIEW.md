# M8-B5 — Final quality, security and architecture review

**Review baseline:** user-uploaded `supply-chain-agent-main (4).zip`, SHA-256 `8632ae0c2008b23253bb6582f2a55a33a6ed38c6f4445245ec4c42710418d4b9`, with accepted `M8_B4_D_LATEST_REPO_CI_FIX_CHANGESET_ONLY.zip` applied, SHA-256 `e2a20540b21cc584919fc87e1540dab4dece53edcba8977dab24e82602f46a32`.  
**Review date:** 2026-11-20.  
**Status:** review corrections and guardrails prepared; B5 acceptance awaits the next complete CI run and inspection of the generated B2 JSONL artifact set.  
**M8-B4-D:** accepted separately; the user confirmed the complete B4-D CI run is green.

## Executive assessment

The B4-D hosted CI acceptance closes the current reliability/safety implementation milestone. B5 is not adding another Fan & Lan mechanism. Fan and Lan's B5/GraSP candidate remains the previously closed negative case; it is not reopened or implemented as a new residual. This milestone performs an evidence-based final review of the repository, repairs demonstrated architecture/reproducibility defects, and captures the B2 empirical run outputs so the final freeze is evidence-backed.

The review identified two concrete repository issues:

1. Three composition/persistence/evaluation modules imported LangGraph4j APIs without declaring their own direct Maven dependency. The build succeeded through transitive dependency leakage, making module dependency ownership implicit and fragile. B5 adds direct `langgraph4j-core` dependencies to app, evaluation and PostgreSQL infrastructure, and the PostgreSQL saver artifact directly to PostgreSQL infrastructure.
2. The latest repository archive contained the B2 comparison CLI and reproduction script, but not the three actual B2 JSONL results. Thus its B2 harness was present while the reviewable run evidence was not available in the source archive. B5 changes the hosted workflow to clean-install the tested reactor, execute the matched comparison CLI against those exact installed artifacts, and upload raw runs, comparison report and determinism check with CI reports.

A further process-control defect was found: the active GitHub workflow `.github/workflows/ci.yml` and the manually maintained `workflows/ci.yml` mirror differed. The mirror had stale troubleshooting logic and different backup/restore invocation behavior. B5 removes the temporary B4-D-only source diagnostic from the active workflow and synchronizes the mirror byte-for-byte.

## Architecture and module boundary review

The root reactor has **11 Maven modules**, not 10. The internal module dependency graph is acyclic. The module responsibilities remain layered:

- Domain, memory, core, simulator, actions, mechanisms and planning remain framework-neutral.
- Runtime owns LangGraph4j cognitive orchestration and LangChain4j model/provider adapters.
- App uses LangGraph4j APIs at the application composition boundary.
- PostgreSQL infrastructure uses LangGraph4j core and the PostgreSQL saver API for checkpoint persistence.
- Evaluation uses LangGraph4j APIs to host/test the compiled application graph.

Direct framework dependencies are now declared by the modules that import them. A new standard-library-only Python guard checks the root reactor module set, dependency DAG acyclicity, direct API-to-POM dependency correspondence, framework-neutral module boundaries, source-test inventory consistency and workflow mirror parity. The active CI workflow runs this guard before Maven.

## Source and test inventory

The current source inventory is:

| Measure | Count |
|---|---:|
| Maven modules | 11 |
| Production Java files | 404 |
| Test Java source files | 62 |
| `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory` annotation occurrences | 334 |
| Test source files containing at least one such annotation | 56 |

These are static source counts, not a claim that 334 separate tests were executed in a particular CI run. The existing `M8-TEST-INVENTORY.csv` matches the current tree. The B5 guard fails if any of these module-level counts drift without updating the inventory.

## B2 experiment evidence capture

The matched comparison harness specifies 25 fixtures × 5 planners (125 primary planner runs), 10 paired planner comparisons, stratified descriptive aggregates and 125 logical replay checks. Runtime remains descriptive and is not used to rank planner quality. Replay mismatches are reported rather than hidden or automatically converted into a performance conclusion.

The latest source archive did not include the three results files. B5 therefore changes CI to run `M8ComparisonCli` after `clean install` and upload:

- `target/m8-b2-results/m8-b2-raw-runs.jsonl`
- `target/m8-b2-results/m8-b2-comparison-report.jsonl`
- `target/m8-b2-results/m8-b2-determinism-check.jsonl`

The next hosted CI output must be downloaded and examined before the B5 review can claim an empirical result. The review will check matrix completeness, run counts, all ten paired comparisons, bootstrap metadata, replay count/fingerprint status, and the explicit planner-only/no-external-dispatch boundary. No B2 outcome numbers are fabricated or inferred from the source-only archive.

## Security and failure-boundary review

Static source inspection of the current Java/SQL/scripts/workflows found:

- No production `ObjectInputStream` or Jackson default typing use in the inspected code paths.
- No production `ProcessBuilder` or `Runtime.exec` path; the process launcher is confined to the forked-JVM integration-test probe.
- No obvious hard-coded secret-like values in source/configuration from the static scan.
- Database repositories predominantly use prepared statements; static migration and health-check statements remain fixed SQL. This is not a formal SQL-injection proof.
- No `TODO`, `FIXME`, `XXX` or `HACK` markers were found in the scanned Java/SQL/script source.

These are bounded static observations, not a formal penetration test, full threat-model certification, dynamic security test or software-composition-analysis result. No CVE/SCA/SAST scanner or external penetration test is claimed as run. The existing provider boundary has deterministic tests for untrusted input, malformed evidence output and fail-closed resource-budget failure, but no live provider/ERP integration is claimed.

## B4-D acceptance and residual limitations

The user confirmed the complete GitHub Actions B4-D CI workflow is green on 2026-11-20. B4-D is accepted. Its evidence includes two child JVMs over a small LangGraph4j test graph with PostgreSQL checkpoint/persistence adapters, a deterministic marker-file provider, shared durable B6 accounting across M5/M3/B6, prompt-injection and malformed-output tests, and fail-closed provider/budget-store failures.

The following are still not established by this test suite:

- Restart of the complete production application graph as a process, rather than the small test graph.
- A live ERP/WMS/supplier/carrier/provider integration or financial/customer side effect.
- Automatic reclaim/reconciliation of an orphan B6 reservation after a hard kill between reservation and commit.
- Trusted provider token-usage accounting.
- Production identity/IAM integration and real principal/tenant deployment configuration.
- Production WAL/PITR enablement, measured RPO/RTO, retention/legal-hold policy, and legacy `__legacy_unscoped__` tenant reconciliation.
- A complete production `compileFull` host; `Phase0Application` remains an intake smoke entry point, and a production host must explicitly inject the same durable `ResourceBudgetExecutionGuard` instance into M5, M3 reasoning and B6 lifecycle.

These are deployment/product gates, not reasons to reopen the accepted M7 persistence phase or the accepted B4 test milestone. They must remain visible in the M8-B6 freeze and release-readiness record.

## Files changed in B5

B5's change-only package will:

1. Add direct LangGraph4j dependencies to `supply-chain-agent-app`, `supply-chain-agent-evaluation` and `supply-chain-agent-infrastructure-postgres`; add direct `langgraph4j-postgres-saver` to PostgreSQL infrastructure.
2. Add `scripts/verify-m8-b5-architecture.py` and execute it in CI.
3. Remove the temporary B4-D troubleshooting assertion from the active workflow; synchronize `workflows/ci.yml` with `.github/workflows/ci.yml`.
4. Change CI from `clean verify` to `clean install`, execute the B2 CLI using those exact installed reactor artifacts, and upload the three B2 JSONL outputs with test/dependency reports.
5. Reconcile README, M8 status, B4 status, decisions, traceability and changelog with the user-confirmed B4-D acceptance and the bounded B5 findings.

## Acceptance criteria

B5 can close only after:

1. The new architecture guard passes in GitHub Actions.
2. The complete Java 21 Maven/PostgreSQL `clean install` reactor passes, including existing B4-D and all M7 integration/reconciliation tests.
3. The B2 CLI exits successfully and all three JSONL files appear in the uploaded CI artifact.
4. The downloaded JSONL records are inspected; counts/matrix/paired analysis/replays are reported from the actual output and reconciled with the B2 protocol. If replays mismatch, the mismatches are preserved and explained rather than hidden.
5. The workflow mirror remains synchronized and the dependency tree artifact is captured.

Only then should M8-B6 create the final frozen source checkpoint and release-readiness summary. Chapters 19 and 20 remain gated on B6 and approval of their detailed outlines.
