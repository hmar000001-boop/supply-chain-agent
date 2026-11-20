# M8 Pre-B7 — Conformance and Reliability Hardening Plan

**Workstream:** M8-PreB7  
**Baseline:** Accepted M8-B6 source freeze dated 2026-11-20  
**Source archive:** `SUPPLY_CHAIN_AGENT_M8_B6_FROZEN_SOURCE_2026-11-20.zip`  
**Source archive SHA-256:** `e68c91eb2112d76d15d80340b44424a56f2e99c03abb5d2246bb41f18887ec37`  
**R0 status:** Accepted — documentation and conformance-baseline corrections complete  
**Execution status:** R1-A/B/C accepted on project-owner-confirmed green CI. The integrated R1-DEF implementation is prepared; complete hosted Java 21/PostgreSQL CI is the remaining acceptance gate.  
**Change class in R0:** Documentation only; no executable source, tests, POMs, migrations or CI workflows changed.

## 1. Purpose

M8-PreB7 is a bounded remediation workstream before M8-B7 book writing. Its purpose is to close material implementation and evidence gaps identified by the paper-to-code review, strengthen the mechanisms that materially affect CoALA/Fan & Lan fidelity, and produce new validation evidence. It is not an attempt to maximize the number of mechanisms or to claim that every concept in either paper must be reproduced.

The accepted M8-B6 freeze remains closed and immutable. Pre-B7 work starts as a new source line based on the frozen M8-B6 source. No M8-B6 source artifact, CI result, or experimental result is silently rewritten. Each implementation increment is delivered separately as a change-only ZIP and must pass CI before the next increment is accepted.

## 2. Primary research sources and interpretation rules

1. Sumers, Yao, Narasimhan and Griffiths, *Cognitive Architectures for Language Agents*, TMLR 2024 / arXiv:2309.02427v3. The implementation baseline uses the paper's three organizing dimensions—memory, action space and decision-making—and its distinctions among working, episodic, semantic and procedural memory, retrieval, reasoning, learning, grounding, and planning/execution.
2. Fan and Lan, *From Cognitive Architectures to Language Agents: A Mechanism-Level Review of Lineage, Convergence, and Migration Gaps*, arXiv:2607.23942v1 (27 July 2026). The implementation baseline uses the seven-field mechanism representation (state, control, trigger, persistence, failure, learning and resource/governance), its independent E1–E4 and D0–D4 axes, the five residual bundles B1/B2/B3/B4/B6, and the closed B5 candidate.

Interpretation safeguards:

- CoALA is a conceptual architecture, not a prescribed class hierarchy or a checklist requiring every possible learning algorithm.
- Fan & Lan is a bounded mechanism-level review and falsifiable research agenda, not a mandatory application architecture. Residual bundles are hypotheses to test, not proven universal improvements.
- Evidence relation (E1–E4) and implementation depth (D0–D4) are distinct axes. Neither is a code-quality score. We do not assign one global E/D rating to the whole application.
- A persisted record or receipt is not called learning unless an observed outcome changes a future selection or control policy.
- B5 is closed as a residual research candidate by Fan & Lan's GraSP closest-baseline screening. The application does not implement GraSP, and B5 is not an implementation gap to fill merely for conformance.
- Positive and negative results are both retained. A simpler baseline that matches or beats a new policy is a valid reason not to enable that policy.

## 3. R0 conformance-baseline corrections

R0 makes the following binding interpretations for the remediation work:

### 3.1 CoALA retrieval and working memory

CoALA treats retrieval as a read from long-term memory into active working memory. The current application makes retrieval explicit and then carries retrieved material through evidence-set references, B1 selection and B3 bounded workspace admission; selected context references are not automatically projected into versioned `WorkingMemorySnapshot` slots. This is a genuine, bounded CoALA integration improvement for R1.

R1 should project only B1-selected, scope-valid evidence references into working memory. This projection precedes B3: B3 remains a separate bounded admission/broadcast stage, so a reference in working memory is not automatically considered a B3-admitted or broadcast item. The projection must preserve source identity and source version, while the authoritative source record retains provenance and validity timestamps for scoped dereferencing. It must reference authoritative evidence rather than duplicate large source payloads. Rejected, stale, expired or cross-scope candidates must not be projected. B3 independently decides which selected references are admitted to its bounded broadcast workspace. The B3 workspace remains a separate cycle-bounded attention/context structure; it is not renamed or conflated with CoALA working memory.

