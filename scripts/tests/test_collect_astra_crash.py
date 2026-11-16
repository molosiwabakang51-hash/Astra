"""Tests for the read-only, Astra-specific ADB crash report collector."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

source = Path(__file__).resolve().parents[1] / "collect_astra_crash.py"
spec = importlib.util.spec_from_file_location("collect_astra_crash", source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

ASTRA_CRASH = """11-16 12:21:24.683 E/AndroidRuntime( 6056): FATAL EXCEPTION: main
11-16 12:21:24.683 E/AndroidRuntime( 6056): Process: com.astra.agent, PID: 6056
11-16 12:21:24.683 E/AndroidRuntime( 6056): java.lang.ExceptionInInitializerError
11-16 12:21:24.684 E/AndroidRuntime( 6056): at com.astra.agent.AstraOnDeviceBrain.reply
"""
OTHER_CRASH = """11-16 12:18:05.111 E/AndroidRuntime( 1111): FATAL EXCEPTION: main
11-16 12:18:05.111 E/AndroidRuntime( 1111): Process: com.other.application, PID: 1111
11-16 12:18:05.112 E/AndroidRuntime( 1111): java.lang.IllegalStateException: unrelated
"""


class CrashDiagnosticTests(unittest.TestCase):
    def test_filters_unrelated_app_crashes(self):
        excerpts = module.crash_excerpts(OTHER_CRASH + ASTRA_CRASH)
        self.assertEqual(1, len(excerpts))
        self.assertIn("com.astra.agent", excerpts[0])
        self.assertNotIn("unrelated", excerpts[0])

    def test_does_not_invent_crashes_when_none_exists(self):
        report = module.build_report("0.12.1 (versionCode 28)", OTHER_CRASH)
        self.assertIn("No Astra-specific crash found", report)
        self.assertNotIn("unrelated", report)
        self.assertIn("does NOT prove", report)

    def test_never_includes_a_log_from_other_package(self):
        self.assertEqual([], module.crash_excerpts(OTHER_CRASH))

    def test_version_from_dumpsys(self):
        text = "Packages:\n Package [com.astra.agent]\n versionCode=28 minSdk=26\n versionName=0.12.1\n"
        self.assertEqual("0.12.1 (versionCode 28)", module.installed_version(text))
        self.assertEqual("unavailable", module.installed_version(""))

    def test_multiple_devices_require_explicit_serial(self):
        devices = ("List of devices attached\n"
                   "DEVICE_A\tdevice product:one\n"
                   "DEVICE_B\tdevice product:two\n")
        with patch.object(module, "adb_read", return_value=devices):
            with self.assertRaisesRegex(ValueError, "exactly one"):
                module.select_device("adb", None)
            self.assertEqual("DEVICE_B", module.select_device("adb", "DEVICE_B"))
            with self.assertRaisesRegex(ValueError, "not connected"):
                module.select_device("adb", "DEVICE_C")

    def test_offline_device_report_is_bounded(self):
        lines = ASTRA_CRASH + "11-16 12:21:24.685 E/AndroidRuntime: stack\n" * 20000
        self.assertLessEqual(len(module.build_report("0.12.1", lines)), module.MAX_REPORT_BYTES)


if __name__ == "__main__":
    unittest.main()
