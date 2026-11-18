# M6-B — Lifecycle service composition (delivery 2/2)

This is an incremental change-only package for the supply-chain agent repository. Apply it on top of the accepted M6-A repository state (including the earlier M6 graph-core correction and M6-A decision-service composition).

## Apply

1. Back up or commit your current repository state.
2. Extract this ZIP at the repository root, preserving relative paths and replacing the listed modified files.
3. Review `M6B_CHANGE_MANIFEST.csv` and the Git diff before committing.
4. Run the full acceptance command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Do not replace the repository with this ZIP; it contains only M6-B project changes, documentation and its package metadata.

## What is included

- `CognitiveCycleServiceOperations`, the complete graph-operation composition of M6-A decision services and M6-B lifecycle services.
- Actual M2 working/episodic memory, B2 impasse, B4 intention, B6 meta-control and M3 action-registry/dispatcher integration.
- Explicit domain and trusted-authority adapter contracts.
- Stable scoped working-memory/intention identities, B2 parent/child validation and compensation, B4 objective governance, B6 budget persistence, candidate-to-M3 command binding, approval revalidation, principal-bound action batches, idempotent execution records, observation reconciliation and proof-gated objective completion.
- In-memory reference stores for action plans, B2 child results, action batches and observations.
- Twelve focused service-integration tests.
- Updated M6 architecture, ADR, traceability, status, README and changelog documentation.

## Local checks performed

- Changed M6-B production sources and their transitive project dependencies compiled using Java 21 `javac --release 21 -Xlint:all` against actual M2/M3/B2/B4/B6 project sources.
- Twelve M6-B test methods compiled and passed using a lightweight local JUnit-compatible shim/reflection harness. This is not JUnit Platform or Maven Surefire.
- The authoring environment did not have the Maven distribution/dependencies available to run the complete Maven reactor. No M6-B CI success is claimed.

## Important boundaries

`CognitiveCycleLifecyclePort` must be implemented by the supply-chain/domain host for authoritative context, evidence, child read operations, fresh planning requests, typed M3 commands, external observation/reconciliation, outcome drafts and objective-satisfaction proofs.

`CognitiveCycleAuthorityPort` must be implemented by the trusted host for approval persistence/resolution/revalidation, authenticated human-control responses, B4 termination authority and authenticated resource-scoped M3 principals. There are no permissive defaults.

Current M2/M4/M3 and M6-B repositories/ledgers are in-memory reference implementations. They are not crash-durable. M7 must provide transactional PostgreSQL stores, persisted approvals and operation ledgers, B2 child-step receipts and restart reconciliation. The current repository does not connect to a real ERP/WMS/supplier/carrier or enterprise identity deployment.

Twelve tests validate the actual service classes at the explicit domain/authority seams; they are not a full LangGraph4j end-to-end run against live external systems. M6 is accepted only after GitHub Actions passes the full reactor and the application host is wired with concrete domain/authority adapters.
