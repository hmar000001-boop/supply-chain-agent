#!/usr/bin/env python3
"""Regression tests for M8 candidate fingerprinting on a tool-mutated CI runner."""
from __future__ import annotations

import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


fingerprint = load("m8_candidate_fingerprint", ROOT / "scripts/fingerprint-m8-experiment-candidate.py")
capture = load("m8_capture_ci_evidence", ROOT / "scripts/m8/capture-ci-evidence.py")


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=root, text=True).strip()


class M8CommittedTreeFingerprintTest(unittest.TestCase):
    def make_repo(self, root: Path) -> None:
        (root / "supply-chain-agent-domain/src/main/java/example").mkdir(parents=True)
        (root / ".mvn/wrapper").mkdir(parents=True)
        (root / "scripts/m8").mkdir(parents=True)
        (root / "pom.xml").write_text("<project>root</project>\n", encoding="utf-8")
        (root / ".gitignore").write_text("**/target/\n", encoding="utf-8")
        (root / "mvnw").write_text("#!/bin/sh\necho pinned\n", encoding="utf-8")
        (root / "mvnw.cmd").write_text("@echo off\necho pinned\n", encoding="utf-8")
        (root / ".mvn/wrapper/maven-wrapper.properties").write_text(
            "distributionVersion=3.9.16\n", encoding="utf-8"
        )
        (root / "scripts/m8/tool.py").write_text("print('source')\n", encoding="utf-8")
        (root / "supply-chain-agent-domain/src/main/java/example/Domain.java").write_text(
            "package example; final class Domain {}\n", encoding="utf-8"
        )
        git(root, "init", "-q")
        git(root, "config", "user.email", "ci-test@example.invalid")
        git(root, "config", "user.name", "CI Test")
        git(root, "add", ".")
        git(root, "commit", "-qm", "fixture")

    def test_wrapper_and_tool_caches_do_not_pollute_committed_source_fingerprint(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_repo(root)
            original = fingerprint.collect_candidate_source(root)

            # Reproduce the observed runner drift after Maven / security tools execute.
            (root / "mvnw").write_text("#!/bin/sh\necho runner-mutated\n", encoding="utf-8")
            (root / "mvnw.cmd").write_text(
                "@echo off\r\necho runner-mutated\r\n", encoding="utf-8"
            )
            (root / ".mvn/wrapper/maven-wrapper.jar").write_bytes(b"runtime wrapper jar")
            (root / ".cache/trivy/db").mkdir(parents=True)
            (root / ".cache/trivy/db/trivy.db").write_bytes(b"runtime cache")
            (root / "scripts/m8/__pycache__").mkdir()
            (root / "scripts/m8/__pycache__/tool.cpython-312.pyc").write_bytes(b"bytecode")

            current = fingerprint.collect_candidate_source(root)
            self.assertEqual(original["codeTreeSha256"], current["codeTreeSha256"])
            self.assertEqual(original["files"], current["files"])
            self.assertEqual(["mvnw", "mvnw.cmd"], current["trackedWorkspaceChanges"])
            self.assertEqual(1, current["generatedWrapperArtifactCount"])
            self.assertNotIn(
                ".mvn/wrapper/maven-wrapper.jar", {item["path"] for item in current["files"]}
            )

    def test_unexpected_tracked_project_source_change_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_repo(root)
            source = root / "supply-chain-agent-domain/src/main/java/example/Domain.java"
            source.write_text("package example; final class TamperedDomain {}\n", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "tracked project source changed"):
                fingerprint.collect_candidate_source(root)

    def test_untracked_candidate_source_file_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_repo(root)
            injected = root / "supply-chain-agent-domain/src/main/java/example/Injected.java"
            injected.write_text("package example; final class Injected {}\n", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "untracked candidate source files"):
                fingerprint.collect_candidate_source(root)

    def test_non_candidate_runner_cache_is_ignored(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_repo(root)
            before = fingerprint.collect_candidate_source(root)["codeTreeSha256"]
            cache = root / ".cache/trivy/policy/content"
            cache.mkdir(parents=True)
            (cache / "policy.rego").write_text("package trivy\n", encoding="utf-8")
            after = fingerprint.collect_candidate_source(root)["codeTreeSha256"]
            self.assertEqual(before, after)

    def test_capture_accepts_known_runner_drift_and_writes_commit_bound_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            self.make_repo(root)
            (root / "scripts").mkdir(exist_ok=True)
            shutil.copy2(
                ROOT / "scripts/fingerprint-m8-experiment-candidate.py",
                root / "scripts/fingerprint-m8-experiment-candidate.py",
            )
            git(root, "add", "scripts/fingerprint-m8-experiment-candidate.py")
            git(root, "commit", "-qm", "add fingerprint tool")

            module_names = [f"supply-chain-agent-{name}" for name in (
                "domain", "memory", "core", "simulator", "actions", "mechanisms",
                "planning", "runtime", "infrastructure-postgres", "app", "evaluation"
            )]
            modules_xml = "".join(f"<module>{name}</module>" for name in module_names)
            root_pom = (
                '<project xmlns="http://maven.apache.org/POM/4.0.0"><modules>'
                + modules_xml + "</modules></project>\n"
            )
            (root / "pom.xml").write_text(root_pom, encoding="utf-8")
            git(root, "add", "pom.xml")
            git(root, "commit", "-qm", "add reactor declaration")
            commit = git(root, "rev-parse", "HEAD")

            report_dir = root / "supply-chain-agent-domain/target/surefire-reports"
            report_dir.mkdir(parents=True)
            (report_dir / "TEST-domain.xml").write_text(
                '<testsuite tests="4" failures="0" errors="0" skipped="1"/>',
                encoding="utf-8",
            )
            fake_bin = Path(temporary) / "fake-bin"
            fake_bin.mkdir()
            psql = fake_bin / "psql"
            psql.write_text(
                "#!/bin/sh\nprintf '1|15|15|15\\n'\n", encoding="utf-8"
            )
            psql.chmod(0o755)

            # Reproduce every material class in the supplied CI failure summary.
            (root / "mvnw").write_text("#!/bin/sh\necho modified by CI\n", encoding="utf-8")
            (root / "mvnw.cmd").write_text(
                "@echo off\r\necho modified by CI\r\n", encoding="utf-8"
            )
            (root / ".mvn/wrapper/maven-wrapper.jar").write_bytes(b"runtime wrapper")
            cache = root / ".cache/trivy/db"
            cache.mkdir(parents=True)
            (cache / "trivy.db").write_bytes(b"runtime cache")
            pycache = root / "scripts/m8/__pycache__"
            pycache.mkdir()
            (pycache / "operator.cpython-312.pyc").write_bytes(b"bytecode")

            output = "target/m8-c1/ci-build-evidence.json"
            argv = ["capture-ci-evidence.py", "--root", str(root), "--output", output]
            env = {
                "GITHUB_SHA": commit,
                "GITHUB_REPOSITORY": "example/supply-chain-agent",
                "GITHUB_RUN_ID": "12345",
                "GITHUB_RUN_ATTEMPT": "1",
                "GITHUB_WORKFLOW": "Java CI",
                "GITHUB_REF": "refs/heads/main",
                "PATH": str(fake_bin) + os.pathsep + os.environ.get("PATH", ""),
            }
            with patch.object(sys, "argv", argv), patch.dict(os.environ, env, clear=False):
                self.assertEqual(0, capture.main())

            data = json.loads((root / output).read_text(encoding="utf-8"))
            self.assertEqual(commit, data["gitCommit"])
            self.assertTrue(data["gitWorkingTreeClean"])
            self.assertEqual(["mvnw", "mvnw.cmd"], data["gitTrackedWorkspaceChanges"])
            self.assertEqual("#!/bin/sh\necho pinned\n", (root / "mvnw").read_text(encoding="utf-8"))
            self.assertEqual("@echo off\necho pinned\n", (root / "mvnw.cmd").read_text(encoding="utf-8"))
            self.assertEqual("", git(root, "diff", "--name-only", "HEAD", "--"))
            self.assertEqual("", git(root, "status", "--porcelain", "--untracked-files=all"))
            self.assertFalse((root / ".cache/trivy").exists())
            self.assertFalse((root / ".mvn/wrapper/maven-wrapper.jar").exists())
            self.assertFalse((root / "scripts/m8/__pycache__").exists())
            self.assertCountEqual(
                [".cache/trivy", ".mvn/wrapper/maven-wrapper.jar", "scripts/m8/__pycache__"],
                data["generatedRunnerArtifactsRemoved"],
            )
            self.assertEqual(12, data["reactorEntryCount"])
            self.assertEqual(4, data["surefire"]["tests"])
            self.assertEqual("V001-V015", data["postgresMigrationLedger"]["expectedVersionRange"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
