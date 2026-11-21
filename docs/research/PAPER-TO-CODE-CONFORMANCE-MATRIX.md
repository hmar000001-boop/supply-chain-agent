# Final CoALA and Fan & Lan Paper-to-Code Conformance Matrix

**Project:** Supply-Chain Disruption Management Agent  
**Assessment revision:** First-five GAP-01–GAP-05 implementation candidate, layered over the owner-confirmed F-01–F-04 green stack  
**Assessment date:** 2026-11-21 (GAP-01–GAP-05 candidate revision)  
**Scope:** CoALA conceptual-framework implementation; Fan & Lan review-method coverage; B1, B2, B3, B4, B5 and B6 control bundles; cross-bundle synthesis and evaluation evidence  
**Change class:** Documentation only. No Java source, tests, POMs, SQL migrations, scripts or CI workflow logic are changed by this matrix package.

> **Primary conclusion:** The first-five implementation increment now supplies durable B1 verified-use/accessibility and utility features; normal future-cycle B2 procedure retrieval into M5 planning; receipt- and coalition-attributed B3 outcome learning with shadow/holdout activation gates; a fuller durable B4 intention/continuation/authority contract; and a target-specific B6 calibration lifecycle with actionable controls at selected decision boundaries. The code paths and targeted tests are present. This is **implementation-level completion of GAP-01–GAP-05 in the selected application scope**, not empirical completion: the real runtime remains `NOT_CALIBRATED`, B1/B3 adaptive policies remain inactive without verified outcome/holdout data, and independent matched experiments/transfer evaluations are still open.
>
> **Total paper conformance is still not claimed.** CoALA's conceptual framework is strongly implemented, but the application still has explicit learning-action scope and durable full-host gaps. Fan & Lan's five residual bundles are implemented as application-adapted control paths, but the review's bounded synthesis runtime still depends on a GraSP-like skill-governance component (B5 is closed as a residual, not something to re-label as a sixth residual). See GAP-06–GAP-10 and the acceptance evidence below.

This matrix is an acceptance record, not authorization to start the book chapters. The owner has required that writing remain blocked until the implementation and conformance claims are accepted.

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
| CoALA working/episodic/semantic/procedural memory | Strong, with governed application-specific lifecycles and active B2 recovery-procedure reuse in subsequent planning requests | **Strong core architecture; learning-action coverage and durable full-host composition remain** |
| CoALA internal/external actions | Substantive; grounding is simulator-backed | **No** — the learning action surface is narrower and no live external system adapters are present |
| CoALA iterative decision procedure | Full bounded graph with proposal/evaluation/retry, authority, execution and observation | **Strong bounded-cycle realization** — the full continuously running host is not supplied |
| Fan & Lan B1 | Durable selection/access/verified-use history and utility/cue updates from exact verified outcomes; guarded training/activation | **Implementation path present; live learning/effectiveness pending an independently verified corpus** |
| Fan & Lan B2 | Typed recovery, governed procedure staging, condition/evidence/action-descriptor revalidation, retrieval in subsequent planning cycles | **Adapted reuse path implemented; transfer/repeat-failure effect still untested empirically** |
| Fan & Lan B3 | Bounded workspace, pair/triple competition, synchronous broadcast, coalition-linked verified outcomes and shadow policy trainer | **Coalition learning path implemented; active policy/effectiveness pending independent evidence and approval** |
| Fan & Lan B4 | Persisted rationale, active plan reference, continuation condition, typed reconsideration triggers, authority policy, termination semantics and F-01 method routing | **Strong adapted control implementation; long-horizon benefit not yet empirically established** |
| Fan & Lan B5 | Correctly excluded from the residual list; no local GraSP implementation | **No** for §VI-E synthesis-runtime completeness; it must be supplied as dependency or subsystem |
| Fan & Lan B6 | Shared resource guard/downshift plus target-specific train/validate/approve calibration and actionable directives at B1, B3, M5 review and plan revalidation | **Calibration machinery implemented; active runtime remains NOT_CALIBRATED and coverage is not universal** |
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
| GAP-01–GAP-05 | `M8_PREB7_GAP01_GAP05_B1_TO_B6_LEARNING_AND_CALIBRATION_IMPLEMENTATION_CHANGESET_ONLY_2026-11-21.zip` and `SUPPLY_CHAIN_AGENT_GAP01_GAP05_CANDIDATE_FROZEN_SOURCE_2026-11-21.zip` | SHA-256 is supplied in the adjacent delivery sidecars; the matrix does not embed a self-referential archive digest |

The changesets are repository-root-relative. The current candidate is the base archive plus F-01/F-02, F-03/F-04 and GAP-01–GAP-05 source replacements applied in that order. A Git commit SHA and hosted workflow-run URL for the first-five candidate were not supplied; the frozen source archive, per-file source manifest, tree fingerprint and sidecar checksums are the source identity for this candidate until it is committed and passes hosted CI.

