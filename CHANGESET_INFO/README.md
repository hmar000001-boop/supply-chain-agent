# M8 runtime inventory and legacy-shadow cleanup repair

## Purpose

This repair addresses the two distinct causes shown by the 2026-11-23 hosted CI log:

1. The active module-owned `CognitiveCycleLifecycleServices.java` and its test were absent from `supply-chain-agent-runtime`. Their absence explains the exact inventory delta: one production source, one test source, and 24 annotated test methods/factories (`38/11/87` expected versus `37/10/63` found).
2. The two older shadow copies still existed under repository-root `src/`, outside every Maven module. Those are the files GAP-10 requires removing.

## What this changeset does

- Restores the authoritative module-owned production source and test at their Maven source-root paths.
- Adds a more explicit architecture-guard diagnostic if either active module-owned file is missing.
- Adds a fail-closed cleanup script which verifies the SHA-256 digests of the active module files and the two exact legacy root files. It refuses to remove changed/unrecognized files or any additional root-level Java sources.
- Adds five unit tests covering exact deletion, idempotency, missing active sources, altered legacy content and unexpected root Java sources.
- Adds a manually dispatched GitHub Actions workflow, **M8 Remove Legacy Root Source Shadows**. Run it once on `main` from the GitHub Actions UI. It will:
  1. Verify the module-owned active source/test against their expected SHA-256.
  2. Remove only the two audited root-level shadow files, if their exact SHA-256 matches.
  3. Run the architecture and durable-application static guards and the cleanup unit tests.
  4. Commit only those two root-file deletions using `GITHUB_TOKEN`.
  5. Dispatch the normal `ci.yml` workflow against the cleaned `main` revision.

## Important path distinction

**Keep these active module-owned files:**

- `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java`
- `supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java`

**Remove only these older repository-root shadows:**

- `src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java`
- `src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java`

The files share the same class names but are not identical. The root copies are older and must not replace the active module copies.

## What you do

1. Publish/extract this changes-only ZIP at the repository root using the same GitHub workflow you normally use.
2. Open **Actions** in GitHub (mobile is sufficient).
3. Select **M8 Remove Legacy Root Source Shadows**.
4. Select branch `main` and tap **Run workflow**.
5. That workflow will perform the guarded deletions, create the cleanup commit and start the normal Java CI workflow. Send the resulting Java CI run link for review.

No local Git, Java, Maven, Python, OpenSSL or artifact download is required.

## Fail-safe behavior

The cleanup script removes no files if the active module source/test are missing or differ from the audited files, if either root shadow has changed, or if any unexpected Java file exists under root `src/`. It never deletes files by basename or wildcard. If repository branch protection or Actions token permissions prevent the workflow commit, the workflow fails without force-pushing; resolve the repository permission/branch-protection requirement or delete only the two exact root-level paths through GitHub's file UI.

## Scope

This is a targeted CI/source-hygiene correction. It does not change the cognitive graph, PostgreSQL schema, mechanism policies, experimental evidence, signing identities or application behavior.
