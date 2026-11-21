# GAP-01–GAP-05 implementation and validation report

**Date:** 2026-11-21  
**Project:** Supply-Chain Disruption Management Agent  
**Status:** Local targeted validation passed; hosted Maven CI pending.

## Scope

This increment implements the first five findings from `FINAL-IMPLEMENTATION-GAPS-AND-ACCEPTANCE-PLAN.md`. It is layered over the owner-confirmed F-01/F-02 and F-03/F-04 source stack. GAP-06 through GAP-10 remain open as described in the acceptance plan. No external outcome corpus was fabricated; adaptive policies remain evidence-gated.

## GAP-01 — durable B1 access, verified use and utility

- Added `B1MemoryFeatureState`, repository interfaces/in-memory implementation and PostgreSQL CAS adapter.
- State is keyed by exact memory kind, scope, item ID and immutable item revision.
- Separate idempotency keys record retrieval access and verified outcome application.
- Added `verifiedUseHistory` distinct from selected retrieval access. It is included in the next candidate's activation/accessibility history.
- Verified B3 downstream outcomes update expected utility and cue strengths; only a verifier-minted outcome capability can trigger this path.
- V014 installs the PostgreSQL store; JSON payload evolution accepts the earlier state shape by defaulting absent verified-use history to empty.

## GAP-02 — B2 active procedure reuse in a future planning cycle

- `CognitiveCycleDecisionServices` invokes `RecoveryProcedurePromotionService.retrieveActive` using trusted condition keys supplied by the decision host.
- Retrieval revalidates active status, scope, condition, evidence freshness and exact registered action descriptor fingerprints.
- Typed recovery knowledge/provenance are attached to `PlanningRequest`.
- Sequential, ToT-inspired and MCTS-inspired planners consume registered descriptor references as soft priorities; they do not treat the artifact as an executable command or capability.
- Normal M3 authorization, approval, revalidation and dispatch remain required.

## GAP-03 — B3 coalition outcome learning

- Extended coalition receipt/outcome contracts to preserve selected proposal fingerprint, preference key, members, workspace/broadcast receipt/version and immutable experiment unit.
- Verified downstream evidence must refer to the exact accepted/consumed coalition receipt and pass the verifier.
- `B3CoalitionFeedbackPolicyService` creates a bounded SHADOW candidate; independent paired holdout, non-overlap, resource/safety gates and separate activation approval are mandatory.
- `WorkspaceAdmissionService` consumes a scope-bound coalition preference adjustment only when an authorized active policy is supplied.
- V015 adds the coalition outcome schema.

## GAP-04 — explicit B4 intention lifecycle/authority contract

- Added explicit rationale, active plan reference, continuation condition, typed reconsideration triggers, authority policy and termination reason/actor.
- Lifecycle transitions check continuation conditions, trigger membership and actor authority.
- The previously implemented F-01 method override remains bound to the exact committed objective fingerprint and reaches the next registered planner.

## GAP-05 — B6 calibration lifecycle and selected-stage directives

- Added scope- and target-specific calibration observations/profiles, training, independent holdout validation, ECE gate, reviewer/approver separation, repository/service and PostgreSQL V013.
- Training requires at least 20 labelled samples per dimension; holdout validation separately requires at least 20 per dimension and rejects overlapping sample/evidence IDs. Maximum validation ECE is 0.15. These are application-specific acceptance thresholds, not values from Fan & Lan.
- Calibrated directives are bound to the exact assessment reference and calibration profile version.
- Directives influence B1 memory-kind diversity, B3 workspace-kind diversity, M5 independent alternative-hypothesis review and independent selected-plan verification at revalidation.
- Uncalibrated assessments remain neutral (`NOT_CALIBRATED`) and do not activate probability thresholds. F-02 computation downshift and the execution-wide hard budget ceiling remain unchanged.
- The current real runtime still has no validated active calibration profile; no calibrated performance claim is made.

## Validation performed

| Validation | Result | Qualification |
|---|---|---|
| Java 21 compile | Pass | Eight framework-neutral production modules, including modified runtime decision/lifecycle sources |
| PostgreSQL Java compile | Pass, 37 source files | Six source files requiring unavailable HikariCP/pgJDBC/LangGraph4j classes excluded |
| Targeted runtime tests | 48/48 pass | Lightweight JUnit-compatible runner; not Surefire |
| Targeted mechanism/planning tests | 83/83 pass | Lightweight JUnit-compatible runner; not Surefire |
| Migration parser tests | 9/9 pass | Covers migration registration through V015 |
| Total targeted tests | **140 pass, 0 fail** | No full Maven reactor or live PostgreSQL test execution locally |
| Architecture guard | Pass | `verify-m8-b5-architecture.py` |
| R2 evidence guard | Pass | `verify-m8-r2-evidence.py` |
| F-03/F-04 guard | Pass | `verify-m8-preb7-f03-f04.py` |

## Not performed / still open

- Full Maven reactor and real Surefire/JUnit execution for this candidate.
- Full LangGraph4j application/full-graph integration execution on real dependencies.
- Real PostgreSQL integration/backup/restore for V013–V015.
- Independent matched outcome experiments, B2 future-case transfer, B4 long-horizon comparison, B3 coalition baseline comparison, real B6 calibration/holdout and calibration transfer.
- GraSP-like B5 governor integration (GAP-06), complete learning-action surface (GAP-07), durable full-host/event ingress (GAP-08), experiments (GAP-09), and root-source hygiene (GAP-10).

## Acceptance

The code-level first-five implementation is prepared. It becomes an accepted source increment only after the project owner confirms hosted CI green on the exact combined changeset/frozen source. The final conformance matrix remains deliberately non-total and writing remains blocked until owner acceptance.
