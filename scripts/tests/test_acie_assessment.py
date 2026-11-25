"""Synthetic validation tests, not actual intelligence measurements."""
import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "quality/evaluate_acie_assessment.py"
spec = importlib.util.spec_from_file_location("acie", path)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def record():
    config = dict(commit="a"*40, apk_sha256="b"*64, model_sha256=gate.MODEL,
                  policy_sha256="c"*64, context_tokens=4096, device_label="phone")
    runs = []
    for form, track in sorted(gate.TRACKS):
        cases = []
        for dim, (count, _) in gate.DIMENSIONS.items():
            for i in range(count):
                key = f"{form}_{track}_{dim}_{i}"
                cases.append(dict(dimension=dim, credits=2, reviewed=True,
                                  track=track, assisted=track == "assisted",
                                  native_generated=True, outcome="complete",
                                  variant_id="v_"+key, evidence_id="e_"+key,
                                  elapsed_ms=1, peak_ram_bytes=1))
        runs.append(dict(form=form, track=track, configuration=dict(config),
                         cases=cases, **dict.fromkeys(gate.ATTEST, True)))
    return dict(protocol="ACIE-v1", runs=runs)


class ACIEAssessmentTest(unittest.TestCase):
    def test_four_disjoint_forms_pass_but_not_release(self):
        result = gate.evaluate(record())
        self.assertEqual(result["model_only_min"], 100)
        self.assertEqual(result["assisted_min"], 100)
        self.assertTrue(result["target_met"])
        self.assertFalse(result["release_ready"])

    def test_failed_cases_stay_in_denominator(self):
        r = record()
        case = r["runs"][0]["cases"][0]
        case.update(outcome="failed", native_generated=False, credits=0)
        result = gate.evaluate(r)
        self.assertEqual(result["scores"]["A:assisted"]["score"], 99.5)
        self.assertTrue(result["target_met"])
        case["credits"] = 1
        self.assertEqual(gate.evaluate(r)["status"], "INCOMPLETE")

    def test_dimension_floor_cannot_be_averaged_away(self):
        r = record()
        for case in r["runs"][0]["cases"]:
            if case["dimension"] == "creativity":
                case["credits"] = 0
        result = gate.evaluate(r)
        self.assertEqual(result["scores"]["A:assisted"]["score"], 95)
        self.assertFalse(result["target_met"])

    def test_reused_evidence_config_and_assistance_fail(self):
        for mutate in (
            lambda r: r["runs"][1]["cases"][0].update(evidence_id=r["runs"][0]["cases"][0]["evidence_id"]),
            lambda r: r["runs"][1]["configuration"].update(commit="d"*40),
            lambda r: r["runs"][1]["cases"][0].update(assisted=True),
            lambda r: r["runs"][0]["cases"].pop(),
            lambda r: r["runs"][0]["cases"][0].update(credits=True),
        ):
            r = record()
            mutate(r)
            self.assertEqual(gate.evaluate(r)["status"], "INCOMPLETE")

    def test_pinned_model_matches_android(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / "android/app/src/main/java/com/astra/agent/AstraModelImport.kt").read_text()
        self.assertIn('ADVANCED_SHA256 = "' + gate.MODEL + '"', source)


if __name__ == "__main__":
    unittest.main()
