# Paper-to-Code Conformance Matrix

**Project:** Supply-Chain Disruption Management Agent  
**Source baseline:** `SUPPLY_CHAIN_AGENT_M8_B6_FROZEN_SOURCE_2026-11-20.zip`  
**Assessment date:** 2026-11-20 (R0 clarified 2026-11-20)  
**Scope:** CoALA and selected Fan & Lan mechanism-level concepts  
**Change class:** Documentation only; no executable source, tests, dependencies, migrations or CI workflows changed.

## 1. Executive assessment

This repository is a substantial, explicit engineering operationalization of the **core CoALA organization**: separate working and long-term memory responsibilities, internal and external actions, and an iterative decision procedure that plans, selects, executes and incorporates feedback. It does not reproduce every technique mentioned in CoALA, and it intentionally imposes stricter evidence, authority and lifecycle boundaries than a minimal demonstration agent.

The repository also implements **selected, adapted mechanisms corresponding to Fan & Lan's residual B1, B2, B3, B4 and B6 bundles**. These are explicit stateful controls, not merely names or graph nodes. They are not complete reproductions of the full residual hypotheses: B1 lacks outcome-updated selection; B2 lacks safe reusable resolution compilation; B3 lacks outcome-dependent admission learning; and B6 lacks demonstrated calibrated uncertainty propagation across the production lifecycle. B4 is assessed against its actual residual definition—persistent intention, explicit reconsideration and live method-switch authority—not against an additional learned future method-selection policy. B5 is a closed negative case in Fan & Lan's review because GraSP is the closest baseline; this repository does not implement B5 or GraSP.

The strongest defensible conclusion is therefore:

> The system implements CoALA's central memory/action/decision-procedure architecture and realizes selected Fan & Lan mechanism-level control ideas as bounded, testable Java components. It is a serious, evidence-backed reference implementation, not a complete implementation of either paper, not proof of zero defects, and not a production certification.

### Assessment labels

| Label | Meaning |
|---|---|
| **Direct core correspondence** | The code has an explicit structure and lifecycle corresponding to a central concept in the paper. This is not a claim of verbatim algorithmic reproduction. |
| **Adapted realization** | The software solves a related control problem with explicit application-specific state, transitions and limits. Important paper semantics may differ. |
| **Partial realization** | Some necessary state/control edges exist, but one or more defining couplings, persistence properties or learning behaviors remain absent. |
| **Not implemented / closed case** | No corresponding local implementation is claimed; the paper's own closest-baseline review may close the candidate. |

These labels are this project's traceability vocabulary. They are **not** Fan & Lan E1–E4 evidence-relation codes or D0–D4 migration-depth codes.

## 2. Method and evidence discipline

### 2.1 Source baseline inspected

The matrix was prepared by inspecting the M8-B6 frozen source archive, including the following implementation and traceability documents:

- `docs/architecture/COALA-MEMORY-ARCHITECTURE.md`
- `docs/architecture/COALA-ACTION-ARCHITECTURE.md`
- `docs/architecture/FAN_LAN-MECHANISM-ARCHITECTURE.md`
- `docs/architecture/PLANNING-ARCHITECTURE.md`
- `docs/architecture/COGNITIVE-CYCLE-DECISION-SERVICES.md`
- `docs/architecture/COGNITIVE-CYCLE-LIFECYCLE-SERVICES.md`
- `docs/adr/0002-coala-memory-boundaries.md`
- `docs/adr/0003-coala-action-and-authority-boundaries.md`
- `docs/adr/0004-fan-lan-mechanism-realizations.md`
- `docs/adr/0006-cognitive-cycle-graph-orchestration.md`
- `docs/adr/0009-durable-semantic-procedural-and-planner-state.md`
- `docs/phase4/M4-TRACEABILITY.md`, `docs/phase5/M5-TRACEABILITY.md`, `docs/phase6/M6-TRACEABILITY.md`
- `docs/phase8/M8-ARCHITECTURE-TRACEABILITY.csv`, `docs/phase8/M8-B3-RESULTS-CLARIFICATION.md`, `docs/phase8/M8-B4-D-PRODUCTION-B6-COMPOSITION.md`
- Production Java classes and corresponding test sources in the memory, actions, mechanisms, planning, runtime and PostgreSQL infrastructure modules.

The source inventory contains 11 Maven modules and 62 Java test-source files. The project owner reported that the accepted CI run passed 340 Maven tests. This matrix did **not** execute Maven or JUnit again; test names below identify source-level evidence and are not a new test-run claim.

### 2.2 How to read the Fan & Lan fields

Fan & Lan's seven-field representation is used to prevent feature-label matching. Each mechanism is inspected through:

- **S — State:** the authoritative state object(s) the mechanism consumes or changes.
- **C — Control:** the component or actor with authority to make the decision.
- **T — Trigger:** the event/condition that activates the transition.
- **P — Persistence:** what survives a cycle, request, transaction or process restart.
- **F — Failure:** explicit rejection, recovery, escalation, unknown-outcome or stopping semantics.
- **L — Learning:** whether an outcome changes future behavior or policy, rather than merely being logged/stored.
- **R — Resource/governance:** budgets, capacity, authority, priority and execution constraints.

Fan & Lan define E1–E4 as **relationships between a historical mechanism and a modern implementation** (lineage, structural correspondence, functional correspondence or convergence), and D0–D4 as **depth for a specific mapped mechanism** (from conceptual similarity to outcome-dependent policy update). Those are research-review coding axes, not software quality grades. This project matrix does not assign an E-code or a global D-code to the application. In particular, recording an episode, use event or maintenance event is not called D4 learning unless it demonstrably changes a later selection/control policy.

