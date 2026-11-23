# Validation report — M8 CI Fix 3

## Changes validated

- Exactly one B3 experimental SHADOW overlay is supplied by the per-arm session configuration.
- The deterministic TRAIN R2 workflow configuration no longer supplies a second overlay.
- `FullGraphEvaluationHost`'s duplicate-overlay `SecurityException` guard is unchanged.
- Existing factory test now asserts the single-overlay contract for both baseline and treatment.
- No new test annotations were added; the test inventory remains unchanged.
- Candidate fingerprint and conformance metadata agree.

## Static validation

All six repository static guard scripts passed:

- `verify-m8-b5-architecture.py`
- `verify-m8-r2-evidence.py`
- `verify-m8-preb7-f03-f04.py`
- `verify-m8-experiment-scenario-register.py`
- `verify-m8-fullgraph-experiment-adapter.py`
- `verify-m8-mechanism-experimental-evidence.py`

The architecture guard reported 11 Maven modules, 581 production Java files, 86 test source files and 488 test annotations/factories, with the source/test inventory in sync.

## Focused semantic checks

The code-path inspection confirms `FullGraphExperimentSession.compile` passes the per-arm overlay to the host and the deterministic factory now leaves the R2-level optional overlay absent. The host's anti-double-wiring rejection remains active.

## Not performed

A full Maven/Surefire/PostgreSQL run was not performed in this delivery environment: Maven is not installed and the Maven Wrapper distribution is not cached. Therefore this report does not claim hosted CI success. The next hosted run must rerun all 12 reactor projects using the repository's full command.

## Candidate identity

- Candidate ID: `M8-GAP01-GAP05-B3-SHADOW-OVERLAY-CI-FIX3`
- Source-tree SHA-256: `64c7b12fb41ef2093f94bf7815966259d62d351fe0472f9842b4e35953ca43cc`
- Fingerprinted source files: `719`
- Hosted CI: pending; not attested by this package.

## Separate non-blocking warning

The supplied CI log reports SLF4J API with no provider. This is not the cause of the build failure and does not fail tests, but it means logging falls back to NOP. This package intentionally does not add a logging backend; that should be a separate dependency/configuration decision.
