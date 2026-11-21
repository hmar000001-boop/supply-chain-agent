# Phase 5 — Persistence, Schema, Checkpoint and Restart Audit

**Project:** Supply-Chain Disruption Management Agent  
**Source baseline:** User-uploaded `supply-chain-agent-main (6).zip`  
**ZIP SHA-256:** `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`  
**Audit phase:** 5 of 6  
**Scope:** PostgreSQL migration chain V001–V011; four CoALA memory repositories; planner-session checkpoints; runtime artifacts, approvals, action batches/ledger, observations, B2/B3 evidence, B6 budgets/leases; LangGraph4j PostgreSQL checkpointing; process-restart tests and application composition.  
**Change class:** Audit only. No project source, tests, dependencies, migrations or CI workflows were modified.

## 1. Executive assessment

The repository has a **substantial and coherent durable persistence layer**. It goes beyond storing graph state: it persists the four CoALA memory types, planner search checkpoints, selected planning artifacts, approval and action-execution records, observations, B2 recovery drafts, B3 receipts/outcomes, and B6 resource budgets and leases. The critical runtime records use scope-qualified identities, transactional updates, optimistic revisions, idempotency keys and/or fencing tokens according to their semantics.

There is meaningful restart evidence in the test sources: the LangGraph4j saver test resumes after a pool replacement; M8-B4-C reconstructs a graph and host against a new pool after an interrupted action; M8-B4-D forks a second JVM and verifies the persisted checkpoint, intention, approval, resource reservation, M3 operation and external outcome. R1DEF adds separate forked-JVM tests around the B6 lease start boundary.

This is strong evidence for the book's durability teaching objectives. It is **not** evidence that the complete cognitive-cycle application is already assembled as a ready-to-deploy durable host. The complete evaluation host deliberately uses some in-memory stores, no durable B3 workspace repository exists, and authentication/approval issuance and real external reconciliation remain host adapters.

## 2. Migration chain and schema governance

### 2.1 Ordered migration inventory

The runner lists eleven migrations in order:

| Version | Source module | Purpose |
|---:|---|---|
| V001 | `supply-chain-agent-memory` | Initial CoALA working, episodic, semantic and procedural schema |
| V002 | PostgreSQL infrastructure | Episode idempotency |
| V003 | PostgreSQL infrastructure | Ordered memory collections |
| V004 | PostgreSQL infrastructure | Ordered episode evidence |
| V005 | PostgreSQL infrastructure | Lossless ordered evidence identities |
| V006 | PostgreSQL infrastructure | ToT/MCTS planner-session checkpoint tables |
| V007 | PostgreSQL infrastructure | Durable runtime artifacts, action batches, approvals, operation ledger, intentions, observations, B2 child artifacts, inbox/outbox |
| V008 | PostgreSQL infrastructure | Scope-qualified runtime operation and messaging keys; legacy rows isolated under `__legacy_unscoped__` |
| V009 | PostgreSQL infrastructure | Durable B6 resource-budget accounts, limits, usage and reservations |
| V010 | PostgreSQL infrastructure | B6 reservation owners, lease expiry, fencing and recovery outcomes |
| V011 | PostgreSQL infrastructure | B2 recovery-procedure drafts/history and B3 receipt/downstream-outcome evidence |

V001 is intentionally packaged in the memory module. The PostgreSQL infrastructure module depends on that module, so the migration resource is on its classpath when the infrastructure runner executes. The root Maven reactor contains both modules. This cross-module resource arrangement is valid in the declared dependency graph.

### 2.2 Runner semantics

`PostgresMigrationRunner`:

- serializes migration startup using a PostgreSQL session advisory lock;
- creates and maintains `agent_schema_history`;
- computes SHA-256 checksums and rejects a previously applied migration whose script content changes;
- rejects databases whose recorded maximum schema version is newer than the binary;
- applies each migration in its own transaction and records the history row in that same transaction;
- releases the advisory lock in a `finally` block; and
- supports the current plain DDL/DML migration language, not dollar-quoted procedural function bodies.

