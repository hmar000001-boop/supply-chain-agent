# Change log — SLF4J dependency convergence

## Modified

- `pom.xml`
  - Added `slf4j.version` property at `2.0.20`.
  - Imported the official `org.slf4j:slf4j-bom` in root `dependencyManagement`.
  - Preserved Maven Enforcer `dependencyConvergence`; no rules were disabled or weakened.

- `docs/phase0/PHASE0-STATUS.md`
  - Recorded the newly observed SLF4J convergence failure and the BOM alignment fix.
  - Kept Phase 0 explicitly open pending a successful clean CI build and PostgreSQL test.

- `docs/phase0/COMPATIBILITY-GATE.md`
  - Added the expected single-version SLF4J dependency-tree condition.

- `docs/phase0/SOURCE-REFERENCES.md`
  - Added official SLF4J release and Maven Central BOM references and the observed version rationale.

## Added

- `docs/phase0/DEPENDENCY-CONVERGENCE-2026-11-17.md`
  - Records the CI evidence, technical cause, resolution and verification commands.
- `CHANGELOG_SLF4J_ALIGNMENT.md`
- `README_CHANGESET.md`
- `CHANGE_MANIFEST.csv`

## Deleted

- None.

No Java source files, LangGraph4j versions, LangChain4j versions or Enforcer rules were changed.
