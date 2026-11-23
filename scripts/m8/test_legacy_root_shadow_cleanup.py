from __future__ import annotations

import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("remove-legacy-root-shadows.py")
SPEC = importlib.util.spec_from_file_location("remove_legacy_root_shadows", SCRIPT)
assert SPEC and SPEC.loader
CLEANUP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLEANUP)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class LegacyRootShadowCleanupTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.active_data = {
            "supply-chain-agent-runtime/src/main/java/CognitiveCycleLifecycleServices.java": b"active production source\n",
            "supply-chain-agent-runtime/src/test/java/CognitiveCycleLifecycleServicesTest.java": b"active test source\n",
        }
        self.legacy_data = {
            "src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java": b"legacy production shadow\n",
            "src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java": b"legacy test shadow\n",
        }
        self.active = {key: digest(value) for key, value in self.active_data.items()}
        self.legacy = {key: digest(value) for key, value in self.legacy_data.items()}
        for relative, data in {**self.active_data, **self.legacy_data}.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def cleanup(self) -> list[str]:
        return CLEANUP.apply_cleanup(
            self.root,
            active_files=self.active,
            legacy_files=self.legacy,
        )

    def test_removes_only_the_two_exact_root_shadows(self) -> None:
        removed = self.cleanup()
        self.assertEqual(set(removed), set(self.legacy))
        for relative in self.active:
            self.assertTrue((self.root / relative).is_file())
        for relative in self.legacy:
            self.assertFalse((self.root / relative).exists())

    def test_is_idempotent_after_legacy_files_are_removed(self) -> None:
        first = self.cleanup()
        second = self.cleanup()
        self.assertEqual(len(first), 2)
        self.assertEqual(second, [])

    def test_refuses_to_delete_if_active_module_copy_is_missing(self) -> None:
        active_path = self.root / next(iter(self.active))
        active_path.unlink()
        with self.assertRaises(CLEANUP.CleanupError):
            self.cleanup()
        self.assertTrue(all((self.root / relative).exists() for relative in self.legacy))

    def test_refuses_unrecognized_root_java_source_without_partial_deletion(self) -> None:
        extra = self.root / "src/main/java/Unexpected.java"
        extra.write_text("class Unexpected {}\n", encoding="utf-8")
        with self.assertRaises(CLEANUP.CleanupError):
            self.cleanup()
        self.assertTrue(all((self.root / relative).exists() for relative in self.legacy))

    def test_refuses_modified_legacy_shadow_without_partial_deletion(self) -> None:
        first = self.root / next(iter(self.legacy))
        first.write_text("changed by an unknown actor\n", encoding="utf-8")
        with self.assertRaises(CLEANUP.CleanupError):
            self.cleanup()
        self.assertTrue(all((self.root / relative).exists() for relative in self.legacy))


if __name__ == "__main__":
    unittest.main()