### 2.3 Evidence categories

- **Source evidence:** classes, interfaces, repositories, state models, transitions and application wiring visible in the frozen source.
- **Test evidence:** test classes that exercise the corresponding service or integration boundary.
- **Experimental evidence:** M8 outcomes, reported separately from static source mapping. A small designed fixture set is not generalized into a population claim.
- **Deployment evidence:** real supplier/ERP/WMS/carrier, enterprise IAM, production database operations and full production-host restart behavior. These remain separate from unit/integration evidence.

## 3. CoALA paper-to-code matrix

**Primary paper:** Theodore R. Sumers, Shunyu Yao, Karthik Narasimhan and Thomas L. Griffiths, *Cognitive Architectures for Language Agents*, arXiv:2309.02427v3 (15 March 2024), especially §§4.1–4.6 and §6.

CoALA describes a language agent through three connected dimensions: information storage, action space, and a decision-making procedure. It is a conceptual framework; it does not prescribe a particular Java class layout, database or agent framework.

| CoALA concept | Implementation mapping | Test evidence | Assessment | Boundaries / qualifications |
|---|---|---|---|---|
| **Three-dimensional agent organization** (memory, action space, decision procedure) | Memory contracts/services: `supply-chain-agent-memory`; internal/perception/grounding action contracts: `supply-chain-agent-actions`; planner strategies: `supply-chain-agent-planning`; mechanism controls: `supply-chain-agent-mechanisms`; lifecycle composition: `supply-chain-agent-runtime`. | `CoalaInternalActionsTest`, `SupplyChainExternalActionsTest`, `CognitiveCycleGraphTest`, `CognitiveCycleDecisionServicesTest`, `CognitiveCycleLifecycleServicesTest`. | **Direct core correspondence** | CoALA is the conceptual organizing framework; LangGraph4j and LangChain4j are implementation choices, not requirements stated by CoALA. |
| **Working memory** (§4.1) | `WorkingMemorySnapshot`, `WorkingMemoryEntry`, `Hypothesis`, `WorkingMemoryService`, `WorkingMemoryRepository`; PostgreSQL adapter `PostgresWorkingMemoryRepository`. Versioned immutable snapshot, scoped typed slots, provisional hypotheses, evidence references and compare-and-set updates. | `WorkingMemoryServiceTest`, `PostgresMemoryPersistenceIntegrationTest`, `CognitiveCycleLifecycleServicesTest`, `CognitiveCycleGraphTest`. | **Direct core correspondence** | Working memory is not `CognitiveCycleState`, `SupplyChainGraphState`, or a LangGraph4j checkpoint. It has a separate identity, lifecycle, version and repository contract. |
| **Episodic long-term memory** (§4.1) | `Episode`, `EpisodeEvent`, `EpisodeOutcome`, `EpisodeStatus`, `EpisodicMemoryService`; PostgreSQL adapter `PostgresEpisodeRepository`. An episode stores selected bounded experience, event ordering, provenance and evidence. | `EpisodicMemoryServiceTest`, `PostgresMemoryPersistenceIntegrationTest`, `CoalaInternalActionsTest`, `CognitiveCycleLifecycleServicesTest`. | **Direct core correspondence, with governed admission** | New episodes are recorded but unverified. They are not retrievable as trusted experience until an explicit review/verification transition. The lifecycle path does not self-verify from model output. Episodes are not general logs. |
| **Semantic long-term memory** (§4.1) | `SemanticFact`, `FactValue`, `FactStatus`, `SemanticFactRevision`, `SemanticAdmissionPolicy`, `SemanticMemoryService`; PostgreSQL adapter `PostgresSemanticFactRepository`. Valid-time and knowledge-time are separate. | `SemanticMemoryServiceTest`, `PostgresMemoryPersistenceIntegrationTest`, `PostgresM7B1PersistenceIntegrationTest`. | **Direct core correspondence, with conservative admission** | Semantic facts are sourced representations, not the authoritative ERP/supplier/inventory system of record. Model-derived content cannot be activated without human review. |
| **Procedural long-term memory** (§4.1) | `ProcedureVersion`, `ProcedureArtifactReference`, `ProcedureKind`, `ProcedureStatus`, `ProceduralMemoryService`; PostgreSQL adapter `PostgresProceduralMemoryRepository`. Immutable versioned artifact references use a SHA-256 identity and a DRAFT → VALIDATED → ACTIVE → RETIRED lifecycle. | `ProceduralMemoryServiceTest`, `PostgresM7B1PersistenceIntegrationTest`. | **Adapted realization** | The paper treats procedural memory broadly, including implicit knowledge in model weights and explicit action/decision code. This implementation governs explicit, reviewed procedure/capability artifacts; it does not store model weights or permit autonomous production-code rewriting/activation. |
| **Internal retrieval action** (§4.3) | `MemoryRetrievalAction` delegates to `MemoryRetrievalService`, which composes scoped exact retrieval across working, verified episodic, active semantic and active procedural memories. The full decision path applies B1 selection, persists an evidence-set reference, then projects selected long-term memory references into the versioned `WorkingMemorySnapshot` under `coala.retrieval.selected.references`. References retain memory kind, stable item ID and source version; the slot also carries an execution/version marker for safe replay, and `WorkingMemoryEntry` carries evidence references. Source payload and its full provenance/validity metadata remain in their authoritative repositories and are dereferenced under scope controls. B3 subsequently applies its own bounded workspace admission/broadcast. | `MemoryRetrievalServiceTest`, `CoalaInternalActionsTest`, `B1MemoryCandidateFactoryTest`, `CognitiveCycleDecisionServicesTest` (including projection, replay-idempotence and stale-version checks), `CognitiveCycleGraphTest` (graph-state version propagation). | **Direct core correspondence with reference-based application adaptation** | R1-A implements a reference projection, not wholesale copying of long-term payloads. Working memory and B3's cycle-bounded broadcast workspace remain distinct: a B1-selected reference in working memory is not automatically a B3-admitted/broadcast item. Cross-scope, expired and stale-version projection fails closed. |
| **Internal reasoning action** (§4.4) | `ReasoningAction`, `ReasoningProvider`, `ReasoningRequest`, `ReasoningAssessment`; runtime adapter `LangChain4jReasoningProvider`, with `BudgetedReasoningProvider`. Output contains typed findings, assumptions, hypotheses, evidence references and confidence; `WorkingMemoryUpdateAction` provides a separate, versioned write-back operation. | `CoalaInternalActionsTest`, `BudgetedReasoningProviderTest`, `LangChain4jReasoningProviderTest`. | **Adapted realization** | Reasoning is evidence-bounded and the working-memory write is a separate controlled action, rather than a monolithic reasoning call mutating WM. The action does not persist hidden chain-of-thought transcripts or authorize/execute actions. Provider structured-output enforcement depends on provider support/configuration. |
| **Internal learning action** (§4.5) | `EpisodeLearningAction` records bounded episodes; `EpisodicMemoryService`, `SemanticMemoryService` and `ProceduralMemoryService` expose separate governed write/review/admission lifecycles. | `CoalaInternalActionsTest`, `EpisodicMemoryServiceTest`, `SemanticMemoryServiceTest`, `ProceduralMemoryServiceTest`. | **Adapted realization** | Persistent memory writes are implemented. Automatic policy learning, model fine-tuning, self-modifying procedural code and automatic promotion of model-derived semantic facts are not implemented. Writing an experience is not itself proof that future policy changed. |
| **External grounding actions** (§4.2) | `SupplyChainPerceptionActions`, `SupplyChainGroundingActions`, `ActionDispatcher`, `ActionRegistry`, `CapabilityAuthorizer`, capability grants, idempotency/fencing ledgers; runtime orchestration via `CognitiveCycleGraph` and lifecycle services. | `SupplyChainExternalActionsTest`, `ActionDispatcherTest`, `CognitiveCycleGraphTest`, `CognitiveCycleLifecycleServicesTest`, PostgreSQL action-ledger and M8 integration tests. | **Adapted realization** | Grounding currently operates against the deterministic supply-chain simulator. It is not a live ERP, WMS, supplier, carrier or bank integration. A proposed plan is not an authorized operation; approval, revalidation and dispatcher controls remain separate. |
| **Decision-making loop** (§4.6) | `CognitiveCycleGraph`, `CognitiveCycleDecisionServices`, `CognitiveCycleLifecycleServices`, `B2ResolutionSubgraph`; M5 `SequentialReasoningPlanner`, `ThoughtSearchPlanner`, `MctsSearchPlanner`; shared deterministic candidate evaluation; M3 execution boundary. | `CognitiveCycleGraphTest`, `CognitiveCycleDecisionServicesTest`, `CognitiveCycleLifecycleServicesTest`, `SequentialReasoningPlannerTest`, `ThoughtSearchPlannerTest`, `MctsSearchPlannerTest`, `M8B4FullGraphWorkflowTest`. | **Direct core correspondence with domain-specific control** | Planning uses proposal/evaluation/selection; execution then returns observations to the next cycle. Retrieval and reasoning may occur within planning. Not every cycle performs every stage. Approval/authorization and reconciliation are application safeguards, not mechanisms prescribed by CoALA. |
| **Modularity / replaceable abstractions** (§6) | Framework-neutral memory/actions/mechanisms/planning/domain modules; explicit ports/adapters; LangGraph4j and LangChain4j isolated at runtime/orchestration and model-provider boundaries. | `verify-m8-b5-architecture.py` plus module-level tests and CI reactor build (the B5 CI result was accepted by the project owner). | **Direct architectural correspondence** | CoALA calls for modular abstractions, not this exact module count or framework. The architecture guard checks repository-specific boundaries; it is not a formal proof of defect-free code or security certification. |

