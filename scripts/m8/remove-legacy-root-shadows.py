#!/usr/bin/env python3
"""Safely remove the two obsolete repository-root Java shadow files.

This cleanup is deliberately exact and fail-closed. It verifies the active
module-owned source/test files are present with the expected bytes, verifies
that any root-level shadow files have the exact audited legacy digests, and
refuses to remove or ignore any other Java source under the root src/ tree.
"""
from __future__ import annotations

import hashlib
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

ACTIVE_MODULE_FILES = {
    "supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java": "04577ba12c96f8d89a553bf8558e30fbc1201f01e3f7d1b0445d086453607442",
    "supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java": "c8be7b1b555c70d9333afcc3698e6d6d094a0b1814cec6dc71129946f7b8fddf",
}

LEGACY_ROOT_FILES = {
    "src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java": "32373e86f96846469f057da25b25886bd2e2b6e0aab13a1d2e2c9e88df74d26f",
    "src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java": "e2a2a025604812c3be393428a72a3d392cc785a9436793d1a0a84c9dd3734b1f",
}


class CleanupError(RuntimeError):
    """Raised when the repository differs from the audited cleanup target."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def apply_cleanup(
    root: Path,
    *,
    active_files: dict[str, str] | None = None,
    legacy_files: dict[str, str] | None = None,
) -> list[str]:
    """Preflight all paths, then remove only exact known legacy files."""
    active_files = ACTIVE_MODULE_FILES if active_files is None else active_files
    legacy_files = LEGACY_ROOT_FILES if legacy_files is None else legacy_files
    root = root.resolve()

    # First prove the authoritative module-owned copies are present and exact.
    for relative, expected_digest in active_files.items():
        path = root / relative
        if path.is_symlink() or not path.is_file():
            raise CleanupError(f"Active module-owned source is missing or unsafe: {relative}")
        actual = sha256(path)
        if actual != expected_digest:
            raise CleanupError(
                f"Active module-owned source differs from the audited version: {relative}; "
                f"expected {expected_digest}, found {actual}"
            )

    # Validate every legacy path before deleting anything (all-or-nothing).
    existing: list[tuple[str, Path]] = []
    allowed = set(legacy_files)
    for relative, expected_digest in legacy_files.items():
        path = root / relative
        if not path.exists():
            continue  # Idempotent when already removed.
        if path.is_symlink() or not path.is_file():
            raise CleanupError(f"Legacy shadow path is not a regular file: {relative}")
        actual = sha256(path)
        if actual != expected_digest:
            raise CleanupError(
                f"Refusing to delete unexpected content at {relative}; "
                f"expected {expected_digest}, found {actual}"
            )
        existing.append((relative, path))

    root_src = root / "src"
    if root_src.exists():
        unexpected = sorted(
            p.relative_to(root).as_posix()
            for p in root_src.rglob("*.java")
            if p.relative_to(root).as_posix() not in allowed
        )
        if unexpected:
            raise CleanupError(
                "Unexpected root-level Java sources exist; cleanup will not touch them: "
                + ", ".join(unexpected)
            )

    removed: list[str] = []
    for relative, path in existing:
        path.unlink()
        removed.append(relative)

    remaining = sorted(p.relative_to(root).as_posix() for p in root_src.rglob("*.java")) if root_src.exists() else []
    if remaining:
        raise CleanupError("Root-level Java sources remain after cleanup: " + ", ".join(remaining))

    return removed


def write_github_output(name: str, value: str) -> None:
    output = os.environ.get("GITHUB_OUTPUT")
    if not output:
        return
    with Path(output).open("a", encoding="utf-8") as stream:
        stream.write(f"{name}={value}\n")


def main() -> int:
    try:
        removed = apply_cleanup(ROOT)
    except CleanupError as exc:
        print(f"M8 root-shadow cleanup: REFUSED: {exc}", file=sys.stderr)
        return 2

    write_github_output("changed", "true" if removed else "false")
    if removed:
        print("M8 root-shadow cleanup: removed exactly:")
        for relative in removed:
            print(f" - {relative}")
    else:
        print("M8 root-shadow cleanup: already clean; no files changed")
    print("Active module-owned source and test files verified by SHA-256")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
