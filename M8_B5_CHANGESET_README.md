# M8-B5 — final quality, security and architecture review

**Patch type:** change-only overlay.  
**Baseline:** the user's latest repository upload `supply-chain-agent-main (4).zip` (SHA-256 `8632ae0c2008b23253bb6582f2a55a33a6ed38c6f4445245ec4c42710418d4b9`) with the accepted `M8_B4_D_LATEST_REPO_CI_FIX_CHANGESET_ONLY.zip` correction applied (SHA-256 `e2a20540b21cc584919fc87e1540dab4dece53edcba8977dab24e82602f46a32`).  
**Acceptance:** B4-D is accepted separately after the user's green CI confirmation. This B5 patch is not yet accepted until the new CI run passes and the generated B2 result artifact is inspected.

## Apply

1. Commit or back up the current repository state.
2. Extract `M8_B5_FINAL_QUALITY_AND_ARCHITECTURE_REVIEW_CHANGESET_ONLY.zip` at the repository root, preserving relative paths and allowing the listed files to be replaced.
3. Review `M8_B5_CHANGE_MANIFEST.csv` and the resulting diff. The ZIP contains only the listed added/modified files; it is not a full repository replacement.
4. Push the patch and run the normal GitHub Actions workflow.

## Changes

- Declare direct LangGraph4j dependencies in each module that imports those APIs: app, evaluation and PostgreSQL infrastructure; PostgreSQL infrastructure now directly declares the PostgreSQL saver artifact too.
- Add `scripts/verify-m8-b5-architecture.py`. It checks the 11-module acyclic Maven dependency graph, direct framework dependency declarations, framework-neutral modules, the source/test inventory, CI workflow parity and presence of B2 artifact generation/upload in the active workflow.
- Remove the temporary B4-D troubleshooting assertion from the active workflow; synchronize `workflows/ci.yml` byte-for-byte with `.github/workflows/ci.yml`.
- Change the full reactor command to `clean install`, then execute `M8ComparisonCli` against the just-installed reactor artifacts. CI uploads the raw planner runs, the stratified/paired comparison report, the logical determinism check, `SHA256SUMS.txt` and `RUN-PROVENANCE.txt` under `target/m8-b2-results/`. Provenance includes repository SHA, GitHub Actions run URL/attempt and baseline archive identity.
- Reconcile M8/B4 status, decisions, traceability, README and changelog with the user-confirmed B4-D green CI and B5 review findings.

## Local validation performed

- `python3 scripts/verify-m8-b5-architecture.py`: passed.
- All 12 Maven POMs parsed as XML.
- Both workflow files parsed as YAML and are byte-identical.
- `bash -n` passed for the comparison runner, ablation runner and PostgreSQL backup/restore scripts.
- Static inventory verified: 11 modules, 404 production Java files, 62 test Java source files, 334 annotated test/factory occurrences.
- No M8-B5 Java, SQL migration or production runtime logic was changed. Migrations V001–V009 are untouched by this B5 patch.
- ZIP integrity and exact overlay reconstruction are verified in the accompanying delivery manifest/check report.

Maven/JUnit were not run locally: the authoring environment has no Maven distribution cached and cannot fetch the pinned wrapper distribution. GitHub Actions remains the acceptance gate.

## What must be inspected after CI

Download the CI artifact named `phase0-test-evaluation-and-dependency-reports` and inspect:

- `target/m8-b2-results/m8-b2-raw-runs.jsonl`
- `target/m8-b2-results/m8-b2-comparison-report.jsonl`
- `target/m8-b2-results/m8-b2-determinism-check.jsonl`

Expected design counts are 25 scenarios × 5 planners (125 primary planner runs), ten paired planner comparisons, and 125 logical replay comparisons. These expected counts must be checked against the actual output; no empirical result is claimed until those files are reviewed. If replay mismatches exist, preserve and explain them rather than hiding them.

B5 closes only after the architecture guard and full Java 21/PostgreSQL CI pass and these results have been inspected. M8-B6 then freezes the complete repository. Live ERP/WMS/carrier/provider integrations, full production-app OS restart, orphan B6 reservation reclamation, token-level usage, production IAM, PITR/RPO/RTO, tenant reconciliation and retention/legal-hold operations remain explicitly outside the tested scope.