### 3.2 Fan & Lan B3 persistence boundary

The live B3 workspace is explicitly keyed by `cycleId`, capacity-bounded and closable. The source uses an in-memory workspace repository. The absence of a PostgreSQL repository for the live workspace is **not, by itself, a conformance defect**: the paper's bundle makes the winner available within a bounded broadcast cycle; it does not require the active workspace to be resurrected as durable state after a process restart.

The real missing edge is durable, attributable downstream-use/outcome evidence that can update a later admission policy. R2/R3 may persist receipts and outcome records for audit and cross-cycle learning while allowing the active workspace snapshot to expire at cycle completion. Do not persist live workspace state merely to claim persistence.

### 3.3 Fan & Lan B4 acceptance boundary

B4's residual is persistent intention, explicit reconsideration and live authority to change the method without silently changing the committed goal. The frozen implementation already has a durable `RecoveryIntention`, objective fingerprint, reconsideration and method-change transitions, delegation/suspension/resumption/completion/termination semantics, and a PostgreSQL repository.

A feedback-trained future method-selection policy is **not** a required B4 acceptance criterion in this workstream. It must not be listed as a missing B4 feature. R1 adds adversarial validation of the existing control contract: stale objective, unauthorized method replacement, oscillation, method change without diagnostic support, premature termination, and delegation/resumption under changed state. Full production authority integration and full production-graph restart evidence remain separate acceptance items.

### 3.4 Fan & Lan B5 disposition

B5 stays excluded as a closed negative case based on the reviewed paper corpus and GraSP closest-baseline screening. This is a statement about the paper's research classification, not a claim that GraSP has been integrated into this application. No B5 implementation task is authorized by this plan.

### 3.5 Adaptive learning policy

B1/B2/B3/B6 adaptive edges are implemented only after their outcome evidence, authority and persistence boundaries are explicit. New policies begin in offline evaluation or shadow mode. They must be compared with a simpler baseline on held-out scenarios and must not become active merely because they can be trained or because a metric improved on the training fixtures.

## 4. Implementation order and grouped delivery model

The order follows the dependency logic in Fan & Lan §VI-D: first make state, authority, uncertainty and recovery explicit; then connect verified outcomes to safe memory/procedure changes; finally evaluate any adaptive policy against simpler baselines and freeze the evidence. The internal workstreams remain independently testable, but the delivery model deliberately groups related work into fewer cumulative packages. A workstream label is not automatically a separate ZIP or CI cycle.

### R1 — Core state, authority and recovery hardening

**R1-A — CoALA retrieval projection (accepted; user-confirmed green CI).** Project only B1-selected, scope-valid evidence references into versioned working memory. Preserve source identity/version and evidence links without duplicating payloads. Reject stale, invalid and cross-scope references. Keep B3 as a separate bounded admission/broadcast stage.

**R1-B — B4 adversarial lifecycle validation (accepted; user-confirmed green CI).** Protect the committed objective and constraints; require explicit reconsideration before method replacement; reject stale intention versions before consulting the host directive; require trusted operator authority for termination; distinguish termination from verified objective completion.

**R1-C — Versioned uncertainty propagation (accepted; user-confirmed green CI).** Carry an execution/scope-bound assessment reference, provenance, assessment/expiry timestamps and explicit calibration state across graph checkpoints, B2, B3, M5, B4, B6, authorization, revalidation and execution. Refreshes advance exactly one revision. Idempotency keys include the exact predecessor reference so a repeated logical trigger at a later revision does not resolve to an obsolete cached assessment. All numeric assessments remain `NOT_CALIBRATED`; no probability-calibration claim is made.

**R1-DEF — Shared budget authority, reservation recovery and full-graph restart validation (one integrated delivery; implementation prepared, hosted CI pending).** This combines the remaining shared-guard, lease/recovery and complete application graph process-boundary work into one cumulative change-only ZIP and one full CI acceptance gate:

