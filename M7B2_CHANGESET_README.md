# M7-B2 change-only delivery

## Baseline

This patch is based exclusively on the user-supplied `supply-chain-agent-main (2).zip` archive.

Baseline archive SHA-256:

`2fe8fb2efc95bb2081d6729c40aa1ed091e15ca3c97ef8849806d843aca8e596`

## Contents

This is an incremental overlay patch, not a full repository export. It contains only files added or modified for M7-B2, plus this delivery documentation and manifests. It does not include unchanged repository files, build output, or test sources.

The implementation patch adds V007 and PostgreSQL persistence for M5 planning artifacts, M6 authorized plans/execution batches/receipts, B2 child artifacts/step receipts, observations, approval records and B4 recovery intentions; an M3 fenced operation ledger; inbox/outbox primitives; LangGraph4j PostgreSQL saver bootstrap; bounded HikariCP configuration; readiness/liveness and aggregate metrics; and backup/restore scripts.

## Apply

1. Keep a clean copy/commit of the exact baseline identified above.
2. Extract this ZIP at the repository root, preserving paths and allowing the included files to overwrite their baseline counterparts.
3. Review the resulting diff before committing.

## Test and verification boundary

Per the user's explicit instruction, this delivery includes **no M7-B2 unit or integration tests** and no Maven build or verification run. Migration application, compilation, PostgreSQL integration, concurrency/failure-injection, checkpoint restart/resume, and backup/restore acceptance are not claimed as passed. Perform those checks in the next dedicated validation step.

M7-B1 is marked CI-green/accepted based on the user's confirmation. M7 itself is not marked complete: all M7-B2 closure-matrix evidence remains pending the separate test/acceptance step.

## Operational notes

See `docs/phase7/M7B2-OPERATIONS-RUNBOOK.md` for migration rollout, pool lifecycle, approval and unknown-outcome handling, at-least-once messaging, backup, isolated restore verification, and operational monitoring.
