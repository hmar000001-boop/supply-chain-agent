# M8 Trusted Research CI Fix 1 — changes-only package

**Date:** 2026-11-22  
**Candidate:** `M8-GAP01-GAP05-TRUSTED-RESEARCH-COMPILE-FIX1`

## Package contents

This archive is a repository-root-relative changeset, **not a copy of the repository**. It contains only the 21 project files listed in `CHANGE_MANIFEST.csv`: 12 additions and 9 replacements. It contains no full source tree, no unchanged module files, no Maven wrapper, no POM replacement, and no dependency additions. It deletes no project files.

The `CHANGESET_INFO/` directory contains only this package's README, manifest, validation report, and checksums. It is packaging metadata, not application source.

## CI root cause

The 2026-11-22 hosted Maven build reached `supply-chain-agent-evaluation` and failed during Java compilation with 32 diagnostics. The preceding 11 reactor modules had compiled. The failures were not a Maven dependency-resolution problem; the evaluation module had an incomplete trusted-research integration:

1. The composition and analysis code referenced implementation types that were absent from the candidate source set: the B6 TRANSFER report, attestation/trust/repository types, endpoint and safety verifiers, the Git/CI freeze verifier, candidate fingerprint support, and the TRAIN bundle codec/archive support.
2. The deterministic TRAIN fixture factory imported B3 and request-interpretation APIs from incorrect packages, so several referenced types could not be resolved.
3. The analysis engine expected `MechanismSpecificExperimentPair.b6ValidationLabels()`, but that accessor was missing.
4. The trusted composition depended on a consistent candidate-code fingerprint, while the Java freeze verifier and Python fingerprint script had different canonicalization/scope rules.
5. The architecture guard and checked-in inventory were stale relative to the source/test tree and needed to be reconciled.

## How the repair addresses it

- Adds the missing B6 TRANSFER report and the expected `b6ValidationLabels()` accessor.
- Adds the missing Ed25519 attestation model, public-key trust loader, filesystem repository, endpoint verifier, safety verifier, B1/B2/B3/B6 artifact verifiers, and Git/CI freeze prerequisite verifier.
- Adds a versioned, size/depth/collection-bounded record-graph codec restricted to explicit M8 data-model package families. It does not use Java native object deserialization.
- Corrects the TRAIN fixture factory's B3 and interpretation API imports.
- Aligns Java and Python candidate fingerprint scope/canonicalization; the metadata and test inventory are updated, and the architecture guard is reconciled to 106 production sources, 20 test sources, and 70 test annotations.

## Apply

Extract the archive at the repository root, preserving the paths. Review `CHANGE_MANIFEST.csv` before overwriting existing files. No files are to be deleted.

Then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

## Validation status

Local focused checks passed as recorded in `VALIDATION_REPORT.md`, including Java 21 compilation of the new implementation classes and typed dependency closure, five focused trust tests through a lightweight JUnit-compatible runner, Java/Python fingerprint parity, six static guards, ZIP integrity, and per-file hash checks. This is **not** a full Maven/Surefire/PostgreSQL run. Hosted CI remains the authoritative qualification for this candidate.

No TRAIN data was collected. HOLDOUT and TRANSFER remain closed. The package does not provision private keys or claim independent custodianship merely from distinct key identities.