The source includes a SQL statement splitter that respects single-quoted strings, double-quoted identifiers, line comments and block comments. The migration parsing test exercises the parser. The PostgreSQL integration tests call the real runner and verify that a second migration run applies zero migrations.

**Limit:** the runner validates checksums for known applied versions and checks the maximum version, but does not independently perform a full preflight assertion that every historical version row is present and contiguous before applying pending versions. The repository's own normal path creates contiguous history; this is a defensive-hardening opportunity, not a defect evidenced by the accepted CI run.

## 3. CoALA memory persistence

### 3.1 Working memory

`PostgresWorkingMemoryRepository` stores the snapshot header, typed slots, evidence references, hypotheses and hypothesis evidence. A create or compare-and-set operation is transactional. `compareAndSet` locks the header row, checks scope and expected version, increments the version by exactly one, updates the header and replaces child rows atomically. The migration schema constrains supported value variants and evidence/hypothesis status shapes. Ordered child collections retain explicit ordinal values.

**Assessment:** strong durable realization of versioned CoALA working memory. It is separate from graph checkpoints and does not conflate orchestration state with cognitive state.

### 3.2 Episodic memory

`PostgresEpisodeRepository` persists an episode and its entities, events and evidence in one transaction. `appendIdempotent` uses `(scope_id, evidence_type, evidence_id)` as a unique idempotency identity and compares the payload fingerprint on a collision; an exact retry returns the existing episode, while a different payload is rejected. Lifecycle updates are compare-and-set. Retrieval is scope- and review-time-aware; unverified recorded episodes have retention handling; only verified (or historically verified-but-later-superseded) episodes are returned by the verified retrieval query.

**Assessment:** strong durable episodic record and trust lifecycle. This is not automatic policy learning: the episode remains an experience record until a distinct reviewed/verified transition, and it does not itself change planner policy.

### 3.3 Semantic memory

`PostgresSemanticFactRepository` persists typed values and provenance, and preserves separate valid time and knowledge/activation time. Activation and temporal amendments are transactional. Multi-segment replacement validates temporal coverage and competing active facts, supersedes the old fact and activates the proposed segments in one transaction. Revisions use expected-version checks.

**Assessment:** strong bitemporal semantic-memory realization with evidence-governed activation. The code's local conflict logic and SQL check constraints complement each other; database foreign keys do not independently enforce every same-scope semantic relation, so the repository remains part of the trust boundary.

### 3.4 Procedural memory

`PostgresProceduralMemoryRepository` stages immutable versioned procedure artifacts, serializes version creation per `(scope, procedureKey)` using a transaction-scoped advisory lock, and performs lifecycle revision checks. Activation locks the procedure key and prior active item so the lifecycle does not permit concurrent active versions for one key. Artifact references carry a SHA-256 digest; status and provenance fields are represented in the schema.

**Assessment:** strong governed explicit procedural-memory store. This is a valuable destination for approved B2 recovery knowledge, but the current graph does not yet promote approved `RecoveryProcedureDraft` records into this repository. That is the previously identified B2 learning gap, not a PostgreSQL durability defect.

## 4. Runtime persistence and scope controls

### 4.1 Scope-qualified runtime stores

V007–V011 define scope-qualified runtime identities for planning artifacts, action batches/steps, child-investigation receipts, observations, recovery intentions/history, approvals, action-operation ledger rows, inbox/outbox, B6 budgets/reservations, B2 drafts/history and B3 receipts/outcomes. V008 moves the action and messaging idempotency keys to composite scope-qualified primary keys and deliberately moves prior unscoped rows into the sentinel `__legacy_unscoped__` scope rather than guessing tenant ownership.

