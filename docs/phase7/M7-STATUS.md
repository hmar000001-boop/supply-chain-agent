# M7 status — Production persistence, transactional recovery and operations

**Scope:** Close all infrastructure, persistence, transaction, recovery and operational gaps left after M6. M7 is exactly three sequential deliveries: M7-A, M7-B1 and M7-B2. M7 is fully complete only after M7-A, M7-B1 and M7-B2 pass the full Maven/JUnit/PostgreSQL CI gate and every closure criterion below is verified. No persistence or operations work is intentionally deferred to M8.

The application remains a modular Java monolith. PostgreSQL infrastructure implements ports owned by memory/planning/runtime/application modules; domain and cognitive modules do not depend on JDBC. The database is authoritative for durable business-operation records, while LangGraph4j checkpoints store execution position only.

## Accepted baseline

### M7-A — PostgreSQL foundation and core memory

**Status: CI green, confirmed by the user.**

- PostgreSQL infrastructure module, validated environment configuration and credential redaction.
- Ordered, checksummed, transactionally applied migrations protected by a PostgreSQL advisory lock.
- Durable Working Memory and Episodic Memory repositories.
- Typed slot values, hypotheses, evidence/provenance, event/entity references and ordered collections survive reload.
- CAS, scoped idempotency, historical verified-episode queries and retention behavior.
- M2 reference adapter aligned with PostgreSQL's episodic payload fingerprint semantics.

### M7-B1 — Durable semantic/procedural memory and planner sessions

**Status: CI green and accepted, confirmed by the user on 2026-11-19.**

- `PostgresSemanticFactRepository` implements stage, lookup, activation, rejection, supersede-and-activate, multi-segment temporal replacement, bitemporal retrieval and retention.
- Semantic conflict control uses a transaction-scoped advisory lock per scope/subject/predicate and validates overlapping valid-time intervals before activation.
- Multi-segment replacement validates contiguous coverage and commits old-fact supersession plus all new active segments atomically.
- `PostgresProceduralMemoryRepository` implements stage, lookup, append-only version history, draft lifecycle CAS, validation, activation, retirement of the previous active version and historical active-at-time retrieval.
- Procedure version creation and activation are serialized by a transaction-scoped advisory lock per scope/procedure key; database uniqueness protects one active version.
- `PostgresPlannerSessionStore` persists planner identity, request/config fingerprints, revisions, statuses, resource counters, elapsed duration, ordered ToT frontier and ordered MCTS tree nodes. Header and child rows save atomically; loads use repeatable-read isolation.
- Migration V006 adds planner-session checkpoint tables; at B1 acceptance, V006 was the sixth and latest installed migration. M7-B2 subsequently adds forward-only V007 and V008, bringing the supported schema to eight ordered migrations.
- `PostgresMemoryPersistence` now exposes all four CoALA repositories. `PostgresAgentPersistence` composes them and planner sessions over one DataSource.
- First M7-B1 CI triage on 2026-11-18 corrected the PostgreSQL untyped-null overlap predicate and the procedural historical-lookup test expectation. The user subsequently confirmed that the complete M7-B1 CI rerun is green; B1 is accepted.

M7-B1 does not persist complete `PlanningRequest`/`PlanningResult` artifacts or M6 runtime records. Those were assigned to M7-B2.

## Final delivery — no third M7 phase

### M7-B2 — Durable runtime, transactions, recovery and production operations

**Implementation: delivered as change-only patches against the user-supplied `supply-chain-agent-main (2).zip` baseline.** The two stale B1 migration-count assertions now use the migration runner's supported count. The 2026-11-19 14:31 UTC CI run then failed before reactor scanning because Maven could not resolve the imported LangGraph4j and SLF4J BOM POMs; the follow-up patch removes those imports and explicitly manages the consumed modules. The B2 patch includes opt-in PostgreSQL integration coverage and a CI backup/restore smoke step. No local Maven build or live PostgreSQL execution is claimed; the exact-source GitHub Actions rerun remains the acceptance gate.