### 2.2 Static inventory of the current candidate tree

| Inventory item | Count | Interpretation |
|---|---:|---|
| Maven submodules | 11 | Domain, memory, core, simulator, actions, mechanisms, planning, runtime, PostgreSQL infrastructure, app and evaluation |
| POM files | 12 | Parent POM plus 11 module POMs |
| Production Java files under Maven modules | 495 | Module production source inventory |
| Test Java files under Maven modules | 74 | Test source inventory; not a Maven/Surefire executed-test count |
| Additional root `src/` Java files | 2 | Legacy shadow copies outside Maven module source roots; not compiled by the reactor and retained as a source-hygiene item |
| Total Java files in tree | 571 | 569 module-source files plus two uncompiled root-level shadows |
| PostgreSQL migration chain | V001–V015 | V001 resides in the memory module; V002–V015 reside in PostgreSQL infrastructure |

The two root-level Java files are `src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java` and the corresponding test under `src/test/java/...`. They are not in any Maven module and are not included in module compilation/test inventory. They are disclosed rather than silently counted as compiled source. Remove or formally archive these shadow copies before declaring repository hygiene complete.

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
| Procedural memory | Implicit procedural knowledge in LLM weights and explicit procedures/code | `ProcedureVersion`, `ProcedureArtifactReference`, `ProceduralMemoryService`, B2 recovery artifacts, F-03 promotion service, V012 and `CognitiveCycleDecisionServices.retrieveActive` | Immutable artifact references and lifecycle `DRAFT → VALIDATED → ACTIVE → RETIRED`; approved B2 drafts can be independently activated, condition-matched and supplied as typed knowledge to later M5 requests | **Strong adapted explicit-procedure governance and future-cycle reuse; weights/code remain out of scope** | Evaluate future-case transfer and document model-weight learning/self-modifying code as excluded or separately governed; M3 action authorization remains independent |
| Grounding actions | External actions interact with the environment through grounding | `SupplyChainPerceptionActions`, `SupplyChainGroundingActions`, action descriptors, `ActionDispatcher`, `CapabilityAuthorizer`, idempotency and resource guards | Action plan is a proposal; approval, revalidation, capability checks and dispatch are separate; simulator is the environment | **Adapted realization** | Add real-world adapter only if the deployment target requires it; for the book, clearly label the simulator as the grounding environment and do not claim live ERP/WMS/supplier/carrier execution |
| Retrieval actions | Read from long-term memory into working memory; rule-based/sparse/dense or recency/importance/relevance methods are examples | `MemoryRetrievalAction`, `MemoryRetrievalService`, `B1MemoryCandidateFactory`, `B1RetrievalSelector`, `B1MemoryFeatureLearningService`, active B2 procedure retrieval, evidence-set/workspace paths and R1A projection | Exact lifecycle-aware retrieval is separated from B1 scoring; durable access and verified-use histories plus cue/utility outcomes enrich subsequent candidates; active B2 procedures enter subsequent `PlanningRequest` as typed knowledge | **Integrated adapted realization; adaptive benefit remains evidence-gated** | Keep independent outcome/holdout evaluation; semantic learning-action breadth and full durable host remain GAP-07/08 |
| Reasoning actions | LLM reasoning reads and writes working memory to generate new information | `ReasoningAction`, `ReasoningProvider`, `LangChain4jReasoningProvider`, `BudgetedReasoningProvider`, separate `WorkingMemoryUpdateAction` | Typed findings, assumptions, hypotheses, evidence references and assessments; write-back is an explicit versioned action | **Adapted realization** | Add end-to-end tests that a reasoning result can be admitted as a typed working-memory update and later consumed; no need to persist private model chain-of-thought |
| Learning actions | Write to long-term memory: episodic experience, semantic knowledge, implicit procedural parameters and explicit procedures/code; potentially learn retrieval/decision procedures | `EpisodeLearningAction`; episodic/semantic/procedural services; B2 governed promotion/retrieval; B1 verified-use/utility updates; B3 coalition outcome trainer/activation | Episodic write is registered; B2 procedures are governed; verified B3 outcomes can update future B1/B3 policy state behind independent evidence/activation gates | **Substantial adapted learning paths; full CoALA learning-action surface remains scoped** | Add/expose semantic/procedural learning actions where required; keep model-weight/code updates explicitly out of scope; independent learning/effectiveness evidence remains GAP-09 |
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

The status below distinguishes **mechanism implementation** from **empirical activation/effectiveness**. The first-five increment closes the planned GAP-01–GAP-05 source/control-path work in the selected application scope. It does not create real outcome data, a calibrated profile, or a causal performance result.

### 6.1 B1 — activation, retrieval latency, downstream utility and maintenance cost

