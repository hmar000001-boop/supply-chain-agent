# Final implementation gaps and acceptance plan — post GAP-01–GAP-05 candidate

**Project:** Supply-Chain Disruption Management Agent  
**Revision:** 2026-11-21  
**Baseline:** Owner-confirmed F-01–F-04 CI-green stack plus the current GAP-01–GAP-05 implementation candidate  
**Acceptance state:** Local targeted validation passed; first-five hosted CI is pending. Do not treat the candidate as accepted until hosted CI passes.

## 1. Executive status

The first five implementation gaps from the previous matrix have been implemented at the **source/control-path level** in the current candidate:

| Gap | Code-level result | Remaining evidence/qualification |
|---|---|---|
| GAP-01 — B1 durable accessibility/use/utility learning | Per-memory-revision PostgreSQL feature state stores retrieval access, verified downstream-use history, cue associations and utility. Exact B3 verified outcomes update features idempotently; the next B1 candidate is enriched from them. | No real verified outcome/holdout corpus; no active adaptive advantage claim. Keep policy activation gated. |
| GAP-02 — B2 future-cycle procedural reuse | Approved and independently activated B2 artifacts are retrieved by trusted condition keys in normal decision-service planning-input construction; evidence and action descriptors are revalidated; typed knowledge/provenance are attached to M5; planners use soft search priority. | No transfer/repeated-failure benefit measured; procedure is not an executable rule/capability and is not literal Soar chunking. |
| GAP-03 — B3 coalition-level outcome learning | Outcomes are tied to exact selected coalition, proposal fingerprint/member set, broadcast receipt and experimental unit. A shadow trainer learns coalition preference adjustments; independent paired holdout and approval gate activation; future admission reads an active adjustment. | No verified corpus or active policy; no empirical coalition benefit. Keep `SHADOW`/`DISABLED`. |
| GAP-04 — B4 complete application intention contract | Persisted intention includes rationale, active plan ref, continuation condition, typed reconsideration triggers, authority policy and termination semantics; lifecycle checks these; F-01 routes the method to the next registered planner while preserving objective identity. | Long-horizon effectiveness/oscillation/goal-retention comparison remains untested. |
| GAP-05 — B6 calibration and selected-stage propagation | Target-bound calibration train/holdout/validation/approval lifecycle; V013 repository; exact assessment/profile version; actionable directives for B1 evidence diversity, B3 workspace diversity, M5 alternative-hypothesis review and independent plan verification; F-02 downshift remains. | No real calibration data; runtime remains `NOT_CALIBRATED`. No claim that calibrated values drive all M3/tool/execution monitoring stages. |

These five are **implementation-path closures**, not empirical acceptance. Tests validate controls and protocol boundaries; they do not make empty evidence non-empty. GAP-09 remains mandatory for effectiveness claims.

## 2. Remaining gaps

| ID | Priority | Scope | Current state | Remaining work | Acceptance evidence |
|---|---|---|---|---|---|
| GAP-06 | P0 only if claiming Fan & Lan §VI-E full synthesis runtime | B5/GraSP skill-governor dependency | B5 is closed as a residual by Fan & Lan's closest-baseline analysis, but the repository does not integrate GraSP or a complete equivalent. | Integrate a GraSP-like governor or a versioned, tested external dependency. Do not call B5 a sixth residual. Alternatively narrow the target explicitly to the five residual bundle implementations and do not claim full §VI-E synthesis runtime. | Full path: confidence route to typed DAG/ReAct, pre/postcondition verifiers, five bounded repair operators, verified-node preservation, global replanning/ReAct fallback, and exact dependency/version tests. |
| GAP-07 | P1 | CoALA learning-action scope | Episodic learning is a registered action; semantic/procedural governance and B1/B3 policy learning exist through services. Model-weight and autonomous source-code adaptation are not implemented. | Define the application's in-scope learning action contract. Add registered semantic/procedural learning actions where needed, or document governed service APIs as the action boundary. Explicitly scope model-weight adaptation and self-modifying code out unless separately implemented/governed. | Action catalog/full-graph tests prove every in-scope learning operation is invocable, versioned, reviewed and consumed in a later cycle; non-goals are explicit. |
| GAP-08 | P1 | Full durable application composition and repeat-cycle ingress | `CognitiveCycleApplicationGraph` and `FullGraphEvaluationHost` compose the graph; many stores have PostgreSQL adapters, but default full-evaluation host still uses some in-memory ports; `Phase0Application` runs the older intake graph. Two root-level Java shadow files remain outside the 11 modules. | Add a runnable full application bootstrap using durable repositories/checkpoint saver and expose a repeat-cycle/event-ingress host. Remove or archive the two root `src/` shadow files after confirming they are obsolete. | Full start-to-finish test uses durable adapters, restarts across pre-effect/post-effect reconciliation, invokes a second top-level cycle with new observations, and confirms no unintended in-memory fallback; repository contains no unowned source shadows. |
| GAP-09 | P0 for empirical effectiveness/calibration/transfer claims | Independent matched experiments, calibration and transfer | B1/B3 feedback trainers and B6 calibration protocol exist. R2 reports no verified outcome corpus/holdout; no real target calibration profile is active. | Preregister and build verified downstream-outcome corpora and independent holdouts; run matched baseline comparisons for B1–B4/B6 and composition; calibrate uncertainty by target and domain; run B2 transfer and B4 long-horizon evaluation; report negative results. Keep all adaptive policies shadow/disabled until separately accepted. | Unique experimental-unit/evidence fingerprints, independent verifier audit, disjoint train/holdout, confidence intervals, task success/constraint/safety/resource/latency/recall/calibration/transfer measures, ablations and raw reproducible evidence artifacts. |
| GAP-10 | P2 hygiene | Source inventory/status hygiene | Two root-level Java files under `src/` are outside Maven modules and are excluded from the module counts/compilation. The new matrix and manifest disclose them. | Confirm whether they are obsolete copies; remove them or move them into a clearly owned module/test source root. Update inventory and manifests. | No unowned Java source outside Maven module roots; architecture guard and source manifest pass. |

