# M1 change log — domain foundation and deterministic simulator

## Added

- `supply-chain-agent-domain`: framework-independent supply-chain identities, value objects, entities, immutable snapshot and cross-record invariants.
- `supply-chain-agent-simulator`: pure typed transitions over immutable snapshots with explicit effects and no external side effects.
- Domain tests for quantity, currency, snapshot, substitution, reservation, shipment, order and purchase-order receipt invariants.
- Simulator tests for supplier-capacity exposure, purchase-order placement, reservation/availability, approved substitution ratios, quality holds, shipment delays, demand changes, disruption recording and deterministic replay.
- `docs/architecture/M1-DOMAIN-MODEL.md` and `docs/phase1/M1-STATUS.md`.

## Modified

- Root `pom.xml`: adds the domain and simulator modules to the Maven reactor.
- `README.md`: documents M0 completion, M1 domain/simulator scope, build instructions and the remaining milestone sequence.
- `docs/adr/0001-framework-selection.md`: records the accepted framework decision based on the reported green M0 CI result.
- `docs/architecture/DECISION-BOUNDARIES.md`: documents the four architectural levels and the domain/simulator boundaries.
- `docs/phase0/COMPATIBILITY-GATE.md` and `docs/phase0/PHASE0-STATUS.md`: record the M0 outcome and explicitly state its remaining limitations.

## Deleted

- None.

## Validation boundary

M1 production sources compile with JDK 21 `javac -Xlint:all`; M1 test sources compile against a local JUnit API compatibility shim and 31 test methods pass in a local reflection runner; the deterministic smoke harness passes; POM XML and module boundaries were checked. The complete Maven/Surefire reactor and the existing seven M0 tests must be confirmed in GitHub Actions. This changelog does not claim an M1 CI pass.