**Paper target:** a runtime memory item carries activation, predicted retrieval latency, downstream value and maintenance cost. Context assembly performs bounded competition; observed use updates accessibility and utility. (Fan & Lan, §VI-C, B1.)

| Field | Current code | Assessment and boundary |
|---|---|---|
| **S — State** | Exact `B1MemoryCandidateReference` binds a feature row to memory kind/scope/item ID/item version. `B1MemoryFeatureState` stores retrieval access history, verified downstream-use history, cue associations, expected utility, outcome counters, idempotency keys and CAS revision. | Durable feature state is version-specific; old/new memory revisions do not share learned values. |
| **C — Control** | `B1MemoryFeatureLearningService` enriches candidates before `B1RetrievalSelector`; only `VerifiedDownstreamOutcomeEvidence` minted from B3 receipt/outcome verification can update the feature row. | The update path is verifier-gated; a model assertion alone cannot write outcome features. |
| **T — Trigger** | Selected candidate access records an idempotent event; a verified downstream outcome records a separate verified-use event and reward/cue update. Both occur in the normal decision-service path when their inputs are available. | Selection is distinguished from downstream consumption. Checkpoint replay is idempotent. |
| **P — Persistence** | `PostgresB1MemoryFeatureRepository` persists the complete record through a scoped CAS row; V014 installs the table. In-memory repository is available for tests. | History is bounded to 64 entries; feature updates are limited to exact memory revisions. |
| **F — Failure** | Scope mismatch, malformed reference, stale revision, duplicate event, time regression and repeated CAS contention are rejected or retried within a bounded limit. | Fail-closed feature attribution; no cross-scope training. |
| **L — Learning** | Retrieval access and verified-use timestamps feed the next candidate's activation/accessibility input; verified useful/harmful/neutral outcomes update expected downstream utility and cue-association strength with bounded updates. | **Implementation path complete; runtime learning remains inactive or unproven without real verified outcomes and an authorized active feedback policy.** Tests prove the state transition and persistence contract, not retrieval improvement. |
| **R — Resources** | Candidate count/context budget, retrieval latency, infrastructure latency, maintenance cost and bounded feature history constrain selection. | Feature values and limits are explicit engineering choices, not equations prescribed by Fan & Lan. |

**Code:** `B1MemoryCandidateReference`, `B1MemoryFeatureState`, `B1MemoryFeatureLearningService`, `PostgresB1MemoryFeatureRepository`, `B1RetrievalSelector`, `CognitiveCycleDecisionServices`, V014, `B1MemoryFeatureLearningServiceTest`, B3 outcome verification path.  
**Assessment:** **Integrated adapted implementation; empirical adaptive benefit pending.**  
**Provisional E/D:** **E3/D3 for the active selection control.** A verified outcome can support a D4-like policy update only when evidence and activation gates permit it; no D4 performance claim is made for the current runtime configuration.  
**Still required:** verified outcome corpus, independent holdout, activation approval and matched comparison against top-k/adaptive retrieval baselines for recall, precision, utility, latency, cost and harmful-memory suppression.

### 6.2 B2 — typed impasse, protected substate, typed return and resolution compilation

**Paper target:** classify an impasse, freeze the relevant parent state, open a bounded recovery subgraph, return a typed resolution and compile only the dependencies that made the resolution valid. (Fan & Lan, §VI-C, B2; Soar lineage in §III-A.)

| Field | Current code | Assessment and boundary |
|---|---|---|
| **S — State** | Typed impasse/case, protected `ParentContextSnapshot`, child state, typed result, resolution findings, immutable recovery artifact and active procedural-memory version; objective/scope/working-memory and action descriptor fingerprints are bound. | Explicit parent/child state and reusable procedure knowledge are represented separately. |
| **C — Control** | `DeterministicImpasseDetector`, `ImpasseResolutionService`, `B2ResolutionSubgraph`, `RecoveryProcedureDraftService`, `RecoveryProcedurePromotionService` and `CognitiveCycleDecisionServices`. | Recovery draft approval, procedural review and activation remain separate governance decisions. |
| **T — Trigger** | Typed impasse/validator signal and `NO_FEASIBLE_CANDIDATE` route to B2. In a later planning cycle, the condition resolver supplies trusted keys and `retrieveActive` is called during planning-input construction. | Future-cycle use is on the standard decision path, not merely an exposed service method. |
| **P — Persistence** | B2 cases/drafts/evidence use PostgreSQL repositories; V011 stores draft/evidence state; V012 stores immutable content-addressed recovery artifact payloads; procedural-memory versions persist separately. | Active procedure content is revalidated when retrieved. The default full evaluation host still uses in-memory adapters for selected stores; durable application-wide composition remains open. |
| **F — Failure** | Parent objective/scope/WM version, condition key, evidence freshness and exact registered action descriptors are rechecked. Stale/mismatched content is rejected. Retrieved procedure knowledge does not grant capability or bypass M3 authorization. | Bounded, typed and evidence-scoped reuse. |
| **L — Learning** | An approved B2 draft is staged as procedural-memory DRAFT, independently reviewed/activated, then condition-matched and revalidated in a later cycle. Typed evidence/provenance and descriptor references enter `PlanningRequest`; sequential, ToT and MCTS planning use them as soft search priorities. | **Future-cycle knowledge reuse implemented.** This is not literal Soar chunking into production rules: it is a governed, immutable, application-specific procedure artifact and planning bias. Transfer/repeated-failure reduction remains empirically untested. |
| **R — Resources** | Child action/read allowlist, parent capability subset, bounded action budget/deadline, retries, artifact size and descriptor freshness constraints. | No capability grant is inherited from retrieved procedural memory. |