Implemented in the M7-B2 patch:

1. **M5 planning artifacts:** durable, versioned `PlanningRequest`/`PlanningResult` artifacts and candidate-set associations. Validate execution, planner-session, request fingerprint, configuration fingerprint and memory-version ownership before reuse. No unsafe Java native deserialization or unrestricted polymorphic class loading.
2. **M6 execution stores:** PostgreSQL repositories for authorized action plans, action execution batches, individual operation/step receipts, B2 child-resolution artifacts and per-child-step receipts, and external observations. Every store needs tenant/scope, identity, revision, retention and idempotency semantics where applicable.
3. **Approval and authority records:** durable approval binding to exact candidate/candidate-set, intention identity/version, principal, requested capability/resource scope, evidence/policy version, expiry, denial/revocation and response identity. On resume, revalidate current authority and plan feasibility. Authentication remains supplied by a trusted identity adapter; the database stores auditable decisions, not passwords or invented identities.
4. **Intention/runtime recovery:** persist the B4 intention lifecycle/versions and all runtime correlation needed to restore a suspended cognitive cycle without changing its protected objective.
5. **M3 operation ledger:** PostgreSQL-backed idempotency claims and receipts with payload fingerprints, atomic acquisition/lease, in-progress/completed/failed/unknown-outcome states, fencing/lease ownership and reconciliation. A key reused for another payload is a conflict. UNKNOWN_OUTCOME is never blindly replayed.
6. **B2 step receipts:** persist each child investigation step before the parent can mark it completed; use stable step identities and idempotent result fingerprints so a crash between child steps does not repeat completed work.
7. **Transactional messaging:** inbox deduplication and transactional outbox; claim with `FOR UPDATE SKIP LOCKED`, lease expiry, bounded retry/backoff, poison-message quarantine/dead-letter state and idempotent consumers. Enqueue local intent in the same database transaction as its operation/record change. Document that delivery is at-least-once and consumers must be idempotent; do not claim globally exactly-once effects.
8. **Checkpoint integration:** provide the production PostgreSQL LangGraph4j saver factory/bootstrap and require unique graph thread identity per execution. Demonstrate pause, process/adapter replacement and resume with PostgreSQL checkpoint state plus durable approval and operation records. A checkpoint is not the business ledger.
9. **Concurrency and consistency:** protect competing resource reservations/claims with database transactions, unique constraints, row locks or scoped advisory locks; use optimistic versions and stale-plan rejection; define lock ordering; prove two-instance races and rollback behavior.
10. **Connection management:** a bounded production pool with max pool size, acquisition timeout, validation, lifecycle/close, leak-safe usage and pool metrics. Keep the underlying pgJDBC DataSource available for unit tests where useful.
11. **Health and observability:** liveness/readiness checks, migration/schema compatibility status, safe persistence metrics and traces (latency, pool wait, lock/conflict, retries, outbox backlog, unknown outcomes, reconciliation age). Never log credentials or sensitive payloads.
12. **Operations and recovery:** documented migration rollout/rollback policy (forward fix for applied DDL), backup/restore and PITR/WAL procedures, secret/SSL configuration, retention/partition/index maintenance, disaster-recovery targets and an executable restore verification procedure or CI/scripted smoke where feasible.
13. **Failure-injection evidence:** restart/adapter replacement after committed and uncommitted writes; crash between external dispatch and receipt; duplicate message delivery; lease expiry; stale approval; concurrent action/resource claims; partial B2 child work; outbox publisher failure; inbox duplicate; database unavailable; migration contention. All ambiguous external effects route to reconciliation.

