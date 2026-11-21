# Final Implementation Gaps and Acceptance Plan

**Project:** Supply-Chain Disruption Management Agent  
**Related document:** `PAPER-TO-CODE-CONFORMANCE-MATRIX.md`  
**Assessment date:** 2026-11-21

**Source stack:** checksum-verified base archive plus F-01/F-02 and F-03/F-04 overlays; combined hosted CI owner-confirmed green  
**Purpose:** define what remains before the project may claim a complete application-level realization of CoALA's central runtime responsibilities and Fan & Lan's five residual bundles plus their bounded synthesis-runtime dependencies.

## Closed implementation increments (accepted)

The project owner confirmed the combined F-01/F-02 and F-03/F-04 stack passed hosted CI. These increments are closed as code-change/test gates, not as proof of the entire paper mechanisms:

- F-01: B4 persisted method authority controls the next planner, bound to the protected objective.
- F-02: B6 typed downshift changes the next planner-request budget while preserving the execution-wide hard ceiling.
- F-03: governed B2 draft-to-procedural-memory staging, independent activation and immutable V012 artifact storage.
- F-04: bounded singleton/pair/triple B3 coalition competition with deterministic truncated-search fallback.

They do not close the remaining learning, retrieval, calibration, synthesis-runtime or empirical-evidence gaps below. Maven was not independently rerun during this matrix revision; no hosted run URL/ID or commit SHA was supplied.

## 1. Target definition

“Implement both papers completely” is not an executable requirement without a scope definition. CoALA is a conceptual framework; Fan & Lan is a bounded review and research agenda. Neither paper supplies a single normative Java specification or a universal conformance test suite.

The recommended project target is:

1. **CoALA application target:** all memory, action and decision-process responsibilities used by the supply-chain domain must have explicit typed state, services, persistence semantics, full-graph wiring and tests. The application must explicitly declare whether LLM-weight adaptation and autonomous source-code modification are out of scope; they must not be silently treated as implemented.
2. **Fan & Lan residual target:** implement the defining S/C/T/P/F/L/R control invariants of B1, B2, B3, B4 and B6 in one composed runtime, and retain independent E/D mapping for each correspondence.
3. **Synthesis-runtime target:** either integrate a GraSP-like B5 skill-governance subsystem or supply it as a versioned, tested external dependency. B5 remains closed as a separate residual candidate; its use as an implementation dependency is a different question.
4. **Evidence target:** tests establish software behavior; matched experiments, independent holdouts, calibration evidence and transfer cases establish empirical claims. These must remain separate.

This plan does not require reimplementing the ten historical architectures or the 42 modern systems reviewed by Fan & Lan. They are the paper's comparison corpus, not a checklist of software modules.

## 2. Remaining implementation gaps and closure hygiene

P0 means the gap blocks a complete implementation claim for the selected synthesis target. P1 means it blocks a complete runnable application claim or complete CoALA action-surface claim. P2 is documentation/source-baseline hygiene. Experimental validation is tracked separately from CI acceptance.