**Code:** `B2ResolutionSubgraph`, `RecoveryProcedureDraftService`, `RecoveryProcedurePromotionService`, `RecoveryProcedureArtifactCodec`, `ProceduralMemoryService`, `CognitiveCycleDecisionServices`, `PlanningRequest`, planner rankers, V011/V012, `ReusableRecoveryProcedurePlanningTest`.  
**Assessment:** **Substantial integrated adapted implementation with next-cycle reuse.**  
**Provisional E/D:** **E2/D3** for typed parent/child control; D4-like cross-cycle adaptation is plausible for activated verified procedure artifacts, but it is human-governed and does not reproduce Soar's exact chunking operator. Keep scientific coding provisional.  
**Still required:** independent future-case transfer/repeat-failure experiments, test distribution shift/overgeneralization, and a durable full-graph composition test using PostgreSQL adapters across process restart.

### 6.3 B3 — typed content competition, bounded workspace, broadcast and admission learning

**Paper target:** typed content candidates compete for a capacity-limited shared workspace; a winning coalition is broadcast for a cycle; downstream utility affects later admission. (Fan & Lan, §VI-C, B3; LIDA comparison in §III-B.)

| Field | Current code | Assessment and boundary |
|---|---|---|
| **S — State** | `WorkspaceCandidate`, singleton/pair/triple `WorkspaceCoalitionProposal`, preference key, exact selected proposal fingerprint/member set, workspace snapshot/version, broadcast-consumer receipts, verifier-bound coalition outcome and experiment-unit fingerprint. | Coalition identity is first-class and outcome attribution records the joint selected set. The candidate taxonomy remains narrower than memory/agents/tools/monitors/plans in the paper's proposal. |
| **C — Control** | `WorkspaceAdmissionService` compares bounded singleton and coalition proposals; an active, scope-bound `WorkspaceCoalitionOutcomeAdjustment` affects a future matching coalition preference key. `WorkspaceBroadcastService` dispatches the admitted snapshot and receipts are persisted. | First-class bounded coalition competition is implemented; it is an engineering analogue, not a complete LIDA global workspace. |
| **T — Trigger** | Pre-inference workspace admission and synchronous broadcast; consumed coalition receipt plus independently verified downstream outcome triggers feedback evidence. | The receipt/outcome binding is exact to the selected coalition, not inferred from a generic event. |
| **P — Persistence** | Coalition receipt/outcome evidence persists through PostgreSQL V015; workspace snapshots remain cycle-bounded and use an in-memory repository in the evaluation host. | Evidence persists; full workspace/outbox/replay durability is part of GAP-08. |
| **F — Failure** | Bounded proposal enumeration, mandatory-capacity/coverage checks, deterministic ordering, singleton fallback on truncation, exact receipt/proposal/member/fingerprint verification, independent holdout and approval gates. | Truncated search does not claim to have found the global optimum. |
| **L — Learning** | `B3CoalitionFeedbackPolicyService` trains from verified coalition outcomes and produces a SHADOW candidate. It requires distinct training units, non-overlapping paired independent holdout runs, acceptable resource/safety results and a separate activation approval. An active policy changes future admission by coalition preference key. | **Coalition-level learning path implemented; currently not active without verified outcome/holdout evidence.** This is the code-level learning operator, not proof that it improves workspace quality. |
| **R — Resources** | Workspace capacity, candidate limits, pair/triple search cap, context units, mandatory priority, score/cost/risk and bounded feedback adjustment. | Resource/capacity handling is explicit; no universal LIDA scoring equation is claimed. |

**Code:** `WorkspaceAdmissionService`, `WorkspaceCoalitionCompetitionPolicy`, `WorkspaceCoalitionProposal`, `WorkspaceCoalitionReceipt`, `B3OutcomeEvidenceService`, `B3CoalitionFeedbackPolicyService`, `PostgresB3OutcomeEvidenceRepository`, V015, `B3OutcomeEvidenceServiceTest`, `WorkspaceAdmissionServiceTest`.  
**Assessment:** **Integrated adapted coalition competition and learning protocol; empirical activation pending.**  
**Provisional E/D:** **E2/D3** for explicit bounded competition and broadcast; a D4-like coalition-preference update is implemented behind verified independent evidence and approval, but inactive in the current evidence state. This is not a claim of LIDA migration or reproduction.  
**Still required:** independent matched comparisons (singletons/top-k/GWA-like baselines), synergy/rare-evidence suppression/cost/truncation outcomes, and real activated policy evaluation. Workspace snapshot durability remains GAP-08.

