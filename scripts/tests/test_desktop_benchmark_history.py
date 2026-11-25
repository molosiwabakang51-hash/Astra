"""Persistent performance history regression tests; all observations synthetic."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("astra_benchmark_history",ROOT/"desktop/astra_benchmark_history.py")
B = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = B
SPEC.loader.exec_module(B)
IDS = ("a"*64,"b"*64,"c"*64)


def report(wins=3, losses=0, shared=1, seconds=10, ratio=1.2):
    cases=[]
    for i, case in enumerate(B.CASE_IDS):
        baseline = not (i < wins or wins+losses <= i < wins+losses+shared)
        cooperation = not (wins <= i < wins+losses+shared)
        cases.append({"id":case,"self_review":{"first_correct":False,"final_correct":baseline,
                                               "seconds":seconds,"calls":3},
                      "cooperation":{"first_correct":False,"final_correct":cooperation,
                                     "seconds":seconds*ratio,"calls":3}})
    return {"cases":cases,"cooperation_correct":999}


class BenchmarkHistoryTests(unittest.TestCase):
    def setUp(self):
        tmp=tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.path=Path(tmp.name)/"history.sqlite"

    def test_recompute_summary_and_persist_only_correctness_and_timing(self):
        with B.BenchmarkHistory(self.path) as h:
            receipt=h.record(report(),*IDS,approved=True)
            self.assertTrue(receipt["recorded"])
            summary=h.report(*IDS)
            self.assertEqual(8,summary["self_review_correct"])
            self.assertEqual(11,summary["cooperation_correct"])
            self.assertEqual("insufficient_evidence",summary["recommendation"])
            columns=[r[1] for r in h.connection.execute("PRAGMA table_info(benchmark_cases)")]
            self.assertEqual(["digest","case_id","self_first","self_final","self_ms",
                              "coop_first","coop_final","coop_ms"],columns)
        with B.BenchmarkHistory(self.path) as h:
            self.assertEqual(summary,h.report(*IDS))

    def test_duplicate_import_is_idempotent(self):
        with B.BenchmarkHistory(self.path) as h:
            first=h.record(report(),*IDS,approved=True)
            reordered=report()
            reordered["cases"].reverse()
            second=h.record(reordered,*IDS,approved=True)
            self.assertEqual(first["digest"],second["digest"])
            self.assertFalse(second["recorded"])
            self.assertEqual(1,h.report(*IDS)["runs"])

    def test_repeated_observations_identify_shared_failure_without_deployment(self):
        with B.BenchmarkHistory(self.path) as h:
            for seconds in (10,11,12):
                h.record(report(seconds=seconds),*IDS,approved=True)
            summary=h.report(*IDS)
            self.assertEqual("candidate_for_independent_validation",summary["recommendation"])
            self.assertEqual(["m04"],summary["recurring_shared_failure_cases"])
            self.assertFalse(summary["deployment_allowed"])
            self.assertEqual(1.2,summary["median_latency_ratio"])
            self.assertEqual(3,summary["runs"])

    def test_any_regression_or_excess_latency_blocks_recommendation(self):
        for bad in (report(wins=1,seconds=12),report(losses=2,seconds=12),report(ratio=1.6,seconds=12)):
            with B.BenchmarkHistory(":memory:") as h:
                for trial in (report(seconds=10),report(seconds=11),bad):
                    h.record(trial,*IDS,approved=True)
                self.assertEqual("retain_baseline",h.report(*IDS)["recommendation"])

    def test_invalid_or_incomplete_suite_does_not_enter_history(self):
        invalid=[]
        for key,value in (("calls",2),("seconds",0),("seconds",float("nan")),("final_correct",1)):
            trial=report()
            trial["cases"][0]["self_review"][key]=value
            invalid.append(trial)
        trial=report()
        trial["cases"].pop()
        invalid.append(trial)
        trial=report()
        trial["cases"][0]["id"]="m02"
        invalid.append(trial)
        with B.BenchmarkHistory(self.path) as h:
            for trial in invalid:
                with self.assertRaises(ValueError):
                    h.record(trial,*IDS,approved=True)
            self.assertEqual(0,h.report(*IDS)["runs"])

    def test_consent_and_configuration_isolation(self):
        with B.BenchmarkHistory(self.path) as h:
            with self.assertRaises(ValueError):
                h.record(report(),*IDS)
            with self.assertRaises(ValueError):
                h.record(report(),IDS[0],IDS[0],IDS[2],approved=True)
            h.record(report(),*IDS,approved=True)
            self.assertEqual(0,h.report(IDS[0],IDS[1],"d"*64)["runs"])

    def test_confirmed_clear_and_corruption_detection(self):
        with B.BenchmarkHistory(self.path) as h:
            h.record(report(),*IDS,approved=True)
            with self.assertRaises(ValueError):
                h.clear()
            with h.connection:
                h.connection.execute("UPDATE benchmark_cases SET self_ms=1 WHERE case_id='m01'")
            with self.assertRaises(ValueError):
                h.report(*IDS)
            h.clear(confirmed=True)
            self.assertEqual(0,h.report(*IDS)["runs"])
            self.assertEqual(0,h.connection.execute("SELECT count(*) FROM benchmark_cases").fetchone()[0])

    def test_capacity_limit_refuses_overwrite(self):
        from unittest.mock import patch
        with B.BenchmarkHistory(self.path) as h, patch.object(B,"MAX_PER_CONFIGURATION",1):
            h.record(report(),*IDS,approved=True)
            with self.assertRaises(ValueError):
                h.record(report(seconds=11),*IDS,approved=True)
            self.assertEqual(1,h.report(*IDS)["runs"])


if __name__=="__main__":
    unittest.main()
