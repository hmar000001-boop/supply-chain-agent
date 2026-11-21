# Validation report — F03/F04

**Date:** 2026-11-21  
**Base:** exact user-provided green-CI archive `supply-chain-agent-main (6).zip` plus F01/F02 candidate changeset  
**Change scope:** 14 added files, 21 modified files, no removals; no POM/dependency changes.

## Implemented

- F03: approved B2 drafts may be staged as governed CoALA procedural-memory DRAFTs; validation, review and activation remain separate; procedural reviewer and activator must be independent of the original B2 approver and each other. Content-addressed immutable artifact storage, scope-bound retrieval, V012 migration, current action descriptor and evidence freshness revalidation are included. Active procedures become eligible for standard M2 retrieval and B1 procedural candidate construction. No procedure is executed or dispatched automatically.
- F04: bounded first-class coalition enumeration and scoring competes singleton and multi-candidate options; coalition and proposal trace invariants are enforced; deterministic singleton fallback is used after search truncation. This is not claimed to reproduce the full LIDA mechanism.
- CI: new F03/F04 static guard added to both mirrored workflows; architecture/R2 guards and traceability inventories updated.

## Checks that passed locally

1. Java 21 compilation of 461 framework-neutral production Java sources across the applicable core modules.
2. Focused test run: **36/36 passed**, comprising `RecoveryProcedureDraftServiceTest` (7), `RecoveryProcedurePromotionServiceTest` (9), and `WorkspaceAdmissionServiceTest` (20), using the local lightweight JUnit-compatible runner.
3. PostgreSQL migration runner parsing tests: **8/8 passed**, including V012 resource/version checks.
4. Changed PostgreSQL main sources compiled with Java 21.
5. `R2OutcomeEvidencePostgresIntegrationTest` source compiled against the relevant application/repository sources and compile-only test/config stubs; its database tests were **not executed**.
6. Static guards passed: `verify-m8-b5-architecture.py`, `verify-m8-r2-evidence.py`, and `verify-m8-preb7-f03-f04.py`.
7. `.github/workflows/ci.yml` and `workflows/ci.yml` are byte-identical.
8. Package root paths, ZIP integrity and per-file SHA-256 checksums verified.

## Not verified locally

- The full Maven reactor was not run successfully. Maven wrapper distribution retrieval failed because the environment could not resolve `archive.apache.org` / `downloads.apache.org`; required real Maven/dependencies were unavailable.
- No live PostgreSQL integration test was executed locally.
- The full evaluation-host graph tests and the end-to-end R2/PostgreSQL tests must be verified by hosted CI.
- F01/F02 is still a prerequisite pending its own CI result; this package is not a claim that the combined F01–F04 stack is green.

## Required hosted CI command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Review the V012 migration, F03 procedural activation/retrieval tests, F04 coalition competition/truncation tests, full-graph integration tests and PostgreSQL R2 integration tests in the hosted run.
