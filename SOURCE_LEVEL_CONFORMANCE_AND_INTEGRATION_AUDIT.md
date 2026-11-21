# Source-Level Conformance and Integration Audit — Phase 1

**Project:** Supply-Chain Disruption Management Agent  
**Audit baseline:** user-supplied `supply-chain-agent-main (6).zip`  
**Source ZIP SHA-256:** `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`  
**Audit date:** 2026-11-21  
**Scope:** CoALA (Part I), Fan & Lan (Part II), Java implementation structure, cross-module wiring, persistence boundaries and CI-visible static guards.  
**Change status:** Audit only. No project source files were modified.

## Executive result

The exact uploaded archive is a valid ZIP and was parsed as the audit baseline. The first audit pass confirms:

- The project has a strong and explicit implementation of CoALA's central organization: separate working and long-term memory models, internal and external action classes, and a full iterative decision graph.
- The full runtime graph integrates substantial portions of Fan & Lan B1, B2, B3, B4 and B6. R1/R2 work adds genuine cross-mechanism paths rather than merely adding service classes: selected memory references are projected into working memory; B2 returns can create typed recovery drafts; B3 consumer receipts and separately verified outcomes feed a guarded B1/B3 policy path; a shared durable B6 budget guard can be used across planning, reasoning and action dispatch.
- The system is not a complete implementation of either paper. In particular, **B4 method changes are not yet enforced as a planner-selection override**, **B6 `REDUCE_COMPUTATION` is not connected by a first-class typed directive to a smaller next planning request**, **B2 approved drafts are not promoted into reusable CoALA procedural memory**, and **B3 constructs descriptive coalitions after item-wise greedy admission rather than competing alternative coalitions**.
- Outcome-based B1/B3 adaptation is implemented as a guarded protocol, but the repository explicitly reports no verified outcome corpus and no independent holdout set. Candidate state remains shadow-only; activation is disabled and the baseline is preserved. No adaptive superiority claim is supportable.
- The full-graph integration test host is an in-memory evaluation composition. PostgreSQL adapters exist, but the application module does not yet provide a concrete production composition root that assembles all durable repositories, trusted host ports, B1/B3 R2 configuration, shared B6 guard and the full graph.
- The uploaded archive contains two obsolete duplicate Java files under repository-root `src/`; they are outside Maven's module source roots and are not checked by the official 11-module inventory guard. They are older than the active runtime-module copies.

**Overall judgement:** strong CoALA core realization; substantial but partial Fan & Lan mechanism realization; meaningful tested integration; several important mechanism-control edges still need explicit implementation or more careful qualification in the book. These are qualitative conformance judgments, not a numerical score and not Fan & Lan E/D codes.

## 1. Baseline identity and verification

### Exact archive and inventory

| Item | Verified value |
|---|---:|
| ZIP entries | 1,023 |
| Maven POMs | 12 (root + 11 modules) |
| Java source files parsed | 519 |
| Java AST type declarations | 739 |
| Top-level production types | 449 (448 in modules + 1 root orphan) |
| Top-level test types | 70 (69 in modules + 1 root orphan) |
| AST parse diagnostics | 0 |
| Java runtime used for source parsing | OpenJDK 21.0.12.1 |

The 739 AST declarations include nested classes/records/interfaces/enums. The full `CLASS_AUDIT_REGISTER.csv` lists every declaration and its source location, methods, fields, module responsibility and audit depth. The `JAVA_SOURCE_FILE_MANIFEST.csv` records each Java file's SHA-256.

### Static verification executed against this exact archive

Commands:

```bash
python scripts/verify-m8-b5-architecture.py
python scripts/verify-m8-r2-evidence.py
```

Both passed locally in this audit environment.

Architecture guard results:

- 11 Maven modules; acyclic internal dependency graph.
- Direct framework dependencies declared by the importing module.
- Framework-neutral module boundary passed.
- Source/test inventory: 448 production Java files, 69 module test sources, 407 test annotations/factories across 63 annotated test sources and 6 helpers.
- CI workflow mirror and M8-B2 artifact-capture checks passed.

R2 guard results:

- B2 typed draft and non-execution boundary passed.
- B3 receipt-linked verified outcomes and ephemeral workspace boundary passed.
- B1/B3 shadow, scope, immutable-unit holdout and paired-run guardrails passed.
- Full-graph B2 draft, B3 receipt/outcome and authorized B1/B3 policy integration checks passed.
- V011 repository and PostgreSQL integration-test presence checks passed.
- No-corpus evidence artifact and hosted evidence/security workflow checks passed.

