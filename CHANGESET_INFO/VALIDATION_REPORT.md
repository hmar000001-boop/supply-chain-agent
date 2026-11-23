# Validation report — M8 GAP-07 / GAP-08 / GAP-10

Date: 2026-11-23
Source reconstruction: frozen GAP-01–GAP-05 source plus accepted F-01/F-02, F-03/F-04, subsequent mechanism/research CI fixes, B3 overlay CI Fix 3, C1/C2 package and the CI evidence-capture repair. The user reports CI is green after the evidence-capture repair; no run URL/commit SHA was provided with this instruction.

## Completed in this environment

- `scripts/verify-m8-app-composition.py`: PASS.
- `scripts/verify-m8-b5-architecture.py`: PASS — 11 reactor modules; acyclic internal dependency graph; direct framework dependency checks; source/test inventory; CI mirror and source-root hygiene.
- `scripts/verify-m8-r2-evidence.py`: PASS.
- `scripts/verify-m8-preb7-f03-f04.py`: PASS.
- `scripts/verify-m8-mechanism-experimental-evidence.py`: PASS — 25 protocol/integrity invariants.
- All 12 Maven POM files parse as XML: PASS.
- `.github/workflows/ci.yml` and `workflows/ci.yml` are byte-identical: PASS.
- Full production source set of `supply-chain-agent-actions` compiled with `javac --release 21`: PASS.
- Repository delta checked against the reconstructed baseline: 11 modified, 7 added, 2 deleted; no `.class`, `.pyc` or `__pycache__` output included.
- Archive path/content/hash verification: performed when package was created.

## Must be confirmed by hosted GitHub Actions

- Full Maven 12-POM reactor compilation and all JUnit/Surefire tests.
- The new `DurableCognitiveApplicationHostPostgresIntegrationTest` against the CI PostgreSQL service, including migrations, durable working memory, graph interrupt, restart, typed resume and second top-level cycle.
- Java compilation of the new `supply-chain-agent-app` host and integration test against the pinned LangGraph4j/LangChain4j/PostgreSQL dependencies.

Maven was not available in this environment and the wrapper distribution host could not be resolved, so no full Maven build or JUnit execution is claimed here.

## Explicit remaining acceptance boundary

The new app-host integration test verifies durable app composition, working memory across host close/reopen, PostgreSQL graph-checkpoint resume after host restart, and a second top-level cycle. It intentionally pauses at a clarification boundary. It does not yet combine, through this new app host in one test, context/retrieval/planning, plan-bound approval, simulator dispatch, outcome reconciliation, restart around an in-flight effect and a subsequent cycle consuming the reconciled observation. Existing full-graph tests cover significant parts of those runtime/effect semantics through the evaluation composition. That remaining host-level combined acceptance test must not be represented as already passed.
