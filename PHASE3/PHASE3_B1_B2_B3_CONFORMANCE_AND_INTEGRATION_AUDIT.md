# Phase 3 — B1, B2 and B3 Source-Level Conformance & Integration Audit

**Source of truth:** user-uploaded `supply-chain-agent-main (6).zip`  
**Source archive SHA-256:** `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`  
**Audit date:** 2026-11-21  
**Scope:** B1 retrieval/activation and outcome feedback; B2 impasse/child recovery/procedure drafts; B3 admission/workspace/broadcast/receipts/downstream outcomes; their runtime, PostgreSQL, evaluation-host and test integration.  
**Change class:** audit only. No repository source files were modified.

## 1. Executive result

The uploaded source contains a substantive, testable integration of B1, B2 and B3. It is not a set of disconnected service stubs. The full evaluation graph composes M2 retrieval with B1 selection and working-memory projection; B3 admission and consumer broadcast; and B2 typed child investigation, parent validation and recovery-draft creation. R2 additionally binds accepted B3 consumer receipts to independently verified downstream outcomes and provides a guarded, human-authorized overlay that can alter future B1/B3 candidate ranking.

However, the paper-level learning loops remain deliberately incomplete or partial:

- **B1:** the selection mechanism is real, but core features are supplied by a host port; there is no in-repository production feature-history provider or durable maintenance ledger, and no verified outcome corpus exists to support a live learned policy. The activation estimator is an engineering approximation.
- **B2:** the full typed impasse→child subproblem→typed return→evidence-bound draft path is integrated. The draft remains non-executable. Even after approval it is not promoted into CoALA procedural memory and is not retrieved as a reusable recovery procedure later. Thus the final compile-and-reuse learning edge in Fan & Lan's B2 hypothesis is not present.
- **B3:** bounded content admission, broadcast, receipts, and outcome evidence are implemented. Selection is greedy per-item admission; coalitions are formed after selection from complementary coverage. This is not a full competition among alternative coalitions as first-class workspace contents.
- **R2 guarded feedback:** the training, holdout verification and activation protocol is implemented, and the full graph can consume an activated overlay. The repository's own evidence CLI explicitly reports zero verified outcomes, zero holdout scenarios, `SHADOW`, and activation `DISABLED`. The paired evidence used in the full-graph integration test is explicitly synthetic and is only protocol/integration evidence, not empirical performance evidence.

The strongest defensible classification is: **strong integrated B1/B2/B3 engineering realization with real guarded feedback plumbing, but partial migration of the proposed learning/competition mechanisms and no evidence of adaptive superiority.**

## 2. Source inventory and review depth

The mechanism packages contain 89 production Java types: 23 B1, 24 B2, 29 B3, and 13 feedback types. The phase register also includes the runtime composition classes, evaluation host/configuration, PostgreSQL adapters/schema, procedural-memory boundary, and seven relevant test source files. Each entry is marked with a review depth. `TARGETED_SOURCE_REVIEW` means the source implementation and significant call sites were inspected; `CONTRACT_AND_INVARIANT_REVIEW` means the declaration was mapped to its responsibility/contract but should not be mistaken for an exhaustive method-by-method audit.

See `PHASE3_TARGETED_CLASS_REVIEW.csv` for the complete file/type register and `PHASE3_SOURCE_EVIDENCE_INDEX.csv` for the supporting source locations.

## 3. B1 — activation, latency, downstream utility and maintenance

### 3.1 Executed source path

`CognitiveCycleDecisionServices.retrieveEvidence()` does the following:

1. Obtains the active scope, M2 retrieval query, and B1 selection request from the decision-domain port, and checks that all scopes match.
2. Calls `MemoryRetrievalService.retrieve(query)`.
3. Converts the exact result to B1 candidates through `B1MemoryCandidateFactory` and the host-supplied `B1CandidateFeatureProvider`.
4. Calls `B1RetrievalSelector.select(...)`.
5. Persists the evidence selection through the host port.
6. Projects only stable references to selected long-term items into the CoALA working-memory snapshot, with scope, expiry, version and narrowly identified replay checks.

This makes the B1 retrieval stage part of the real decision path rather than a separate demonstration. The R1A projection is particularly valuable: the source memory remains in its authoritative repository, while the active working memory receives references.

### 3.2 Scoring semantics

For each candidate, `B1RetrievalSelector`:

- estimates activation from access history and cue associations;
- transforms activation into an accessibility probability using a sigmoid;
- predicts cognitive retrieval latency;
- accounts separately for measured infrastructure latency;
- estimates maintenance cost over the configured horizon;
- applies a bounded additive outcome adjustment when a trusted feedback overlay is configured; and
- computes net utility, orders candidates, and admits them within accessibility, utility, count and context-unit limits.

