# Dependency-resolution correction — 2026-11-17

## Cause

GitHub Actions successfully bootstrapped Maven 3.9.16, then failed because Maven Central could not resolve `langgraph4j-core:1.8.28` or `langgraph4j-postgres-saver:1.8.28`.

## Modified

- `pom.xml`: changed the LangGraph4j baseline to 1.8.20 and imported the official `langgraph4j-bom:1.8.20`; removed duplicate per-module version management so the BOM controls core and PostgreSQL saver together.
- `README.md`: corrected the selected LangGraph4j version and documented BOM-based alignment.
- `docs/adr/0001-framework-selection.md`: superseded the unavailable 1.8.28 dependency selection with the common published 1.8.20 LTS artifact set and explained the evidence and trade-off.
- `docs/phase0/COMPATIBILITY-GATE.md`: updated the expected dependency tree and framework version.
- `docs/phase0/PHASE0-STATUS.md`: recorded the CI failure and the dependency correction; Phase 0 remains open pending rerun.
- `docs/phase0/SOURCE-REFERENCES.md`: updated repository evidence and Maven Central links.

## Added

- `docs/phase0/DEPENDENCY-RESOLUTION-2026-11-17.md`: detailed cause, evidence, decision and rerun commands.
- `CHANGELOG_DEPENDENCY_FIX.md`: this changelog.
- `README_CHANGESET.md`: application instructions.
- `CHANGE_MANIFEST.csv`: change inventory and SHA-256 hashes.

## Deleted

None.

## Verification status

XML parsing and static POM validation are performed before packaging. The full Maven build and PostgreSQL integration tests have not been run in this authoring environment. CI must confirm the corrected dependency set.
