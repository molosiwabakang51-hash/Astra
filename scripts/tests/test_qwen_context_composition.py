"""Checks for matched contextual composition diagnostic."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_context_composition as probe


class ContextCompositionTests(unittest.TestCase):
    @staticmethod
    def builders(suite):
        required_by_q = {q: required for _, q, _, _, required in suite}
        return {"recovery": lambda q, h: "\n".join(required_by_q[q]) + probe.BOUNDARY + q}

    def test_seeded_suite_and_prompt_isolation(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({r[0] for r in suite}), 8)
            prepared = probe.prepare(seed, self.builders(suite), suite)
            for case, q, _, _, _ in suite:
                pair = prepared[case]
                if case.startswith("dual_"):
                    self.assertEqual(pair["composition"].replace(probe.HINT + "\n\n", ""), pair["baseline"])
                else:
                    self.assertEqual(pair["composition"], pair["baseline"])
                self.assertTrue(pair["composition"].endswith(q))

    def test_preflight_rejects_bad_evidence(self):
        suite = probe.cases(19)
        good = self.builders(suite)["recovery"]
        for build in (lambda q,h: probe.BOUNDARY+q,
                      lambda q,h: "Actually, use spicy rice.\n"+good(q,h),
                      lambda q,h: good(q,h)+" extra"):
            with self.assertRaises(RuntimeError):
                probe.prepare(19, {"recovery": build}, suite)

    def test_matched_scoring_and_native_metadata(self):
        suite = probe.cases(19)
        calls = []
        def infer(payload):
            calls.append(payload)
            return dict(content="wrong", stop_type="eos", truncated=False,
                        tokens_predicted=1, generation_settings=payload)
        with patch.object(probe.native, "system_prompt", return_value="Synthetic system"):
            result = probe.evaluate(19, infer, self.builders(suite), suite)
            with self.assertRaises(RuntimeError):
                probe.evaluate(19, lambda _: {}, self.builders(suite), suite)
        self.assertEqual(len(calls), 16)
        for i in range(0, len(calls), 2):
            self.assertEqual(calls[i]["seed"], calls[i+1]["seed"])
            self.assertEqual(calls[i]["n_predict"], calls[i+1]["n_predict"])
        self.assertEqual(result["matched"]["neither"], 8)
        self.assertEqual(result["totals"]["composition"]["complete"], 8)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("Actually, make it", str(result))
