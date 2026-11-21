# Final Source-Level Conformance and Integration Audit

**Project:** Supply-Chain Disruption Management Agent  
**Audit baseline:** User-uploaded `supply-chain-agent-main (6).zip`  
**ZIP SHA-256:** `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`  
**Audit date:** 21 November 2026  
**Scope:** CoALA core architecture; Fan & Lan B1–B6; full cognitive graph and cross-mechanism integration; internal/external actions; runtime authorization and execution; PostgreSQL schema/repositories; checkpoint and restart boundaries; tests and experimental evidence.  
**Change class:** Audit only. No project source, tests, dependencies, migrations or CI workflows were changed.

## 1. Executive outcome

The source-level conformance and integration audit is complete across six phases. The uploaded ZIP was checksum-verified and parsed; the repository contains 11 Maven modules. The source inventory covers 519 Java source files and 739 Java type declarations (including nested and test declarations). The user reports this exact source archive is green in GitHub Actions. The audit did not rerun Maven or PostgreSQL tests.

### Overall verdict

1. **CoALA:** strong core architectural correspondence. The repository has genuinely distinct working, episodic, semantic and procedural memory responsibilities; explicit internal and external action families; and a composed, iterative cognitive decision/execution loop. Its Java module structure and detailed memory lifecycles are implementation choices, not class layouts dictated by CoALA.
2. **Fan & Lan:** substantial implementation of selected B1, B2, B3, B4 and B6 control ideas, but none should be described as a complete reproduction of its full residual hypothesis. The operational control and persistence layers are real; specific learning/control couplings remain incomplete.
3. **Integration:** the full evaluation graph genuinely wires B1/B3 and B2/R2 paths, B4/B6 lifecycle controls, planning and M3 execution. It is not merely a directory of disconnected mechanism services. The full evaluation host nevertheless uses in-memory adapters for some stores; the source does not supply a single fully assembled durable/authorized production host.
4. **Execution authority:** a strong engineering boundary separates planning from approval, revalidation, dispatch and reconciliation. The actual environment is simulator-backed and trusted IAM/provider adapters remain outside the repository.
5. **Durability:** PostgreSQL implementations cover most important memory and runtime state, and restart tests exist through pool replacement and forked JVMs. Important scope-binding gaps remain in the planner-session store and ID-only memory service APIs; B3 workspace snapshots have no PostgreSQL repository.
6. **Evidence:** CI green is accepted as the user's report. The source/test evidence is substantial, but current R2 evidence does not establish an adaptive-performance improvement. The outcome corpus and independent holdout set are empty; feedback remains SHADOW and activation DISABLED.

**The project is a strong educational reference implementation and a defensible Java operationalization of the two papers' ideas. It is not a complete implementation of either paper, it is not evidence that every proposed mechanism has been validated, and it should not be described as a deployed production supply-chain system.**

## 2. Baseline and audit method

### 2.1 Immutable source baseline

- Archive: `supply-chain-agent-main (6).zip`
- SHA-256: `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`
- Archive integrity: `unzip -t` passed.
- Maven modules: 11.
- Java source files parsed: 519.
- Java type declarations inventoried: 739.
- Main-source Java files in extracted module roots: 449.

The inventory includes nested records/classes/interfaces/enums, test types and test nesting. Counts of Java declarations are not counts of independently deployed classes.

### 2.2 Review method

For CoALA, the audit mapped memory, action space, decision procedure and module boundaries to concrete Java classes, service contracts, repositories, graph nodes and test sources.

For Fan & Lan, the audit used the seven fields:

\[
\mathcal K=\langle S,C,T,P,F,L,R\rangle
\]

where S is state substrate, C control locus, T transition trigger, P persistence boundary, F failure semantics, L learning operator and R resource/governance policy. These dimensions were checked against source state and call paths, not inferred from component names.

For integration, the audit traced selected paths end-to-end: actual callers, dependency injection, returned decisions, persistence adapters, parent/child state boundaries, planner invocations, action dispatch and observation handling.

For tests, the audit inspected test source and test assertions. It did not execute the full Maven build, PostgreSQL, a real provider or a deployed host. The reported green CI status belongs to the user's confirmation for this archive.

### 2.3 Review-depth disclosure

The 739-type inventory is complete as an inventory and contains review-depth classifications. Critical production paths were targeted in Phases 2–5, including detailed source reviews of B4/B6, B1/B2/B3, execution authority, migration/persistence and restart adapters. Many of the remaining declarations received module/architecture-level classification or test-inventory review rather than exhaustive method-by-method inspection.

