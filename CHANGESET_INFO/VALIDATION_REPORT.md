# M8-C1-C2 validation report

**Validation date:** 2026-11-23  
**Package:** `M8_C1_C2_TRUSTED_RESEARCH_HOST_QUALIFICATION_CHANGES_ONLY_2026-11-23.zip`

## Passed checks

- Python syntax compilation: all `scripts/m8/*.py` files.
- Operator tooling unit tests: **7 passed, 0 failed**.
  - Java-compatible Ed25519 record/payload interoperability verified by OpenSSL using a disposable encrypted test key.
  - Role-scoped evidence-type policy and exact contiguous PostgreSQL migration range V001–V015 tested.
  - Provisioner rejection of repository-local destinations tested before directory creation.
- Issuer provisioner shell syntax: `bash -n` passed.
- Issuer provisioner smoke test: disposable encrypted Ed25519 key generated; private mode `0600`, public mode `0644`, DER SHA-256 checked; temporary key files deleted.
- M8 architecture/dependency/source inventory guard: passed (11 modules, acyclic internal dependency graph, framework dependency and source/test inventory checks).
- M8-R2 evidence guard: passed.
- M8 pre-B7 F-03/F-04 procedural-memory and coalition guard: passed.
- M8 scenario register/split guard: passed (280 TRAIN, 280 HOLDOUT, 210 TRANSFER; development-only fixtures excluded).
- M8 full-graph experiment adapter guard: passed (pair matching, graph isolation, approval/effect and split gates).
- M8 mechanism-specific experimental evidence guard: passed.
- Canonical and mirrored CI workflow YAML parse: passed; both workflow files are byte-identical.
- Local candidate fingerprint manifest generated and structurally checked. It is informational only: this reconstructed directory has no `.git` metadata, so `gitCommit=NOT_AVAILABLE` and `gitWorkingTreeDirty=null`.

## Not run / pending

- **Maven compile, Java unit tests and PostgreSQL-backed reactor build were not run locally.** Maven is not installed in this execution environment; only Java 21 is available. The Maven Wrapper previously could not resolve the Apache distribution host from this environment. The first required post-application validation is the repository's hosted CI run for the exact commit containing this package.
- The prior Fix3 CI-green report predates this package. It does not validate these changes.
- No real project issuer keys, signatures, freeze context or qualification report were generated. Only temporary test keys were used and deleted.
- C1-C2 remain operationally pending until hosted CI evidence is finalized, seven externally governed public keys are provisioned/pinned, the CI issuer signs the exact freeze subject, and the one-unit full-graph qualification CLI passes.

## Interpretation

These checks establish static guard consistency and the operator-side Python/OpenSSL tooling behavior. They do **not** establish Java compilation, full-host runtime qualification, empirical mechanism effectiveness, calibrated uncertainty, or production readiness. Do not start the bulk 280-unit TRAIN run based on this package alone.