`PostgresRuntimePersistence` is itself constructed with a required `scopeId`. It composes durable planning artifacts, approved action plans, execution batches, B2 child artifacts and step receipts, observations, intentions, impasse cases, approvals, M3 operation ledger, B6 resource budgets, messaging and metrics. Its `newResourceBudgetExecutionGuard` factory creates the single shared guard intended for M5, M3 and B6; the full graph service composition checks object identity of that shared guard.

B2 draft and B3 receipt/outcome repositories are exposed through separate scope-bound factories on `PostgresAgentPersistence`, rather than included in `PostgresRuntimePersistence`'s fields.

### 4.2 B6 durable budget and reservation leases

`PostgresResourceBudgetLedger` locks the execution budget row for mutations, so independent application instances serialize budget reservations and cannot each spend the same available capacity. Budget definitions are immutable after registration; `ensureRegistered` validates the same limits and normalized PostgreSQL deadline on host restart.

The leased path records owner, lease expiry, fencing token and whether the protected operation crossed its start marker. Expired unstarted reservations are released and can be reclaimed with an incremented fence. Expired started reservations are not reopened: they are conservatively charged and marked `EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED`, so the operation cannot be silently replayed. The implementation prioritizes releasing expired unstarted reservations before calculating the conservative charge for started unknown work.

**Important terminology:** after-start recovery is a conservative budget charge based on the reserved amount and remaining budget capacity; it is not a measurement of actual provider consumption. That is deliberate fail-closed accounting for an ambiguous started operation. Tests exercise it as such.

### 4.3 M3 operation ledger and batch state

The scope-bound PostgreSQL action ledger stores request fingerprint, stable operation ID, state, owner/lease/fencing token, receipt or failure payload and retryability/unknown-outcome flags. A successful completion or failure write is accepted only from the current unexpired owner/fence. An expired in-progress operation becomes `UNKNOWN_OUTCOME`; ordinary dispatch cannot retry it. Explicit reconciliation can mark it completed or definitively not applied. The action execution batch store separately persists ordered per-step receipts and the batch aggregate state.

Phase 4 separately establishes that the database fencing token protects ledger state, not an already-running remote provider. A live external adapter still requires provider idempotency and authoritative query/reconciliation.

### 4.4 B2 and B3 evidence stores

`PostgresRecoveryProcedureDraftRepository` is scope-bound, persists JSONB plus header fields, checks payload/header identity on read, updates using compare-and-set on version and both objective/source fingerprints, and appends a history row in the same transaction. Its state is a **draft/approval record**, not an active procedural-memory item.

`PostgresB3OutcomeEvidenceRepository` stores scope-bound append-only receipts and outcomes. It checks that outcomes reference an accepted stored receipt and that cycle, workspace, consumer, candidate ID and exact candidate fingerprint match the receipt. Idempotency-key collisions must reproduce the same evidence payload.

### 4.5 Observation provenance

`PostgresObservationStore` persists the disposition, evidence set, payload fingerprint, execution, principal, source reference and time through a supplied `ObservationProvenanceResolver`. It checks the same observation ID cannot be reused with different content or provenance. The resolver is intentionally host supplied. As Phase 4 notes, the current observation data contract does not itself carry a per-operation reconciliation proof binding every action in a batch to provider evidence; a typed reconciliation proof would be a useful engineering extension.

## 5. Planner-session and graph checkpoints

### 5.1 ToT/MCTS planner sessions

`PostgresPlannerSessionStore` persists session headers, frontier paths and tree nodes in a transaction. Save uses a `SELECT ... FOR UPDATE` revision check followed by header update/insert and child-set replacement. The checkpoint contains planner ID, request fingerprint, algorithm-configuration fingerprint, revision, status, counters and elapsed duration. The request fingerprint intentionally excludes replenishable resource ceilings and resume-session ID but includes structural search bounds, objective/world/evidence/action definitions, working-memory content/scope and uncertainty reference. Resume checks planner identity, request fingerprint, algorithm configuration and non-completed status.