1. **D — Shared authority boundary.** Provide an explicit application composition path requiring the same exact `ResourceBudgetExecutionGuard` instance for M5 planner execution, M3 budgeted reasoning/action dispatch and B6 meta-control. Build it from the scope-bound PostgreSQL `LeasedResourceBudgetLedger`; reject mismatched instances. The older compatibility/test path remains distinct and does not imply production deployment wiring.
2. **E — Durable reservation recovery.** Add forward-only PostgreSQL migration V010; keep V001–V009 immutable. Add per-reservation owner, lease expiry, operation-start marker, monotonically increasing fencing token and durable recovery outcome. An expired unstarted reservation may be released and reacquired with a higher fence. An expired started reservation is never retried; unknown work is charged up to remaining finite budget capacity after preserving other active reservations. An unbounded resource dimension has no finite ceiling and cannot receive a mathematically conservative unknown-usage charge; production budgets must place finite limits on dimensions requiring hard caps. Explicit release is terminal and separately fenced. Provider measurement remains observable/conservative rather than trusted token-level accounting.
3. **F — Complete application graph restart evidence.** Exercise `CognitiveCycleApplicationGraph.compileFull(...)` in a child JVM with PostgreSQL-backed LangGraph4j checkpoint persistence, terminate the JVM after the graph reaches a safe clarification interrupt, then resume in a fresh JVM and verify terminal output plus a durable committed M5 planner reservation. Preserve the existing separate fault-injection tests for started/unstarted B6 reservation recovery.

**R1-DEF evidence and acceptance boundary.** Targeted tests cover exact shared-guard identity, pre-start lease reclaim, started-work conservative charge, expired-unstarted recovery ordering, stale-owner and legacy-unfenced API rejection, explicit-release fencing, M3 unknown-outcome handling after a successful effect but failed B6 commit, concurrent live owners, abrupt child-JVM death on both sides of operation start, and complete application graph checkpoint restoration in a fresh JVM. The full graph restart scenario uses deterministic test adapters and deliberately halts at the pre-domain-effect clarification boundary. It demonstrates the full application graph and checkpoint recovery, not a fully durable production graph for every memory, approval, IAM, authority and business adapter. Live ERP/WMS/supplier/carrier systems remain outside this pilot. R1-DEF is accepted only after the complete Java 21/PostgreSQL Maven CI run is green.

**R1 exit evidence.** R1-A/B/C are user-confirmed green. R1-DEF must add hosted CI evidence for the shared production composition boundary, V010 migration, durable lease/fencing/reconciliation integration tests and full-graph fresh-JVM checkpoint recovery. R1 is then closed as an implementation/conformance increment, without claiming production deployment certification or calibrated uncertainty.

### R2 — Outcome feedback, guarded adaptation and final evidence validation

The former R2, R3 and R4 workstreams are grouped into one larger R2 delivery, one cumulative change-only ZIP and one full acceptance run. The sub-workstreams below are internal implementation/validation boundaries, not separate default delivery increments.

1. **B2 verified recovery procedure drafts.** A verified child resolution may produce a typed, condition-scoped `DRAFT` procedure candidate with registered capability/action references, preconditions, expected postconditions, applicable scope, failure conditions, evidence references, version/expiry and validation requirements. Free-form findings or LLM text never become executable instructions. Validate the parent objective/state, evidence provenance, registered capabilities, postconditions and constraints. Activation requires a separate authorized validation/approval transition.
2. **B3 durable downstream-use/outcome evidence.** Persist attributable consumer receipts, candidate consumption and verified usefulness/harm with cycle, candidate/version, consumer and policy lineage. A receipt or visibility event alone is not proof of usefulness. Keep the live workspace cycle-bounded and ephemeral; persist only the evidence needed for future admission decisions and audit.
3. **B1/B3 offline or shadow feedback evaluation.** Feed verified downstream-use evidence into versioned candidate policies. Preserve the existing negative/mixed B1 comparison against top-k as the frozen baseline. Use independent held-out scenarios and preregister primary/safety metrics, confidence intervals, exclusions and rejection criteria. Candidate policies remain offline/shadow-only unless they beat the simpler baseline without unacceptable safety or cost regressions. If they do not, retain the baseline and report the negative result; do not force adaptation into production.
4. **Integrated experimental and quality validation.** Run the full Maven/PostgreSQL suite and mechanism ablations that separate new state representation, authoritative control edges and outcome-driven policy updates. Generate reproducible raw/paired/replay outputs with exact per-run fingerprints, checksums and source/run provenance. Run SAST and SCA with tool/database versions, triage and accepted exceptions. Refresh the paper-to-code/conformance matrix and create a final engineering/evidence report and source checkpoint.

