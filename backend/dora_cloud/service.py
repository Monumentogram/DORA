"""Manual-only job orchestration with crash-safe reservations and cleanup outbox."""
import base64
import hashlib
import json
import math
import struct
import time
from dataclasses import dataclass
from decimal import Decimal, ROUND_CEILING

from .aws_adapter import ProviderError
from .errors import ServiceError


@dataclass(frozen=True)
class Policy:
    daily_reserve_usd: str = "0.25"
    per_minute_reserve_usd: str = "0.03"
    ancillary_per_attempt_usd: str = "0.01"

    def __post_init__(self):
        for value in (self.daily_reserve_usd, self.per_minute_reserve_usd, self.ancillary_per_attempt_usd):
            if not Decimal(value).is_finite() or Decimal(value) <= 0:
                raise ValueError("positive finite reserve required")
        if Decimal(self.daily_reserve_usd) > 5:
            raise ValueError("owner Alpha reserve ceiling is USD 5/day")


class Service:
    def __init__(self, store, provider, policy=None, clock=time.time):
        self.store, self.provider, self.clock = store, provider, clock
        self.policy = policy or Policy()

    def register(self, principal, body):
        db = self.store.db
        row = db.execute("SELECT * FROM recordings WHERE id=?", (body["recording_id"],)).fetchone()
        if row:
            if row["owner"] != principal["owner"]:
                raise ServiceError("NOT_FOUND", 404)
            if json.loads(row["binding"]) != body:
                raise ServiceError("SOURCE_CONFLICT", 409)
        else:
            if db.execute("SELECT 1 FROM tombstones WHERE id=?", (body["recording_id"],)).fetchone():
                raise ServiceError("IDENTITY_RETIRED", 409)
            db.execute("INSERT INTO recordings VALUES(?,?,?,?)", (body["recording_id"], principal["owner"], json.dumps(body), int(self.clock())))
        return {"recording_id": body["recording_id"]}

    def create(self, principal, body):
        old = self.store.job(body["action_id"])
        if old:
            self._owner(principal, old)
            if old["creation"] != body:
                raise ServiceError("ACTION_CONFLICT", 409)
            return self.view(old)
        if self.store.db.execute("SELECT 1 FROM tombstones WHERE id=?", (body["action_id"],)).fetchone():
            raise ServiceError("IDENTITY_RETIRED", 409)
        row = self.store.db.execute("SELECT * FROM recordings WHERE id=?", (body["recording_id"],)).fetchone()
        if not row or row["owner"] != principal["owner"]:
            raise ServiceError("NOT_FOUND", 404)
        if (body["consent_version"] != "dora-cloud-manual-v1" or body["consent_source"] != "MANUAL_USER_ACTION" or
                not 0 < body["consent_granted_at"] <= (self.clock()+60)*1000):
            raise ServiceError("CONSENT_INVALID", 403)
        self._active_quota(principal["owner"])
        day = int(self.clock()) // 86400
        count = self.store.db.execute("SELECT count(*) FROM jobs WHERE owner=? AND created>=? AND created<?",
                                     (principal["owner"], day*86400, (day+1)*86400)).fetchone()[0]
        if count >= 10:
            raise ServiceError("DAILY_JOB_LIMIT", 429)
        job = {"id": body["action_id"], "owner": principal["owner"], "installation": principal["id"],
               "creation": body, "binding": json.loads(row["binding"]), "created": int(self.clock()),
               "status": "UPLOADING", "uploaded_bytes": 0, "revoked": False, "deadline": None,
               "error_code": None, "known_failure": False, "attempt_count": 0, "validated": False,
               "uploaded_sha256": None, "completed_at": None}
        self.store.db.execute("INSERT INTO jobs VALUES(?,?,?,?)", (job["id"], job["owner"], job["created"], json.dumps(job)))
        return self.view(job)

    def _owner(self, principal, job):
        if not job or job["owner"] != principal["owner"] or job["installation"] != principal["id"]:
            raise ServiceError("NOT_FOUND", 404)

    def _grant_valid(self, job):
        row = self.store.db.execute("SELECT * FROM installations WHERE id=?", (job["installation"],)).fetchone()
        return bool(row and not row["revoked"] and row["owner"] == job["owner"] and
                    (row["trusted_at"] is not None or row["expires"] > self.clock()))

    def _consent(self, job):
        if job["revoked"] or not self._grant_valid(job):
            raise ServiceError("CONSENT_REVOKED", 403)
        if job["deadline"] is not None and self.clock() >= job["deadline"]:
            if job["status"] != "COMPLETED":
                self._cancel(job, "DEADLINE_EXCEEDED")
                raise ServiceError("DEADLINE_EXCEEDED", 403)

    def _active_quota(self, owner, excluding=None):
        for row in self.store.db.execute("SELECT payload FROM jobs WHERE owner=?", (owner,)):
            job = json.loads(row[0])
            if job["id"] != excluding and job["status"] in ("UPLOADING", "QUEUED", "IN_PROGRESS"):
                raise ServiceError("ACTIVE_JOB_LIMIT", 429)

    def audio(self, principal, job_id, body):
        job = self.store.job(job_id)
        self._owner(principal, job)
        self._consent(job)
        try:
            chunk = base64.b64decode(body["data_base64"], validate=True)
        except ValueError:
            raise ServiceError("INVALID_BASE64") from None
        if not 0 < len(chunk) <= 65536:
            raise ServiceError("CHUNK_SIZE", 413)
        offset = body["offset"]
        digest = hashlib.sha256(chunk).hexdigest()
        old = self.store.db.execute("SELECT * FROM chunks WHERE job=? AND offset=?", (job_id, offset)).fetchone()
        if old:
            if old["digest"] != digest or old["size"] != len(chunk) or old["final"] != int(body["final"]):
                raise ServiceError("CHUNK_CONFLICT", 409)
            if body["final"] and job["status"] == "UPLOADING" and not job["attempt_count"]:
                self._finalize(job)
            return self.view(job)
        if job["status"] != "UPLOADING" or offset != job["uploaded_bytes"]:
            raise ServiceError("UPLOAD_OFFSET", 409)
        total = job["binding"]["frames"] * 2 + 44
        if offset + len(chunk) > total:
            raise ServiceError("AUDIO_SIZE", 413)
        if body["final"] and offset + len(chunk) != total:
            raise ServiceError("INVALID_MEDIA")
        with self.store.transaction():
            if job["deadline"] is None:
                job["deadline"] = int(self.clock()) + 86400
            self.store.db.execute("INSERT INTO chunks VALUES(?,?,?,?,?,?)", (job_id, offset, len(chunk), digest,
                                  int(body["final"]), self.store.encrypt(f"audio:{job_id}:{offset}", chunk)))
            job["uploaded_bytes"] += len(chunk)
            self.store.save_job(job)
        if body["final"]:
            self._finalize(job)
        return self.view(job)

    def _finalize(self, job):
        audio = self._audio(job)
        try:
            self._validate_audio(job, audio)
        except ServiceError:
            job.update(status="FAILED", error_code="INVALID_MEDIA", known_failure=False)
            with self.store.transaction():
                self.store.save_job(job)
                self.store.erase_audio(job["id"])
            raise
        job.update(validated=True, uploaded_sha256=hashlib.sha256(audio).hexdigest())
        self.store.save_job(job)
        self._dispatch(job, audio)

    def _audio(self, job):
        return b"".join(self.store.decrypt(f"audio:{job['id']}:{c['offset']}", c["ciphertext"])
                        for c in self.store.chunks(job["id"]))

    def _validate_audio(self, job, audio):
        frames = job["binding"]["frames"]
        if not 8000 <= frames <= 28800000 or len(audio) != frames*2+44:
            raise ServiceError("INVALID_MEDIA")
        header = struct.pack("<4sI4s4sIHHIIHH4sI", b"RIFF", frames*2+36, b"WAVE", b"fmt ",
                             16, 1, 1, 16000, 32000, 2, 16, b"data", frames*2)
        if audio[:44] != header:
            raise ServiceError("INVALID_MEDIA")

    def _dispatch(self, job, audio):
        self._consent(job)
        self._active_quota(job["owner"], job["id"])
        if job["attempt_count"] >= 2:
            raise ServiceError("ATTEMPT_LIMIT", 429)
        seconds = max(15, math.ceil(job["binding"]["frames"] / 16000))
        reserve = (Decimal(seconds)/60 * Decimal(self.policy.per_minute_reserve_usd) +
                   Decimal(self.policy.ancillary_per_attempt_usd))
        micro = int((reserve*1000000).to_integral_value(rounding=ROUND_CEILING))
        day = int(self.clock()) // 86400
        total = self.store.db.execute("SELECT coalesce(sum(micro_usd),0) FROM reservations WHERE owner=? AND day=?",
                                     (job["owner"], day)).fetchone()[0]
        if Decimal(total + micro) > Decimal(self.policy.daily_reserve_usd)*1000000:
            raise ServiceError("DAILY_RESERVE_LIMIT", 429)
        number = job["attempt_count"] + 1
        attempt = {"job": job["id"], "number": number, "name": f"dora-{job['id']}-{number}",
                   "phase": "START_UNCERTAIN", "cleanup": True, "input_deleted": False,
                   "output_deleted": False, "job_deleted": False, "terminal": False}
        # Persist the reservation AND ambiguous intent before any external side effect.
        # A crash anywhere after here reconciles Get; it never repeats Start.
        with self.store.transaction():
            self.store.db.execute("INSERT INTO reservations VALUES(?,?,?,?,?)", (job["id"], number, job["owner"], day, micro))
            self.store.save_attempt(attempt)
            job.update(attempt_count=number, status="QUEUED", known_failure=False, error_code="START_UNCERTAIN")
            self.store.save_job(job)
        try:
            self.provider.put(attempt, audio)
            self._consent(job)
            state = self.provider.start(attempt, job)
            if state not in ("QUEUED", "IN_PROGRESS", "COMPLETED", "FAILED"):
                raise ProviderError("PROVIDER_INVALID_RESULT", uncertain=True)
            attempt["phase"] = "ACCEPTED"
            self.store.save_attempt(attempt)
            job.update(status=state if state in ("QUEUED", "IN_PROGRESS") else "QUEUED", error_code=None)
            self.store.save_job(job)
        except ProviderError as exc:
            job["error_code"] = exc.code
            self.store.save_job(job)

    def get(self, principal, job_id):
        job = self.store.job(job_id)
        self._owner(principal, job)
        if not job["revoked"]:
            self._consent(job)
        self._poll(job)
        return self.view(job)

    def retry(self, principal, job_id):
        job = self.store.job(job_id)
        self._owner(principal, job)
        self._consent(job)
        # Polling an active attempt cannot in the same request authorize a new paid attempt.
        if job["status"] in ("QUEUED", "IN_PROGRESS"):
            self._poll(job)
        elif job["status"] == "FAILED" and job["known_failure"] and job["attempt_count"] < 2:
            self._dispatch(job, self._audio(job))
        elif job["status"] == "UPLOADING" and job["validated"] and not job["attempt_count"]:
            self._dispatch(job, self._audio(job))
        return self.view(job)

    def cancel(self, principal, job_id):
        job = self.store.job(job_id)
        self._owner(principal, job)
        self._cancel(job, "CANCELLED_BY_OWNER")
        return self.view(job)

    def _cancel(self, job, code):
        job.update(revoked=True, status="CANCELLED", error_code=code, known_failure=False)
        with self.store.transaction():
            self.store.save_job(job)
            self.store.erase_audio(job["id"])
            self.store.db.execute("DELETE FROM results WHERE job=?", (job["id"],))
        self._cleanup(job)

    def _poll(self, job):
        if job["revoked"] or job["status"] in ("FAILED", "COMPLETED", "CANCELLED"):
            self._cleanup(job)
            return
        if not job["attempt_count"]:
            return
        attempt = self.store.attempts(job["id"])[-1]
        try:
            state = self.provider.status(attempt, job)
            if state is None:
                return
            if state in ("QUEUED", "IN_PROGRESS"):
                # Never regress observed IN_PROGRESS to QUEUED.
                if not (state == "QUEUED" and job["status"] == "IN_PROGRESS"):
                    job["status"] = state
                job["error_code"] = None
                self.store.save_job(job)
            elif state == "FAILED":
                job.update(status="FAILED", error_code="PROVIDER_FAILED", known_failure=True)
                attempt.update(terminal=True, phase="FAILED")
                with self.store.transaction():
                    self.store.save_job(job)
                    self.store.save_attempt(attempt)
                self._cleanup(job)
            elif state == "COMPLETED":
                self._consent(job)
                result = self.provider.result(attempt, job)
                self._consent(job)  # Download may span the original deadline/grant expiry.
                result.update(provider_id="amazon-transcribe", model=f"standard-batch:eu-central-1:{job['creation']['language']}",
                              provider_job_id=attempt["name"], source_digest=job["binding"]["source_digest"],
                              source_version=job["binding"]["source_version"], completed_at=int(self.clock()*1000))
                job.update(status="COMPLETED", error_code=None, completed_at=result["completed_at"])
                attempt.update(terminal=True, phase="COMPLETED")
                with self.store.transaction():
                    self.store.db.execute("INSERT INTO results VALUES(?,?)", (job["id"], self.store.encrypt(f"result:{job['id']}", json.dumps(result).encode())))
                    self.store.save_job(job)
                    self.store.save_attempt(attempt)
                    self.store.erase_audio(job["id"])
                self._cleanup(job)
            else:
                raise ProviderError("PROVIDER_INVALID_RESULT")
        except ProviderError as exc:
            job["error_code"] = exc.code
            self.store.save_job(job)

    def _cleanup(self, job):
        for attempt in self.store.attempts(job["id"]):
            if not attempt["cleanup"]:
                continue
            # Active jobs retain input until terminal/cancel/deadline. Each side effect
            # is idempotent; durable receipts may be repeated after a crash.
            if not job["revoked"] and not attempt["terminal"]:
                continue
            try:
                if not attempt["input_deleted"]:
                    self.provider.delete_input(attempt)
                    attempt["input_deleted"] = True
                    self.store.save_attempt(attempt)
                if not attempt["terminal"]:
                    state = self.provider.status(attempt, job)
                    # Absence after ambiguous Start is not conclusive: keep the outbox.
                    if state not in ("COMPLETED", "FAILED"):
                        self.provider.delete_output(attempt)
                        continue
                    attempt["terminal"] = True
                    self.store.save_attempt(attempt)
                if not attempt["output_deleted"]:
                    self.provider.delete_output(attempt)
                    attempt["output_deleted"] = True
                    self.store.save_attempt(attempt)
                if not attempt["job_deleted"]:
                    self.provider.delete_job(attempt)
                    attempt["job_deleted"] = True
                attempt["cleanup"] = False
                self.store.save_attempt(attempt)
            except ProviderError:
                # Outbox remains durable. Never claim erasure from failed calls.
                pass

    def view(self, job):
        row = self.store.db.execute("SELECT ciphertext FROM results WHERE job=?", (job["id"],)).fetchone()
        return {"id": job["id"], "status": job["status"], "uploaded_bytes": job["uploaded_bytes"],
                "retryable": bool(not job["revoked"] and ((job["status"] == "FAILED" and job["known_failure"] and job["attempt_count"] < 2) or
                                  job["status"] in ("UPLOADING", "QUEUED", "IN_PROGRESS"))),
                "error_code": job["error_code"], "cleanup_pending": any(a["cleanup"] for a in self.store.attempts(job["id"])),
                "result": json.loads(self.store.decrypt(f"result:{job['id']}", row[0])) if row and job["status"] == "COMPLETED" else None}

    def sweep(self):
        with self.store.serialized():
            now = int(self.clock())
            for row in self.store.db.execute("SELECT payload FROM jobs").fetchall():
                job = json.loads(row[0])
                if not job["revoked"] and (not self._grant_valid(job) or
                        (job["status"] != "COMPLETED" and now >= (job["deadline"] or job["created"]+86400))):
                    self._cancel(job, "DEADLINE_EXCEEDED" if self._grant_valid(job) else "GRANT_REVOKED")
                else:
                    self._poll(job)
                if now-job["created"] >= 30*86400:
                    self.store.db.execute("DELETE FROM results WHERE job=?", (job["id"],))
                    if not any(a["cleanup"] for a in self.store.attempts(job["id"])):
                        with self.store.transaction():
                            history = {"creation": job["creation"], "binding": job["binding"], "owner": job["owner"],
                                       "installation": job["installation"], "revoked": job["revoked"]}
                            self.store.db.execute("INSERT OR IGNORE INTO consent_history VALUES(?,?,?)", (job["id"], now+90*86400,
                                self.store.encrypt(f"consent:{job['id']}", json.dumps(history).encode())))
                            self.store.db.execute("INSERT OR IGNORE INTO tombstones VALUES(?,?)", (job["id"], now))
                            self.store.db.execute("DELETE FROM jobs WHERE id=?", (job["id"],))
                            self.store.db.execute("DELETE FROM attempts WHERE job=?", (job["id"],))
                            self.store.db.execute("DELETE FROM chunks WHERE job=?", (job["id"],))
            # Source bindings are authority records: preserve while any owning
            # installation remains active, then keep only bounded nonsecret history.
            for row in self.store.db.execute("SELECT * FROM recordings").fetchall():
                if self.store.db.execute("SELECT 1 FROM installations WHERE owner=? AND trusted_at IS NOT NULL AND revoked=0",
                                         (row["owner"],)).fetchone():
                    continue
                last = self.store.db.execute("""SELECT max(CASE WHEN trusted_at IS NOT NULL
                    THEN coalesce(revoked_at, expires) ELSE expires END)
                    FROM installations WHERE owner=?""", (row["owner"],)).fetchone()[0]
                if now >= max(last or row["created"], row["created"])+90*86400 and not self.store.db.execute(
                        "SELECT 1 FROM jobs WHERE owner=?", (row["owner"],)).fetchone():
                    with self.store.transaction():
                        self.store.db.execute("INSERT OR IGNORE INTO tombstones VALUES(?,?)", (row["id"], now))
                        self.store.db.execute("DELETE FROM recordings WHERE id=?", (row["id"],))
            for row in self.store.db.execute("SELECT * FROM installations WHERE trusted_at IS NULL AND expires<?", (now-90*86400,)).fetchall():
                if not self.store.db.execute("SELECT 1 FROM jobs WHERE owner=?", (row["owner"],)).fetchone():
                    with self.store.transaction():
                        self.store.db.execute("INSERT OR IGNORE INTO tombstones VALUES(?,?)", (row["id"], now))
                        self.store.db.execute("DELETE FROM installations WHERE id=?", (row["id"],))
            self.store.db.execute("DELETE FROM consent_history WHERE expires<=?", (now,))
            self.store.db.execute("DELETE FROM nonces WHERE expires<?", (now,))
            self.store.db.execute("DELETE FROM sessions WHERE expires<=?", (now,))
            self.store.db.execute("DELETE FROM reservations WHERE day<?", (now//86400-30,))
