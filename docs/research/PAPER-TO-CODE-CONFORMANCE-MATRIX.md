# Final CoALA and Fan & Lan Paper-to-Code Conformance Matrix

**Project:** Supply-Chain Disruption Management Agent  
**Assessment revision:** Final as-built assessment for the post-R1/R2 and F-01–F-04 candidate source stack  
**Assessment date:** 2026-11-21  
**Scope:** CoALA conceptual-framework implementation; Fan & Lan review-method coverage; B1, B2, B3, B4, B5 and B6 control bundles; cross-bundle synthesis and evaluation evidence  
**Change class:** Documentation only. No Java source, tests, POMs, SQL migrations, scripts or CI workflow logic are changed by this matrix package.

> **Primary conclusion:** The current application has strong architectural correspondence to CoALA and substantial, integrated but incomplete realizations of Fan & Lan's B1, B2, B3, B4 and B6 residual bundles. It is **not yet a total implementation of all the operational invariants in either paper**, and the project must not claim that it is. In particular, B1/B3 outcome-driven learning is not active on a verified corpus, B2 procedure reuse is not yet part of the normal full-graph planning path, B4's complete intention record is narrower than the paper's proposed record, B6 uncertainty is not calibrated or value-propagated across stages, and the GraSP-like B5 skill governor is not implemented even though Fan & Lan make it an implementation dependency of their bounded synthesis runtime.

This matrix therefore does **not** authorize starting the book chapters. The remaining implementation/evaluation work is listed in `FINAL-IMPLEMENTATION-GAPS-AND-ACCEPTANCE-PLAN.md`.

---

## 1. Executive verdict

### 1.1 Direct answer: can the code be said to conform completely to both papers?

**No—not yet, and a green CI result does not establish that claim.** The defensible current claims are narrower:

- **CoALA:** the project implements the central memory/action/decision organization and the core cognitive-cycle control flow as a Java reference architecture. It has distinct working, episodic, semantic and procedural memory responsibilities; internal retrieval/reasoning/learning operations; external perception/grounding actions; and a planning/evaluation/selection/execution/reconciliation graph. Several choices are deliberate application-specific adaptations. Some CoALA learning forms and the continuously running host loop are not implemented.
- **Fan & Lan:** the project has executable and tested control paths corresponding to all five residual bundles (B1, B2, B3, B4, B6), with F-01–F-04 materially improving B4 authority, B6 planner downshift, B2 governed recovery-procedure staging, and B3 coalition competition. But the defining learning couplings and stage-wide uncertainty semantics are not all complete or empirically demonstrated. B5 is correctly not treated as a sixth residual; it is closed by GraSP in the paper. However, Fan & Lan's §VI-D/§VI-E bounded synthesis runtime reuses a migrated GraSP-like skill governor. This repository does not contain that governor.
- **Evidence:** the project owner has confirmed the combined CI is green. The three repository static guards were also rerun on the reconstructed combined source stack and passed. CI verifies the code and tests exercised by its workflow; it does not prove empirical mechanism superiority, calibration, cross-domain transfer, complete historical migration, or implementation of an absent B5 dependency.

The word *complete* must be tied to an explicit target. This matrix defines a concrete next target: **complete the CoALA memory/action/decision responsibilities selected for this supply-chain application (with model-weight and source-code self-modification either implemented under governance or explicitly scoped out), implement all five Fan & Lan residual control bundles as composable S/C/T/P/F/L/R invariants, integrate or explicitly depend on the closed GraSP/B5 governor where the synthesis runtime requires it, and supply the empirical evidence required for any effectiveness claims.**

### 1.2 What the papers do—and do not—require us to implement

