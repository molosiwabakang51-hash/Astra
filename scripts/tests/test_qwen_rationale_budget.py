import sys
from pathlib import Path
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
from evaluate_qwen_rationale_checkpoints import budget_pair

class BudgetTests(unittest.TestCase):
    def test_early_eos_and_padding(self):
        self.assertEqual(budget_pair([1,9,9],[1,9,9,9],9,3,5),
                         ([1,9],True,[1,9],True,True))

    def test_recovered_complete_and_capped(self):
        self.assertEqual(budget_pair([1,2,3],[1,2,3,4,9],9,3,5),
                         ([1,2,3],False,[1,2,3,4,9],True,True))
        self.assertTrue(budget_pair([1,2,3],[1,2,3,4,5],9,3,5)[-1])

    def test_divergence_not_assumed_to_be_continuation(self):
        for short,long in (([1,2,3],[1,4,3,5,9]),([1,9],[1,2,3,4,9]),
                           ([1,2,3],[1,9]),([1,2,9],[1,2,3,4,9])):
            self.assertFalse(budget_pair(short,long,9,3,5)[-1])

    def test_invalid_runs(self):
        for short,long in (([],[1,2,3,4,9]),([1,2],[1,2,3,4,9]),
                           ([1,2,3],[1,2,3,4]),([1,9,2],[1,9]),
                           ([1,2,3,4],[1,2,3,4,9]),([1,2,3],[1,2,3,4,5,9])):
            with self.assertRaises(ValueError):
                budget_pair(short,long,9,3,5)
        for a,b in ((0,5),(3,3),(5,3),(True,5)):
            with self.assertRaises(ValueError):
                budget_pair([1,9],[1,9],9,a,b)

if __name__=="__main__":
    unittest.main()
