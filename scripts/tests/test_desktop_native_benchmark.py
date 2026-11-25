"""Generated transfer and native recording regression tests; no real models."""
import importlib.util
from fractions import Fraction
from pathlib import Path
import re
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_evidence", "astra_vector_memory",
             "astra_hybrid_evidence", "astra_quantitative_verify", "astra_verified_finance",
             "astra_local_finance", "astra_orchestrator", "astra_local_answer",
             "astra_symbiotic_eval", "astra_generated_validation", "astra_native_benchmark"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
G = sys.modules["astra_generated_validation"]
N = sys.modules["astra_native_benchmark"]
SEED = "1234567890abcdef"*2
IDS = ("a"*64, "b"*64, "c"*64)


class GeneratedValidationTests(unittest.TestCase):
    def test_reproducibility_and_distinct_seed(self):
        first = G.generate(SEED)
        self.assertEqual(first, G.generate(SEED))
        self.assertNotEqual(first, G.generate("0"*32))
        self.assertEqual(12, len(first))
        self.assertEqual(12, len({case.id for case in first}))
        for family in ("simple_interest","weighted_return","conditional_probability","present_value"):
            self.assertEqual(3, sum(case.family == family for case in first))

    def test_exact_keys_and_distinct_options_across_seeds(self):
        for seed in range(40):
            for case in G.generate(f"{seed:032x}"):
                options = dict(re.findall(r"\b([A-D]): (-?\d+(?:/\d+)?)", case.text))
                self.assertEqual(4,len(options))
                self.assertEqual(4,len(set(options.values())))
                stem = case.text.split(" A:")[0]
                nums = list(map(int,re.findall(r"-?\d+",stem)))
                if case.family == "simple_interest":
                    principal, rate, years = nums
                    expected = Fraction(principal*rate*years,100)
                elif case.family == "weighted_return":
                    weight, first, other, second = nums
                    self.assertEqual(100,weight+other)
                    expected = Fraction(weight*first+other*second,100)
                elif case.family == "conditional_probability":
                    red, blue = nums
                    expected = Fraction(red*(red-1),(red+blue)*(red+blue-1))
                else:
                    future, rate = nums
                    expected = Fraction(future,1)/Fraction(100+rate,100)**2
                self.assertEqual(expected,Fraction(options[case.expected]))

    def test_invalid_seed_and_missing_approval_prevent_inference(self):
        for seed in ("", "G"*32, "a"*31, None):
            with self.assertRaises(ValueError):
                G.generate(seed)
        with self.assertRaises(ValueError):
            G.evaluate(SEED,lambda *_:self.fail("No approval"))

    def test_equal_calls_and_correct_scoring(self):
        cases=G.generate(SEED)
        calls=[]
        def runner(role,prompt):
            case=cases[len(calls)//6]
            calls.append((role,prompt))
            self.assertIn(case.text,prompt)
            self.assertNotIn(SEED,prompt)
            return "FINAL: "+case.expected
        result=G.evaluate(SEED,runner,approved=True)
        self.assertEqual(72,len(calls))
        self.assertEqual(72,result["model_calls"])
        self.assertEqual(12,result["self_review_correct"])
        self.assertEqual(12,result["cooperation_correct"])
        self.assertFalse(result["deployment_allowed"])
        self.assertEqual(["primary"]*4+["critic","primary"],[r for r,_ in calls[:6]])
        self.assertNotIn(SEED,str(result))
        for row in result["families"].values():
            self.assertEqual(3,row["cooperation_correct"])

    def test_malformed_answer_aborts_entire_evaluation(self):
        with self.assertRaises(ValueError):
            G.evaluate(SEED,lambda *_:"FINAL: A\nFINAL: B",approved=True)


class NativeBenchmarkTests(unittest.TestCase):
    def test_fingerprint_binds_weights_and_timeout_without_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths=[Path(tmp)/name for name in ("llama-cli","primary.gguf","critic.gguf")]
            for i,path in enumerate(paths):
                path.write_bytes(bytes([i])*1024)
            before=N.fingerprint(*paths,60,G)
            self.assertEqual(before,N.fingerprint(*paths,60,G))
            self.assertNotEqual(before[2],N.fingerprint(*paths,61,G)[2])
            paths[1].write_bytes(b"changed")
            self.assertNotEqual(before[0],N.fingerprint(*paths,60,G)[0])
            self.assertTrue(all(re.fullmatch(r"[0-9a-f]{64}",x) for x in before))

    def test_transfer_seed_generated_after_configuration_is_fingerprinted(self):
        events=[]
        def identity(*args):
            events.append("fingerprint")
            return IDS
        def seed(*args):
            events.append("seed")
            return SEED
        def evaluate(*args,**kwargs):
            events.append("evaluate")
            return {"questions":12}
        with patch.object(N.native,"LocalAnswerRunner"),patch.object(N,"fingerprint",side_effect=identity), \
             patch.object(N.secrets,"token_hex",side_effect=seed),patch.object(G,"evaluate",side_effect=evaluate):
            result=N.run("binary","primary","critic",approved=True)
        self.assertEqual(["fingerprint","seed","evaluate","fingerprint"],events)
        self.assertEqual(SEED,result["reproduction_seed"])
        self.assertEqual(IDS[2],result["identity"]["configuration_sha"])

    def test_direct_development_recording_after_complete_run(self):
        report={"cases":[]}
        evaluator=MagicMock(return_value=report)
        history=MagicMock()
        history.__enter__.return_value.record.return_value={"recorded":True}
        factory=MagicMock(return_value=history)
        modules={"astra_equal_call_eval":SimpleNamespace(evaluate=evaluator),
                 "astra_benchmark_history":SimpleNamespace(BenchmarkHistory=factory)}
        with patch.object(N.importlib,"import_module",side_effect=modules.__getitem__), \
             patch.object(N.native,"LocalAnswerRunner"),patch.object(N,"fingerprint",return_value=IDS):
            result=N.run("binary","primary","critic",suite="development",approved=True,
                         database="benchmark.sqlite",approve_storage=True)
        self.assertTrue(result["history_receipt"]["recorded"])
        factory.assert_called_once_with("benchmark.sqlite")
        history.__enter__.return_value.record.assert_called_once_with(report,*IDS,approved=True)

    def test_changed_configuration_and_failed_run_never_open_history(self):
        factory=MagicMock()
        evaluator=MagicMock(return_value={"cases":[]})
        modules={"astra_equal_call_eval":SimpleNamespace(evaluate=evaluator),
                 "astra_benchmark_history":SimpleNamespace(BenchmarkHistory=factory)}
        with patch.object(N.importlib,"import_module",side_effect=modules.__getitem__), \
             patch.object(N.native,"LocalAnswerRunner"),patch.object(N,"fingerprint",side_effect=[IDS,("d"*64,*IDS[1:])]):
            with self.assertRaises(RuntimeError):
                N.run("binary","primary","critic",suite="development",approved=True,
                      database="benchmark.sqlite",approve_storage=True)
        factory.assert_not_called()
        evaluator.side_effect=ValueError("incomplete")
        with patch.object(N.importlib,"import_module",side_effect=modules.__getitem__), \
             patch.object(N.native,"LocalAnswerRunner"),patch.object(N,"fingerprint",return_value=IDS):
            with self.assertRaises(ValueError):
                N.run("binary","primary","critic",suite="development",approved=True,
                      database="benchmark.sqlite",approve_storage=True)
        factory.assert_not_called()

    def test_approval_and_suite_boundaries(self):
        with patch.object(N.native,"LocalAnswerRunner") as runner:
            for options in ({},{"approved":True,"database":"db"},
                            {"approved":True,"approve_storage":True},
                            {"approved":True,"suite":"development","seed":SEED},
                            {"approved":True,"timeout":0}):
                with self.assertRaises(ValueError):
                    N.run("binary","primary","critic",**options)
            runner.assert_not_called()


if __name__=="__main__":
    unittest.main()