**CI provenance qualification:** the user identifies this exact ZIP as the last green-CI source. This audit independently reran the source parser and two static repository guards, but did not rerun the full Maven/PostgreSQL GitHub Actions workflow. Therefore the full CI-green status remains the user's report about this archive, not a new CI execution by this audit.

## 2. Module-level architecture review

| Module | Main Java files | Test Java files | Responsibility | Paper relationship |
|---|---:|---:|---|---|
| `supply-chain-agent-domain` | 53 | 4 | Typed supply-chain entities, value objects, snapshots and invariants | Application domain; CoALA environment/world state |
| `supply-chain-agent-memory` | 52 | 7 | Working, episodic, semantic and procedural memory; retrieval; in-memory reference adapters | Direct CoALA memory correspondence |
| `supply-chain-agent-actions` | 58 | 5 | Internal, perception and grounding actions; typed dispatch, capability and idempotency contracts | CoALA action-space realization |
| `supply-chain-agent-mechanisms` | 123 | 9 | B1/B2/B3/B4/B6 stateful mechanism services and R2 feedback | Fan & Lan residual bundles, adapted to this domain |
| `supply-chain-agent-core` | 4 | 0 | Shared interpretation contracts | Application support |
| `supply-chain-agent-simulator` | 5 | 2 | Deterministic world transitions and effects | Environment for planning/evaluation |
| `supply-chain-agent-planning` | 51 | 8 | Planning APIs, CoT-style sequential, ToT search, RAP-inspired MCTS, selection and evaluation | CoALA planning procedure; not a paper-mandated algorithm set |
| `supply-chain-agent-runtime` | 37 | 11 | LangGraph4j cognitive cycle, lifecycle/decision service composition, B2 child graph, B6 budget guard | Integration of CoALA and B1/B2/B3/B4/B6 |
| `supply-chain-agent-infrastructure-postgres` | 40 | 15 | Durable memory/runtime/action/budget/evidence/checkpoint adapters and migrations | Persistence implementation, not prescribed by either paper |
| `supply-chain-agent-app` | 2 | 0 | Graph composition API and Phase 0 intake smoke app | Application entrypoints; full production host assembly is incomplete |
| `supply-chain-agent-evaluation` | 23 | 8 | Planner comparisons, ablations, R2 evidence and full-graph evaluation host | Experimental evidence and integration tests |

The root directory contains two additional Java files under `src/main/java` and `src/test/java`. They are not part of an 11-module POM. Their contents differ materially from the active runtime-module copies; see Finding F-06.

### Dependency structure

The 11-module Maven graph is acyclic. Framework-specific imports are isolated to runtime/application/infrastructure/evaluation edges rather than leaking into domain, memory, action and mechanism abstractions. This is a sound engineering choice that supports framework-neutral mechanisms and lets the book distinguish the papers' concepts from LangGraph4j/LangChain4j implementation decisions.

## 3. CoALA conformance assessment (Part I)

CoALA's organizing model is information storage, action space and decision-making procedure. It does not prescribe this exact Java package structure, a particular database or an agent framework.

| CoALA concept | Current code | Assessment | Key boundary |
|---|---|---|---|
| Working memory | `WorkingMemorySnapshot`, typed `MemoryValue`, hypotheses, `WorkingMemoryService`, repository ports and PostgreSQL adapter | **Strong direct core correspondence** | Separate versioned active context; not LangGraph state/checkpoint |
| Episodic memory | `Episode`, events/outcomes/provenance, `EpisodicMemoryService`, verification/rejection/supersession | **Strong direct core correspondence** | New outcomes are recorded unverified; only verified episodes are eligible for trusted episodic retrieval |
| Semantic memory | `SemanticFact`, temporal revisions, valid-time/knowledge-time queries, conservative admission | **Strong direct core correspondence** | Knowledge is a sourced representation, not the authoritative ERP/supplier system of record |
| Procedural memory | Immutable artifact references, digest, version chain and DRAFT/VALIDATED/ACTIVE/RETIRED lifecycle | **Strong adapted correspondence** | Governed explicit procedures; not model weights or self-modifying executable code |
| Internal retrieval | Exact scoped/time-aware cross-memory `MemoryRetrievalService`; followed by B1 candidate formation/selection | **Adapted realization** | Exact lookup and B1 selection remain separate stages |
| Internal reasoning | `ReasoningAction`, `ReasoningProvider`, LangChain4j adapter, evidence-bounded structured result | **Adapted realization** | Reasoning result is not itself a working-memory mutation or action grant |
| Internal learning | Episode recording and governed semantic/procedural write APIs | **Partial/adapted** | Persistence is present; automatic policy improvement is not implied by recording an episode |
| External actions | Read-only perception and typed grounding actions through `ActionDispatcher` | **Strong adapted realization** | Simulator-backed in the reference host; not live ERP/WMS/carrier calls |
| Decision procedure | `CognitiveCycleGraph`, decision/lifecycle services, M5 planner router, evaluator, observation and renewed cycle | **Strong direct core correspondence** | Proposal/evaluation/selection can iterate; execution is kept separate and downstream of authorization |
| Modularity | Framework-neutral modules and explicit ports/adapters | **Strong architectural correspondence** | Exact Java module count/framework choice is engineering, not CoALA text |

