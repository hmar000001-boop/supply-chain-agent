# M6 status — LangGraph4j cognitive orchestration

**Accepted — the user confirmed GitHub Actions CI green after M6-A and M6-B on 2026-11-17.** The uploaded M7 starting repository contains both M6 deliveries, the graph correction and the complete service composition. The exact final CI log was not included in the upload, so this status records the user's acceptance report without inventing a post-run test count.

## Accepted capabilities

- `CognitiveCycleOperations` defines the typed graph/application contract; `CognitiveCycleState` carries compact identifiers, versions, control metadata and references.
- `CognitiveCycleGraph` is the cyclic LangGraph4j controller; `B2ResolutionSubgraph` is a bounded child graph.
- Explicit interrupt/resume boundaries exist for clarification, external evidence, human control and approval.
- Approval binds the exact candidate set, candidate, intention and intention version. Human identity is verified against host-authenticated responses.
- Unknown external outcomes route to observation/reconciliation rather than blind retries. Observation writes use optimistic working-memory versions.
- M6-A composes M2 retrieval, B1 activation/selection, B3 workspace admission/broadcast and M5 planner routing/evaluation.
- M6-B adds `CognitiveCycleServiceOperations`, composing M6-A with actual M2 working/episodic services, B2 impasse resolution, B4 intention governance, B6 meta-control and M3 action dispatch.
- The complete application path is `CognitiveCycleApplicationGraph.compileFull(...)`; the arbitrary host-operations overload remains deprecated as a migration seam.

## External trust boundaries

`CognitiveCycleLifecyclePort` remains the required supply-chain/domain adapter for authoritative scope/objective data, evidence ingestion, bounded child-read plans, parent snapshots, fresh planning requests, typed M3 command construction, external observation/reconciliation, outcome drafts and verified objective-satisfaction proofs.

`CognitiveCycleAuthorityPort` remains the trusted host adapter for approval persistence/resolution/expiry/revocation, authenticated human-control responses, B4 termination authority and resource-scoped `ActionPrincipal`s. Neither has a permissive default. The reference simulator is not an ERP/WMS/supplier/carrier integration or enterprise identity provider.

## Handoff to M7

M6's in-memory reference stores were accepted as orchestration/service-composition adapters, not crash-durable production stores. M7 replaces the memory, planner, approval, action-operation, child-step and observation stores with durable repositories and adds transaction boundaries, idempotency, outbox/inbox, restart recovery, concurrency control and operational hardening.

Chapters 19 and 20 remain unwritten until M8 freezes the complete application, as agreed.
