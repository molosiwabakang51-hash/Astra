"""Regression tests for zero-network, read-only USB GGUF backup."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

SCRIPT = Path(__file__).resolve().parents[1] / "backup_installed_qwen.py"
spec = importlib.util.spec_from_file_location("backup_installed_qwen", SCRIPT)
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)

MODEL_FIXTURE = b"GGUF" + b"simulated tiny fixture" * 8
SHA = hashlib.sha256(MODEL_FIXTURE).hexdigest()


class UsbBackupTests(unittest.TestCase):
    def test_verified_stream_publishes_exact_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / backup.MODEL_NAME
            size = backup.save_verified_stream(
                io.BytesIO(MODEL_FIXTURE), dest,
                SHA, minimum_bytes=16, maximum_bytes=500
            )
            self.assertEqual(len(MODEL_FIXTURE), size)
            self.assertEqual(MODEL_FIXTURE, dest.read_bytes())
            self.assertEqual([], list(Path(tmp).glob("*.partial")))

    def test_wrong_checksum_never_publishes_or_leaves_partial(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "backup.gguf"
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                backup.save_verified_stream(
                    io.BytesIO(MODEL_FIXTURE), dest,
                    "0" * 64, minimum_bytes=16, maximum_bytes=500
                )
            self.assertFalse(dest.exists())
            self.assertEqual([], list(Path(tmp).glob("*.partial")))

    def test_truncated_stream_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "backup.gguf"
            with self.assertRaisesRegex(ValueError, "Incomplete"):
                backup.save_verified_stream(
                    io.BytesIO(MODEL_FIXTURE[:4]), dest,
                    SHA, minimum_bytes=16, maximum_bytes=500
                )
            self.assertFalse(dest.exists())

    def test_oversized_stream_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "backup.gguf"
            with self.assertRaisesRegex(ValueError, "oversized"):
                backup.save_verified_stream(
                    io.BytesIO(MODEL_FIXTURE), dest,
                    SHA, minimum_bytes=1, maximum_bytes=5
                )
            self.assertFalse(dest.exists())

    def test_existing_backup_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "backup.gguf"
            dest.write_bytes(b"existing personal backup")
            with self.assertRaises(FileExistsError):
                backup.save_verified_stream(
                    io.BytesIO(MODEL_FIXTURE), dest,
                    SHA, minimum_bytes=1, maximum_bytes=500
                )
            self.assertEqual(b"existing personal backup", dest.read_bytes())

    def test_backup_never_overwrites_destination_created_during_verification(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "backup.gguf"

            def competing_writer():
                dest.write_bytes(b"another verified backup")

            with self.assertRaises(FileExistsError):
                backup.save_verified_stream(
                    io.BytesIO(MODEL_FIXTURE), dest,
                    SHA, minimum_bytes=1, maximum_bytes=500,
                    completed=competing_writer
                )
            self.assertEqual(b"another verified backup", dest.read_bytes())
            self.assertEqual([], list(Path(tmp).glob("*.partial")))

    def test_publish_uses_no_clobber_operation(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "backup.gguf"
            with patch.object(backup.os, "link", wraps=backup.os.link) as link:
                backup.save_verified_stream(
                    io.BytesIO(MODEL_FIXTURE), dest,
                    SHA, minimum_bytes=1, maximum_bytes=500
                )
                link.assert_called_once()
            self.assertEqual(MODEL_FIXTURE, dest.read_bytes())

    def test_adb_stream_must_complete_successfully(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "backup.gguf"
            with self.assertRaisesRegex(RuntimeError, "ADB stopped"):
                backup.save_verified_stream(
                    io.BytesIO(MODEL_FIXTURE), dest,
                    SHA, minimum_bytes=1, maximum_bytes=500,
                    completed=lambda: (_ for _ in ()).throw(
                        RuntimeError("ADB stopped before completion"))
                )
            self.assertFalse(dest.exists())

    def test_phone_selection_requires_one_authorized_device(self):
        devices = ("List of devices attached\n"
                   "SERIAL1\tdevice product:one\n"
                   "SERIAL2\tdevice product:two\n")
        with patch.object(backup.subprocess, "run",
                          return_value=SimpleNamespace(returncode=0, stdout=devices)):
            with self.assertRaisesRegex(RuntimeError, "exactly one"):
                backup.active_device("adb", None)
            self.assertEqual("SERIAL1", backup.active_device("adb", "SERIAL1"))
            with self.assertRaisesRegex(RuntimeError, "not authorised"):
                backup.active_device("adb", "BAD")

    def test_sha_matches_model_import_validation(self):
        src = (Path(__file__).resolve().parents[2] /
               "android/app/src/main/java/com/astra/agent/AstraModelImport.kt")
        self.assertIn(backup.SHA256, src.read_text(encoding="utf-8"))
        self.assertIn(backup.MODEL_NAME, src.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