**R2 acceptance boundary.** B2 drafts must be typed, idempotent, evidence-bound and non-executable until separately activated. B3 evidence must survive restart and be attributable to actual downstream use. B1/B3 policies must be versioned and held-out tested; any policy that fails its preregistered threshold stays inactive. Full CI must be green, experiments reproducible and security findings triaged. Independent penetration testing, threat-model signoff, production IAM and live external integrations remain explicit release gates unless evidence shows they were actually completed.

## 5. Global pre-B7 exit criteria

All of the following must be true before M8-B7 writing begins:

1. R1-A/B/C are accepted on user-confirmed green CI and the integrated R1-DEF delivery is accepted on full Java 21/PostgreSQL CI.
2. The grouped R2 delivery is complete and accepted on its full CI run; its final commit and source archive/checksums are identified.
3. CoALA working-memory projection has explicit provenance/scope/version/expiry semantics; B4 passes objective/method-authority adversarial tests; B6 shared guard, lease/fencing/reconciliation and complete-graph restart boundaries are demonstrated within the stated adapter limitations.
4. B2 procedure drafts are typed, evidence-bound, review-gated and fail closed; B3 outcome evidence is durable while the active workspace remains cycle-scoped.
5. B1/B3 adaptive candidates have held-out evidence. A candidate failing the preregistered criterion remains inactive, and the simpler baseline is retained where appropriate.
6. SAST/SCA results and unresolved findings are documented. Independent penetration testing, production IAM, ERP/WMS/supplier/carrier integration, PITR/RPO/RTO, tenant reconciliation and retention/legal-hold approval remain separate rollout gates unless actually completed.
7. The revised paper-to-code matrix ties every claim to source, tests, experimental evidence and limitations. No whole-paper/full-paper implementation claim is made.
8. Detailed Chapter 19 and Chapter 20 outlines are prepared and each is approved before drafting.

## 6. Change and evidence discipline

- M8-B6 remains accepted and immutable. Pre-B7 has its own source line, change-only ZIPs and checkpoints; never rewrite the B6 archive or checksums.
- Group related work into a small number of substantial deliveries. Keep D/E/F or B2/B3/B1-B3/quality workstreams independently traceable inside the package, but do not create separate ZIP/CI cycles for every subtask unless a dependency or risk genuinely requires it.
- Each delivery includes full replacement source files and new files only, repository-relative paths, a change manifest, SHA-256 checksums, rationale, tests, and validation boundary. Do not supply Git patch files or a full repository archive for incremental delivery.
- Do not claim tests were executed locally unless they were. Hosted CI evidence must be tied to a concrete commit and workflow run; user confirmation of green CI is recorded as such.
- Keep original M8-B2/B3 outputs as historical baseline evidence. New experiments are additive, independently versioned and preregistered; never rewrite old result artifacts or tune against held-out fixtures.

## 7. Current R0/R1/R2 decision

**R0 is accepted.** It corrects traceability and acceptance definitions only and does not alter M8-B6.

**R1-A and R1-B are accepted on user-confirmed green CI.** They provide selected-reference working-memory projection and adversarial validation of B4 intention/objective authority.

**R1-C is accepted on user-confirmed green CI.** It provides versioned uncertainty propagation and predecessor-scoped refresh idempotency. It does not establish statistical calibration or a production PostgreSQL uncertainty-assessment repository.

**R1-DEF is implemented and pending the next full CI run.** It groups shared guard composition, V010 fenced reservation leases and conservative orphan recovery, and full-application-graph fresh-JVM checkpoint restart evidence into one delivery. The restart test is bounded to the safe clarification boundary with deterministic test adapters; do not present it as full production adapter durability.

**R2 is the next grouped delivery.** It combines typed B2 procedure drafts, durable B3 outcome evidence, offline/held-out B1/B3 feedback evaluation, integrated ablations, security scans, conformance refresh and the final pre-B7 evidence/source freeze. No separate R2/R3/R4 ZIP cadence is planned by default.
