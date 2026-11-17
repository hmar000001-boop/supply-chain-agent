# M3 — CoALA internal and external action system

Date: 2026-11-17

## Purpose

M3 introduces the explicit action layer between CoALA's memory/domain responsibilities and the later LangGraph4j orchestration. The action module is framework-neutral. The LangChain4j adapter remains in the runtime module.

## Added

### New `supply-chain-agent-actions` Maven module

- Typed action IDs, categories, effects, descriptors, handlers, input/output contracts, principals, capabilities, resource references, preconditions, receipts, results and failure classifications.
- Action registry and dispatcher with exact input/output type checks.
- Capability and complete resource-scope authorization before preconditions or execution.
- Idempotency ledger port with read-only inspection, atomic claim, completion and failure transitions; thread-safe in-memory reference implementation for tests/local runs.
- Internal actions for M2 memory retrieval, scoped/versioned working-memory update, append-only unverified episode recording and bounded structured reasoning.
- Perception actions for suppliers, inventory lots, available inventory, customer orders, shipments and lot-level quality disposition. Each produces a versioned observation with provenance and evidence references.
- Grounding actions for inventory reservation/release, purchase-order placement and shipment rerouting against the deterministic M1 simulator.
- A catalog registering 14 actions: 4 internal, 6 perception and 4 grounding.

### LangChain4j model adapter

- A narrow AI Service that returns a typed reasoning assessment.
- No model tools are exposed; the provider cannot authorize or execute operations.
- Returned evidence keys are resolved only against references supplied by the caller; unsupported citations are rejected.
- Output is bounded and stores concise rationale/findings, not private chain-of-thought transcripts.
- JSON Schema enforcement remains provider/model-specific and must be enabled explicitly; the implementation does not claim universal schema enforcement.

### M1 simulator extension

- Added a guarded shipment-reroute command and explicit `SHIPMENT_REROUTED` effect.
- Rerouting requires a planned or delayed shipment, an active target route, an active carrier supporting that route, a route change and an ETA meeting the route's minimum transit time.
- In-transit shipments are rejected. The effect identifies impacted customer orders.
- Added simulator tests for successful planned rerouting, in-transit rejection and minimum-transit-time rejection.

### Documentation

- Added action architecture documentation, ADR-0003, M3 status and local test report.
- Updated README, M1/M2 status records and Maven module configuration.

## Important behavioral decisions

- A state-changing action requires an idempotency key and canonical request fingerprint.
- The dispatcher first performs read-only ledger inspection so an exact completed replay can be acknowledged even if the successful operation changed the current world/memory version.
- Mutable preconditions are checked before an idempotency claim so a rejected request does not consume its key.
- An atomic ledger claim follows those checks and is authoritative for concurrent requests.
- Unexpected failures during a state-changing operation are classified as unknown outcome and are not blindly retryable.
- The M3 ledger and simulator world are in-memory reference adapters only. They are not cross-process or production-durable implementations.

## Local validation

- Java 21 production sources for domain, memory, simulator and actions compiled with `javac --release 21 -Xlint:all`.
- Domain, memory, simulator and action test sources compiled against a local JUnit API compatibility shim.
- The corrected reflection runner creates a new test instance per method, matching JUnit Jupiter's default lifecycle.
- 102 local regression test methods passed: 14 domain, 43 memory, 20 simulator and 25 action tests.
- This is supplementary authoring-environment validation, not a Maven Surefire/JUnit Platform run. The user's GitHub Actions clean Maven reactor remains the authoritative M3 acceptance gate.

## Deferred

- Durable PostgreSQL action ledger, memory repositories, migration execution and result resolver: M7.
- Real ERP, WMS, supplier, carrier and procurement adapters: later production integration work; M3 grounding is simulator-backed.
- Human approval and persistent intention governance: B4/M6/M7.
- B1 activation-based retrieval and B2/B3/B4/B6 mechanisms: M4.
- CoT/ToT/MCTS decision engines: M5.
- Integration of this action catalog into the main LangGraph4j cognitive graph: M6.

No files were deleted by M3.
