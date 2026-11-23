# M8 learning-action test compile repair

## Apply

Extract this ZIP at the repository root, preserving the repository-relative path. It modifies exactly one source file and makes no production-code, dependency, migration, workflow or runtime-behavior changes.

## Root cause

`LearningActionCatalog` is declared in `dev.supplychain.agent.actions.internal.learning`, but `LearningActionCatalogTest` is in `dev.supplychain.agent.actions` and referenced the type by its simple name without importing it. The compiler therefore treated `LearningActionCatalog.*` as an unresolved package/type and emitted the 21 errors shown in the CI log.

## Change

- Add `import dev.supplychain.agent.actions.internal.learning.LearningActionCatalog;` to `supply-chain-agent-actions/src/test/java/dev/supplychain/agent/actions/LearningActionCatalogTest.java`.
- Keep all test cases and assertions unchanged.

## Validation

- JDK 21 targeted Java compilation of `LearningActionCatalogTest` and `ActionTestFixtures` passed using compiled project classes and lightweight JUnit API stubs solely to type-check the test source.
- All four in-memory memory repository production sources were compiled as part of that targeted compile.
- Full Maven reactor/JUnit execution was not run locally; GitHub Actions remains authoritative.

## Scope

- No research-signing or experiment changes.
- No file deletions.
- No generated classes or local test stubs are included.
