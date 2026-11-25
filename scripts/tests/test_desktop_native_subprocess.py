"""Exercise the external-process isolation boundary without native model files."""
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/"desktop"))
import astra_native_subprocess as N


class NativeProcessTests(unittest.TestCase):
    def test_frozen_environment_removes_only_bundle_paths(self):
        env={"Path":r'C:\Tools;C:\Astra\_internal;"C:\Astra\_internal\lib";C:\Astra\_internal2;D:\Tools',
             "_PYI_APPLICATION_HOME_DIR":"private","PYINSTALLER_RESET_ENVIRONMENT":"1",
             "SYSTEMROOT":r"C:\Windows"}
        result=N.clean_environment(env,r"C:\Astra\_internal")
        self.assertEqual(r"C:\Tools;C:\Astra\_internal2;D:\Tools",result["Path"])
        self.assertEqual(r"C:\Windows",result["SYSTEMROOT"])
        self.assertNotIn("_PYI_APPLICATION_HOME_DIR",result)
        self.assertNotIn("PYINSTALLER_RESET_ENVIRONMENT",result)
        self.assertIn("_PYI_APPLICATION_HOME_DIR",env)

    def test_restore_dll_directory_immediately_after_spawn(self):
        events=[]
        def setter(value):
            events.append(("directory",value))
            return True
        child=Mock()
        def spawn(command,**kwargs):
            events.append(("spawn",command))
            self.assertEqual({"PATH":r"C:\Tools"},kwargs["env"])
            self.assertFalse(kwargs["shell"])
            return child
        root=r"C:\Astra\_internal"
        command=[r"C:\Tools\llama-cli.exe"]
        self.assertIs(child,N.spawn_windows(command,bundle=root,
                      env={"PATH":root+r";C:\Tools"},popen=spawn,set_directory=setter,shell=False))
        self.assertEqual([("directory",None),("spawn",command),("directory",root)],events)

    def test_spawn_failure_still_restores_bundle_directory(self):
        setter=Mock(return_value=True)
        with self.assertRaises(OSError):
            N.spawn_windows(["llama-cli.exe"],bundle=r"C:\Astra\_internal",
                            env={},popen=Mock(side_effect=OSError("synthetic")),set_directory=setter)
        self.assertEqual([None,r"C:\Astra\_internal"],[c.args[0] for c in setter.call_args_list])

    def test_restore_failure_terminates_child(self):
        child=Mock()
        with self.assertRaises(OSError):
            N.spawn_windows(["llama-cli.exe"],bundle=r"C:\Astra\_internal",
                            env={},popen=Mock(return_value=child),set_directory=Mock(side_effect=[True,False]))
        child.kill.assert_called_once()
        child.wait.assert_called_once()

    def test_reset_failure_never_starts_child(self):
        spawn=Mock()
        with self.assertRaises(OSError):
            N.spawn_windows(["llama-cli.exe"],bundle=r"C:\Astra\_internal",
                            env={},popen=spawn,set_directory=Mock(return_value=False))
        spawn.assert_not_called()

    def test_source_execution_uses_original_runner(self):
        runner=Mock(return_value=subprocess.CompletedProcess(["llama-cli"],0,b"answer",None))
        with patch.object(sys,"frozen",False,create=True):
            result=N.run_external(["llama-cli"],timeout=30,env={"X":"1"},
                                  runner=runner,check=False,shell=False)
        self.assertEqual(b"answer",result.stdout)
        runner.assert_called_once_with(["llama-cli"],timeout=30,env={"X":"1"},check=False,shell=False)


if __name__=="__main__":
    unittest.main()