### CoALA interpretation

This is one of the strongest parts of the project. The four memories are separate data models and services, not labels placed over a single generic store. Working memory is versioned active context; episodes carry a verification lifecycle; semantic facts have separate valid/knowledge times; procedures are immutable versioned artifact references.

R1A adds a useful integration edge: B1-selected long-term memory references are projected into the active working-memory snapshot under a versioned/idempotent CAS contract. That strengthens the CoALA relationship between long-term memory and active working memory. The original source distinction remains important: graph checkpoints hold orchestration IDs/versions/routing, while the working-memory repository owns the active cognitive context.

The main qualification is learning. The code records outcome episodes and provides feedback services, but it does not autonomously promote an episode into trusted knowledge. This conservative boundary is compatible with an auditable reference system. It must not be described as automatic CoALA learning merely because a memory write occurred.

## 4. Fan & Lan conformance assessment (Part II)

For every mechanism below, the audit uses Fan & Lan's seven dimensions: **S** operative state substrate, **C** control locus, **T** transition trigger, **P** persistence boundary, **F** failure semantics, **L** learning operator and **R** resource/governance policy. These dimensions are analysed independently. E1–E4 evidence relationships and D0–D4 migration depth are not global software-quality grades; this Phase 1 report does not assign a single E/D code to the whole application.

### B1 — activation, retrieval latency, downstream utility and maintenance

**State (S):** M2 memory candidates with scope, lifecycle status and candidate features; access history/cues, expected downstream utility, context units, measured infrastructure latency and maintenance profile.  
**Control (C):** `B1RetrievalSelector` computes accessibility, predicted cognitive latency, infrastructure latency penalty, maintenance cost and net utility, then enforces count/context/accessibility thresholds.  
**Trigger (T):** after exact M2 retrieval during context assembly and before B3 workspace admission.  
**Persistence (P):** source memories are persisted through memory repositories; B1 candidate features are supplied by `B1CandidateFeatureProvider`; the visible maintenance ledger implementation is in-memory. R2 policy/evidence stores can be durable, but no durable general B1 access-history feature store is guaranteed by the B1 module.  
**Failure (F):** scope/lifecycle checks, finite-score checks, bounded adjustments and capacity limits are present.  
**Learning (L):** R2 adds an actual guarded evidence-attributed route: B3-minted verified downstream outcomes -> trainer -> shadow candidate -> independent holdout evaluation -> separate authorized activation -> immutable scope-bound overlay consumed by B1 and B3. The code path exists, but no real corpus or holdout exists; nothing is trained/activated in the accepted evidence state.  
**Resources (R):** candidate count, context units, predicted cognitive latency, infrastructure I/O latency and maintenance cost are explicit.

**Assessment: substantial structural/adapted implementation, but no demonstrated adaptive improvement.** The activation estimator is explicitly an engineering approximation, not a paper-prescribed equation. M8-B3's small designed fixtures previously found the current selector underperformed a simple top-k comparator on recall/precision. R2 closes the code path for outcome attribution and guarded policy activation, but the evidence CLI remains `NO_VERIFIED_OUTCOME_CORPUS`, zero verified outcomes, zero holdout scenarios, `SHADOW`, `DISABLED`, and `baselinePreserved=true`. This is honest fail-closed behavior, not an empirical win.

### B2 — typed impasse, protected substate, typed return and compilation

