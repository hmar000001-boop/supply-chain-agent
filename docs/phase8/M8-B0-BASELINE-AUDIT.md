# M8-B0 — Accepted baseline and architecture audit

## 1. Frozen starting point

The M8 starting point is the exact M7-accepted project assembled from the following source inputs. No earlier M7 package is applied over a newer repository snapshot.

| Input | SHA-256 | Role |
|---|---|---|
| `supply-chain-agent-main (2).zip` | `2fe8fb2efc95bb2081d6729c40aa1ed091e15ca3c97ef8849806d843aca8e596` | User-supplied repository snapshot |
| `M7B2_FINAL_CI_FIX_AND_CLOSURE_CHANGESET_ONLY.zip` | `0afe79ea4b9184c2d3e0c26800ed967311d64c3a74a8049dd87178e2f769d256` | Cumulative M7-B2 runtime/operations implementation and Maven dependency correction |
| `M7B2_PGJDBC_INSTANT_BINDING_CI_FIX_CHANGESET_ONLY.zip` | `7574f32f320adac9d2814428829a651d082651495142d09df483d27503a99d63` | JDBC 4.2 Instant binding correction and V008 parser expectation |
| `M7B2_OBSERVATION_TIMESTAMP_PRECISION_AND_BATCH_CONTRACT_FIX_CHANGESET_ONLY.zip` | `78fcea7a875c988464147fa38c1b9e96b295bef89a13e499162be5e266814777` | PostgreSQL timestamp precision/idempotency correction and batch exception contract |

The reconstruction contains **556 project files**. It matches the staged accepted repository file-for-file and SHA-for-SHA; the only files in the raw patch ZIPs not included in the source-tree count are the patch delivery READMEs/manifests/checksum documents. The supplied repository ZIP contains no `.git` directory, so no commit hash or Git history is claimed.

**Baseline identity**

- Baseline ID: `M8-M7-ACCEPTED-2026-11-19`
- Full checkpoint: `SUPPLY_CHAIN_AGENT_M7_ACCEPTED_BASELINE_2026-11-19.zip`
- Full checkpoint SHA-256: `7bf42b69f3f3db48e944fef907f9aa805bacf60bfa8f9b24b95a0a4636cdec49`
- Checkpoint size: 763252 bytes
- Sorted file-tree manifest SHA-256: `08e82ac250aab5ec01271ca9ccea624d8aa7c0ad7a13bad2c680393f31667a8c`
- File-level checksums: `M8-BASELINE-FILE-MANIFEST.sha256`

The checkpoint is the M7-accepted source state before M8 documentation is applied. It is not a Git commit and contains no `.git` metadata.

## 2. Repository structure

The accepted Maven reactor is a ten-module modular monolith. Counts below were derived from the actual source tree, not inferred from the roadmap.

| Module | Production Java files | Test Java sources | Annotated test methods/factories | Responsibility |
|---|---:|---:|---:|---|
| `supply-chain-agent-domain` | 53 | 4 | 14 | Typed supply-chain entities, values, lifecycle and invariants |
| `supply-chain-agent-memory` | 52 | 7 | 44 | CoALA memory contracts, services and in-memory/reference repositories |
| `supply-chain-agent-core` | 4 | 0 | 0 | Framework-neutral application contracts |
| `supply-chain-agent-simulator` | 5 | 2 | 20 | Deterministic simulator and transition rules |
| `supply-chain-agent-actions` | 55 | 5 | 25 | Internal actions, external capability actions and dispatch contracts |
| `supply-chain-agent-mechanisms` | 86 | 6 | 70 | B1, B2, B3, B4 and B6 mechanisms |
| `supply-chain-agent-planning` | 51 | 8 | 30 | CoT-style, ToT-inspired, MCTS planning, shared evaluation and routing |
| `supply-chain-agent-runtime` | 33 | 8 | 38 | LangGraph4j cognitive cycle and LangChain4j provider/interpreter adapters |
| `supply-chain-agent-infrastructure-postgres` | 37 | 7 | 28 | PostgreSQL persistence, saver factory, pooling and operations |
| `supply-chain-agent-app` | 2 | 0 | 0 | Local smoke entrypoint and full graph composition entrypoint |
| **Total** | **378** | **47** | **269** | |

The 47 test Java sources include five support/fixture sources; 42 sources contain test annotations. The count of 269 is the number of `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory` and `@TestTemplate` annotation occurrences in the source. It is a source inventory, **not** a claim about the number of executed test cases in the final CI run.