Therefore, this is a complete **architectural, mechanism-conformance and critical-integration audit**, not a claim that every method in all 739 declarations has been line-reviewed. The class register makes the review depth explicit so later work can target any unreviewed implementation file without repeating the baseline inventory.

## 3. CoALA conformance

| CoALA concept | Code correspondence | Verdict | Important boundary |
|---|---|---|---|
| Three-dimensional agent organization | Memory, actions, planning, mechanisms and runtime modules | Strong core correspondence | The module count and framework choices are engineering decisions. |
| Working memory | Typed `WorkingMemorySnapshot`, versioned entries/hypotheses, service/repository and PostgreSQL CAS | Strong | Graph checkpoints are not working memory. |
| Episodic memory | Episode/event/outcome/provenance/review lifecycles and scoped retrieval | Strong | Recording an episode is not automatic policy learning. |
| Semantic memory | Typed fact values, provenance, valid time/knowledge time, proposed/active/superseded/rejected status | Strong | Model-derived facts do not become trusted facts merely by being written. |
| Procedural memory | Immutable procedure versions, artifact digest and draft/validated/active/retired/rejected lifecycle | Strong adapted realization | Only explicit artifacts are governed; this does not persist model weights or auto-edit code. |
| Internal retrieval/reasoning/learning | Explicit typed actions and services, separated writes and evidence references | Strong adapted realization | The exact memory handoff is project-specific; it is not a verbatim implementation of every CoALA example. |
| External grounding/perception | Typed action descriptors/handlers, action registry and dispatcher | Strong adapted realization | Grounding currently targets a simulator, not live ERP/WMS/carrier systems. |
| Decision-making procedure | Full cognitive graph with proposal/evaluation/selection, execution and renewed observation cycle | Strong core correspondence | CoALA does not mandate this exact graph sequence; planning sub-processes are iterative/interleaved. |
| Modularity | Framework-neutral domain/memory/actions/mechanisms/planning ports and runtime adapters | Strong | No claim that the exact architecture is prescribed by CoALA. |

The strongest CoALA teaching example is the treatment of the four memory responsibilities as different kinds of state with different trust, lifecycle and retrieval rules. The other particularly strong example is the separation of planning proposals from the authority to execute external actions.

## 4. Fan & Lan mechanism conformance

### B1 — Activation, latency, downstream utility and maintenance

**Present:** typed candidates, activation estimation, latency and maintenance/cognitive-cost features, B1 selector, M2 retrieval integration, evidence-set persistence and R1A working-memory projection. R2 introduces an authorized, bounded feedback overlay into the full evaluation path.

**Incomplete:** source-level feature history is partly caller/host supplied; maintenance events are not a complete durable feature-training loop; the current selector's outcomes do not yet train future activation weights/ranking. The R2 evidence gate has zero verified outcome records and zero independent holdout scenarios. Feedback is SHADOW and activation disabled. Existing small ablation fixtures did not show selector superiority over simpler top-k retrieval.

**Verdict:** partial realization. Selection is real and integrated; adaptive learning is not established.

### B2 — Typed impasse, protected substate, typed return and resolution compilation

**Present:** deterministic impasse detection, typed impasse cases, parent context snapshots, bounded child investigation, child step receipts/leases, full-graph routing (including the `NO_FEASIBLE_CANDIDATE` case after Fix 3), parent-state/objective validation, typed result return and typed recovery procedure drafts. Draft approval checks scope, evidence, registered action descriptors and current parent identity/version. PostgreSQL draft state and append-only transition history are present.

**Incomplete:** approved recovery drafts are not promoted into CoALA `ProceduralMemoryService`, and the normal retrieval path cannot reuse them as active recovery procedures in later cases. Consequently the final compilation/reuse/learning edge remains absent.

**Verdict:** strong adapted recovery substate and typed-return realization, partial full B2 hypothesis.

### B3 — Content competition, workspace, broadcast and admission learning

**Present:** typed candidates, mandatory-content protection, capacity and conflict constraints, bounded greedy marginal admission, complementary coverage accounting, workspace snapshot, broadcast, consumer receipts, and R2 verified outcomes bound to accepted receipts and exact candidate fingerprints.

**Incomplete:** the service greedily admits individual candidates and only then aggregates selected members into a derived coalition. It does not compare alternative coalitions as first-class competitors. Consumer outcomes do not yet train future admission policy because the evidence gate remains closed. The workspace repository has only an in-memory implementation; receipts and outcomes are durable in PostgreSQL, but the admitted workspace snapshot itself is not.