**State (S):** `ImpasseResolutionCase`, protected `ParentContextSnapshot`, child investigation steps/receipts, typed findings/result and R2 `RecoveryProcedureDraft`.  
**Control (C):** deterministic impasse detector and `ImpasseResolutionService`; bounded `B2ResolutionSubgraph`; parent lifecycle service validates return; `RecoveryProcedureDraftService` creates/reviews a typed proposal.  
**Trigger (T):** impasse signals during retrieval/workspace/planning, including `NO_FEASIBLE_CANDIDATE` in the current full graph. Child opens as a nested graph; it receives parent identities/versions/evidence/workspace/planner references rather than parent objective text, full memory contents or capabilities.  
**Persistence (P):** case/result/child receipts have PostgreSQL adapters; recovery drafts have separate V011 scope-bound persistence; graph state carries references. The full evaluation host defaults to in-memory stores unless R2 stores are supplied.  
**Failure (F):** capability allow-list/subset, action budget, deadline, parent scope/version/objective fingerprint checks, typed unresolved/terminal case behavior, bounded child recursion and parent re-evaluation are present. The same terminal B2 case is not reopened as an unbounded retry loop.  
**Learning (L):** resolved findings can create a typed draft whose actions are resolved against the registered `ActionRegistry`; evidence and action descriptors are fingerprinted. Approval changes review status only and does not dispatch. **The draft is not promoted to an active `ProcedureVersion` in CoALA procedural memory, and future B2 cases do not retrieve it as a reusable compiled rule.** The distinctive Soar-like future-use/chunking link remains unimplemented.  
**Resources (R):** bounded child actions, explicit capability restrictions, recursion limit and deadline.

**Assessment: strong typed recovery control path; partial realization of the complete B2 learning/compilation hypothesis.** R2 significantly advances this bundle beyond an isolated recovery service, but typed draft creation and approval are not equivalent to compiled reusable procedural knowledge.

### B3 — content competition, workspace and broadcast learning

**State (S):** typed workspace candidates, conflict/coverage/capacity attributes, immutable cycle-bounded snapshot, consumer receipts and separately verified downstream outcome records.  
**Control (C):** `WorkspaceAdmissionService` admits mandatory candidates first, then greedily selects optional individual candidates by base score plus marginal coverage and bounded outcome adjustment. `WorkspaceBroadcastService` calls consumers and checks receipt identity/consumption scope. R2 outcome evidence service verifies receipt-linked reports.  
**Trigger (T):** after B1 candidate selection, before planning. Broadcast follows successful admission.  
**Persistence (P):** admitted workspace snapshots are ephemeral/in-memory and cycle-scoped. R2 persists receipts and downstream outcomes in PostgreSQL; the workspace itself is intentionally not a durable global workspace.  
**Failure (F):** mandatory over-capacity fails without a partial snapshot; conflicts/required coverage/broadcast acceptance are explicit; unregistered/mismatched consumers fail. Verified evidence is rechecked before training.  
**Learning (L):** R2 supplies the outcome-attributed feedback protocol and B1/B3 adjustment overlay. It is opt-in and shadow-gated; no verified outcome data is available to activate it.  
**Resources (R):** context-unit capacity, maximum candidate count, required coverage and policy-bounded adjustment.

**Assessment: meaningful bounded workspace/admission/broadcast implementation, but partial LIDA-like correspondence.** The current service performs item-wise greedy admission. `WorkspaceCoalition` is formed after selection as a derived descriptive coalition over already admitted candidates; the code does not generate several competing coalitions and select a winning coalition before broadcast. This is an important difference if the book describes B3 as a LIDA-derived content-coalition competition mechanism. R2 closes receipts/outcomes/feedback plumbing but has no empirical outcome corpus and no active learned policy.

### B4 — persistent intentions, reconsideration and method authority

**State (S):** `RecoveryIntention` carries protected objective, hard constraints, objective fingerprint, current `RecoveryMethodReference`, lifecycle status and version.  
**Control (C):** `RecoveryIntentionService` and lifecycle service enforce continue/reconsider/change-method/suspend/delegate/return/terminate transitions; termination and human control pass through trusted authority interfaces.  
**Trigger (T):** host-supplied intention directives, typed delegation return, human control, objective satisfaction proof, or lifecycle triggers. The graph loops back to context loading after reconsideration/change-method.  
**Persistence (P):** PostgreSQL `RecoveryIntentionRepository` adapter exists; test-only full graph host currently constructs an in-memory repository.  
**Failure (F):** optimistic version, immutable objective fingerprint, explicit reconsideration before method replacement, bounded reconsideration count, authorized termination, verified objective completion.  
**Learning (L):** lifecycle records method reference updates; it does not by itself demonstrate outcome-trained future reconsideration policy.  
**Resources (R):** bounded reconsiderations and authorized human/host governance.

**Assessment: strong intention-lifecycle realization, but incomplete method-authority integration.** A concrete source trace shows `CognitiveCycleDecisionServices.proposeCandidates()` obtains a `PlanningRequest` from the host port and calls `plannerRouter.plan(request)`. `PlannerRouter.plan()` selects using `PlanningComplexity` and `PlannerBudget`. `RecoveryMethodReference` is not carried as a typed planner override in `CycleContext` or `PlanningRequest`; `planUsing(PlanningIdOverride, ...)` exists but has no call site in production source. A host adapter could implicitly alter complexity/budget in a method-sensitive way, but the current application contract does not enforce or test that. Existing B4 tests prove the intention method can change while preserving the objective, not that the next graph round invokes the selected replacement planner/method. This is a high-priority book-fidelity issue.

