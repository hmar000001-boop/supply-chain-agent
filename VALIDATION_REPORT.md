# R2 local validation report

**Date:** 2026-11-20  
**JDK:** `javac 21.0.12.1`  
**Baseline:** reconstructed project-owner-confirmed green R1-DEF state  
**Result:** all completed local checks below passed; hosted Maven/PostgreSQL acceptance remains pending.

## Compilation completed

- `javac --release 21 -Xlint:all` clean-room compilation passed for 294 production Java sources across domain (53), memory (52), simulator (5), core (4), actions (58), and mechanisms (122).
- All nine mechanism test-source files compiled against the freshly compiled mechanism production classes and a local JUnit API compatibility stub set.
- The two new PostgreSQL repositories, PostgreSQL JDBC/JSON support used by them, migration runner, and `PostgresAgentPersistence` R2 composition methods compiled. External peer adapters not part of this focused compile were represented by narrow compile-only stubs.
- The new `R2OutcomeEvidencePostgresIntegrationTest` and migration parsing test sources compiled.
- The R2 evidence CLI, M8-B3 ablation runner/CLI and R2 CLI test compiled.

## Tests and executable evidence completed

- **101 focused tests passed, zero failures:** 93 mechanism tests (including B2, B3 and feedback), seven migration-runner parsing tests (including V011 registration and six-statement parsing), and one deterministic R2 evidence CLI test. Tests were executed using an assertion-capable local reflection runner with JUnit API stubs, not Maven Surefire.
- M8-B3 ablation CLI executed: 19 scenarios, 45 primary observations, 18 deterministic replay rows, 63 raw rows, 26 paired contrasts, and 18/18 replay matches. JSONL records parsed successfully.
- R2 evidence CLI executed and its SHA-256 manifest verified. It reports zero verified outcomes, zero holdout scenarios, `SHADOW`, `DISABLED`, and baseline preserved.
- `scripts/verify-m8-b5-architecture.py`: PASS.
- `scripts/verify-m8-r2-evidence.py`: PASS.
- Both GitHub Actions YAML files parsed successfully and were byte-identical.
- All modified/added CSV files are rectangular and parse successfully.

## Not completed locally

- The complete Maven reactor command `./mvnw -B -ntp -Dphase0.postgres=true clean install` was not run. Maven is not installed in the authoring environment and the Maven Wrapper distribution could not be retrieved from its host.
- Live PostgreSQL execution of V011 and the R2 repository integration test was not run locally. The test source compiled; hosted CI must exercise it.
- CodeQL and Trivy scans were not executed locally. The workflow now captures their SARIF outputs and tool versions; the owner must review and triage findings in the hosted artifact before R2 acceptance.
- No production downstream-outcome corpus or independent holdout dataset was available. No adaptive-policy performance advantage is claimed and no feedback policy is active.

## Interpretation

The local evidence establishes source compilation for the listed source sets, focused control/protocol test behavior, migration parser registration, deterministic evidence generation, and static architecture/workflow guardrails. It does **not** establish full reactor compilation, live database behavior, clean security scans, operational readiness, or empirical adaptive superiority. The single hosted CI cycle is the R2 acceptance gate.
