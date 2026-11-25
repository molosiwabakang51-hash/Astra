"""Persistent benchmark history for the equal-call synthetic reasoning suite.

Records only aggregate-relevant correctness and duration, plus explicit
model/configuration identities. Imported observations are caller attestations,
not authenticated inference measurements. Repeated fixed questions are not
independent test populations. Recommendations never authorize deployment.
"""
import hashlib
import json
import math
from pathlib import Path
import re
import sqlite3
import statistics

SUITE = "equal-call-v1"
CASE_IDS = tuple(f"m{i:02d}" for i in range(1,13))
ROLES = ("self_review", "cooperation")
MAX_RUNS = 200
MAX_PER_CONFIGURATION = 20
DIGEST = re.compile(r"[0-9a-f]{64}\Z")
MAX_ARM_MS = 1200000


def identity(primary, critic, configuration):
    if any(not isinstance(value, str) or not DIGEST.fullmatch(value)
           for value in (primary, critic, configuration)):
        raise ValueError("Lowercase SHA-256 identities required.")
    if primary == critic:
        raise ValueError("Distinct model identities required.")
    return primary, critic, configuration


def observations(report):
    """Recompute all summaries from exactly twelve bounded paired rows."""
    if type(report) is not dict or type(report.get("cases")) is not list:
        raise ValueError("Expected benchmark report.")
    rows = report["cases"]
    if len(rows) != len(CASE_IDS):
        raise ValueError("Incomplete benchmark suite.")
    seen, normalized = set(), []
    for row in rows:
        if type(row) is not dict or set(row) != {"id", *ROLES}:
            raise ValueError("Unexpected case fields.")
        case = row["id"]
        if type(case) is not str or case not in CASE_IDS or case in seen:
            raise ValueError("Unknown or duplicate case ID.")
        seen.add(case)
        values = [case]
        for role in ROLES:
            arm = row[role]
            if type(arm) is not dict or set(arm) != {"first_correct","final_correct","seconds","calls"}:
                raise ValueError("Invalid paired observation.")
            if type(arm["first_correct"]) is not bool or type(arm["final_correct"]) is not bool:
                raise ValueError("Correctness must be explicit booleans.")
            if type(arm["calls"]) is not int or arm["calls"] != 3:
                raise ValueError("Each arm must contain three inference calls.")
            seconds = arm["seconds"]
            if type(seconds) not in (int,float) or not 0 < seconds <= MAX_ARM_MS / 1000 or not math.isfinite(seconds):
                raise ValueError("Invalid arm duration; real positive timing required.")
            milliseconds = round(seconds * 1000)
            if not 1 <= milliseconds <= MAX_ARM_MS:
                raise ValueError("Duration outside millisecond bounds.")
            values.extend((int(arm["first_correct"]),int(arm["final_correct"]),milliseconds))
        normalized.append(tuple(values))
    return sorted(normalized)


def canonical_digest(ids, rows):
    value = json.dumps([SUITE, list(ids), rows], separators=(",",":"), allow_nan=False)
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def summarize(runs):
    """Descriptive paired metrics; never claim statistical significance."""
    counts = len(runs)
    if not counts:
        return {"suite":SUITE,"runs":0,"recommendation":"insufficient_evidence",
                "deployment_allowed":False}
    details = []
    for index, case in enumerate(CASE_IDS):
        paired = [rows[index] for rows in runs]
        details.append({
            "id":case,
            "self_review_failures":sum(1-row[2] for row in paired),
            "cooperation_failures":sum(1-row[5] for row in paired),
            "shared_failures":sum(row[2]==0 and row[5]==0 for row in paired),
            "cooperation_wins":sum(row[2]==0 and row[5]==1 for row in paired),
            "cooperation_losses":sum(row[2]==1 and row[5]==0 for row in paired),
        })
    run_summaries = []
    for rows in runs:
        self_correct = sum(row[2] for row in rows)
        coop_correct = sum(row[5] for row in rows)
        wins = sum(row[2]==0 and row[5]==1 for row in rows)
        losses = sum(row[2]==1 and row[5]==0 for row in rows)
        self_ms = sum(row[3] for row in rows)
        coop_ms = sum(row[6] for row in rows)
        run_summaries.append({
            "self_review_correct":self_correct,"cooperation_correct":coop_correct,
            "wins":wins,"losses":losses,
            "latency_ratio":coop_ms/self_ms,
        })
    # A strict descriptive development gate: every retained run must improve
    # by >=2 cases, regress on <=1 case, and stay within 1.5x baseline time.
    # Even three repeat records are not independent held-out observations.
    eligible = counts >= 3 and all(
        row["wins"]-row["losses"] >= 2 and row["losses"] <= 1
        and row["latency_ratio"] <= 1.5 for row in run_summaries)
    return {
        "suite":SUITE,"runs":counts,"cases_per_run":len(CASE_IDS),
        "self_review_correct":sum(row["self_review_correct"] for row in run_summaries),
        "cooperation_correct":sum(row["cooperation_correct"] for row in run_summaries),
        "median_latency_ratio":round(statistics.median(row["latency_ratio"] for row in run_summaries),3),
        "recurring_shared_failure_cases":[row["id"] for row in details if row["shared_failures"]>=2],
        "case_profiles":details,
        "recommendation":"candidate_for_independent_validation" if eligible else
                         "insufficient_evidence" if counts<3 else "retain_baseline",
        "deployment_allowed":False,
        "scope":"Imported or caller-measured fixed synthetic observations; repeat records are not independent evidence and cannot authorize specialist deployment.",
    }


