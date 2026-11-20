# M8-B6 — application source freeze and repository checkpoint

**Checkpoint date:** 2026-11-20  
**Checkpoint ID:** `SUPPLY-CHAIN-AGENT-M8-B6-SOURCE-2026-11-20`  
**Source basis:** M8-B5-reviewed repository tree, after the user confirmed the complete M8-B5 GitHub Actions workflow green on 2026-11-20.  
**Checkpoint type:** complete source snapshot, not a change-only overlay.  
**Project:** Supply-Chain Disruption Management Agent — Part III reference application.

## 1. Freeze decision

M8-B6 packages the complete M8-B5-accepted source tree as an immutable, checksum-addressed source checkpoint. The archive includes the Maven reactor, wrapper/configuration, all eleven modules, source and test files, migrations, scripts, CI workflow, architecture records, phase decisions and experiment protocol/results already committed to the source tree.

The package is a source/repository checkpoint. It is not a binary release, production deployment, security certification, or proof that external enterprise integrations are operational.

## 2. Acceptance state

- M8-B0 through M8-B4: accepted; the user confirmed the complete B4-D CI green.
- M8-B5: CI/build/architecture gate accepted; the user confirmed the complete workflow green on 2026-11-20. The workflow runs the architecture guard, Java 21/PostgreSQL `clean install`, M8-B2 CLI, non-empty result checks, checksums/provenance, PostgreSQL backup/isolated restore, dependency-tree capture and artifact upload.
- M8-B2 empirical-content review: **pending**. The three hosted JSONL files were generated/checked/uploaded by the green workflow, but their actual payload was not attached to this authoring workspace. No primary run count, paired outcome, bootstrap interval or replay-match count is asserted from the CI green signal alone.
- M8-B6: source checkpoint packaged. Final evidence freeze is conditional on the companion CI artifact being downloaded, archived beside the source ZIP and reviewed against `M8-B2-COMPARATIVE-ANALYSIS.md`.

## 3. Snapshot inclusion and exclusion

Included: `.github/`, `.mvn/`, `mvnw`, `mvnw.cmd`, all reactor POMs, Java source/test files, SQL migrations, scripts, workflow mirror, `.env.example`, documentation, manifests and the M8-B6 freeze records.

Excluded intentionally: `.git/` (the uploaded repository archives contain no Git history), all `target/` directories, local `.env` or other local secrets, IDE metadata and transient logs. The resulting archive is source-reproducible but does not reconstruct Git commit history. The CI commit SHA and Actions run ID were not provided in the conversation; the B5 workflow artifact is expected to contain exact `source_revision`, run URL/attempt and source provenance.

## 4. Reproduction

Use Java 21 and the checked-in Maven Wrapper. From the extracted repository root:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

This command expects a reachable PostgreSQL 16-compatible test service when the PostgreSQL phase-zero property is enabled. The hosted CI workflow is the accepted execution evidence for the current source tree.

To regenerate the matched B2 comparison artifacts after the full reactor install:

```bash
./mvnw -B -ntp -pl supply-chain-agent-evaluation \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.supplychain.agent.evaluation.app.M8ComparisonCli \
  -Dexec.classpathScope=runtime \
  -Dexec.args="--output-dir target/m8-b2-results"
```

Or run `./scripts/run-m8-comparison.sh`, which performs its documented `clean install` preflight and generates the three JSONL outputs. Preserve `SHA256SUMS.txt`, `RUN-PROVENANCE.txt`, the CI test/dependency reports and the exact CI artifact metadata with these outputs.

## 5. Integrity verification

- Verify the full ZIP against `M8_B6_CHECKPOINT_SHA256SUMS.txt`.
- Verify individual source files using `M8-B6-SOURCE-MANIFEST.sha256` from the checkpoint.
- The source manifest is sorted by relative path, uses SHA-256, and excludes only itself (to avoid a recursive self-hash); it includes the checkpoint metadata and freeze/readiness documents.
- The ZIP contains file paths relative to the repository root and preserves Unix executable modes for `mvnw` and shell scripts.

## 6. Explicit residual production gates

The following remain open and must not be described as production-ready merely because CI is green:

1. A complete production `compileFull` host with trusted domain and authority adapters; production host must inject the same durable `ResourceBudgetExecutionGuard` into M5, M3 reasoning and B6 lifecycle.
2. A full production-application graph OS-process kill/relaunch test. B4-D exercises a small LangGraph4j test graph across two child JVMs with PostgreSQL adapters.
3. Durable reconciliation/reclamation of orphan B6 reservations after a hard kill between reservation and commit.
4. Trusted provider token-level usage accounting; current usage is bounded by observable provider/evaluation counts and conservative charging.
5. Real ERP/WMS, supplier, carrier, provider and financial/customer-effect adapters, including their external idempotency and reconciliation contracts.
6. Production IAM/principal/tenant deployment configuration and authoritative identity integration.
7. Production WAL/PITR enablement and measured RPO/RTO; backup/isolated-restore CI smoke is not operational DR certification.
8. Legacy `__legacy_unscoped__` tenant reconciliation, retention policy and legal-hold operations.
9. Formal SAST/SCA/CVE scanning, threat-model sign-off and independent penetration testing; B5 includes bounded static source inspection only.
10. B2 experiment result review using the actual hosted JSONL payload; no planner winner or empirical performance statement should be derived from the CI status alone.

## 7. Change policy after freeze

Any source, POM, migration, script or workflow change after this checkpoint must create a new versioned archive and new complete SHA-256 manifest. Do not silently overwrite this checkpoint. Documentation-only corrections also require a new checkpoint identity if they alter the frozen archive bytes.

Chapters 19 and 20 remain gated on final evidence reconciliation, acceptance of the source checkpoint and approval of their detailed outlines.