### CoALA-specific interpretation

The memory model is more than four similarly named tables: each type has distinct truth conditions and lifecycle rules. Working memory is mutable only through versioned updates; episodes are reviewed before trusted retrieval; semantic facts are bitemporal and conservatively admitted; procedures are immutable, versioned and explicitly activated. R1-A closes the direct context-handoff gap with an explicit reference-based projection of B1-selected long-term memories into working memory. This is a strong operationalization of CoALA's memory distinctions, while preserving source-of-truth ownership and the separate B3 attention/broadcast boundary.

The implementation deliberately avoids two common category errors:

1. **Graph checkpoint ≠ working memory.** A graph checkpoint persists orchestration state; CoALA working memory persists active cognitive context with its own semantics.
2. **A stored record ≠ learning.** A recorded episode or maintenance event only becomes a learning mechanism in the stronger sense when a verified outcome changes future behavior or policy. That adaptive loop is not generally implemented here.

## 4. Fan & Lan mechanism-level matrix

**Primary paper:** Haodi Fan and Zucong Lan, *From Cognitive Architectures to Language Agents: A Mechanism-Level Review of Lineage, Convergence, and Migration Gaps*, arXiv:2607.23942v1 (27 July 2026), especially §§II-C–II-E, IV-C, V, VI-B–VI-E and the implementation-boundary appendices.

