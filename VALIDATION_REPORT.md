# Validation Report — Final Conformance Matrix Revision

**Date:** 2026-11-21  
**Scope:** documentation/evidence delivery only.

## Source reconstruction and integrity

- Base archive: `supply-chain-agent-main (6).zip` — SHA-256 `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`.
- Overlay 1: F-01/F-02 — SHA-256 `a05ed45d02d9a3c141f4f21d2b1924ddcc57c1f13779032b28a5f7146a08593a`.
- Overlay 2: F-03/F-04 — SHA-256 `27a16c8869f2e4eb54cbb00007a405e84caff6799bc6af04abee1ce3a5fe5d01`.
- Reconstructed source-tree fingerprint: `b13a09eb307a4c6237d2fc7361810a93636a350b13c06df34b595abe9ac727df`.
- Manifest entries: 757 files, SHA-256 listed individually in `docs/research/CONFORMANCE_SOURCE_BASELINE_MANIFEST.csv`.
- Inventory: 11 Maven modules; 12 POMs; 462 production Java files; 72 test Java files; 534 Java files total; no Java files outside module source roots; PostgreSQL migrations V001–V012.

## Matrix validation

- `PAPER-TO-CODE-CONFORMANCE-MATRIX.csv`: 30 data rows; 17 columns per row; CSV parser validation passed.
- `CONFORMANCE_ASSESSMENT_METADATA.json`: parsed successfully; recorded matrix row count is 30.
- The matrix distinguishes application-relevant concepts from the papers' survey/review corpus. E1–E4 and D0–D4 are separate analytical axes; the mappings are marked provisional and are not software quality scores.
- The report explicitly records that CoALA's conceptual framework and Fan & Lan's bounded review do not translate into a literal one-to-one implementation checklist.

## Independently repeated checks on reconstructed source

- `python3 scripts/verify-m8-b5-architecture.py` — PASS.
- `python3 scripts/verify-m8-r2-evidence.py` — PASS.
- `python3 scripts/verify-m8-preb7-f03-f04.py` — PASS.

## CI provenance and execution limitations

- The project owner confirmed the combined R1/R2 + F-01/F-02 + F-03/F-04 hosted Java 21/PostgreSQL CI is green.
- The GitHub Actions run URL, run ID and commit SHA were not supplied in the assessment workspace.
- The assessor did **not** rerun `./mvnw -B -ntp -Dphase0.postgres=true clean install` during this documentation revision.
- The static guards are separate from the owner-confirmed hosted CI result.

## Remaining evidence limitations

- R2 still reports `NO_VERIFIED_OUTCOME_CORPUS`, zero verified outcomes, zero independent holdout scenarios, candidate `SHADOW`, activation `DISABLED`, and baseline preserved.
- B6 calibration remains `NOT_CALIBRATED`.
- No causal advantage, cross-bundle superiority, transfer, full GraSP migration or real external supply-chain integration is claimed.

## Delivery integrity

- Repository paths are relative to the project root.
- The archive contains only the six listed repository documentation/evidence files plus delivery metadata.
- ZIP integrity and all internal SHA-256 entries are verified after packaging.
