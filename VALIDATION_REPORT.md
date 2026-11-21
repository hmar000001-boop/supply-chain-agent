# Validation report — GAP-01–GAP-05 implementation candidate

**Date:** 2026-11-21  
**Status:** Targeted local validation passed; hosted full Maven CI pending.

## Source identity

- Original owner-uploaded base archive: `supply-chain-agent-main (6).zip`, SHA-256 `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`.
- F-01/F-02 overlay SHA-256: `a05ed45d02d9a3c141f4f21d2b1924ddcc57c1f13779032b28a5f7146a08593a`.
- F-03/F-04 overlay SHA-256: `27a16c8869f2e4eb54cbb00007a405e84caff6799bc6af04abee1ce3a5fe5d01`.
- This candidate is layered on the accepted F-01–F-04 source stack. Exact first-five candidate archive checksums are supplied as external sidecars with this release.
- No Git commit SHA or hosted workflow run ID/URL was supplied.

## Change summary

- Relative to accepted F-01–F-04 baseline: 41 new paths, 52 modified paths, zero deletions.
- Four root-level delivery descriptors (`README.md`, `CHANGE_MANIFEST.csv`, `SHA256SUMS.txt`, `VALIDATION_REPORT.md`) are excluded from the self-describing change-manifest row count. The project change manifest therefore lists 90 project paths.
- PostgreSQL migration head: V015 (V001 is in the memory module; V002–V015 are in PostgreSQL infrastructure).

## Targeted local compilation and execution

| Check | Result | Exact limitation |
|---|---|---|
| Java 21 production compilation | PASS | Eight framework-neutral production modules, including changed runtime decision/lifecycle source |
| PostgreSQL production compilation | PASS: 37 source files | Six third-party-bound source files were excluded because local HikariCP/pgJDBC/LangGraph4j dependencies were unavailable |
| Runtime tests | 48 passed, 0 failed | Targeted `CognitiveCycleDecisionServicesTest`, `CognitiveCycleLifecycleServicesTest`, `ResourceBudgetExecutionGuardTest`; lightweight JUnit-compatible runner |
| Mechanism/planning tests | 83 passed, 0 failed | B1 feature learning, B2 governed procedure reuse, B3 coalition evidence/admission, B4 intention lifecycle, B6 calibration/governor and future-cycle planner integration; lightweight runner |
| Migration parser tests | 9 passed, 0 failed | V012–V015 resource/registration/sequence checks |
| **Total** | **140 passed, 0 failed** | Not a Maven Surefire/JUnit reactor execution |
| M8-B5 architecture guard | PASS | 11 modules; module/dependency/source inventory checks |
| R2 evidence guard | PASS | B2/B3 verified evidence and shadow/holdout boundaries |
| F-03/F-04 guard | PASS | Governed recovery procedure and bounded coalition competition checks |
| Repository ZIP/file checksum validation | To be confirmed by final delivery packaging | Archive `unzip -t` and external/internal SHA-256 verification |

## Not run; required from hosted CI or later research

- Full `./mvnw -B -ntp -Dphase0.postgres=true clean install` with real pinned dependencies and Surefire/JUnit.
- Real PostgreSQL repository, migration/backup/restore integration for V013–V015 and full LangGraph4j graph execution.
- Independent matched experiments, verified downstream-outcome corpus, target-specific uncertainty calibration, B2 transfer, B3 coalition effectiveness, B4 long-horizon effect, and B6 calibration/transfer.

## Evidence restrictions

- B1/B3 adaptive feedback remains `SHADOW`/`DISABLED` until independently verified outcomes, disjoint holdout cases and separate activation approval exist.
- B6 runtime remains `NOT_CALIBRATED`; implementation of the calibration lifecycle does not mean a calibrated runtime profile is active.
- Unit-test fixtures are not empirical training or holdout evidence.
- Hosted CI for this exact first-five candidate is pending. This report does not claim that the candidate is fully CI-accepted or that total conformance to either paper has been achieved.
