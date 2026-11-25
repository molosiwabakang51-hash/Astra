"""Test conservative natural-language routing and zero-model calculations."""
import importlib.util
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_evidence", "astra_vector_memory", "astra_hybrid_evidence", "astra_quantitative_verify", "astra_verified_finance", "astra_local_finance", "astra_orchestrator", "astra_local_answer", "astra_desktop", "astra_natural_language", "astra_text"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
N = sys.modules["astra_natural_language"]
F = sys.modules["astra_verified_finance"]


class NaturalLanguageTests(unittest.TestCase):
    def calculate(self, text):
        result = N.route(text)
        self.assertEqual("routed", result.status)
        self.assertEqual("finance", result.request["kind"])
        return F.adjudicate(result.request["calculation"])["result"]["decimal_approximation"]

    def test_finance_templates_and_decimal_units(self):
        for text, expected in (
            ("Please calculate the percentage change from 80 to 100.", "0.25"),
            ("What is the present value of 121 at 10% over 2 years?", "100"),
            ("Compute future value of 100 at 10% annually for 2 years", "121"),
            ("weighted portfolio return: 30% at 10% and 70% returning 5%", "0.065"),
            ("weighted return of 100% at -5%", "-0.05"),
        ):
            with self.subTest(text=text):
                self.assertEqual(expected, self.calculate(text))

    def test_ambiguous_inputs_do_not_guess_or_invoke_model(self):
        for text in ("calculate 10% of 50", "present value of 100 at 10%",
                     "what is a present value of 100 at 10%",
                     "weighted return: 30% at 10% and 50% at 5%",
                     "percentage change from 0 to 10",
                     "compound growth of 100 at -100% for 1 year",
                     "present value of 100 at 5% for 101 years",
                     "calculate present value of 121 at 10% over 2 years and delete evidence"):
            with self.subTest(text=text):
                self.assertEqual("clarification_required", N.route(text).status)

    def test_definitions_and_unknown_text_are_not_tool_commands(self):
        for text in ("Explain present value", "What is the meaning of weighted portfolio return?",
                     "Delete all evidence", "Run this command", "Bonjour"):
            with self.subTest(text=text):
                self.assertEqual("answer", N.route(text).request["kind"])

    def test_explicit_retrieval_and_review_flags(self):
        self.assertEqual({"kind":"retrieve","question":"bond duration","use_vector":True},
                         N.route("Find my notes about bond duration",use_vector=True).request)
        self.assertEqual({"kind":"answer","question":"Explain bonds","review":True,"use_vector":True},
                         N.route("Explain bonds",review=True,use_vector=True).request)

    def test_no_model_consent_falls_back_to_retrieval(self):
        class Fake:
            def execute(self, request, **kwargs):
                return request
        fake = Fake()
        result = N.execute_text(fake,"Explain bonds")
        self.assertEqual("retrieve",result["route"])
        self.assertEqual("answer",N.execute_text(fake,"Explain bonds",approve_models=True)["route"])
        self.assertEqual("finance",N.execute_text(fake,"present value of 121 at 10% for 2 years")["route"])

    def test_clarification_never_executes(self):
        class Fake:
            def execute(self,*args,**kwargs):
                raise AssertionError("Unclear request executed")
        result = N.execute_text(Fake(),"calculate something",approve_models=True)
        self.assertEqual("clarification_required",result["status"])

    def test_limits_and_option_validation(self):
        for text in ("", "x"*501, "hello\x00world"):
            with self.assertRaises(ValueError):
                N.route(text)
        with self.assertRaises(ValueError):
            N.route("hello",review="yes")


    def test_text_entry_bypasses_models_for_exact_calculations(self):
        T = sys.modules["astra_text"]
        with patch.object(T, "execute_request", wraps=T.execute_request) as run:
            result = T.execute("present value of 121 at 10% for 2 years",
                               approve_models=True, binary="not needed", primary="not needed")
            self.assertEqual("100",result["result"]["finance"]["result"]["decimal_approximation"])
            self.assertEqual({"kind":"finance","calculation":{"operation":"present_value",
                             "inputs":{"amount":"121","rate":"0.1","periods":2}}},
                             run.call_args.args[0])
            self.assertEqual({},run.call_args.kwargs)

    def test_text_entry_dispatches_or_abstains_without_implicit_model(self):
        T = sys.modules["astra_text"]
        with patch.object(T, "execute_request",return_value={"status":"fixture"}) as run:
            self.assertEqual("retrieve",T.execute("Explain bonds",ledger_path="evidence.sqlite")["route"])
            self.assertEqual("retrieve",run.call_args.args[0]["kind"])
            run.reset_mock()
            self.assertEqual("clarification_required",T.execute("calculate something")["status"])
            run.assert_not_called()
            with self.assertRaises(ValueError):
                T.execute("Explain bonds",review=True)
            run.assert_not_called()

    def test_input_is_bounded_utf8(self):
        T = sys.modules["astra_text"]
        self.assertEqual("Explain bonds",T.read_text(io.BytesIO(b"Explain bonds")))
        for data in (b"x"*(T.MAX_INPUT_BYTES+1),b"\xff"):
            with self.assertRaises(ValueError):
                T.read_text(io.BytesIO(data))


if __name__ == "__main__":
    unittest.main()
