"""Development diagnostic contract tests, not model evidence."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import run_qwen_quantity_dependencies as probe


class DependencyDiagnosticTests(unittest.TestCase):
    @staticmethod
    def builders(suite):
        data = {(q,tuple(h)):(case,required) for case,q,_,h,required in suite}
        def build(q,h,baseline):
            case, required = data[q,tuple(h)]
            if baseline and not case.startswith("control_"):
                required = required[:1] + required[-1:] if case.startswith("chain_") else required[:2]
            return "".join('Previous user: "' + fragment + '"\n' for fragment in required) + probe.BOUNDARY + q
        return {"baseline":lambda q,h:build(q,h,True),"recovery":lambda q,h:build(q,h,False)}

    def test_seeded_suite_and_oracles(self):
        for seed in range(20):
            suite=probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}),12)
            self.assertEqual(sum(r[2]=="UNKNOWN" for r in suite),2)
            for name,q,expected,h,evidence in suite:
                value=int(evidence[0].split(" is ")[1].split()[0])
                for step in evidence[1:]:
                    if "unknown" in step or "no longer know" in step:
                        value=None
                    elif step.startswith("Set it to "):
                        value=int(step.split()[3])
                    elif step.startswith("Increase "):
                        value+=int(step.split()[3])
                    elif step.startswith("Decrease "):
                        value-=int(step.split()[3])
                    elif step.startswith("Add "):
                        value+=int(step.split()[1])
                    else:
                        self.fail(step)
                self.assertEqual(expected,"UNKNOWN" if value is None else str(value))
            probe.prepare(seed,self.builders(suite),suite)

    def test_preflight_rejects_missing_or_reordered_evidence_and_changed_controls(self):
        suite=probe.cases(19)
        good=self.builders(suite)
        with self.assertRaises(RuntimeError):
            probe.prepare(19,{"baseline":good["recovery"],"recovery":good["recovery"]},suite)
        def reverse(q,h):
            full=good["recovery"](q,h)
            head=full.split(probe.BOUNDARY)[0].splitlines(keepends=True)
            return "".join(reversed(head))+probe.BOUNDARY+q
        with self.assertRaises(RuntimeError):
            probe.prepare(19,{"baseline":good["baseline"],"recovery":reverse},suite)
        with self.assertRaises(RuntimeError):
            probe.prepare(19,{"baseline":good["baseline"],"recovery":good["baseline"]},suite)

    def test_strict_answer_contract(self):
        self.assertTrue(probe.answer_matches("UNKNOWN","UNKNOWN"))
        self.assertFalse(probe.answer_matches("The amount is UNKNOWN.","UNKNOWN"))
        self.assertFalse(probe.answer_matches("unknown","UNKNOWN"))
        self.assertTrue(probe.answer_matches("−5","-5"))
        self.assertFalse(probe.answer_matches("5 coins","5"))

    def test_matched_calls_and_unknown_accounting(self):
        suite=probe.cases(19)
        calls=[]
        def infer(payload):
            calls.append(payload)
            return dict(content="UNKNOWN",stop_type="eos",truncated=False,
                        tokens_predicted=1,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            report=probe.evaluate(19,infer,self.builders(suite),suite)
        self.assertEqual(len(calls),24)
        for a,b in zip(calls[::2],calls[1::2]):
            self.assertEqual({k:v for k,v in a.items() if k!="prompt"},
                             {k:v for k,v in b.items() if k!="prompt"})
        self.assertEqual(report["matched"]["both_correct"],2)
        self.assertEqual(report["matched"]["neither"],10)
        self.assertIsNone(report["official_ai_rating"])


if __name__ == "__main__":
    unittest.main()
