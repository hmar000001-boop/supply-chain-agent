# M6 traceability — CoALA, Fan & Lan, planning and LangGraph4j

The table identifies which layer owns each responsibility and which M6 service-backed operations invoke it. It does not claim that LangGraph4j implements the academic mechanisms or planning algorithms.

| Concern | M6 graph/runtime boundary | Actual service or owner | Persistence owner | Failure / recovery invariant |
|---|---|---|---|---|
| CoALA working memory | load context; accept evidence; write observation | M2 `WorkingMemoryService` via M6-B lifecycle service | Working-memory repository | Stable ID is scope-bound; version conflicts reload; persisted observations are retried idempotently, not external actions |
| CoALA episodic memory | retrieval; record outcome | M2 `EpisodicMemoryService` via M6-B | Episode repository | Outcomes are append-idempotent by scope/observation reference; conflicting payload reuse fails; records remain unverified until review |
| CoALA semantic memory | evidence retrieval | M2 retrieval plus authoritative domain store | Semantic fact repository/domain store | Provenance, validity and authoritative source precedence are required |
| CoALA procedural memory | context, planning and action definitions | M2 procedural memory and versioned Java capabilities | Procedural repository / reviewed deployment | Model output cannot mutate or activate production procedures |
| CoALA internal actions | retrieve, interpret, reason, memory update, learning | M2/M3 services and domain reasoning adapter | Corresponding memory repositories | Typed inputs/results; learning never self-verifies experience |
| CoALA external actions | perception, grounding, action execution | M3 `ActionDispatcher` with typed domain commands | Operation ledger / external system | Capability grants and preconditions; idempotency and reconciliation; no arbitrary model command |
| B1 | retrieval selection | M4 `B1MemoryCandidateFactory`, `B1RetrievalSelector` | B1 metrics/policy state | Accessibility, latency, utility and maintenance-aware bounded selection |
| B2 | bounded child graph and lifecycle operations | M4 `ImpasseResolutionService` plus M6-B child adapter | B2 repository + child-result artifact; step receipts are M7 work | Capability/action budget checks; parent scope/version/objective validation; compensation on failed start |
| B3 | workspace admission and broadcast | M4 `WorkspaceAdmissionService`, `WorkspaceBroadcastService` | Workspace repository | Planning only after complete admission and consumer acknowledgement |
| B4 | intention governance, delegation, suspension, completion | M4 `RecoveryIntentionService` | Intention repository | Objective fingerprint protected; method authority is bounded; termination requires trusted actor; completion requires verified proof |
| B6 | resource/uncertainty governance | M4 `MetaControlGovernor` | Host budget ledger / resource repository | Execution-bound budgets; hard business constraints cannot be relaxed; stop/interrupt routes honored |
| CoT / ToT / MCTS | planner dispatch and bounded repeat | M5 `PlannerRouter` and independent strategies | Planner session store | Search session is separate from graph state; no planner authority to execute |
| Candidate evaluation | candidate evaluation node and final pre-execution revalidation | M5 common evaluator + M1 simulator | Planning artifact store | Reconstruct trusted actions and replay; infeasible candidates never selected by score |
| Approval | prepare, interrupt, resolve, revalidate | M6-B `CognitiveCycleAuthorityPort` + registered M3 descriptors | Approval repository | Exact request/execution/scope/candidate/candidate-set/intention/action fingerprint and authenticated approver |
| Execution | authorized execution node | M6-B typed command + M3 dispatcher | Action batch + M3 idempotency ledger | Authenticated principal, stable batch/step keys; ambiguous outcomes reconcile, never blind retry |
| Observation | observe/reconcile and graph observation loop | M6-B domain adapter + observation store | Observation repository / authoritative systems | Reconcile existing operation receipts; no repeated execution |
| Learning/completion | update WM and record outcome | M2 memory services + B4 intention service | WM/episode/intention repositories | Episode remains unverified; completion proof must match protected objective and reconciled observation |
| Framework lifecycle | route, cycle, interrupt, resume, checkpoint | LangGraph4j `CognitiveCycleGraph` and B2 subgraph | LangGraph4j checkpoint saver | Checkpoint is orchestration state only; durable domain/memory/approval/operation records remain separate |

## Seven-field analysis of the M6 runtime

- **S — State:** compact typed graph state with request/execution IDs and references to M2 memory, evidence, B3 workspace, B4 intention, M5 session/candidate set, approval, operation and observation. Full artifacts remain in their owning stores.
- **C — Control:** LangGraph4j controls sequencing; M1 owns business invariants; M2 owns memory lifecycle; M3 owns capability/dispatch; M4 owns B1/B2/B3/B4/B6 policy; M5 owns candidate search/evaluation; host authority owns human identity and approval.
- **T — Trigger:** request/new observation, typed impasse, workspace/evidence outcome, planner termination, resource/uncertainty decision or authenticated host response.
- **P — Persistence:** checkpoint stores graph continuation; M2 repositories store memory; B2/B4 stores own child/intention lifecycle; M5 stores planner sessions; host stores approval/budget; M3 ledger stores action operation; M6 references observation/artifacts. Current runtime test adapters are in-memory; M7 replaces them with durable repositories.
- **F — Failure:** typed impasse, evidence interruption, stale approval, B4 suspension, B6 stop, action rejection/unknown outcome, observation reconciliation, optimistic version conflict and bounded escalation. Failed child startup compensates B2/B4 transitions.
- **L — Learning:** observations update WM; outcome episodes are append-idempotent but unverified; explicit domain proof is required for B4 objective completion; future episodic verification and semantic/procedural promotion remain governed by M2 policy.
- **R — Resources:** B2 child-action budget, B6 compute/resource budget, M5 search budget, deadlines, graph loop bounds, M3 principal scopes and real business resource constraints are distinct and independently enforced.

## Non-equivalences and invariants

1. A LangGraph4j node is not CoT, ToT or MCTS.
2. A graph checkpoint is not episodic memory or the complete CoALA working-memory store.
3. A planner candidate is not an M3 command or an authorization.
4. An approved plan is still revalidated against current state before dispatch.
5. A model-generated statement is not an objective-satisfaction proof.
6. An episodic record is not verified merely because B4 intention completion is proven.
7. `UNKNOWN_OUTCOME` requires observation/reconciliation, never a blind retry.
8. A B2 child is restricted to the delegated capabilities/action budget and cannot change the parent objective.
9. The graph state cannot create authenticated identity, authority, approval or an action principal.
10. Current in-memory stores do not prove process-restart safety; that belongs to M7/M8.