**Finding P5-01 — planner-session rows are not database-scope-qualified.** The `planner_session` primary key is only `session_id`, the `PlannerSessionStore` API only accepts a session ID, and `PostgresPlannerSessionStore` has no configured scope. Session IDs are currently random UUIDs and resumed sessions are checked against a fingerprint that includes working-memory scope, so a cross-scope resume will fail the content check. Nevertheless, this is weaker than the composite `scope_id` boundary used by the rest of runtime persistence: a known session ID can be read through the generic store without a scope check, and existence/read authorization depends on the trusted host/planner path. **Recommendation:** make planner sessions scope-bound at the repository/API level (e.g. `find(scope, sessionId)`, `save(scope, ...)` plus a V012 composite-key migration) before claiming database-enforced multi-tenant isolation.

### 5.2 LangGraph4j checkpointing

`PostgresLangGraph4jCheckpointSaverFactory` uses the caller's shared `DataSource` and the graph's required state serializer. `threadIdForExecution` hashes `scopeId + executionId`, giving stable restart identity and separating different scopes/executions in the application-level thread namespace.

The tests cover:

- a graph interrupt before approval, pool close/recreation, state restore and resume;
- M8-B4-C: a full test workflow interrupted after a provider committed but returned a timeout, a new pool/host/compiled graph, restored intention/approval/budget/operation state, reconciliation, and no second external effect; and
- M8-B4-D: a second JVM process, with a new runtime and pool, restoring the checkpoint/intention/approval/B6 reservation/M3 operation and reconciling the already-committed external effect.

**Boundary:** the LangGraph4j saver tables do not expose this application's `scope_id` as a separate relational tenant column. Isolation relies on the stable application-generated thread ID and trusted use of the factory. The application does not configure database row-level security in this source.

## 6. Persistence composition and remaining gaps

### 6.1 What is actually assembled

`PostgresAgentPersistence` is a real datasource/migration composition root for four CoALA repositories, planner sessions, scope-bound runtime persistence, B2 drafts and B3 outcomes. `PostgresRuntimePersistence` composes many durable M3/M5/B2/B4/B6 stores and supplies the shared durable B6 guard. `CognitiveCycleApplicationGraph.buildFull/compileFull` is the full graph assembly boundary and requires the exact shared budget guard when the guarded overload is used.

### 6.2 What is not delivered as one ready host

- The application module's `Phase0Application` is a deterministic intake smoke application using `MemorySaver`, not the full cognitive cycle.
- `CognitiveCycleApplicationGraph` composes service objects supplied by a host; it does not itself instantiate all repositories, policy adapters, trusted authority port, model provider, domain-world adapter, reconciliation adapter and checkpoint saver.
- `FullGraphEvaluationHost` is an evaluation composition. Its working memory, workspace, action ledger, planning artifacts, action batches and observation stores use in-memory adapters in the relevant test host. Do not describe its full-graph run as a full PostgreSQL-backed application deployment.
- There is an `InMemoryWorkspaceRepository`, but no PostgreSQL workspace-snapshot repository in main source. B3 receipts/outcomes are durable while the exact admitted workspace snapshot is not persisted by a first-party PG adapter.
- The `CognitiveCycleAuthorityPort` authentication/approver decision and `ObservationProvenanceResolver` remain trusted host adapters. The database stores their decisions and provenance output; it does not establish the identity of the human approver.
- The supplier/inventory/shipment world remains simulator-backed; the PG persistence layer does not add live ERP/WMS/carrier systems.

These boundaries are appropriate to state in an educational reference implementation and do not require expanding the scope into a production deployment project.

## 7. Additional scope API finding