### B6 — uncertainty, resources, interruption and stopping

**State (S):** immutable `UncertaintyAssessment` explicitly records evidence, hypothesis and plan dimensions, execution/scope, provenance, expiry and separate calibration status; budget snapshot/reservations/actual usage. Graph carries assessment identity/version references.  
**Control (C):** `MetaControlGovernor` chooses CONTINUE, REQUEST_EVIDENCE, REDUCE_COMPUTATION, INTERRUPT_FOR_HUMAN or STOP. `ResourceBudgetExecutionGuard` uses a lease/fencing-capable ledger and is injected as the same object across M5 planning, M3 dispatch/reasoning and B6 lifecycle in the guarded production composition.  
**Trigger (T):** after retrieval/planning/human control and before authorization or continuation; resource reservations before guarded operations.  
**Persistence (P):** PostgreSQL leased budget ledger and runtime stores; exact identity composition checks; restart/forked-JVM tests for bounded persisted operations.  
**Failure (F):** no unbudgeted guarded reasoning action; budget/lease failures prevent invocation or yield conservative unknown-outcome handling; reservations are not blindly replayed after operation start.  
**Learning (L):** resource usage and uncertainty assessments are persisted, but uncertainty is `NOT_CALIBRATED`; no verified calibration data or outcome-trained resource/stopping policy is present.  
**Resources (R):** typed resource vector (search expansions, evaluations, model calls, rollouts, dispatches, token/other units) and deadline.

**Assessment: strong durable resource enforcement and explicit meta-control, but only partial uncertainty-conditioned computation adaptation.** The `REDUCE_COMPUTATION` outcome expresses an instruction to use a bounded lower-cost strategy, but the current graph routes directly back to `propose`. The decision-service code re-fetches the host `PlanningRequest` and invokes `plannerRouter.plan(request)`; the graph/context contract does not carry a typed “lower next-step budget / cheaper planner” directive. Existing tests prove the governor can emit REDUCE_COMPUTATION; they do not prove the subsequent planner changes or receives a smaller budget. A trusted host could implement an implicit policy in its own planning-request adapter, but this is not enforced by the repository's contract or a full-graph regression test. This is the second high-priority book-fidelity issue.

### B5 — skill governance

**Assessment: correctly not implemented.** The Fan & Lan review's closest-baseline/GraSP analysis closes B5 as a residual candidate in the bounded review corpus. Do not describe B5 as a missing sixth mechanism or add it simply for symmetry.

## 5. Full integration: observed path and boundaries

### Current cognitive-cycle path

The current full graph has the following control sequence (conditional branches and bounded retries omitted):

1. Initialize and interpret; obtain real host clarification on an interrupt rather than fabricating it.
2. Open/load versioned CoALA working memory and the active B4 intention.
3. Retrieve exact M2 memories; adapt to B1 candidates, select under context/latency/utility/maintenance policy, persist the evidence set, and project selected long-term-memory references into working memory with CAS/version checks.
4. Assess/refresh the scoped versioned B6 uncertainty assessment.
5. Admit B3 workspace content, broadcast to configured consumers, validate consumer receipts and persist them if the R2 recorder is configured.
6. Invoke M5 planner router; keep planner candidate artifacts outside the graph checkpoint; deterministically replay candidates against the simulator and hard constraints.
7. Route IMPASSE/NO_FEASIBLE_CANDIDATE to the bounded B2 child graph; validate parent state on return; create a typed recovery draft where configured; then reload/re-evaluate parent context.
8. Govern B4 intention and then B6 meta-control.
9. Prepare action authorization; interrupt for plan-bound human approval when required; revalidate the exact selected plan against fresh world state and approval immediately before dispatch.
10. Dispatch typed M3 commands through capability, idempotency, fencing and optional shared B6 budget boundaries.
11. Observe/reconcile success or unknown outcome; update working memory; record an unverified outcome episode; do not silently promote it to trusted memory.

### Key authority boundaries

