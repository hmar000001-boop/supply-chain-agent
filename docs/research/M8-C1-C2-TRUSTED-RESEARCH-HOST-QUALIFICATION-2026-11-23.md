# M8-C1-C2 — Trusted Research Host Qualification

**Status:** implementation package prepared; hosted CI freeze, external issuer custody and one-unit qualification remain operator gates.

The accompanying `M8-C1-C2-CANDIDATE-CODE-FINGERPRINT-LOCAL-2026-11-23.json` is a local source-tree inventory only. This reconstructed working copy has no `.git` metadata, so its `gitCommit` is `NOT_AVAILABLE`; do not use it as the CI candidate fingerprint or freeze evidence. The success-gated CI capture generates the authoritative fingerprint from the committed, clean checkout.

**Purpose:** establish an auditable source/CI freeze and qualify the existing full LangGraph4j experiment host before any bulk TRAIN execution. This package does not produce TRAIN evidence, verified outcome labels, a frozen learning bundle, HOLDOUT authorization, TRANSFER authorization or causal/effectiveness findings.

## 1. Gate model

| Gate | Evidence required | Pass condition | Current state |
|---|---|---|---|
| C1-A — source/build capture | CI evidence artifact | exact commit/tree, clean checkout, candidate code fingerprint, Maven POM fingerprint, full 12-entry reactor, clean Surefire XML totals and PostgreSQL V001–V015 ledger | workflow and capture code added; next hosted run required |
| C1-B — hosted-run finalization | completed GitHub Actions run + exact artifact + clean local checkout | same repository, workflow, run ID/attempt, URL, commit, tree, candidate fingerprint and POM fingerprint; conclusion `success` | finalizer added; requires a real completed run and authenticated `gh` |
| C1-C — signed freeze | `M8_CI_FREEZE` Ed25519 attestation | signature verifies against the externally pinned `m8-ci-v1.pub` identity | signer added; requires real external key custody/signature |
| C2-A — issuer trust root | seven pinned Ed25519 public keys | seven expected issuer IDs, exactly seven `.pub` files and seven distinct public-key fingerprints | provisioning tool added; requires seven externally governed identities |
| C2-B — full-graph preflight | qualification JSON report | one preregistered TRAIN fixture, matched BASELINE/TREATMENT pair for each of B1/B2/B3/B4/B6, all ten sessions unique, every graph stops at APPROVAL, approval ID present, zero action dispatches, zero simulator state effects, zero sandbox side effects | CLI and fail-closed checks added; must run after C1-C2-A |

**Do not launch the 280-unit TRAIN batch before all of these gates pass.** The user's previously reported green CI run was for the earlier Fix3 repair, before this C1-C2 package changes the source and CI workflow; it is not evidence for this package.

## 2. C1 — produce the freeze context

### 2.1 Apply, commit and push

Apply this changes-only package at the repository root. Commit and push the changes. The new CI workflow runs the operator-tool unit tests, architecture/evidence guards, the full Maven reactor (`./mvnw -B -ntp -Dphase0.postgres=true clean install`), PostgreSQL backup/restore verification, then captures the C1 evidence artifact only when the build and preceding steps have succeeded.

The evidence step is intentionally success-gated. It fails closed unless it can confirm:

- `HEAD` equals `GITHUB_SHA` and the Git working tree is clean;
- the source-code fingerprint can be generated from the clean checkout;
- the Maven reactor contains the root parent plus eleven named modules;
- at least one Surefire report exists and aggregate failures/errors are zero;
- `public.agent_schema_history` contains exactly versions V001–V015 (15 rows, 15 distinct versions, max version 15).

The step writes `target/m8-c1/ci-build-evidence.json`. This is build evidence, **not** an attestation and not a final workflow conclusion. The artifact is included in `m8-r2-test-evaluation-security-and-evidence`.

### 2.2 Download and finalize the exact hosted run

On a machine with GitHub CLI authenticated for the repository, check out the exact commit from the successful workflow run and keep that checkout clean. Use the actual run ID and repository from GitHub; do not copy values from a different run.

```bash
REPOSITORY=OWNER/REPOSITORY
RUN_ID=ACTUAL_GITHUB_ACTIONS_RUN_ID
ARTIFACT_DIR=/secure/m8-ci-artifact-download
mkdir -p "$ARTIFACT_DIR"
gh run download "$RUN_ID" --repo "$REPOSITORY" \
  --name m8-r2-test-evaluation-security-and-evidence \
  --dir "$ARTIFACT_DIR"
find "$ARTIFACT_DIR" -type f -name ci-build-evidence.json -print
```

Use the exact path printed by `find` as `--evidence`. Do not rename or edit the JSON artifact. From the matching, clean checkout:

```bash
python3 scripts/m8/finalize-c1-freeze-record.py \
  --root "$PWD" \
  --evidence /secure/m8-ci-artifact-download/EXACT/PATH/ci-build-evidence.json \
  --output /secure/m8-c1/ci-freeze-context.properties
```

