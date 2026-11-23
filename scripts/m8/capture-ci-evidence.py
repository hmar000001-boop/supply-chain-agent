#!/usr/bin/env python3
"""Capture success-gated M8-C1 source, Maven/Surefire and PostgreSQL evidence in CI.

This is deliberately not a CI attestation. The completed GitHub run must be independently
queried after completion and signed by the separate M8 CI issuer.

The candidate fingerprint is calculated from committed Git blobs. Runner-generated caches
are not source, and are excluded by the fingerprint policy; tracked project-source mutation
and untracked candidate-source injection still fail closed.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
EXPECTED_MIGRATION_HEAD = 15
EXPECTED_REACTOR_MODULES = 12  # root parent + eleven named modules


def run(root: Path, *command: str) -> str:
    result = subprocess.run(command, cwd=root, check=True, text=True, capture_output=True)
    return result.stdout.strip()


def dependency_manifest_fingerprint(root: Path) -> str:
    excluded = {".git", "target", "build", ".gradle", ".idea", "out", "node_modules"}
    poms = []
    for path in root.rglob("pom.xml"):
        rel = path.relative_to(root)
        if any(part in excluded for part in rel.parts):
            continue
        if path.is_symlink() or not path.is_file():
            raise RuntimeError(f"dependency manifest contains a non-regular file: {rel}")
        poms.append((rel.as_posix(), path.read_bytes()))
    digest = hashlib.sha256()
    for name, content in sorted(poms):
        encoded_name = name.encode("utf-8")
        digest.update(len(encoded_name).to_bytes(8, "big"))
        digest.update(encoded_name)
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    return digest.hexdigest()


def collect_surefire(root: Path) -> dict:
    reports = sorted(root.glob("supply-chain-agent-*/target/surefire-reports/TEST-*.xml"))
    if not reports:
        raise RuntimeError("no Maven Surefire XML reports found after clean install")
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    by_module: dict[str, dict[str, int]] = {}
    for report in reports:
        try:
            document = ET.parse(report).getroot()
        except ET.ParseError as failure:
            raise RuntimeError(
                f"invalid Surefire XML report {report.relative_to(root)}: {failure}"
            ) from failure
        module = report.relative_to(root).parts[0]
        module_totals = by_module.setdefault(
            module, {"reports": 0, **{key: 0 for key in totals}}
        )
        suites = [document] if document.tag == "testsuite" else list(document.iter("testsuite"))
        if not suites:
            raise RuntimeError(f"Surefire XML report has no testsuite: {report.relative_to(root)}")
        module_totals["reports"] += 1
        for suite in suites:
            for key in totals:
                value = int(suite.attrib.get(key, "0"))
                totals[key] += value
                module_totals[key] += value
    if totals["tests"] < 1 or totals["failures"] or totals["errors"]:
        raise RuntimeError(f"Surefire evidence is not clean: {totals}")
    return {**totals, "reportFiles": len(reports), "modulesWithReports": by_module}


def parse_migration_ledger(output: str) -> dict:
    fields = output.split("|")
    if len(fields) != 4:
        raise RuntimeError(f"unexpected PostgreSQL migration ledger response: {output!r}")
    minimum, head, count, distinct = map(int, fields)
    state = {
        "firstVersion": minimum,
        "latestVersion": head,
        "appliedRows": count,
        "distinctVersions": distinct,
        "expectedVersionRange": "V001-V015",
    }
    if (minimum, head, count, distinct) != (
        1, EXPECTED_MIGRATION_HEAD, EXPECTED_MIGRATION_HEAD, EXPECTED_MIGRATION_HEAD
    ):
        raise RuntimeError(f"PostgreSQL migration history is incomplete or dirty: {state}")
    return state


def collect_postgres_migration_state(root: Path) -> dict:
    query = (
        "SELECT COALESCE(MIN(version),0)::text || '|' || COALESCE(MAX(version),0)::text || '|' "
        "|| COUNT(*)::text || '|' || COUNT(DISTINCT version)::text FROM public.agent_schema_history"
    )
    output = run(root, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-A", "-t", "-c", query)
    return parse_migration_ledger(output)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args()
    root = args.root.resolve()
    requested_output = args.output or Path("target/m8-c1/ci-build-evidence.json")
    output = (requested_output if requested_output.is_absolute() else root / requested_output).resolve()
    if not output.is_relative_to(root / "target"):
        raise RuntimeError("CI build-evidence output must be under the ignored repository target/ directory")

    commit = run(root, "git", "rev-parse", "HEAD")
    expected_sha = os.environ.get("GITHUB_SHA", "").strip()
    if not expected_sha or expected_sha != commit:
        raise RuntimeError(f"GITHUB_SHA does not match checkout HEAD: env={expected_sha!r}, head={commit!r}")

    # The current workflow runs `chmod +x ./mvnw`; the supplied CI log also shows
    # mvnw.cmd as modified. These launcher-only worktree mutations are not source changes.
    # Record and restore just those two known paths, then require the tracked checkout to
    # be clean. Any other tracked mutation fails closed. Untracked generated runner data
    # is handled separately by the committed-tree fingerprint policy.
    changed_raw = subprocess.check_output(
        ["git", "diff", "--name-only", "-z", "HEAD", "--"], cwd=root
    )
    tracked_workspace_changes = [
        item.decode("utf-8", "surrogateescape")
        for item in changed_raw.split(b"\0") if item
    ]
    launcher_allowlist = {"mvnw", "mvnw.cmd"}
    unexpected_changes = sorted(set(tracked_workspace_changes) - launcher_allowlist)
    if unexpected_changes:
        raise RuntimeError(
            "tracked project source changed during CI: " + ", ".join(unexpected_changes[:30])
        )
    if tracked_workspace_changes:
        run(root, "git", "restore", "--source=HEAD", "--worktree", "--", *tracked_workspace_changes)

    # Remove only generated artifacts explicitly observed in this CI failure. This restores
    # the strict clean-checkout invariant rather than silently accepting an arbitrarily dirty
    # runner. The Maven distribution itself is cached under ~/.m2 and is not removed.
    generated_artifacts_removed: list[str] = []
    trivy_cache = root / ".cache/trivy"
    if trivy_cache.is_symlink():
        raise RuntimeError("refusing to clean a symlink at .cache/trivy")
    if trivy_cache.is_dir():
        shutil.rmtree(trivy_cache)
        generated_artifacts_removed.append(".cache/trivy")
    wrapper_jar = root / ".mvn/wrapper/maven-wrapper.jar"
    if wrapper_jar.is_symlink():
        raise RuntimeError("refusing to clean a symlink at .mvn/wrapper/maven-wrapper.jar")
    if wrapper_jar.exists():
        if not wrapper_jar.is_file():
            raise RuntimeError("unexpected non-file at .mvn/wrapper/maven-wrapper.jar")
        wrapper_jar.unlink()
        generated_artifacts_removed.append(".mvn/wrapper/maven-wrapper.jar")
    scripts_root = root / "scripts"
    if scripts_root.is_dir():
        for cache_dir in scripts_root.rglob("__pycache__"):
            if cache_dir.is_symlink():
                raise RuntimeError(f"refusing to clean Python cache symlink: {cache_dir.relative_to(root)}")
            if cache_dir.is_dir():
                shutil.rmtree(cache_dir)
                generated_artifacts_removed.append(cache_dir.relative_to(root).as_posix())

    # Any remaining modification, including an unexpected untracked file, is an actual
    # checkout-integrity failure and must not be papered over.
    status = run(root, "git", "status", "--porcelain", "--untracked-files=all")
    if status:
        raise RuntimeError("Git working tree remains dirty after known CI artifacts were cleaned: " + status)

    # This computes the candidate over committed Git blobs. It also checks that there are
    # no untracked files in candidate-source locations. It intentionally does not reject
    # runner caches such as .cache/trivy, __pycache__, target/, or the downloaded Maven
    # wrapper JAR; none of those are committed candidate source.
    fingerprint_script = root / "scripts/fingerprint-m8-experiment-candidate.py"
    fingerprint_output = root / "target/m8-c1/candidate-fingerprint.json"
    fingerprint_output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [sys.executable, str(fingerprint_script), "--root", str(root),
         "--output", str(fingerprint_output),
         "--candidate-id", "M8-C1-C2-TRUSTED-RESEARCH-HOST"],
        cwd=root, check=True, text=True, capture_output=True,
    )
    candidate = json.loads(fingerprint_output.read_text(encoding="utf-8"))
    if candidate.get("gitCommit") != commit or candidate.get("gitWorkingTreeDirty") is not False:
        raise RuntimeError("candidate fingerprint is not bound to the committed source tree")
    git_tree = run(root, "git", "rev-parse", "HEAD^{tree}")
    if candidate.get("gitTree") != git_tree:
        raise RuntimeError("candidate fingerprint Git tree does not match checkout HEAD")

    pom = ET.parse(root / "pom.xml").getroot()
    modules_node = pom.find("m:modules", NS)
    if modules_node is None:
        raise RuntimeError("root POM does not declare a Maven reactor modules element")
    modules = [node.text.strip() for node in modules_node.findall("m:module", NS) if node.text]
    if len(modules) + 1 != EXPECTED_REACTOR_MODULES:
        raise RuntimeError(f"expected root parent + eleven modules, found {len(modules) + 1}")

    repository = os.environ.get("GITHUB_REPOSITORY", "").strip()
    run_id = os.environ.get("GITHUB_RUN_ID", "").strip()
    run_attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "").strip()
    if not repository or not run_id.isdigit() or not run_attempt.isdigit():
        raise RuntimeError("GitHub repository, run ID and attempt are required in the hosted CI environment")
    workflow_url = f"https://github.com/{repository}/actions/runs/{run_id}"
    surefire = collect_surefire(root)
    postgres = collect_postgres_migration_state(root)
    evidence = {
        "schemaVersion": "M8-C1-CI-BUILD-EVIDENCE-V1",
        "capturedAtUtc": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "repository": repository,
        "gitCommit": commit,
        "gitTree": git_tree,
        # Retained for the current finalizer's schema compatibility. This flag now means
        # committed project source integrity, not absence of disposable runner caches.
        "gitWorkingTreeClean": True,
        "gitTrackedWorkspaceChanges": tracked_workspace_changes,
        "generatedRunnerArtifactsRemoved": generated_artifacts_removed,
        "candidateFingerprintSource": "committed Git HEAD blobs; generated runner files removed before strict clean-tree verification",
        "candidateCodeFingerprint": candidate["codeTreeSha256"],
        "candidateFileCount": candidate["fileCount"],
        "dependencyManifestFingerprint": dependency_manifest_fingerprint(root),
        "workflow": os.environ.get("GITHUB_WORKFLOW", ""),
        "workflowRef": os.environ.get("GITHUB_REF", ""),
        "workflowRunId": run_id,
        "workflowRunAttempt": run_attempt,
        "workflowRunUrl": workflow_url,
        "workflowConclusion": "PENDING_FINAL_GITHUB_RUN_CHECK",
        "buildStepOutcome": "SUCCESS_GATED_STEP",
        "mavenCommand": "./mvnw -B -ntp -Dphase0.postgres=true clean install",
        "reactorParentAndModules": ["root-parent", *modules],
        "reactorEntryCount": len(modules) + 1,
        "surefire": surefire,
        "postgresMigrationLedger": postgres,
        "interpretation": "Build evidence only. The complete hosted run must be queried after completion and independently attested by the M8 CI issuer.",
        "privateKeyMaterialIncluded": False,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise RuntimeError(f"refusing to overwrite CI evidence file: {output}")
    output.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(output),
        "gitCommit": commit,
        "gitTree": git_tree,
        "candidateCodeFingerprint": candidate["codeTreeSha256"],
        "gitTrackedWorkspaceChanges": tracked_workspace_changes,
        "generatedRunnerArtifactsRemoved": generated_artifacts_removed,
        "reactorEntryCount": evidence["reactorEntryCount"],
        "surefire": surefire,
        "postgresMigrationLedger": postgres,
    }, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"M8-C1 CI evidence capture: FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
