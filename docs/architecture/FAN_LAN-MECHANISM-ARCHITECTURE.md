# Fan & Lan mechanism realization architecture

## Purpose and boundary

The `supply-chain-agent-mechanisms` module contains framework-neutral, executable engineering realizations of the applicable Fan & Lan residual mechanism bundles B1, B2, B3, B4 and B6. It depends on the domain, CoALA memory and action contracts, but it does not depend on LangGraph4j, LangChain4j, JDBC or PostgreSQL.

This separation is intentional:

- CoALA supplies the larger information/action/decision-procedure architecture.
- This module supplies bounded runtime capabilities and invariants for the selected mechanism bundles.
- M6 will compose these capabilities into the LangGraph4j execution lifecycle.
- M7 will replace in-memory repository adapters with durable, transactional persistence and operational governance.

A class name or graph node is not, by itself, evidence that a paper mechanism has been implemented. Each mechanism has explicit state, control, trigger, persistence, failure, learning and resource behavior. The details and limitations are recorded in `docs/phase4/M4-TRACEABILITY.md`.

## B1 — Activation-aware retrieval, downstream utility and maintenance

`B1MemoryCandidateFactory` adapts M2's `MemoryRetrievalResult` into typed B1 candidates without mutating the underlying memory records. It verifies scope and the relevant lifecycle/knowledge-time condition for working memory, verified episodes, active semantic facts and active procedural artifacts. A trusted `B1CandidateFeatureProvider` supplies features that are not part of the M2 memory records: access history, cue associations, expected downstream utility, context cost, measured I/O latency and a maintenance profile.

`RecencyFrequencyActivationEstimator` is an explicitly configurable engineering approximation. For a non-empty access history, it aggregates a recency/frequency strength using a configurable power decay and takes its logarithm. Query-cue associations contribute a separately weighted associative term. Empty history uses a configured low activation baseline. This is not presented as a paper-exact activation equation.

`B1RetrievalSelector` transforms activation into an accessibility estimate with a logistic function and applies an independently configured cognitive-latency model. It keeps predicted cognitive retrieval latency distinct from measured infrastructure I/O latency. Its net utility calculation is:

`P(access) × expected downstream utility − cognitive-latency penalty − infrastructure-latency penalty − estimated maintenance cost`.

Selection is constrained by context units and a maximum candidate count. `MemoryMaintenanceAccountingService` records actual maintenance events through an idempotent ledger. The accounting ledger does not automatically train the activation model or mutate retrieval policy; calibration and verified downstream-feedback loops remain future work.

## B2 — Typed impasse and bounded child resolution

`DeterministicImpasseDetector` consolidates typed signals emitted by validators. It groups by impasse type and subject, retains the highest severity and combines evidence references. It does not infer authoritative business facts from free text.

`ImpasseResolutionCase` captures a parent request, scope, working-memory version, protected objective fingerprint, child capability grant, deadline and action budget. Child capabilities must be both a subset of the parent's grant and members of a strict read/reasoning allow-list. The child returns `ChildResolutionResult`: evidence-backed findings, assumptions and a disposition. The result type has no command or parent-mutation field.

The parent case checks that its scope, request, version and objective fingerprint have not changed and that the child result matches the protected case snapshot. A positive child result completes only the child case; the parent remains responsible for validating the findings and deciding whether to continue. An unresolved child is escalated and retained for audit. A terminal case is not reopened in place; a subsequent attempt receives a new case identity and a fresh parent snapshot.

## B3 — Bounded workspace, admission, coalitions and broadcast

`WorkspaceAdmissionService` admits mandatory content first. If mandatory content exceeds the candidate-count or context budget, it returns a typed insufficient-capacity outcome without creating a partial snapshot. Optional candidates are considered deterministically using utility, reliability, risk and marginal coverage value. Explicit conflict declarations are checked symmetrically.

The resulting immutable `WorkspaceSnapshot` records admitted entries, newly covered tags, coverage, context accounting and complementary `WorkspaceCoalition` records. Snapshot construction validates unique candidate IDs, scope, capacity, newly covered tags and coalition accounting.

`WorkspaceBroadcastService` synchronously dispatches an admitted snapshot to selected consumers and returns a receipt for each consumer, including unknown consumers and failures. It checks scope, workspace state and that a consumer has not acknowledged a candidate outside the admitted snapshot. This is a synchronous reference implementation, not a durable message broker or transactional outbox. Durable delivery, retry/replay and cross-process receipt state belong to M7.

## B4 — Persistent intention and method governance

`RecoveryIntention` captures the active recovery objective, protected hard constraints, objective owner, a stable SHA-256 objective fingerprint, current method reference, lifecycle state, version, suspension return state and any active delegation. The fingerprint uses a versioned, length-prefixed UTF-8 encoding so user-controlled delimiters cannot create ambiguous field boundaries; set-valued objective fields are sorted before encoding.

Method changes are allowed only after an explicit reconsideration transition and only when the asserted objective fingerprint still matches. Suspension preserves whether the intention was active or awaiting reconsideration. Delegation is bounded and returns to active or reconsideration-pending according to the typed result. Completion requires an evidence-bearing proof that matches the protected objective and falls within the intention's time interval. Termination is time-monotonic and requires a named actor and reason.

The current repository is an in-memory, thread-safe compare-and-set reference adapter. The service enforces lifecycle transitions and optimistic version checks, but a durable authorization policy for who may terminate, approve completion or change governance records must be integrated at the application boundary in M6/M7. A proof object is not itself a cryptographic attestation; its verifier and evidence must be checked by the trusted application service.

## B6 — Uncertainty and meta-control/resource governor

`UncertaintyAssessment` requires evidence, hypothesis and plan uncertainty explicitly. Missing dimensions are rejected rather than silently treated as zero. `MetaControlGovernor` evaluates hard deadlines, actual budget overruns, the resource requirements declared for the next step and configured uncertainty thresholds. It can continue, request evidence, reduce computation, interrupt for human review or authorized budget replenishment, or stop.

The governor compares only the resources needed by the next step. An exhausted MCTS rollout allowance does not block a CoT step that consumes no rollouts. Business resources such as inventory, supplier capacity and money are deliberately excluded from `ResourceUnit`; those remain authoritative domain resources.

`InMemoryResourceBudgetLedger` supports concurrent reservations, idempotent reservation IDs, commit/release lifecycle and actual-usage accounting. Actual overruns are retained. Aggregation uses saturating arithmetic so `long` overflow cannot wrap a recorded overrun into a negative amount and conceal it. `Long.MAX_VALUE` is the explicit unlimited sentinel. This is a reference in-memory ledger; transactional, durable accounting, replenishment authorization and cross-process enforcement remain M7 responsibilities.

## Explicit non-goals

- The module does not implement B5/GraSP. It preserves the previously reviewed B5/GraSP boundary and does not claim a new residual mechanism.
- The module does not implement CoT, ToT or MCTS. Those are separate decision algorithms in M5.
- The module does not implement LangGraph4j nodes or subgraphs. M6 composes the capabilities into the outer cognitive decision procedure.
- The module does not provide PostgreSQL persistence, durable delivery, external approvals or real supply-chain connectors. Those are application/infrastructure concerns scheduled for M6/M7.
- The engineering formulas and policies are explicit domain adaptations and must not be described as exact reproductions of the source papers unless a separate paper-fidelity analysis establishes that claim.