This paper is a mechanism-level review and research agenda, not a blueprint requiring every application to implement all candidate bundles. The repository's `supply-chain-agent-mechanisms` module explicitly implements B1, B2, B3, B4 and B6. The following table checks their control semantics and states what is still missing. **Naming caution:** Fan & Lan B2 means typed impasse/recovery; M8-B2 is the separate milestone for matched planner comparisons. Fan & Lan B3 means bounded workspace; M8-B3 is the separate mechanism-ablation milestone.

### B1 — Activation, latency, downstream utility and maintenance

**Code:** `B1MemoryCandidateFactory`, `B1CandidateFeatureProvider`, `B1CandidateFeatures`, `RecencyFrequencyActivationEstimator`, `B1RetrievalSelector`, `CognitiveRetrievalLatencyModel`, `MemoryMaintenanceAccountingService`; integrated by `CognitiveCycleDecisionServices` after exact M2 retrieval and before B3 workspace admission.

| Field | Implemented behavior and assessment |
|---|---|
| **S — State** | Typed candidates originate from M2 memory. Trusted features can include access history/cues, expected downstream utility, context cost, infrastructure latency and maintenance profile. **Present, but feature quality depends on caller/provider.** |
| **C — Control** | `B1RetrievalSelector` computes scores and applies count/context limits. **Explicit local selector authority.** |
| **T — Trigger** | Runs during context/evidence assembly after exact retrieval and before workspace admission. **Explicit trigger.** |
| **P — Persistence** | Underlying CoALA memories have PostgreSQL adapters. The B1 maintenance event ledger shown in the module is `InMemoryMemoryMaintenanceLedger`; do not assume all feature/access history is durable. **Partial.** |
| **F — Failure** | Candidate filtering, lifecycle checks, bounded capacity and explicit selector results constrain invalid/unusable inputs. This is not a complete adaptive-memory failure/recovery policy. **Partial.** |
| **L — Learning** | Actual maintenance events can be accounted for, but the recorded events do not train activation weights or update future selection from verified downstream use. **Missing defining feedback edge.** |
| **R — Resources** | Context units/candidate count, predicted cognitive latency, measured I/O latency and estimated maintenance cost enter selection. B6 can separately bound compute. **Present as configurable engineering policy.** |

**Tests:** `B1MemoryCandidateFactoryTest`, `B1RetrievalSelectorTest`, `CognitiveCycleDecisionServicesTest`.  
**M8 evidence:** B3 ablation on four fixtures found the current selector below a top-k comparator on recall/precision (reported selector recall 0.8333 and precision 0.4375 versus top-k recall 1.0 and precision 0.5625). The use-edge changed downstream-use accounting from zero to four in the fixtures but did not change candidate ranking. This is a mixed/negative result for the current selector, not evidence that B1 improves retrieval quality.  
**Assessment:** **Partial realization.** The activation/latency/value/cost selector exists, but the outcome-dependent policy-update coupling required by the residual B1 hypothesis is not present; ablation evidence does not establish a performance benefit.

### B2 — Typed impasse, protected substate, typed return and resolution compilation

**Code:** `DeterministicImpasseDetector`, `ImpasseResolutionCase`, `ParentContextSnapshot`, `ChildResolutionResult`, `ResolutionFinding`, `ImpasseResolutionService`, runtime `B2ResolutionSubgraph`; PostgreSQL adapters `PostgresImpasseCaseRepository`, `PostgresImpasseChildArtifactStore`; lifecycle composition in `CognitiveCycleLifecycleServices`.

| Field | Implemented behavior and assessment |
|---|---|
| **S — State** | Typed impasse signals/cases bind parent request, scope, working-memory version and protected objective fingerprint; child has a bounded case/substate. **Present.** |
| **C — Control** | B2 service and parent lifecycle own the case transition. Child receives a strictly bounded read/reasoning capability grant; it cannot mutate parent or issue commands. **Present.** |
| **T — Trigger** | Typed validator/impasse signals open a child-resolution case with deadline and action budget. **Present.** |
| **P — Persistence** | PostgreSQL stores impasse case and child artifacts/receipts; memory and parent intention have their own durable repositories. **Present for these records.** |
| **F — Failure** | Stale parent version/objective, invalid child capabilities, deadline/budget exhaustion and unresolved result are rejected or escalated. Parent re-reads authoritative state before accepting return. **Strong bounded failure semantics.** |
| **L — Learning** | Findings are retained for audit/parent reasoning; successful resolutions are not compiled into reusable recovery rules or automatically used to change future control. **Missing resolution-compilation edge.** |
| **R — Resources** | Child action count/capabilities/deadline and associated B6 budget guard bound investigation. **Present within host wiring.** |

**Tests:** `ImpasseResolutionServiceTest`, `CognitiveCycleGraphTest`, `CognitiveCycleLifecycleServicesTest`, `PostgresM7B2CoreIntegrationTest`.  
**M8 evidence:** B3's three designed B2 cases consolidated three raw signals (including a duplicate) into two typed substates, a 33.3% reduction in resolution units. This does not establish broad diagnosis quality, successful recovery or reduced recurring failure.  
**Assessment:** **Partial realization.** The protected, bounded substate and typed return are materially implemented; reusable resolution compilation and outcome-driven recovery learning are not.

