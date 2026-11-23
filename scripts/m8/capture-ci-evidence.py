#!/usr/bin/env python3
"""Capture success-gated M8-C1 source, Maven/Surefire and PostgreSQL evidence in CI.

This is deliberately not a CI attestation. The completed GitHub run must be independently
queried after completion and signed by the separate M8 CI issuer.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
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
            raise RuntimeError(f"invalid Surefire XML report {report.relative_to(root)}: {failure}") from failure
        module = report.relative_to(root).parts[0]
        module_totals = by_module.setdefault(module, {"reports": 0, **{key: 0 for key in totals}})
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
    state = {"firstVersion": minimum, "latestVersion": head, "appliedRows": count,
             "distinctVersions": distinct, "expectedVersionRange": "V001-V015"}
    if (minimum, head, count, distinct) != (1, EXPECTED_MIGRATION_HEAD,
                                             EXPECTED_MIGRATION_HEAD, EXPECTED_MIGRATION_HEAD):
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
    output = (args.output or root / "target/m8-c1/ci-build-evidence.json").resolve()
    if not output.is_relative_to(root / "target"):
        raise RuntimeError("CI build-evidence output must be under the ignored repository target/ directory")

    commit = run(root, "git", "rev-parse", "HEAD")
    expected_sha = os.environ.get("GITHUB_SHA", "").strip()
    if not expected_sha or expected_sha != commit:
        raise RuntimeError(f"GITHUB_SHA does not match checkout HEAD: env={expected_sha!r}, head={commit!r}")
    status = run(root, "git", "status", "--porcelain", "--untracked-files=all")
    if status:
        raise RuntimeError(f"Git working tree is not clean at CI evidence capture: {status}")
    git_tree = run(root, "git", "rev-parse", "HEAD^{tree}")

    fingerprint_script = root / "scripts/fingerprint-m8-experiment-candidate.py"
    fingerprint_output = root / "target/m8-c1/candidate-fingerprint.json"
    fingerprint_output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(fingerprint_script), "--output", str(fingerprint_output),
                    "--candidate-id", "M8-C1-C2-TRUSTED-RESEARCH-HOST"],
                   cwd=root, check=True, text=True, capture_output=True)
    candidate = json.loads(fingerprint_output.read_text(encoding="utf-8"))
    if candidate.get("gitCommit") != commit or candidate.get("gitWorkingTreeDirty") is not False:
        raise RuntimeError("canonical candidate fingerprint did not bind to the clean CI checkout")

    pom = ET.parse(root / "pom.xml").getroot()
    modules_node = pom.find("m:modules", NS)
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
        "gitWorkingTreeClean": True,
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
    print(json.dumps({"output": str(output), "gitCommit": commit,
                      "candidateCodeFingerprint": candidate["codeTreeSha256"],
                      "reactorEntryCount": evidence["reactorEntryCount"],
                      "surefire": surefire, "postgresMigrationLedger": postgres}, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"M8-C1 CI evidence capture: FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
