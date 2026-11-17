# M3 change-only package

Apply the ZIP contents at the root of the existing `supply-chain-agent` repository, preserving the directory structure.

This package contains only M3 additions and modifications. It does not contain the complete repository.

## Apply

1. Extract the ZIP into the repository root, allowing the listed files to be added or replaced.
2. Review `CHANGE_MANIFEST.csv` before committing.
3. Commit the changes and run the complete CI command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

## Scope

- Adds the framework-neutral `supply-chain-agent-actions` module.
- Adds the LangChain4j stable-core reasoning adapter to the runtime module.
- Extends the M1 deterministic simulator with guarded shipment rerouting.
- Updates architecture/status documentation and Maven reactor configuration.
- Preserves the M0 compatibility tests and all M1/M2 sources and tests.

## Validation note

The authoring environment compiled the framework-neutral domain, memory, simulator and actions production sources with JDK 21 and executed 102 local regression test methods using a JUnit-compatible shim/reflection harness. It did not run Maven Surefire/JUnit Platform and could not compile the LangChain4j adapter against Maven artifacts locally. GitHub Actions is the acceptance gate for the complete Maven reactor.

## Explicit limitations

M3 uses in-memory reference adapters for the action ledger and simulator world. It does not provide durable action-ledger semantics, live ERP/WMS/carrier integrations, plan-bound human approval or LangGraph4j orchestration of the new action catalog. Those remain later milestones as documented in `docs/phase3/M3-STATUS.md`.
