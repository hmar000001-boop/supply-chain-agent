# M7-B2 CI failure analysis — 2026-11-19

## Failure observed

The Java 21 `clean verify` run compiled and passed the first nine reactor modules. The PostgreSQL infrastructure module failed in two test initializers:

- `PostgresMemoryPersistenceIntegrationTest.initializeSchema`: expected six migrations, actual total seven.
- `PostgresM7B1PersistenceIntegrationTest.initialize`: expected six migrations, actual total seven.

V007 was introduced by M7-B2. The two older tests retained the B1-era literal `6`; neither failure indicated a migration execution error.

## Source correction

Both initializers now compare totals and skipped migrations with `PostgresMigrationRunner.supportedMigrationCount()` rather than a historical literal. They continue to assert that a second migration pass applies zero scripts.

V001–V007 remain immutable. M7-B2 adds V008 as a forward-only correction to make M3 operation idempotency and inbox/outbox message identity scope-bound. V008 migrates pre-existing unscoped records into the explicit `__legacy_unscoped__` scope, rather than assigning them to a tenant whose identity cannot be inferred.

## Additional closure hardening

- Readiness now requires a complete contiguous migration history through the latest supported version (currently V008).
- M3 claims are scope-bound; a matching pre-scope operation key causes `UNKNOWN_OUTCOME` and blocks redispatch pending reconciliation.
- M3 receipts are bound to the principal and action in their idempotency key; request fingerprints must be lowercase SHA-256 digests.
- Inbox payload fingerprints are computed from PostgreSQL's canonical JSONB representation so whitespace/property-order differences do not create false payload conflicts.
- The B2 PostgreSQL suite exercises operation fencing and reconciliation, message retry/dead-letter and rollback, tenant separation, approval expiry/revocation, child-step resume, child artifacts, planning/execution artifacts, observation integrity, intention CAS, metrics and readiness.
- The CI workflow adds a custom-format backup and isolated-database restore smoke after the Maven/PostgreSQL test run.

## Acceptance boundary

This note records the source-level correction and added acceptance coverage, not a passing rerun. Run `./mvnw -B -ntp -Dphase0.postgres=true clean verify`; the workflow then runs the backup/restore smoke. M7 remains open until that run is green and the deployment-specific WAL/PITR and multi-instance recovery procedures have been exercised.


## Second CI failure — parent-model BOM resolution (2026-11-19 14:31 UTC)

The next run stopped before Maven scanned the reactor. Maven could not resolve the imported model POMs `org.bsc.langgraph4j:langgraph4j-bom:1.8.20` and `org.slf4j:slf4j-bom:2.0.20` from Central. The published coordinates are listed by Maven Central, so this log does not establish that either version is invalid; it establishes that the runner could not resolve those import descriptors at that time.

The follow-up source correction removes both imported BOM descriptors and directly manages the only LangGraph4j modules used by this reactor (`langgraph4j-core` and `langgraph4j-postgres-saver`) at the shared `${langgraph4j.version}`, plus `slf4j-api` at `${slf4j.version}`. The existing pinned library versions remain unchanged. This avoids resolving the two imported BOM POMs during parent model construction while preserving explicit, convergent versions for consumed artifacts.

The PostgreSQL integration suite also adds a race between two independently-created Hikari pools, representing two application instances. The checkpoint restart test closes the first pool before a new pool reloads and resumes the checkpoint. These are added acceptance tests; they are not reported as passing until CI executes them.

## Current acceptance state

The exact-source GitHub Actions rerun is still required. The authoring container has Java 21 but no Maven installation and cannot resolve external distribution hosts, so it cannot produce a credible `clean verify` result. Do not mark M7 accepted from source inspection alone. Once the full Maven/PostgreSQL job and its backup/restore smoke pass, the software release gate is green. Production-specific WAL/PITR configuration, legal retention durations and any authoritative tenant mapping for quarantined legacy rows remain deployment inputs; the code fails closed rather than guessing those values.
