"""Unmarked relative correction preflight and paired inference regression tests."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_unmarked_relative as probe


class UnmarkedRelativeTests(unittest.TestCase):
    @staticmethod
    def builders(suite):
        data={(q,tuple(history)):(case,required) for case,q,_,history,required in suite}
        def build(q,h,baseline):
            case,required=data[q,tuple(h)]
            evidence=required[:1] if baseline and not case.startswith("control_") else required
            return "".join('Previous user: "'+f+'"\n' for f in evidence)+probe.BOUNDARY+q
        return {"baseline":lambda q,h:build(q,h,True),
                "recovery":lambda q,h:build(q,h,False)}

    def test_seeded_fixtures_and_arithmetic(self):
        for seed in range(30):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({row[0] for row in suite}),12)
            for domain in ("unmarked_","chain_","control_"):
                indices=[i for i,row in enumerate(suite) if row[0].startswith(domain)]
                self.assertEqual(len(indices),4)
                self.assertEqual(sum(i%2==0 for i in indices),2)
            for case,q,expected,history,required in suite:
                initial=int(required[0].split(" is ")[1].split()[0])
                words=required[1].removeprefix("Actually, ").lower().split()
                magnitude=int(words[3] if words[0] in ("increase","decrease") else words[1])
                value=initial+(magnitude if words[0] in ("increase","add") else -magnitude)
                if case.startswith("chain_"):
                    value+=int(required[2].split(" by ")[1].split()[0])
                self.assertEqual(value,int(expected))
                self.assertTrue(q.endswith(probe.SUFFIX))
            prepared=probe.prepare(seed,self.builders(suite),suite)
            for case,q,_,history,required in suite:
                self.assertTrue(all(f in prepared[case]["recovery"] for f in required))
                self.assertEqual(prepared[case]["baseline"]==prepared[case]["recovery"],
                                 case.startswith("control_"))

    def test_preflight_fails_on_missing_or_non_distinguishing_evidence(self):
        suite=probe.cases(19)
        good=self.builders(suite)
        for builders in (
            {"baseline":good["baseline"],"recovery":good["baseline"]},
            {"baseline":good["recovery"],"recovery":good["recovery"]},
            {"baseline":good["baseline"],"recovery":lambda q,h:probe.BOUNDARY+q},
            {"baseline":good["baseline"],"recovery":lambda q,h:good["recovery"](q,h)+" extra"},
        ):
            with self.assertRaises(RuntimeError):
                probe.prepare(19,builders,suite)

    def test_integer_grading_does_not_extract_prose(self):
        for raw,expected in (("-17",-17),("−17",-17),("+17",17),("17",17),
                             ("17 coins",None),("17 or -17",None),("17.0",None),
                             ("UNKNOWN",None),("1234567890",None),("− 17",None)):
            self.assertEqual(probe.integer(raw),expected)

    def test_matched_calls_and_evidence_accounting(self):
        suite=probe.cases(19)
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(content="wrong",stop_type="eos",truncated=False,
                        tokens_predicted=1,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            report=probe.evaluate(19,infer,self.builders(suite),suite)
            with self.assertRaises(RuntimeError):
                probe.evaluate(19,lambda _: {},self.builders(suite),suite)
        self.assertEqual(len(calls),24)
        for i in range(0,len(calls),2):
            self.assertEqual({k:v for k,v in calls[i].items() if k!="prompt"},
                             {k:v for k,v in calls[i+1].items() if k!="prompt"})
        self.assertEqual(report["matched"]["neither"],12)
        self.assertEqual(sum(row["correction_retained"] for row in report["cases"]),16)
        self.assertEqual(sum(row["all_corrections_retained"] for row in report["cases"]),16)
        self.assertEqual(report["totals"]["recovery"]["complete"],12)
        self.assertIsNone(report["official_ai_rating"])
        self.assertNotIn("harbour repair reserve",str(report))