## 3. Toolchain and runtime composition

- Java compiler release: 21, with `-Xlint:all` enabled.
- Maven Wrapper: 3.9.16, SHA-512 pinned in `.mvn/wrapper/maven-wrapper.properties`.
- LangGraph4j: 1.8.20 (`langgraph4j-core`, `langgraph4j-postgres-saver`).
- LangChain4j: 1.23.0; SLF4J API: 2.0.20; pgJDBC: 42.7.8; HikariCP: 7.1.0; JUnit Jupiter: 5.12.2.
- PostgreSQL migration chain: V001–V008 (V001 is packaged in the CoALA memory module; V002–V008 in the PostgreSQL infrastructure module).
- CI workflow: Ubuntu 24.04, Temurin Java 21, PostgreSQL 16.4; `./mvnw -B -ntp -Dphase0.postgres=true clean verify`; then a custom-format backup and isolated restore verification step.

The user confirmed that the complete workflow is green after the final M7-B2 corrections. The exact hosted run URL/ID was not present in the repository archive supplied for this audit, so no run ID or test-execution count is invented here.

## 4. Architecture findings

### 4.1 Implemented structure

1. **Domain:** typed identifiers and records cover products/substitutions, suppliers/capabilities, inventory/locations/lots/reservations, orders, purchase orders, carriers/routes/shipments, disruptions, recovery objectives/candidates and snapshots.
2. **Simulator:** `SupplyChainSimulator` applies deterministic state transitions. `SimulatorBackedPlanningWorldModel` validates proposal-scope and action/command correspondence, then delegates transitions to the simulator. The simulator is authoritative only for the modeled world; it is not an ERP, WMS, carrier or supplier system.
3. **Memory:** Working, Episodic, Semantic and Procedural memory have distinct contracts and services. PostgreSQL adapters preserve the accepted M7 repositories and bitemporal/versioned semantics.
4. **Actions:** external actions are typed, capability-checked and dispatched through M3 contracts. Reference implementations operate over the simulator; they do not connect to live enterprise endpoints.
5. **Mechanisms:** B1 selectors, B2 typed impasse/resolution, B3 workspace/admission/broadcast, B4 persistent intention governance and B6 uncertainty/resource meta-control are implemented with component tests.
6. **Planning:** `SequentialReasoningPlanner`, `ThoughtSearchPlanner` and `MctsSearchPlanner` share planning contracts and deterministic candidate evaluation. ToT searches structured partial action paths; MCTS searches explicit simulator states/actions. MCTS is RAP-inspired, not a faithful reproduction of RAP's LLM world-model arrangement.
7. **Runtime:** LangGraph4j owns outer cognitive-cycle orchestration; LangChain4j abstractions wrap reasoning/request interpretation. `CognitiveCycleApplicationGraph.compileFull(...)` composes decision and lifecycle services while requiring explicit domain and authority ports.
8. **Persistence:** PostgreSQL stores durable memory, planning sessions/artifacts, approvals, execution batches/receipts, observations, intentions, operation-ledger records, child-step receipts and inbox/outbox state. Graph checkpoints remain execution-position state, not the authoritative business-operation ledger.

### 4.2 Findings that shape M8-B1

**A. The scenario corpus is not yet broad enough.** Existing `ScenarioFixtures` provide a compact example world (one main order/product/supplier/warehouse/route/shipment with an approved-substitution variant). Simulator and planner unit tests are useful, but M8 must add independently identified request families and scenario fixtures. One fixture is not evidence of multi-request capability.

**B. There is no explicit greedy/deterministic planner comparator.** `PlannerId` contains `COT_SEQUENTIAL`, `TOT_BOUNDED_SEARCH` and `RAP_INSPIRED_MCTS`. `DeterministicCandidateEvaluator` is the common hard-constraint/evaluation boundary; it is not a separate decision procedure. The Part III requirements explicitly call for a greedy or deterministic heuristic plan-selection comparison. M8-B1 must add a fair evaluation-only deterministic/greedy baseline (or document and approve an equivalent explicit deterministic selection procedure) without prematurely changing the production hybrid router.

**C. Router thresholds are provisional.** `DefaultPlannerSelectionPolicy` uses fixed initial thresholds for branching factor, horizon, uncertainty, consequence severity, expansion budget and rollout availability. Existing documentation explicitly says these values require M8 evaluation. M8-B2 must measure them on matched cases before any threshold tuning.