`RecencyFrequencyActivationEstimator` is explicitly an engineering approximation. The papers do not prescribe this exact equation. This distinction must remain in the book.

### 3.3 Missing B1 edges / limits

**F3-01 — Feature history is a host contract, not an implemented production subsystem (Medium).** `B1CandidateFeatureProvider` is a functional port. The main-source tree does not contain a production implementation that obtains durable access history, cue association, downstream utility, context cost and actual infrastructure latency. The full-graph deterministic test adapter supplies feature values itself; for its seeded episode examples, access history and cue associations are empty. This is legitimate dependency injection, but the real feature source must not be attributed to the core mechanism implementation.

**F3-02 — Maintenance accounting is not an adaptive feedback path (Medium).** `MemoryMaintenanceAccountingService` records idempotent events and sums actual cost. The only `MemoryMaintenanceLedger` implementation in the repository is `InMemoryMemoryMaintenanceLedger`. No code path from the accounting service updates the B1 feature provider, activation estimate, or future maintenance profile. Estimated cost affects B1 selection; recorded actual cost does not currently teach that estimate.

**F3-03 — Feedback is enabled by capability, not by present data (High evidence limitation).** B1 accepts a bounded scope-keyed `CandidateOutcomeAdjustment`. `ActiveOutcomeFeedbackPolicy` is created only after holdout evaluation and separate authorization. FullGraphEvaluationHost injects the same active overlay into B1 and B3. But the R2 CLI deliberately produces a zero-data shadow policy and disables activation. The system must continue to preserve the baseline unless real independently verified data is supplied.

**F3-04 — Cross-stage identity is a host integration contract (Low/Medium).** The same feedback overlay keys adjustments by `(scopeId, candidateId)` for both B1 memory candidates and B3 workspace candidates. The full-graph integration fixture deliberately uses aligned IDs such as `episodic:candidate-a:1`; the type system does not enforce a universal typed identity mapping. Production adapters must preserve this canonical mapping or B1 will simply receive a zero adjustment for unmatched IDs.

### 3.4 B1 seven-field assessment

| Field | Current implementation | Assessment |
|---|---|---|
| S | M2 memory records plus host-provided feature sidecar | Present; feature truth is externalized |
| C | `B1RetrievalSelector` | Present |
| T | Evidence retrieval/context assembly | Present |
| P | Memory repositories; feature history and maintenance ledger not durably composed | Partial |
| F | Scope, active/verified status, expiry, finite bounds, capacity | Substantial local safeguards |
| L | Authorized downstream outcome overlay can change future ranking; no corpus presently exists; maintenance accounting is not learned | Partial / currently inactive |
| R | Candidate/context limits, latency and maintenance penalties | Present as configurable engineering policy |

**B1 disposition:** a substantive selection mechanism and integrated working-memory handoff, with a defined but currently inactive outcome-learning extension. Do not claim learned B1 superiority.

## 4. B2 — typed impasse, protected substate, typed return and compilation

### 4.1 Executed source path

The runtime lifecycle service:

1. Opens an impasse against the current working-memory scope/version and objective fingerprint.
2. Delegates the active B4 intention to the exact child impasse.
3. Executes a bounded list of child investigation steps using the registered child capabilities and step lifecycle.
4. Stores a typed `ChildResolutionResult` and intended intention return.
5. On return, re-loads the authoritative working-memory snapshot and verifies scope, request ID, current memory version and unchanged objective fingerprint.
6. Resolves the exact impasse and returns the delegated intention.
7. If the case is resolved and R2 is configured, obtains a host-built typed draft request and verifies that it is tied to the exact case version and parent snapshot.
8. Creates a typed `RecoveryProcedureDraft`, and carries its ID into parent graph state.

This path is directly exercised by `fullGraphResolvedB2ReturnCreatesTypedRecoveryDraftAndCarriesReference`.

### 4.2 Draft safety and persistence

`RecoveryProcedureDraftService.createDraft()` requires:

- exact expected impasse version;
- resolved case with a typed result;
- exact scope, request ID, parent working-memory version and objective fingerprint;
- valid case/draft time interval;
- non-empty findings evidence accepted by a host verifier; and
- action IDs that resolve to registered descriptors.

The draft contains opaque condition keys and action references with capability, effect and descriptor fingerprint. It deliberately does not parse narrative into action IDs, arguments or instructions, and does not execute actions.

