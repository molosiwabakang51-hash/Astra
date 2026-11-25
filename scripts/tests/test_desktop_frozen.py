"""Headless regression checks for the Windows executable pilot."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"desktop"))
import astra_launcher as L
import astra_frozen_build as B
import astra_desktop_distribution as D


class FrozenTests(unittest.TestCase):
    def test_frozen_child_command_uses_same_executable(self):
        child=Mock()
        child.poll.return_value=None
        spawn=Mock(return_value=child)
        with tempfile.TemporaryDirectory() as tmp:
            exe=Path(tmp)/"AstraDesktop.exe"
            controller=L.WorkspaceProcess(executable=str(exe),spawn=spawn)
            with patch.object(sys,"frozen",True,create=True),patch.object(L,"installed",return_value=True):
                controller.launch("assistant")
            self.assertEqual([str(exe),"--workspace","assistant"],spawn.call_args.args[0])
            self.assertFalse(spawn.call_args.kwargs["shell"])
            self.assertEqual(exe.resolve().parent,Path(spawn.call_args.kwargs["cwd"]))

    def test_frozen_module_presence_checked_via_imports(self):
        with patch.object(sys,"frozen",True,create=True),patch.object(L.importlib.util,"find_spec",return_value=None):
            self.assertFalse(L.installed("assistant"))
        with patch.object(sys,"frozen",True,create=True),patch.object(L.importlib.util,"find_spec",return_value=object()):
            self.assertTrue(L.installed("assistant"))

    def test_manifest_detects_changed_added_and_missing_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            exe=folder/"AstraDesktop.exe"
            exe.write_bytes(b"synthetic executable fixture")
            dll=folder/"python311.dll"
            dll.write_bytes(b"synthetic runtime")
            manifest=B.bundle_manifest(folder)
            self.assertTrue(B.verify(folder,json.loads(json.dumps(manifest))))
            self.assertEqual("unsigned",manifest["signature"])
            dll.write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                B.verify(folder,manifest)
            dll.write_bytes(b"synthetic runtime")
            extra=folder/"extra.txt"
            extra.write_text("unexpected",encoding="utf-8")
            with self.assertRaises(ValueError):
                B.verify(folder,manifest)
            extra.unlink()
            dll.unlink()
            with self.assertRaises(ValueError):
                B.verify(folder,manifest)

    def test_model_database_and_size_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            exe=root/"AstraDesktop.exe"
            exe.write_bytes(b"test")
            for name in ("model.gguf","evidence.sqlite","credentials.json"):
                extra=root/name
                extra.write_bytes(b"private fixture")
                with self.assertRaises(ValueError):
                    B.bundle_manifest(root)
                extra.unlink()
            with patch.object(B,"MAX_BYTES",3):
                with self.assertRaises(ValueError):
                    B.bundle_manifest(root)

    def test_hidden_imports_and_source_package_dependency(self):
        imports=B.hidden_imports()
        for name in ("astra_launcher","astra_workbench","astra_frozen_smoke","astra_evidence"):
            self.assertIn(name,imports)
        self.assertIn("astra_frozen_smoke.py",D.CORE)
        self.assertEqual(sorted(set(imports)),imports)
        self.assertNotIn("tkinter.ttk",imports) # collected by the ordinary static dependency graph

    @unittest.skipUnless(importlib.util.find_spec("tkinter") is not None,"Tkinter not installed on source runner")
    def test_source_functional_smoke(self):
        from astra_frozen_smoke import smoke_test
        result=smoke_test()
        self.assertTrue(result["passed"],result["checks"])
        self.assertTrue(result["checks"]["exact_finance"])
        self.assertTrue(result["checks"]["reviewed_evidence"])


    @unittest.skipUnless(importlib.util.find_spec("tkinter") is not None,"Tkinter not installed on source runner")
    def test_complete_release_exercises_all_eight_engines(self):
        from astra_frozen_smoke import smoke_test
        result=smoke_test(require_complete=True)
        self.assertTrue(result["passed"],result["checks"])
        expected=set(D.GROUPS)-{"analytics","professional-report"}
        self.assertEqual(8,len(expected))
        self.assertEqual(expected,{k.removeprefix("example_") for k in result["checks"] if k.startswith("example_")})
        self.assertTrue(result["checks"]["professional_present"])
        self.assertTrue(result["checks"]["professional_renderer"])

    @unittest.skipUnless(importlib.util.find_spec("tkinter") is not None,"Tkinter not installed on source runner")
    def test_complete_release_refuses_missing_renderer(self):
        from astra_frozen_smoke import smoke_test
        original=importlib.util.find_spec
        def without_renderer(name,*args,**kwargs):
            if name=="astra_professional_report":
                return None
            return original(name,*args,**kwargs)
        with patch.object(importlib.util,"find_spec",side_effect=without_renderer):
            result=smoke_test(require_complete=True)
        self.assertFalse(result["passed"])
        self.assertFalse(result["checks"]["professional_present"])

    @unittest.skipUnless(importlib.util.find_spec("tkinter") is not None,"Tkinter not installed on source runner")
    def test_complete_release_refuses_unavailable_engine(self):
        from astra_frozen_smoke import smoke_test
        import astra_analytics
        modes=astra_analytics.available()
        modes["simulation"]=False
        with patch.object(astra_analytics,"available",return_value=modes):
            result=smoke_test(require_complete=True)
        self.assertFalse(result["passed"])
        self.assertFalse(result["checks"]["example_simulation"])


    def test_source_only_complete_smoke_needs_no_gui_imports(self):
        from astra_frozen_smoke import smoke_test
        original=importlib.import_module
        def no_gui(name,*args,**kwargs):
            if name in ("tkinter","astra_desktop_gui","astra_analytics_gui"):
                raise AssertionError("GUI import is not permitted in source-only verification.")
            return original(name,*args,**kwargs)
        with patch.object(importlib,"import_module",side_effect=no_gui):
            receipt=smoke_test(require_complete=True,require_gui=False)
        self.assertTrue(receipt["passed"],receipt["checks"])
        self.assertFalse(receipt["require_gui"])
        self.assertEqual(8,sum(key.startswith("example_") and value for key,value in receipt["checks"].items()))

    def test_frozen_cannot_skip_gui_acceptance(self):
        from astra_frozen_smoke import smoke_test
        with patch.object(sys,"frozen",True,create=True),self.assertRaises(ValueError):
            smoke_test(require_complete=True,require_gui=False)


    def acceptance_receipt(self):
        checks={key:True for key in ("exact_finance","reviewed_evidence","finance_session_import",
                "assistant_gui_import","tkinter_import","analytics_gui_import",
                "analytics_present","professional_present","professional_renderer","portfolio_analytics")}
        checks.update({"example_"+name:True for name in D.GROUPS if name not in ("analytics","professional-report")})
        return {"passed":True,"frozen":True,"require_complete":True,"require_gui":True,"checks":checks}

    def test_acceptance_requires_full_frozen_receipt(self):
        good=self.acceptance_receipt()
        with patch.object(B.subprocess,"run",return_value=Mock(stdout=json.dumps(good))) as run:
            self.assertEqual(good,B.acceptance("AstraDesktop.exe",cwd="."))
            self.assertEqual(["AstraDesktop.exe","--complete-smoke-test"],run.call_args.args[0])
        for key in ("frozen","require_complete","require_gui","passed"):
            bad=self.acceptance_receipt()
            bad[key]=False
            with patch.object(B.subprocess,"run",return_value=Mock(stdout=json.dumps(bad))),self.assertRaises(ValueError):
                B.acceptance("AstraDesktop.exe",cwd=".")
        bad=self.acceptance_receipt()
        del bad["checks"]["example_simulation"]
        with patch.object(B.subprocess,"run",return_value=Mock(stdout=json.dumps(bad))),self.assertRaises(ValueError):
            B.acceptance("AstraDesktop.exe",cwd=".")

    def test_relocated_acceptance_uses_verified_copy_and_clean_environment(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)/"original"
            folder.mkdir()
            (folder/"AstraDesktop.exe").write_bytes(b"fixture")
            manifest=B.bundle_manifest(folder)
            def accept(exe,*,cwd,env):
                self.assertNotEqual(folder,exe.parent)
                self.assertIn(" ",str(exe.parent))
                self.assertTrue(exe.is_file())
                self.assertEqual(cwd,exe.parent.parent)
                self.assertNotIn("PYTHONPATH",env)
                self.assertNotIn("PYTHONHOME",env)
                self.assertNotIn("_PYI_APPLICATION_HOME_DIR",env)
                self.assertNotIn("untrusted-python",env["PATH"])
                self.assertTrue(B.verify(exe.parent,manifest))
                return self.acceptance_receipt()
            with patch.dict(B.os.environ,{"SystemRoot":str(Path(tmp)/"Windows"),
                                          "PATH":"untrusted-python","PYTHONHOME":"untrusted-python",
                                          "PYTHONPATH":"untrusted-python","_PYI_APPLICATION_HOME_DIR":"old"}), \
                 patch.object(B,"acceptance",side_effect=accept):
                receipt=B.relocated_acceptance(folder,manifest)
            self.assertTrue(receipt["passed"])
            self.assertTrue(B.verify(folder,manifest))


if __name__=="__main__":
    unittest.main()
