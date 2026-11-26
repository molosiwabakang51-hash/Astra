"""Tests of matched retrieval invariance and format distraction scoring."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_format_retrieval as probe


class FormatRetrievalTests(unittest.TestCase):
    @staticmethod
    def builders(suite):
        data={q:required for _,q,_,_,required in suite}
        def build(q,h,baseline):
            head="".join('Previous user: "'+f+'"\n' for f in data[q])
            if baseline and q.endswith(probe.SUFFIX):
                head+='Previous user: "'+probe.DISTRACTOR+'"\n'
                head+='Previous user: "'+probe.DISTRACTOR_CORRECTION+'"\n'
            return head+probe.BOUNDARY+q
        return {"baseline":lambda q,h:build(q,h,True),
                "recovery":lambda q,h:build(q,h,False)}

    def test_seeded_fixture_balance_and_invariance(self):
        for seed in range(30):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({row[0] for row in suite}),8)
            for domain in ("formatted_","control_"):
                indices=[i for i,row in enumerate(suite) if row[0].startswith(domain)]
                self.assertEqual(len(indices),4)
                self.assertEqual(sum(i%2==0 for i in indices),2)
            pair=probe.prepare(seed,self.builders(suite),suite)
            for case,q,_,_,required in suite:
                self.assertTrue(pair[case]["recovery"].endswith(q))
                self.assertTrue(all(f in pair[case]["recovery"] for f in required))
                self.assertNotIn(probe.DISTRACTOR,pair[case]["recovery"])
                self.assertEqual(pair[case]["baseline"]==pair[case]["recovery"],case.startswith("control_"))

    def test_preflight_fails_closed_on_uninformative_or_bad_recovery(self):
        suite=probe.cases(19)
        good=self.builders(suite)
        for bad in (
            {"baseline":good["recovery"],"recovery":good["recovery"]},
            {"baseline":good["baseline"],"recovery":good["baseline"]},
            {"baseline":good["baseline"],"recovery":lambda q,h:probe.BOUNDARY+q},
            {"baseline":good["baseline"],"recovery":lambda q,h:good["recovery"](q,h)+" extra"},
        ):
            with self.assertRaises(RuntimeError):
                probe.prepare(19,bad,suite)

    def test_matched_scoring_and_distractor_counts(self):
        suite=probe.cases(19)
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(content="wrong",stop_type="eos",truncated=False,
                        tokens_predicted=1,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            result=probe.evaluate(19,infer,self.builders(suite),suite)
            with self.assertRaises(RuntimeError):
                probe.evaluate(19,lambda _: {},self.builders(suite),suite)
        self.assertEqual(len(calls),16)
        for i in range(0,len(calls),2):
            self.assertEqual({k:v for k,v in calls[i].items() if k!="prompt"},
                             {k:v for k,v in calls[i+1].items() if k!="prompt"})
        self.assertEqual(result["matched"]["neither"],8)
        self.assertEqual(sum(row["distractor_retained"] for row in result["cases"]),4)
        self.assertEqual(sum(row["distractor_correction_retained"] for row in result["cases"]),4)
        self.assertEqual(result["totals"]["recovery"]["complete"],8)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn(probe.DISTRACTOR,str(result))
