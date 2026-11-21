# Validation Report — R2-02 CI Fix 1

## CI failure diagnosis

The supplied hosted CI log shows:
- Modules 1–11 succeeded.
- Runtime: 84 tests, 0 failures/errors.
- Mechanisms: 94 tests, 0 failures/errors before this fix.
- PostgreSQL infrastructure: 54 tests, 0 failures/errors.
- Evaluation: 31 tests, 0 assertion failures, 4 errors.

The four errors were:
- Three `M8B4FullGraphWorkflowTest` scenarios: `planning request was built from a stale working-memory version`.
- `M8R2OutcomeFeedbackEvidenceCliTest`: normal adaptive trainer rejected the empty corpus because it requires at least two distinct verified experimental units.

## Validation performed after corrections

| Check | Result |
|---|---|
| Java 21 `javac --release 21 -Xlint:all` compilation of 346 production sources across domain, memory, core, simulator, actions, mechanisms and planning | PASS |
| All 9 mechanism test classes using the local assertion-capable JUnit-compatible runner | 95 passed, 0 failed |
| `M8R2OutcomeFeedbackEvidenceCliTest` using the same local JUnit-compatible runner | 1 passed, 0 failed |
| Java syntax parse of all 6 changed Java files with Java 21 compiler parser | PASS |
| `scripts/verify-m8-b5-architecture.py` | PASS |
| `scripts/verify-m8-r2-evidence.py` | PASS |
| ZIP integrity | PASS |
| SHA-256 checksums for packaged files | PASS |

## Validation limitations

- Maven is not installed locally. The Maven wrapper attempted to retrieve Maven 3.9.16, but DNS resolution for `archive.apache.org` and `downloads.apache.org` failed.
- Therefore the full Maven reactor was not rerun locally.
- The changed full-graph host and integration test were syntax-parsed and checked against the repository contracts. Their complete type-check and execution with real LangGraph4j dependencies, and the PostgreSQL suite after these changes, remain for hosted CI.
- The local JUnit-compatible runner is not Maven Surefire and does not replace the hosted Maven acceptance cycle.

## Acceptance status

`R2-02` remains `IMPLEMENTED_PENDING_CI`. This package is intended to resolve the four evaluation errors in the supplied CI run. Do not mark R2-02 or pre-M8-B7 closed until a new full Java 21/PostgreSQL reactor run succeeds.
