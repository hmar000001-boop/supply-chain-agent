# Validation report — M8 Step 1 deterministic TRAIN host composition

## Passed

- M8 architecture/source inventory guard (11 modules, acyclic internal dependencies, dependency ownership, updated evaluation inventory).
- R2 evidence guard.
- F-03/F-04 integration guard.
- Full-graph experiment adapter guard (22 checks).
- Mechanism and independent evidence guard (25 checks).
- Scenario register guard (280 TRAIN / 280 HOLDOUT / 210 TRANSFER; disjoint seed bands and identities).
- Java 21 compilation smoke against API-compatible minimal contract stubs for the six new production host classes and the new host unit-test source.
- Focused source regression assertions for composed factory usage, fresh per-arm ScenarioRuntime, distinct workspace consumers, approval-boundary termination, and zero side effects.
- ZIP archive integrity and internal SHA-256 checks.

## Not run / not claimed

- Full Maven compiler and Surefire were not run locally: Maven Wrapper could not download Apache Maven because DNS resolution for the configured Apache hosts was unavailable.
- The new `M8B4FullGraphWorkflowTest` modification has not been compiled by hosted Maven in this candidate.
- Full PostgreSQL integration and complete hosted CI remain pending.
- No TRAIN batch, mechanism learning, verified label generation, confirmatory inference, HOLDOUT or TRANSFER execution has been performed.

## Scope limitation

The deterministic host and graph-session composer are implemented, but a reusable main-source implementation of the scenario-specific `DeterministicTrainPortsFactory` has not yet been extracted. The existing test fixture provides the deterministic composition exercised by the updated full-graph test. Step 1 remains in progress until that reusable provider is delivered and qualified.

## Candidate fingerprint

`a1a2b4372b8c778e92a17fc363250c2f84dafdd0e6f2ed46abf0e15d964e5737`
