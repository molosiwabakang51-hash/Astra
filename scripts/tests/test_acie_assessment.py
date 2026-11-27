"""Synthetic validation tests, not actual intelligence measurements."""
import hashlib
import importlib.util
import tempfile
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

    def test_resource_summary_includes_failed_attempts_and_separate_completion(self):
        r = record()
        cases = r["runs"][0]["cases"]
        for i, case in enumerate(cases, 1):
            case.update(elapsed_ms=i, peak_ram_bytes=i * 1024)
        cases[-1].update(outcome="failed", native_generated=False, credits=0,
                         elapsed_ms=10000)
        cases[0]["credits"] = 1
        result = gate.evaluate(r)
        summary = result["scores"]["A:assisted"]["resources"]
        self.assertEqual(summary["attempted"], 200)
        self.assertEqual(summary["completed"], 199)
        self.assertEqual(summary["failed"], 1)
        self.assertEqual(summary["all_attempt_latency"], {"p50_ms": 100, "p95_ms": 190})
        self.assertEqual(summary["completed_latency"], {"p50_ms": 100, "p95_ms": 190})
        self.assertEqual(summary["max_reported_peak_ram_bytes"], 204800)
        self.assertEqual((summary["full_credit"], summary["partial_credit"],
                          summary["zero_credit"]), (198, 1, 1))
        self.assertEqual(summary["assisted_cases"], 200)
        self.assertEqual(result["scores"]["A:assisted"]["by_dimension_resources"]
                         ["creativity"]["failed"], 1)

    def test_all_failed_cases_have_no_completed_latency(self):
        r = record()
        for case in r["runs"][0]["cases"]:
            case.update(outcome="failed", native_generated=False, credits=0)
        result = gate.evaluate(r)
        summary = result["scores"]["A:assisted"]["resources"]
        self.assertEqual(summary["completed_latency"], {"p50_ms": None, "p95_ms": None})
        self.assertEqual(summary["failed"], 200)
        self.assertFalse(result["provisional_80_numeric_met"])

    def test_provisional_threshold_does_not_relax_acceptance(self):
        r = record()
        for case in r["runs"][0]["cases"][:40]:
            case["credits"] = 0
        result = gate.evaluate(r)
        self.assertEqual(result["scores"]["A:assisted"]["score"], 80)
        self.assertTrue(result["provisional_80_numeric_met"])
        self.assertEqual(result["form_score_spread_points"], {"model": 0, "assisted": 20})
        self.assertFalse(result["target_met"])
        self.assertFalse(result["acceptance_record_eligible"])
        r["runs"][0]["cases"][40]["credits"] = 1
        self.assertFalse(gate.evaluate(r)["provisional_80_numeric_met"])

    def test_pinned_model_matches_android(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / "android/app/src/main/java/com/astra/agent/AstraModelImport.kt").read_text()
        self.assertIn('ADVANCED_SHA256 = "' + gate.MODEL + '"', source)

    def test_uncommitted_record_cannot_be_acceptance_eligible(self):
        result = gate.evaluate(record())
        self.assertTrue(result["target_met"])
        self.assertFalse(result["freeze_commitment_matched"])
        self.assertFalse(result["acceptance_record_eligible"])

    def test_freeze_manifest_binds_all_cases_and_configuration(self):
        r = record()
        manifest = dict(protocol="ACIE-v1-freeze",
                        configuration=dict(r["runs"][0]["configuration"]), runs=[])
        for run in r["runs"]:
            planned = dict(form=run["form"], track=run["track"], cases=[])
            for case in run["cases"]:
                case["rubric_sha256"] = hashlib.sha256(case["variant_id"].encode()).hexdigest()
                planned["cases"].append({k: case[k] for k in
                    ("variant_id", "dimension", "rubric_sha256")})
            manifest["runs"].append(planned)
        digest = gate.manifest_digest(manifest)
        result = gate.evaluate(r, manifest, digest)
        self.assertTrue(result["freeze_commitment_matched"])
        self.assertTrue(result["acceptance_record_eligible"])
        self.assertFalse(result["release_ready"])
        self.assertEqual(gate.evaluate(r, manifest, "e"*64)["status"], "INCOMPLETE")
        self.assertEqual(gate.evaluate(r, manifest)["status"], "INCOMPLETE")
        self.assertEqual(gate.evaluate(r, None, digest)["status"], "INCOMPLETE")
        case = r["runs"][0]["cases"][0]
        for key, changed in (("variant_id", "new_variant"), ("rubric_sha256", "e"*64)):
            original = case[key]
            case[key] = changed
            self.assertEqual(gate.evaluate(r, manifest, digest)["status"], "INCOMPLETE")
            case[key] = original
        for run in r["runs"]:
            run["configuration"]["apk_sha256"] = "e"*64
        self.assertEqual(gate.evaluate(r, manifest, digest)["status"], "INCOMPLETE")

    def test_manifest_duplicate_variant_and_extra_fields_rejected(self):
        r = record()
        manifest = dict(protocol="ACIE-v1-freeze",
                        configuration=dict(r["runs"][0]["configuration"]), runs=[])
        for run in r["runs"]:
            planned = dict(form=run["form"], track=run["track"], cases=[])
            for case in run["cases"]:
                case["rubric_sha256"] = hashlib.sha256(case["variant_id"].encode()).hexdigest()
                planned["cases"].append({k: case[k] for k in
                    ("variant_id", "dimension", "rubric_sha256")})
            manifest["runs"].append(planned)
        case = manifest["runs"][0]["cases"][0]
        case["private_prompt"] = "must not be in manifest"
        self.assertEqual(gate.evaluate(r, manifest, gate.manifest_digest(manifest))["status"], "INCOMPLETE")
        del case["private_prompt"]
        case["variant_id"] = manifest["runs"][1]["cases"][0]["variant_id"]
        self.assertEqual(gate.evaluate(r, manifest, gate.manifest_digest(manifest))["status"], "INCOMPLETE")


    def test_distinct_ids_cannot_hide_reused_private_rubric(self):
        r = record()
        manifest = dict(protocol="ACIE-v1-freeze",
                        configuration=dict(r["runs"][0]["configuration"]), runs=[])
        for run in r["runs"]:
            planned = dict(form=run["form"], track=run["track"], cases=[])
            for case in run["cases"]:
                case["rubric_sha256"] = hashlib.sha256(case["variant_id"].encode()).hexdigest()
                planned["cases"].append({k: case[k] for k in
                    ("variant_id", "dimension", "rubric_sha256")})
            manifest["runs"].append(planned)
        self.assertTrue(gate.evaluate(r, manifest, gate.manifest_digest(manifest))
                        ["acceptance_record_eligible"])
        # Even a self-consistent new commitment cannot disguise exact rubric
        # reuse under different IDs, whether within a form or across tracks.
        for run_idx, case_idx in ((0, 1), (1, 0), (3, 199)):
            actual = r["runs"][run_idx]["cases"][case_idx]
            planned = manifest["runs"][run_idx]["cases"][case_idx]
            old = actual["rubric_sha256"]
            actual["rubric_sha256"] = planned["rubric_sha256"] = r["runs"][0]["cases"][0]["rubric_sha256"]
            self.assertEqual(gate.evaluate(r, manifest, gate.manifest_digest(manifest))["status"],
                             "INCOMPLETE")
            actual["rubric_sha256"] = planned["rubric_sha256"] = old
        self.assertTrue(gate.evaluate(r, manifest, gate.manifest_digest(manifest))
                        ["acceptance_record_eligible"])

    def test_private_json_rejects_duplicate_keys_constants_and_oversize(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / "record.json"
            p.write_text('{"protocol":"ACIE-v1","runs":[]}', encoding="utf-8")
            self.assertEqual(gate.read_private_json(p), {"protocol":"ACIE-v1","runs":[]})
            for invalid in ('{"protocol":"x","protocol":"ACIE-v1"}',
                            '{"runs":[{"credits":0,"credits":2}]}',
                            '{"a":NaN}', '{"a":Infinity}', '{"a":-Infinity}'):
                p.write_text(invalid, encoding="utf-8")
                with self.assertRaises(ValueError):
                    gate.read_private_json(p)
            p.write_bytes(b" " * (4 * 1024 * 1024 + 1))
            with self.assertRaises(ValueError):
                gate.read_private_json(p)
            p.write_bytes(b"\xff")
            with self.assertRaises(ValueError):
                gate.read_private_json(p)


if __name__ == "__main__":
    unittest.main()
