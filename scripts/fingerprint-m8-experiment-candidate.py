#!/usr/bin/env python3
"""Fingerprint the implementation tree for the M8 preregistered experiments.

Includes all files under supply-chain-agent-* modules plus reactor, wrapper, CI and scripts.
Excludes .git, target and generated experiment/build outputs. Uses only the Python stdlib.
"""
from __future__ import annotations
import argparse, hashlib, json, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PREFIX = "supply-chain-agent-"
ROOT_FILES = {"pom.xml", "mvnw", "mvnw.cmd", ".gitignore", ".gitattributes", ".env.example"}
EXCLUDED_PARTS = {".git", "target", "build", ".gradle", ".idea", ".vscode", "out", "node_modules", "__pycache__"}


def included(path: Path) -> bool:
    rel = path.relative_to(ROOT)
    if any(part in EXCLUDED_PARTS for part in rel.parts) or path.suffix == ".pyc":
        return False
    if path.is_dir():
        return False
    if rel.parts[0].startswith(MODULE_PREFIX):
        return True
    if rel.parts[0] in {".mvn", ".github", "scripts"}:
        return True
    if len(rel.parts) == 1 and rel.name in ROOT_FILES:
        return True
    return False


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def git_value(*args: str) -> str | None:
    try:
        return subprocess.check_output(["git", *args], cwd=ROOT, stderr=subprocess.DEVNULL, text=True).strip()
    except Exception:
        return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "target/m8-experiment-freeze/candidate-fingerprint.json")
    parser.add_argument("--ci-run-id", default=None)
    parser.add_argument("--ci-run-url", default=None)
    parser.add_argument("--ci-commit", default=None)
    parser.add_argument("--candidate-id", default="M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1")
    parser.add_argument("--ci-conclusion", choices=["success", "failure", "unknown"], default="unknown")
    args = parser.parse_args()

    files = []
    for path in sorted(ROOT.rglob("*")):
        if not included(path):
            continue
        if path.is_symlink():
            raise SystemExit(f"candidate source tree contains a symbolic link: {path.relative_to(ROOT)}")
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT).as_posix()
        content_hash = sha256(path.read_bytes())
        files.append({"path": rel, "sha256": content_hash})
    digest_input = "".join(f"{item['path']}\0{item['sha256']}\n" for item in files).encode("utf-8")
    head = git_value("rev-parse", "HEAD")
    status = git_value("status", "--porcelain")
    dirty = None if status is None else bool(status)
    ci_commit = args.ci_commit or head
    manifest = {
        "candidateId": args.candidate_id,
        "fingerprintAlgorithm": "SHA-256 over sorted UTF-8 path NUL file-SHA256 newline records",
        "codeTreeSha256": sha256(digest_input),
        "fileCount": len(files),
        "gitCommit": head or "NOT_AVAILABLE",
        "gitWorkingTreeDirty": dirty,
        "ci": {
            "conclusion": args.ci_conclusion,
            "runId": args.ci_run_id or "NOT_PROVIDED",
            "runUrl": args.ci_run_url or "NOT_PROVIDED",
            "commit": ci_commit or "NOT_PROVIDED",
            "commitMatchesGitHead": None if not (ci_commit and head) else ci_commit == head,
            "attestation": "OWNER_REPORTED_GREEN" if args.ci_conclusion == "success" else "NOT_ATTESTED_BY_THIS_SCRIPT"
        },
        "files": files
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: manifest[k] for k in ("candidateId", "codeTreeSha256", "fileCount", "gitCommit", "gitWorkingTreeDirty", "ci")}, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
