# Validation report

## Root cause resolution

The failing CI tuple was `found (37, 10, 63, 10, 0)` versus the accepted inventory `(38, 11, 87, 11, 0)`. The restored runtime test source contributes exactly 24 JUnit annotations; together the restored production/test files reconcile all three deltas.

## Executed validations

- Restored production source SHA-256: `04577ba12c96f8d89a553bf8558e30fbc1201f01e3f7d1b0445d086453607442`.
- Restored test source SHA-256: `c8be7b1b555c70d9333afcc3698e6d6d094a0b1814cec6dc71129946f7b8fddf`.
- Legacy root production SHA-256 precondition: `32373e86f96846469f057da25b25886bd2e2b6e0aab13a1d2e2c9e88df74d26f`.
- Legacy root test SHA-256 precondition: `e2a2a025604812c3be393428a72a3d392cc785a9436793d1a0a84c9dd3734b1f`.
- Cleanup unit tests: **5 passed**.
- Simulated cleanup on reconstructed repository: removed only the two root-level files; active module copies remained present.
- `verify-m8-b5-architecture.py`: PASS (11 modules, dependency DAG, direct framework dependencies, module source/test inventory, source ownership and workflow mirror).
- `verify-m8-app-composition.py`: PASS.
- `verify-m8-r2-evidence.py`: PASS.
- `verify-m8-preb7-f03-f04.py`: PASS.
- `verify-m8-mechanism-experimental-evidence.py`: PASS (25 invariants).
- The cleanup workflow YAML passed structural parsing and its references were checked against repository workflow/script paths.
- ZIP integrity, repository-relative paths, manifest and SHA-256 checksums: PASS.

## Not run here

The full Maven reactor/JUnit/PostgreSQL integration workflow was not run locally. Maven is not installed in this environment and external Maven distribution downloads are unavailable. The new manual cleanup workflow dispatches the repository's existing full Java/PostgreSQL CI after the cleanup commit; that hosted run is the authoritative final validation.