The finalizer rechecks the commit, Git tree, source fingerprint, dependency-manifest fingerprint and clean tree locally. It also queries the completed GitHub Actions run with `gh run view` and requires `completed/success`, the same commit, URL, attempt and workflow name. It writes a new, mode-0600 properties file outside the checkout and refuses to overwrite an existing file. The `ciAttestationSubject` is SHA-256 over the canonical Java `FreezeContext` fields and is covered by the CI issuer's signature in the next step.

Prerequisites: `git`, Python 3, `gh` authenticated for the repository, and an exact local checkout of the CI commit. Do not finalize a pending, failed, cancelled or superseded workflow run.

## 3. C2 — provision external issuer identities

### 3.1 Fixed issuer registry

| Role | Issuer ID | Permitted evidence types |
|---|---|---|
| Endpoint evidence | `m8-endpoint-v1` | `M8_ENDPOINT_HOLDOUT`, `M8_ENDPOINT_TRANSFER` |
| Safety evidence | `m8-safety-v1` | `M8_SAFETY_TRAIN` |
| CI freeze | `m8-ci-v1` | `M8_CI_FREEZE` |
| B1 outcome | `m8-b1-v1` | `M8_B1_DOWNSTREAM_OUTCOME` |
| B2 procedure | `m8-b2-v1` | `M8_B2_APPROVED_PROCEDURE` |
| B3 coalition outcome | `m8-b3-v1` | `M8_B3_COALITION_OUTCOME` |
| B6 simulator label | `m8-b6-v1` | `M8_B6_SIMULATOR_LABEL` |

The application requires all seven public-key pins even when only the CI signature is required for this preflight. The issuer identities must have separate Ed25519 key material. For an independent-evidence claim, use distinct authorized custodians/approval paths as well; seven labels or seven files controlled by one unreviewed process do **not** establish independent custody.

### 3.2 Provision one key per role

Each role's authorized custodian runs the script on a secure machine. Use separate private-key and public-export directories for every role. The public-export directory is role-specific; the custodian transfers only the resulting `.pub` file through the approved channel to a separate trust-root administrator. Do not give every custodian write access to the final pinned-key directory.
The provisioning machine needs Bash, OpenSSL 3, GNU `realpath -m`, `mktemp` and `sha256sum`; run the script from the checked-out repository so it can reject repository-local destinations.

For example, the CI custodian runs:

```bash
bash scripts/m8/provision-m8-issuer-key.sh \
  ci /secure/m8/private/ci /secure/m8/public-export/ci
```

Repeat with the corresponding private/export paths for `endpoint`, `safety`, `b1`, `b2`, `b3` and `b6`. The trust-root administrator then creates the exact pin directory and installs the seven public files (the sources are the role-specific export folders):

```bash
install -d -m 0700 /secure/m8/pinned-public-keys
install -m 0444 /secure/m8/public-export/endpoint/m8-endpoint-v1.pub /secure/m8/pinned-public-keys/
install -m 0444 /secure/m8/public-export/safety/m8-safety-v1.pub /secure/m8/pinned-public-keys/
install -m 0444 /secure/m8/public-export/ci/m8-ci-v1.pub /secure/m8/pinned-public-keys/
install -m 0444 /secure/m8/public-export/b1/m8-b1-v1.pub /secure/m8/pinned-public-keys/
install -m 0444 /secure/m8/public-export/b2/m8-b2-v1.pub /secure/m8/pinned-public-keys/
install -m 0444 /secure/m8/public-export/b3/m8-b3-v1.pub /secure/m8/pinned-public-keys/
install -m 0444 /secure/m8/public-export/b6/m8-b6-v1.pub /secure/m8/pinned-public-keys/
```

Create the separate attestation directory before signing:

```bash
install -d -m 0700 /secure/m8/attestations
```

Repeat with each role token: `endpoint`, `safety`, `ci`, `b1`, `b2`, `b3`, `b6`. OpenSSL prompts twice to create and confirm the encrypted key, then prompts once more to decrypt it for public-key export; enter the same passphrase at all three prompts. Do not put the passphrase in command arguments, shell history, CI variables, repository files or the attestation. Keep each encrypted private key under its own role's custody. Transfer only the public key to the pinned-key administrator.

The completed public-key directory must contain exactly these files and nothing else:

```text
m8-endpoint-v1.pub
m8-safety-v1.pub
m8-ci-v1.pub
m8-b1-v1.pub
m8-b2-v1.pub
m8-b3-v1.pub
m8-b6-v1.pub
```

Record and independently compare each public key's DER SHA-256 fingerprint in the external key registry. Do not reuse one public key for multiple issuer IDs. Do not place private keys in the repository, application, CI artifact, ZIP or shared attestation directory.

### 3.3 Sign the C1 freeze (CI custodian only)

After C1 finalization and after the seven public pins are assembled, the `m8-ci-v1` custodian signs the exact subject in the freeze context:

