# M4 status — Fan & Lan mechanism implementations

**Status: implementation package prepared; awaiting GitHub Actions CI acceptance.**

## Added

- New framework-neutral `supply-chain-agent-mechanisms` Maven module, depending on domain, CoALA memory and action contracts.
- B1 activation-aware retrieval, explicit latency/utility/maintenance scoring, bridge from M2 retrieval results and maintenance event accounting.
- B2 typed impasse detection, bounded read/reasoning child capability, action/deadline budgets, parent-version/objective protection and evidence-backed child result lifecycle.
- B3 mandatory/optional workspace admission, context/candidate limits, symmetric conflicts, required coverage reporting, immutable snapshot validation, complementary coalition record and scoped synchronous consumer broadcast.
- B4 persistent recovery-intention model with objective fingerprint, method reconsideration, suspension/resume, bounded delegation, evidence-bearing completion proof and termination.
- B6 required uncertainty dimensions, per-execution computational budget reservation/consumption, next-step resource-aware meta-control, deadlines, interruption/replenishment, safe stopping and low-resource reduction.
- ADR-0004, mechanism architecture and S/C/T/P/F/L/R traceability.

## Validation performed in the authoring environment

- Compiled all M4 production sources with JDK 21 using `javac --release 21 -Xlint:all`.
- Compiled all M1/M2/M3/M4 non-runtime test sources against a local JUnit API compatibility shim.
- Executed 172 non-runtime regression methods using a fresh test-class instance per method, matching JUnit Jupiter's default `PER_METHOD` lifecycle: 172 passed, 0 failed.
- The local set contains 14 domain, 43 memory, 20 simulator, 25 action and 70 mechanism tests.
- Parsed Maven POMs as XML and reviewed the dependency boundary.
- M4 has no LangGraph4j, LangChain4j or JDBC/PostgreSQL dependency.

**Limitation:** this local execution used a JUnit-compatible shim and reflection runner, not Maven Surefire/JUnit Platform. No full Maven reactor or M0 LangGraph4j runtime tests are claimed locally. GitHub Actions is the acceptance gate.

## Explicit limitations and deferred work

- M4 repositories and ledgers are in-memory reference adapters; M7 supplies durable transactional persistence and migrations.
- B1 activation/latency/utility scoring is an explicit configurable engineering approximation, not a claim of a paper-exact equation. Actual maintenance cost is recorded but does not automatically train the estimator.
- B2 returns evidence-backed findings only; parent revalidation and authorized parent mutation remain parent responsibilities.
- B3 broadcast is synchronous; durable outbox/replay and cross-process delivery belong to M7.
- B4 actor labels and proof references are contracts, not a complete external identity/authorization service; M6/M7 integrate trusted policy and proof verification.
- B6 covers computational/runtime resources, not business resources. Budget replenishment must be explicitly authorized by the application.
- B5/GraSP is not implemented as a residual mechanism.
- CoT/ToT/RAP-inspired MCTS are separate M5 algorithms; LangGraph4j composition is M6.

## Acceptance criteria

- M4's 70 mechanism tests pass in Maven Surefire in CI.
- M0/M1/M2/M3 tests remain in the reactor and pass.
- The module stays independent of agent framework and persistence implementations.
- All five applicable bundles have S/C/T/P/F/L/R traceability.
