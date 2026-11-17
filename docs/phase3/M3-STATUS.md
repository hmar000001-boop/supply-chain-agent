# M3 status — CoALA internal and external action architecture

**Status: implementation package prepared; awaiting the user's GitHub Actions CI result.**

## Added

- New framework-neutral `supply-chain-agent-actions` Maven module.
- Typed action IDs, descriptors, categories/effects, input/output contracts, trusted principals, capability grants, resource references, preconditions, receipts, typed results/failures, registry and dispatcher.
- Idempotency ledger port with read-only inspection, atomic claim, completion and failure transitions; thread-safe in-memory reference adapter.
- Internal actions: deterministic memory retrieval, scoped/versioned working-memory update, unverified episodic record, and bounded structured reasoning.
- `ReasoningProvider` port plus LangChain4j stable-core AI Service adapter with typed POJO response, evidence-key validation, no tools and no business authority.
- Perception actions for supplier, inventory lot, available inventory, customer order, shipment and lot-level quality disposition, each returning versioned observations with evidence and provenance.
- Simulator-backed grounding actions for inventory reservation/release, purchase-order placement and shipment rerouting.
- Shipment rerouting transition in the deterministic simulator with status, route, carrier and transit-time validation plus an explicit `SHIPMENT_REROUTED` effect.
- Action catalog registers 14 actions: 4 internal, 6 perception and 4 grounding.
- Architecture documentation and ADR-0003 describing action, authority, effect, provenance, idempotency and adapter boundaries.

## Validation performed in the authoring environment

- Compiled domain, memory, simulator and action production sources with JDK 21 using `javac --release 21 -Xlint:all`.
- Compiled all domain, memory, simulator and action test sources against a local JUnit API compatibility shim.
- Executed the complete local regression set with a fresh test-class instance per test method, matching JUnit Jupiter's default `PER_METHOD` lifecycle: 102 methods passed, 0 failed.
- The 102 local methods comprise 14 domain tests, 43 memory tests, 20 simulator tests and 25 M3 action tests.
- The action tests cover authorization/resource scope, idempotency, safe retry, stale versions, memory boundaries, bounded reasoning output, evidence-citation validation, perception provenance, quality disposition, reservations, reservation release, purchase-order placement and rerouting.
- Simulator tests cover supported planned-shipment rerouting, in-transit rejection and minimum-transit-time enforcement.
- Parsed Maven POMs as XML and reviewed the reactor dependency direction.
- Static review confirms the action module contains no LangGraph4j or LangChain4j imports; only the runtime adapter depends on LangChain4j.

**Limitation:** these local test executions used a JUnit-compatible shim and reflection runner, not Maven Surefire/JUnit Platform. The LangChain4j runtime adapter was reviewed against the official LangChain4j 1.23.0 AI Services/structured-output documentation but could not be compiled locally without Maven dependencies. The clean GitHub Actions Maven reactor is the acceptance gate.

## Explicit limitations

- The action ledger is in-memory only. M7 must supply durable transactional inspect/claim/complete/fail behavior, including cross-process concurrency.
- The simulator-world adapter is in-memory and reference-only. No real supplier, ERP, WMS, carrier or procurement connector exists yet.
- M2 memory stores remain in-memory; M7 adds durable PostgreSQL adapters.
- The current receipt stores a stable result reference; duplicate calls return the receipt, not the full result payload. A durable result resolver is required in M7.
- The quality action exposes the lot's current disposition, not an underlying inspection report or trusted event-ingestion connector.
- LangChain4j returns a typed POJO. Strict JSON Schema enforcement depends on explicit provider/model support and configuration; it is not assumed universally.
- Human approval is not implemented by M3; grounding capabilities represent already-issued trusted authorization. B4/M6/M7 add persistent intention governance and plan-bound approval.
- M3 retrieval is exact deterministic M2 lookup, not B1 activation ranking.
- B1/B2/B3/B4/B6 mechanisms and CoT/ToT/MCTS planners remain future phases.

## Acceptance criteria

- 14 action handlers are registered without framework types in the catalog.
- Every state-changing action requires an idempotency key and a stable canonical fingerprint.
- Resource-scoped authorization covers all resources before execution.
- Precondition rejection does not consume the idempotency key.
- Exact completed duplicates do not execute a second time, even after a successful mutation changes the world/memory version.
- Unknown side-effect outcomes are not blindly retried.
- Perception returns provenance and snapshot version; read operations do not mutate the world.
- Grounding applies only simulator-validated transitions.
- LangChain4j adapter has no business tools and cannot authorize or execute an action.
- M0/M1/M2 tests remain in the reactor and pass in the user's clean CI run.

## Next

After M3 passes CI, proceed to M4 — Fan & Lan B1/B2/B3/B4/B6 mechanism implementation. B5/GraSP remains the reviewed closed-candidate boundary, not a newly claimed residual mechanism.