| ID | Priority | Area | Current state | Required change | Acceptance evidence |
|---|---|---|---|---|---|
| GAP-01 | P0 | B1 accessibility learning | B1 selector uses activation/accessibility, latency, downstream utility and maintenance cost. R2 overlay can change candidate net utility, but not access history or accessibility. Overlay is shadow-only without a verified corpus. | Add durable access/cue/use features and an outcome update that changes both accessibility/activation and downstream utility. Keep a no-learning baseline and scope-bound approval. | Unit/state-machine tests show verified use updates both state dimensions; PostgreSQL reload preserves them; full-graph tests prove the next context admission changes; independent holdout comparison against top-k/adaptive baselines with relevant-recall and cost metrics. |
| GAP-02 | P0 | B2 procedural reuse | Approved drafts can be staged into CoALA procedural `DRAFT`, independently reviewed/activated and dereferenced through `retrieveActive`. The normal full-graph planning path does not call the dereference method to hydrate condition-specific procedure content. | Add a condition resolver and normal graph path that retrieves the active procedure, checks scope/condition/evidence/action descriptor fingerprints, and supplies its typed planning knowledge to B1/M5. Keep retrieved procedures as knowledge, never action grants. | Full-graph two-execution test: first case creates/approves/validates/activates procedure; second matching case automatically retrieves it; mismatched condition, scope, stale evidence or changed action descriptor rejects it; M3 still requires ordinary authorization. Add repeated-failure/transfer evaluation. |
| GAP-03 | P0 | B3 coalition learning | F-04 explicitly compares singleton/pair/triple alternatives, but R2 outcomes are attributed to individual consumed candidates rather than a selected coalition and its joint result. Workspace is in-memory; feedback remains shadow/disabled. | Extend receipt/outcome schema with exact selected coalition/proposal fingerprint, member set, workspace/broadcast version and coalition-level outcome attribution. Update future coalition admission using verified outcome evidence, not only summed candidate adjustments. Add missing typed candidate kinds when agents/tools/monitors are actually present. | Full-graph tests prove selected coalition → receipt → verified coalition outcome → next-cycle coalition score/admission update. Add top-k/GWA/singleton controls; check rare-evidence suppression, coalition synergy, cost and truncation behavior. |
| GAP-04 | P0 | B4 intention contract | F-01 binds persisted intention method to the next registered planner and protects the objective. `RecoveryIntention` has objective/fingerprint, owner, protected constraints, current method, status/version/reason, delegation/resume and completion proof. F-01 is now closed: the method binding reaches the next registered planner and remains objective-fingerprint-bound. | Complete the remaining explicit intention record and governor contract: separate rationale, active plan/trajectory reference, continuation condition, typed reconsideration triggers, explicit authority policy and fully specified termination semantics. Preserve the now-working F-01 method-to-router binding and objective immutability. | Schema/lifecycle tests cover each field and all decisions (continue, delegate, suspend, abandon/terminate, change method); end-to-end tests for stale commitment, goal drift, oscillation, method switch, premature stop and recovery delegation; matched B4-on/B4-off long-horizon experiments. |
| GAP-05 | P0 | B6 uncertainty calibration and propagation | Versioned evidence/hypothesis/plan uncertainty references and local MetaControlGovernor exist; planner downshift now reduces per-call compute limits. Current assessment is `NOT_CALIBRATED`; most downstream stages carry a reference, not actionable calibrated values. | Build target-specific calibration profiles from labelled outcomes. Propagate calibrated values into pre-retrieval B1, B3 coalition scoring, M5 algorithm/search allocation, M3 tool authorization, execution monitoring, verification scheduling and stopping. Extend resource accounting for any tool/verification costs the runtime controls. | Independent calibration set and calibration report; graph tests prove values affect every declared stage; risk/resource ablations show the effect of uncertainty-aware policies; evaluate sequential miscalibration, abstention, verification cost and interruption correctness. |
| GAP-06 | P0 for §VI-E claim | B5/GraSP synthesis dependency | B5 is closed by GraSP in Fan & Lan; the repository contains no typed skill DAG, confidence router, node pre/postcondition verifier, five repair operators or ReAct fallback. | Either implement a GraSP-like skill-governance module or declare a versioned external dependency with stable API and complete integration. Do not re-label B5 as a residual hypothesis. | Full path test: calibrated confidence routes to typed DAG or ReAct; node pre/postconditions enforced; five repair operators bounded; verified unaffected nodes preserved; failure escalates to global replanning/ReAct; source and tests identify exact dependency version. |
| GAP-07 | P1 | CoALA learning-action completeness | Episode learning is registered as an internal action. Semantic/procedural memory writes happen through governed services. B1/B3 policy learning is guarded and inactive. No LLM-weight or self-modifying code update is implemented. | Define the explicit CoALA learning-action scope. Add registered semantic/procedural learning actions or document why their governed service APIs are the intended action boundary. Declare model-weight adaptation and autonomous source-code mutation out of scope unless a separately reviewed adapter is selected. | Action catalog tests cover every learning action in scope; full graph can invoke each through typed authority boundaries; lifecycle/review tests prove no unapproved memory or code activation; explicit documented non-goals for weights/code if omitted. |
| GAP-08 | P1 | Full durable application composition | `CognitiveCycleApplicationGraph.compileFull` is a composition seam; full evaluation host uses in-memory adapters for workspace/procedural/artifact stores. `Phase0Application` still runs the older intake graph. | Add a runnable full application bootstrap that composes PostgreSQL repositories, durable checkpoint saver, full CoALA memory/action services, B1–B6 controls, approval provider and reasoning adapter. Expose a repeat-cycle/event-ingress host. | Start-to-finish application integration test uses durable adapters; process restart across pre-effect and post-effect/reconciliation states; repeated top-level invocations consume fresh observations while preserving intention/memory; no accidental fallback to in-memory stores. |
| GAP-09 | P1 | Empirical evidence for the five bundles | CI and fixture tests are green. R2 reports zero verified outcomes and zero holdout scenarios, `SHADOW`, `DISABLED`; B6 calibration remains absent. | Build a verified downstream-outcome corpus, independent holdout scenarios and matched baseline protocol. Run ablations for each bundle and cross-bundle dependencies. | Preregistered protocol; unique immutable experimental-unit fingerprints; independent baseline/candidate runs; verifier audit; reports with confidence intervals, task outcome, safety, resource, latency, calibration and failure/transfer metrics; publish negative results. |
| GAP-10 | **Closed in this revision** | Source and status hygiene | The prior M8 status contained stale pending language. This revision synchronizes `docs/phase8/M8-STATUS.md`, records the final F-01–F-04 source chain, and includes the 757-file SHA-256 manifest. The reconstructed tree contains zero Java files outside the 11 Maven module source roots. | No further code work is required. Preserve the source manifest and refresh it only when a later accepted source increment changes the baseline. | Status, inventory, archive chain and three static-guard results are recorded in this delivery. |

