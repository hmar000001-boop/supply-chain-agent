# M7-B2 CI follow-up — PostgreSQL timestamp precision and batch exception contract

## CI failures addressed

1. `PostgresM7B2CoreIntegrationTest.observationsVerifyTypedContentAfterJsonbCanonicalization`
   failed because the test/application supplied a nanosecond-precision `Instant`, while PostgreSQL
   `timestamptz` stores microsecond precision. The observation duplicate-verification path compared
   the unnormalized Java value to the precision-reduced value read from PostgreSQL and falsely
   treated an identical observation as conflicting provenance/content.
2. `planningArtifactsAuthorizedPlansAndExecutionReceiptsSurviveRepositoryReplacement`
   expected `IllegalArgumentException` for an unknown batch. The in-memory reference adapter and
   the PostgreSQL adapter both model this as an invalid lifecycle state and throw
   `IllegalStateException`; the integration assertion was inconsistent with the established contract.

## Changes

- `PostgresJdbc.normalizeInstant` canonicalizes database-bound instants to microseconds.
- `PostgresJdbc.setInstant` applies that normalization before binding `OffsetDateTime` in UTC.
- `PostgresObservationStore` compares persisted observation provenance timestamps using the same
  microsecond-normalized representation.
- The observation integration scenario now verifies that an exact duplicate save is idempotent,
  while a different observation under the same ID is still rejected with `SecurityException`.
- The unknown-batch integration assertion now expects `IllegalStateException`, matching
  `InMemoryActionExecutionBatchStore` and `PostgresActionExecutionBatchStore` behavior.

No SQL migration or schema change is required. No existing migration is modified.
