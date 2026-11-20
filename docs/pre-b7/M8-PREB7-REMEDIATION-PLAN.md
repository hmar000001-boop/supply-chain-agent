# M8 Pre-B7 — Conformance and Reliability Hardening Plan

**Workstream:** M8-PreB7  
**Baseline:** Accepted M8-B6 source freeze dated 2026-11-20  
**Source archive:** `SUPPLY_CHAIN_AGENT_M8_B6_FROZEN_SOURCE_2026-11-20.zip`  
**Source archive SHA-256:** `e68c91eb2112d76d15d80340b44424a56f2e99c03abb5d2246bb41f18887ec37`  
**R0 status:** Accepted — documentation and conformance-baseline corrections complete  
**Execution status:** R1 in progress — R1-A CoALA working-memory projection and R1-B B4 lifecycle adversarial tests prepared; R1-A must pass CI before R1-B is applied and validated  
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

## 4. Implementation order

The order follows the dependency logic in Fan & Lan §VI-D: make commitment, uncertainty, resource and recovery state explicit before adding adaptive learning; then implement typed recovery and bounded content-admission outcomes; only then evaluate adaptive memory policy changes.

### R1 — Core state, authority and recovery hardening

**Scope**

1. CoALA B1-selected evidence-reference projection into versioned working memory, with source-reference provenance, scope/version/expiry checks and no source-payload duplication. **R1-A code and targeted tests are drafted; CI validation remains pending.**
2. B4 adversarial lifecycle tests for objective preservation, authorized method change, reconsideration, oscillation, delegation, suspension/resumption and termination.
3. One explicit, versioned uncertainty contract with provenance, scope and calibration metadata; propagate it through relevant planning, memory-admission, verification, authorization, monitoring and stop/interruption decision boundaries where the application host owns those decisions.
4. Verify one shared durable `ResourceBudgetExecutionGuard` instance is injected across M5 planning, M3 reasoning/action dispatch and B6 at the complete production host composition boundary.
5. Reservation lease/ownership/fencing and idempotent orphan-reservation reconciliation. Expiration alone must not release unknown consumption; recovery must use conservative accounting and explicit unknown-outcome handling.
6. Complete production `compileFull` application-graph OS-process kill/relaunch tests with PostgreSQL and authoritative test adapters. Existing smaller forked-JVM graph tests remain supporting evidence, not a substitute.

**R1 exit evidence**

- Retrieval-to-working-memory integration tests cover selected-reference projection, exact replay after a graph checkpoint interruption, stale version conflict, expired memory and cross-scope rejection. R1-A adds the projection, replay-idempotence and stale-version cases; remaining cases must be covered before R1 acceptance.
- B4 adversarial tests prove that no planner/model proposal can mutate the committed objective or switch the method without the defined authority and diagnostic preconditions.
- Uncertainty provenance is traceable across the implemented lifecycle stages; absent or invalid required uncertainty fails closed where specified. Calibration is not claimed until a preregistered held-out calibration evaluation is completed.
- Shared-guard host-wiring test proves the same guard instance is used at all required execution boundaries.
- Child-JVM tests kill and relaunch the complete production application graph at each reservation/operation boundary, verifying idempotency, fencing, reconciliation and no duplicate external-effect simulation.
- No claim of real external supplier/ERP/WMS/carrier effects; use deterministic adapters and explicit host ports.

### R2 — Typed recovery procedure drafts and durable B3 outcome evidence

**Scope**

1. Extend B2 so a verified child resolution may produce a typed, condition-scoped **draft** recovery procedure candidate. A finding or free-form model text must never become executable code.
2. Represent preconditions, registered capability/action references, expected postconditions, applicable scope, failure conditions, evidence references, version, expiry and required validation/approval. Reuse the existing procedural-memory lifecycle; newly compiled candidates enter `DRAFT`, never directly `ACTIVE`.
3. Validate parent objective/state, child result, evidence provenance, action registry references, postconditions and applicable constraints before drafting. Require separate validation and activation authority.
4. Add a durable B3 receipt/outcome ledger for consumer receipt, candidate consumption, verified usefulness/harm, cycle and policy version. Keep the live workspace itself cycle-bounded and ephemeral.

**R2 exit evidence**

- Verified recoveries create idempotent, auditable DRAFT candidates; stale/unverified/unsafe recoveries cannot.
- Candidates are not executable until validated and explicitly activated under authority policy.
- Failure cases include hallucinated capability references, missing preconditions, failed postconditions, scope mismatch, expired evidence, duplicate compilation and revoked procedures.
- B3 outcome records survive restart, are attributable to admitted candidate/version and consumer/cycle, and cannot be forged by mere receipt acknowledgement.

### R3 — Offline/shadow adaptive B1 and B3 feedback

**Scope**

