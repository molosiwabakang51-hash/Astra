"""Revision-bound analysis state and export regression tests without a GUI."""
import importlib.util
import json
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"desktop"))
import astra_analytics as A


class AnalyticsStateTests(unittest.TestCase):
    def setUp(self):
        self.envelope={"provenance":{"analysis":"portfolio","request_sha256":"a"*64,
                                    "scope":"Supplied inputs only."},
                       "result":{"value":"100"}}
        self.markdown="# Report\n\n## Results\n\nValue: 100."

    def test_input_revision_invalidates_report(self):
        state=A.ReportState()
        revision=state.invalidate()
        self.assertTrue(state.publish(revision,self.envelope,self.markdown))
        self.assertEqual((self.envelope,self.markdown),state.report)
        self.assertGreater(state.invalidate(),revision)
        self.assertIsNone(state.report)

    def test_late_result_cannot_replace_current_report(self):
        state=A.ReportState()
        previous=state.invalidate()
        current=state.invalidate()
        self.assertTrue(state.publish(current,self.envelope,self.markdown))
        self.assertFalse(state.publish(previous,{},"Stale"))
        self.assertEqual((self.envelope,self.markdown),state.report)

    def test_failed_new_analysis_does_not_leave_previous_export(self):
        state=A.ReportState()
        state.publish(state.revision,self.envelope,self.markdown)
        state.invalidate()
        self.assertIsNone(state.report)

    def test_markdown_and_json_export_preserve_completed_result(self):
        with patch.object(A,"run_analysis",side_effect=AssertionError("Must not recompute")):
            self.assertEqual(self.envelope,json.loads(A.export_content(self.envelope,self.markdown,"json")))
            rendered=A.export_content(self.envelope,self.markdown,"markdown")
            self.assertIn("Value: 100.",rendered)
            self.assertIn("Canonical request SHA-256: "+"a"*64,rendered)

    def test_html_export_receives_same_result_and_provenance(self):
        calls=[]
        def renderer(md,mode,result):
            calls.append((md,mode,result))
            return "<html>report</html>"
        fake=SimpleNamespace(render_html=renderer)
        with patch.dict(sys.modules,{"astra_professional_report":fake}):
            with patch.object(A,"run_analysis",side_effect=AssertionError("Must not recompute")):
                self.assertEqual("<html>report</html>",A.export_content(self.envelope,self.markdown,"html"))
        self.assertEqual(1,len(calls))
        self.assertEqual("portfolio",calls[0][1])
        self.assertIs(self.envelope["result"],calls[0][2])
        self.assertIn("Canonical request SHA-256",calls[0][0])

    def test_missing_optional_renderer_fails_closed(self):
        with patch.dict(sys.modules,{"astra_professional_report":None}):
            with self.assertRaises(RuntimeError):
                A.export_content(self.envelope,self.markdown,"html")


if __name__=="__main__":
    unittest.main()
