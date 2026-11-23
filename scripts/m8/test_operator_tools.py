#!/usr/bin/env python3
"""Unit tests for M8-C1/C2 operator-side evidence and signing policy helpers."""
from __future__ import annotations

import base64
import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load(name: str, relative: str):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module


capture = load("m8_capture_ci_evidence", "scripts/m8/capture-ci-evidence.py")
finalize = load("m8_finalize_ci_freeze", "scripts/m8/finalize-c1-freeze-record.py")
signer = load("m8_sign_attestation", "scripts/m8/sign-m8-attestation.py")


class M8OperatorToolsTest(unittest.TestCase):
    def test_dependency_manifest_fingerprint_is_stable_and_content_sensitive(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "pom.xml").write_text("<project>one</project>\n", encoding="utf-8")
            module = root / "module"
            module.mkdir()
            (module / "pom.xml").write_text("<project>two</project>\n", encoding="utf-8")
            first = capture.dependency_manifest_fingerprint(root)
            self.assertEqual(first, finalize.dependency_manifest_fingerprint(root))
            self.assertEqual(first, capture.dependency_manifest_fingerprint(root))
            (module / "pom.xml").write_text("<project>changed</project>\n", encoding="utf-8")
            self.assertNotEqual(first, capture.dependency_manifest_fingerprint(root))

    def test_surefire_summary_counts_all_modules_and_rejects_failures(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            one = root / "supply-chain-agent-a/target/surefire-reports"
            two = root / "supply-chain-agent-b/target/surefire-reports"
            one.mkdir(parents=True); two.mkdir(parents=True)
            (one / "TEST-a.xml").write_text('<testsuite tests="3" failures="0" errors="0" skipped="1"/>', encoding="utf-8")
            (two / "TEST-b.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>', encoding="utf-8")
            result = capture.collect_surefire(root)
            self.assertEqual((5, 0, 0, 1),
                             (result["tests"], result["failures"], result["errors"], result["skipped"]))
            (two / "TEST-b.xml").write_text('<testsuite tests="2" failures="1" errors="0" skipped="0"/>', encoding="utf-8")
            with self.assertRaises(RuntimeError):
                capture.collect_surefire(root)

    def test_postgres_migration_ledger_requires_contiguous_v001_to_v015(self):
        expected = {"firstVersion": 1, "latestVersion": 15, "appliedRows": 15,
                    "distinctVersions": 15, "expectedVersionRange": "V001-V015"}
        self.assertEqual(expected, capture.parse_migration_ledger("1|15|15|15"))
        for invalid in ("0|15|15|15", "1|15|14|14", "1|16|15|15", "1|15|15|14"):
            with self.subTest(invalid=invalid):
                with self.assertRaises(RuntimeError):
                    capture.parse_migration_ledger(invalid)

    def test_ci_attestation_subject_matches_java_canonical_field_order(self):
        context = {
            "gitCommit": "1" * 40,
            "ciWorkflowUrl": "https://github.com/acme/repo/actions/runs/123",
            "ciRunId": "123",
            "ciConclusion": "SUCCESS",
            "candidateCodeFingerprint": "2" * 64,
            "dependencyManifestFingerprint": "3" * 64,
            "gitWorkingTreeClean": "true",
        }
        expected = finalize.hashlib.sha256(("|".join(context.values())).encode("utf-8")).hexdigest()
        self.assertEqual(expected, finalize.attestation_subject(context))

    @unittest.skipUnless(shutil.which("openssl"), "OpenSSL is required for Ed25519 interoperability test")
    def test_signer_emits_java_compatible_ed25519_attestation(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            repository = base / "checkout"
            private_dir = base / "private"
            attestation_dir = base / "attestations"
            for directory in (repository, private_dir, attestation_dir):
                directory.mkdir()
            private_key = private_dir / "m8-b1-v1.pem"
            public_key = private_dir / "m8-b1-v1.pub"
            passphrase = "ephemeral-test-passphrase-2026"
            subprocess.run([
                "openssl", "genpkey", "-algorithm", "ED25519", "-aes-256-cbc",
                "-pass", f"pass:{passphrase}", "-out", str(private_key),
            ], check=True, capture_output=True)
            os.chmod(private_key, 0o600)
            subprocess.run([
                "openssl", "pkey", "-in", str(private_key), "-passin", f"pass:{passphrase}",
                "-pubout", "-out", str(public_key),
            ], check=True, capture_output=True)

            subject = "a" * 64
            command = [
                sys.executable, str(ROOT / "scripts/m8/sign-m8-attestation.py"),
                "--role", "b1", "--evidence-type", "M8_B1_DOWNSTREAM_OUTCOME",
                "--subject-fingerprint", subject, "--private-key", str(private_key),
                "--attestation-directory", str(attestation_dir), "--root", str(repository),
            ]
            signed = subprocess.run(command, input=(passphrase + "\n").encode(),
                                    text=False, capture_output=True, check=True)
            self.assertIn(b"privateKeyMaterialWritten=false", signed.stdout)
            records = list(attestation_dir.glob("*.attestation"))
            self.assertEqual(1, len(records))
            lines = records[0].read_text(encoding="ascii").splitlines()
            self.assertEqual("M8-ATTESTATION-FILE-V1", lines[0])
            evidence_type = base64.b64decode(lines[1], validate=True).decode("utf-8")
            issuer = base64.b64decode(lines[3], validate=True).decode("utf-8")
            self.assertEqual("M8_B1_DOWNSTREAM_OUTCOME", evidence_type)
            self.assertEqual("m8-b1-v1", issuer)
            canonical = ("M8-ED25519-ATTESTATION-V1\n" + evidence_type + "\n" + subject
                         + "\n" + issuer + "\n" + lines[4] + "\n").encode("utf-8")
            payload = base / "payload.bin"
            signature = base / "signature.bin"
            payload.write_bytes(canonical)
            signature.write_bytes(base64.b64decode(lines[5], validate=True))
            subprocess.run([
                "openssl", "pkeyutl", "-verify", "-rawin", "-pubin", "-inkey", str(public_key),
                "-in", str(payload), "-sigfile", str(signature),
            ], check=True, capture_output=True)
            self.assertFalse(any("PRIVATE KEY" in item.name for item in attestation_dir.iterdir()))

    @unittest.skipUnless(shutil.which("bash") and shutil.which("realpath"),
                         "bash and realpath are required for the issuer provisioner guard test")
    def test_provisioner_rejects_repository_local_key_destinations_before_creating_them(self):
        with tempfile.TemporaryDirectory() as temporary:
            private_inside_checkout = ROOT / "target" / "m8-provisioner-should-not-create" / "private"
            public_outside_checkout = Path(temporary) / "public-export"
            self.assertFalse(private_inside_checkout.exists())
            result = subprocess.run([
                "bash", str(ROOT / "scripts/m8/provision-m8-issuer-key.sh"),
                "b1", str(private_inside_checkout), str(public_outside_checkout),
            ], text=True, capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("must be stored outside the repository", result.stderr)
            self.assertFalse(private_inside_checkout.exists())
            self.assertFalse(public_outside_checkout.exists())

    def test_each_role_can_sign_only_its_registered_evidence_types(self):
        allowed = {
            "endpoint": "M8_ENDPOINT_HOLDOUT",
            "safety": "M8_SAFETY_TRAIN",
            "ci": "M8_CI_FREEZE",
            "b1": "M8_B1_DOWNSTREAM_OUTCOME",
            "b2": "M8_B2_APPROVED_PROCEDURE",
            "b3": "M8_B3_COALITION_OUTCOME",
            "b6": "M8_B6_SIMULATOR_LABEL",
        }
        for role, (issuer, evidence_types) in signer.ROLE_POLICY.items():
            self.assertTrue(issuer.startswith("m8-"))
            self.assertIn(allowed[role], evidence_types)
            self.assertEqual(1, len(evidence_types)) if role != "endpoint" else self.assertEqual(2, len(evidence_types))
        self.assertNotIn("M8_CI_FREEZE", signer.ROLE_POLICY["endpoint"][1])
        self.assertNotIn("M8_B3_COALITION_OUTCOME", signer.ROLE_POLICY["b1"][1])


if __name__ == "__main__":
    unittest.main(verbosity=2)