**Verdict:** substantial partial B3 realization. Do not call it a complete LIDA/global-workspace implementation.

### B4 — Persistent intention, reconsideration and method authority

**Present:** durable intention records, protected objective fingerprint and constraints, method/reasoning change records, reconsideration triggers, authority decisions, status lifecycle, history and PostgreSQL compare-and-set. R1DEF and the action lifecycle protect the objective/approval relationship.

**Incomplete — F-01:** a method-change decision is not guaranteed to control the next planner invocation. The normal decision path still reaches `PlannerRouter` without a required binding from the active intention's selected method to the planner/method actually invoked. The objective can remain unchanged while the replacement planning method is not enforced.

**Verdict:** strong adapted commitment/reconsideration lifecycle, incomplete end-to-end method authority.

### B5 — Skill-governance candidate

The repository does not implement B5/GraSP. This is correct for the selected scope: Fan & Lan's closest-baseline analysis closes the B5 candidate through GraSP. It should not be counted as a missing residual bundle or implemented merely to make the feature list look complete.

### B6 — Uncertainty, resource allocation, interruption and stopping

**Present:** uncertainty assessment references and versioned propagation, meta-control decisions, shared resource-budget enforcement, hard resource ceilings, durable reservation/usage accounting, lease owners, fencing tokens, conservative started-operation recovery and stopping/interruption outcomes. M5, M3 and B6 are required to share the same exact `ResourceBudgetExecutionGuard` instance in the guarded full graph composition.

**Incomplete — F-02:** `REDUCE_COMPUTATION` returns to planning, but the next planner request is not required to consume a typed downshift directive that reduces the planner-specific resource budget or selects a lower-cost method. The hard execution budget remains enforced, but that is not the same as changing the next planning computation. Uncertainty is explicitly `NOT_CALIBRATED`, so no calibrated uncertainty-driven performance claim is justified. Learning does not yet train a future allocation/stopping policy.

**Verdict:** strong adapted resource enforcement and explicit meta-control, partial B6 coupling across uncertainty and future planning effort.

## 5. Full cognitive integration

The application has a full cognitive-cycle graph and a concrete service-composition seam. The key sequence is:

1. Interpret and prepare authoritative request/context.
2. Retrieve CoALA memory and build typed B1 candidate features.
3. Apply B1 selection and B3 bounded workspace admission/broadcast.
4. Build the planning request and invoke one of the M5 planning strategies.
5. Evaluate candidates and maintain persistent B4 intention/goal state.
6. Route typed impasses to the bounded B2 recovery child graph; validate returned evidence against parent context; create governed drafts where appropriate.
7. Apply B6 uncertainty/resource meta-control and shared budget accounting.
8. Convert a selected proposal into typed M3 commands; verify registered descriptors, action-plan fingerprint and capability scope.
9. Obtain host-issued approval for state-changing actions; resume, revalidate the exact plan and current approval, then dispatch with idempotency and fenced ledger semantics.
10. Persist action results; stop blind dispatch on partial/unknown outcomes; reconcile through a trusted domain host; record versioned observation/working-memory updates and unverified outcome episodes.
11. Record accepted workspace consumer receipts and separately verified downstream outcomes; R2 feedback remains guarded until evidence and holdout gates pass.

The strongest evidence of integration is that B2, B3 and R2 are wired into the actual full evaluation path and that the action authority/reconciliation lifecycle is composed around the planner. The main integration gaps are where a decision is recorded but does not control the next stage (B4/F-01 and B6/F-02), and where durable subsystem stores are not all used by one full host (B3 workspace and evaluation-host adapters).

## 6. Action, execution and authority boundary

The M3 action layer is one of the strongest engineering aspects of the system:

- planners produce proposals, not execution permissions;
- state-changing candidates map to registered typed `GROUNDING`/`DOMAIN_WRITE` descriptors;
- the authorized plan fingerprint binds candidate set, selected candidate, intention identity/version and command fingerprints;
- state-changing plans require host-issued approval; no model-generated `NO_APPROVAL_REQUIRED` can bypass this;
- approval is rechecked against the exact binding and immediately before dispatch;
- the dispatcher independently checks action types, capability/resource scope, preconditions, idempotency and fencing;
- multi-step batches persist ordered results; and
- `UNKNOWN_OUTCOME` is reconciled rather than blindly redispatched.

