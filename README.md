# M8 Pre-B7 R1-DEF — CI Fix 2

This is a changes-only ZIP containing **four complete source-file replacements** for the checkout that already has:

- the accepted green R1-C changes, including predecessor-scoped uncertainty idempotency;
- the R1-DEF integrated budget-authority / reservation-recovery delivery;
- R1-DEF CI Fix 1 (the `ReservationStatus` import in `ResourceBudgetExecutionGuardTest.java`).

Do not apply this ZIP to the frozen M8-B6 baseline or to a checkout that does not already contain R1-DEF.

## Apply

Extract this archive at the repository root, preserving paths and allowing the four listed files to overwrite their current versions. Commit and push the result, then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

## Corrections

1. `CognitiveCycleGraphTest.java`: restores the exact complete test source from the green R1-C Fix 5 delivery. This preserves the post-human-control second B6 evaluation and its assertion that it consumes uncertainty revision 3. R1-DEF had unintentionally replaced it with an older pre-Fix-5 copy.
2. `ResourceBudgetExecutionGuardTest.java`: marks the second live reservation's operation as started before committing it, respecting the production lease state machine.
3. `BudgetedReasoningProviderTest.java`: changes the simulated unavailable ledger to implement `LeasedResourceBudgetLedger`, matching the production guard contract, and asserts that no lease operation/provider invocation occurs when durable account initialization is unavailable.
4. `BudgetReservationInUseException.java`: adds `serialVersionUID` to remove the Java serialization warning.

No production behavior or authorization checks are weakened. No migration, POM, dependency, workflow, inventory count, or database schema is changed by this CI correction.

## Validation performed

- Exact restored graph test source compared to the complete R1-C Fix 5 delivery.
- All four replacement paths and source hashes recorded in the manifest/checksum files.
- Java 21 compilation of `ResourceBudgetExecutionGuardTest` and `BudgetedReasoningProviderTest` against the R1-DEF compiled production classes: PASS.
- Focused assertion-backed execution of those two test classes: **13/13 PASS**.
- Exception source compiles under Java 21.
- ZIP integrity and per-file SHA-256 verification: PASS.

This focused harness is not a Maven Surefire run. The complete GitHub Actions Maven/PostgreSQL CI remains the acceptance gate for R1-DEF. The graph test was restored from the previously green R1-C Fix 5 source and must be re-executed by Surefire in CI.
