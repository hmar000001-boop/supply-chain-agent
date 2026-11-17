# M6-A — Service-backed decision pipeline (delivery 1/2)

**Status: implementation delivered; five focused service-composition tests passed in the authoring harness. Full Maven/GitHub Actions validation is pending.**

M6 is intentionally split into two independently reviewable deliveries. M6-A connects the decision/evidence path to real M2, B1, B3 and M5 services. M6-B will complete the remaining `CognitiveCycleOperations` implementation for B2, B4, B6, M3 grounding/authorization and memory learning, then exercise the complete graph with the real composed services.

## Added

- `CognitiveCycleDecisionPort`: explicit supply-chain/domain translation port. It constructs typed retrieval, B1, workspace and planning inputs; stores evidence selection and broadcast receipts. It does not perform retrieval, ranking, workspace admission, planner selection or candidate evaluation.
- `CognitiveCycleDecisionServices`: direct composition of:
  - M2 `MemoryRetrievalService`;
  - B1 `B1MemoryCandidateFactory` and `B1RetrievalSelector`;
  - B3 `WorkspaceAdmissionService` and `WorkspaceBroadcastService`;
  - M5 `PlannerRouter`, `PlanningWorldModel` and common `CandidateEvaluator`.
- `PlanningArtifactStore` and the thread-safe `InMemoryPlanningArtifactStore` reference adapter. The full `PlanningRequest` and `PlanningResult` are kept outside the LangGraph4j state and are retrieved by the candidate-set reference for deterministic evaluation. The in-memory adapter is not claimed as durable production persistence; M7 supplies the durable adapter.
- `CognitiveCycleDecisionOperations`: graph-facing decorator that routes evidence retrieval, workspace admission/broadcast, planner invocation and candidate evaluation through the real decision services, while forwarding the remaining lifecycle/control operations to the host delegate for M6-B.
- `CognitiveCycleApplicationGraph`: application-level composition entry point that wraps host operations with the decision pipeline before compiling `CognitiveCycleGraph`.
- Five focused tests covering M2 retrieval → B1 selection, successful B3 admission plus acknowledgement, fail-closed broadcast handling, M5 routing/artifact retrieval, and deterministic selection of a simulator-replayed feasible candidate.

## Behavioral guarantees

- The retrieval scope, B1 scope and current memory scope must match.
- B1 selection operates on the real M2 retrieval result; the host port only provides trusted feature estimates and persists the selected evidence-set record.
- B3 admission is performed by the real admission service. The graph is told `ADMITTED` only when every configured broadcast consumer acknowledges the workspace.
- Missing mandatory capacity maps to `INSUFFICIENT_CAPACITY`; incomplete coverage maps to `INCOMPLETE`; failed/missing broadcast acknowledgement maps to `FAILED`.
- B3 and M5 require the persisted evidence-set reference; M5 additionally requires an admitted workspace snapshot.
- M5 planning inputs must match the current graph execution, working-memory ID/version and memory scope.
- Candidate-set artifacts are stored outside graph state. Evaluation verifies execution and working-memory identity/version, reconstructs each candidate's action path through the trusted planning world model, checks canonical action definitions, and applies the common deterministic evaluator again.
- Infeasible candidates cannot be selected based on planner-provided scores. Feasible candidates are ordered by deterministic score, with candidate ID as the tie-breaker.
- Search/resource-budget exhaustion with no candidate returns `NEEDS_MORE_SEARCH`; other empty/infeasible results fail closed.

## Explicit boundaries

The domain port remains responsible for converting real supply-chain records into `MemoryRetrievalQuery`, `B1SelectionRequest`, `WorkspaceAdmissionRequest` and a complete `PlanningRequest`, and for durably storing evidence/receipt references. Those are not generic cognitive services and must not be fabricated inside graph nodes.

The M6-A graph decorator deliberately delegates the remaining operations to the host implementation. It is an integration slice, not the completed M6 production application. M6-B must replace that remaining host delegate with the complete application implementation using:

- `WorkingMemoryService` and `EpisodicMemoryService` for working-memory lifecycle and verified/unverified episode recording;
- `ImpasseResolutionService` for the B2 child lifecycle;
- `RecoveryIntentionService` for B4 objective/method governance;
- `MetaControlGovernor` for B6 budget and uncertainty decisions;
- M3 `ActionDispatcher` for read/write external actions, with durable approval and observation/reconciliation adapters;
- end-to-end graph tests using those actual services rather than scripted `CognitiveCycleOperations`.

Durable PostgreSQL stores for memory, workspace, planner artifacts, intentions, approvals and operation ledgers remain M7 work. The M6-A in-memory planning artifact store is an explicit reference implementation only.