### B3 — Bounded content competition, workspace, broadcast and admission learning

**Code:** `WorkspaceAdmissionService`, `WorkspaceCandidate`, `WorkspaceSnapshot`, `WorkspaceCoalition`, `WorkspaceBroadcastService`, `WorkspaceConsumerReceipt`, `InMemoryWorkspaceRepository`; composed after B1 by `CognitiveCycleDecisionServices`.

| Field | Implemented behavior and assessment |
|---|---|
| **S — State** | Typed candidates, required tags, conflict declarations, bounded workspace snapshots and coalition records. **Present.** |
| **C — Control** | Admission service admits mandatory content first, then ranks optional candidates under configured scoring/capacity rules; broadcast service delivers only admitted snapshot. **Present.** |
| **T — Trigger** | Runs before planning/model inference for the bounded workspace assembly. **Present.** |
| **P — Persistence** | `WorkspaceSnapshot` carries an explicit `cycleId` and close lifecycle; the current repository is in-memory. The live workspace is intentionally bounded to a decision cycle and does not need to survive process restart as durable cognitive state. Cross-cycle outcome evidence does need durable retention if it will update later admission policy. **Cycle-scoped identity/lifecycle is explicit; the in-memory snapshot is ephemeral, and durable learning evidence is absent.** |
| **F — Failure** | Mandatory overflow yields typed insufficient-capacity outcome; conflicts, unrecognized consumers and broadcast failures produce explicit outcomes/receipts. **Present locally.** |
| **L — Learning** | Broadcast/consumer receipts are recorded, but consumer outcomes do not train future admission or coalition policy. **Missing defining admission-learning edge.** |
| **R — Resources** | Candidate-count/context capacity and mandatory-first admission constrain workspace. **Present.** |

**Tests:** `WorkspaceAdmissionServiceTest`, `CognitiveCycleDecisionServicesTest`.  
**M8 evidence:** In three designed fixtures, B3 admitted the same three candidates/seven units as top-k while covering all 3/3 required tags versus top-k's 2/3. This is a bounded fixture result, not a universal gain or evidence of learned admission.  
**Assessment:** **Partial realization.** Bounded competition, explicit cycle identity, coalition accounting and synchronous broadcast exist. The live workspace need not be persisted across restart to satisfy the one-cycle broadcast semantics. The actual residual gap is durable, attributable outcome evidence and outcome-dependent admission learning. This is not a full LIDA/global-workspace architecture.

### B4 — Persistent intention, reconsideration and method authority

**Code:** `RecoveryIntention`, `ObjectiveFingerprint`, `RecoveryIntentionService`, `ObjectiveSatisfactionProof`, `DelegationReturn`; PostgreSQL `PostgresRecoveryIntentionRepository`; runtime `CognitiveCycleLifecycleServices` and `CognitiveCycleAuthorityPort`.

| Field | Implemented behavior and assessment |
|---|---|
| **S — State** | Persistent recovery objective, hard constraints, stable objective fingerprint, current method, lifecycle/version, suspension and delegation fields. **Present.** |
| **C — Control** | Recovery intention service controls lifecycle transitions; authority port supplies authenticated principal/approval/termination decisions. Planner/model suggestions cannot silently rewrite the objective. **Present as separated control boundaries.** |
| **T — Trigger** | Explicit reconsideration, method-change, delegation, suspension/resumption, proof-backed completion and authorized termination. **Present.** |
| **P — Persistence** | PostgreSQL intention repository supports durable state/version operations. **Present for intention records.** |
| **F — Failure** | Stale versions/fingerprints, invalid transitions, invalid delegation returns and missing completion proof are rejected; method replacement must preserve objective fingerprint. **Present.** |
| **L — Learning** | The B4 residual bundle does not prescribe a cross-trial learned method-selection policy. Its central control edge is runtime reconsideration and authorized method change while preserving the committed objective. Lifecycle records are not counted as D4 learning unless they alter a later policy. |
| **R — Resources** | Delegation limits and B6 resource governance bound work; approval/authority rules constrain consequential transitions. **Present, subject to production authority-port wiring.** |

**Tests:** `RecoveryIntentionServiceTest`, `CognitiveCycleLifecycleServicesTest`, `PostgresM7B1PersistenceIntegrationTest`, `PostgresM7B2CoreIntegrationTest`.  
**M8 evidence:** Two designed method-reconsideration probes preserved the objective fingerprint and constraints across method change. B4 lifecycle and persistence/recovery tests add implementation evidence. This does not establish broad goal stability under live production workloads.  
**Assessment:** **Strong adapted realization of the B4 residual control bundle at the application/mechanism level.** Persistent intention, explicit reconsideration and objective-preserving method change are implemented. Adversarial tests for oscillation, stale commitment, unauthorized replacement and premature termination are planned in Pre-B7 R1. Full live enterprise authority integration and full production-host process-restart behavior remain deployment gates. A cross-trial learned method-selection policy is not required by B4 and is not a claimed gap.

### B5 — Skill routing, verification, repair and fallback

**Paper status:** Fan & Lan's closest-baseline screening closes this candidate because GraSP already provides the reviewed selection–verification–repair–fallback chain within the paper's corpus. The paper retains B5 as a closed negative case, not one of its five residual hypotheses.

**Repository status:** No local B5/GraSP implementation is claimed in `supply-chain-agent-mechanisms`. The project did not add B5 as a residual mechanism. Planner validation, approval, revalidation and failure handling elsewhere in the application must not be relabelled as a complete GraSP implementation.

