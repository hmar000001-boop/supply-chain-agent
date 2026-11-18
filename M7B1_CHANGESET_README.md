# M7-B1 — Durable semantic/procedural memory and planner sessions

This is an incremental, change-only package for the repository state after M7-A. It is based on the latest repository archive supplied by the user, with the M7-A package overlaid. M7-A GitHub Actions CI was confirmed green by the user.

## Included

- PostgreSQL-backed `SemanticFactRepository` with bitemporal reads, scoped conflict serialization, atomic temporal segmentation and retention.
- PostgreSQL-backed `ProceduralMemoryRepository` with append-only versions, lifecycle CAS, atomic activation/retirement and historical queries.
- PostgreSQL-backed `PlannerSessionStore` with ordered ToT frontier and MCTS tree state, atomic revision CAS and repeatable-read loading.
- Forward-only V006 planner-session schema migration and migration-runner registration.
- Single-DataSource `PostgresAgentPersistence` composition and exposure of all four CoALA memory repositories.
- Four PostgreSQL integration scenarios, an additional packaged-migration parser test, M7 status/closure matrix, ADR and architecture updates.

## Apply

Extract this ZIP at the repository root, preserving paths. It contains only files added or changed relative to the M7-A-accepted baseline. Review the extracted diff before committing.

## Validate

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

The PostgreSQL CI service in `.github/workflows/ci.yml` is required. M7-B1 is not accepted until this real Maven/Surefire/PostgreSQL run is green.

## Authoring-environment validation

- 316 production Java sources compiled with Java 21 `--release 21 -Xlint:all`, using a temporary compile-only pgJDBC `PGSimpleDataSource` API stub.
- Seven relevant test source files compiled with Java 21 and temporary JUnit API stubs.
- All six migration scripts parsed: V001=22, V002=2, V003=7, V004=1, V005=12, V006=5; total 49 SQL statements.
- Eleven POM files parsed as XML.
- Maven Surefire and live PostgreSQL were not run locally: Maven 3.9.16 could not be downloaded because outbound DNS to Apache distribution hosts was unavailable, and no local PostgreSQL service/client is installed.

Temporary stubs and ad-hoc validation harnesses are not included in this package. These static checks do not replace GitHub Actions acceptance.

## M7 scope and no-gap commitment

M7-A is accepted by user-confirmed CI. M7-B1 closes durable Semantic Memory, Procedural Memory and planner-session checkpoints. M7-B2 is the final M7 delivery and must close every remaining line in `docs/phase7/M7-STATUS.md`: complete M5 planning artifacts; all M6 durable execution, B2, observation, approval and intention records; M3 operation ledger; inbox/outbox; reconciliation; PostgreSQL graph-saver bootstrap; bounded pooling; health/metrics; failure/concurrency/restart tests; and backup/restore operations.

M7 is complete only after M7-B1 and M7-B2 both pass the full CI command and every closure-matrix row has implementation and test evidence. There is no third M7 infrastructure delivery. M8 is reserved for business/algorithm evaluation and application freeze, not deferred M7 infrastructure.