Approval rechecks expected draft version, exact unchanged parent snapshot, evidence freshness, the current registered action descriptors, and the injected approval authority. PostgreSQL persists the draft and append-only version history with scope-qualified access and compare-and-set transitions. The PostgreSQL integration test verifies repository replacement, approval history, tenant isolation and stale-version rejection.

### 4.3 Missing B2 compile-and-reuse edge

**F3-05 — Approved recovery drafts never enter CoALA procedural memory (High paper-fidelity gap).** Repository-wide main-source search finds no call from the B2 draft lifecycle to `ProceduralMemoryService.stage`, `validate`, or `activate`. The approved draft remains a separately stored B2 artifact. The retrieval service can retrieve active procedural-memory records, but it cannot retrieve these B2 drafts as active procedures. The implementation therefore reaches typed return and governed draft/approval, but not the final reusable procedural learning transition proposed by B2.

This should not be “fixed” by auto-executing or automatically activating a model-generated procedure. The appropriate engineering extension is a controlled promotion adapter: approved typed draft → validation into a `ProcedureVersion` with provenance and artifact reference → separately governed activation → retrieval on a later matching case, with fresh checks of action descriptor/implementation identity and preconditions. That would retain the book's human-authority boundary while completing the operational learning edge.

**F3-06 — Approval is bound to the exact original parent version (Medium, conservative constraint).** Approval rejects if the parent working-memory version changes. This is safe and explicit, but it can make review brittle if unrelated working-memory updates occur while a draft is awaiting approval. Do not silently relax this. A future design could bind approval to a validated projection of relevant parent dependencies, but only if that is explicitly versioned and checked.

**F3-07 — Action implementation identity is represented by descriptor fingerprint (Low/Medium).** The recorded action reference fingerprints descriptor metadata, not the executable implementation artifact or version. This is enough to detect descriptor changes; it is not proof that the underlying action code is unchanged. If an approved draft is promoted for later use, promotion/reuse should bind the registered action implementation version as well.

### 4.4 B2 seven-field assessment

| Field | Current implementation | Assessment |
|---|---|---|
| S | Typed impasse case, parent snapshot, child result, draft | Strong |
| C | Impasse service, runtime child lifecycle, host approval authority | Strong but host authority remains adapter-supplied |
| T | Typed impasse signal; bounded child plan; verified child return | Strong |
| P | Parent/case records; draft PostgreSQL CAS/history | Strong for current artifact lifecycle |
| F | Typed unresolved/resolved dispositions, child-step failure/unknown-outcome, parent/version rejection | Strong |
| L | Typed draft and approval persisted; no promotion/retrieval as reusable procedural memory | Partial; defining compile/reuse edge missing |
| R | Child capability allow-list, maximum child actions/deadline, registered action checks | Strong application-specific governance |

**B2 disposition:** full-graph typed recovery and draft creation are implemented. The paper's final compilation-to-future-production-knowledge edge is not yet implemented. This is a real gap, but the draft-only state is also a deliberate safety boundary, not a defect in approval logic.

## 5. B3 — content competition, workspace and broadcast learning

### 5.1 Executed source path

`CognitiveCycleDecisionServices.prepareWorkspace()` requires a persisted B1 evidence-set reference and exact current B6 uncertainty reference, obtains a scope-bound request and immutable experiment-unit fingerprint, then invokes `WorkspaceAdmissionService`. It requires registered consumers, broadcasts the admitted snapshot, records receipts and returns failure/incomplete/admitted status to the graph.

The R2 evaluation composition configures `B3OutcomeEvidenceService` as the workspace receipt recorder. The service records accepted-consumption receipts and later accepts a downstream outcome only if the candidate was actually consumed by that exact receipt and the external evidence verifier approves it. Before trainer input, it re-loads the receipt and revalidates scope, workspace, cycle, consumer, candidate fingerprint, policy version, experimental-unit hash, verifier ID and proof.

The PostgreSQL repository stores receipts and outcomes transactionally, validates receipt linkage and idempotency conflicts, and the V011 schema defines append-only evidence storage. It intentionally does **not** persist the active workspace snapshot: the migration says active workspace remains ephemeral and cycle-bounded.

### 5.2 Actual admission and coalition semantics

`WorkspaceAdmissionService`:

1. admits mandatory candidates first, rejecting mandatory conflict/capacity/count overflow without a partial snapshot;
2. repeatedly scans remaining individual candidates;
3. chooses the highest current score with conflict and residual-capacity checks;
4. adds a coverage bonus for newly covered tags; and
5. stops when no positive-scoring candidate can be added or count/capacity is exhausted.