**Assessment:** **Not implemented / correctly excluded from the residual implementation scope.** This is not an open M8 blocker and should not be presented as a missing residual bundle.

### B6 — Uncertainty, resource allocation, interruption and stopping

**Code:** `UncertaintyAssessment`, `UncertaintySignal`, `MetaControlGovernor`, `MetaControlPolicy`, `ResourceBudget`, `ResourceBudgetLedger`, `BudgetReservation`, `ResourceUsage`; runtime `ResourceBudgetExecutionGuard`, `BudgetedReasoningProvider`; PostgreSQL `PostgresResourceBudgetLedger` (migration V009); shared guard composition across M5 planning, M3 reasoning and B6 when the production host injects the same instance.

| Field | Implemented behavior and assessment |
|---|---|
| **S — State** | Explicit evidence, hypothesis and plan uncertainty; per-execution resource budget, reservations and usage. **Present.** |
| **C — Control** | `MetaControlGovernor` chooses continue, request evidence, reduce computation, interrupt for human review/replenishment or stop; the shared execution guard enforces resource checks at call sites. **Present when wired to the same guard instance.** |
| **T — Trigger** | Before a reasoning/planning/next-step action when uncertainty, deadline or resource conditions are evaluated. **Present.** |
| **P — Persistence** | PostgreSQL durable resource-budget ledger and reservation/usage state. **Present.** |
| **F — Failure** | Missing uncertainty dimensions and provider/budget-store failures are fail-closed; reservation lifecycle and concurrent accounting are tested. Hard-kill orphan-reservation reclamation is not yet automatic. **Strong local failure semantics, with recovery gap.** |
| **L — Learning** | Usage is accounted and recorded; no validated uncertainty calibration or feedback-trained future allocation/stopping policy is present. **Missing adaptive learning edge.** |
| **R — Resources** | Bounded compute/resource units, reservations, actual usage, deadlines and algorithmic budgets. Business resources such as inventory, money and supplier capacity remain authoritative domain state, not B6 compute units. **Present.** |

**Tests:** `MetaControlGovernorTest`, `ResourceBudgetExecutionGuardTest`, `BudgetedReasoningProviderTest`, `M8B4DPostgresBudgetExecutionIntegrationTest`, `M8B4ReliabilityIntegrationTest`. `M8B4ForkedJvmProcessRestartIntegrationTest` exercises a smaller LangGraph4j test graph with PostgreSQL adapters across child JVMs; it does not prove process-kill recovery of the complete production application graph.  
**M8 evidence:** B3/B4/B6 and B4-D tests exercise selected interruption, resource and recovery paths. They are not evidence of a fully calibrated, end-to-end cross-stage uncertainty policy in a production host.  
**Assessment:** **Partial-to-strong adapted realization of budget enforcement and meta-control actions.** Durable budgets and explicit interrupt/stop decisions exist. Full uncertainty propagation/calibration, automatic orphan reservation reclamation and complete production-host composition/restart validation remain incomplete.

## 5. Cross-cutting integration: how the architecture fits together

The source keeps several states and authority boundaries distinct. That separation is a significant part of the implementation quality and should be visible in the book.

| Concern | Authoritative implementation boundary | What it is not |
|---|---|---|
| Current cognitive context | M2 `WorkingMemoryService` and versioned `WorkingMemorySnapshot` | Not LangGraph state/checkpoint. |
| Long-term experience/knowledge/procedures | M2 episodic, semantic and procedural services/repositories | Not an untyped shared vector store or raw log bucket. |
| Context candidate scoring | Fan & Lan B1 factory/selector | Not exact M2 retrieval and not a learned selector. |
| Bounded inference context | B3 workspace admission/snapshot/broadcast | Not a general-purpose message bus or full global-workspace theory. |
| Recovery substate | B2 impasse case, parent snapshot and child result | Not generic checkpoint/resume. |
| Goal/commitment lifecycle | B4 recovery intention with objective fingerprint | Not a planner's mutable goal string or a handoff alone. |
| Compute/resource governance | B6 ledger/governor/guard | Not domain inventory, money or supplier-capacity authority. |
| Plan generation | M5 CoT-style, ToT-inspired and RAP-inspired planners | Not action authorization or external execution. |
| Model reasoning | LangChain4j adapter behind `ReasoningProvider` in runtime | Not the owner of application permissions or action grants. |
| Workflow orchestration | LangGraph4j `CognitiveCycleGraph` and B2 subgraph | Not the memory model, planner algorithm, or business system of record. |
| Consequential action | M3 `ActionDispatcher`, trusted `ActionPrincipal`/capabilities, plan-bound approval, revalidation, idempotency/fencing and reconciliation | Not direct LLM tool execution. |
| External environment | Deterministic simulator and explicit domain/authority ports | Not deployed real ERP/WMS/supplier/carrier systems. |

This layering supports the following bounded path:

1. Retrieve context from the four CoALA memory responsibilities.
2. Apply B1 selection and B3 workspace admission where configured.
3. Plan candidates with M5 against the trusted simulator/world model.
4. Independently evaluate hard constraints and candidate evidence.
5. Preserve/reconsider the recovery objective through B4, open B2 child investigation where needed, and apply B6 resource decisions.
6. For state-changing actions, require trusted authorization/approval and revalidation, then dispatch through M3 with idempotency/fencing.
7. Observe/reconcile the modeled outcome; update working memory and record an episode subject to its verification lifecycle; continue the cycle.

A plan candidate, a planner score, a graph checkpoint or a reasoning response is never itself permission to perform a consequential action.