## 3. Definition of implementation completion

### 3.1 Selected CoALA application scope

The application-level CoALA scope can be considered implemented when:

- Working, episodic, semantic and procedural memories have explicit state, ownership, trust/lifecycle, persistence and failure semantics.
- Internal retrieval, reasoning and learning actions are explicit and connected to the decision cycle.
- External grounding actions remain separate from planning, approval, authorization, dispatch and reconciliation.
- Proposal/evaluation/selection can iterate; execution and observation feed later decisions.
- Every learning operation in the declared application scope has a typed, governed and tested path. Model-weight adaptation and autonomous source-code mutation may be explicit non-goals; do not claim to implement them.
- The durable full application host and repeated-cycle ingress are delivered if the book claims a runnable full application rather than a full-graph reference/evaluation host.

### 3.2 Fan & Lan residual bundles

B1/B2/B3/B4/B6 must each have an observable control chain through all seven fields:

`operative state → identifiable trigger → authorized control locus → state transition → persistence/failure behavior → learning effect → resource/governance policy`.

The current candidate supplies the source/control-path implementation for each of the five bundles. What remains is acceptance by hosted CI, empirical activation/transfer evidence, and any deliberately broader integration scope. This does not mean literal reproduction of ACT-R/Soar/LIDA/BDI/MIDCA/EPIC/Sigma.

### 3.3 Full synthesis runtime claim

To claim the complete Fan & Lan §VI-D/§VI-E bounded synthesis runtime, GAP-06 must be closed with GraSP-like skill governance or a compatible tested dependency. B5 remains closed as a residual hypothesis. If the project chooses not to integrate GraSP, the book must explicitly limit its target to the five residual control bundles and avoid calling it the complete synthesis runtime.

## 4. Evaluation and evidence rules

- Green CI proves only that configured builds/tests passed. It does not prove research effectiveness, calibration, transfer or historical migration.
- Do not activate B1/B3 outcome policies or B6 calibration from synthetic fixtures or an empty corpus.
- Training and holdout must use disjoint immutable experimental-unit fingerprints and disjoint labelled evidence IDs.
- Report matched controls and uncertainty intervals; include costs, harmful outcomes, failure modes and negative results.
- B2 transfer must use future cases not involved in drafting/training the reusable procedure.
- B4 evaluation must compare intention/method authority with a no-B4 baseline on long-horizon tasks.
- B3 should compare coalition competition with singleton/top-k and an appropriate global-workspace comparator; check rare evidence and interaction/synergy failures.
- B6 should report calibration by dimension and target, ECE/reliability, selective-risk behavior, verification burden, interruption decisions and transfer under distribution shift.

## 5. Current validation record for the GAP-01–GAP-05 candidate

- Java 21 fresh compilation: eight framework-neutral production modules passed, including changed runtime decision/lifecycle sources.
- PostgreSQL compilation: 37 source files passed; six third-party-bound files requiring unavailable HikariCP/pgJDBC/LangGraph4j dependencies were excluded from the local compile.
- Targeted lightweight test execution: **140 passed, 0 failed** (48 runtime + 82 mechanism/planning + 9 migration parsing). This is not Maven Surefire/JUnit.
- Static guards: `verify-m8-b5-architecture.py`, `verify-m8-r2-evidence.py` and `verify-m8-preb7-f03-f04.py` all passed.
- PostgreSQL migration chain registered through V015; migration parser test covers V012–V015.
- Hosted Maven CI for GAP-01–GAP-05: **pending**. The prior F-01–F-04 stack was owner-confirmed CI-green.
- Real PostgreSQL integration for the new repositories, full LangGraph4j graph execution, independent experiments, calibration and transfer: **not run/available in this local validation**.

## 6. Freeze and book gate

The GAP-01–GAP-05 source candidate will be frozen with a complete SHA-256 source manifest and a full source archive. It is not the accepted final source until the owner confirms hosted CI green on this exact candidate. After CI, update this plan/matrix with commit SHA, workflow run, migration head and full test summary. Keep Chapters 19 and 20 blocked until the owner explicitly accepts the conformance scope and remaining exclusions.