After this greedy item-level admission, it creates at most one `WorkspaceCoalition` from selected candidates that contribute newly covered tags. `WorkspaceSnapshot` checks the coalition's members, combined coverage, context units and aggregate score.

This is meaningful complementary-content handling. It does not make competing coalitions first-class candidates in the competition stage.

**F3-08 — B3 is greedy item admission with post-hoc coalition aggregation (Medium paper-fidelity gap).** The implementation does not enumerate or score alternative coalitions against one another. This should be described as bounded item-level competition with complementary coverage and a derived coalition record, not full LIDA coalition competition. There are two honest routes: implement explicit coalition candidates/competition with bounded formation, or narrow the book's migration claim. Do not implement a combinatorial optimizer unless the book's chosen experiment and bounded workspace capacity justify it.

### 5.3 Broadcast and persistence boundaries

`WorkspaceBroadcastService` is synchronous. It checks consumer identity and that consumed candidate IDs are inside the admitted snapshot; consumer failures become typed receipts. Receipt persistence failure throws rather than pretending success and surfaces a stable receipt ID for reconciliation.

The workspace snapshot repository has only `InMemoryWorkspaceRepository`. This aligns with the V011 design comment that the active B3 workspace is cycle-bounded/ephemeral. Receipts and independently verified downstream outcomes are durable audit evidence. The current service is not a durable outbox, asynchronous delivery bus or global workspace scheduler; it should not be described as one.

The default constructor still uses a no-op receipt recorder for compatibility. The R2 full evaluation host supplies `B3OutcomeEvidenceService` when an R2 configuration is present; absent R2 configuration, receipt evidence is not persisted by this mechanism.

### 5.4 B3 seven-field assessment

| Field | Current implementation | Assessment |
|---|---|---|
| S | Typed workspace candidates and cycle-bounded snapshot | Present |
| C | Mandatory-first greedy admission service | Present, but per-item rather than coalition-level |
| T | `prepareWorkspace` following B1 evidence and current uncertainty | Present |
| P | Active workspace ephemeral; receipts/outcomes append-only in PostgreSQL adapter | Deliberate split; adequate for cycle workspace + durable evidence |
| F | Mandatory overflow, conflicts, incomplete coverage, consumer rejection/failure, receipt-persistence error | Substantial |
| L | Verified outcomes can train and activate bounded score overlay; no real corpus/holdout currently | Designed, inactive |
| R | Capacity, max candidates, risk penalty, coverage bonus, mandatory-content policy | Present as application-specific governance |

**B3 disposition:** integrated bounded content admission/broadcast plus evidence-linked consumer outcomes. Not a complete LIDA implementation and no empirical evidence of adaptive benefit.

## 6. R2 outcome feedback and empirical boundary

The code separates four things that must not be collapsed:

1. **Receipt:** a consumer accepted/consumed candidate(s); not a positive outcome.
2. **Verified downstream outcome:** the external verifier validates evidence for a candidate consumed in the exact accepted receipt.
3. **Training candidate:** deterministic bounded adjustment from verified `USEFUL`/`HARMFUL` outcomes; always starts `SHADOW` and needs at least two distinct training experimental units.
4. **Activation:** paired holdout evaluation checks distinct scenario IDs, distinct experimental-unit fingerprints, unique baseline/candidate run fingerprints, no overlap with training units, and host-side verification; a separate approval must bind scope, policy version and exact evaluation fingerprint.

The actual evidence CLI source says explicitly:

- verified outcomes: **0**;
- independent holdout scenarios: **0**;
- candidate disposition: **SHADOW**;
- activation: **DISABLED**;
- baseline policy preserved;
- empirical advantage established: **false**.

The full-graph feedback integration test constructs two synthetic training outcomes and twelve synthetic paired holdout observations in memory, then verifies that the approved overlay changes real B1 and B3 selections in a recompiled graph. The test itself labels this as synthetic protocol/integration evidence and explicitly says it is not an empirical benchmark. It establishes that the wiring and gates can work, not that the candidate policy is better in real experiments.

## 7. Integration map

```text
M2 retrieval
  └─ B1 candidate factory + host-supplied features
       └─ B1 activation / latency / utility / maintenance / feedback selection
            └─ persisted evidence selection
                 └─ CoALA working-memory reference projection
                      └─ B3 bounded workspace admission
                           └─ synchronous workspace broadcast
                                └─ consumer receipt recorder
                                     └─ verified downstream outcome evidence
                                          └─ guarded training / independent holdout / approval
                                               └─ optional B1 + B3 score overlay

Planning and action cycle:
  typed impasse → bounded B2 child graph → typed resolution → parent validation
       └─ B4 delegated-intention return
            └─ typed recovery-procedure draft (DRAFT)
                 └─ separate approval (APPROVED)
                      └─ [MISSING] promotion into CoALA ProceduralMemoryService
                           └─ [MISSING] later retrieval/reuse in matching impasse
```

