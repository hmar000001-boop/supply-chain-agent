# M8 PRE-B7 R1-DEF CI Fix 4 — Evaluation Test Compilation

This is an incremental, changes-only source replacement for the R1-DEF branch.

## Apply

Extract this ZIP at the repository root and overwrite the matching file:

`supply-chain-agent-evaluation/src/test/java/dev/supplychain/agent/evaluation/M8B4FullGraphWorkflowTest.java`

This package is intended to be applied on top of the R1-DEF base package and CI Fixes 1, 2, and 3. It does not contain or replace those earlier changes.

## Corrections

1. Renamed the local Java executable path variable from `java` to `javaExecutable`. This removes package-name shadowing so `java.util.Locale.ROOT` resolves to the JDK package.
2. Corrected the provenance record reference to `ObservationProvenanceResolver.ObservationProvenance`. The record is nested in `ObservationProvenanceResolver`, not in `PostgresRuntimeCodecs`.
3. Added the explicit import for `ObservationProvenanceResolver`.

No production source, runtime behavior, persistence schema, or lease-security checks are changed.

## Validation scope

- Confirmed both reported compiler-error expressions are corrected.
- Confirmed the referenced provenance record declaration in the production PostgreSQL module.
- Confirmed the archive contains one complete Java source replacement and no patch-only files.
- Full Maven reactor / PostgreSQL CI was not run in the authoring environment. GitHub Actions remains the acceptance gate.
