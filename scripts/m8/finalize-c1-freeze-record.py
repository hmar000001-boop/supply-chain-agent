#!/usr/bin/env python3
"""Verify the completed GitHub run and create the immutable M8 Java FreezeContext properties."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path


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
        digest.update(len(encoded_name).to_bytes(8, "big")); digest.update(encoded_name)
        digest.update(len(content).to_bytes(8, "big")); digest.update(content)
    return digest.hexdigest()


def candidate_fingerprint(root: Path) -> str:
    script = root / "scripts/fingerprint-m8-experiment-candidate.py"
    output = root / "target/m8-c1/finalizer-fingerprint.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(script), "--output", str(output),
                    "--candidate-id", "M8-C1-C2-TRUSTED-RESEARCH-HOST"],
                   cwd=root, check=True, text=True, capture_output=True)
    manifest = json.loads(output.read_text(encoding="utf-8"))
    return manifest["codeTreeSha256"]


def attestation_subject(context: dict[str, str]) -> str:
    canonical = "|".join((context["gitCommit"], context["ciWorkflowUrl"], context["ciRunId"],
                           context["ciConclusion"], context["candidateCodeFingerprint"],
                           context["dependencyManifestFingerprint"], context["gitWorkingTreeClean"]))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True,
                        help="ci-build-evidence.json downloaded from the matching GitHub run")
    parser.add_argument("--output", type=Path, required=True,
                        help="new properties file outside the repository, protected as operator evidence")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    root = args.root.resolve()
    raw_evidence_path = args.evidence.expanduser().absolute()
    if raw_evidence_path.is_symlink() or not raw_evidence_path.is_file():
        raise RuntimeError("CI evidence input must be a regular, non-symlink file")
    if raw_evidence_path.stat().st_size > 2 * 1024 * 1024:
        raise RuntimeError("CI evidence file is unexpectedly large")
    evidence_path = raw_evidence_path.resolve()
    output = args.output.expanduser().resolve()
    if output.is_relative_to(root):
        raise RuntimeError("freeze context must be written outside the repository working tree")
    if output.exists():
        raise RuntimeError(f"refusing to overwrite an existing freeze context: {output}")

    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    if evidence.get("schemaVersion") != "M8-C1-CI-BUILD-EVIDENCE-V1":
        raise RuntimeError("unknown or unsupported M8-C1 CI evidence schema")
    required = ("repository", "gitCommit", "gitTree", "candidateCodeFingerprint", "dependencyManifestFingerprint",
                "workflow", "workflowRunId", "workflowRunAttempt", "workflowRunUrl",
                "reactorEntryCount", "surefire", "postgresMigrationLedger")
    missing = [field for field in required if field not in evidence]
    if missing:
        raise RuntimeError(f"CI evidence omits required fields: {missing}")
    if evidence.get("gitWorkingTreeClean") is not True:
        raise RuntimeError("CI evidence does not assert a clean checkout")
    if not isinstance(evidence.get("repository"), str) or not re.fullmatch(
            r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", evidence["repository"]):
        raise RuntimeError("CI evidence has an invalid GitHub OWNER/REPOSITORY identity")
    if not str(evidence.get("workflow", "")).strip() or not str(evidence.get("workflowRef", "")).strip():
        raise RuntimeError("CI evidence omits the workflow name or ref")
    if not str(evidence.get("workflowRunId", "")).isdigit() or int(evidence["workflowRunId"]) < 1:
        raise RuntimeError("CI evidence has an invalid workflow run ID")
    if not str(evidence.get("workflowRunAttempt", "")).isdigit() or int(evidence["workflowRunAttempt"]) < 1:
        raise RuntimeError("CI evidence has an invalid workflow run attempt")
    if not re.fullmatch(r"[0-9a-f]{64}", str(evidence.get("candidateCodeFingerprint", ""))):
        raise RuntimeError("CI evidence has an invalid candidate code fingerprint")
    if not re.fullmatch(r"[0-9a-f]{64}", str(evidence.get("dependencyManifestFingerprint", ""))):
        raise RuntimeError("CI evidence has an invalid dependency manifest fingerprint")
    if not str(evidence.get("gitCommit", "")).strip() or not str(evidence.get("gitTree", "")).strip():
        raise RuntimeError("CI evidence omits the Git commit or tree identity")
    if int(evidence.get("candidateFileCount", 0)) < 1:
        raise RuntimeError("CI evidence records an empty candidate code tree")
    if evidence["workflowConclusion"] != "PENDING_FINAL_GITHUB_RUN_CHECK":
        raise RuntimeError("unexpected workflow conclusion marker in the pre-completion evidence")
    if evidence["buildStepOutcome"] != "SUCCESS_GATED_STEP":
        raise RuntimeError("CI evidence was not captured by a success-gated step")
    if evidence["reactorEntryCount"] != 12:
        raise RuntimeError("the full 12-entry Maven reactor was not captured")
    surefire = evidence["surefire"]
    if (surefire.get("tests", 0) < 1 or surefire.get("failures") != 0
            or surefire.get("errors") != 0):
        raise RuntimeError(f"Maven/Surefire evidence is not green: {surefire}")
    migration = evidence["postgresMigrationLedger"]
    if (migration.get("firstVersion") != 1 or migration.get("latestVersion") != 15
            or migration.get("appliedRows") != 15 or migration.get("distinctVersions") != 15):
        raise RuntimeError(f"PostgreSQL migration ledger is not exactly V001-V015: {migration}")

    commit = run(root, "git", "rev-parse", "HEAD")
    if commit != evidence["gitCommit"]:
        raise RuntimeError("local checkout HEAD does not match the CI source revision")
    tree = run(root, "git", "rev-parse", "HEAD^{tree}")
    if tree != evidence["gitTree"]:
        raise RuntimeError("local checkout Git tree does not match the captured CI tree")
    status = run(root, "git", "status", "--porcelain", "--untracked-files=all")
    if status:
        raise RuntimeError(f"local checkout is not clean: {status}")
    candidate = candidate_fingerprint(root)
    if candidate != evidence["candidateCodeFingerprint"]:
        raise RuntimeError("local executable source fingerprint differs from the captured CI fingerprint")
    dependency = dependency_manifest_fingerprint(root)
    if dependency != evidence["dependencyManifestFingerprint"]:
        raise RuntimeError("local Maven POM fingerprint differs from the captured CI dependency fingerprint")

    repository = evidence["repository"]
    run_id = str(evidence["workflowRunId"])
    gh = subprocess.run(["gh", "run", "view", run_id, "--repo", repository,
                         "--json", "status,conclusion,headSha,url,workflowName,attempt"],
                        cwd=root, check=True, text=True, capture_output=True)
    hosted = json.loads(gh.stdout)
    if hosted.get("status") != "completed" or hosted.get("conclusion") != "success":
        raise RuntimeError(f"GitHub Actions run is not completed successfully: {hosted}")
    if hosted.get("headSha") != commit:
        raise RuntimeError("GitHub Actions run SHA does not match the clean local checkout")
    if hosted.get("url") != evidence["workflowRunUrl"]:
        raise RuntimeError("GitHub Actions run URL does not match the evidence bundle")
    if str(hosted.get("attempt")) != str(evidence.get("workflowRunAttempt")):
        raise RuntimeError("GitHub Actions run attempt does not match the evidence bundle")
    if hosted.get("workflowName") != evidence.get("workflow"):
        raise RuntimeError("GitHub Actions workflow name does not match the evidence bundle")

    context = {
        "schema": "M8-C1-FREEZE-CONTEXT-V1",
        "candidateCodeFingerprint": candidate,
        "gitCommit": commit,
        "ciWorkflowUrl": evidence["workflowRunUrl"],
        "ciRunId": run_id,
        "ciConclusion": "SUCCESS",
        "hostedCiGreen": "true",
        "gitWorkingTreeClean": "true",
        "dependencyManifestFingerprint": dependency,
        "bundleVerifierId": "m8-bundle-verifier-v1",
    }
    context["ciAttestationSubject"] = attestation_subject(context)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise RuntimeError(f"refusing to overwrite an existing freeze context: {output}")
    contents = "".join(f"{key}={value}\n" for key, value in context.items())
    pending = output.with_name(output.name + ".pending")
    if pending.exists():
        raise RuntimeError(f"pending output already exists: {pending}")
    pending.write_text(contents, encoding="utf-8")
    os.chmod(pending, 0o600)
    try:
        os.link(pending, output)
    except FileExistsError:
        raise RuntimeError(f"refusing to overwrite an existing freeze context: {output}")
    finally:
        pending.unlink(missing_ok=True)

    print(json.dumps({"freezeContext": str(output), "ciAttestationSubject": context["ciAttestationSubject"],
                      "gitCommit": commit, "candidateCodeFingerprint": candidate,
                      "dependencyManifestFingerprint": dependency,
                      "hostedCiConclusion": hosted["conclusion"],
                      "surefire": surefire, "postgresMigrationLedger": migration}, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"M8-C1 freeze finalization: FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