Boundaries: the repository does not supply a concrete production `CognitiveCycleAuthorityPort` that authenticates the human approver and issues the trusted principal; the supply-chain world is simulator-backed; reconciliation is a host-supplied port; and database fencing cannot stop an external provider side effect already in flight. Live providers need their own idempotency keys and authoritative status queries.

## 7. Persistence and restart conclusions

### Strengths

- V001–V011 are ordered and checksummed; migration execution is advisory-lock serialized and per-script transactional.
- Working memory uses row-lock/CAS and transactional child replacement.
- Episode persistence has scoped idempotency and verified retrieval; semantic facts preserve valid/knowledge time; procedures are immutable versioned artifacts with controlled lifecycle.
- Runtime stores use scope-qualified keys for action operations, batches, approvals, intentions, B2 artifacts, B3 evidence and B6 accounting.
- B6 budget mutations lock the authoritative execution row; expired unstarted leases can be reclaimed with a new fence; started expired leases are conservatively charged and not reopened.
- LangGraph4j PostgreSQL checkpointing uses a stable scope/execution-derived thread ID and shares the host's data source.
- Test sources cover pool replacement, graph interruption/resume, a full test workflow with unknown external outcome and an independent forked JVM restart.

### Findings

**P5-01 — Planner sessions are not scope-qualified at the persistence API/schema.** `planner_session` is keyed only by `session_id`; `PlannerSessionStore` accepts only a session ID; `PostgresPlannerSessionStore` is not constructed with a runtime scope. Random UUIDs and the request fingerprint (which includes working-memory scope) prevent a mismatched resume from being accepted through the normal planner path, but the durable store is weaker than the scope-qualified runtime tables. Recommend scope-bound `find/save` and a composite-key migration before claiming database-level tenant isolation.

**P5-02 — Memory service lifecycle operations are ID-only.** Several `WorkingMemoryService`, `EpisodicMemoryService`, `SemanticMemoryService` and `ProceduralMemoryService` reads/updates do not accept the caller's expected scope. The persisted records carry scope, collection queries use scope, and write adapters preserve the existing scope, but a caller who knows another record's ID can invoke the ID-based service methods without presenting an expected scope. This is not evidence of a currently exposed remote exploit—the uploaded repository has no production IAM/API host—but it is not an authorization boundary. Recommend scope-aware service operations or an explicit trusted internal-only API boundary.

**P5-03 — B3 workspace snapshot has no durable PostgreSQL repository.** Receipts/outcomes are durable, but the exact admitted workspace state is in-memory in the evaluation host. Either add persistence if restartable workspace semantics are part of the case study or accurately limit the claim.

**P5-04 — Complete durable host composition is not delivered.** `PostgresAgentPersistence`, `PostgresRuntimePersistence` and `CognitiveCycleApplicationGraph` provide useful composition components/seams. They do not together constitute a ready host factory that instantiates all service objects and trusted authority/domain adapters. The evaluation host intentionally uses some in-memory stores; `Phase0Application` is a separate smoke/intake app using `MemorySaver`.

**P5-05 — Checkpoint tenant isolation relies on thread naming.** The LangGraph4j saver factory derives thread ID from `scopeId + executionId`, but its saver database rows do not add the application's explicit scope column or row-level security. This is acceptable as a controlled application namespace in the reference test, but not a claim of database-enforced tenant isolation.

## 8. Ranked findings and next implementation sequence