CoALA is presented by Sumers et al. as a **conceptual framework** to organize language agents around memory, action space and decision-making. It is not a Java class diagram, a prescribed persistence technology, or a requirement to reproduce every system discussed in its survey. The relevant implementation organization is §§4.1–4.6. [Primary source: CoALA, arXiv:2309.02427v3, especially §§4.1–4.6.](https://arxiv.org/html/2309.02427v3)

Fan & Lan is a **bounded mechanism-level review and research agenda**, not a single executable architecture specification. Its corpus includes ten historical cognitive architectures, eight language-agent runtime families and 42 mechanism-focused modern systems. Those ten architectures and 42 systems are evidence and comparison subjects; implementing every one of them inside this application is neither required nor a meaningful interpretation of the paper. The paper first derives six candidates, closes B5 through closest-baseline screening with GraSP, and retains five residual bundles: B1, B2, B3, B4 and B6. [Primary source: Fan & Lan, arXiv:2607.23942v1, §§II, IV–VI.](https://arxiv.org/html/2607.23942v1)

Fan & Lan's §VI-D/§VI-E then describes cross-bundle dependencies and a bounded synthesis runtime that **reuses a migrated GraSP-like skill governor**. Consequently:

1. Implementing B1/B2/B3/B4/B6 does not mean the app has implemented the whole review paper.
2. B5 is not an outstanding residual hypothesis; it is the paper's closed candidate.
3. If the target is the paper's *bounded synthesis runtime*, a GraSP-like skill-governance component must nevertheless be supplied as an integrated subsystem or explicit, tested external dependency. This repository currently has neither.

### 1.3 Overall status

| Scope | Current status | Is “complete” justified? |
|---|---|---|
| CoALA's three organizing dimensions | Strong and integrated | **No** — some learning modes and the outer host loop remain out of scope or absent |
| CoALA working/episodic/semantic/procedural memory | Strong, with governed application-specific lifecycles | **Strong core architecture; not complete for active procedural reuse, learning-action coverage, durable full-host composition or all CoALA learning forms** |
| CoALA internal/external actions | Substantive; grounding is simulator-backed | **No** — the learning action surface is narrower and no live external system adapters are present |
| CoALA iterative decision procedure | Full bounded graph with proposal/evaluation/retry, authority, execution and observation | **Strong bounded-cycle realization** — the full continuously running host is not supplied |
| Fan & Lan B1 | Integrated selector and shadow feedback protocol | **No** — accessibility update and active, verified learning are incomplete |
| Fan & Lan B2 | Typed recovery, guarded drafts, governed procedural staging | **No** — automatic condition-specific retrieval/use in the normal planner path and transfer evidence are incomplete |
| Fan & Lan B3 | Bounded workspace, pair/triple coalition competition, synchronous broadcast and receipt-linked evidence | **No** — coalition-level outcome learning and live evidence activation are absent |
| Fan & Lan B4 | Persistent intention and actual planner-method authority | **No** — intention record remains narrower than the full proposed schema and trigger policy |
| Fan & Lan B5 | Correctly excluded from the residual list; no local GraSP implementation | **No** for §VI-E synthesis-runtime completeness; it must be supplied as dependency or subsystem |
| Fan & Lan B6 | Shared resource guard, typed downshift, interruption and stopping | **No** — uncertainty is not calibrated or consumed as values across all target stages |
| Empirical validation of the five-bundle composition | CI and synthetic/fixture evidence; no verified outcome/holdout corpus | **No** |

---

## 2. Source baseline and evidence provenance

### 2.1 Exact source stack assessed

The final source assessed here is the base archive supplied by the project owner, overlaid in order with the accepted F-01/F-02 and F-03/F-04 changesets:

| Layer | Exact source archive | SHA-256 |
|---|---|---|
| Base project | `supply-chain-agent-main (6).zip` | `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6` |
| F-01/F-02 | `M8_PREB7_F01_F02_B4_METHOD_AUTHORITY_AND_B6_PLANNER_DOWNSHIFT_CHANGESET_ONLY_2026-11-21.zip` | `a05ed45d02d9a3c141f4f21d2b1924ddcc57c1f13779032b28a5f7146a08593a` |
| F-03/F-04 | `M8_PREB7_F03_F04_GOVERNED_RECOVERY_MEMORY_AND_B3_COALITION_COMPETITION_CHANGESET_ONLY_2026-11-21.zip` | `27a16c8869f2e4eb54cbb00007a405e84caff6799bc6af04abee1ce3a5fe5d01` |

The changesets are repository-root-relative. This assessment reconstructs the combined tree by applying them in the order above. A Git commit SHA and hosted workflow-run URL were not supplied with the files, so the source identity available for this assessment is the three-archive digest chain, not a Git commit identity.

### 2.2 Static inventory of the reconstructed tree

| Inventory item | Count | Interpretation |
|---|---:|---|
| Maven submodules | 11 | Domain, memory, core, simulator, actions, mechanisms, planning, runtime, PostgreSQL infrastructure, app and evaluation |
| POM files | 12 | Parent POM plus 11 module POMs |
| Production Java files under Maven modules | 462 | Compiled module production source inventory |
| Test Java files under Maven modules | 72 | Test source inventory; not an executed-test count |
| Additional root `src/` Java files | 0 | All Java files are under the 11 module source roots |
| Total Java files in tree | 534 | All are within the 11 Maven module source roots |
| PostgreSQL migration chain | V001–V012 | V001 is in the memory module; subsequent runtime/infrastructure migrations extend through V012 |

The final reconstructed stack contains no Java files outside the 11 Maven modules. All 534 Java files are located under module `src/main/java` or `src/test/java` roots. Earlier audit inventories that reported two root-level duplicate files described a prior candidate tree; that finding does not apply to this final F-01–F-04 stack.

### 2.3 Validation status and limitations

The project owner confirmed that the combined source stack passed hosted CI after F-01/F-02 and F-03/F-04. The three repository static guards were independently rerun on the reconstructed combined tree and passed. This assessment does not include a new Maven reactor execution, and no CI workflow URL, run ID or Git commit SHA was supplied. The authoritative source identity available here is the checksum-verified base archive followed by the two checksum-verified overlay archives, plus the reconstructed source-tree fingerprint in `CONFORMANCE_ASSESSMENT_METADATA.json`.

The following repository static guards were independently rerun on the reconstructed combined tree and passed:

- `python3 scripts/verify-m8-b5-architecture.py`
- `python3 scripts/verify-m8-r2-evidence.py`
- `python3 scripts/verify-m8-preb7-f03-f04.py`

The guards verified module dependency boundaries, R2/B2/B3 evidence wiring and shadow/disabled controls, F-03 procedural-memory governance and V012 artifact support, and F-04 bounded coalition competition and truncation fallback. The complete Maven reactor was not independently rerun in this workspace. This matrix therefore records hosted CI as **project-owner-confirmed**, and static-guard execution as **independently rerun**. It does not invent a test total, commit SHA or run ID.

### 2.4 Evidence categories

Every assertion below belongs to one of four evidence classes:

- **Source evidence:** production types, interfaces, methods, module wiring, SQL migrations and composition points in the reconstructed source.
- **Test evidence:** test source and the green CI acceptance reported by the owner. A test proves only the behavior its assertions exercise.
- **Experimental evidence:** controlled outcomes, paired baselines, holdout observations and ablations. This is distinct from CI. Current R2 evidence explicitly reports no verified downstream-outcome corpus and no independent holdout scenarios.
- **Deployment evidence:** a production host composed with durable adapters, real ERP/WMS/supplier/carrier integrations, live IAM and operational recovery. This is not established by this reference implementation's CI.

---

## 3. Assessment vocabulary

### 3.1 Conformance labels used in this matrix

| Label | Meaning |
|---|---|
| **Strong core correspondence** | The central concept is represented by explicit types/services and is connected to the full decision graph. It is not necessarily a literal reproduction of the paper's examples or every optional learning mode. |
| **Adapted realization** | The application implements the same responsibility through domain-specific state, governance and lifecycle choices. Those choices are explicitly identified as engineering decisions. |
| **Partial realization** | A meaningful part of the required state/control chain exists, but one or more defining couplings, learning edges, persistence boundaries or full-graph integrations are absent. |
| **Not implemented** | No local executable implementation is claimed. |
| **Closed candidate / dependency** | Fan & Lan's review closes the candidate as an independent residual, while the resulting baseline may still be a dependency of the paper's synthesis architecture. |
| **Not applicable as runtime feature** | The paper item is a review method, survey corpus, analytical coding rule or conceptual discussion, not an application subsystem. |

These labels are project traceability labels, not scientific maturity codes.

### 3.2 Fan & Lan's seven-field tuple and independent E/D axes

For each mechanism the paper asks:

\[
\mathcal{K}=\langle S,C,T,P,F,L,R\rangle
\]

where `S` is operative state, `C` control locus, `T` transition trigger, `P` persistence boundary, `F` failure semantics, `L` learning operator and `R` resource/governance policy.

The paper also codes two **independent** dimensions:

- **E1–E4 — evidence relation:** E1 documented lineage; E2 implemented structural correspondence; E3 functionally comparable control with materially different internal organization; E4 structural convergence without evidence of transfer.
- **D0–D4 — migration depth:** D0 conceptual resemblance; D1 callable interface; D2 explicit state/component; D3 a trigger autonomously executes the mapped control transition; D4 observed outcomes update future control policy.

An E-code is not a quality level, and a D-code is not a completeness score. D4 can coexist with missing historical invariants. Neither axis is a global score for the whole application. The E/D values in the mechanism rows below are **provisional project recodings**, not independently adjudicated scientific findings. They are deliberately conservative and are accompanied by the exact invariant that remains missing. (Fan & Lan, §II-C–§II-E.)

---

## 4. CoALA conceptual-framework conformance matrix

**Primary source:** Sumers, Yao, Narasimhan and Griffiths, *Cognitive Architectures for Language Agents*, arXiv:2309.02427v3, particularly §§4.1–4.6. CoALA organizes an agent through information storage, internal/external action space, and an interactive planning/execution decision procedure. [Full paper.](https://arxiv.org/html/2309.02427v3)

| CoALA concept | Paper meaning | Current implementation | Integration and evidence | Status | Remaining requirement for a complete application-level realization |
|---|---|---|---|---|---|
| Three dimensions | Memory; internal/external action space; decision procedure | `supply-chain-agent-memory`, `supply-chain-agent-actions`, `supply-chain-agent-planning`, `supply-chain-agent-mechanisms`, `supply-chain-agent-runtime` and `supply-chain-agent-app` | `CognitiveCycleApplicationGraph.buildFull/compileFull` composes decision and lifecycle services; architecture guard checks module boundaries | **Strong core correspondence** | Build and document a durable application composition root that actually assembles the full graph, rather than only exposing a compile seam and an evaluation-host composition |
| Working memory | Short-term active symbolic state that persists across model calls; current perception, retrieved knowledge, hypotheses and goals | `WorkingMemorySnapshot`, `WorkingMemoryEntry`, `MemoryValue`, `Hypothesis`, `WorkingMemoryService`, `WorkingMemoryRepository`, `PostgresWorkingMemoryRepository` | Versioned immutable snapshots, typed slots, evidence links, expiry and compare-and-set; working memory is explicitly distinct from LangGraph state/checkpoint | **Strong core correspondence** | Ensure the full production host uses the durable repository on every path; document which goal/plan fields belong in working memory vs authoritative intention state |
| Episodic memory | Stored experiences and event/trajectory knowledge | `Episode`, `EpisodeEvent`, `EpisodeOutcome`, `EpisodicMemoryService`, PostgreSQL adapter | New episodes are `RECORDED`; verified episodes have reviewer/evidence/time and are eligible for trusted retrieval | **Strong adapted realization** | Add verified outcome attribution from real downstream actions and show that retrieved episodes improve later decisions in controlled experiments; do not treat generic logs as episodes |
| Semantic memory | General facts and world knowledge | `SemanticFact`, `SemanticFactRevision`, `SemanticMemoryService`, `SemanticAdmissionPolicy`, PostgreSQL adapter | Valid-time and knowledge-time are separate; sourced facts are staged and conservatively activated; model-derived facts require review | **Strong adapted realization** | Provide explicit full-graph semantic learning/write action and evaluate whether future retrieval actually consumes admitted facts; preserve authoritative source-system boundary |
| Procedural memory | Implicit procedural knowledge in LLM weights and explicit procedures/code | `ProcedureVersion`, `ProcedureArtifactReference`, `ProceduralMemoryService`, B2 recovery artifacts, F-03 promotion service and V012 store | Immutable artifact references and lifecycle `DRAFT → VALIDATED → ACTIVE → RETIRED`; approved B2 drafts can be staged for separate review/activation | **Strong for explicit procedures; partial overall** | Complete the full-graph B2 reuse path; decide and document whether model-weight learning is explicitly out of scope or supplied through a separately governed model-adaptation adapter; code/prompt change must remain reviewed and versioned |
| Grounding actions | External actions interact with the environment through grounding | `SupplyChainPerceptionActions`, `SupplyChainGroundingActions`, action descriptors, `ActionDispatcher`, `CapabilityAuthorizer`, idempotency and resource guards | Action plan is a proposal; approval, revalidation, capability checks and dispatch are separate; simulator is the environment | **Adapted realization** | Add real-world adapter only if the deployment target requires it; for the book, clearly label the simulator as the grounding environment and do not claim live ERP/WMS/supplier/carrier execution |
| Retrieval actions | Read from long-term memory into working memory; rule-based/sparse/dense or recency/importance/relevance methods are examples | `MemoryRetrievalAction`, `MemoryRetrievalService`, `B1MemoryCandidateFactory`, `B1RetrievalSelector`, evidence-set and workspace paths, R1A working-memory projection | Exact lifecycle-aware retrieval is separated from B1 scoring; selected references are projected into versioned working memory | **Adapted realization** | Integrate activated B2 artifact dereference into the normal full-graph planning context; improve B1 learning so observed use updates accessibility as well as utility; ensure uncertainty can influence pre-inference admission |
| Reasoning actions | LLM reasoning reads and writes working memory to generate new information | `ReasoningAction`, `ReasoningProvider`, `LangChain4jReasoningProvider`, `BudgetedReasoningProvider`, separate `WorkingMemoryUpdateAction` | Typed findings, assumptions, hypotheses, evidence references and assessments; write-back is an explicit versioned action | **Adapted realization** | Add end-to-end tests that a reasoning result can be admitted as a typed working-memory update and later consumed; no need to persist private model chain-of-thought |
| Learning actions | Write to long-term memory: episodic experience, semantic knowledge, implicit procedural parameters and explicit procedures/code; potentially learn retrieval/decision procedures | `EpisodeLearningAction`; episodic/semantic/procedural services; B2-to-procedural bridge; R2 outcome policy trainer/activation boundary | Episode write is registered as internal action; semantic/procedural updates use governed services; retrieval/admission overlay is implemented but shadow-only | **Partial/adapted** | Provide an explicit, composable learning-action contract for semantic/procedural promotion if the action-space claim is intended to cover all memory types; complete and evaluate retrieval-policy learning; keep weight/code changes an explicit governed scope decision |
| Planning | Reasoning and retrieval form proposals and evaluate possible grounding or learning actions | M5 planners and candidate/evaluation services; `CognitiveCycleDecisionServices`; proposal, evaluation and selection edges in `CognitiveCycleGraph` | Several planner methods are registered and compared; `NEEDS_MORE_SEARCH` can return to proposal; infeasible candidates can enter B2 | **Strong core correspondence** | Ensure the B5/GraSP governor is integrated if claiming Fan & Lan's complete synthesis runtime; make B6 uncertainty values available to the actual planning algorithms, not only a reference |
| Execution | Selected grounding/learning action executes | intention governance, approval, `ActionDispatcher`, simulator, action batches/ledger and B6 budget guard | Human approval for state-changing operations; plan revalidation before dispatch; unknown external outcomes go to reconciliation rather than blind retry | **Strong adapted realization** | Full production host and live connector/restart tests remain outside the simulator-backed reference implementation |
| Observation and repeated decision cycles | Observe action outcome and begin another cycle; CoALA describes a continuously repeated main procedure | `observe-and-reconcile`, working-memory update, episode learning and graph paths back to `load-context` for reconsideration/evidence/recovery | The graph supports bounded planning/reconsideration and observation-driven return; successful terminal execution records an episode and ends the invocation | **Strong bounded-cycle realization** | Add an explicit host-level outer loop/event ingress that invokes the complete graph for subsequent observations/tasks; current `Phase0Application` runs the older simple intake graph, not the full cognitive graph |
| Modularity | CoALA modules/processes provide clean conceptual organization; no exact module count is prescribed | 11 Maven modules, dependency guards, framework-neutral domain/memory/action/mechanism/planning modules, runtime adapters | Three static architecture/evidence guards pass on this stack | **Strong architectural correspondence** | Maintain boundary checks; no framework choice or module count should be attributed to CoALA itself |

### CoALA paper-wide scope check beyond the central §4 model

| Paper part | Paper contribution | Implementation obligation for this application | Current relationship |
|---|---|---|---|
| §§1–3 | Motivation, production systems, cognitive architecture and connections to language models | Explain the conceptual lineage accurately; these sections do not prescribe additional runtime services | Research/background coverage, not a separate code checklist |
| §4 | CoALA framework: memory, grounding, retrieval, reasoning, learning and decision making | Implement the application-relevant memory/action/decision responsibilities | Mapped in the main CoALA table above; several learning/reuse paths remain partial |
| §5 | Case studies of language agents such as ReAct, Voyager, Generative Agents and Tree of Thoughts | Use as examples/comparators; do not claim to reproduce these agents wholesale | The project has related planner/memory patterns, not complete recreations of each case study |
| §6 | Actionable insights: modularity, explicit memory/action/decision design, structured reasoning, long-term memory beyond retrieval, and broader learning forms | Show modular components, typed reasoning/write-back and read/write memory paths; explicitly scope any omitted weight/code adaptation | Modularity and structured reasoning are strong; semantic/procedural learning action breadth and adaptive retrieval/decision learning remain partial |
| §§7–8 | Discussion, open questions and conclusion | Preserve limitations and avoid treating future directions as mandatory code components | Addressed as claim-boundary and scope notes in this matrix |

CoALA explicitly treats working memory as a data structure persisting across model calls, describes retrieval/reasoning/learning as different internal actions, and discusses episodic, semantic, parameter-level and code-level learning as a spectrum. Its §6 also recommends matching the memory and action design to the application. Therefore this application does not need autonomous fine-tuning or self-modifying source code to use CoALA as its organizing framework, but it must not claim those learning forms are implemented. (CoALA, §§4.1–4.6 and §6.)

### CoALA conclusion

The application is a strong CoALA-organized reference implementation. The memory model is particularly strong because the four memories have different types, truth conditions, admission rules, persistence semantics and retrieval behavior. The main remaining CoALA completeness issues are not the basic three-dimensional architecture; they are the breadth and integration of learning actions, explicit procedural-memory reuse, the implicit-weight/explicit-code scope decision, and the complete application-level outer loop.

CoALA's discussion of updating LLM parameters and agent code is part of its broader account of procedural learning, not a requirement that every application autonomously fine-tune a model or rewrite production code. For this application, the safe and defensible target is to keep model weights and source code externally governed while fully implementing explicit memory learning and procedure retrieval. This scope decision must be stated in the book rather than silently treating these forms as implemented. (CoALA, §4.5.)

---

## 5. Fan & Lan review-protocol and analytical-framework conformance

**Primary source:** Fan and Lan, *From Cognitive Architectures to Language Agents: A Mechanism-Level Review of Lineage, Convergence, and Migration Gaps*, arXiv:2607.23942v1. [Full paper.](https://arxiv.org/html/2607.23942v1)

| Paper contribution | Is it an application runtime feature? | Current project relationship | Assessment |
|---|---|---|---|
| Ten historical cognitive architectures and their distinctive mechanisms | No. They form the historical comparison corpus | The project selects ideas from ACT-R, Soar, LIDA, BDI/MIDCA, EPIC/Sigma as design references for residual bundles | **Analytical coverage, not ten software implementations** |
| Eight language-agent runtime families and 42 mechanism-focused modern systems | No. They form the bounded modern comparison corpus | The project has its own Java runtime and can compare itself with selected closest baselines | **Not a requirement to implement all reviewed systems** |
| Seven-field mechanism representation `S/C/T/P/F/L/R` | Analytical method, not a runtime DTO | Applied in this matrix and in the project's mechanism architecture documentation; not a single runtime tuple class | **Partially operationalized as traceability; no claim of runtime implementation** |
| E1–E4 evidence-relation coding | Analytical method | Project uses explicit paper-source mapping, but does not maintain a complete independently adjudicated E-code ledger for every atomic correspondence | **Needs formal evidence ledger if the book claims full review-method reproduction** |
| D0–D4 migration-depth coding | Analytical method | Mechanism controls can be mapped to D-level, but this matrix supplies provisional mappings and distinguishes active vs dormant D4 feedback | **Provisional, mechanism-specific recoding** |
| Six candidate bundles → five residuals + B5 closed | Research result | Project implements five residual bundles in part; no local GraSP/B5 skill governor | **Residual selection respected; synthesis-runtime dependency remains missing** |
| Cross-bundle dependencies and bounded synthesis runtime (§VI-D/§VI-E) | Architecture/research agenda with executable implications | B1/B2/B3/B4/B6 have graph wiring; no full GraSP-like skill governor; no production full-host composition root | **Partial synthesis runtime** |
| Falsifiable evaluations and compositional ablations | Research protocol | Unit/integration tests and small fixture ablations exist; no independent outcome/holdout corpus for adaptive learning | **Not empirically complete** |

### Important interpretation

The paper expressly says the five bundles are not independent plug-ins: B4 scopes B2 recovery; B1 and migrated skills provide candidates to B3; B6 governs verification, tool authorization, interruption and stopping; workspace evidence can trigger B4 reconsideration. It also states that a migrated B5 skill governor remains an implementation dependency of the synthesis runtime. These are the important composition constraints for this project, not a requirement to reimplement ACT-R, Soar, LIDA, BDI, MIDCA, ICARUS, EPIC and Sigma wholesale. (Fan & Lan, §§VI-D–VI-E.)

---

## 6. Fan & Lan residual mechanism conformance matrix

### 6.1 B1 — activation, retrieval latency, downstream utility and maintenance cost

**Paper target:** a runtime memory item carries activation, predicted retrieval latency, downstream value and maintenance cost. Context assembly performs bounded competition; observed use updates both accessibility and utility. (Fan & Lan, §VI-C, B1.)

| Field | Current code | Assessment and missing invariant |
|---|---|---|
| **S — State** | M2 memory records become `B1MemoryCandidate` values; feature provider supplies access history, cue associations, expected utility, context units, measured I/O latency and maintenance profile | Explicit candidate state exists. Several features are host-supplied; durable, complete access/cue history is not a first-class PostgreSQL B1 feature store |
| **C — Control** | `B1RetrievalSelector` scores, filters and admits candidates | Real selector authority, not merely a label |
| **T — Trigger** | Context/evidence assembly after exact M2 retrieval and before workspace admission | Integrated in the full decision service path |
| **P — Persistence** | M2 memory records have PostgreSQL adapters; `MemoryMaintenanceAccountingService` uses `MemoryMaintenanceLedger`, whose provided implementation is in-memory; feature history is supplied externally | Partial persistence for B1 feature and maintenance evidence |
| **F — Failure** | Scope validation, lifecycle validation, deterministic score calculation, candidate-count/context limits and minimum-accessibility/net-utility gates | Explicit local failure/bounding semantics; no full adaptive selector recovery policy |
| **L — Learning** | R2 can train a candidate-specific score adjustment from separately verified B3 receipts/outcomes and apply it through an authorized overlay. This adjustment changes net utility. It does **not** update the activation/accessibility model or the feature provider's access history. The overlay remains `SHADOW`/`DISABLED` because there is no verified outcome corpus/holdout set | **Defining coupling incomplete:** observed use must update both accessibility and utility. Current feedback addresses only candidate utility. No live D4 learning claim |
| **R — Resources** | Context-unit capacity, maximum candidates, predicted cognitive latency, measured infrastructure latency, maintenance estimate and bounded outcome adjustment | The resource dimensions are explicitly considered; feature quality depends on host data |

**Code:** `B1MemoryCandidateFactory`, `B1CandidateFeatureProvider`, `RecencyFrequencyActivationEstimator`, `CognitiveRetrievalLatencyModel`, `B1RetrievalSelector`, `MemoryMaintenanceAccountingService`, R2 feedback trainer/activation service.  
**Assessment:** **Partial realization.**  
**Provisional E/D:** **E3/D3 active** for the candidate-selection control: functionally comparable memory-admission control through a custom organization; the selector autonomously runs at context assembly. A D4 utility-adjustment capability exists behind verified evidence and separate activation, but it is dormant and does not complete B1 because accessibility is not updated.  
**Acceptance needed:** durable access/cue/use features; verified outcome attribution; update both activation/accessibility and downstream utility; demonstrate on independent holdout units that the coupled selector beats or justifies its cost against top-k and current adaptive-memory baselines without reducing relevant recall.

### 6.2 B2 — typed impasse, protected substate, typed return and resolution compilation

**Paper target:** classify an impasse, freeze the relevant parent state, open a bounded recovery subgraph, return a typed resolution and compile only the dependencies that made the resolution valid. (Fan & Lan, §VI-C, B2; Soar lineage in §III-A.)

| Field | Current code | Assessment and missing invariant |
|---|---|---|
| **S — State** | `ImpasseSignal`, `ImpasseResolutionCase`, `ParentContextSnapshot`, protected objective/scope/version references, child state, `ChildResolutionResult`, resolution findings and recovery drafts/artifacts | Strong explicit parent/child state model |
| **C — Control** | `DeterministicImpasseDetector`, `ImpasseResolutionService`, `B2ResolutionSubgraph`, parent-result validation and governed draft/promotion services | Actual trigger and child-resolution authority in graph; promotion requires separate actors |
| **T — Trigger** | Typed validator signals, unresolved planning choice and full-graph `NO_FEASIBLE_CANDIDATE`/impasse routing | Integrated into the full graph, including the F-01–F-04 stack |
| **P — Persistence** | PostgreSQL impasse case/draft repositories; V011 for drafts/evidence; F-03 immutable content-addressed artifact store and V012; procedural-memory versions | Durable component persistence exists. Default evaluation host uses in-memory procedural/artifact adapters; no production full-host assembly |
| **F — Failure** | Child capabilities are bounded by parent grant and allowlist; parent identity/objective/WM version rechecked; typed disposition; terminal case not reopened; scope/evidence/action descriptor freshness checks; bounded retries | Strong explicit failure and corruption controls |
| **L — Learning** | Approved draft may be staged as a CoALA procedural `DRAFT`; independent procedural review and activation are required; `retrieveActive` returns verified typed planning knowledge. Ordinary M2 retrieval/B1 candidate formation can expose an active procedure reference. However, source call-site inspection found no normal full-graph production call to `retrieveActive`; the dereference/reuse operation is exercised through service tests/host API rather than the planner's standard request construction | **Important remaining gap:** the actual future-cycle planner must receive the condition-matched typed procedure content through an explicit full-graph path. A service API plus a memory reference does not prove automated reuse. No repeated-failure/transfer evidence |
| **R — Resources** | Child action/read allowlist, parent capability subset, bounded action budget, deadline and bounded retry/promotion attempts | Explicit bounded recovery and governance |

**Code:** `DeterministicImpasseDetector`, `ImpasseResolutionCase`, `ParentContextSnapshot`, `ChildResolutionResult`, `ImpasseResolutionService`, `B2ResolutionSubgraph`, `RecoveryProcedureDraftService`, `RecoveryProcedurePromotionService`, `RecoveryProcedureArtifactCodec`, V011/V012 PostgreSQL adapters.  
**Assessment:** **Substantial adapted realization, still partial at compilation-to-reuse integration.**  
**Provisional E/D:** **E2/D3** for the typed parent/child impasse and return structure; **D2** for the reusable-procedure edge as presently connected to normal graph execution. The governed artifact is not Soar's exact chunking/production representation.  
**Acceptance needed:** invoke exact-scope `retrieveActive` as part of the normal planner context construction; supply the typed conditions/effects/action references to planner evaluation; revalidate before use; prove that the resulting plan still passes ordinary M3 authorization (procedure retrieval must never be an execution grant); demonstrate repeated-failure reduction on independent future cases and reject overgeneralized reuse.

### 6.3 B3 — typed content competition, bounded workspace, broadcast and admission learning

**Paper target:** typed candidates from memory, agents, tools, monitors and plans compete for a bounded shared workspace; a winning coalition is broadcast for one cycle; downstream utility changes future admission. (Fan & Lan, §VI-C, B3; LIDA comparison in §III-B.)

| Field | Current code | Assessment and missing invariant |
|---|---|---|
| **S — State** | `WorkspaceCandidate` kinds currently include `OBSERVATION`, `MEMORY`, `HYPOTHESIS`, `CONSTRAINT`, `CANDIDATE_PLAN`, `POLICY`; pair/triple `WorkspaceCoalitionProposal`, workspace snapshot, receipts, downstream outcomes and experiment-unit fingerprints | Typed state and explicit coalitions exist. Candidate types do not separately express tools, monitors and agents, which are explicit in the paper's proposal |
| **C — Control** | `WorkspaceAdmissionService`, `WorkspaceCoalitionCompetitionPolicy`, `WorkspaceSnapshot` validation and `WorkspaceBroadcastService` | Admission authority is explicit; mandatory items are protected; singleton and coalition alternatives are compared |
| **T — Trigger** | Pre-inference workspace admission; selected snapshot is synchronously broadcast to registered consumers for the cycle | Integrated before M5 proposal generation |
| **P — Persistence** | Workspace snapshots use `InMemoryWorkspaceRepository` in the full evaluation host and are cycle-bounded/ephemeral; B3 consumer receipts and separately verified outcomes have PostgreSQL repositories | Receipt/outcome durability exists; no durable workspace event/outbox/replay store is wired in the full host |
| **F — Failure** | Conflicts, mandatory capacity, insufficient coverage, deterministic proposal selection, hard maximum of 512 evaluated coalitions, singleton-only fallback if coalition enumeration is truncated; receipt identity/consumption checks | Strong bounded failure semantics; synchronous broadcast rather than durable async delivery |
| **L — Learning** | R2 receipt-linked verified outcomes can train candidate-specific utility adjustments for B1/B3. This changes later candidate base scores when an authorized overlay is active. Current outcome attribution is per candidate, not per winning coalition/proposal; the overlay remains disabled with no verified outcome corpus | **Defining B3 gap:** downstream consequences of the admitted coalition/broadcast must update future coalition admission. Current system adjusts candidate scores and recomputes deterministic coalition scores; it does not learn coalition-level interaction/synergy or broadcast utility from coalition-specific outcome attribution |
| **R — Resources** | Bounded workspace capacity, maximum candidate count, context units, mandatory-content priority, candidate utility/reliability/risk and coverage-value scoring; coalition search bound | Explicit bounded admission policy |

**Code:** `WorkspaceCandidate`, `WorkspaceAdmissionRequest`, `WorkspaceAdmissionService`, `WorkspaceCoalitionCompetitionPolicy`, `WorkspaceCoalitionProposal`, `WorkspaceCoalition`, `WorkspaceSnapshot`, `WorkspaceBroadcastService`, `B3OutcomeEvidenceService`, R2 feedback services.  
**Assessment:** **Substantial adapted structural realization; outcome-learning component is partial.**  
**Provisional E/D:** **E2/D3** for explicit bounded content/coalition competition and broadcast trigger. The R2 candidate-score feedback has a dormant D4-like update capability, but it is not coalition-outcome learning and is not active on evidence.  
**Acceptance needed:** represent agent/tool/monitor candidates where the runtime needs them; bind outcomes to the exact coalition proposal and broadcast recipients; train/adapt coalition-level admission from verified downstream utility; persist/replay workspace/receipt lifecycles if restart/resume is part of the runtime target; compare against top-k and a Global Workspace Agent-like selector on matched experiments; prove that decisive/rare evidence is not suppressed.

### 6.4 B4 — persistent intention, reconsideration and method authority

**Paper target:** a persistent intention stores committed goal, rationale, plan, continuation condition, reconsideration triggers, authority and termination status. A governor may continue, delegate, suspend, abandon or change method. (Fan & Lan, §VI-C, B4; BDI/MIDCA comparison in §III-C.)

| Field | Current code | Assessment and missing invariant |
|---|---|---|
| **S — State** | `RecoveryIntention`: ID, scope, objective ID/fingerprint/owner, protected constraints, current method reference, status, version/timestamps, reason, resume state, delegation ID and completion proof | Objective and method are durable. The record does not separately encode all proposed fields: a full plan object, continuation condition, typed reconsideration-trigger policy, and explicit authority field are not present as first-class intention data |
| **C — Control** | `RecoveryIntentionService`, `CognitiveCycleAuthorityPort`, `PlannerMethodResolver`, `PlannerSelectionOverride`, `PlannerRouter` | F-01 makes method authority live: selected registered planner follows the persisted intention binding, not a model proposal; objective fingerprint and intention version bind the override |
| **T — Trigger** | Explicit reconsideration, delegation return, suspension/resume, completion/termination; method switch only after reconsideration and matching objective fingerprint | Actual transition path reaches the next planner invocation |
| **P — Persistence** | In-memory and PostgreSQL intention repositories with compare-and-set/version history | Durable persistence for the intention state |
| **F — Failure** | Protected objective hash, version checks, missing/unknown method rejection, invalid transition rejection, planner session invalidation on changed method/objective binding, completion proof verification | Strong objective-preservation semantics |
| **L — Learning** | Method change alters the next invocation's planner choice. No outcome-trained method preference policy is claimed | B4 requires live method authority; it does not require autonomous online learning of method choices. However, effectiveness of reconsideration is not experimentally established |
| **R — Resources/governance** | Delegation, suspension, actor/reason termination, explicit approval/authority boundary, bounded reconsideration rounds | Strong application governance; fuller authority/continuation schema remains |

**Code:** `RecoveryIntention`, `RecoveryIntentionService`, `RecoveryMethodReference`, `ObjectiveFingerprint`, `PlannerMethodResolver`, `PlannerSelectionOverride`, planner routing and full-graph F-01 tests.  
**Assessment:** **Strong adapted realization, but not a complete copy of the proposed intention record/governor.**  
**Provisional E/D:** **E2/D3** for durable intention state and trigger-driven planner-method control. No E1 historical inheritance claim is made; it is an application-level structural transfer.  
**Acceptance needed:** add explicit continuation conditions, structured reconsideration triggers and rationale; make the plan/trajectory and authority semantics explicit; test stale commitments, wrong-method selection, oscillation, premature stopping, objective drift and the full decision set; add controlled evaluations against a no-B4 baseline.

### 6.5 B5 — skill governance closed by GraSP

**Paper disposition:** Fan & Lan's adversarial closest-baseline screening closes B5 through GraSP. GraSP combines calibrated confidence routing between a typed skill DAG and ReAct, node pre/postcondition verification, five bounded typed repair operators, and escalation to global replanning or ReAct. The paper characterizes this as an E3/D4 convergence case and removes it from the residual intervention count. (Fan & Lan, §VI-C, B5.)

| Question | Current project state |
|---|---|
| Is B5 an additional residual gap like B1/B2/B3/B4/B6? | **No.** The paper closes it as already represented by GraSP in the reviewed corpus. |
| Does this repository implement GraSP or an equivalent typed skill governor? | **No.** Search of active Java production sources found no typed skill DAG, GraSP router, node pre/postcondition lifecycle, five repair operators, or ReAct fallback subsystem. |
| Does §VI-E synthesis runtime reuse a GraSP-like skill governor? | **Yes.** Fan & Lan explicitly treat a migrated B5 skill governor as an implementation dependency. |
| What is needed if the project claims the complete §VI-E synthesis runtime? | Integrate a GraSP-like governor or declare and test a versioned external dependency that supplies the full routing/verification/repair/fallback contract. |

**Assessment:** **Closed candidate in the paper; absent local implementation; unresolved synthesis-runtime dependency.** Do not describe B5 as a sixth residual hypothesis, and do not claim the entire Fan & Lan synthesis runtime until this dependency is met.

### 6.6 B6 — calibrated uncertainty, resource allocation, interruption and stopping

**Paper target:** one calibrated uncertainty object accompanies consequential proposals and propagates through memory admission, planning, tool authorization, execution monitoring and stopping. Resource policy allocates bounded compute/verification/action resources and supports interruption/stopping. (Fan & Lan, §VI-C, B6.)

| Field | Current code | Assessment and missing invariant |
|---|---|---|
| **S — State** | `UncertaintyAssessment` with `EVIDENCE`, `HYPOTHESIS`, `PLAN` signals, provenance, scope/execution/version and calibration metadata; `ResourceBudget`, `ResourceBudgetSnapshot`, `ResourceUsage`, planner local budget and durable reservation state | Explicit uncertainty and resource objects. The calibration type supports `CALIBRATED`, but active source evidence identifies runtime assessments as `NOT_CALIBRATED` |
| **C — Control** | `MetaControlGovernor`, `ResourceBudgetExecutionGuard`, `LeasedResourceBudgetLedger`, B6 reservation/recovery services, full-graph state and planner downshift | Explicit governor and shared resource authority; F-02 causes downshift to affect the next `PlanningRequest` |
| **T — Trigger** | Next-step resource requirement, remaining budget fraction, actual overrun, deadline, and configured maximum uncertainty thresholds | Triggered control transitions operate in graph, not only as metrics |
| **P — Persistence** | PostgreSQL durable resource budget ledger, lease ownership/fencing and migrations V009/V010; assessment store is host-owned and not established as a calibrated production dataset | Strong resource persistence; uncertainty calibration/evidence persistence is incomplete |
| **F — Failure** | `STOP`, `REQUEST_EVIDENCE`, `INTERRUPT_FOR_HUMAN`, `REDUCE_COMPUTATION`; typed planner downshift is applied cumulatively to consumable planner resources; the execution-wide shared resource ceiling remains unchanged | Strong local resource/failure controls. No general scheduler for concurrent tasks, process preemption or tool-level interruption is claimed |
| **L — Learning** | No calibration-update or outcome-trained uncertainty policy; no future meta-control policy update from verified outcomes | Missing learning operator for calibrated uncertainty/resource adaptation |
| **R — Resources/governance** | Resource vector (search expansions, reasoning evaluations, model calls, MCTS rollouts and related limits), shared guard, leases/fences, hard deadline, planner-local budget scale and human approval boundaries | Strong planner/action resource guard; resource allocation does not yet cover every scheduler/tool/verification cost dimension |

**Critical propagation detail:** the graph carries a versioned uncertainty **reference** through workspace/planning/authorization boundaries and rechecks currency. That proves identity and freshness binding, not that the actual calibrated uncertainty values affect every stage. In the normal ordering, B1 retrieval selection occurs before the uncertainty-assessment node. `WorkspaceAdmissionRequest` and `PlanningRequest` carry the assessment reference rather than the uncertainty channels as actionable scores; workspace admission does not incorporate them into coalition scoring; M5 planners do not receive a calibrated uncertainty vector as an active search/risk control; the meta-governor later consumes the assessment's maximum uncertainty for its local policy. Therefore B6 has meaningful reference propagation and a working resource downshift, but **not the paper's calibrated cross-stage value propagation**.

**Code:** `UncertaintyAssessment`, `UncertaintySignal`, `MetaControlGovernor`, `PlannerBudgetDownshift`, `ResourceBudget`, `ResourceBudgetExecutionGuard`, PostgreSQL ledger and leases, F-02 graph/planning request changes.  
**Assessment:** **Substantial adapted resource-control realization; uncertainty integration remains partial.**  
**Provisional E/D:** **E3/D3** for the resource-governance and interruption control: a functionally comparable solution through Java budget ledgers and a meta-governor, with triggers that change planning/authorization behavior. No D4 uncertainty-learning claim.  
**Acceptance needed:** create and validate task-specific calibration profiles on an independent labelled dataset; carry the calibrated value vector—not just the reference—into pre-retrieval/context selection, B3 coalition admission, M5 strategy/search allocation, M3 tool authorization, execution monitoring and stopping; evaluate calibration, selective risk, resource use, interruptions and task outcomes; include verification costs and any intended parallel scheduling/preemption in the resource policy.

---

## 7. Cross-bundle synthesis and full-graph integration

Fan & Lan's §VI-D/§VI-E dependency structure is the most demanding test of the current implementation. It is not enough that each service has a unit test. The full agent must pass the correct authoritative state and decision across component boundaries.

| Required dependency | Current implementation | Remaining integration gap |
|---|---|---|
| B4 scopes B2 recovery to the committed parent objective | B2 child context receives parent objective/scope/working-memory identity and checks result against the protected snapshot; B4 objective fingerprint is carried and checked | Complete intention schema/trigger policy remains; add adversarial tests across long-running parent/child recovery and restart |
| B2 produces reusable resolutions for skill governance | F-03 stages approved drafts into CoALA procedural memory, independently reviewed/activated; V012 stores immutable artifact payloads | Normal full-graph planning does not call `retrieveActive` to hydrate condition-matched reusable procedure content; B5/GraSP governor is absent |
| B1 and migrated skills provide candidates to B3 | B1 candidates are derived from M2 retrieval; B3 admits typed candidates and compares singleton/pair/triple options | No GraSP skill candidates; B1 accessibility learning is incomplete; B3 outcome adjustment is per-candidate rather than coalition-level |
| B3 decides what becomes globally available for a cycle | Bounded workspace snapshot and synchronous consumer broadcast; receipts/outcomes are persisted and validated | Workspace snapshot itself is ephemeral/in-memory; no durable outbox/replay; outcome corpus remains empty and feedback shadow-only |
| Workspace evidence can trigger B4 reconsideration | Graph supports observation/evidence/reconsideration routes into context loading and intention governance | Need controlled experiments proving this dependency improves goal retention or avoids method oscillation |
| B6 governs verification, tool authorization, interruption and stopping across bundles | Shared resource guard, exact uncertainty reference checks, planner downshift, approval and stop routes | Uncertainty values are not calibrated and do not drive all relevant stages; resource vector is not a complete cross-service scheduler |
| Durable runtime state includes intention, workspace, memory, skills, uncertainty, budgets and recovery substate | Intention, memory, budgets, cases, action ledgers and B2 artifacts have persistence adapters; checkpointing exists | Workspace is ephemeral; active procedure payload reuse is not in the graph; uncertainty data store/calibration not complete; no GraSP skill state; default full evaluation host composes many in-memory adapters |

### 7.1 Full graph vs production host

`CognitiveCycleApplicationGraph.buildFull/compileFull` is a real composition entry point, and the evaluation host composes the full cognitive graph. The `Phase0Application`, however, still runs the older `SupplyChainIntakeGraph`; it is a local deterministic smoke application, not the complete CoALA/Fan & Lan host. The full evaluation host uses in-memory workspace, procedural memory and recovery-artifact stores by default, even though PostgreSQL adapters exist for selected components. The application module does not yet provide a complete, durable, production-style bootstrap that wires every full-graph port to the PostgreSQL stores and a durable checkpoint saver.

This is not a reason to reject the educational architecture. It is a reason not to claim that a fully composed durable production runtime has already been delivered.

### 7.2 Continuous cycle boundary

`CognitiveCycleGraph` has internal loops for clarification, evidence requests, planning retries, B2 recovery, B4 reconsideration, B6 downshift and observation-driven re-entry. A completed objective records an episode and terminates that graph invocation; new top-level perceptual input is expected to arrive through another host invocation. CoALA describes a continuously repeated main procedure. A caller-driven invocation model can implement that pattern, but the currently supplied application entry point does not provide a full always-running perception/decision host. The outer-loop contract should be made explicit and tested before claiming complete cycle hosting.

---

## 8. Empirical evidence: what green CI does and does not establish

### 8.1 What is established

- The project owner reports the combined Java 21/PostgreSQL Maven CI is green after F-01/F-02 and F-03/F-04.
- The full graph has tests for B1/B2/B3 integration, B4 method selection, B6 budget downshift, B2 approved-draft governance, B3 coalition selection/fallback, R2 evidence binding and PostgreSQL migration/repository behavior.
- Three source-level architecture/evidence guards independently pass on the reconstructed combined tree.

### 8.2 What is not established

- **B1/B3 learning benefit:** `M8R2OutcomeFeedbackEvidenceCli` explicitly records `NO_VERIFIED_OUTCOME_CORPUS`, zero holdout scenarios, candidate `SHADOW`, activation `DISABLED` and baseline preserved. Tests of the trainer/activation protocol use test data; they are not empirical evidence of improvement.
- **B6 calibration:** the code can represent a calibrated profile, but there is no validated production calibration profile/sample set in the current runtime evidence. Current status remains `NOT_CALIBRATED`.
- **B2 transfer:** service tests show draft promotion, governance and retrieval semantics; they do not show the normal full graph automatically retrieves and uses the procedure on independent future cases, nor that repeated failures decrease.
- **B3 coalition superiority:** deterministic pair/triple competition and bounded fallback are unit-tested. There is no independent experiment showing improved performance over top-k or Global Workspace Agent-like baselines, and there is no coalition-attributed outcome training.
- **B4 effectiveness:** the method-change override is tested as a control transition; it has not been shown to improve long-horizon outcomes against a no-B4 baseline.
- **Composed runtime:** successful compilation and unit/integration tests do not establish that the composed five-bundle runtime outperforms simpler controls or that the historical mechanisms have been fully migrated.

### 8.3 F-01–F-04 closure on the current source stack

The project owner confirmed that F-01, F-02, F-03 and F-04 were applied cumulatively and the combined hosted CI is green. Their implementation scope is now:

- **F-01:** persisted B4 intention method authority is bound to the protected objective fingerprint and controls the next registered planner invocation; unknown/stale method overrides fail closed.
- **F-02:** B6 `REDUCE_COMPUTATION` carries a typed planner-budget downshift into the next planning request while retaining the execution-wide hard resource ceiling.
- **F-03:** approved B2 drafts may be staged into CoALA procedural memory as immutable content-addressed artifacts, with independent validation/activation and PostgreSQL V012 storage. The active-artifact dereference API is present, but no normal full-graph production call invokes it to hydrate future planning context.
- **F-04:** B3 admission evaluates singleton and bounded pair/triple coalitions as first-class alternatives. Truncated enumeration falls back to singleton selection; coalition-level outcome learning is not yet implemented.

Green CI closes these four code-change/test gates. It does not close the remaining conformance gaps listed in §§6–10.

Fan & Lan explicitly frame the five bundles as falsifiable hypotheses and note that the paper's architecture/review results do not establish cross-domain causal value when composed. A green CI is therefore necessary but not sufficient for the book's strongest claims. (Fan & Lan, §§VI-E–VII.)

---

## 9. Final claim policy for the project and book

### 9.1 Claims that are safe now

1. “The Supply-Chain Agent is organized around the central CoALA memory, action and decision-process framework.”
2. “The implementation provides explicit, integrated Java realizations of five control bundles examined by Fan & Lan: B1, B2, B3, B4 and B6.”
3. “F-01/F-02 make B4 method authority and B6 planner downshift affect the next planning invocation.”
4. “F-03 provides a governed bridge for approved B2 recovery drafts into CoALA procedural memory; the artifact retrieval API exists, but normal full-graph planning does not yet invoke it to hydrate condition-matched procedure knowledge.”
5. “F-04 implements bounded competition between singleton content and pair/triple workspace coalitions with traceable deterministic fallback; it is not a full LIDA reproduction.”
6. “R2 includes a guarded outcome-feedback training/holdout/activation protocol, but it remains shadow-only because there is no verified outcome corpus or independent holdout data.”
7. “B5 is excluded as a residual hypothesis because Fan & Lan close it with GraSP; a GraSP-like governor remains a dependency if the project's target includes the full §VI-E synthesis runtime.”

### 9.2 Claims that are not safe yet

- “The code completely implements CoALA.”
- “The code completely implements Fan & Lan.”
- “All five residual bundles are complete / fully migrated / D4.”
- “The B2 recovery procedure is automatically reused in subsequent full-graph planning.”
- “B3 learns which coalitions are useful from verified downstream outcomes.”
- “B1 updates both memory accessibility and utility from observed use.”
- “B6 propagates calibrated uncertainty through every stage.”
- “The application implements GraSP/B5 or the complete bounded synthesis runtime.”
- “The adaptive feedback improves planner/retrieval performance.”
- “The full application is a production deployment with real supply-chain integrations.”

### 9.3 Recommended book wording

> The Supply-Chain Agent is a Java reference implementation organized around the central CoALA framework. It implements distinct working, episodic, semantic and procedural memory responsibilities; internal and external action paths; and an iterative decision procedure with bounded planning, governed execution and outcome handling. It also provides integrated but application-adapted realizations of Fan and Lan's B1, B2, B3, B4 and B6 residual control bundles. The implementation is not presented as a literal reproduction of the historical architectures, a complete realization of every proposed learning coupling, or an implementation of GraSP. Where the Fan and Lan synthesis runtime depends on a migrated GraSP-like skill governor, that dependency is identified explicitly. Adaptive-learning and uncertainty-calibration claims are limited to the evidence actually available.

---

## 10. Acceptance rule before the book can claim complete implementation

The project should not label the implementation “complete” until all blockers in `FINAL-IMPLEMENTATION-GAPS-AND-ACCEPTANCE-PLAN.md` are either implemented and validated or explicitly approved as out-of-scope with a narrower claim. At minimum, that means:

1. B1 updates accessibility and utility from verified use and persists its feature history.
2. B2 procedures are condition-matched, dereferenced and supplied to normal future planner requests, with safe revalidation and transfer evidence.
3. B3 outcome attribution includes the selected coalition and its broadcast consequences; future admission changes from verified outcomes.
4. B4 has the full explicit intention/continuation/reconsideration/authority contract and its trigger decisions remain live through the graph.
5. B6 has validated calibration and operational value propagation into memory admission, workspace, planning, tool authorization, execution monitoring and stopping.
6. A GraSP-like B5 governor is supplied or the §VI-E synthesis-runtime claim is explicitly excluded.
7. A durable full-graph application host and repeat-cycle ingress are provided if the book claims a complete runnable application.
8. Controlled, matched experiments test each bundle and the composition; no performance claim is made from CI alone.

Until then the implementation is a strong and useful reference system, but **not total paper conformance**. The owner has explicitly said writing must wait until satisfied; this matrix supports that gate rather than bypassing it.

---

## 11. Primary sources

1. Sumers, T. R., Yao, S., Narasimhan, K., and Griffiths, T. L. (2024). *Cognitive Architectures for Language Agents*. arXiv:2309.02427v3. https://arxiv.org/abs/2309.02427 (especially §§4.1–4.6).
2. Fan, H., and Lan, Z. (2026). *From Cognitive Architectures to Language Agents: A Mechanism-Level Review of Lineage, Convergence, and Migration Gaps*. arXiv:2607.23942v1. https://arxiv.org/abs/2607.23942 (especially §§II-C–II-E, III, IV, V, VI-A–VI-E and VII).

### Project source records

- `docs/architecture/COALA-MEMORY-ARCHITECTURE.md`
- `docs/architecture/COALA-ACTION-ARCHITECTURE.md`
- `docs/architecture/FAN_LAN-MECHANISM-ARCHITECTURE.md`
- `docs/architecture/PLANNING-ARCHITECTURE.md`
- `docs/architecture/COGNITIVE-CYCLE-DECISION-SERVICES.md`
- `docs/architecture/COGNITIVE-CYCLE-LIFECYCLE-SERVICES.md`
- `docs/phase8/M8-STATUS.md` (status corrected by this documentation changeset; see note in the status history)
- Production/test modules and PostgreSQL migrations V001–V012 in the reconstructed source baseline.