- Versioned M5 planning-artifact persistence with candidate-set/execution/session ownership metadata and payload fingerprints.
- PostgreSQL stores for authorized plans, execution batches and step receipts, B2 child artifacts and resumable child-step receipts, observations with provenance, recovery intentions/history, and impasse-case runtime state.
- Durable approval records bound to exact candidate-set/candidate, intention version, action-plan fingerprint, principal, capability/resource scope, evidence/policy version and decision/expiry status. The host remains responsible for authenticating principals.
- M3 operation ledger with idempotency payload fingerprints, leases, fencing tokens, terminal receipts and explicit `UNKNOWN_OUTCOME` state; unknown external effects are not automatically redispatched.
- Transactional inbox/outbox primitives with payload deduplication, leases, bounded exponential retry and dead-letter state. Delivery is at-least-once; consumers must remain idempotent.
- Production LangGraph4j PostgreSQL saver factory with stable per-scope/per-execution thread identity. Checkpoints remain separate from business-operation records.
- Bounded HikariCP pool settings/factory, schema-aware liveness/readiness, privacy-safe aggregate runtime metrics, and a single-DataSource composition entry point.
- V007 creates the runtime persistence tables. V008 adds tenant/scope keys to the M3 operation ledger and inbox/outbox without editing already-applied migrations; pre-scope rows are isolated in `__legacy_unscoped__`. PostgreSQL custom-format backup and isolated-target restore-verification scripts are included.
- Operational instructions are in `docs/phase7/M7B2-OPERATIONS-RUNBOOK.md`. The CI workflow executes a backup, creates an isolated restore database, validates required M7 tables and the complete eight-migration history, and removes the temporary database.

## M7 closure matrix — must be all green after M7-B2

