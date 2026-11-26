"""Regression checks for scoped assistant-excerpt ablation."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "quality"))
import run_qwen_owner_evidence as probe


class OwnerEvidenceTests(unittest.TestCase):
    @staticmethod
    def builders(suite):
        data = {q: required for _, q, _, _, required in suite}
        def build(q, h):
            owner = "".join('Previous user: "' + f + '"\n' for f in data[q])
            return owner + 'Previous Astra: "Unrelated weather."\n' + probe.BOUNDARY + q
        return {"recovery": build}

    def test_preserves_owner_quotes_and_control_identity(self):
        for seed in range(30):
            suite = probe.cases(seed)
            self.assertEqual(suite, probe.cases(seed))
            self.assertEqual(len({row[0] for row in suite}), 8)
            prepared = probe.prepare(seed, self.builders(suite), suite)
            for case, q, _, _, fragments in suite:
                pair = prepared[case]
                if case.startswith("dual_"):
                    self.assertEqual(pair["owner_only"], pair["baseline"].replace(
                        'Previous Astra: "Unrelated weather."\n', ''))
                    self.assertNotIn('Previous Astra: "', pair["owner_only"])
                else:
                    self.assertEqual(pair["owner_only"], pair["baseline"])
                self.assertTrue(pair["owner_only"].endswith(q))
                self.assertTrue(all(f in pair["owner_only"] for f in fragments))

    def test_preflight_rejects_invalid_or_unchanged_dual_fixtures(self):
        suite = probe.cases(19)
        good = self.builders(suite)["recovery"]
        for build in (lambda q,h: probe.BOUNDARY+q,
                      lambda q,h: good(q,h).replace('Previous Astra: "Unrelated weather."\n', ''),
                      lambda q,h: "Actually, use spicy rice.\n"+good(q,h)):
            with self.assertRaises(RuntimeError):
                probe.prepare(19, {"recovery": build}, suite)

    def test_matched_native_scoring_and_no_raw_transcript(self):
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
        self.assertEqual(result["totals"]["owner_only"]["complete"], 8)
        self.assertIsNone(result["official_ai_rating"])
        self.assertNotIn("Actually, make it", str(result))