**D. The application module is a composition seam, not a complete external deployment.** It has two Java source files and no module-local test sources. `Phase0Application` is a deterministic local smoke program using a fixed `ChatModel` response and LangGraph4j `MemorySaver`; it exercises the intake graph, not the full durable M6/M7 lifecycle. `CognitiveCycleApplicationGraph.compileFull(...)` supplies the complete graph composition entry point, but its `CognitiveCycleLifecyclePort` and `CognitiveCycleAuthorityPort` remain explicit host dependencies. M8-B1 must build a deterministic evaluation host using the simulator and test authority adapters and exercise the full graph. It must not invent a live ERP/IAM/carrier integration.

**E. External adapters and identity are deliberate trust boundaries.** The accepted M6 status says the current repository contains deterministic simulator and in-memory reference adapters, not a connected real ERP/WMS/supplier/carrier deployment or enterprise identity provider. For the pilot, evaluate strict ports and deterministic contract adapters. Live enterprise integration is a separate deployment concern and is not silently claimed by M8.

**F. Causal/learning claims need evidence.** The mechanism types/services exist, but M8 must measure whether outcomes change future memory/workspace admission or validated procedure behavior. Mere retrieval, admission or log presence is not evidence of downstream value or learning.

**G. B3 outcome attribution is not yet demonstrated.** `WorkspaceAdmissionService` and `WorkspaceBroadcastService` implement bounded admission, typed workspace state and consumer receipts. The inspected implementation does not establish a versioned, outcome-attributed admission-policy updater. M8 must distinguish current admission/broadcast behavior from adaptive learning. If no update operator exists in the accepted code, B3 learning is not evaluated; M8-B1/B3 must either add a bounded, auditable update path justified by the source mechanism or report the learning field as not applicable/unknown. Merely recording a receipt is not evidence that the policy learned.

**H. B6 resource accounting needs a durable host-level proof.** `MetaControlGovernor` evaluates uncertainty, deadline, resource margin and stop/continue decisions. The inspected `InMemoryResourceBudgetLedger` is a synchronized in-memory reference implementation, not a durable multi-process budget ledger. The lifecycle/host ports must supply authoritative budget snapshots and persist the corresponding decisions/reservations where execution requires them. M8-B1/B4 must test budget ownership, restart/interruption and concurrent resource claims through the evaluation host. Do not infer that M7 added a dedicated durable B6 budget ledger merely because the B6 governor and PostgreSQL operation ledger exist.

**I. Full-graph application coverage is not present in the app module.** The runtime graph has focused tests, but `supply-chain-agent-app` contains no module-local test source. M8-B1/B4 must exercise `CognitiveCycleApplicationGraph.compileFull(...)` through a deterministic host using explicit simulator, lifecycle and authority adapters, and assert business outcomes rather than only graph compilation/intake responses.

**J. Mechanism persistence is not uniform by design.** Durable business state, approvals, intentions, action receipts and graph checkpoints are persisted in M7. B3 workspace snapshots are cycle-scoped runtime context. M8 must distinguish durable state from intentionally ephemeral context and test the relevant restart semantics rather than assuming every transient mechanism state is a database record.

### 4.3 Framework-scope reconciliation

An earlier Part III draft labels LangGraph4j and LangChain4j as out of scope. The subsequently accepted M6 implementation explicitly uses LangGraph4j for orchestration and LangChain4j for model/provider interfaces, and the user accepted the full M6 CI. M8 therefore treats the accepted M6 framework choice as the current project decision. The book must explain the orchestration/framework boundary accurately; it must not describe the repository as framework-free. CoALA and Fan & Lan remain the theoretical foundations; CoT/ToT/RAP-inspired MCTS remain method-level comparators.

## 5. Known rollout controls — not M8 source gaps

The following remain deployment-owned and must stay visible on the production rollout checklist:

- enable and validate WAL archiving/PITR against deployment-specific RPO/RTO;
- approve retention durations, archival and legal holds with the data owner;
- establish authoritative tenant mapping before reassigning any records quarantined under `__legacy_unscoped__`;
- provision trusted identity and real external-system adapters when deploying beyond the deterministic pilot environment.

These are not a reason to reopen M7 or introduce M7-B3. The CI backup/restore smoke is not a substitute for a deployment-specific PITR exercise.

## 6. M8-B0 conclusion

The accepted source baseline is frozen and reproducibly identified. The architecture contains the intended building blocks, but the next phase must demonstrate integrated behavior over a broader corpus. The most important M8 implementation gap is the missing end-to-end evaluation host/corpus and the explicit deterministic/greedy comparator—not another persistence layer.
