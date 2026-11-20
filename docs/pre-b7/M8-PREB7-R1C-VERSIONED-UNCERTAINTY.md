# M8-PreB7 R1-C — Versioned uncertainty assessment and lifecycle handoff

**Status:** Implementation prepared; CI validation pending  
**Baseline:** M8-B6 accepted source plus R1-A/R1-B and the R1-A/B inventory correction  
**Research alignment:** Fan & Lan B6 (uncertainty, resource allocation, interruption and stopping); supporting CoALA decision-cycle integration

## 1. Objective

R1-C makes the uncertainty assessment a shared, explicit, versioned decision input rather than an unversioned map of numeric signals. The same exact assessment revision must be identifiable at the points where the application admits context, plans, governs intention, applies meta-control, authorizes actions, revalidates a selected plan and dispatches an operation.

This increment does not change the existing three uncertainty dimensions or tune the existing B6 governor thresholds. It establishes identity, provenance, validity, calibration status and consistent handoff.

## 2. Assessment contract

`UncertaintyAssessment` is an immutable authoritative value with:

- `Reference`: assessment series ID, monotonically increasing revision, execution ID and memory-scope ID;
- `assessedAt` and `validUntil` timestamps;
- a non-empty provenance reference;
- explicit calibration metadata; and
- exactly one signal for each supported dimension: evidence, hypothesis and plan.

The reference is checkpoint-safe. LangGraph state stores only assessment ID and revision; the execution and scope identities are obtained from the enclosing graph state and validated when the reference is reconstructed. The full assessment and its provenance remain in the host-owned assessment store.

A numeric signal in [0,1] is not automatically a calibrated probability. The current assessments are marked `NOT_CALIBRATED`. The `CALIBRATED` state requires a profile identifier, validation-evidence reference and positive validation sample count, but R1-C does not itself perform a calibration study or enable any calibrated interpretation.

## 3. Revision and retry rules

- The first assessment in an execution-scoped series is revision 1.
- A refresh preserves the assessment series ID and advances the revision by exactly one.
- The lifecycle service derives an opaque idempotency key from the logical trigger, execution/scope and exact predecessor assessment reference. Replaying the same graph checkpoint therefore reuses the same request key, while a later refresh that encounters the same logical trigger after the predecessor revision has advanced receives a distinct key and can create the next revision.
- A reference for another execution or memory scope is rejected.
- A missing, expired, future-dated, provenance-free or mismatched assessment is rejected at any stage that requires it.
- A caller cannot silently substitute the latest host revision for the precise revision stored in graph state. The host must resolve the exact referenced revision.

Exact one-step revision advancement prevents skipped revision numbers from being hidden by a greater-than comparison. Trigger idempotence prevents retrying a graph node from creating multiple revisions for one logical transition.

## 4. Lifecycle propagation

The main cognitive-cycle graph creates or refreshes the assessment at these boundaries:

1. After retrieval, before B3 workspace admission.
2. Before entering B2 from an evidence/retrieval impasse.
3. After planning/evaluation, before B4 intention governance and B6 meta-control.
4. After host human control, before resuming B6 decisions.

B2 receives the current parent assessment reference in its bounded child state. After child investigation, the child graph creates a new assessment revision using a deterministic trigger ID. Parent-return validation uses this refreshed revision. On return, the child graph passes the new ID/revision back into the parent LangGraph state before parent-context validation and reload.

The B2 refresh is important because a child investigation can consume time and add evidence; the assessment used to validate the parent return must not be an old pre-investigation snapshot.

## 5. Decision-boundary bindings

| Boundary | Required binding |
|---|---|
| B3 workspace admission | `WorkspaceAdmissionRequest.uncertaintyAssessmentReference` must equal the exact current graph reference. |
| M5 planning | `PlanningRequest.uncertaintyAssessmentReference` must equal the exact current graph reference. |
| Planner continuation | The assessment reference participates in `PlanningRequestFingerprint`; a session cannot resume under a different assessment revision. |
| B4 intention governance | The lifecycle service resolves the exact current assessment before asking the trusted authority/domain port for a directive. |
| B6 meta-control | The budget decision and uncertainty input must use the same exact graph-referenced assessment. |
| Authorization and approval | Missing, stale, expired or mismatched uncertainty causes authorization to be denied or the approval path to fail closed. |
| Final revalidation | A fresh planning request must carry the exact current reference and be deterministically checked. |
| M3 execution | Dispatch is refused when the required assessment is missing, stale, expired or mismatched. |
| Post-dispatch observation/reconciliation | Reconciliation remains available even if the assessment later expires, because once an external effect may have occurred the system must determine the actual outcome rather than skip reconciliation. |

The B3 workspace remains cycle-bounded. This increment does not turn its live workspace into persistent memory. The assessment itself is expected to be persisted by the host adapter through the lifecycle port; R1-C defines and tests the contract but does not add a production PostgreSQL assessment repository.

## 6. Validation added or extended

- `MetaControlGovernorTest`: assessment validity/scope and calibration metadata constraints.
- `CognitiveCycleLifecycleServicesTest`: predecessor-scoped trigger idempotence (including a repeated logical trigger across successive revisions), exact scope/revision matching, expiry rejection and rejection of a skipped revision number.
- `CognitiveCycleDecisionServicesTest`: B3 and M5 request builders must carry the exact assessment reference.
- `CognitiveCycleGraphTest`: B2 opens and investigates under the parent revision, refreshes once after child investigation, validates parent return under the next revision and returns that reference to the parent before reload.
- `PlanningRequestFingerprintTest`: changing only the assessment revision changes the planner-session fingerprint.
- `M8B4FullGraphWorkflowTest`: full-graph test adapter resolves and propagates exact references across B3, M5, B4 and B6.

The architecture verifier and tracked source inventory were updated for the additional test. Static verification passed locally; Maven/JUnit has not been executed in this environment.

## 7. Explicit limitations and non-claims

R1-C does not:

- demonstrate statistically calibrated uncertainty;
- change the B6 scoring model, threshold policy or governor decisions;
- implement outcome-trained uncertainty calibration or adaptive meta-control;
- provide a concrete production PostgreSQL repository for uncertainty assessments;
- establish one shared `ResourceBudgetExecutionGuard` across all production host boundaries;
- resolve orphan budget reservations after hard process termination; or
- prove full production-graph OS-process kill/relaunch recovery.

These remain R1-D/R1-E/R1-F or R3/R4 work. The assessment contract is an important enabling interface, not proof that every host-side persistence and reliability gate is complete.

## 8. Acceptance

R1-C is accepted only after the project owner's GitHub Actions run is green on the commit containing this patch, including the architecture verifier, complete Maven reactor tests and PostgreSQL integration workflow. Any runtime or test failure must be corrected before proceeding to R1-D.
