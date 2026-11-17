# M6 traceability — theory/mechanism to orchestration

The table identifies the runtime boundary that invokes each previously implemented capability. It does not claim that LangGraph4j implements the academic mechanism or algorithm. The delegated operation adapter is responsible for calling the corresponding concrete service.

| Concern | M6 graph node / contract | State consumed | Trigger | Persistence owner | Failure/recovery | Learning/resource rule |
|---|---|---|---|---|---|---|
| CoALA working memory | `load-context`; `updateWorkingMemoryFromObservation` | memory scope/id/version, observation ref | request start, post-action observation | M2 working-memory repository | version conflict reloads and retries the persisted observation without re-executing the external action; update failure escalates | only evidence-linked writes; optimistic version check |
| CoALA episodic memory | `retrieve-evidence`; `recordOutcomeEpisode` | evidence set, episode/outcome refs | retrieval, verified objective observation | M2 episodic repository | typed retrieval failure/impasse; episode is not self-promoted | raw episode is recorded unverified until policy validation |
| CoALA semantic memory | `retrieve-evidence` | semantic evidence-set ref and provenance | task-conditioned retrieval | M2 semantic repository plus authoritative domain store | stale/conflicting facts feed B2/evidence request | no model-only auto-promotion |
| CoALA procedural memory | `load-context`, `propose-candidates`, `prepare-authorization` | intention/procedure/plan refs | context load, planning, capability check | M2 procedural repository and versioned application code | invalid/retired procedure rejected by delegated service | procedure/code changes require reviewed deployment |
| Internal/external actions | retrieve, interpret, update WM, episode, observe, execute | typed request/result IDs | cognitive-cycle stages | M3 action registry, ledgers and memory stores | typed failures; action idempotency and reconciliation | writes require capability authority |
| B1 | retrieve-evidence | evidence-set ref, uncertainty and retrieval outcome | after context load | M2 + M4 B1 policy/metrics | insufficient evidence loops or B2 | activation, latency, utility, maintenance budgets in B1 |
| B2 | compiled child graph | impasse case, parent memory version, finding ref | typed impasse or incomplete workspace | M4 B2 repository/service | bounded child scope; parent rejects invalid/stale return | no direct parent objective/capability mutation |
| B3 | workspace admission/broadcast operation | workspace snapshot ref | after B1 retrieval | M4 B3 workspace repository | incomplete/insufficient workspace enters B2 | capacity and admission/outcome accounting |
| B4 | intention governance | intention id/version, reconsideration count | after candidate selection | M4 B4 intention repository | suspend/escale/reconsider/change method; objective protected | only method changes under intention authority |
| B6 | meta-control and human-control boundary | uncertainty, budget/session refs, round | after B4; on uncertainty or resource limits | M4 B6 resource/uncertainty ledger | evidence request, reduced computation, human interrupt or stop | hard business constraints never relaxed |
| CoT / ToT / MCTS | `propose-candidates` | planner ID/session, candidate set, termination reason/count | after workspace and bounded re-search | M5 planner-session store | search budget/identity validation; bounded repeat | graph does not contain search tree/frontier |
| Common evaluator | `evaluate-candidates` | candidate-set ref and selected-candidate ref | after planner returns | M5 evaluator and M1 simulator | hard-constraint rejection, typed impasse/no feasible candidate | score cannot override infeasibility |
| Approval | authorize / interrupt / resolve / revalidate | approval id, response, approver, candidate + intention binding | every state-changing candidate | application approval repository | denied/expired/invalid/mismatched approvals stop | approval is separate from graph checkpoint |
| External execution | execute / observe | operation id, result ref, observation/evidence ref | authorized plan | M3 dispatcher/ledger and external system | unknown outcome reconciled; no blind retry | idempotency key, capability and transactional rules |

## S/C/T/P/F/L/R analysis of the orchestrator

- **S — State:** typed scalar graph state plus IDs/versions to working memory, evidence, workspace, intention, planner session, candidate set, approval, operation and observation records. Large authoritative records are not graph state.
- **C — Control:** LangGraph4j owns node/edge sequencing only. Domain invariants belong to M1; memory writes to M2; capability/authorization/dispatch to M3; B1/B2/B3/B4/B6 policy to M4; planner search/evaluation to M5; durable records to application repositories.
- **T — Trigger:** request intake and new observations initiate cycles; typed operation results select conditional routes; host-supplied response refs resume explicit interruptions.
- **P — Persistence:** LangGraph4j checkpoint saves orchestration position; M2 memory, M4 intention/impasse/budget ledgers, M5 planner sessions, approval records and operation ledger persist independently. Full durable stores are M7.
- **F — Failure:** typed impasses enter B2; missing evidence interrupts; invalid/denied/stale approvals stop or replan; unknown external outcomes reconcile; working-memory conflicts reload; bounded loops terminate in escalation.
- **L — Learning:** observations update working memory; episodes are recorded unverified. Semantic/procedural promotion remains governed by M2 policy and is not inferred from model outputs.
- **R — Resources:** graph loop bounds, M4 B6 budgets, M5 planner budgets, deadlines and business-resource constraints are distinct. The graph recursion limit is only a final guard.

## Key invariants

1. A LangGraph4j node is not a planner algorithm.
2. A graph checkpoint is not CoALA memory.
3. A ToT/MCTS frontier is not graph state.
4. A selected candidate is not an authorized command.
5. A state-changing action cannot take the approval-free path.
6. An approval must match the exact selected candidate and intention version.
7. Approval and authority are revalidated immediately before execution.
8. `UNKNOWN_OUTCOME` routes to observe/reconcile, not retry.
9. A working-memory version conflict never silently overwrites a concurrent update.
10. A model-generated conclusion does not autonomously promote semantic facts or procedural policy.