### 6.4 B4 — persistent intention, reconsideration and method authority

**Paper target:** a persistent intention contains committed goal, rationale, plan, continuation condition, reconsideration triggers, authority and termination status; a governor can continue, delegate, suspend, abandon or change method. (Fan & Lan, §VI-C, B4; BDI/MIDCA comparison in §III-C.)

| Field | Current code | Assessment and boundary |
|---|---|---|
| **S — State** | `RecoveryIntention` stores objective/scope/fingerprint/owner, protected constraints, current method, rationale, active plan reference, continuation condition, typed reconsideration triggers, `IntentionAuthorityPolicy`, status/version/timestamps, delegation/resume state, completion proof and typed termination reason/actor. | The proposed intention fields are explicit rather than inferred from a generic reason string. |
| **C — Control** | `RecoveryIntentionService`, `CognitiveCycleAuthorityPort`, `PlannerMethodResolver`, `PlannerSelectionOverride` and `PlannerRouter`. | F-01 binds the stored method to the next registered planner; objective fingerprint/intention version are verified and stale/unknown methods fail closed. |
| **T — Trigger** | Continuation condition is checked at lifecycle transitions; only configured typed reconsideration triggers authorize reconsideration; delegate return, suspend/resume, verified completion and authorized termination have explicit transitions. | Model proposals do not themselves exercise intention authority. |
| **P — Persistence** | Scoped in-memory and PostgreSQL repositories with CAS/version history and immutable objective identity. | Durable intention state is implemented; full host composition across all ports remains GAP-08. |
| **F — Failure** | Actor policy, objective hash, intention version, method registry, allowed trigger, termination authorization and completion proof are validated. | Wrong-method, stale-intention, unauthorized termination and objective drift reject. |
| **L — Learning** | A governed method change changes the next planner invocation while the committed objective is held fixed. No autonomous method-preference training is claimed or required for the core B4 control hypothesis. | **Application-level B4 control implementation complete; long-horizon effectiveness remains untested.** |
| **R — Resources/governance** | Owner/governor/delegate/terminator authority, continuation/reconsideration bounds, suspension and delegation transitions. | Application-specific authority policy is explicit. |

**Code:** `RecoveryIntention`, `ContinuationCondition`, `ReconsiderationTrigger`, `IntentionAuthorityPolicy`, `IntentionTerminationReason`, `RecoveryIntentionService`, F-01 planner-method resolver/override, `CognitiveCycleLifecycleServices`, `RecoveryIntentionServiceTest`, `CognitiveCycleDecisionServicesTest`.  
**Assessment:** **Strong adapted implementation of the B4 intention/method-authority contract.**  
**Provisional E/D:** **E2/D3** for durable intention structure and trigger-driven planner-method control; this is an application-level structural transfer, not historical BDI/MIDCA inheritance.  
**Still required:** matched long-horizon B4-on/B4-off experiments for goal retention, method oscillation, recovery and premature stopping; durable full-host/restart coverage remains GAP-08.

### 6.5 B5 — skill governance closed by GraSP

**Paper disposition:** Fan & Lan's closest-baseline analysis closes B5 through GraSP; B5 is not a sixth residual hypothesis. The GraSP-like skill-governance capability nevertheless remains a dependency for the paper's bounded synthesis-runtime claim in §VI-D/§VI-E. It includes confidence routing between a typed skill DAG and ReAct, node pre/postcondition verification, five bounded repair operators and escalation. (Fan & Lan, §VI-C B5, §VI-D–§VI-E.)

| Question | Current project state |
|---|---|
| Is B5 an additional residual like B1/B2/B3/B4/B6? | **No.** It is closed as a residual by the paper's closest-baseline analysis. |
| Does this repository implement GraSP/a complete equivalent? | **No.** The repository does not supply the complete typed skill-DAG compiler/executor, five repair operators, confidence router and ReAct fallback as an integrated governor. |
| Is a GraSP-like governor needed to claim the whole bounded synthesis runtime? | **Yes.** It must be integrated or supplied as a versioned, tested dependency. |
| What is the current decision? | Keep GAP-06 open; do not claim §VI-E synthesis-runtime completeness. Do not describe B5 as an unimplemented sixth residual. |

**Assessment:** **Correctly closed residual; local synthesis dependency remains absent.**

### 6.6 B6 — uncertainty, resources, interruption and stopping

