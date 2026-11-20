# M8-PreB7 R2 — Outcome Feedback, Guarded Adaptation and Final Evidence Validation

**Delivery:** one integrated R2 changes-only package combining the former R2, R3 and R4 workstreams.  
**Baseline:** project-owner-confirmed green R1-DEF source state (2026-11-20).  
**Package shape:** complete source files at repository-relative paths; no patch files and no full-repository snapshot.

## Apply

1. Use the project repository at the accepted green R1-DEF revision.
2. Extract this ZIP at the repository root, allowing the included files to overwrite their matching paths.
3. Review `CHANGE_MANIFEST.csv` and `VALIDATION_REPORT.md`.
4. Commit the cumulative R2 change and push it.
5. Run the single hosted acceptance command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

The active and mirrored GitHub Actions workflows include the same R2 evidence, CodeQL and Trivy steps. The hosted CI run is the acceptance gate for the complete Maven reactor, live PostgreSQL migration/repository integration, and scanner artifacts.

## Scope

- **B2:** typed, scope/version/evidence-bound recovery-procedure drafts built only from registered action references. Draft approval changes review state only; it does not authorize or dispatch actions.
- **B3:** durable consumer receipts and separately verified downstream-outcome evidence. The active workspace remains cycle-bounded and ephemeral. Receipts alone are not usefulness signals.
- **B1/B3 feedback:** verified-evidence-only bounded training, disjoint holdout evaluation, scope-bound shadow candidates and a distinct host-authorized activation boundary. Neutral baseline behavior remains the default.
- **Persistence:** forward-only PostgreSQL migration V011, typed B2 draft/history repository, B3 receipt/outcome repository, and migration/integration tests.
- **Evidence/security:** reproducible M8-B3 ablation outputs, explicit no-corpus R2 status output, provenance/checksums, CodeQL and Trivy SARIF artifacts, source inventory, conformance and acceptance records.

## Important empirical boundary

The accepted inputs contain no independently verified downstream-outcome corpus and no independent holdout dataset. The deterministic R2 evidence artifact therefore reports:

- `NO_VERIFIED_OUTCOME_CORPUS`
- zero verified downstream outcomes and zero holdout scenarios
- candidate `SHADOW`, activation `DISABLED`
- `baselinePreserved=true`
- no empirical adaptive-policy advantage claimed

The R2 feedback implementation is a guarded capability and evaluation protocol. It is not evidence that adaptive selection outperforms the accepted B1/top-k or B3 baseline.

## Package integrity

`CHANGE_MANIFEST.csv` lists every added/replaced repository file. `SHA256SUMS.txt` contains a SHA-256 digest for every package file except the checksum file itself. Verify with:

```bash
sha256sum -c SHA256SUMS.txt
```

## Acceptance status

R1-DEF is accepted on project-owner-confirmed green CI. R2 has passed the local Java 21 compilation and focused source-level checks described in `VALIDATION_REPORT.md`, but is **not accepted** until the one full hosted Java 21/PostgreSQL CI run succeeds and CodeQL/Trivy findings are reviewed and dispositioned. Trivy is intentionally non-blocking in this initial evidence-producing run; a successful workflow is not equivalent to a clean scan or penetration-test certification.
