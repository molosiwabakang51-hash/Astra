"""Check scoped uncertainty diagnostic, without access to a model."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_scoped_uncertainty as probe


class ScopedUncertaintyTests(unittest.TestCase):
    @staticmethod
    def builder(suite):
        data = {}
        for item in suite:
            history = tuple(item["history"])
            data[item["question"],history] = item["required"]
            for j,q in enumerate(item["subquestions"]):
                data[q,history] = (item["required"][j],)
        return lambda q,h: "".join('Previous user: "'+f+'"\n' for f in data[q,tuple(h)]) + probe.BOUNDARY + q

    def test_seeded_balanced_cases_and_explicit_unknown_source(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite,probe.cases(seed))
            self.assertEqual(len({x["case"] for x in suite}),12)
            for domain in ("dual_","missing_","single_"):
                rows = [(i,x) for i,x in enumerate(suite) if x["case"].startswith(domain)]
                self.assertEqual(len(rows),4)
                self.assertEqual(sum(i%2==0 for i,x in rows),2)
            for item in suite:
                self.assertEqual(item["expected"]=="UNKNOWN",item["case"].startswith("missing_"))
                pair = probe.prepare(self.builder(suite),item)
                for j,sub in enumerate(item["subquestions"]):
                    self.assertTrue(pair["scoped"][j].endswith(sub))
                    self.assertIn(item["required"][j],pair["scoped"][j])
                    if len(item["required"])==2:
                        self.assertNotIn(item["required"][1-j],pair["scoped"][j])
                if item["operation"]=="identity":
                    self.assertEqual(pair["shared"],pair["scoped"])

    def test_explicit_abstention_is_not_invalid_output(self):
        self.assertEqual(probe.scalar("UNKNOWN"),"UNKNOWN")
        self.assertEqual(probe.scalar("unknown"),"UNKNOWN")
        self.assertEqual(probe.scalar("12"),12)
        for invalid in ("UNKNOWN because","", "Answer: UNKNOWN","3/4","1.5","UNKNOWN or 2"):
            self.assertIsNone(probe.scalar(invalid))

    def test_unknown_and_wrong_numeric_are_scored_separately(self):
        item = next(x for x in probe.cases(19) if x["case"]=="missing_0")
        # A one-case suite executes shared first, then scoped.
        replies = iter([str(item["operands"][0]),"123",
                        str(item["operands"][0]),"UNKNOWN"])
        def infer(payload):
            return dict(content=next(replies),stop_type="eos",truncated=False,
                        tokens_predicted=1,generation_settings=payload)
        # Preflight requires a distinguishing complete dual fixture, so
        # include one such item before the missing-value item.
        dual = probe.cases(19)[0]
        a,b = dual["operands"]
        replies = iter([str(a),str(b),str(a),str(b),
                        str(item["operands"][0]),"UNKNOWN",
                        str(item["operands"][0]),"123"])
        suite = [dual,item]
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            result = probe.evaluate(19,infer,self.builder(suite),suite)
        missing = result["by_domain"]["missing"]
        self.assertEqual(missing["shared"]["unsupported_numeric"],1)
        self.assertEqual(missing["shared"]["correct"],0)
        self.assertEqual(missing["scoped"]["explicit_unknown"],1)
        self.assertEqual(missing["scoped"]["correct"],1)

    def test_all_attempts_count_and_truncation_receives_no_credit(self):
        suite = probe.cases(19)
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(content="UNKNOWN",stop_type="limit",truncated=True,
                        tokens_predicted=probe.CAP,generation_settings=payload)
        with patch.object(probe.native,"system_prompt",return_value="Synthetic system"):
            result = probe.evaluate(19,infer,self.builder(suite),suite)
        self.assertEqual(len(calls),40)
        for arm in ("shared","scoped"):
            self.assertEqual(result["totals"][arm]["calls"],20)
            self.assertEqual(result["totals"][arm]["correct"],0)
            self.assertEqual(result["totals"][arm]["explicit_unknown"],0)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("Actually, I do not know",str(result))