| Concern | Authority | Not equivalent to |
|---|---|---|
| Active cognitive context | `WorkingMemoryService` + memory repository | LangGraph state/checkpoint |
| Orchestration/restart cursor | `CognitiveCycleState` + checkpoint saver | CoALA working memory |
| Planner selection | M5 `PlannerRouter` and selection policy | action authorization |
| Candidate correctness | common deterministic evaluator + simulator replay | evidence of external real-world truth |
| Intention/goal commitment | B4 `RecoveryIntentionService` | model goal string |
| Recovery substate | B2 case and bounded child graph | generic retry/checkpoint |
| Execution permission | trusted authority port + approval binding + `ActionPrincipal` capability | model/planner proposal |
| External modeled effect | M3 dispatcher and simulator world | live ERP/WMS/carrier effect |
| Computational budget | B6 resource ledger/guard | inventory, money or supplier-capacity governance |
| Verified outcome learning | B3 outcome-evidence verifier + separate training/holdout/activation | consumer receipt alone or an unverified episode |

### Persistence and composition boundary

PostgreSQL adapters and factories are present for M2 memory, M5 planning artifacts/sessions, runtime action/approval/observation/impasse/intention/budget state, B2 recovery drafts and B3 receipts/outcomes. The `PostgresAgentPersistence`/`PostgresRuntimePersistence` classes expose durable stores, and V011 adds R2 draft/receipt/outcome tables.

However, `CognitiveCycleApplicationGraph.compileFull()` composes service objects supplied by the host; it does not instantiate all of them from PostgreSQL or construct the trusted application adapters. `FullGraphEvaluationHost` is the current executable full-graph composition, and it defaults core repositories/artifact stores to in-memory adapters (with R2 stores/configuration optionally injected). `Phase0Application` is a small deterministic `SupplyChainIntakeGraph` smoke app, not the full cognitive graph. Source search found no production app class that constructs `PostgresRuntimePersistence`, the M2 Postgres adapters, B2/B3 R2 stores, trusted lifecycle/authority ports and then calls `compileFull` as a unified durable host.

This is an application integration/composition gap, not a contradiction of CoALA or Fan & Lan. For the book, label the current full graph as an integrated executable reference/evaluation host and present the Postgres composition as adapters/host wiring still to be assembled; do not describe the Phase 0 entrypoint as starting the full graph.

## 6. Planning and evaluation boundary

The M5 planners use a common `PlanningRequest` with authoritative world snapshot, working-memory snapshot, evidence references, action descriptors, hard constraints, objective, complexity and bounded `PlannerBudget`. The router has sequential, ToT-bounded-search and RAP-inspired MCTS strategies. The MCTS implementation is explicitly “RAP-inspired” rather than claimed to reproduce RAP. Candidate evaluation replays action paths through the deterministic simulator and checks hard constraints before a candidate can proceed to authorization.

Important source caveats:

- The evaluator deliberately rejects `PRESERVE_PROTECTED_CUSTOMER_COMMITMENTS` because the current domain model cannot verify it. This is fail-closed behavior and should remain explicit in chapters; do not claim all declared business constraints are currently verifiable.
- Aggregated action risk multiplies survival probabilities, which the source explicitly labels an engineering assumption of independence.
- The planner output remains a proposal; it contains no executable lambdas and is not an authorization grant.
- The M8-B2 experiment is bounded: 25 scenarios × 5 planners = 125 runs, 125/125 deterministic replay matches and zero hard-constraint violations were reported. Planners were identical on 24/25 scenarios; the single FAIL-10 tight-budget case differentiated feasibility at increased search cost. This does not establish general planner superiority.
- M8-B3 ablations are small designed fixtures. Results show mixed/negative B1 ranking and narrow coverage/control path findings, not population-level causal proof.

## 7. Prioritized findings and next actions

