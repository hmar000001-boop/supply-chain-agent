# GAP-01–GAP-05 implementation candidate — changeset and frozen source

**Project:** Supply-Chain Disruption Management Agent  
**Date:** 2026-11-21  
**Base:** owner-confirmed F-01–F-04 hosted-CI-green stack  
**Increment:** GAP-01 through GAP-05  
**Hosted CI:** pending for this candidate.

## Application order

The changeset is repository-root-relative. Apply it only over the source stack containing the accepted F-01/F-02 and F-03/F-04 increments. Do not apply it directly to the original base ZIP without those overlays. A complete source snapshot accompanies this delivery as a reproducible checkpoint.

## What this increment implements

- **GAP-01 / B1:** durable exact-memory-revision retrieval-access and verified-use histories, cue associations and expected utility; only verifier-bound B3 outcomes update learned features; PostgreSQL V014.
- **GAP-02 / B2:** approved recovery procedures are retrieved in later normal planning cycles by trusted condition keys, revalidated for scope/evidence/live action-descriptor identity, and supplied to M5 as typed non-executable planning knowledge and soft search priority. No capability or execution authority is granted.
- **GAP-03 / B3:** exact coalition/proposal/member/receipt/outcome attribution; shadow preference trainer; disjoint paired holdout, resource/safety gates and separate activation approval; future admission can use an active coalition preference adjustment; PostgreSQL V015.
- **GAP-04 / B4:** persisted rationale, plan reference, continuation condition, typed reconsideration triggers, authority and termination semantics; F-01's protected method-to-next-planner binding remains.
- **GAP-05 / B6:** target-specific train/holdout/calibration/review/approval lifecycle; assessment/profile binding; directives for selected B1/B3/M5/plan-verification boundaries; PostgreSQL V013. F-02 downshift and the immutable execution-wide resource ceiling remain.

## Local evidence

- Eight framework-neutral production modules compile with Java 21, including changed runtime decision/lifecycle sources.
- Thirty-seven PostgreSQL production Java sources compile locally; six classes requiring unavailable HikariCP/pgJDBC/LangGraph4j dependencies were excluded.
- **140 targeted tests passed, zero failed:** 48 runtime, 83 mechanism/planning and 9 migration parser tests. Execution used a lightweight JUnit-compatible runner, not Maven Surefire/JUnit.
- Three source/architecture guards pass: M8-B5 architecture, R2 evidence and F-03/F-04 integration.
- The migration chain is registered through V015 (V001 in the memory module, V002–V015 in PostgreSQL infrastructure).
- Full Maven reactor/Surefire, live PostgreSQL integration for V013–V015 and full LangGraph4j hosted graph tests were not run locally.
- The predecessor F-01–F-04 stack was owner-confirmed hosted-CI green. **Hosted CI for this GAP-01–GAP-05 candidate is pending.**

## Evidence limits and remaining gaps

- R2 still has no verified outcome corpus or independent holdout. B1/B3 feedback remains `SHADOW`/`DISABLED`; no adaptive superiority is claimed.
- B6 remains `NOT_CALIBRATED` until a real target-specific labelled corpus, independent validation and authorized activation exist.
- GAP-06 (GraSP-like governor for full Fan & Lan §VI-E synthesis runtime), GAP-07 (full selected CoALA learning-action surface), GAP-08 (durable full application host/repeat-cycle ingress), GAP-09 (independent matched experiments/calibration/transfer) and GAP-10 (two uncompiled root-level Java shadow files) remain open.
- Total paper conformance is **not** claimed. These are application-adapted realizations of the selected concepts and mechanism bundles, not literal reproductions of the historical architectures.

## Build command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Hosted CI on this exact candidate is required before accepting the increment. See `VALIDATION_REPORT.md`, `docs/research/PAPER-TO-CODE-CONFORMANCE-MATRIX.md` and `docs/research/FINAL-IMPLEMENTATION-GAPS-AND-ACCEPTANCE-PLAN.md`. Chapters 19 and 20 remain blocked pending owner approval of conformance.
