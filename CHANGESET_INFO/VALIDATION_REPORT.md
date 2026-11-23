# Validation report

## Locally executed

- Python syntax compilation: passed for all three delivered Python files.
- `python3 -m unittest discover -s scripts/m8 -p 'test_source_fingerprint.py' -v`: **5 passed, 0 failed**.
- Integration fixture exercised:
  - tracked `mvnw` and `mvnw.cmd` mutation;
  - generated `.mvn/wrapper/maven-wrapper.jar`;
  - generated `.cache/trivy` content;
  - generated `scripts/m8/__pycache__`;
  - restoration and strict final clean-tree verification;
  - committed Git SHA/tree fingerprint;
  - 12-entry reactor count;
  - Surefire aggregation;
  - PostgreSQL V001–V015 migration-ledger parsing.
- Failure-injection tests confirmed that unexpected tracked source changes and untracked candidate-source files are rejected.
- ZIP contents and per-file SHA-256 manifest independently verified after archive creation.

## Not executed locally

- Maven build / Java JUnit / full PostgreSQL GitHub Actions workflow: not run in this environment.
- The five delivered tests are the new focused regression tests, not the repository's entire existing Python suite.
- Hosted CI must validate the complete combined repository after this changeset is committed.

## Acceptance condition

Accept the repair only after the GitHub Actions run for the commit containing this changeset completes successfully, including the M8-C1 evidence-capture step and artifact upload.