**Paper target:** uncertainty and resource controls affect consequential decisions, including evidence selection, planning, verification, tool authorization, execution monitoring and stopping. (Fan & Lan, §VI-C B6; EPIC/Sigma comparisons in §III-D.)

| Field | Current code | Assessment and boundary |
|---|---|---|
| **S — State** | `UncertaintyAssessment` has evidence/hypothesis/plan signals, scope/execution/trigger/revision and calibration metadata. `UncertaintyCalibrationProfile` stores target-bound bins, training/holdout fingerprints, labels, ECE and reviewer/approver identity. | Versioned uncertainty and auditable target-specific calibration are modeled. |
| **C — Control** | `UncertaintyCalibrationTrainer/Service`, lifecycle calibration binding, `UncertaintyGovernanceDirective`, `MetaControlGovernor`, B1 selector, B3 admission, M5 alternative-hypothesis review, independent plan verification and F-02 planner downshift. | Calibrated directive is bound to exact assessment/profile/version; missing or stale independent review fails closed. |
| **T — Trigger** | Target-specific validated profile is applied to an assessment. Directive thresholds govern B1 evidence diversity, B3 workspace diversity, M5 independent alternative-hypothesis review and revalidation-time independent plan verification. Resource triggers still drive reduce/stop/interruption. | There is an actual cross-stage value-dependent control path at these named stages, not universal propagation. |
| **P — Persistence** | Calibration profiles and observations are persisted through PostgreSQL V013; resource budgets/reservations remain on V009/V010; assessment persistence remains host/domain supplied. | Calibration lifecycle persistence exists, but no production-calibrated profile is installed/active in current evidence. |
| **F — Failure** | Calibration rejects insufficient labels, non-independent training/holdout IDs/evidence, scope/target mixing and ECE above threshold; directives remain neutral when `NOT_CALIBRATED`; missing alternative/plan verifier fails closed. | Calibration failure does not turn heuristic values into probabilities. |
| **L — Learning** | Target-specific training and independent validation can produce a versioned calibrated profile, and separately approved activation affects future governance directives. | **Calibration workflow implemented; no real labelled corpus/validated active profile exists.** Test fixtures verify the lifecycle, not real calibration quality. |
| **R — Resources/governance** | ECE gate (configured maximum 0.15), minimum 20 labelled samples per uncertainty dimension for training and independent validation, target/scope binding, separate reviewer/approver, B1/B3 diversity and M5 verification costs; F-02 keeps hard execution ceiling immutable. | These numerical thresholds are application engineering choices, not values prescribed by Fan & Lan. No complete concurrent scheduler or tool-level preemption is claimed. |

**Code:** `UncertaintyCalibrationObservation/Profile/Trainer/Service/Repository`, `PostgresUncertaintyCalibrationRepository`, V013, `UncertaintyGovernanceDirective`, `CognitiveCycleLifecycleServices`, `CognitiveCycleDecisionServices`, `IndependentAlternativeHypothesisReview`, `IndependentPlanVerification`, `MetaControlGovernor`, `ResourceBudgetExecutionGuard`, F-02 downshift.  
**Assessment:** **Calibration and selected-stage governance implementation complete; operational calibration and full paper-wide propagation remain incomplete.**  
**Provisional E/D:** **E3/D3** for resource/interruption authority and the implemented calibrated-control pathway. No D4 uncertainty-learning claim is made for the current runtime because there is no validated active profile.  
**Still required:** real target-specific labelled outcomes, independent calibration/validation, calibration report, sequential/stress and transfer evaluations; expand controls to additional stages only where the application explicitly claims them (M3 authorization/execution monitoring is not yet driven by calibrated numeric values).

## 7. Cross-bundle synthesis and full-graph integration

The current graph integrates the selected B1/B2/B3/B4/B6 paths; integration does not imply that every historical architecture is reproduced or that every empirical effect is demonstrated.

