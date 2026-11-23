# M8 CI evidence-capture repair — changes-only package

**Target repository:** `hmar000001-boop/supply-chain-agent`  
**Inspected workflow source revision:** `c49c543`  
**Purpose:** repair the post-build M8-C1 evidence-capture failure caused by tool-generated runner files being treated as source changes.

## Apply

Copy the three repository-relative files in this archive into the repository root, preserving their paths, and commit them to the branch that triggers the existing Java CI workflow.

No local build, terminal command, Maven invocation, or artifact download is required. The existing workflow already discovers `scripts/m8/test_*.py`; the new regression suite therefore runs automatically in GitHub Actions.

## What changes

1. `scripts/fingerprint-m8-experiment-candidate.py`
   - hashes only source files committed in `HEAD` using Git object data;
   - excludes runner-generated files from the candidate fingerprint;
   - rejects unexpected tracked changes and untracked candidate-source files;
   - permits only the known Maven launcher worktree drift (`mvnw`, `mvnw.cmd`) when used independently, while recording it.

2. `scripts/m8/capture-ci-evidence.py`
   - records the observed launcher drift;
   - restores only those two launcher paths from `HEAD`;
   - removes only the generated paths evidenced in the failing run: `.cache/trivy`, `.mvn/wrapper/maven-wrapper.jar`, and Python `__pycache__` below `scripts/`;
   - then requires `git status --porcelain --untracked-files=all` to be empty;
   - creates the same M8-C1 evidence schema with the committed source fingerprint, Surefire summary, reactor inventory and PostgreSQL V001–V015 ledger.

3. `scripts/m8/test_source_fingerprint.py`
   - adds five regression tests, including a full capture-path test using a temporary Git repository and fixture Surefire/PostgreSQL data.

## Integrity properties retained

- This does not suppress the clean-checkout requirement.
- Only the two observed Maven launcher paths are restored; any other tracked mutation fails.
- Untracked source files in candidate roots fail.
- The candidate fingerprint uses committed Git blobs, not the mutable CI workspace.
- The cleanup list is intentionally narrow; unknown untracked files remain visible and cause failure.

## Expected next action

After the commit, GitHub Actions runs the existing workflow plus the five new Python regression tests. The M8-C1 evidence step should then proceed past the prior `Git working tree is not clean` error. The full Maven/PostgreSQL CI result still has to be confirmed by that hosted run.

**Scope note:** this package repairs the evidence-capture failure only. It does not claim that the durable full-graph application host (GAP-08), complete learning-action contract (GAP-07), or source-inventory cleanup (GAP-10) have been completed.
