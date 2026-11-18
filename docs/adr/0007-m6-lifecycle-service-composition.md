# ADR-0007 — Complete M6 lifecycle service composition

- **Status:** Proposed for CI acceptance (implementation delivered in M6-B)
- **Date:** 2026-11-17
- **Decision owners:** Project author

## Context

M6-A connected M2 retrieval, B1 selection, B3 workspace admission/broadcast and M5 planning/evaluation to the LangGraph4j cognitive-cycle graph. The remaining lifecycle operations were still delegated through a catch-all host `CognitiveCycleOperations` implementation. That seam made it possible for the graph to be present while actual memory, mechanism and action services were not composed.

M6 requires a concrete separation between framework orchestration, CoALA/Fan & Lan services, M5 planning and environment-specific supply-chain/authority adapters.

## Decision

Use `CognitiveCycleServiceOperations` as the complete graph-facing composition of `CognitiveCycleDecisionServices` (M6-A) and `CognitiveCycleLifecycleServices` (M6-B). `CognitiveCycleApplicationGraph.compileFull(...)` is the complete application composition path. The older arbitrary-host-operations overload is retained only as a deprecated migration seam.

M6-B calls the actual M2 working/episodic memory services, B2 impasse service, B4 intention service, B6 meta-control governor and M3 action dispatcher. Domain-specific translations and external/identity authority remain explicit `CognitiveCycleLifecyclePort` and `CognitiveCycleAuthorityPort` dependencies.

The lifecycle path enforces stable scoped identities, protected objectives, B2 parent/child authority and budgets, exact candidate-to-M3 command mapping, approval binding, authenticated principals, final deterministic revalidation, action idempotency, unknown-outcome reconciliation, versioned observation writes, idempotent episode recording and verified objective-satisfaction proof before B4 completion.

The current in-memory stores are reference adapters. M7 will replace them with durable transactional repositories and define restart/reconciliation guarantees.

## Consequences

### Positive

- The graph no longer needs a catch-all host delegate for its complete M6 operation contract.
- CoALA memory, Fan & Lan mechanisms, M5 planning and LangGraph4j orchestration remain separate and testable.
- Planners cannot execute actions or issue authority; all state-changing execution goes through M3 and authenticated grants.
- Approval refers to one exact plan and is revalidated immediately before dispatch.
- Unknown or partial external outcomes are reconciled rather than blindly retried.
- Verified objective completion and episodic recording are separate governed transitions.

### Trade-offs and limitations

- The application host must implement the domain and authority ports; no default adapter guesses business truth or user identity.
- Current action, observation, intention, B2 and memory repositories are in-memory references; M6 does not claim durable process-restart safety.
- B2 child read operations require step-level idempotency/receipts from the host adapter before process-restart durability is enabled.
- M6-B focused tests validate service composition but do not substitute for full graph execution tests, real external-system integration or M7 PostgreSQL crash/restart validation.

## Acceptance

Accept this ADR and M6 only after the full Java 21 Maven reactor passes in GitHub Actions with the previous 14 M6 graph tests, M6-A decision-service tests, M6-B service-integration tests, existing framework/PostgreSQL compatibility tests and application-host compilation. M8 must still complete system-level evaluation before application freeze.