| Area | M7 implementation | Required proof at final closure |
|---|---|---|
| Working memory | M7-A | PostgreSQL round-trip, CAS, expiry, order and conflict |
| Episodic memory | M7-A | Idempotency, review, supersession, retention, conflicting replay |
| Semantic memory | M7-B1 | Bitemporal queries, overlapping-key conflict, atomic segment replacement |
| Procedural memory | M7-B1 | Append-only versions, validation/activation, historical lookup, CAS |
| Planner sessions | M7-B1 | ToT frontier/MCTS tree round-trip, fingerprints, CAS, two-instance race |
| Planning artifacts | M7-B2 | `PostgresM7B2CoreIntegrationTest.planningArtifactsAuthorizedPlansAndExecutionReceiptsSurviveRepositoryReplacement`; typed/versioned round-trip, scope/ownership and session fingerprint checks |
| Authorized plans | M7-B2 | `PostgresM7B2CoreIntegrationTest.approvalsAreBoundToExactScopeAndDecisionAndCannotBeReadThroughAnotherTenantStore`; exact candidate/intention binding, expiry, stale revision and revocation |
| M6 execution batches/receipts | M7-B2 | `PostgresM7B2CoreIntegrationTest.planningArtifactsAuthorizedPlansAndExecutionReceiptsSurviveRepositoryReplacement`; idempotent per-step receipt, terminal-state protection and reload |
| B2 child artifacts/step receipts | M7-B2 | `PostgresM7B2CoreIntegrationTest.childStepReceiptsResumeCompletedWorkAndFenceExpiredClaims` and `.childArtifactsAndImpasseCasesSurviveRepositoryReplacementAndCas`; resume at last committed step, fencing and immutable child result |
| Observations | M7-B2 | `PostgresM7B2CoreIntegrationTest.observationsVerifyTypedContentAfterJsonbCanonicalization`; durable provenance, identity, JSONB-safe fingerprint verification and conflict handling |
| Human approvals | M7-B2 | `PostgresM7B2CoreIntegrationTest.approvalsAreBoundToExactScopeAndDecisionAndCannotBeReadThroughAnotherTenantStore`; audit trail, expiry, denial/revocation, exact binding and revision control |
| B4 intention state | M7-B2 | `PostgresM7B2CoreIntegrationTest.recoveryIntentionsPersistVersionHistoryAndProtectObjectiveAcrossScopeAndCas`; version history, scope guard, CAS and protected objective fingerprint |
| M3 operation ledger | M7-B2 | `PostgresM7B2CoreIntegrationTest.m3LedgerIsScopeBoundIdempotentAndRequiresReconciliationAfterLeaseExpiry`, `.m3LedgerFailsClosedOnPreScopeIdempotencyRows`, `.concurrentM3ClaimsAcquireExactlyOneFencingLease`; atomic claims, payload conflict, fencing, positive/negative reconciliation and legacy fail-closed |
| Inbox/outbox | M7-B2 | `PostgresM7B2CoreIntegrationTest.inboxOutboxAreTenantScopedDeduplicatedRetriedAndTransactional`; scope dedupe, canonical JSONB payload hash, rollback, retries and dead-letter |
| LangGraph4j checkpoint | M7-B2 | `PostgresLangGraph4jSaverFactoryIntegrationTest.productionSaverFactoryUsesSharedDataSourceForPauseAndRestartResume`; production saver factory, first-pool shutdown, second independent pool reload and resume |
| External effect safety | M7-B2 | No blind replay; idempotency plus reconciliation/compensation contract |
| Concurrency | M7-B2 | `PostgresM7B2CoreIntegrationTest.concurrentM3ClaimsAcquireExactlyOneFencingLease` and `.concurrentM3ClaimsAcrossIndependentApplicationPoolsAcquireExactlyOneFencingLease` plus transactional outbox rollback; the latter races separate Hikari pools against the same PostgreSQL constraints |
| Connection pool | M7-B2 | `PostgresM7B2CoreIntegrationTest.readinessMetricsAndPoolDefaultsAreBoundedAndScopeFiltered`; bounded production defaults, failed migration closes owned pool, readiness exposes pool state |
| Health/observability | M7-B2 | `PostgresM7B2CoreIntegrationTest.readinessMetricsAndPoolDefaultsAreBoundedAndScopeFiltered`; schema-aware readiness, process liveness and scope-filtered aggregate metrics |
| Backup/restore | M7-B2 | `.github/workflows/ci.yml` step `Verify PostgreSQL backup and isolated restore` runs `backup.sh` and `restore-verify.sh` against a fresh database; latest successful CI run is required as evidence. PITR/WAL activation remains deployment-specific |
| Migrations | M7-A + B1 + B2 | `PostgresMigrationRunnerParsingTest.runtimeScopeMigrationIsRegisteredAndSplitsIntoExpectedStatements`; eight ordered forward migrations, checksum guard, advisory lock, rerun behavior and newer-schema rejection |
| CI | all | Clean Java 21 Maven reactor with `-Dphase0.postgres=true`, all prior regressions and failure tests green |

## M7 final acceptance command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The named tests and CI restore step provide the acceptance hooks for this matrix. Their presence is not a passing result: record the actual GitHub Actions run and restore-smoke output before marking M7 complete. M7 is not complete if a row is merely documented but lacks its implementation or acceptance proof. If a live external ERP/IAM/carrier endpoint is unavailable, the application must provide a strict port, a deterministic contract test adapter and deployment-specific acceptance criteria; it must not invent a production endpoint or claim a live integration.

## Boundary with M8 and the book

M8 owns business/algorithm evaluation, mechanism ablations, broader disruption corpus, quality/performance benchmarking and application freeze. M8 is not a substitute for the infrastructure closure criteria above. Chapters 19 and 20 remain unwritten until M8 freezes the application.


**M7-B2 test/acceptance status (2026-11-19):** The stale migration-count assertions are corrected; the parent BOM resolution failure is addressed by explicit dependency management; and integration coverage now includes separate-pool M3 contention, checkpoint reload through a replacement pool, runtime persistence and backup/restore smoke. The authoring environment cannot execute Maven or live PostgreSQL. M7 is therefore implementation-complete in source but not formally accepted until the full GitHub Actions run is green. Production rollout still requires deployment-owned WAL/PITR settings, retention-policy approval and authoritative tenant mapping for any pre-scope records quarantined by V008; these values cannot safely be inferred by application code.
