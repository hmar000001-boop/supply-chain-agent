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
