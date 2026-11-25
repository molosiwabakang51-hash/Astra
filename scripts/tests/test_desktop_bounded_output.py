"""Model-free tests of real bounded subprocess capture."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/"desktop"))
import astra_native_subprocess as N


class BoundedOutputTests(unittest.TestCase):
    def run_child(self, code, limit=128, timeout=5):
        with tempfile.TemporaryDirectory() as folder:
            return N.run_bounded([sys.executable,"-E","-s","-c",code],
                                 timeout=timeout,env=dict(os.environ),
                                 max_stdout_bytes=limit,cwd=folder)

    def test_exact_limit(self):
        result=self.run_child("import sys; sys.stdout.buffer.write(b'x'*128)")
        self.assertEqual(0,result.returncode)
        self.assertEqual(b"x"*128,result.stdout)

    def test_overflow(self):
        with self.assertRaisesRegex(RuntimeError,"exceeds limit"):
            self.run_child("import sys; sys.stdout.buffer.write(b'x'*129)")

    def test_continuous_output(self):
        with self.assertRaisesRegex(RuntimeError,"exceeds limit"):
            self.run_child("import os\nwhile True: os.write(1,b'x'*4096)")

    def test_silent_timeout(self):
        with self.assertRaises(subprocess.TimeoutExpired):
            self.run_child("import time; time.sleep(30)",timeout=1)

    def test_nonzero_exit(self):
        result=self.run_child("import sys; print('failed'); sys.exit(7)")
        self.assertEqual(7,result.returncode)
        self.assertEqual(b"failed",result.stdout.strip())

    def test_invalid_limits(self):
        for limit in (0,True,1048577):
            with self.subTest(limit=limit),self.assertRaises(ValueError):
                self.run_child("raise AssertionError()",limit=limit)


if __name__=="__main__":
    unittest.main()
