"""Distribution and launcher lifecycle tests, no display or model required."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"desktop"))
import astra_launcher as L
import astra_desktop_distribution as D


class DistributionTests(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root=Path(tmp.name)/"source"
        self.root.mkdir()
        self.target=Path(tmp.name)/"release.zip"
        for name in D.CORE:
            self.put(name,b"test fixture")

    def put(self,name,data):
        path=self.root/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(data)
        return path

    def test_reproducible_archive_and_excluded_private_files(self):
        self.put("private.sqlite",b"secret")
        self.put("model.gguf",b"GGUF")
        self.put("examples/user_data.json",b"private")
        manifest=D.build(self.root,self.target)
        self.assertEqual(manifest,D.verify(self.target))
        self.assertEqual([],manifest["groups"])
        self.assertEqual(set(D.CORE),{row["path"] for row in manifest["files"]})
        second=self.target.with_name("second.zip")
        D.build(self.root,second)
        self.assertEqual(self.target.read_bytes(),second.read_bytes())
        with self.assertRaises(FileExistsError):
            D.build(self.root,self.target)

    def test_complete_optional_group_and_partial_rejection(self):
        names=D.GROUPS["portfolio"]
        self.put(names[0],b"fixture")
        with self.assertRaises(ValueError):
            D.build(self.root,self.target)
        for name in names[1:]:
            self.put(name,b"fixture")
        manifest=D.build(self.root,self.target)
        self.assertEqual(["portfolio"],manifest["groups"])
        self.assertEqual(manifest,D.verify(self.target))

    def test_missing_core_and_oversized_file(self):
        name=D.CORE[0]
        (self.root/name).unlink()
        with self.assertRaises(ValueError):
            D.build(self.root,self.target)
        self.put(name,b"x"*(D.MAX_FILE+1))
        with self.assertRaises(ValueError):
            D.build(self.root,self.target)

    def test_tampered_archive_rejected(self):
        D.build(self.root,self.target)
        other=self.target.with_name("tampered.zip")
        with zipfile.ZipFile(self.target) as source,zipfile.ZipFile(other,"w") as dest:
            for info in source.infolist():
                data=source.read(info.filename)
                if info.filename.endswith("/"+D.CORE[0]):
                    data+=b"tampered"
                dest.writestr(info,data)
        with self.assertRaises(ValueError):
            D.verify(other)
        with zipfile.ZipFile(self.target,"a") as z:
            z.writestr("AstraDesktop/../outside",b"bad")
        with self.assertRaises(ValueError):
            D.verify(self.target)

    def test_real_checkout_extracts_and_imports_without_source_tree(self):
        # Tests the actual packaged files, not mock fixtures or working-tree imports.
        manifest=D.build(ROOT/"desktop",self.target)
        self.assertEqual(manifest,D.verify(self.target))
        output=self.target.parent/"extracted"
        with zipfile.ZipFile(self.target) as archive:
            archive.extractall(output)
        folder=output/"AstraDesktop"
        command=[sys.executable,"-E","-s","-X","utf8",str(folder/"astra_launcher.py"),"--diagnostics"]
        run=subprocess.run(command,cwd=folder,check=True,capture_output=True,text=True,timeout=30)
        self.assertTrue(json.loads(run.stdout)["assistant_installed"])
        imports="import astra_workbench; import astra_launcher"
        if "analytics" in manifest["groups"]:
            imports+="; import astra_analytics; assert astra_analytics.available()['portfolio']"
        if "professional-report" in manifest["groups"]:
            imports+="; import astra_report_export"
        subprocess.run([sys.executable,"-E","-s","-X","utf8","-c",imports],cwd=folder,
                       check=True,capture_output=True,text=True,timeout=30)
        # Execute each installed example using the extracted source, not merely
        # import it. All inputs are bundled synthetic fixtures.
        for group in manifest["groups"]:
            if group in ("analytics","professional-report"):
                continue
            _,reporter,example=D.GROUPS[group]
            run=subprocess.run([sys.executable,"-E","-s","-X","utf8",reporter,example,"--format","json"],
                               cwd=folder,check=True,capture_output=True,text=True,
                               encoding="utf-8",timeout=60)
            self.assertIsInstance(json.loads(run.stdout),dict,group)
        if "analytics" in manifest["groups"]:
            run=subprocess.run([sys.executable,"-E","-s","-X","utf8","astra_analytics.py","portfolio",
                                "examples/portfolio_analysis.json","--format","json"],
                               cwd=folder,check=True,capture_output=True,text=True,
                               encoding="utf-8",timeout=60)
            self.assertEqual("0.15",json.loads(run.stdout)["result"]["total_return"]["decimal_approximation"])
        if "professional-report" in manifest["groups"]:
            html=folder/"smoke-report.html"
            subprocess.run([sys.executable,"-E","-s","-X","utf8","astra_report_export.py","portfolio",
                            "examples/portfolio_analysis.json","--output",str(html)],
                           cwd=folder,check=True,capture_output=True,text=True,timeout=60)
            rendered=html.read_text(encoding="utf-8")
            self.assertIn("Closing market-value allocation",rendered)
            self.assertIn("Canonical request SHA-256",rendered)

    def test_launcher_one_child_and_fixed_command(self):
        spawn=Mock()
        child=Mock()
        child.poll.return_value=None
        spawn.return_value=child
        self.put("astra_analytics_gui.py",b"fixture")
        controller=L.WorkspaceProcess(self.root,executable="trusted-python",spawn=spawn)
        controller.launch("assistant")
        self.assertTrue(controller.active())
        self.assertEqual(["trusted-python","-E","-s",str(self.root.resolve()/"astra_desktop_gui.py")],
                         spawn.call_args.args[0])
        self.assertFalse(spawn.call_args.kwargs["shell"])
        self.assertEqual(self.root.resolve(),Path(spawn.call_args.kwargs["cwd"]))
        with self.assertRaises(RuntimeError):
            controller.launch("analytics")
        self.assertIsNone(controller.collect())
        child.poll.return_value=1
        self.assertEqual(("assistant",1),controller.collect())
        controller.launch("analytics")
        with self.assertRaises(ValueError):
            L.installed("../unknown",self.root)

    def test_report_group_requires_registry(self):
        for name in D.GROUPS["professional-report"]:
            self.put(name,b"fixture")
        with self.assertRaises(ValueError):
            D.build(self.root,self.target)


if __name__=="__main__":
    unittest.main()
