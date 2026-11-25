"""Local evidence and correction ledger for Astra's desktop pilot.

Only explicitly reviewed, owner-supplied records may enter this database.
Review is an attestation, not independent factual verification. Search is
lexical, not embedding-based. No model-generated facts are automatically saved.
"""
import hashlib
import heapq
import re
import sqlite3
import unicodedata
from pathlib import Path

MAX_RECORDS = 10000
MAX_TEXT = 4000
MAX_SOURCE = 500
MAX_QUERY = 500
SCHEMA = 1
WORD = re.compile(r"[^\W_]+", re.UNICODE)

def terms(value):
    return set(WORD.findall(unicodedata.normalize("NFKC", value).casefold()))

def clean(value, maximum, name):
    if not isinstance(value, str):
        raise ValueError(name + " must be text.")
    value = value.strip()
    if not value or len(value) > maximum or "\x00" in value:
        raise ValueError("Invalid " + name + ".")
    return value

def fingerprint(subject, text, source, kind, supersedes):
    import json
    values = [subject, text, source, kind, supersedes]
    return hashlib.sha256(json.dumps(values, ensure_ascii=False,
                                    separators=(",", ":")).encode("utf-8")).hexdigest()

class Ledger:
    def __init__(self, path):
        self.connection = sqlite3.connect(str(Path(path)), timeout=5)
        try:
            self.connection.row_factory = sqlite3.Row
            self.connection.execute("PRAGMA foreign_keys=ON")
            self.connection.execute("PRAGMA secure_delete=ON")
            self.connection.execute("PRAGMA trusted_schema=OFF")
            with self.connection:
                version = self.connection.execute("PRAGMA user_version").fetchone()[0]
                if version not in (0, SCHEMA):
                    raise ValueError("Unsupported evidence schema.")
                self.connection.execute("""
                    CREATE TABLE IF NOT EXISTS evidence (
                        id INTEGER PRIMARY KEY,
                        subject TEXT NOT NULL,
                        text TEXT NOT NULL,
                        source TEXT NOT NULL,
                        kind TEXT NOT NULL CHECK(kind IN ('reference','correction')),
                        supersedes INTEGER UNIQUE REFERENCES evidence(id),
                        fingerprint TEXT NOT NULL UNIQUE,
                        active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1))
                    )
                """)
                self.connection.execute("PRAGMA user_version=1")
        except BaseException:
            self.connection.close()
            raise

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def close(self):
        self.connection.close()

    def add(self, subject, text, source, *, reviewed=False, supersedes=None):
        if reviewed is not True:
            raise ValueError("Explicit owner review required.")
        subject = clean(subject, 200, "subject")
        text = clean(text, MAX_TEXT, "evidence text")
        source = clean(source, MAX_SOURCE, "source")
        if supersedes is not None and (type(supersedes) is not int or supersedes <= 0):
            raise ValueError("Invalid superseded evidence identifier.")
        kind = "correction" if supersedes is not None else "reference"
        key = fingerprint(subject, text, source, kind, supersedes)
        # An immediate transaction serialises competing corrections.
        with self.connection:
            self.connection.execute("BEGIN IMMEDIATE")
            duplicate = self.connection.execute(
                "SELECT id FROM evidence WHERE fingerprint=?", (key,)).fetchone()
            if duplicate:
                return duplicate["id"]
            count = self.connection.execute("SELECT count(*) FROM evidence").fetchone()[0]
            if count >= MAX_RECORDS:
                raise ValueError("Evidence capacity reached.")
            if supersedes is not None:
                previous = self.connection.execute(
                    "SELECT subject, active FROM evidence WHERE id=?", (supersedes,)).fetchone()
                if not previous or not previous["active"] or previous["subject"] != subject:
                    raise ValueError("Correction must supersede an active record on the same subject.")
                self.connection.execute("UPDATE evidence SET active=0 WHERE id=?", (supersedes,))
            cursor = self.connection.execute(
                "INSERT INTO evidence(subject,text,source,kind,supersedes,fingerprint) VALUES(?,?,?,?,?,?)",
                (subject, text, source, kind, supersedes, key))
            return cursor.lastrowid

    def search(self, query, limit=5):
        query = clean(query, MAX_QUERY, "query")
        if type(limit) is not int or not 1 <= limit <= 10:
            raise ValueError("Search limit must be 1–10.")
        tokens = terms(query)
        if not tokens:
            return []
        def candidates():
            # Stream all matching records but retain only the requested top-k
            # during ranking, avoiding a large duplicate in-memory corpus.
            for row in self.connection.execute(
                    "SELECT id,subject,text,source,kind,supersedes FROM evidence WHERE active=1"):
                subject_hits = len(tokens & terms(row["subject"]))
                text_hits = len(tokens & terms(row["text"]))
                if subject_hits or text_hits:
                    yield (3 * subject_hits + text_hits, row["id"], dict(row))
        ranked = heapq.nsmallest(limit, candidates(), key=lambda item: (-item[0], item[1]))
        return [dict(item[2], trust="owner_reviewed_not_independently_verified",
                     lexical_score=item[0]) for item in ranked]

    def vector_snapshot(self, limit=256):
        """Newest active records and their total count from one SQL snapshot."""
        if type(limit) is not int or not 1 <= limit <= 256:
            raise ValueError("Vector snapshot limit must be 1–256.")
        rows = self.connection.execute(
            "SELECT id,subject,text,source,kind,supersedes,COUNT(*) OVER() AS active_count "
            "FROM evidence WHERE active=1 ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
        total = rows[0]["active_count"] if rows else 0
        records = []
        for row in rows:
            record = dict(row)
            del record["active_count"]
            records.append(dict(record, active=True,
                                trust="owner_reviewed_not_independently_verified"))
        return records, total

    def lookup_active(self, ids):
        """Re-read only requested active IDs after retrieval or correction."""
        if not isinstance(ids, (list, tuple)) or len(ids) > 20:
            raise ValueError("Active lookup requires a bounded ID list.")
        if any(type(value) is not int or not 1 <= value <= (2**63 - 1) for value in ids):
            raise ValueError("Invalid evidence ID.")
        if not ids:
            return []
        unique = list(dict.fromkeys(ids))
        placeholders = ",".join("?" for _ in unique)
        rows = self.connection.execute(
            "SELECT id,subject,text,source,kind,supersedes FROM evidence "
            "WHERE active=1 AND id IN (" + placeholders + ")", unique).fetchall()
        return [dict(row, active=True, trust="owner_reviewed_not_independently_verified")
                for row in rows]

    def history(self, subject):
        subject = clean(subject, 200, "subject")
        return [dict(row) for row in self.connection.execute(
            "SELECT id,subject,text,source,kind,supersedes,active FROM evidence WHERE subject=? ORDER BY id",
            (subject,))]

    def clear(self, *, confirmed=False):
        if confirmed is not True:
            raise ValueError("Explicit deletion confirmation required.")
        # Delete complete history, not just active records. This does not
        # promise forensic erasure from SSDs, backups or external copies.
        with self.connection:
            self.connection.execute("BEGIN IMMEDIATE")
            self.connection.execute("PRAGMA defer_foreign_keys=ON")
            self.connection.execute("DELETE FROM evidence")
