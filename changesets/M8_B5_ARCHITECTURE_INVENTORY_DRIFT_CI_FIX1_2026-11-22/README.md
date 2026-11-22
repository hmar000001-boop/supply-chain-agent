# M8 B5 architecture inventory drift — CI Fix 1

## Apply

Extract this ZIP at the repository root, preserving all paths. It contains six project-file updates: five replacements and one added research note. No Java production or test source is changed.

## Why this fixes CI

The hosted guard reported the actual evaluation-module tuple `(95, 20, 68, 20, 0)`, while the guard and CSV expected `(109, 21, 69, 21, 0)`. The latter numbers did not describe the checked-out Java tree and included a test class that does not exist. The exact 20 test sources are now listed; Ed25519 trust cases are in `M8TrustedResearchCompositionTest`.

The guard remains strict: it compares the hard-coded expected inventory and CSV to the actual file tree. No requirements were weakened, no placeholder classes were added, and no Java implementation was removed.

## Next qualification

The existing test `M8B4FullGraphWorkflowTest.concreteDeterministicTrainHostComposesTheActualGraphAndStopsBeforeApprovalEffects` exercises the concrete full-graph TRAIN session with one preregistered TRAIN unit and both matched arms. It asserts the approval boundary and zero side effects. The trust composition tests exercise Ed25519 trust using ephemeral test-only keypairs. Hosted Maven/Surefire must pass before any TRAIN telemetry collection. Real trusted issuer public keys must be provisioned by their independent custodians before trusted evidence can be accepted.

## Validation boundary

The six static architecture/research guards and metadata consistency checks pass in the reconstructed source tree. The local Maven Wrapper could not download Apache Maven 3.9.16 because `archive.apache.org` and `downloads.apache.org` DNS resolution failed. This package does not claim Maven/Surefire or PostgreSQL success, does not run experiments, and does not open HOLDOUT or TRANSFER.
