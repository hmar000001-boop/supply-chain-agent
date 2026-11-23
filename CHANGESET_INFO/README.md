# M8-C1-C2 Trusted Research Host Qualification — changes-only package

This ZIP contains **only the 14 repository files listed in `CHANGESET_INFO/CHANGE_MANIFEST.csv`**, stored at their repository-root-relative paths. It is not a full repository archive and contains no Git patch.

## Apply

1. Back up/commit your current working tree.
2. Extract the ZIP at the root of the existing repository, preserving paths. The 14 source files will overlay their matching paths; `CHANGESET_INFO/` is package metadata and may be retained outside the repository or removed after review.
3. Review `CHANGESET_INFO/CHANGE_MANIFEST.csv` and run `sha256sum -c CHANGESET_INFO/SHA256SUMS.txt` from the repository root.
4. Commit and push. Run the updated GitHub Actions workflow on that exact commit. This package has **not** been validated by hosted CI yet.

## What's included

- C1 CI evidence capture and exact hosted-run finalizer, including full reactor/Surefire and contiguous PostgreSQL V001–V015 checks.
- C2 one-role-at-a-time encrypted Ed25519 key provisioner and role-scoped Java-compatible attestation signer.
- Java composition fingerprint exposure and a one-unit, five-mechanism full-graph qualification CLI.
- Operator runbook, focused tests, and corrected source/test inventory.

## Important safety and scope boundaries

- Private signing keys are **not** included. Do not create project issuer keys inside the repository. Use separate custodians/approval paths and role-specific key material.
- The local candidate fingerprint JSON is informational only; its `gitCommit` is `NOT_AVAILABLE` because the reconstruction lacks `.git` metadata. The hosted CI capture creates the authoritative committed-source fingerprint.
- Apply/commit this package and obtain a new green hosted CI run before producing the CI freeze record. Then provision/pin all seven issuer public keys, obtain the signed CI freeze attestation, and run the one-unit qualification.
- The preflight fixture is not TRAIN evidence and must not be counted in the 280-unit batch. Do not launch bulk TRAIN before all C1-C2 gates pass.
- This is a research/reference implementation. Passing the preflight does not establish empirical mechanism efficacy or production readiness.

See `docs/research/M8-C1-C2-TRUSTED-RESEARCH-HOST-QUALIFICATION-2026-11-23.md` for exact operator commands and acceptance gates.
