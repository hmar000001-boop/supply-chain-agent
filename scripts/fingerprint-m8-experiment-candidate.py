#!/usr/bin/env python3
"""Fingerprint the M8 candidate from committed Git blobs, not the mutable CI workspace.

The CI runner may create Maven-wrapper, Trivy, CodeQL and Python cache files after
checkout. They are not source inputs and must never enter a candidate fingerprint.
Only files present in HEAD are hashed. Unexpected untracked candidate-source files
and unexpected tracked-file mutations still fail closed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import PurePosixPath
from pathlib import Path

MODULE_PREFIX = "supply-chain-agent-"
ROOT_FILES = {"pom.xml", "mvnw", "mvnw.cmd", ".gitignore", ".gitattributes", ".env.example"}
EXCLUDED_PARTS = {
    ".git", "target", "build", ".gradle", ".idea", ".vscode", "out",
    "node_modules", "__pycache__",
}
# chmod/bootstrap tooling can legitimately touch these checked-out launchers in CI.
# Their committed blobs are still fingerprinted; only post-checkout workspace drift
# on these two launcher paths is tolerated and explicitly recorded.
ALLOWED_CI_WORKSPACE_DRIFT = {"mvnw", "mvnw.cmd"}
# The pinned bootstrapper downloads this executable wrapper helper at runtime. It is
# deliberately not committed or included in the source fingerprint.
GENERATED_UNTRACKED_FILES = {".mvn/wrapper/maven-wrapper.jar"}


def git_bytes(root: Path, *args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.PIPE)


def git_text(root: Path, *args: str) -> str:
    return git_bytes(root, *args).decode("utf-8").strip()


def included_relative_path(relative: str) -> bool:
    path = PurePosixPath(relative)
    parts = path.parts
    if not parts or any(part in EXCLUDED_PARTS for part in parts):
        return False
    if path.suffix == ".pyc":
        return False
    if parts[0].startswith(MODULE_PREFIX):
        return True
    if parts[0] in {".mvn", ".github", "scripts"}:
        return True
    return len(parts) == 1 and parts[0] in ROOT_FILES


def workspace_changes(root: Path) -> tuple[list[str], int]:
    """Return tracked mutations and unexpected untracked candidate-source files."""
    changed_raw = git_bytes(root, "diff", "--name-only", "-z", "HEAD", "--")
    changed = sorted(item.decode("utf-8", "surrogateescape")
                     for item in changed_raw.split(b"\0") if item)
    unexpected_changed = [path for path in changed if path not in ALLOWED_CI_WORKSPACE_DRIFT]

    untracked_raw = git_bytes(root, "ls-files", "--others", "--exclude-standard", "-z")
    generated_count = 0
    unexpected_untracked: list[str] = []
    for item in untracked_raw.split(b"\0"):
        if not item:
            continue
        relative = item.decode("utf-8", "surrogateescape")
        if relative in GENERATED_UNTRACKED_FILES:
            generated_count += 1
        elif included_relative_path(relative):
            unexpected_untracked.append(relative)
    if unexpected_changed:
        raise RuntimeError(
            "tracked project source changed after checkout; refusing candidate fingerprint: "
            + ", ".join(unexpected_changed[:30])
        )
    if unexpected_untracked:
        raise RuntimeError(
            "untracked candidate source files are not allowed: "
            + ", ".join(sorted(unexpected_untracked)[:30])
        )
    return changed, generated_count


def collect_candidate_source(root: Path) -> dict:
    commit = git_text(root, "rev-parse", "HEAD")
    tree = git_text(root, "rev-parse", "HEAD^{tree}")
    tracked_changes, generated_count = workspace_changes(root)

    listing = git_bytes(root, "ls-tree", "-r", "-z", "--full-tree", "HEAD")
    files: list[dict[str, str]] = []
    for record in listing.split(b"\0"):
        if not record:
            continue
        try:
            metadata, raw_path = record.split(b"\t", 1)
            mode, object_type, object_id = metadata.decode("ascii").split(" ", 2)
        except ValueError as failure:
            raise RuntimeError("Git returned a malformed HEAD tree entry") from failure
        relative = raw_path.decode("utf-8", "surrogateescape")
        if not included_relative_path(relative):
            continue
        if object_type != "blob" or mode == "120000":
            raise RuntimeError(f"candidate source contains a non-regular Git object: {relative}")
        content = git_bytes(root, "cat-file", "blob", object_id)
        files.append({"path": relative, "sha256": hashlib.sha256(content).hexdigest()})

    files.sort(key=lambda item: item["path"].encode("utf-8", "surrogateescape"))
    digest_input = "".join(
        f"{item['path']}\0{item['sha256']}\n" for item in files
    ).encode("utf-8", "surrogateescape")
    return {
        "commit": commit,
        "tree": tree,
        "files": files,
        "codeTreeSha256": hashlib.sha256(digest_input).hexdigest(),
        "trackedWorkspaceChanges": tracked_changes,
        "generatedWrapperArtifactCount": generated_count,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path,
                        default=Path(__file__).resolve().parents[1]
                        / "target/m8-experiment-freeze/candidate-fingerprint.json")
    parser.add_argument("--ci-run-id", default=None)
    parser.add_argument("--ci-run-url", default=None)
    parser.add_argument("--ci-commit", default=None)
    parser.add_argument("--candidate-id", default="M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1")
    parser.add_argument("--ci-conclusion", choices=["success", "failure", "unknown"], default="unknown")
    args = parser.parse_args()

    root = args.root.resolve()
    snapshot = collect_candidate_source(root)
    ci_commit = args.ci_commit or snapshot["commit"]
    manifest = {
        "candidateId": args.candidate_id,
        "fingerprintAlgorithm": "SHA-256 over sorted committed HEAD path NUL file-SHA256 newline records",
        "fingerprintSource": "git-object-database:HEAD",
        "codeTreeSha256": snapshot["codeTreeSha256"],
        "fileCount": len(snapshot["files"]),
        "gitCommit": snapshot["commit"],
        "gitTree": snapshot["tree"],
        "gitWorkingTreeDirty": False,
        "gitTrackedWorkspaceChanges": snapshot["trackedWorkspaceChanges"],
        "generatedWrapperArtifactCountExcluded": snapshot["generatedWrapperArtifactCount"],
        "workspaceMutationPolicy": "Only post-checkout mvnw/mvnw.cmd launcher drift is tolerated; their committed HEAD blobs remain fingerprinted. All other tracked changes and untracked candidate-source files fail.",
        "ci": {
            "conclusion": args.ci_conclusion,
            "runId": args.ci_run_id or "NOT_PROVIDED",
            "runUrl": args.ci_run_url or "NOT_PROVIDED",
            "commit": ci_commit or "NOT_PROVIDED",
            "commitMatchesGitHead": None if not (ci_commit and snapshot["commit"]) else ci_commit == snapshot["commit"],
            "attestation": "OWNER_REPORTED_GREEN" if args.ci_conclusion == "success" else "NOT_ATTESTED_BY_THIS_SCRIPT",
        },
        "files": snapshot["files"],
    }
    output = args.output if args.output.is_absolute() else root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise RuntimeError(f"refusing to overwrite existing fingerprint: {output}")
    output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: manifest[key] for key in (
        "candidateId", "codeTreeSha256", "fileCount", "gitCommit", "gitTree",
        "gitWorkingTreeDirty", "gitTrackedWorkspaceChanges",
        "generatedWrapperArtifactCountExcluded", "ci",
    )}, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"M8 candidate fingerprint: FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
