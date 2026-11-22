#!/usr/bin/env python3
"""Safely remove the two known orphan source copies from the repository-root src tree."""
from __future__ import annotations

import hashlib
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java":
        "32373e86f96846469f057da25b25886bd2e2b6e0aab13a1d2e2c9e88df74d26f",
    "src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java":
        "e2a2a025604812c3be393428a72a3d392cc785a9436793d1a0a84c9dd3734b1f",
}

# Validate every present file before deleting any of them.
present: list[Path] = []
for relative, expected_hash in EXPECTED.items():
    path = ROOT / relative
    if not path.exists():
        continue
    if not path.is_file():
        print(f"Refusing cleanup: expected a regular file at {relative}", file=sys.stderr)
        raise SystemExit(2)
    actual_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual_hash != expected_hash:
        print(f"Refusing cleanup: {relative} differs from the archived orphan copy", file=sys.stderr)
        print(f"expected {expected_hash}; found {actual_hash}", file=sys.stderr)
        raise SystemExit(2)
    present.append(path)

for path in present:
    path.unlink()

# Remove only now-empty directories, never recursively delete unrecognized files.
for relative in sorted({str(Path(item).parent) for item in EXPECTED}, key=lambda value: len(Path(value).parts), reverse=True):
    directory = ROOT / relative
    while directory != ROOT and directory.exists():
        try:
            directory.rmdir()
        except OSError:
            break
        directory = directory.parent

print(f"Removed {len(present)} verified orphan root-src file(s); canonical module sources were untouched.")
