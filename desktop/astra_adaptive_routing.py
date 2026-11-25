"""Measured, offline specialist-routing experiment.

Record only a complete fixed synthetic paired suite. Store aggregate-relevant
correctness and timing, not prompts, model outputs, private chats or paths.
Routing recommendations are advisory and never activate models or train weights.
The trusted caller must supply real local-model SHA-256 identities and a
configuration fingerprint; this module cannot authenticate the runner.
"""
from dataclasses import dataclass
import math
from pathlib import Path
import re
import sqlite3
import statistics
import time

SUITE = "logic-v1"
MAX_CONFIGS = 100
MAX_LATENCY_MS = 600000
OPTIONS = frozenset("ABCD")
DIGEST = re.compile(r"[0-9a-f]{64}\Z")
INSTRUCTION = "\nReturn only FINAL: followed by A, B, C or D."


@dataclass(frozen=True)
class Case:
    id: str
    question: str
    expected: str


# Varied deductive structures, not one repeated arithmetic template.
# These are fixed development probes, not an independent held-out population.
CASES = (
    Case("l01", "All mammals in this puzzle are warm-blooded. A whale is a mammal. What follows? A: It is warm-blooded B: It is a fish C: All warm-blooded things are whales D: Nothing follows", "A"),
    Case("l02", "If a file is signed, it has a signature. File X has no signature. What follows? A: X is signed B: X is not signed C: X is encrypted D: Not enough information", "B"),
    Case("l03", "If a lamp is on, the room is lit. The room is lit. Must the lamp be on? A: Yes B: Only at night C: No, another source could light it D: The lamp is broken", "C"),
    Case("l04", "Every A is B. Some B are C. Is it guaranteed that some A are C? A: Yes B: All A are C C: No A are C D: Not guaranteed", "D"),
    Case("l05", "Exactly one of X and Y is true. X is false. What follows? A: Y is true B: Y is false C: Both are false D: Neither can be determined", "A"),
    Case("l06", "X implies Y; Y implies Z. X is true. What follows? A: Z is false B: Z is true C: Y is false D: Z is unknown", "B"),
    Case("l07", "No A is B. Every C is A. Can a C be B? A: Always B: Sometimes C: No D: Not enough information", "C"),
    Case("l08", "M is taller than N. N is taller than P. Which is shortest? A: M B: N C: Cannot tell D: P", "D"),
    Case("l09", "At least one of X and Y is true. Both may be true. X is true. Is Y necessarily false? A: No B: Yes C: Y is necessarily true D: X is false", "A"),
    Case("l10", "A necessary condition for entering is a ticket. Lina has no ticket. Can Lina enter under the rule? A: Yes B: No C: Always D: A ticket is irrelevant", "B"),
    Case("l11", "A sufficient condition for access is a valid badge. Jo has a valid badge. What follows? A: Jo lacks access B: Nobody else has access C: Jo has access D: Access is unknowable", "C"),
    Case("l12", "All red boxes are sealed. Some sealed boxes are blue. Which is guaranteed? A: All blue boxes are red B: No blue box is sealed C: All sealed boxes are red D: Every red box is sealed", "D"),
    Case("l13", "Three tasks must be ordered. A must precede B, and B must precede C. Which order is valid? A: A B C B: C A B C: B A C D: A C B", "A"),
    Case("l14", "A system permits access only if both X and Y hold. X holds, Y does not. What follows? A: Access is permitted B: Access is not permitted C: Y holds D: Access is guaranteed by X alone", "B"),
    Case("l15", "Not all reports are accurate. What follows? A: All are inaccurate B: None exist C: At least one report is inaccurate D: Exactly one is accurate", "C"),
    Case("l16", "If P then Q. If Q then R. R is false. What follows? A: P is true B: Q is true C: R is true D: P is false", "D"),
    Case("l17", "A set has elements {1,2,3}. Another has {2,3,4}. Which is their intersection? A: {2,3} B: {1,4} C: {1,2,3,4} D: {}", "A"),
    Case("l18", "If either valve fails, the alarm activates. The alarm did not activate. Which follows? A: Both valves failed B: Neither valve failed C: Exactly one failed D: Nothing can be concluded", "B"),
    Case("l19", "All poets are writers. At least one poet exists. Which is guaranteed? A: All writers are poets B: No writers exist C: At least one writer exists D: All poets are not writers", "C"),
    Case("l20", "A statement and its negation are both asserted true in classical logic. What is the result? A: A tautology B: A valid proof C: An implication D: A contradiction", "D"),
    Case("l21", "A number is even and greater than 5 but less than 9. Which could it be? A: 8 B: 5 C: 9 D: 7", "A"),
    Case("l22", "Only employees may enter the office. Sam entered. Under the rule, what follows? A: All employees entered B: Sam is an employee C: Sam is the manager D: Nobody else entered", "B"),
    Case("l23", "Neither X nor Y is true. What is true? A: X is true B: Y is true C: Both are false D: Exactly one is true", "C"),
    Case("l24", "A rule says if P then Q. Which observation disproves the rule? A: P false, Q false B: P false, Q true C: P true, Q true D: P true, Q false", "D"),
)