1. Add verified downstream-use evidence to B1: candidate selected, projected, actually consumed, contributed to a validated action/outcome, or was harmful/irrelevant. Keep maintenance cost and latency evidence separate.
2. Evaluate alternative B1 scoring/update policies using the durable evidence and the existing top-k baseline. The M8-B3 result where the current selector underperformed top-k remains in the baseline; no weight tuning against those same fixtures is accepted as evidence of improvement.
3. Add B3 outcome-dependent typed-content admission policy using durable receipts plus verified downstream outcomes, not receipt/visibility alone.
4. Version every learned/scored policy and preserve policy lineage, training/evaluation split, sample counts, objective/metrics and rollback target.
5. Keep candidate policies offline or shadow-only until held-out evaluation demonstrates the preregistered benefit and no unacceptable regressions. Activation requires explicit deployment approval.

**R3 exit evidence**

- Preregistered held-out evaluation with baseline, fixed workload/model/tool budgets, enough independent scenarios/replications, primary and safety metrics, confidence intervals, exclusion rules and rejection threshold.
- B1 outcomes cover useful recall/precision, downstream task effect, stale/harmful retrieval, selector latency and compute/context cost.
- B3 outcomes cover evidence density, required-evidence suppression, distractor robustness, context cost and admission turnover.
- If a policy fails to beat the simpler baseline under the preregistered criterion, leave it inactive and document the negative result.

### R4 — Final quality, integrated experiments and conformance audit

**Scope**

1. Run full Maven reactor CI, PostgreSQL integration, full production-graph process-kill/restart and all R1–R3 tests.
2. Run SAST and software-composition analysis (SCA), record tool/database versions, findings, triage and accepted exceptions. These scans supplement, not replace, independent threat-model review or penetration testing.
3. Run factorial/paired ablations separating new state objects, authoritative control edges and adaptive learning updates. Preserve pre-registered protocols and negative findings.
4. Refresh the source-to-paper matrix from the resulting source and tests. Mark each claim implemented, tested, experimentally demonstrated, partial, intentionally excluded or production gate.
5. Produce a final pre-B7 engineering/evidence report. The chapters may begin only after the exit criteria in §5 are satisfied or explicitly waived by the project owner with a documented limitation.

## 5. Global pre-B7 exit criteria

All of the following must be true before M8-B7 writing begins:

1. R1–R4 changes have been applied and their final Git commit is identified.
2. Full CI is green on that commit, including tests for the complete production graph and PostgreSQL recovery boundaries.
3. The CoALA working-memory retrieval projection has explicit provenance/scope/version/expiry semantics and tests.
4. B4 passes the adversarial objective/method-authority lifecycle suite.
5. B6 shared guard wiring, uncertainty handoff, reservation fencing/reconciliation and complete-graph kill/relaunch are demonstrated; any still-unavailable cross-stage calibration must be reported as partial, not implied.
6. B2 recovery-procedure compilation is typed, review-gated and fails closed; B3 feedback evidence is durable while the active workspace remains cycle-scoped.
7. B1/B3 adaptive policies have held-out evidence. If they fail the preregistered criterion, they remain inactive and the negative result is documented; the simpler baseline may remain the accepted policy.
8. Security/dependency scan results and unresolved findings are documented. Formal independent penetration testing and production IAM/ERP/WMS/supplier deployment remain separate production gates unless actually completed.
9. The revised paper-to-code matrix maps source classes, tests, experimental evidence and limitations to each claim. No whole-paper/full-paper implementation claim is made.
10. Chapter 19 and Chapter 20 detailed outlines are prepared and separately approved before chapter drafting.

## 6. Change and evidence discipline

- M8-B6 remains accepted and immutable. Pre-B7 has its own change-only patches and source checkpoints.
- Do not overwrite the frozen M8-B6 archive or its checksums.
- Every code increment includes a file manifest, rationale, test additions, validation summary and change-only ZIP.
- Do not claim tests were executed locally unless they were. CI results are tied to a concrete commit SHA and workflow run.
- Keep the original M8-B2/B3 results as historical baseline evidence; new experiments are additive and must identify their exact source revision.
- Do not retrofit post-hoc metrics or alter old experimental output. New policies require a new preregistered run.

## 7. Current R0/R1 decision

**R0 is accepted.** It corrects traceability and acceptance definitions only and does not alter M8-B6.

**R1 is in progress.** R1-A adds the CoALA reference-projection implementation to `CognitiveCycleDecisionServices` (including an execution/base-version marker so only the exact prior CAS can be replayed idempotently), propagates the updated working-memory identity/version through `CognitiveCycleGraph`, and injects the same `WorkingMemoryService` instance into the full evaluation-host decision and lifecycle compositions. Targeted tests cover successful semantic-memory reference projection, exact idempotent replay after a graph-checkpoint interruption, and stale-version fail-closed behavior. The code patch is not yet CI-validated. R1-B through R1-F remain open; no global Pre-B7 or B7 acceptance is implied.
