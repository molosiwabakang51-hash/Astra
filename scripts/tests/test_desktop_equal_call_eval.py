"""Equal-call comparison harness tests; no actual model performance claimed."""
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
for name in ("astra_resource_plan", "astra_symbiotic_eval", "astra_equal_call_eval"):
    spec = importlib.util.spec_from_file_location(name, ROOT / "desktop" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
E = sys.modules["astra_equal_call_eval"]


class EqualCallTests(unittest.TestCase):
    def test_equal_calls_and_order_alternation(self):
        calls = []
        def runner(role,prompt):
            calls.append((role,prompt))
            return "FINAL: A"
        result = E.evaluate(runner,approved=True)
        self.assertEqual(12,result["questions"])
        self.assertEqual(36,result["self_review_calls"])
        self.assertEqual(36,result["cooperation_calls"])
        self.assertEqual(72,len(calls))
        self.assertEqual(["primary"]*4+["critic","primary"],[r for r,_ in calls[:6]])
        self.assertEqual(["primary","critic","primary"]+["primary"]*3,[r for r,_ in calls[6:12]])
        self.assertEqual(3,result["self_review_correct"])
        self.assertEqual(3,result["cooperation_correct"])
        self.assertEqual(0,result["cooperation_wins"])
        self.assertEqual(0,result["cooperation_losses"])

    def test_exact_scoring_and_paired_wins_losses(self):
        calls = []
        def runner(role,prompt):
            index = len(calls)
            calls.append(role)
            item = E.ITEMS[index//6]
            phase = index%6
            cooperative = (phase>=3) if (index//6)%2==0 else (phase<3)
            correct = item.expected
            wrong = next(letter for letter in "ABCD" if letter!=correct)
            return "FINAL: "+(correct if cooperative else wrong)
        result = E.evaluate(runner,approved=True)
        self.assertEqual(12,result["cooperation_correct"])
        self.assertEqual(0,result["self_review_correct"])
        self.assertEqual(12,result["cooperation_wins"])

    def test_consent_and_malformed_output_fail_closed(self):
        with self.assertRaises(ValueError):
            E.evaluate(lambda *_:self.fail("Unapproved inference"))
        for bad in ("A","FINAL: E","FINAL: A\nFINAL: B","<think>hidden</think>\nFINAL: A"):
            with self.assertRaises(ValueError):
                E.evaluate(lambda *_:bad,approved=True)

    def test_adjudication_receives_only_candidate_letters(self):
        calls=[]
        def runner(role,prompt):
            calls.append(prompt)
            return "FINAL: B"
        E.evaluate(runner,approved=True)
        for i,prompt in enumerate(calls):
            if i%3==2:
                self.assertIn("Candidate letters: B, B",prompt)
            else:
                self.assertNotIn("Candidate letters:",prompt)


if __name__=="__main__":
    unittest.main()
