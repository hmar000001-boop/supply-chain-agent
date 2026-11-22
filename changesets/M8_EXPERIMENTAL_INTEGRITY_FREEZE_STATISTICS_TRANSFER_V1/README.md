# M8 Experimental Integrity, Freeze, Confirmatory Statistics and TRANSFER Analysis — Changeset v1

## Apply

Extract this archive at the repository root, preserving the archive paths. It contains only complete new files and complete replacements; no project files are deleted.

## Contents

- Independent audit of the exact five TRAIN telemetry JSONL artifacts, including exact unit-ID/input-fingerprint mapping and byte-level revalidation.
- Typed independently verified episode-level endpoints for HOLDOUT and TRANSFER; telemetry, scripted model outputs and LLM-judge scores cannot be promoted into H1-H4 outcome evidence.
- Dependency-free Java paired analysis: 10,000 profile-stratified HOLDOUT bootstrap replicates, hierarchical family/profile TRANSFER bootstrap, continuous paired sign-flip inference, exact McNemar inference for binary endpoints, and HOLDOUT-only Holm correction across H1-H5.
- Independent zero-tolerance safety counter evidence for each of five mechanisms and both arms, required before TRAIN freeze and HOLDOUT/TRANSFER analysis.
- Content-addressed archive/read-back contract for the complete typed TRAIN bundle, immutable create-new freeze manifest binding code/commit/CI/dependencies and verifier identities, and a dependency-ordered readiness ledger with actionable reminders.
- Protocol amendment documenting the safety gate and remaining host-integration limitations.

## Important boundaries

This package does not execute any experiment, create verified outcomes, open HOLDOUT or TRANSFER, activate B1/B3/B6, or claim real-world supply-chain effectiveness. The concrete deterministic TRAIN port factory, trusted endpoint and safety verifiers, B6 label producer, full-content bundle codec, and Git/hosted-CI attestation adapter remain host integration requirements. The readiness ledger emits reminders in a snapshot; it is not a background scheduler.

The archive deliberately uses custom Java statistical routines and no external statistics dependency. Full Maven/Surefire/PostgreSQL qualification is pending hosted CI.

## Candidate fingerprint

- Candidate: `M8-GAP01-GAP05-EXPERIMENT-INTEGRITY-FREEZE-ANALYSIS-V1`
- Code-tree SHA-256: `e5f94c2d7da325010b5720dcf750b436b6e07962e672be21659767fb9e59aec6`
- Fingerprinted file count: 705
- Git commit: NOT_AVAILABLE in the extracted working tree
- CI conclusion: UNKNOWN / not attested by this package

See `VALIDATION_REPORT.md` for validation boundaries and `CHANGE_MANIFEST.csv` for all changed project paths.