## 6. Test and experimental evidence register

| Evidence | What it supports | What it does not establish |
|---|---|---|
| `WorkingMemoryServiceTest`, `EpisodicMemoryServiceTest`, `SemanticMemoryServiceTest`, `ProceduralMemoryServiceTest`, `MemoryRetrievalServiceTest` | Distinct CoALA memory models, lifecycle rules, scope/time checks and retrieval contracts. | Cognitive validity of the theory or general memory-task superiority. |
| `CoalaInternalActionsTest`, `SupplyChainExternalActionsTest`, `ActionDispatcherTest` | Typed internal, perception and grounding actions; evidence and capability boundaries; idempotency/failure handling. | Live ERP/WMS/carrier behavior or deployed IAM correctness. |
| `B1MemoryCandidateFactoryTest`, `B1RetrievalSelectorTest`, `ImpasseResolutionServiceTest`, `WorkspaceAdmissionServiceTest`, `RecoveryIntentionServiceTest`, `MetaControlGovernorTest` | Explicit B1/B2/B3/B4/B6 mechanism state and transition invariants. | Complete residual-bundle learning couplings or cross-domain generalization. |
| `SequentialReasoningPlannerTest`, `ThoughtSearchPlannerTest`, `MctsSearchPlannerTest`, `PlannerRouterTest` | Planner algorithms, deterministic constraints, routing and bounded execution. | That ToT/MCTS implementations reproduce the original papers exactly. |
| `CognitiveCycleGraphTest`, `CognitiveCycleDecisionServicesTest`, `CognitiveCycleLifecycleServicesTest` | Composition and control flow across memory, mechanisms, planners, approval, execution and reconciliation. | Full real-host integration or complete production infrastructure. |
| `PostgresMemoryPersistenceIntegrationTest`, `PostgresM7B1PersistenceIntegrationTest`, `PostgresM7B2CoreIntegrationTest`, `M8B4DPostgresBudgetExecutionIntegrationTest` | PostgreSQL persistence/integration for memory, planner/runtime state, operation ledgers, intention and budgets. | Production HA, measured RPO/RTO, formal DR certification or all application state under hard kill. |
| `M8B4ForkedJvmProcessRestartIntegrationTest` | A child-JVM process-boundary/restart test for a smaller LangGraph4j graph with PostgreSQL adapters. | Hard-kill/relaunch recovery of the complete production `compileFull` graph and all host adapters. |
| M8-B2 user-verified report: 125 runs; 25×5 complete; 170 aggregate values and 10 pairs recomputed; 125/125 reported replay matches; zero hard-constraint violations; `plannerOnly=true` | Completeness and consistency of this bounded planner-only evaluation; a specific feasibility-versus-compute trade-off under FAIL-10. | General planner superiority, superior score, LLM-planner benefit, external-action execution or cross-environment determinism. |
| M8-B3 ablations | Small designed-fixture tests of selected B1/B2/B3/B4/B6 controls and combinations. | Population-level effect, all-mechanism composition, or broad causal claims beyond the reported probes. |
| M8-B5 CI architecture/build checks and owner-reported 340 passing Maven tests | The source passed the accepted build/test and architecture workflow reported for the frozen milestone. | Zero defects, formal code-quality certification, SAST/SCA, independent penetration testing or production readiness. |

### Experimental interpretation guardrails

- M8-B2 found the five planners identical on 24 of 25 scenarios. FAIL-10 was the only differentiating case: CoT and Greedy did not find a feasible plan; ToT, RAP-inspired MCTS and Hybrid did, at score 0.70675. The defensible interpretation is a bounded-search feasibility benefit under one tight-budget scenario at increased compute cost—not general planner superiority.
- The lower overall mean score for the three search planners is not a like-for-like penalty because FAIL-10 enters those planners' feasible-result means but not CoT/Greedy's. On the 22 scenarios all five solve, the score difference is exactly zero, as reported by the project owner.
- The 125/125 determinism result is a single summary record without per-run fingerprints and comes from one CI environment. It supports the reported replay protocol result with that independent-verification caveat; it does not establish cross-JVM/OS identity.
- B3 ablation counts are small and fixture-designed. They should be reported with denominators and not generalized into system-wide effects.

## 7. Gaps and production boundaries to keep explicit

### Research-mechanism gaps

1. **B1:** no feedback-trained update that changes future activation/accessibility or utility selection from verified downstream use; M8-B3's small comparison is mixed/negative for current selector ranking.
2. **B2:** no safe compilation of a verified child resolution into a reusable, condition-scoped recovery procedure, nor an experiment showing fewer repeated future failures.
3. **B3:** no durable, attributable downstream-use/outcome ledger that updates future typed-content admission. The in-memory, cycle-scoped workspace itself is not treated as a missing durable database feature.
4. **B4:** no defining residual-bundle gap remains in the core intention/reconsideration/method-authority contract. Additional adversarial validation is planned; learned future method selection is not required by B4.
5. **B6:** no validated uncertainty calibration/policy-learning loop; automatic hard-kill orphan reservation reclamation remains outstanding; full production-host sharing of the same resource guard and complete-graph process restart remain release gates.
6. **CoALA learning:** no model fine-tuning or autonomous procedural-code self-modification; semantic/procedural changes remain governed. This is a deliberate safety boundary, not a defect in the four-memory model.

### Production-readiness gaps

