# M6 status — LangGraph4j cognitive orchestration

**The corrected M6 graph-core CI was reported green by the user. The user marked M6-A (delivery 1/2) complete. M6-B (delivery 2/2) implementation is now delivered; full M6 acceptance is pending the next complete Maven/GitHub Actions reactor run.**

M6 has two deliveries in addition to the graph-core/CI correction work. M6-A implements the real decision-service path; M6-B implements the remaining lifecycle service path. The framework remains the outer execution orchestrator. CoALA memory, Fan & Lan mechanisms, M5 planning algorithms, M1 domain invariants and M3 authorization/action execution remain their own services.

## Graph core and CI correction

- `CognitiveCycleOperations` defines the typed graph/application boundary.
- `CognitiveCycleState` carries compact identifiers, versions, bounded control metadata and references, not whole memory corpora, planning trees or authoritative domain records.
- `CognitiveCycleGraph` is the cyclic LangGraph4j controller; `B2ResolutionSubgraph` is a bounded child graph.
- Explicit interrupt/resume boundaries exist for clarification, external evidence, human control and approval.
- State-changing plans cannot use the approval-free path. Approval binds the exact candidate set, candidate, intention and intention version. Actor identity is verified against host-authenticated responses.
- Unknown external outcomes go to observation/reconciliation, not a blind execution retry. Observation writes use working-memory optimistic versions.
- The first full CI log had one B2 reason-propagation error and two tests that assumed unwrapped exceptions. Both corrections were applied. The user confirmed the corrected CI run green; its updated log was not supplied, so no post-correction total is asserted here.

## M6-A — decision-service composition (delivery 1/2)

M6-A connects the real:

- M2 `MemoryRetrievalService`;
- B1 `B1MemoryCandidateFactory` and `B1RetrievalSelector`;
- B3 `WorkspaceAdmissionService` and `WorkspaceBroadcastService`;
- M5 `PlannerRouter`, `PlanningWorldModel` and deterministic `CandidateEvaluator`.

The complete planner request/result and candidate artifacts remain outside graph state behind `PlanningArtifactStore`. Planning only follows successful workspace admission and required consumer acknowledgements. Candidate evaluation reconstructs canonical action paths, replays through the trusted M1 simulator and rejects infeasibility independently of planner scores.

The user marked M6-A complete. Its five focused tests are documented in `M6A-TEST-RESULTS.md`; the specific M6-A CI output was not included in this conversation.

## M6-B — remaining lifecycle service composition (delivery 2/2)

M6-B adds `CognitiveCycleServiceOperations`, the graph-facing composition of M6-A decisions with `CognitiveCycleLifecycleServices`. It implements every `CognitiveCycleOperations` method without a catch-all host operations delegate. `CognitiveCycleApplicationGraph.compileFull(...)` is the complete composition entry point; the old arbitrary-host overload remains deprecated as a migration seam.

The lifecycle implementation calls the actual M2 `WorkingMemoryService` and `EpisodicMemoryService`; M4 B2 `ImpasseResolutionService`, B4 `RecoveryIntentionService` and B6 `MetaControlGovernor`; and M3 `ActionRegistry` / `ActionDispatcher`. It includes stable scoped memory/intention identities, B2 capability/action-budget enforcement and parent return validation, B4 protected-objective governance, B6 execution-bound budget handling, exact typed candidate-to-action mapping, authenticated principal binding, approval revalidation, operation batch idempotency, external observation reconciliation, versioned working-memory writes and verified objective-completion proof.

Twelve focused M6-B tests passed through a local Java 21 lightweight JUnit-compatible harness against actual M2/M3/B2/B4/B6 service classes. They are not Maven/Surefire/JUnit Platform results. See `M6B-STATUS.md` and `M6B-TEST-RESULTS.md`.

## Explicit integration and persistence boundaries

`CognitiveCycleLifecyclePort` remains the required supply-chain/domain adapter for authoritative scope/objective data, evidence ingestion, bounded child-read plans, current parent snapshots, fresh planning requests, typed M3 command construction, external observation/reconciliation, outcome drafts and verified objective-satisfaction proofs.

`CognitiveCycleAuthorityPort` remains the trusted host adapter for approval persistence/resolution/expiry/revocation, authenticated human-control responses, B4 termination authority and issuance of resource-scoped `ActionPrincipal`s. Neither port has a permissive default implementation. The repository includes the deterministic simulator/reference environment; it is not connected to a real ERP/WMS/supplier/carrier or enterprise identity/approval provider.

M6-B's plan, child-result, action-batch and observation stores are in-memory reference implementations. M2, B2/B4 and M3 currently use their existing in-memory adapters in the local environment. B2 step-level external read receipts are not crash-durable. M7 must replace these stores with transactional PostgreSQL repositories, persist approvals/operations/child-step receipts and define restart reconciliation. M8 then performs full application evaluation, ablation, concurrency/failure testing and code freeze.

## Overall M6 acceptance gate

M6 is not marked accepted until GitHub Actions passes the full Java 21 Maven reactor after applying M6-B. Require the corrected 14 graph tests, all M6-A/M6-B tests, the prior M0–M5 regression suite, framework/PostgreSQL checkpoint compatibility tests and application-host compilation. The exact CI command is:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Chapters 19 and 20 remain unwritten until M8 freezes the complete application, as agreed.
