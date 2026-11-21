# M8-PreB7 R2-02 — Full-Agent Workflow Integration and Holdout Provenance

**Delivery type:** changes-only ZIP, complete files at repository-relative paths.  
**Baseline:** accepted green R1-DEF + accepted R2 implementation + R2 V011 backup/restore verifier correction.  
**Purpose:** close the integration gaps identified in the independent R2 review and make the educational full cognitive-cycle implementation ready for M8-B7, subject to one hosted CI acceptance cycle.

## Apply

1. Start from the repository revision containing the accepted R1-DEF, original R2 delivery, and R2 CI Fix 1 (the V011-aware `scripts/postgres/restore-verify.sh`).
2. Extract this ZIP at the repository root and overwrite matching files.
3. Review `CHANGE_MANIFEST.csv` and `VALIDATION_REPORT.md`.
4. Commit and push the cumulative changeset.
5. Run the agreed single acceptance command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

## What this increment connects

- **B2 → runtime lifecycle → parent graph:** a resolved child return is revalidated against its current parent context, converted into a typed recovery-procedure draft using registered action references, persisted through the configured repository, and its draft ID is carried into parent graph state. Draft creation is not approval or execution.
- **B3 → broadcast → receipts/outcomes:** the full graph host injects the same configured B3 evidence service as the workspace receipt recorder. Consumer receipt capture occurs during actual graph broadcasts; a downstream outcome is separately submitted and revalidated before it can become training evidence.
- **B1/B3 → shared authorized overlay:** one `CandidateOutcomeAdjustment` derived from an explicitly authorized, immutable, scope-bound active policy is injected into the B1 retrieval selector and B3 admission service used by the compiled graph. Without an active policy, both remain neutral.
- **Holdout integrity:** training and holdout are separated by the same immutable experiment-unit SHA-256 fingerprint, not unrelated cycle/scenario identifier strings. Each holdout observation binds paired source-run fingerprints; a trusted host verifier must authenticate both runs, the common input unit, the frozen baseline version and exact candidate ID/version.

## Evidence boundary

The full-graph tests exercise actual graph composition and the B2/B3/B1 paths. The paired holdout run ledger and utility figures in the graph activation test are synthetic test fixtures: they verify the approval/provenance/overlay wiring only. They are not external evaluation results and do not demonstrate adaptive-policy superiority. With no verified outcome corpus, the deterministic R2 evidence CLI must continue to report `NO_VERIFIED_OUTCOME_CORPUS`, `SHADOW`, `DISABLED`, and `baselinePreserved=true`.

The evaluation `FullGraphEvaluationHost` is a deterministic test/evaluation composition. An application host must inject its real durable repositories and trusted run/evidence verifiers; no domain authority is manufactured by the composition layer.

## Validation summary

See `VALIDATION_REPORT.md`. The affected Java 21 production sources and test source sets were compiled locally. Focused mechanism tests passed. The full Maven reactor, real LangGraph4j runtime execution, and live PostgreSQL integration tests still require the hosted CI acceptance run; this package is not declared accepted until that run is green.