- Real supplier, ERP, WMS, carrier and other external integrations are not connected; simulator/test adapters remain the validated environment.
- Production IAM, tenant identity and authority provider deployment must be supplied by the host.
- Full production `compileFull` host validation and full application-graph OS-process kill/relaunch are not established by the smaller forked-JVM graph test.
- Hard-kill orphan resource reservation reconciliation is not automatic.
- Production WAL/PITR, measured RPO/RTO, retention/legal-hold policy, formal SAST/SCA, independent threat-model sign-off and penetration testing remain separate gates.

These gaps do not invalidate the accepted M8-B6 freeze or the implementation work. They constrain what may be claimed as implemented, tested, experimentally demonstrated or production-certified.

## 8. Defensible claims for Chapters 19 and 20

### Recommended claim

> The Supply-Chain Disruption Management Agent is a production-oriented Java reference implementation that operationalizes CoALA's central memory, action-space and decision-procedure organization. It implements distinct working, episodic, semantic and procedural memory contracts; typed internal, perception and grounding actions; explicit planning and execution boundaries; and a guarded cognitive lifecycle. It also realizes selected Fan & Lan B1, B2, B3, B4 and B6 mechanism concepts as bounded, inspectable controls with explicit state, triggers, persistence, failure and resource semantics. The implementation is supported by automated unit/integration tests, PostgreSQL integration evidence and bounded planner/mechanism experiments. It does not claim complete reproduction of either paper or complete implementation of every Fan & Lan residual learning coupling, nor does it establish external-system production readiness or formal security certification.

### Claims to avoid

- “We implemented all of CoALA.” — CoALA is a conceptual framework and this repository implements a specific subset/operationalization, not every technique or all possible learning pathways.
- “We fully implemented Fan & Lan.” — the paper is a review/research agenda; B1/B2/B3/B4/B6 are partial/adapted and B5 is closed by GraSP in the paper's corpus.
- “Our planner is superior.” — M8-B2 does not support that claim.
- “MCTS is RAP.” — it is a deterministic, simulator-backed RAP-inspired MCTS adaptation.
- “Our ToT planner reproduces ToT exactly.” — it searches structured domain action paths, not the original textual-thought protocol.
- “The code is bug-free / production certified.” — CI and tests are meaningful evidence but do not establish those guarantees.
- “The agent executes real supply-chain operations.” — current validated grounding is against a deterministic simulator and application ports.

## 9. Primary references

1. Sumers, T. R., Yao, S., Narasimhan, K., & Griffiths, T. L. (2024). *Cognitive Architectures for Language Agents*. arXiv:2309.02427v3. https://arxiv.org/abs/2309.02427. Relevant: §4.1 Memory; §4.2 Grounding actions; §4.3 Retrieval actions; §4.4 Reasoning actions; §4.5 Learning actions; §4.6 Decision making; §6 Actionable Insights.
2. Fan, H., & Lan, Z. (2026). *From Cognitive Architectures to Language Agents: A Mechanism-Level Review of Lineage, Convergence, and Migration Gaps*. arXiv:2607.23942v1. https://arxiv.org/abs/2607.23942. Relevant: §II-C seven-field mechanism representation; §II-D evidence/depth distinction; §IV-C implementation-status boundaries; §V migration analysis; §VI five residual bundles and closed B5 candidate; Appendices B–E for evidence and boundary cases.

## 10. Review status

This is a source-to-code traceability assessment against the M8-B6 frozen source archive and the project-owner-reported accepted CI/experimental results. R0 clarifications correct the B3 persistence interpretation and the B4 residual-bundle acceptance boundary; they do not change executable source or reopen the M8-B6 freeze. It is documentation, not a new implementation milestone. It does not alter or reopen the M8-B6 freeze. Its purpose is to supply an auditable basis for Pre-B7 remediation and, later, the Chapter 19 and Chapter 20 outlines.


## Pre-B7 implementation addendum (2026-11-20)

The main matrix above is the source-to-code snapshot against the accepted M8-B6 source freeze. The following post-freeze increments are tracked separately so that the original evidence baseline is not silently rewritten:

- **R1-A — CoALA retrieval projection:** B1-selected, scope-valid memory references are now projected into versioned `WorkingMemorySnapshot` under `coala.retrieval.selected.references`; the source records are not duplicated. The projection has an execution/base-version marker for exact idempotent replay and fails closed on stale CAS. The project owner reported green CI after the inventory correction.
- **R1-B — B4 lifecycle validation:** adversarial application-boundary tests now cover method-change reconsideration, objective preservation, stale intention versions and authorized termination semantics. The project owner reported green CI after the inventory correction. No learned future method-selection policy is claimed or required by B4.
- **R1-C — Versioned uncertainty handoff:** implementation is prepared and **pending CI**. `UncertaintyAssessment` now has a small immutable reference (assessment ID, exact revision, execution and memory scope), assessment/expiry timestamps, provenance and explicit calibration metadata. The LangGraph checkpoint carries only the reference. B3 admission requests and M5 planning requests carry the exact revision; planner-session fingerprints include it. B4/B6 and authorization/revalidation/execution boundaries resolve and validate the exact revision. The B2 child graph receives the parent reference, refreshes it after investigation and returns the refreshed revision to the parent before parent-context revalidation. Refreshes are trigger-idempotent and must advance exactly one revision.

R1-C does **not** establish calibration: values remain uncertainty signals and the fixture assessments are explicitly `NOT_CALIBRATED`. It also defines a durable host-port contract but does not add a production database-backed uncertainty repository. Those are separate validation/host integration concerns. The final matrix will be regenerated at R4 against the final Pre-B7 source and CI commit.