The R2 evaluation host injects the draft service/factory and B3 receipt/outcome service into the full graph. It also passes the same approved feedback overlay to B1 and B3. The full evaluation host uses in-memory working, episodic, semantic, procedural, impasse, workspace, intention, action-ledger and planning-artifact stores. PostgreSQL R2 adapters separately demonstrate durable drafts and evidence, but the evaluator's full graph does not thereby become an all-PostgreSQL production composition.

The application module exposes `CognitiveCycleApplicationGraph` builders that compose explicit decision/lifecycle services and enforce a shared durable B6 guard. Its `Phase0Application` remains a local intake smoke app. There is no complete domain-specific production composition root in this archive that provides every port/adapter and durable repository.

## 8. Prioritized findings

| ID | Priority | Finding | Recommended treatment |
|---|---|---|---|
| F3-05 | High paper-fidelity | Approved B2 drafts are not promoted into active/retrievable CoALA procedural memory | Implement governed promotion, or explicitly document B2 as draft-only and incomplete for compilation/reuse |
| F3-03 | High evidence limitation | R2 adaptive overlay has no real outcome/holdout corpus; remains SHADOW/DISABLED | Preserve baseline; do not activate from synthetic fixtures |
| F3-08 | Medium paper-fidelity | B3 coalition is post-hoc aggregation after greedy item selection | Narrow claim or implement bounded explicit coalition competition |
| F3-01 | Medium integration | B1 features are a trusted host port; no production provider in main source | Add a deterministic/domain feature adapter when the actual domain host is built |
| F3-02 | Medium integration | Maintenance ledger is in-memory and actual costs do not update future features | Decide whether maintenance feedback is within the implementation claim; otherwise document as accounting only |
| F3-04 | Low/Medium integration | Cross-B1/B3 candidate identity alignment is string/host convention | Define and validate a shared typed candidate identity when composing a non-test host |
| F3-06 | Medium conservative guard | B2 approval requires exact original parent memory version | Retain unless an explicit dependency-snapshot/version protocol is designed and tested |
| F3-07 | Low/Medium future reuse | Draft action fingerprint covers descriptor metadata, not executable implementation version | Add implementation identity if/when drafts are promoted to active procedures |

## 9. What should change—and what should not

Do **not** activate outcome learning or fabricate benchmark observations to complete B1/B3. The current fail-closed shadow-only design is the correct behavior while real evidence is absent.

Do **not** make B2 draft creation directly executable. The promotion path should enter the existing CoALA procedural lifecycle with provenance, validation and separate activation, not dispatch directly from a B2 approval.

For B3, a full coalition optimizer is not automatically needed. First decide what the book claims. A clear statement that the implementation uses bounded greedy item admission with derived complementary coalitions may be more faithful than adding a large, untested optimization scheme.

The most valuable next implementation candidate from Phase 3 is the B2 draft-to-procedural-memory promotion bridge, because it closes the missing `L` edge without removing human governance. B1/B3 learning should remain inactive until data exists. B3 coalition competition is a claim-depth decision rather than a prerequisite for the existing workflow.

## 10. Audit limitations

- The source archive was parsed and inspected, but complete Maven/CI was not rerun as part of this phase. The green-CI status is the user's report for this exact archive.
- Tests were inspected as source evidence; this audit did not re-execute them.
- No production ERP/WMS verifier is supplied; external truth is delegated through verifier ports.
- No verified real downstream outcome corpus or independent holdout is present according to the committed CLI evidence-status implementation.
- This phase covers B1/B2/B3 and the R2 feedback path; it does not complete the entire 739-type project audit or Phase 4/5/6.

## 11. Conclusion

B1, B2 and B3 are connected into the real cognitive-cycle evaluation graph. B1 retrieval feeds active CoALA working memory; B3 creates a bounded, consumed workspace and verifiable downstream evidence; B2 returns a typed resolution to an unchanged parent and creates a human-reviewable recovery draft. R2 provides a coherent, fail-closed pathway from verified consumer outcome to a candidate policy, independent holdout, host approval and future B1/B3 score overlay.

What is still missing is not basic integration. It is the final learning semantics that distinguish a record or draft from a future control change: B2 has no procedural-memory promotion/reuse; B1/B3 do not have real verified training/holdout data; and B3's coalition representation does not perform first-class coalition competition. These are the exact boundaries the book should teach.
