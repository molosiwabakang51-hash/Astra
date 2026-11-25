"""Synthetic records test the gate, never Astra's intelligence."""
import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "quality/evaluate_generalization.py"
spec = importlib.util.spec_from_file_location("generalization", path)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def record():
    config = dict(commit="synthetic", apk_sha256="a" * 64, device_label="test_phone",
                  android_version="13", model_sha256=gate.MODEL, prompt_revision="p1",
                  context_tokens=2048, mode="standard", sampling_revision="s1")
    return {"runs": [dict(configuration=dict(config),
                           **{k: True for k in gate.ATTESTATIONS},
                           cases={k: dict(points=2, route=v, native_generated=True,
                                          reviewed=True, variant_id=f"variant_{n}_{k}",
                                          evidence_id=f"evidence_{n}_{k}")
                                  for k, v in gate.CASES.items()})
                     for n in (1, 2)]}


class GeneralizationGateTests(unittest.TestCase):
    def test_valid_attestations_do_not_award_official_rating(self):
        result = gate.evaluate(record())
        self.assertEqual(result["generalization_score"], 24)
        self.assertEqual(result["model_only_minimum"], 22)
        self.assertTrue(result["target_met"])
        self.assertIsNone(result["official_ai_rating"])
        self.assertFalse(result["stable_release"])

    def test_missing_case_or_review_withholds_score(self):
        for field in ("reviewed", "native_generated"):
            r = record()
            r["runs"][1]["cases"]["H01"][field] = False
            self.assertIsNone(gate.evaluate(r)["generalization_score"])
        r = record()
        del r["runs"][1]["cases"]["H01"]
        self.assertIsNone(gate.evaluate(r)["generalization_score"])

    def test_reused_public_variant_wrong_model_or_changed_config_fail(self):
        r = record()
        r["runs"][1]["cases"]["H01"]["variant_id"] = "variant_1_H01"
        self.assertIsNone(gate.evaluate(r)["generalization_score"])
        r = record()
        r["runs"][1]["configuration"]["model_sha256"] = "b" * 64
        self.assertIsNone(gate.evaluate(r)["generalization_score"])
        r = record()
        r["runs"][1]["configuration"]["mode"] = "think"
        self.assertIsNone(gate.evaluate(r)["generalization_score"])

    def test_tool_substitution_is_not_neural_evidence(self):
        r = record()
        r["runs"][0]["cases"]["H03"]["route"] = "tool"
        self.assertIsNone(gate.evaluate(r)["generalization_score"])

    def test_lower_run_and_source_trust_gate(self):
        r = record()
        r["runs"][1]["cases"]["H08"]["points"] = 1
        result = gate.evaluate(r)
        self.assertEqual(result["generalization_score"], 23)
        self.assertFalse(result["target_met"])
        r = record()
        for k in ("H01", "H02", "H03"):
            r["runs"][0]["cases"][k]["points"] = 0
        self.assertEqual(gate.evaluate(r)["generalization_score"], 18)
        self.assertFalse(gate.evaluate(r)["target_met"])

    def test_invalid_numeric_type_and_private_text_not_echoed(self):
        r = record()
        r["runs"][0]["cases"]["H01"]["points"] = True
        self.assertIsNone(gate.evaluate(r)["generalization_score"])
        r = record()
        r["runs"][0]["cases"]["H01"]["evidence_id"] = "private text"
        result = gate.evaluate(r)
        self.assertNotIn("private text", str(result))


if __name__ == "__main__":
    unittest.main()
