# M3 status — CoALA internal and external action architecture

**Status: accepted — the user reported that GitHub Actions CI is green for M3.**

## Added

- New framework-neutral `supply-chain-agent-actions` Maven module.
- Typed action IDs, descriptors, categories/effects, input/output contracts, trusted principals, capability grants, resource references, preconditions, receipts, typed results/failures, registry and dispatcher.
- Idempotency ledger port with read-only inspection, atomic claim, completion and failure transitions; thread-safe in-memory reference adapter.
- Internal actions: deterministic memory retrieval, scoped/versioned working-memory update, unverified episodic record, and bounded structured reasoning.
- `ReasoningProvider` port plus LangChain4j stable-core AI Service adapter with typed POJO response, evidence-key validation, no tools and no business authority.
- Perception actions for supplier, inventory lot, available inventory, customer order, shipment and lot-level quality disposition, each returning versioned observations with evidence and provenance.
- Simulator-backed grounding actions for inventory reservation/release, purchase-order placement and shipment rerouting.
- Shipment rerouting transition in the deterministic simulator with status, route, carrier and transit-time validation plus an explicit `SHIPMENT_REROUTED` effect.
- The compact compatibility catalog registers 14 legacy actions (4 internal, 6 perception, 4 grounding). The complete durable application catalog registers 23 actions (3 internal, 10 versioned learning, 6 perception, 4 grounding); see `docs/architecture/COALA-ACTION-ARCHITECTURE.md`.
- Architecture documentation and ADR-0003 describing action, authority, effect, provenance, idempotency and adapter boundaries.

## Validation record

- The user reported M3 GitHub Actions CI green. This is the acceptance signal for the complete Maven reactor at M3.
- The authoring-environment supplemental local regression set previously compiled domain, memory, simulator and action sources with Java 21 and executed 102 methods using a JUnit-compatible reflection harness. This was not Maven Surefire and is not substituted for the CI result.

## Explicit limitations

- The action ledger is in-memory only. M7 supplies durable transactional inspect/claim/complete/fail behavior, including cross-process concurrency.
- The simulator-world adapter is in-memory and reference-only. No real supplier, ERP, WMS, carrier or procurement connector exists yet.
- M2 memory stores remain in-memory; M7 adds durable PostgreSQL adapters.
- The current receipt stores a stable result reference; duplicate calls return the receipt, not the full result payload. A durable result resolver is required in M7.
- The quality action exposes the lot's current disposition, not an underlying inspection report or trusted event-ingestion connector.
- LangChain4j returns a typed POJO. Strict JSON Schema enforcement depends on explicit provider/model support and configuration; it is not assumed universally.
- Human approval is not implemented by M3; grounding capabilities represent already-issued trusted authorization. B4/M6/M7 add persistent intention governance and plan-bound approval.
- M3 retrieval is exact deterministic M2 lookup, not B1 activation ranking; B1 is implemented in M4.

## Acceptance criteria

- 14 action handlers are registered without framework types in the catalog.
- Every state-changing action requires an idempotency key and stable canonical fingerprint.
- Resource-scoped authorization covers all resources before execution.
- Precondition rejection does not consume the idempotency key.
- Exact completed duplicates do not execute a second time, even after a successful mutation changes the world/memory version.
- Unknown side-effect outcomes are not blindly retried.
- Perception returns provenance and snapshot version; read operations do not mutate the world.
- Grounding applies only simulator-validated transitions.
- LangChain4j adapter has no business tools and cannot authorize or execute an action.
- M0/M1/M2/M3 tests passed in the user's reported green CI run.

## Next

M4 implements Fan & Lan B1/B2/B3/B4/B6. B5/GraSP remains the reviewed closed-candidate boundary, not a newly claimed residual mechanism.
