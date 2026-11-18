# M6-B test inventory and validation

**Local result: 12/12 focused service-integration tests passed. Maven/Surefire/GitHub Actions acceptance is pending.**

| Test | Verified behavior |
|---|---|
| `loadContextUsesRealWorkingMemoryAndCreatesPersistentB4Intention` | Actual M2 working-memory open/write and B4 intention creation; stable IDs make a repeated load idempotent and scope/objective-bound. |
| `observationWritesAreVersionedAndIdempotentAfterReload` | Versioned observation write, conflict detection and idempotent retry after reloading the latest working-memory version. |
| `objectiveCompletionRequiresVerifiedProofAndOutcomeEpisodeIsIdempotent` | A reconciled observation and protected-objective proof are required before B4 completion; the outcome episode is idempotently recorded but remains unverified. |
| `episodeIdempotencyReturnsTheSameOutcomeButRejectsConflictingReuse` | Replaying the same observation key and equivalent payload returns the original episode; reusing the key with a different outcome is rejected. |
| `b4MethodChangeCannotAlterTheProtectedObjective` | B4 refuses a method change that does not preserve the immutable objective fingerprint. |
| `b4TerminationRequiresAnAuthenticatedAuthorizedActor` | A spoofed/unapproved actor cannot terminate the intention; the intention remains active. |
| `b6DecisionIsPersistedAndResourceExhaustionStopsTheCycle` | Actual B6 evaluation returns STOP for an already-over-budget execution and records the decision through the host budget port. |
| `b2ResolutionRunsOnlyDeclaredChildCapabilitiesAndReturnsToParentIntention` | Actual B2 child case/action-budget path; read finding returns through parent validation and B4 delegation return. |
| `b2ChildCannotExecuteACapabilityOutsideItsDelegatedScope` | A B2 child cannot invoke an inventory reservation when only supplier-read authority was delegated. |
| `typedPlanCommandUsesM3DispatcherAndIdempotencyLedger` | Typed command dispatch through actual M3 `ActionDispatcher` and action idempotency ledger. |
| `typedPlanCommandCannotExceedPrincipalCapabilities` | M3 principal capability enforcement denies a command outside its grants. |
| `batchBindsToAuthenticatedPrincipalAndSupportsIdempotentStepCompletion` | Execution batch binds to the authenticated principal and handles identical step receipt replay without duplicate append. |

## Local authoring-environment verification

- M6-B production sources and their transitive project dependencies compiled with `javac --release 21 -Xlint:all` against the actual Java source from the project modules (M2/M3/B2/B4/B6 are not stubbed).
- The three M6-B test classes compiled and ran through a lightweight local JUnit-compatible API shim/reflection harness. All 12 test methods passed.
- The shim is not JUnit Platform, and this is not a Maven/Surefire execution. The LangGraph4j graph/application host was not recompiled locally in this pass because Maven dependencies were not available in the authoring environment.
- The focused tests use deterministic doubles only at the explicit `CognitiveCycleLifecyclePort` and `CognitiveCycleAuthorityPort` seams. The M2/M3/B2/B4/B6 service implementations and in-memory stores/ledgers are actual project code.

## Required CI command

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
```

Require all previous M0–M5 modules, 14 corrected graph tests, M6-A decision-service tests, the 12 M6-B tests, framework/PostgreSQL compatibility tests and the application-host module to pass.
