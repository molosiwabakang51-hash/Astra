from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"quality"))
import qwen_choice_invariance as inv
from qwen_transfer_choices import cases, LABELS
from qwen_contrastive_curriculum import training_cases

class ChoiceInvarianceTests(unittest.TestCase):
    def test_all_rotations_preserve_semantic_answer(self):
        for original in cases()+training_cases():
            for offset in range(4):
                variant=inv.rotate(original,offset)
                self.assertEqual(variant.question,original.question)
                self.assertEqual(variant.domain,original.domain)
                self.assertEqual(variant.options[variant.correct],original.options[original.correct])
                self.assertEqual(set(variant.options),set(original.options))
                self.assertEqual(variant.correct,(original.correct-offset)%4)
        for bad in (-1,4,True,"1"):
            with self.assertRaises(ValueError):
                inv.rotate(cases()[0],bad)

    def test_matched_schedule_and_balanced_labels(self):
        schedules=inv.training_schedules()
        a,b=schedules["fixed"],schedules["rotated"]
        self.assertEqual(len(a),128)
        self.assertEqual(len(b),128)
        self.assertEqual([c.question for c in a],[c.question for c in b])
        for i in range(32):
            fixed=a[i::32]
            rotated=b[i::32]
            self.assertEqual(len({c.correct for c in fixed}),1)
            self.assertEqual({c.correct for c in rotated},set(range(4)))
            self.assertEqual(len({c.options[c.correct] for c in fixed+rotated}),1)
        for arm in (a,b):
            self.assertEqual([sum(c.correct==i for c in arm) for i in range(4)],[32]*4)
        self.assertFalse({c.question for c in a}&{c.question for c in inv.development()})
        self.assertEqual(len({c.ident for c in inv.development()}),96)

    def rows(self,mode):
        rows=[]
        for c in inv.development():
            offset=int(c.ident.rsplit("__r",1)[1])
            index=c.correct if mode=="oracle" else 0 if mode=="always_a" else (c.correct+1)%4
            rows.append(dict(ident=c.ident,domain=c.domain,predicted=LABELS[index],correct=index==c.correct))
        return rows

    def test_always_a_is_not_semantic_reasoning(self):
        report=inv.summarize(self.rows("always_a"))["all"]
        self.assertEqual(report["questions"],24)
        self.assertEqual(report["rotations"],96)
        self.assertEqual(report["correct_rotations"],24)
        self.assertEqual(report["all_correct_questions"],0)
        self.assertEqual(report["consistent_questions"],0)
        self.assertEqual(report["original_correct"],6)

    def test_consistent_wrong_answers_not_rewarded_as_correct(self):
        oracle=inv.summarize(self.rows("oracle"))
        wrong=inv.summarize(self.rows("wrong"))
        self.assertEqual(oracle["all"]["all_correct_questions"],24)
        self.assertEqual(oracle["all"]["consistent_questions"],24)
        self.assertEqual(wrong["all"]["all_correct_questions"],0)
        self.assertEqual(wrong["all"]["consistent_questions"],24)
        paired=inv.paired_questions(wrong,oracle)
        self.assertEqual(paired["more_correct_rotations"],24)
        self.assertEqual(paired["newly_all_correct"],24)

    def test_incomplete_invalid_and_duplicate_rotation_rows_fail(self):
        rows=self.rows("oracle")
        for bad in (rows[:-1],rows+rows[:1],[dict(rows[0],predicted="E")]+rows[1:],
                    [dict(rows[0],correct=False)]+rows[1:],
                    [dict(rows[0],domain="other")]+rows[1:]):
            with self.assertRaises(ValueError):
                inv.summarize(bad)

if __name__=="__main__":
    unittest.main()
