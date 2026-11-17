# M4 — Fan & Lan mechanism implementations

Date: 2026-11-17

## Purpose

M4 adds explicit, independently testable, framework-neutral engineering realizations for the applicable Fan & Lan mechanism bundles B1, B2, B3, B4 and B6. It builds on the accepted M1 domain/simulator, M2 CoALA memory and M3 action contracts. It does not add graph orchestration or decision algorithms.

## Added

### New `supply-chain-agent-mechanisms` Maven module

The module depends on the domain, CoALA memory and action contracts. It deliberately has no LangGraph4j, LangChain4j, JDBC or PostgreSQL dependency.

- **B1 — Activation-aware retrieval:** bridges M2 working, episodic, semantic and procedural retrieval results into typed candidates; validates scope and lifecycle/knowledge-time; uses a configurable recency/frequency and cue-association activation estimate; keeps predicted cognitive retrieval latency separate from measured infrastructure latency; ranks candidates using accessibility, downstream utility, latency penalties and maintenance cost; applies candidate/context budgets; and records actual maintenance events through an idempotent ledger.
- **B2 — Impasse resolution:** consolidates validator-emitted typed signals; creates a bounded child-resolution case tied to the parent scope, request, working-memory version and objective fingerprint; restricts child capabilities to a parent-granted read/reasoning allow-list; enforces deadline/action limits; and accepts only evidence-backed findings. A child result never mutates the parent objective or executes a parent action.
- **B3 — Workspace:** protects mandatory content; performs deterministic optional admission under context/count limits; checks candidate conflicts symmetrically; rewards new required coverage; reports incomplete coverage without misrepresenting it as complete; emits immutable snapshots and explicit coverage-complementarity records; and broadcasts admitted snapshots to scoped consumers with per-consumer receipts.
- **B4 — Intention governance:** stores a recovery intention around an immutable objective fingerprint and protected constraints; permits method changes only after reconsideration and fingerprint verification; models suspension/resumption and bounded delegation return; requires evidence-bearing objective completion proof; and enforces optimistic version checks and terminal-state rules.
- **B6 — Meta-control:** requires evidence, hypothesis and plan uncertainty assessments; tracks per-execution computational resource reservations and actual usage; checks next-step requirements against remaining budgets; and returns typed continue, evidence-request, reduced-computation, human/budget-interruption or stop decisions. Business inventory, money and supplier capacity remain domain resources and are not conflated with computational units.

### Documentation

- Added ADR-0004 for the mechanism/framework boundary.
- Added mechanism architecture and S/C/T/P/F/L/R traceability.
- Added M4 status and test-results records.
- Updated README and M3 status to reflect the user-reported green M3 CI result and current M4 milestone.

## Important boundaries and limitations

- The activation, cognitive-latency, utility and workspace scoring functions are explicit configurable engineering approximations. They are not claimed to reproduce the source paper's equations exactly.
- B1's maintenance ledger records actual maintenance cost; it does not autonomously train the activation estimator or calibrate downstream utility.
- B2 child resolution returns findings only. Parent-side evidence verification and any parent mutation remain the parent/application's responsibility.
- B3 broadcast is synchronous and in-memory; durable delivery/outbox/replay is M7 work. Consumer receipts provide outcome attribution, but no automatic policy training is claimed.
- B4 uses an in-memory compare-and-set repository. The actor/proof fields are contracts, not a complete identity, authorization or cryptographic attestation service.
- B6 uses an in-memory reference budget ledger. Durable transactional accounting and authorized replenishment are M7 work.
- B5/GraSP is not implemented as a residual mechanism; it remains the reviewed closed-candidate/comparison boundary.
- CoT/ToT/RAP-inspired MCTS are M5 planner implementations. LangGraph4j composition is M6. Durable PostgreSQL adapters are M7.

## Validation performed in the authoring environment

- Compiled 200+ main-source files across the framework-neutral domain, memory, simulator, action and mechanism modules with JDK 21 (`--release 21 -Xlint:all`).
- Compiled the complete corresponding non-runtime test sources with JDK 21 against a small JUnit API compatibility shim.
- Executed 172 test methods with a fresh test-class instance per method, matching JUnit Jupiter's default `PER_METHOD` lifecycle: 172 passed, 0 failed.
- The 172-method set comprises 14 domain, 43 memory, 20 simulator, 25 action and 70 mechanism tests.
- Parsed Maven POMs and inspected module dependency boundaries.

**Limit:** this local harness is supplemental. It is not Maven Surefire/JUnit Platform and does not run the M0 LangGraph4j runtime/PostgreSQL tests. The user's GitHub Actions clean Maven reactor remains the acceptance gate for M4.

## File disposition

- Added: new mechanism module, ADR, architecture/traceability/status/test documentation.
- Modified: root Maven reactor, README and M3 status record.
- Deleted: none.
