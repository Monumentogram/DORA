"""Private bounded SQLite attempt journal. One process owner, durable transitions.

Caller supplies an existing private directory outside every Git checkout. No raw
content, model path or device serial is stored. This is not production persistence.
"""
from __future__ import annotations

import os
from pathlib import Path
import sqlite3

SCHEMA = ("CREATE TABLE attempts (case_id TEXT NOT NULL, ordinal INTEGER NOT NULL, "
          "request TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('RUNNING','FINISHED')), "
          "result TEXT, PRIMARY KEY(case_id,ordinal))")


class AttemptStore:
    def __init__(self, path):
        from alpha_asr_runner import (RunnerError, require, parse_request, validate_record,
                                      validate_request, canonical, MAX_ATTEMPTS)
        self.db = None
        self.lock = None
        self.closed = False
        require(not Path(path).is_symlink(), "PRIVATE_DIRECTORY_REQUIRED")
        path = Path(path).resolve()
        require(path.parent.is_dir(), "PRIVATE_DIRECTORY_REQUIRED")
        require(not any((p / ".git").exists() for p in path.parents), "PRIVATE_DIRECTORY_REQUIRED")
        require(not path.is_symlink(), "PRIVATE_DIRECTORY_REQUIRED")
        try:
            self.lock = open(str(path) + ".lock", "a+b")
            self.lock.seek(0)
            if os.name == "nt":
                import msvcrt
                try:
                    msvcrt.locking(self.lock.fileno(), msvcrt.LK_NBLCK, 1)
                except OSError:
                    raise RunnerError("STORE_BUSY") from None
            else:
                import fcntl
                try:
                    fcntl.flock(self.lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                except OSError:
                    raise RunnerError("STORE_BUSY") from None
            self.db = sqlite3.connect(path, timeout=0)
            self.db.execute("PRAGMA synchronous=FULL")
            version = self.db.execute("PRAGMA user_version").fetchone()[0]
            if version == 0:
                require(not self.db.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall(),
                        "PERSISTENCE_SCHEMA_MISMATCH")
                with self.db:
                    self.db.execute(SCHEMA)
                    self.db.execute("PRAGMA user_version=1")
            else:
                require(version == 1, "PERSISTENCE_SCHEMA_MISMATCH")
            columns = self.db.execute("PRAGMA table_info(attempts)").fetchall()
            require([x[1] for x in columns] == ["case_id", "ordinal", "request", "state", "result"],
                    "PERSISTENCE_SCHEMA_MISMATCH")
            require(self.db.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name='attempts'").fetchone()
                    == (SCHEMA,), "PERSISTENCE_SCHEMA_MISMATCH")
            rows = self.db.execute("SELECT case_id,ordinal,request,state,result FROM attempts ORDER BY case_id,ordinal").fetchall()
            require(len(rows) <= MAX_ATTEMPTS, "ATTEMPT_LIMIT")
            previous = {}
            for case_id, ordinal, encoded, state, result in rows:
                request = parse_request(encoded)
                require(type(request) is dict, "INVALID_RECORD")
                validate_request(request, request.get("nativeCandidateSha256"))
                require(request["caseId"] == case_id and request["attemptOrdinal"] == ordinal,
                        "INVALID_RECORD")
                require(ordinal == previous.get(case_id, 0) + 1, "INVALID_ATTEMPT_SEQUENCE")
                previous[case_id] = ordinal
                if state == "RUNNING":
                    require(result is None, "INVALID_RECORD")
                    self.finish(request, "ENGINE_FAILURE")
                else:
                    require(state == "FINISHED", "INVALID_RECORD")
                    record = parse_request(result)
                    validate_record(record)
                    require(canonical(record["request"]) == canonical(request), "INVALID_RECORD")
        except RunnerError:
            self.close()
            raise
        except (OSError, sqlite3.Error, ValueError):
            self.close()
            raise RunnerError("PERSISTENCE_FAILURE") from None

    def close(self):
        if self.closed:
            return
        self.closed = True
        if self.db is not None:
            self.db.close()
        if self.lock is not None:
            self.lock.close()  # OS releases process ownership, including after a crash.

    def count(self):
        from alpha_asr_runner import RunnerError
        try:
            return self.db.execute("SELECT COUNT(*) FROM attempts").fetchone()[0]
        except sqlite3.Error:
            raise RunnerError("PERSISTENCE_FAILURE") from None

    def begin(self, request):
        from alpha_asr_runner import RunnerError, require, canonical, parse_request, MAX_ATTEMPTS
        try:
            encoded = canonical(request)
            key = (request["caseId"], request["attemptOrdinal"])
            with self.db:
                self.db.execute("BEGIN IMMEDIATE")
                row = self.db.execute("SELECT request,state,result FROM attempts WHERE case_id=? AND ordinal=?", key).fetchone()
                if row:
                    require(row[0] == encoded, "REPLAY_MISMATCH")
                    require(row[1] == "FINISHED", "ATTEMPT_IN_PROGRESS")
                    return parse_request(row[2])
                require(self.count() < MAX_ATTEMPTS, "ATTEMPT_LIMIT")
                last = self.db.execute("SELECT COALESCE(MAX(ordinal),0) FROM attempts WHERE case_id=?", key[:1]).fetchone()[0]
                require(key[1] == last + 1, "INVALID_ATTEMPT_SEQUENCE")
                self.db.execute("INSERT INTO attempts VALUES (?,?,?,'RUNNING',NULL)", (*key, encoded))
            return None
        except sqlite3.Error:
            raise RunnerError("PERSISTENCE_FAILURE") from None

    def finish(self, request, outcome):
        from alpha_asr_runner import RunnerError, require, canonical, attempt_record
        result = attempt_record(request, outcome)
        try:
            with self.db:
                updated = self.db.execute("UPDATE attempts SET state='FINISHED',result=? "
                                         "WHERE case_id=? AND ordinal=? AND request=? AND state='RUNNING'",
                                         (canonical(result), request["caseId"], request["attemptOrdinal"], canonical(request)))
                require(updated.rowcount == 1, "ALREADY_FINALIZED")
            return result
        except sqlite3.Error:
            raise RunnerError("PERSISTENCE_FAILURE") from None