| Required dependency | Current implementation | Remaining boundary |
|---|---|---|
| B4 scopes B2 recovery to a committed parent objective | B2 child context carries parent objective/scope/working-memory identity; B4 intention carries continuation/authority/trigger fields and the method override; B2 result and later reuse are checked against exact objective and current registered actions | Transfer and long-horizon tests remain empirical work; full durable host composition remains GAP-08 |
| B2 produces future reusable procedural knowledge | Approved B2 artifact is staged/activated through separate CoALA procedural governance; `CognitiveCycleDecisionServices` calls `retrieveActive` each planning cycle with trusted condition keys; `PlanningRequest` receives typed procedure knowledge and provenance | This is soft planning priority, not code compilation or execution authority. M3 still validates/authorizes normal actions. |
| B1 and B2 knowledge are admitted into B3 | M2 retrieval enriches B1 candidates with durable exact-revision access/use/cue/utility; active B2 procedures can appear as typed procedural candidates; B3 compares singleton and bounded coalition proposals | Feature/policy feedback remains inactive until actual verified corpus and independent approval. Candidate kinds do not fully cover every theoretical source (agents/tools/monitors). |
| B3 outcome changes future coalition admission | Exact selected coalition/proposal/member/receipt/experiment-unit outcome is verified and stored; B3 trainer can create a shadow preference candidate; future `WorkspaceAdmissionService` reads an explicitly active coalition adjustment | No activated policy or empirical benefit exists in the current corpus. Workspace snapshots remain in-memory in the evaluation host. |
| B3/workspace evidence can trigger B4 reconsideration | Full graph feeds observations/evidence/reconsideration into the intention lifecycle; B4 owns goal/method authority | Causal benefit for goal retention/oscillation remains untested. |
| B6 governs resources and calibrated evidence/verification at selected stages | Shared resource guard and F-02 next-planning-request downshift; exact calibrated directive controls B1/B3 diversity and M5 alternative review/selected-plan verification where an active profile exists | Runtime remains `NOT_CALIBRATED`; calibrated numeric controls are not propagated to every M3/tool/execution stage. GAP-09 remains. |
| Durable state covers the whole bounded runtime | PostgreSQL adapters exist for memory, B1 features, B2 drafts/artifacts, B3 receipts/outcomes, B4 intentions, B6 profiles and resource leases; runtime checkpointing/restart tests exist | Full application bootstrap is not yet composed to use every durable adapter; evaluation host keeps some in-memory stores; top-level event ingress/outer cycle remains GAP-08. |
| B5 skill governance participates in the synthesis runtime | B5 correctly remains closed as a residual; no integrated GraSP-like governor is in this repository | GAP-06 is open for a full §VI-E synthesis-runtime claim. |

### 7.1 Full graph vs durable application host

`CognitiveCycleApplicationGraph.buildFull/compileFull` is the graph composition seam, and the evaluation host composes the full cognitive graph. The default `Phase0Application` still runs the older `SupplyChainIntakeGraph`, not the full CoALA/Fan–Lan host. The full evaluation host still uses in-memory adapters for some workspace/procedural/recovery artifact services even though PostgreSQL adapters exist for many stores. The application module does not yet supply a runnable bootstrap that wires every full-graph port to durable PostgreSQL repositories and a durable checkpoint saver. This remains GAP-08.

### 7.2 Repeated-cycle boundary

The graph contains bounded internal loops for clarification, evidence requests, retries, B2 recovery, B4 reconsideration, B6 downshift and observation/re-entry. Completion of an objective terminates one graph invocation; fresh top-level observations/tasks require another host invocation. CoALA's continuously repeated main procedure can be hosted by a caller loop, but the supplied full application host/event-ingress contract is not complete. Treat this as an application-host gap, not a missing CoALA decision-process node.

## 8. Validation and empirical evidence: what tests do and do not establish

### 8.1 Source and targeted-test evidence for GAP-01–GAP-05

The first-five candidate was validated locally as follows:

- Eight framework-neutral production modules compiled with Java 21, including domain, memory, actions, mechanisms, planning and the changed runtime decision/lifecycle code.
- 37 PostgreSQL production sources compiled; six classes depending on unavailable HikariCP/pgJDBC/LangGraph4j libraries were excluded from this local compile.
- A lightweight JUnit-compatible runner executed **140 targeted tests**: 48 runtime decision/lifecycle/resource-guard tests, 83 mechanism/planning tests and 9 PostgreSQL migration parsing tests. All 139 passed. This is not a real JUnit/Surefire/Maven reactor execution.
- The three repository static guards passed: M8-B5 architecture, R2 evidence, and F-03/F-04 procedure/coalition checks.
- The PostgreSQL migration chain is registered through V015; targeted parser tests cover the extended migrations.

The project owner previously confirmed F-01–F-04 hosted CI green. The first-five GAP-01–GAP-05 candidate **still requires a new hosted Maven CI run**. No claim that this candidate is CI-accepted is made here.

### 8.2 What implementation tests establish

- B1 tests exercise durable per-revision access and verified-use features, idempotency, cue/utility updates and their use in the next candidate enrichment.
- B2 planning tests exercise condition-matched active procedure retrieval, action descriptor freshness and soft prioritization in the next `PlanningRequest`.
- B3 tests exercise receipt/proposal/member-bound coalition outcome evidence, SHADOW candidate training, independent paired holdout gates, activation authority and future preference lookup.
- B4 tests exercise continuation/trigger/authority/termination lifecycle transitions and the F-01 persisted-method-to-next-planner binding.
- B6 tests exercise target-bound training, independent holdout validation, calibration ECE gating, separate approval, neutral NOT_CALIBRATED directives and selected-stage control propagation.

These are implementation and protocol tests. They do not substitute for independent outcome samples, deployment persistence tests or matched causal evaluation.

### 8.3 What is not established