## 3. Required implementation order

### Stage A — make the defining control transitions complete

1. GAP-04: finish the B4 intention/continuation/reconsideration authority contract. B4 scopes the parent objective that B2 must preserve.
2. GAP-05: finish B6 calibrated uncertainty and cross-stage value propagation. B6 governs resource/verification/action decisions across all other bundles.
3. GAP-02: bring activated B2 procedures into the normal next-cycle planner context. Reuse must be condition-specific and evidence-checked.
4. GAP-06: decide and implement the GraSP/B5 governor dependency before claiming the complete §VI-E synthesis runtime.

### Stage B — complete outcome-dependent adaptation

5. GAP-01: update B1 accessibility and utility with verified use.
6. GAP-03: link outcomes to selected B3 coalitions and update future coalition admission.
7. GAP-09: populate the verified outcome/holdout corpus and run controlled experiments. Keep overlays shadow-only until activation gates pass.

### Stage C — complete application-level runtime

8. GAP-07: define the complete learning-action surface and explicit weight/code non-goals.
9. GAP-08: compose a durable full application host and repeated-cycle ingress.
10. GAP-10: clean source/documentation inventory and freeze a single post-gap source checkpoint.

The order is a recommendation, not a claim that every application requires every possible historical mechanism. Any decision to omit an item must narrow the corresponding book claim and be recorded as an explicit scope choice.

## 4. Definition of done

The project may claim **complete application-level realization of the selected CoALA scope and Fan & Lan synthesis target** only when:

- Every in-scope CoALA memory/action/decision responsibility has authoritative state, trigger, persistence, failure semantics, governance and integration tests.
- Every residual bundle B1/B2/B3/B4/B6 has its defining learning/authority coupling implemented, not merely a named class or an event record.
- The bounded synthesis runtime has a tested GraSP-like skill-governance dependency, or the book explicitly limits its claim to the five residual bundles without claiming §VI-E completeness.
- The seven-field S/C/T/P/F/L/R matrix and E/D recoding are updated from the final frozen source, one correspondence per E-code, with missing invariants recorded.
- Independent reviewers can follow each claimed control path from source state through trigger, authority, transition, persistence and test/experiment evidence.
- Controlled experiments compare the added bundle with the closest baselines and report negative results. Green CI is not substituted for empirical evidence.
- The final source tree, commit SHA, migration head, test report, evidence artifact and paper-to-code matrix are frozen together.

## 5. Claims that remain disallowed until acceptance

Do not claim that the application is a literal CoALA cognitive architecture, a full reproduction of ACT-R/Soar/LIDA/BDI/MIDCA/EPIC/Sigma, a full GraSP implementation, a fully migrated Fan & Lan synthesis runtime, D4 for all bundles, calibrated uncertainty, or evidence-backed adaptive superiority unless the specific acceptance evidence exists.

A precise, complete application-specific implementation can be a valuable research/reference system without claiming literal reconstruction of every architecture or every mechanism in the reviewed corpus.