| ID | Priority | Finding | Why it matters | Recommended action / acceptance evidence |
|---|---|---|---|---|
| F-01 | High | B4 method reference is not an enforced planner-method override | The paper mechanism's method authority must affect the active reasoning method, not just persist a reference | Add a typed method-selection contract from current intention to planner routing; test CHANGE_METHOD -> next invocation uses exact replacement method while objective fingerprint stays fixed |
| F-02 | High | B6 REDUCE_COMPUTATION does not have a first-class downshift directive to M5 | Logging a control decision does not establish a control transition affecting subsequent computation | Add a typed, bounded planner directive or budget revision consumed by `PlanningRequest`; full-graph test proves planner/limits change and never exceed remaining shared budget |
| F-03 | Medium-high | B2 approved recovery drafts are not promoted/retrieved through CoALA procedural memory | Draft approval is not Soar-like chunking / reusable future production knowledge | Define a governed promotion bridge from approved `RecoveryProcedureDraft` to `ProcedureVersion`; test future matching B2 retrieval with conditions/evidence/action descriptor revalidation. Keep review and dispatch authority separate |
| F-04 | Medium-high | B3 coalitions are descriptive outputs after greedy item-level selection, not competing coalitions | Current source's LIDA-like correspondence is narrower than the full content-competition hypothesis | Either implement candidate coalition generation/competition/winner selection with bounded capacity and winner broadcast, or explicitly narrow the book claim to item-level admission plus derived complementary grouping |
| F-05 | Medium | B1/B3 feedback pathway is implemented but no empirical data activates it | Code-level integration cannot establish adaptive improvement | Keep baseline neutral; collect receipt-linked independently verified outcomes and disjoint holdout runs; activate only after preregistered criteria plus trusted approval |
| F-06 | Medium | Obsolete root-level duplicate lifecycle Java files are not built/guarded | Developers can inspect/edit stale code that omits R2 and current B6 semantics | Remove or archive root `src/` duplicates after repository-owner approval; add architecture guard rejecting Java files outside known module source roots |
| F-07 | Medium | No single production full-graph durable composition root | Adapter presence is not end-to-end durable application integration | Add a separate host/composition module or app factory that wires Postgres M2/M5/M6/B2/B3 stores, trusted domain/authority ports and one shared B6 guard; verify real full graph with PostgreSQL checkpoint/runtime across restart |
| F-08 | Low-medium | B1 feature/maintenance inputs remain host-supplied and partly in-memory | Persistence of memory itself does not guarantee persistence of activation signals/maintenance history | Specify and test durable provenance for access history, cue association, downstream value and maintenance profile if claiming persistent B1 activation state |

### Recommended implementation order

1. Resolve F-01 and F-02, because both are mechanism-authority/control-edge gaps affecting how the graph changes the next reasoning operation.
2. Decide whether the book needs full F-04 coalition semantics or a deliberately bounded B3 realization.
3. Implement F-03 only if Part III will claim B2-derived reusable procedural knowledge; otherwise document it as a safe typed draft boundary.
4. Keep F-05 dormant until the required evidence actually exists; do not manufacture synthetic production data.
5. Handle F-06 and F-07 as repository/composition improvements, not as claims demanded by the theoretical papers.

## 8. Audit register status — what is done and what remains

This is Phase 1 of the requested all-class audit, not a statement that every class has already had every method reviewed.

**Completed in this pass:**

- Exact archive identity, unpackability, Java AST parse of all 519 Java files with zero parser diagnostics.
- All 739 declarations inventoried, mapped to module/package role and given an explicit review-depth label.
- 11-module POM/dependency architecture and both source guard scripts checked.
- Targeted end-to-end source tracing of the key CoALA memory services, M3 dispatcher, B1/B2/B3/B4/B6 control services, planner request/router/evaluator, full runtime graph, R2 child workflow, PostgreSQL persistence roots, app graph entrypoint and full-graph evaluation host.
- Key tests and integration evidence matched to the code paths; absent end-to-end assertions are explicitly identified.
- Updated paper-to-code conclusions, including R1/R2 additions and unresolved authority/learning edges.

**Still required to complete the user's request to inspect every class:**

- Method-by-method review of the remaining domain records and all domain invariants/property edge cases.
- Every action input/output descriptor, perception/grounding handler and capability rule.
- Every B1/B2/B3/B4/B6 value object, state transition, repository contract and PostgreSQL adapter/SQL migration.
- Every ToT/MCTS/CoT planner class, session/checkpoint contract, tie-breaking/budget/cancellation semantics and trace output.
- Every runtime lifecycle helper and every evaluation/test class, including the exact assertions each test does and does not prove.
- A line-by-line migration/repository consistency review for V001–V011 and a separate check of the full CI artifact set (this pass only reran the architecture and R2 static guards).

The companion `CLASS_AUDIT_REGISTER.csv` deliberately distinguishes `TARGETED_SOURCE_REVIEW`, `MODULE_ARCHITECTURE_REVIEW`, `TEST_INVENTORY_ONLY` and `DUPLICATE_SOURCE_REVIEW`. Rows marked as module-architecture or inventory-only are not falsely represented as deep line-by-line reviews.

## 9. Suggested book treatment

### Part I / CoALA

The implementation is a strong worked example of the conceptual framework. Use the four memory models and the iterative proposal/evaluation/selection/execution cycle as the main teaching anchors. Explain that memory types, action categories and decision procedure are conceptual responsibilities, while Java modules, PostgreSQL, LangGraph4j and LangChain4j are this project's engineering choices.

### Part II / Fan & Lan