- **B1/B3 adaptive advantage:** there is still no verified production outcome corpus/independent holdout. R2 remains `NO_VERIFIED_OUTCOME_CORPUS`, `SHADOW`, `DISABLED`; no adaptive superiority claim is justified.
- **B2 transfer:** future-cycle procedure reuse is integrated, but lower repeated failure or transfer to independent scenarios is not measured.
- **B3 coalition benefit:** coalition-level outcome attribution and training gates exist, but no active preference policy or independent comparison proves superiority over singleton/top-k/GWA-like controls.
- **B4 effectiveness:** method authority and lifecycle are tested, but B4-on/B4-off long-horizon goal retention and method oscillation outcomes have not been measured.
- **B6 calibration:** the trainer/validation/activation lifecycle exists, but the actual application remains `NOT_CALIBRATED`; no real target-specific labels, calibration report or risk/resource transfer results are supplied.
- **Cross-bundle causal value:** the full graph paths and unit/integration tests do not prove that the combined system outperforms simpler controls.
- **Full synthesis runtime:** the GraSP-like governor (GAP-06) is absent; durable full-host/event-ingress composition (GAP-08) is not complete.

### 8.4 Required independent evaluation

Keep GAP-09 open. The next research package must preregister experimental units, matched conditions, baselines, outcome labels and metrics; use disjoint training/holdout unit and evidence fingerprints; verify downstream outcomes independently; report confidence intervals and negative results; include B1 retrieval controls, B2 no-reuse and transfer conditions, B3 singleton/top-k/GWA-like controls and coalition synergy, B4 on/off long-horizon conditions, B6 calibration/risk/verification-cost and downshift conditions, and combined-bundle ablations. Calibration and transfer must be reported separately by target task/domain. Do not activate R2 policies or B6 calibration solely to make the implementation appear complete.

## 9. Final claim policy for the project and book

### 9.1 Claims that are safe after the GAP-01–GAP-05 implementation candidate passes hosted CI

1. “The Supply-Chain Agent is organized around CoALA's central memory, action-space and decision-procedure dimensions.”
2. “The application has explicit, durable and integrated B1 access/verified-use/utility feature learning, with exact memory-revision attribution and verified outcome gating.”
3. “Approved B2 recovery artifacts can be condition-matched, revalidated and supplied as typed knowledge to later M5 planning requests, without granting action capability.”
4. “B3 performs bounded singleton/coalition competition and records verifier-bound coalition outcomes; a guarded shadow trainer can produce a candidate future-admission preference policy.”
5. “B4 stores a fuller intention/continuation/reconsideration/authority contract, and the persisted method controls the next registered planner while preserving the committed objective.”
6. “B6 provides a target-specific calibration lifecycle and calibrated directives for B1 evidence diversity, B3 workspace diversity, M5 alternative-hypothesis review and independent selected-plan verification; its resource governor also supports a planner-budget downshift.”
7. “These are application-adapted realizations of Fan & Lan's five residual bundles, not literal reproductions of ACT-R, Soar, LIDA, BDI/MIDCA, EPIC or Sigma.”
8. “The B5 candidate is closed as a residual by GraSP; a GraSP-like governor is still required to claim the complete Fan & Lan §VI-E synthesis runtime.”

### 9.2 Claims that remain disallowed

- “The code completely implements CoALA in every learning form.”
- “The code completely implements Fan & Lan or their full bounded synthesis runtime.”
- “B1/B3 learned policies are empirically superior or active on real verified outcomes.”
- “B2 procedures reduce repeated failures or transfer successfully to independent cases.”
- “B3 coalition learning improves workspace quality.”
- “B4 improves long-horizon goal retention compared with no B4.”
- “B6 uncertainty is calibrated in the current runtime” or “calibrated values govern every M3/tool/execution stage.”
- “The application includes a complete GraSP implementation.”
- “The full durable application host and continuous top-level cycle are delivered.”
- “Green CI proves research effectiveness.”

### 9.3 Recommended as-built wording after hosted CI acceptance

> The Supply-Chain Agent is a Java reference implementation organized around CoALA's central memory, action-space and decision-procedure framework. It implements application-adapted, integrated control paths for Fan & Lan's five residual bundles B1, B2, B3, B4 and B6: durable verified-use retrieval features, governed future-cycle recovery-procedure reuse, bounded coalition competition with verifier-bound outcome learning, persistent intention/method authority, and target-specific uncertainty calibration/governance. The implementation is not a literal reproduction of the historical architectures or an empirical demonstration of adaptive superiority. B1/B3 policy activation and B6 calibrated operation remain evidence-gated; independent matched experiments and transfer evaluation are still required. B5 is closed as a residual by GraSP, but a GraSP-like governor remains a dependency for the full §VI-E synthesis-runtime claim.

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
- Production/test modules and PostgreSQL migrations V001–V015 in the first-five candidate source baseline (V001 in the memory module; V002–V015 in PostgreSQL infrastructure).
