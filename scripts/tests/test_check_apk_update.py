"""Regression tests for Astra's fail-closed, read-only APK update preflight."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import tempfile

SCRIPT = Path(__file__).resolve().parents[1] / "check_apk_update.py"
spec = importlib.util.spec_from_file_location("check_apk_update", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

CERT_A = "aa" * 32
CERT_B = "bb" * 32


def fake_run_for(cert_installed=CERT_A, cert_new=CERT_A,
                 installed_code=28, new_code=29, package="com.astra.agent"):
    def execute(command, **kwargs):
        if command[-2:] == ["devices", "-l"]:
            return "List of devices attached\nSERIAL123\tdevice product:phone\n"
        if command[1:3] == ["dump", "badging"]:
            return f"package: name='{package}' versionCode='{new_code}' versionName='test'\n"
        if "verify" in command:
            is_installed = "installed-base.apk" in command[-1]
            cert = cert_installed if is_installed else cert_new
            return "Signer #1 certificate SHA-256 digest: " + cert
        if command[-3:] == ["pm", "path", "com.astra.agent"]:
            return "package:/data/app/~~abc/com.astra.agent-xyz/base.apk\n"
        if "pull" in command:
            return "1 file pulled\n"
        if command[-3:] == ["dumpsys", "package", "com.astra.agent"]:
            return f"Packages:\n Package [com.astra.agent]\n versionCode={installed_code} minSdk=33\n"
        raise AssertionError(f"Unexpected command: {command}")
    return execute


class UpdatePreflightTest(unittest.TestCase):
    def test_certificate_fingerprint_requires_64_hex_digits(self):
        self.assertEqual(CERT_A, module.cert_fingerprint(
            "Signer #1 certificate SHA-256 digest: " + CERT_A.upper()))
        with self.assertRaises(ValueError):
            module.cert_fingerprint("Signer #1 certificate SHA-256 digest: broken")

    def test_multiple_signers_never_pass_as_single_signer(self):
        metadata = (
            "Signer #1 certificate SHA-256 digest: " + CERT_A + "\\n" +
            "Signer #2 certificate SHA-256 digest: " + CERT_B + "\\n"
        )
        with self.assertRaisesRegex(ValueError, "exactly one"):
            module.cert_fingerprint(metadata)

    def test_missing_signer_one_or_duplicate_signer_one_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            module.cert_fingerprint("Signer #2 certificate SHA-256 digest: " + CERT_A)
        duplicate = ("Signer #1 certificate SHA-256 digest: " + CERT_A + "\\n" +
                     "Signer #1 certificate SHA-256 digest: " + CERT_A)
        with self.assertRaisesRegex(ValueError, "exactly one"):
            module.cert_fingerprint(duplicate)

    def test_package_identity_is_read_from_package_line(self):
        self.assertEqual(("com.astra.agent", 29), module.apk_identity(
            "package: name='com.astra.agent' versionCode='29' versionName='test'\n"))

    def test_mismatched_certificate_blocks_update(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "new.apk"
            apk.write_bytes(b"test")
            with patch.object(module, "run", side_effect=fake_run_for(cert_installed=CERT_B)):
                allowed, message = module.check(apk, "adb", "apksigner", "aapt")
        self.assertFalse(allowed)
        self.assertIn("certificates differ", message)

    def test_downgrade_blocks_update(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "new.apk"
            apk.write_bytes(b"test")
            with patch.object(module, "run", side_effect=fake_run_for(installed_code=31)):
                allowed, message = module.check(apk, "adb", "apksigner", "aapt")
        self.assertFalse(allowed)
        self.assertIn("older", message)

    def test_matching_certificate_with_new_version_is_compatible(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "new.apk"
            apk.write_bytes(b"test")
            with patch.object(module, "run", side_effect=fake_run_for()):
                allowed, message = module.check(apk, "adb", "apksigner", "aapt")
        self.assertTrue(allowed)
        self.assertIn("has not installed anything", message)

    def test_wrong_package_blocks_update(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "new.apk"
            apk.write_bytes(b"test")
            with patch.object(module, "run", side_effect=fake_run_for(package="com.other.app")):
                allowed, message = module.check(apk, "adb", "apksigner", "aapt")
        self.assertFalse(allowed)
        self.assertIn("not com.astra.agent", message)

    def test_missing_device_refuses_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "new.apk"
            apk.write_bytes(b"test")
            with patch.object(module, "run", return_value="List of devices attached\n"):
                with self.assertRaisesRegex(ValueError, "exactly one"):
                    module.check(apk, "adb", "apksigner", "aapt")


if __name__ == "__main__":
    unittest.main()