def validate_identity(primary, specialist, configuration):
    if any(not isinstance(value, str) or not DIGEST.fullmatch(value)
           for value in (primary, specialist, configuration)):
        raise ValueError("Expected lowercase SHA-256 identities.")
    if primary == specialist:
        raise ValueError("Distinct model identities required.")


def measure_pair(run, *, approved=False, clock=time.monotonic_ns):
    """Collect one complete suite; runner receives only role and question.

    Caller owns subprocess timeouts, memory admission and actual model loading.
    Alternating first role reduces order bias; it does not eliminate it.
    A malformed or incomplete trial is rejected rather than scored.
    """
    if approved is not True:
        raise ValueError("Explicit synthetic evaluation approval required.")
    rows = []
    for index, case in enumerate(CASES):
        values = {}
        roles = ("primary", "specialist") if index % 2 == 0 else ("specialist", "primary")
        for role in roles:
            started = clock()
            answer = run(role, case.question + INSTRUCTION)
            elapsed = clock() - started
            if type(answer) is not str or answer not in OPTIONS:
                raise ValueError("Incomplete or ambiguous synthetic model output.")
            if elapsed <= 0 or elapsed > MAX_LATENCY_MS * 1000000:
                raise ValueError("Invalid or excessive measured latency.")
            values[role] = (answer, math.ceil(elapsed / 1000000))
        rows.append((case.id, values["primary"][0], values["specialist"][0],
                     values["primary"][1], values["specialist"][1]))
    return rows


def score_rows(rows):
    if not isinstance(rows, (list, tuple)) or len(rows) != len(CASES):
        raise ValueError("Complete synthetic suite required.")
    expected = {case.id: case.expected for case in CASES}
    seen = set()
    scored = []
    for row in rows:
        if not isinstance(row, (list, tuple)) or len(row) != 5:
            raise ValueError("Invalid paired observation.")
        case, primary, specialist, primary_ms, specialist_ms = row
        if type(case) is not str or case not in expected or case in seen:
            raise ValueError("Unknown or duplicate synthetic case.")
        if any(type(answer) is not str or answer not in OPTIONS for answer in (primary, specialist)):
            raise ValueError("Invalid proposal.")
        if any(type(value) is not int or not 1 <= value <= MAX_LATENCY_MS
               for value in (primary_ms, specialist_ms)):
            raise ValueError("Invalid latency.")
        seen.add(case)
        scored.append((case, int(primary == expected[case]), int(specialist == expected[case]),
                       primary_ms, specialist_ms))
    return sorted(scored)


def assess(scored):
    if len(scored) != len(CASES):
        raise ValueError("No recommendation from incomplete trials.")
    primary = sum(row[1] for row in scored)
    specialist = sum(row[2] for row in scored)
    wins = sum(row[1] == 0 and row[2] == 1 for row in scored)
    losses = sum(row[1] == 1 and row[2] == 0 for row in scored)
    ratio = statistics.median(row[4] / row[3] for row in scored)
    # Conservative descriptive gate, NOT a statistical significance claim.
    # Retain primary unless the specialist wins >=4 net cases, loses at most
    # two, and its median paired wall latency is within 1.5x the primary.
    eligible = wins - losses >= 4 and losses <= 2 and ratio <= 1.5
    return {
        "suite": SUITE, "cases": len(scored),
        "primary_correct": primary, "specialist_correct": specialist,
        "specialist_wins": wins, "specialist_losses": losses,
        "both_correct": sum(row[1] == 1 and row[2] == 1 for row in scored),
        "both_wrong": sum(row[1] == 0 and row[2] == 0 for row in scored),
        "primary_error_cases": [row[0] for row in scored if row[1] == 0],
        "specialist_error_cases": [row[0] for row in scored if row[2] == 0],
        "median_specialist_to_primary_latency": round(ratio, 3),
        "recommendation": "specialist_candidate_for_independent_validation" if eligible else "retain_primary",
        "deployment_allowed": False,
        "scope": "Fixed synthetic logic probes; caller-supplied runtime identity; not proof of general reasoning improvement.",
    }