Use the seven-field analysis repeatedly. Do not call B1/B2/B3/B4/B6 complete simply because there is a package or graph node. Distinguish (a) representation, (b) authority to cause a transition, (c) persistence across the claimed boundary and (d) verified learning that changes future control. B2 draft approval and B3 receipts are state/evidence capabilities; they are not yet the complete future-policy-learning edge. B4/B6 require the two planner-control fixes in F-01/F-02 before claiming the mechanism controls the subsequent method/computation. Keep B5 closed.

### Part III / case-study implementation

Use this source snapshot as an executable reference baseline, but state that the current complete graph is instantiated by the evaluation host with in-memory stores and the app module has not yet assembled a production durable host. Keep the M8 experimental limits beside any code examples. Do not claim adaptive learning superiority, calibrated uncertainty or live supplier execution.

---

## Appendix A — key source locations reviewed

| Area | Exact source locations |
|---|---|
| CoALA memory | `supply-chain-agent-memory/.../working/WorkingMemoryService.java`; `.../episodic/EpisodicMemoryService.java`; `.../semantic/SemanticMemoryService.java`; `.../procedural/ProceduralMemoryService.java`; `.../retrieval/MemoryRetrievalService.java` |
| M3 action boundary | `supply-chain-agent-actions/.../api/ActionDispatcher.java`; `docs/architecture/COALA-ACTION-ARCHITECTURE.md` |
| B1 | `supply-chain-agent-mechanisms/.../b1/B1MemoryCandidateFactory.java`; `B1RetrievalSelector.java`; `RecencyFrequencyActivationEstimator.java` |
| B2 | `.../b2/ImpasseResolutionService.java`; `RecoveryProcedureDraftService.java`; runtime `B2ResolutionSubgraph.java`; `B2ResolutionGraphState.java` |
| B3/R2 | `.../b3/WorkspaceAdmissionService.java`; `WorkspaceBroadcastService.java`; `.../feedback/OutcomeFeedbackPolicyTrainer.java`; `FeedbackPolicyHoldoutEvaluator.java`; `FeedbackPolicyActivationService.java` |
| B4/B6 | `.../b4/RecoveryIntention.java`; `RecoveryIntentionService.java`; `RecoveryMethodReference.java`; `.../b6/MetaControlGovernor.java`; `UncertaintyAssessment.java` |
| Planning | `supply-chain-agent-planning/.../api/PlanningRequest.java`; `DeterministicCandidateEvaluator.java`; `SimulatorBackedPlanningWorldModel.java`; `routing/PlannerRouter.java`; `DefaultPlannerSelectionPolicy.java` |
| Runtime | `supply-chain-agent-runtime/.../CognitiveCycleGraph.java`; `CognitiveCycleOperations.java`; `decision/CognitiveCycleDecisionServices.java`; `lifecycle/CognitiveCycleLifecycleServices.java`; `ResourceBudgetExecutionGuard.java`; `ResourceBudgetedActionExecution.java` |
| Composition | `supply-chain-agent-app/.../cognitive/CognitiveCycleApplicationGraph.java`; `Phase0Application.java`; `supply-chain-agent-evaluation/.../host/FullGraphEvaluationHost.java`; `R2WorkflowConfiguration.java` |
| PostgreSQL | `supply-chain-agent-infrastructure-postgres/.../PostgresAgentPersistence.java`; `runtime/PostgresRuntimePersistence.java`; `runtime/PostgresRuntimeCodecs.java`; migrations V001–V011 |
| Project docs | `docs/architecture/COALA-MEMORY-ARCHITECTURE.md`; `COALA-ACTION-ARCHITECTURE.md`; `FAN_LAN-MECHANISM-ARCHITECTURE.md`; `COGNITIVE-CYCLE-DECISION-SERVICES.md`; `COGNITIVE-CYCLE-LIFECYCLE-SERVICES.md`; `docs/pre-b7/M8-PREB7-R2-OUTCOME-FEEDBACK-AND-FINAL-EVIDENCE.md`; `M8-PREB7-R2-EXPERIMENT-PROTOCOL.md` |

## Appendix B — audit labels

- **Strong/direct core correspondence:** central concept has corresponding state, service/control path and relevant tests.
- **Adapted realization:** function is represented with explicit application-specific state and control, but not as a literal paper algorithm.
- **Partial realization:** a key transition, persistence, failure or learning coupling remains absent or unverified.
- **Not implemented / closed:** no implementation claimed; B5 is the closed case in the reviewed Fan & Lan corpus.
- **Targeted source review:** exact implementation file was opened and inspected for this pass.
- **Module architecture review:** type is inventoried and package/module role checked; its complete method body has not yet been reviewed line by line.
- **Test inventory only:** test type inventoried; each assertion not yet individually audited.
