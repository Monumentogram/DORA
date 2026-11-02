"""Durable serialized ledger. Never put plaintext audio/results in SQLite."""
import contextlib
import json
import os
import sqlite3
import threading
from pathlib import Path

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class Store:
    def __init__(self, path, key):
        if len(key) != 32:
            raise ValueError("32-byte custody key required")
        self.path = Path(path).resolve()
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.cipher = AESGCM(key)
        self._mutex = threading.RLock()
        self._local = threading.local()
        self._lock = open(str(self.path) + ".lock", "a+b")
        if self._lock.seek(0, 2) == 0:
            self._lock.write(b"0")
            self._lock.flush()
        self.db = sqlite3.connect(self.path, timeout=30, isolation_level=None, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        custody_mismatch = False
        with self.serialized():
            self.db.executescript("""
                PRAGMA journal_mode=WAL;
                PRAGMA synchronous=FULL;
                PRAGMA secure_delete=ON;
                CREATE TABLE IF NOT EXISTS installations(
                    id TEXT PRIMARY KEY, owner TEXT NOT NULL, public_key BLOB NOT NULL,
                    expires INTEGER NOT NULL, revoked INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE IF NOT EXISTS nonces(
                    installation TEXT NOT NULL, nonce TEXT NOT NULL, expires INTEGER NOT NULL,
                    PRIMARY KEY(installation,nonce));
                CREATE TABLE IF NOT EXISTS sessions(
                    token_hash TEXT PRIMARY KEY, installation TEXT NOT NULL, expires INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS recordings(
                    id TEXT PRIMARY KEY, owner TEXT NOT NULL, binding TEXT NOT NULL, created INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS jobs(
                    id TEXT PRIMARY KEY, owner TEXT NOT NULL, created INTEGER NOT NULL, payload TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS chunks(
                    job TEXT NOT NULL, offset INTEGER NOT NULL, size INTEGER NOT NULL,
                    digest TEXT NOT NULL, final INTEGER NOT NULL, ciphertext BLOB NOT NULL,
                    PRIMARY KEY(job,offset));
                CREATE TABLE IF NOT EXISTS attempts(
                    job TEXT NOT NULL, number INTEGER NOT NULL, payload TEXT NOT NULL,
                    PRIMARY KEY(job,number));
                CREATE TABLE IF NOT EXISTS reservations(
                    job TEXT NOT NULL, number INTEGER NOT NULL, owner TEXT NOT NULL,
                    day INTEGER NOT NULL, micro_usd INTEGER NOT NULL, PRIMARY KEY(job,number));
                CREATE TABLE IF NOT EXISTS results(job TEXT PRIMARY KEY, ciphertext BLOB NOT NULL);
                CREATE TABLE IF NOT EXISTS consent_history(id TEXT PRIMARY KEY, expires INTEGER NOT NULL, ciphertext BLOB NOT NULL);
                CREATE TABLE IF NOT EXISTS tombstones(id TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS custody(singleton INTEGER PRIMARY KEY CHECK(singleton=1), ciphertext BLOB NOT NULL);
                CREATE TABLE IF NOT EXISTS namespace(singleton INTEGER PRIMARY KEY CHECK(singleton=1), binding TEXT NOT NULL);
            """)
            # Additive migration: legacy finite invitations are never implicitly trusted.
            columns = {row[1] for row in self.db.execute("PRAGMA table_info(installations)")}
            if "trusted_at" not in columns:
                self.db.execute("ALTER TABLE installations ADD COLUMN trusted_at INTEGER")
            if "revoked_at" not in columns:
                self.db.execute("ALTER TABLE installations ADD COLUMN revoked_at INTEGER")
            existing = self.db.execute("SELECT ciphertext FROM custody WHERE singleton=1").fetchone()
            if existing:
                try:
                    custody_mismatch = self.decrypt("custody-check-v1", existing[0]) != b"DORA-CUSTODY-V1"
                except InvalidTag:
                    custody_mismatch = True
            else:
                self.db.execute("INSERT INTO custody VALUES(1,?)", (self.encrypt("custody-check-v1", b"DORA-CUSTODY-V1"),))
        if custody_mismatch:
            self.close()
            raise ValueError("custody key mismatch")

    @contextlib.contextmanager
    def serialized(self):
        """Share a process lock with the independent watchdog and invitation CLI."""
        with self._mutex:
            depth = getattr(self._local, "depth", 0)
            if depth == 0:
                self._lock.seek(0)
                if os.name == "nt":
                    import msvcrt
                    msvcrt.locking(self._lock.fileno(), msvcrt.LK_LOCK, 1)
                else:
                    import fcntl
                    fcntl.flock(self._lock.fileno(), fcntl.LOCK_EX)
            self._local.depth = depth + 1
            try:
                yield
            finally:
                self._local.depth -= 1
                if depth == 0:
                    self._lock.seek(0)
                    if os.name == "nt":
                        import msvcrt
                        msvcrt.locking(self._lock.fileno(), msvcrt.LK_UNLCK, 1)
                    else:
                        import fcntl
                        fcntl.flock(self._lock.fileno(), fcntl.LOCK_UN)

    @contextlib.contextmanager
    def transaction(self):
        self.db.execute("BEGIN IMMEDIATE")
        try:
            yield
            self.db.execute("COMMIT")
        except BaseException:
            self.db.execute("ROLLBACK")
            raise

    def encrypt(self, identity, value):
        nonce = os.urandom(12)
        return nonce + self.cipher.encrypt(nonce, value, identity.encode())

    def bind_namespace(self, binding):
        with self.serialized():
            existing = self.db.execute("SELECT binding FROM namespace WHERE singleton=1").fetchone()
            if existing and json.loads(existing[0]) != binding:
                raise ValueError("ledger destination cannot change")
            if not existing:
                self.db.execute("INSERT INTO namespace VALUES(1,?)", (json.dumps(binding, sort_keys=True),))

    def decrypt(self, identity, value):
        return self.cipher.decrypt(value[:12], value[12:], identity.encode())

    def job(self, job_id):
        row = self.db.execute("SELECT payload FROM jobs WHERE id=?", (job_id,)).fetchone()
        return json.loads(row[0]) if row else None

    def save_job(self, job):
        self.db.execute("UPDATE jobs SET payload=? WHERE id=?", (json.dumps(job), job["id"]))

    def chunks(self, job_id):
        return self.db.execute("SELECT * FROM chunks WHERE job=? AND length(ciphertext)>0 ORDER BY offset", (job_id,)).fetchall()

    def iter_chunks(self, job_id):
        return self.db.execute("SELECT * FROM chunks WHERE job=? AND length(ciphertext)>0 ORDER BY offset", (job_id,))

    def erase_audio(self, job_id):
        # Keep bounded nonsecret receipts for duplicate upload acknowledgments.
        self.db.execute("UPDATE chunks SET ciphertext=X'' WHERE job=?", (job_id,))

    def attempts(self, job_id):
        return [json.loads(r[0]) for r in self.db.execute(
            "SELECT payload FROM attempts WHERE job=? ORDER BY number", (job_id,))]

    def save_attempt(self, attempt):
        self.db.execute("INSERT INTO attempts VALUES(?,?,?) ON CONFLICT(job,number) DO UPDATE SET payload=excluded.payload",
                        (attempt["job"], attempt["number"], json.dumps(attempt)))

    def close(self):
        self.db.close()
        self._lock.close()
