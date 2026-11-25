"""Contextual finance scenarios: exact arithmetic, bounded changes and reset."""
import importlib.util
import io
import json
from fractions import Fraction
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_evidence", "astra_vector_memory",
             "astra_hybrid_evidence", "astra_quantitative_verify", "astra_verified_finance",
             "astra_local_finance", "astra_orchestrator", "astra_local_answer", "astra_desktop",
             "astra_natural_language", "astra_text", "astra_finance_session", "astra_session"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
S = sys.modules["astra_finance_session"]
CLI = sys.modules["astra_session"]
T = sys.modules["astra_text"]


def value(snapshot):
    return S.fraction_from_result(snapshot["finance"])


class FinanceSessionTests(unittest.TestCase):
    def setUp(self):
        self.session = S.FinanceSession()

    def start(self):
        result = self.session.execute("Calculate the present value of P10,000 at 8% over five years.")
        self.assertEqual("calculated", result["status"])
        self.assertEqual("BWP", result["scenario"]["currency"])
        self.assertEqual({"amount":"10000","rate":"0.08","periods":5},
                         result["scenario"]["calculation"]["inputs"])
        return result["scenario"]

    def test_pula_scenario_and_rate_followup(self):
        initial = self.start()
        result = self.session.execute("What happens if interest rates increase to 10%?")
        self.assertEqual("calculated",result["status"])
        self.assertEqual("rate",result["changed_field"])
        updated = result["scenario"]
        self.assertEqual("BWP",updated["currency"])
        self.assertEqual({"amount":"10000","rate":"0.1","periods":5},
                         updated["calculation"]["inputs"])
        self.assertEqual(Fraction(10000)/(Fraction(11,10)**5),value(updated))
        comparison = self.session.execute("Compare both results and explain the implications.")
        self.assertEqual("compared",comparison["status"])
        self.assertEqual("lower",comparison["direction"])
        self.assertIn("higher discount rate lowers present value",comparison["explanation"])
        delta = comparison["absolute_change"]
        self.assertEqual(value(updated)-value(initial),Fraction(int(delta["numerator"]),int(delta["denominator"])))

    def test_previous_and_original_scenarios_are_distinct(self):
        initial = self.start()
        changed = self.session.execute("What if 10%?")["scenario"]
        final = self.session.execute("What about six years?")["scenario"]
        self.assertEqual(6,final["calculation"]["inputs"]["periods"])
        self.assertEqual("0.1",final["calculation"]["inputs"]["rate"])
        self.assertEqual(changed,self.session.execute("compare with previous")["before"])
        self.assertEqual(initial,self.session.execute("compare with original")["before"])

    def test_invalid_changes_are_transactional(self):
        initial = self.start()
        for text in ("what if -100%?", "what if 101 years?", "what if rates go to 10% and 6 years?"):
            self.assertEqual("clarification_required",self.session.execute(text)["status"])
        updated = self.session.execute("change the amount to BWP 12,000")["scenario"]
        self.assertEqual("12000",updated["calculation"]["inputs"]["amount"])
        self.assertEqual(initial,self.session.execute("compare both results")["before"])

    def test_no_implicit_anchor_or_cross_operation_reuse(self):
        self.assertEqual("clarification_required",self.session.execute("what about 10%?")["status"])
        self.session.execute("percentage change from 80 to 100")
        self.assertEqual("clarification_required",self.session.execute("what if 10%")["status"])
        self.start()
        self.session.execute("future value of 100 at 10% for two years")
        self.assertEqual("clarification_required",self.session.execute("compare both results")["status"])

    def test_returned_snapshots_cannot_mutate_session(self):
        first = self.start()
        first["calculation"]["inputs"]["amount"] = "999"
        second = self.session.execute("what if 10%")["scenario"]
        self.assertEqual("10000",second["calculation"]["inputs"]["amount"])

    def test_topic_switch_and_explicit_reset_clear_state(self):
        called = []
        session = S.FinanceSession(lambda text: called.append(text) or {"fixture":True})
        session.execute("present value of 121 at 10% for 2 years")
        self.assertEqual("delegated",session.execute("Explain government bonds")["status"])
        self.assertEqual(["Explain government bonds"],called)
        self.assertEqual("clarification_required",session.execute("what if 8%")["status"])
        self.start()
        self.assertEqual("finance_context_cleared",self.session.execute("reset scenario")["status"])
        self.assertEqual("clarification_required",self.session.execute("compare both results")["status"])

    def test_currency_cannot_be_assumed_or_changed_mid_scenario(self):
        self.session.execute("present value of 121 at 10% for 2 years")
        self.assertEqual("clarification_required",self.session.execute("set amount to P200")["status"])
        result = self.session.execute("set amount to 200")
        self.assertIsNone(result["scenario"]["currency"])
        self.assertEqual("BWP",T.execute("present value of P121 at 10% for two years")["currency"])
        self.assertEqual("clarification_required",self.session.execute("present value of P10,00 at 8% for five years")["status"])

    def test_interactive_session_preserves_context_until_exit(self):
        source = io.BytesIO(b"present value of 121 at 10% for 2 years\nwhat if 0%\ncompare both results\nexit\n")
        output, errors = io.StringIO(), io.StringIO()
        self.assertEqual(0,CLI.interact(self.session,source,output,errors))
        rows = [json.loads(line) for line in output.getvalue().splitlines()]
        self.assertEqual(["calculated","calculated","compared"],[row["status"] for row in rows])
        self.assertEqual("higher",rows[-1]["direction"])
        self.assertEqual("",errors.getvalue())
        self.assertEqual("clarification_required",self.session.execute("what if 8%")["status"])

    def test_oversized_line_ends_session_without_processing_remainder(self):
        self.start()
        output, errors = io.StringIO(), io.StringIO()
        self.assertEqual(1,CLI.interact(self.session,io.BytesIO(b"x"*4097+b"\n"),output,errors))
        self.assertEqual("",output.getvalue())
        self.assertIn("session ended",errors.getvalue())
        self.assertEqual("clarification_required",self.session.execute("what if 8%")["status"])


if __name__ == "__main__":
    unittest.main()