class BenchmarkHistory:
    def __init__(self,path):
        self.connection=sqlite3.connect(str(Path(path)),timeout=5)
        try:
            self.connection.execute("PRAGMA foreign_keys=ON")
            self.connection.execute("PRAGMA secure_delete=ON")
            self.connection.execute("PRAGMA trusted_schema=OFF")
            with self.connection:
                version=self.connection.execute("PRAGMA user_version").fetchone()[0]
                if version not in (0,1):
                    raise ValueError("Unsupported benchmark history schema.")
                self.connection.execute("""
                    CREATE TABLE IF NOT EXISTS benchmark_runs (
                        digest TEXT PRIMARY KEY,
                        suite TEXT NOT NULL,
                        primary_sha TEXT NOT NULL,
                        critic_sha TEXT NOT NULL,
                        configuration_sha TEXT NOT NULL
                    )
                """)
                self.connection.execute("""
                    CREATE TABLE IF NOT EXISTS benchmark_cases (
                        digest TEXT NOT NULL REFERENCES benchmark_runs(digest) ON DELETE CASCADE,
                        case_id TEXT NOT NULL,
                        self_first INTEGER NOT NULL CHECK(self_first IN (0,1)),
                        self_final INTEGER NOT NULL CHECK(self_final IN (0,1)),
                        self_ms INTEGER NOT NULL CHECK(self_ms BETWEEN 1 AND 1200000),
                        coop_first INTEGER NOT NULL CHECK(coop_first IN (0,1)),
                        coop_final INTEGER NOT NULL CHECK(coop_final IN (0,1)),
                        coop_ms INTEGER NOT NULL CHECK(coop_ms BETWEEN 1 AND 1200000),
                        PRIMARY KEY(digest,case_id)
                    )
                """)
                self.connection.execute("PRAGMA user_version=1")
        except BaseException:
            self.connection.close()
            raise

    def __enter__(self):
        return self

    def __exit__(self,*args):
        self.connection.close()

    def _digests(self,ids):
        return [row[0] for row in self.connection.execute(
            "SELECT digest FROM benchmark_runs WHERE suite=? AND primary_sha=? AND critic_sha=? AND configuration_sha=? ORDER BY digest",
            (SUITE,*ids))]

    def _rows(self,digest):
        rows=self.connection.execute(
            "SELECT case_id,self_first,self_final,self_ms,coop_first,coop_final,coop_ms FROM benchmark_cases WHERE digest=? ORDER BY case_id",
            (digest,)).fetchall()
        if tuple(row[0] for row in rows)!=CASE_IDS:
            raise ValueError("Stored benchmark trial is incomplete.")
        return rows

    def record(self,report,primary,critic,configuration,*,approved=False):
        if approved is not True:
            raise ValueError("Explicit permission to retain benchmark observations required.")
        ids=identity(primary,critic,configuration)
        rows=observations(report)
        digest=canonical_digest(ids,rows)
        with self.connection:
            self.connection.execute("BEGIN IMMEDIATE")
            existing=self._digests(ids)
            if digest in existing:
                if self._rows(digest)!=rows:
                    raise ValueError("Stored benchmark fingerprint mismatch.")
                return {"digest":digest,"recorded":False}
            if len(existing)>=MAX_PER_CONFIGURATION or self.connection.execute(
                    "SELECT count(*) FROM benchmark_runs").fetchone()[0]>=MAX_RUNS:
                raise ValueError("Benchmark history capacity reached.")
            self.connection.execute("INSERT INTO benchmark_runs VALUES(?,?,?,?,?)",(digest,SUITE,*ids))
            self.connection.executemany("INSERT INTO benchmark_cases VALUES(?,?,?,?,?,?,?,?)",
                                        [(digest,*row) for row in rows])
        return {"digest":digest,"recorded":True}

    def report(self,primary,critic,configuration):
        ids=identity(primary,critic,configuration)
        # One read transaction protects the summary from concurrent deletion.
        with self.connection:
            self.connection.execute("BEGIN")
            runs=[]
            for digest in self._digests(ids):
                rows=self._rows(digest)
                if canonical_digest(ids,rows)!=digest:
                    raise ValueError("Stored benchmark fingerprint mismatch.")
                runs.append(rows)
            return summarize(runs)

    def clear(self,*,confirmed=False):
        if confirmed is not True:
            raise ValueError("Explicit deletion confirmation required.")
        with self.connection:
            self.connection.execute("DELETE FROM benchmark_runs")
