"""Synthetic tests for measured specialist selection, not device benchmarks."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("astra_adaptive_routing", ROOT / "desktop/astra_adaptive_routing.py")
R = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = R
SPEC.loader.exec_module(R)

PRIMARY = "a" * 64
SPECIALIST = "b" * 64
CONFIG = "c" * 64


def wrong(answer):
    return next(x for x in "ABCD" if x != answer)


def observations(wins=0, losses=0, specialist_ms=100):
    rows = []
    for i, case in enumerate(R.CASES):
        first = wrong(case.expected) if i < wins else case.expected
        second = wrong(case.expected) if wins <= i < wins + losses else case.expected
        rows.append((case.id, first, second, 100, specialist_ms))
    return rows


class AdaptiveRoutingTests(unittest.TestCase):
    def test_proposals_are_independent_and_order_alternates(self):
        calls = []
        ticks = iter(range(0, 2000000000, 10000000))
        def runner(role, prompt):
            calls.append((role, prompt))
            return "A"
        with self.assertRaises(ValueError):
            R.measure_pair(runner)
        self.assertEqual([], calls)
        rows = R.measure_pair(runner, approved=True, clock=lambda: next(ticks))
        self.assertEqual(24, len(rows))
        self.assertEqual(48, len(calls))
        self.assertEqual(["primary", "specialist", "specialist", "primary"],
                         [role for role, _ in calls[:4]])
        for i, case in enumerate(R.CASES):
            self.assertEqual(case.question + R.INSTRUCTION, calls[2*i][1])
            self.assertEqual(calls[2*i][1], calls[2*i+1][1])
        self.assertEqual(10, rows[0][3])
        self.assertEqual(10, rows[0][4])

    def test_invalid_or_incomplete_trial_is_not_scored(self):
        with self.assertRaises(ValueError):
            R.measure_pair(lambda role, prompt: "invalid", approved=True)
        for rows in (observations()[:-1], observations()[:-1] + observations()[:1]):
            with self.assertRaises(ValueError):
                R.score_rows(rows)
        for replacement in (("l01","A","B",True,100), ("l01","A","B",100,600001),
                            ("l01","A","invalid",100,100), ("unknown","A","B",100,100)):
            with self.assertRaises(ValueError):
                R.score_rows([replacement] + observations()[1:])

    def test_quality_and_latency_gates(self):
        for wins, losses, speed, expected in (
            (6, 1, 140, "specialist_candidate_for_independent_validation"),
            (3, 0, 100, "retain_primary"),
            (10, 3, 100, "retain_primary"),
            (8, 0, 160, "retain_primary"),
            (0, 0, 100, "retain_primary"),
        ):
            result = R.assess(R.score_rows(observations(wins, losses, speed)))
            self.assertEqual(expected, result["recommendation"])
            self.assertEqual(wins, result["specialist_wins"])
            self.assertEqual(losses, result["specialist_losses"])
            self.assertFalse(result["deployment_allowed"])

    def test_completed_trial_persists_without_raw_answers(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "performance.sqlite"
            rows = observations(6, 1, 140)
            with R.PerformanceLedger(path) as ledger:
                self.assertEqual("insufficient_evidence", ledger.recommendation(PRIMARY, SPECIALIST, CONFIG)["recommendation"])
                with self.assertRaises(ValueError):
                    ledger.record(PRIMARY, SPECIALIST, CONFIG, rows)
                result = ledger.record(PRIMARY, SPECIALIST, CONFIG, rows, approved=True)
                columns = [x[1] for x in ledger.connection.execute("PRAGMA table_info(paired_cases)")]
                self.assertEqual(["run_id", "case_id", "primary_correct", "specialist_correct",
                                  "primary_ms", "specialist_ms"], columns)
                self.assertEqual(24, ledger.connection.execute("SELECT count(*) FROM paired_cases").fetchone()[0])
            with R.PerformanceLedger(path) as ledger:
                self.assertEqual(result, ledger.recommendation(PRIMARY, SPECIALIST, CONFIG))
                self.assertEqual("insufficient_evidence", ledger.recommendation(PRIMARY, SPECIALIST, "d"*64)["recommendation"])

    def test_replays_cannot_inflate_or_replace_evidence(self):
        with R.PerformanceLedger(":memory:") as ledger:
            rows = observations(6, 1, 140)
            first = ledger.record(PRIMARY, SPECIALIST, CONFIG, rows, approved=True)
            self.assertEqual(first, ledger.record(PRIMARY, SPECIALIST, CONFIG, list(reversed(rows)), approved=True))
            with self.assertRaises(ValueError):
                ledger.record(PRIMARY, SPECIALIST, CONFIG, observations(0, 0), approved=True)
            self.assertEqual(1, ledger.connection.execute("SELECT count(*) FROM paired_runs").fetchone()[0])
            self.assertEqual(first, ledger.recommendation(PRIMARY, SPECIALIST, CONFIG))

    def test_same_model_or_invalid_identity_refused(self):
        with R.PerformanceLedger(":memory:") as ledger:
            for ids in ((PRIMARY, PRIMARY, CONFIG), ("fake", SPECIALIST, CONFIG),
                        (PRIMARY, SPECIALIST, None)):
                with self.assertRaises(ValueError):
                    ledger.record(*ids, observations(), approved=True)
            self.assertEqual(0, ledger.connection.execute("SELECT count(*) FROM paired_runs").fetchone()[0])

    def test_confirmed_clear_cascades_and_recommendation_resets(self):
        with R.PerformanceLedger(":memory:") as ledger:
            ledger.record(PRIMARY, SPECIALIST, CONFIG, observations(), approved=True)
            with self.assertRaises(ValueError):
                ledger.clear()
            ledger.clear(confirmed=True)
            self.assertEqual(0, ledger.connection.execute("SELECT count(*) FROM paired_cases").fetchone()[0])
            self.assertEqual("insufficient_evidence", ledger.recommendation(PRIMARY, SPECIALIST, CONFIG)["recommendation"])

    def test_corrupted_incomplete_persistence_is_rejected(self):
        with R.PerformanceLedger(":memory:") as ledger:
            ledger.record(PRIMARY, SPECIALIST, CONFIG, observations(), approved=True)
            with ledger.connection:
                ledger.connection.execute("DELETE FROM paired_cases WHERE case_id='l01'")
            with self.assertRaises(ValueError):
                ledger.recommendation(PRIMARY, SPECIALIST, CONFIG)

if __name__ == "__main__":
    unittest.main()
