# M8 Pre-B7 R1-DEF CI Fix 1 — ReservationStatus import

## Failure addressed

The GitHub Actions build compiled production code successfully but failed during
`testCompile` in `supply-chain-agent-runtime`:

```text
ResourceBudgetExecutionGuardTest.java:[147,22] cannot find symbol: ReservationStatus
ResourceBudgetExecutionGuardTest.java:[150,22] cannot find symbol: ReservationStatus
```

`ReservationStatus` is a standalone enum in
`dev.supplychain.agent.mechanisms.b6`, while the test class is in
`dev.supplychain.agent.runtime.cognitive`. The new test used the enum's simple
name without importing it.

## Change

Adds exactly this import to the complete replacement source file:

```java
import dev.supplychain.agent.mechanisms.b6.ReservationStatus;
```

No production source, migration, test logic, test count, dependency, POM, CI
workflow or other repository file is changed.

## Apply

Extract this ZIP at the repository root, preserving paths and allowing the
single Java test file to overwrite its existing version.

## Validation

- Verified the enum declaration in the accepted source baseline.
- Verified the ZIP contains the complete test source at its exact repository-relative path.
- Verified balanced Java braces/parentheses and presence of all four enum references.
- Maven/CI has not been run for this correction. Run the complete workflow after applying it.