**Finding P5-02 — several memory APIs read and mutate by record ID without accepting an expected caller scope.** The Postgres tables retain scope, and collection/retrieval queries include scope. However, `WorkingMemoryService.find/load/put/remove/putHypothesis`, `EpisodicMemoryService.load/verify/reject/supersede`, `SemanticMemoryService.load/activate/reject` and `ProceduralMemoryService.load/validate/activate/reject` use ID-only repository methods. The repository compare-and-set often checks that the replacement preserves the stored object's scope, but there is no caller-supplied scope against which to authorize the request at those service APIs. IDs are globally unique, which lowers accidental collision risk, but it is not an authorization boundary if an ID is known.

**Recommendation:** make externally reachable or tenant-facing service operations scope-aware; assert the principal/runtime scope against the loaded record before read or mutation. The internal repository API can remain ID-based only if its caller is a trusted scope-bound component and that trust boundary is explicit. This finding is an architecture boundary, not evidence of a currently exposed remote exploit; the uploaded application has no production user-facing IAM/API host.

## 8. Test evidence inspected

Source inspection identified the following direct evidence classes:

| Area | Test classes | Evidence exercised in source |
|---|---|---|
| Migrations and memory | `PostgresMigrationRunnerParsingTest`, `PostgresMemoryPersistenceIntegrationTest`, `PostgresM7B1PersistenceIntegrationTest` | Parsing, repeated migration no-op, typed-value/evidence ordering round trips, working-memory CAS, episode idempotency/review/history/retention, semantic/procedural durable lifecycle |
| Planner checkpoint | `PostgresCheckpointCompatibilityTest`, `PostgresLangGraph4jSaverFactoryIntegrationTest` | compatibility and saver pause/restart/resume across pools |
| Runtime transactions | `PostgresM7B2CoreIntegrationTest`, `M8B4ReliabilityIntegrationTest` | action batches, approvals, operation ledger, unknown outcomes, concurrent dispatch, host replacement/reconcile |
| B6 budget/lease | `M8B4DPostgresBudgetExecutionIntegrationTest`, `R1DefBudgetLeaseRecoveryIntegrationTest` | shared guard, over-budget rejection, unstarted/started lease expiry, fencing, concurrent ownership, forked-JVM crash boundaries |
| Full restart | `M8B4CRestartReconciliationIntegrationTest`, `M8B4ForkedJvmProcessRestartIntegrationTest` | replacement host/pool and independent JVM restart, checkpoint and operation restore, no blind external redispatch |
| R2 evidence | `R2OutcomeEvidencePostgresIntegrationTest` | scope-bound receipt/outcome persistence and evidence identity checks |

These are source-level test-evidence inspections. The user reports the uploaded archive is the green-CI source. This audit did not run Maven, PostgreSQL, Testcontainers, a separate JVM test, backup/restore or a real provider in this session.

## 9. Phase 5 conclusions

**Strong:** durable CoALA memory repositories; revisioned planner search checkpoints; immutable/fingerprinted runtime artifacts; approval/action/observation stores; M3 idempotency and unknown-outcome handling; B6 shared-budget locking and lease recovery; B2/B3 evidence persistence; and actual restart/process-replacement tests in the test source.

**Partial / not yet end-to-end durable:** B3 workspace snapshot itself; complete durable host assembly; scope-qualified planner-session store; explicit expected-scope authorization at ID-based memory service APIs; and provider-side fencing/idempotency beyond the M3 ledger boundary.

**Paper connection:** this persistence layer strongly supports CoALA's distinct long-term memory forms and Fan & Lan's emphasis on state substrate and persistence boundaries. It does not close the B1/B2/B3/B4/B6 learning/control gaps identified in Phases 2–3: persistence is one of the seven mechanism dimensions, not proof of a complete mechanism.

## 10. Review depth and limits

This phase targeted the migration runner, all eleven SQL migration scripts, all four first-party CoALA memory repositories, planner-session store, checkpoint saver factory, runtime persistence composition and its major stores, B6 resource-budget/lease implementation, and focused PostgreSQL/restart tests. It did not exhaustively audit every SQL statement in every runtime repository or rerun database integration tests. The full 739-type inventory remains the index for any narrower follow-up review.