| ID | Priority | Finding | Why it matters | Suggested action |
|---|---|---|---|---|
| F-01 | High, paper/control fidelity | B4 method change does not compel the next planner/method invocation | The mechanism may record a switch without exercising method authority | Add a typed intention method override to the next planning request/router; validate method is allowed; prove chosen planner invocation; preserve objective fingerprint |
| F-02 | High, paper/control fidelity | B6 `REDUCE_COMPUTATION` does not compel an actual next-request downshift | Meta-control decision lacks a concrete effect on future computation | Add a typed bounded downshift directive that changes `PlannerBudget` and/or validated planner route while retaining execution-wide hard ceiling |
| F-03 | Medium-high, learning completeness | Approved B2 drafts do not enter active CoALA procedural memory | No later-cycle reuse/compilation edge | Governed draft-to-procedure stage/validate/activate flow; retrieval by matching failure conditions; provenance and revocation tests |
| P5-01 | Medium-high, tenant boundary | Planner-session persistence lacks explicit scope | Weaker storage boundary than other runtime state | Scope-bound PlannerSessionStore API and schema migration |
| P5-02 | Medium-high, tenant boundary | Memory service load/mutate APIs omit expected caller scope | Record identity alone is not a tenant authorization contract | Scope-aware service method contracts and negative cross-scope tests |
| F-04 | Medium, B3 fidelity | Greedy item admission and post-selection coalition do not equal competing coalitions | Avoid overclaiming LIDA-like competition | Either narrow the claim or implement first-class alternative coalition candidates and competition |
| P5-03 | Medium, restart scope | B3 workspace snapshots are in-memory | Workspace cannot be restored as durable state | Add a PG WorkspaceRepository if needed, otherwise label ephemeral lifetime |
| F4-03 | Medium, reconciliation assurance | Reconciliation observation is not a typed per-operation proof | Host adapter owns the proof that each batch effect was reconciled | Add a typed reconciliation proof binding batch, plan, operations, provider references, source version and verifier |
| P5-04 | Medium, integration packaging | No single complete durable host assembly | Repositories and graph composition seams exist, but are manually assembled | Create a reference composition module/example if the book needs a complete runnable end-to-end host |
| F4-04 | Medium, external effects | DB fence does not fence live provider operation | A late provider completion can remain externally applied after lease expiry | Require provider idempotency by operation ID and authoritative reconciliation |
| B1-01 | Evidence-gated | No verified outcomes/holdout; selector has no proven adaptive lift | Learning/activation claims unsupported | Keep SHADOW/DISABLED; collect verified independent data before activation |
| B6-01 | Evidence-gated | Uncertainty NOT_CALIBRATED and no adaptive allocation training | No calibrated uncertainty/performance claims | Keep confidence conservative and measure target-specific calibration before policy adaptation |

### Recommended code sequence

1. Fix F-01 and F-02 first. These have the greatest direct impact on the claimed B4/B6 control semantics and can be tested in the full graph without new infrastructure.
2. Close P5-01 and P5-02 if scope/tenant correctness is part of the teaching case. They are concrete persistence/API invariants, not generic hardening.
3. Implement F-03 only with explicit review/activation gates; do not make model-generated recovery drafts executable automatically.
4. Decide whether B3 coalition competition and durable workspace are essential to the chapter's claimed migration depth. Otherwise narrow the description.
5. Keep B1/R2 activation disabled until verified outcome and independent holdout evidence exists. Do not manufacture a positive experiment to make the mechanism appear complete.
6. Treat host IAM, live ERP/WMS/carrier adapters and provider reconciliation as separate integration examples or deployment work, not as mandatory prerequisites to writing the theoretical chapters.

## 9. Implications for the book

### Claims that are supported

- CoALA's memory/action/decision organization is implemented as explicit Java responsibilities with separate lifecycles.
- The application uses an iterative cognitive cycle; planning, execution and outcome observation are distinct.
- Fan & Lan's seven-field analysis is applied meaningfully to B1/B2/B3/B4/B6, with tangible authoritative state and control paths.
- B2 typed recovery and B3 receipts/outcome evidence are integrated into the full evaluation graph.
- B4 intention state and B6 budgets/leases survive the tested persistence/restart boundaries.
- M3 approval, dispatch and unknown-outcome reconciliation are independent of model proposals.

### Claims that must remain qualified

- Do not say “complete CoALA implementation” or “complete Fan & Lan implementation”.
- Do not say B1 has learned a superior retrieval policy or that R2 has demonstrated adaptive improvement.
- Do not say approved B2 drafts are compiled/reused as future procedural knowledge.
- Do not say B3 implements full LIDA-style coalition competition or durable global workspace.
- Do not say B4 method reconsideration necessarily selects a new next-cycle planner.
- Do not say B6 reduction decisions already downshift the next planner request or uncertainty is calibrated.
- Do not say PostgreSQL persistence proves the full evaluation graph is a fully durable deployed host.
- Do not say the simulator demonstrates live ERP/WMS or provider integration.
- Do not use global E/D scores as software-quality grades. E1–E4 and D0–D4 belong to specific historical mechanism mappings and should be assigned only with paper-specific evidence.

## 10. Final disposition

The source-level conformance and integration audit is complete. The remaining items are implementation opportunities and explicit claim boundaries, not unfinished audit phases. No project code was changed in this audit.

**Recommended next development increment:** an integrated F-01/F-02 control-authority increment with full-graph tests proving (a) an intention method switch changes the planner invoked while preserving the same objective, and (b) B6 `REDUCE_COMPUTATION` changes the subsequent planning budget/route while the immutable execution-wide budget remains authoritative. Then decide whether to close P5-01/P5-02 before freezing the reference implementation used in Part III.