class PerformanceLedger:
    def __init__(self, path):
        self.connection = sqlite3.connect(str(Path(path)), timeout=5)
        try:
            self.connection.execute("PRAGMA foreign_keys=ON")
            self.connection.execute("PRAGMA secure_delete=ON")
            self.connection.execute("PRAGMA trusted_schema=OFF")
            with self.connection:
                version = self.connection.execute("PRAGMA user_version").fetchone()[0]
                if version not in (0, 1):
                    raise ValueError("Unsupported performance database.")
                self.connection.execute("""
                    CREATE TABLE IF NOT EXISTS paired_runs (
                        id INTEGER PRIMARY KEY,
                        suite TEXT NOT NULL,
                        primary_sha TEXT NOT NULL,
                        specialist_sha TEXT NOT NULL,
                        configuration_sha TEXT NOT NULL,
                        UNIQUE(suite,primary_sha,specialist_sha,configuration_sha)
                    )
                """)
                self.connection.execute("""
                    CREATE TABLE IF NOT EXISTS paired_cases (
                        run_id INTEGER NOT NULL REFERENCES paired_runs(id) ON DELETE CASCADE,
                        case_id TEXT NOT NULL,
                        primary_correct INTEGER NOT NULL CHECK(primary_correct IN (0,1)),
                        specialist_correct INTEGER NOT NULL CHECK(specialist_correct IN (0,1)),
                        primary_ms INTEGER NOT NULL CHECK(primary_ms BETWEEN 1 AND 600000),
                        specialist_ms INTEGER NOT NULL CHECK(specialist_ms BETWEEN 1 AND 600000),
                        PRIMARY KEY(run_id,case_id)
                    )
                """)
                self.connection.execute("PRAGMA user_version=1")
        except BaseException:
            self.connection.close()
            raise

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.connection.close()

    def _rows(self, run_id):
        return self.connection.execute(
            "SELECT case_id,primary_correct,specialist_correct,primary_ms,specialist_ms "
            "FROM paired_cases WHERE run_id=? ORDER BY case_id", (run_id,)).fetchall()

    def _lookup(self, primary, specialist, configuration):
        return self.connection.execute(
            "SELECT id FROM paired_runs WHERE suite=? AND primary_sha=? AND specialist_sha=? AND configuration_sha=?",
            (SUITE, primary, specialist, configuration)).fetchone()

    def record(self, primary, specialist, configuration, observations, *, approved=False):
        if approved is not True:
            raise ValueError("Explicit permission to retain synthetic measurements required.")
        validate_identity(primary, specialist, configuration)
        scored = score_rows(observations)
        with self.connection:
            self.connection.execute("BEGIN IMMEDIATE")
            previous = self._lookup(primary, specialist, configuration)
            if previous:
                # A replay cannot inflate evidence or replace an inconvenient
                # result. New configurations need distinct fingerprints.
                if self._rows(previous[0]) != scored:
                    raise ValueError("A completed trial already exists for this configuration.")
                return assess(scored)
            if self.connection.execute("SELECT count(*) FROM paired_runs").fetchone()[0] >= MAX_CONFIGS:
                raise ValueError("Performance ledger capacity reached.")
            cursor = self.connection.execute(
                "INSERT INTO paired_runs(suite,primary_sha,specialist_sha,configuration_sha) VALUES(?,?,?,?)",
                (SUITE, primary, specialist, configuration))
            self.connection.executemany(
                "INSERT INTO paired_cases VALUES(?,?,?,?,?,?)",
                [(cursor.lastrowid, *row) for row in scored])
        return assess(scored)

    def recommendation(self, primary, specialist, configuration):
        validate_identity(primary, specialist, configuration)
        row = self._lookup(primary, specialist, configuration)
        if not row:
            return {"recommendation": "insufficient_evidence", "deployment_allowed": False}
        scored = self._rows(row[0])
        if {r[0] for r in scored} != {c.id for c in CASES}:
            raise ValueError("Stored synthetic trial is incomplete.")
        return assess(scored)

    def clear(self, *, confirmed=False):
        if confirmed is not True:
            raise ValueError("Explicit deletion confirmation required.")
        with self.connection:
            self.connection.execute("DELETE FROM paired_runs")