```bash
SUBJECT=$(awk -F= '$1 == "ciAttestationSubject" { print $2 }' \
  /secure/m8-c1/ci-freeze-context.properties)

python3 scripts/m8/sign-m8-attestation.py \
  --role ci \
  --evidence-type M8_CI_FREEZE \
  --subject-fingerprint "$SUBJECT" \
  --private-key /secure/m8/private/ci/m8-ci-v1.pem \
  --attestation-directory /secure/m8/attestations
```

The signer prompts OpenSSL interactively, accepts only the evidence types assigned to that role, requires an encrypted PKCS#8 private key with restrictive file permissions, writes the immutable Java-compatible attestation record, and refuses to overwrite an existing record. Never pass the passphrase on the command line.

For this qualification run, no B1/B2/B3/B6 outcome attestation or endpoint holdout/transfer attestation is needed yet. Their signing permissions are prepared for later independently verified evidence; their keys must nevertheless be pinned and unique.

## 4. Run the one-unit full-graph qualification

First ensure the checkout remains the same exact clean CI commit and that `ci-freeze-context.properties`, public keys, attestations and the TRAIN artifact archive are outside the checkout. Build the reactor locally so the evaluation module and its runtime dependencies are installed:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Then run the qualification CLI. Use secure paths without whitespace in this command, or quote/escape each path within Maven's `exec.args` string:

```bash
./mvnw -B -ntp -pl supply-chain-agent-evaluation \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.supplychain.agent.evaluation.experiment.research.M8SingleUnitHostQualificationCli \
  -Dexec.classpathScope=runtime \
  -Dexec.args="--repo-root . --ci-freeze-context /secure/m8-c1/ci-freeze-context.properties --pinned-public-key-dir /secure/m8/pinned-public-keys --attestation-dir /secure/m8/attestations --training-archive-dir /secure/m8/training-archive --output target/m8-c1c2/qualification-report.json"
```

The CLI:

1. validates the exact, versioned freeze-context property set and its canonical attestation subject;
2. loads the seven exact public pins, rejects duplicated key material and verifies the signed `M8_CI_FREEZE` attestation;
3. rechecks the current Git commit, clean tree, candidate source fingerprint and dependency-manifest fingerprint;
4. selects the first preregistered `MULTI_ALTERNATIVE` TRAIN fixture as a **preflight-only** unit;
5. runs the actual full graph for matched BASELINE/TREATMENT arms for each of B1, B2, B3, B4 and B6 (five pairs, ten graph sessions);
6. checks exact fixture/input and request fingerprints, distinct session identities, the APPROVAL interruption boundary, a bound approval ID, one-or-more approval requests, zero action-dispatch attempts, zero simulator-state effects and a side-effect-free sandbox audit;
7. writes a qualification-only JSON report under ignored `target/` and does not resume the graph or dispatch approval/action effects.

A pass prints `M8_C1_C2_HOST_QUALIFICATION_PASS` and writes `target/m8-c1c2/qualification-report.json`. Review and retain that report as qualification evidence. Any missing metric, unknown schema, absent signature, duplicate key, mismatch, unexpected graph boundary or nonzero effect is a hard failure.

**Important TRAIN isolation rule:** the qualification unit is generated from the TRAIN split to exercise the registered TRAIN host, but its run is explicitly preflight-only. It is not added to the 280-unit TRAIN telemetry, is not a verified outcome label, is not fed into any mechanism trainer and must not be used as an experimental observation. The later 280-unit batch must execute the exact preregistered TRAIN set under its separate evidence-capture path.

## 5. Acceptance record and next gates

C1-C2 is complete only when the following items are retained outside the source tree with appropriate integrity controls:

- exact GitHub repository, workflow, run ID and attempt;
- downloaded CI evidence artifact and its SHA-256;
- successful finalizer output and the mode-0600 freeze-context file;
- external issuer register with seven public-key fingerprints and custodianship/approval records;
- signed CI attestation and public-key pin set;
- qualification JSON and its SHA-256, with all five mechanism pairs and ten isolated sessions;
- the final Git commit SHA containing this implementation package and the successful CI run for that exact commit.

Only after this acceptance should a separate decision authorize the 280-unit TRAIN run. The TRAIN batch must still produce mechanism-specific, verifiable source evidence, signed safety and mechanism attestations, a complete content-addressed bundle and an independent bundle verification. HOLDOUT (280) and TRANSFER (210) remain closed until the corresponding signed artifact/endpoint gates pass.

This qualification proves only that the current deterministic research host reaches the intended approval boundary under its fixed test conditions. It does not show that any mechanism is empirically effective, establish calibrated uncertainty, prove transfer performance, validate real-world supplier/carrier outcomes, complete GAP-07 learning-action contracts, close GAP-08 durable application hosting, or demonstrate production readiness. GAP-06's GraSP-like skill governance remains a separately scoped architecture decision; GAP-09 still requires independent outcome/holdout evidence; GAP-10 repository hygiene remains to be completed.
