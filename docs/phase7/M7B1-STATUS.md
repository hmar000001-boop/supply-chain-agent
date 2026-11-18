# M7-B1 status — Durable semantic/procedural memory and planner sessions

Date: 2026-11-18

**Status: implementation delivered; GitHub Actions Maven/PostgreSQL acceptance pending.** M7-A is CI-green per user confirmation.

## Implemented

- `PostgresSemanticFactRepository` implements every `SemanticFactRepository` operation: stage, lookup, activation, supersede-and-activate, multi-segment temporal replacement, rejection, bitemporal retrieval and retention deletion.
- Semantic conflict control uses a transaction-scoped advisory lock over scope/subject/predicate. It checks overlapping valid-time intervals before activating a fact.
- Multi-segment replacement validates complete contiguous coverage of the original validity interval and commits old-fact supersession plus all replacement activations atomically.
- `PostgresProceduralMemoryRepository` implements every `ProceduralMemoryRepository` operation. Version staging appends after the latest immutable predecessor. Lifecycle CAS permits only draft-to-validated/rejected transitions and preserves artifact/provenance identity. Activation retires the previous active version and activates the validated replacement atomically.
- Procedure staging and activation acquire a transaction-scoped advisory lock per scope/procedure key before row locking; the existing partial unique index enforces one active version.
- `PostgresPlannerSessionStore` persists planner identity, request/config fingerprints, revision/status/resource counters/elapsed duration, ordered frontier paths and ordered tree nodes. Header and child rows save atomically with revision CAS; loads use repeatable-read isolation.
- Migration V006 creates planner-session tables. The migration runner applies six ordered/checksummed migrations.
- `PostgresMemoryPersistence` exposes all four CoALA repositories. `PostgresAgentPersistence` composes those repositories and planner sessions over one DataSource.
- Added four PostgreSQL integration scenarios covering semantic timeline replacement, semantic conflict rejection, procedural version activation/history and planner-session round-trip/CAS across repository instances.

## Validation performed in authoring environment

- Java 21 production compilation (`--release 21 -Xlint:all`) across 316 source files in domain, memory, actions, mechanisms, simulator, planning and PostgreSQL infrastructure passed using a temporary compile-only pgJDBC `PGSimpleDataSource` API stub.
- Seven relevant test source files (all PostgreSQL infrastructure tests plus M2 `MemoryFixtures` and the episodic-service regression test) compiled with Java 21 and temporary JUnit API stubs.
- All six migration scripts split successfully: V001=22 statements, V002=2, V003=7, V004=1, V005=12, V006=5; total 49 statements.
- Maven Surefire/JUnit Platform and live PostgreSQL execution were not run in this environment. No local PostgreSQL service/client is installed, and Maven 3.9.16 could not be downloaded because outbound DNS to Apache distribution hosts is unavailable.
- The temporary pgJDBC/JUnit compile stubs and ad-hoc parser harness are authoring tools only; they are not included in the change package.

## Deliberately assigned to M7-B2

Complete M5 planning artifacts; M6 authorized-plan and action-batch stores; individual action/operation receipts and the M3 durable action ledger; B2 child investigation artifact and per-step receipts; external observations; approval/authority decision records; persistent B4/runtime lifecycle artifacts; transactional outbox/inbox; unknown-outcome reconciliation; production PostgreSQL LangGraph4j saver bootstrap; bounded connection pooling; health/readiness/metrics; backup/restore/retention runbooks; and two-instance/process-restart/failure-injection acceptance.

These items are tracked individually in the M7 closure matrix at `docs/phase7/M7-STATUS.md`. M7-B2 is the last M7 delivery; there is no third infrastructure delivery.

## Acceptance

Run `./mvnw -B -ntp -Dphase0.postgres=true clean verify`. Require the accepted M0-M6 and M7-A regression baseline plus the four new M7-B1 PostgreSQL scenarios to pass under the real Maven Surefire/JUnit Platform run. Do not mark B1 accepted before CI is green.
