"""Finite sequential Batch children sharing one source, reservation and first-byte clock."""
import json
import uuid
from decimal import Decimal, ROUND_CEILING

from .errors import ServiceError

MAX_FRAMES = 1_382_400_000
WINDOW_FRAMES = 28_800_000
POLICY = "cloud-balanced-1800-v1"


def ranges(frames):
    count = (frames+WINDOW_FRAMES-1)//WINDOW_FRAMES
    return [(frames*i//count, frames*(i+1)//count-frames*i//count) for i in range(count)]


class CompositeRuns:
    def __init__(self, service):
        self.service, self.store = service, service.store
        self.store.db.executescript("""
            CREATE TABLE IF NOT EXISTS composite_runs(id TEXT PRIMARY KEY, owner TEXT NOT NULL,
                created INTEGER NOT NULL, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS composite_parts(run TEXT NOT NULL, part INTEGER NOT NULL,
                child TEXT NOT NULL UNIQUE, first_frame INTEGER NOT NULL, frames INTEGER NOT NULL,
                PRIMARY KEY(run,part));
        """)

    def load(self, run_id):
        row = self.store.db.execute("SELECT payload FROM composite_runs WHERE id=?", (run_id,)).fetchone()
        return json.loads(row[0]) if row else None

    def save(self, run):
        self.store.db.execute("UPDATE composite_runs SET payload=? WHERE id=?", (json.dumps(run), run["id"]))

    def owned(self, principal, run_id):
        run = self.load(run_id)
        self.service._owner(principal, run)
        return run

    def reserve(self, frames):
        seconds = max(15, (frames+15999)//16000)
        policy = self.service.policy
        value = Decimal(seconds)/60*Decimal(policy.per_minute_reserve_usd)+Decimal(policy.ancillary_per_attempt_usd)
        return int((value*1_000_000).to_integral_value(rounding=ROUND_CEILING))

    def quote(self, principal, body):
        previous = self.load(body["action_id"])
        if previous:
            self.service._owner(principal, previous)
            if previous["creation"] != body:
                raise ServiceError("ACTION_CONFLICT", 409)
            self.consent(previous)
        parts = ranges(body["frames"])
        required = sum(self.reserve(count) for _, count in parts)
        day = int(self.service.clock())//86400
        spent = self.store.db.execute("SELECT coalesce(sum(micro_usd),0) FROM reservations WHERE owner=? AND day=?",
                                      (principal["owner"], day)).fetchone()[0]
        return {"enabled": self.service.policy.long_runs_enabled, "part_count": len(parts),
                "required_micro_usd": required, "available_micro_usd": max(0, int(Decimal(self.service.policy.daily_reserve_usd)*1_000_000)-spent),
                "affordable": previous is not None or spent+required <= Decimal(self.service.policy.daily_reserve_usd)*1_000_000,
                "window_policy": POLICY, "max_frames": MAX_FRAMES, "max_part_frames": WINDOW_FRAMES}

    def consent(self, run):
        if run["revoked"] or not self.service._grant_valid(run):
            raise ServiceError("CONSENT_REVOKED", 403)
        if (run["deadline"] is not None and self.service.clock() >= run["deadline"] and
                self.view(run)["status"] != "COMPLETED"):
            self.cancel_run(run, "DEADLINE_EXCEEDED")
            raise ServiceError("DEADLINE_EXCEEDED", 403)

    def create(self, principal, body):
        previous = self.load(body["action_id"])
        if previous:
            self.service._owner(principal, previous)
            if previous["creation"] != body:
                raise ServiceError("ACTION_CONFLICT", 409)
            self.consent(previous)
            return self.view(previous)
        if not self.service.policy.long_runs_enabled:
            raise ServiceError("LONG_RUNS_DISABLED", 403)
        if self.store.job(body["action_id"]) or self.store.db.execute("SELECT 1 FROM tombstones WHERE id=?", (body["action_id"],)).fetchone():
            raise ServiceError("IDENTITY_RETIRED", 409)
        if (body["consent_version"] != "dora-cloud-manual-v1" or body["consent_source"] != "MANUAL_USER_ACTION" or
                not 0 < body["consent_granted_at"] <= (self.service.clock()+60)*1000):
            raise ServiceError("CONSENT_INVALID", 403)
        self.service._active_quota(principal["owner"])
        quote = self.quote(principal, body)
        if not quote["affordable"]:
            raise ServiceError("AGGREGATE_RESERVE_LIMIT", 429)
        now = int(self.service.clock())
        day = now//86400
        count = self.store.db.execute("SELECT count(*) FROM composite_runs WHERE owner=? AND created>=? AND created<?",
                                     (principal["owner"], day*86400, (day+1)*86400)).fetchone()[0]
        ordinary = self.store.db.execute("SELECT count(*) FROM jobs WHERE owner=? AND created>=? AND created<? "
            "AND id NOT IN (SELECT child FROM composite_parts)", (principal["owner"], day*86400, (day+1)*86400)).fetchone()[0]
        if count+ordinary >= 10:
            raise ServiceError("DAILY_JOB_LIMIT", 429)
        binding = {key: body[key] for key in ("recording_id", "source_digest", "source_version", "frames")}
        run = {"id": body["action_id"], "owner": principal["owner"], "installation": principal["id"],
               "creation": body, "binding": binding, "created": now, "revoked": False, "deadline": None,
               "status": "UPLOADING", "reserved_micro_usd": quote["required_micro_usd"]}
        with self.store.transaction():
            self.service.register(principal, binding)
            self.store.db.execute("INSERT INTO composite_runs VALUES(?,?,?,?)", (run["id"], run["owner"], now, json.dumps(run)))
            # Number zero is the aggregate admission hold; child attempt one consumes it.
            self.store.db.execute("INSERT INTO reservations VALUES(?,?,?,?,?)", (run["id"], 0, run["owner"], day, quote["required_micro_usd"]))
            for index, (first, frames) in enumerate(ranges(body["frames"])):
                child = str(uuid.uuid5(uuid.UUID(run["id"]), f"{POLICY}:{index}:{first}:{frames}"))
                self.store.db.execute("INSERT INTO composite_parts VALUES(?,?,?,?,?)", (run["id"], index, child, first, frames))
        return self.view(run)

    def get(self, principal, run_id):
        run = self.owned(principal, run_id)
        if not run["revoked"]:
            self.consent(run)
        return self.view(run)

    def part(self, principal, run_id, index):
        run = self.owned(principal, run_id)
        self.consent(run)
        if not self.service.policy.long_runs_enabled:
            raise ServiceError("LONG_RUNS_DISABLED", 403)
        row = self.store.db.execute("SELECT * FROM composite_parts WHERE run=? AND part=?", (run_id, index)).fetchone()
        if not row:
            raise ServiceError("NOT_FOUND", 404)
        for prior in self.store.db.execute("SELECT child FROM composite_parts WHERE run=? AND part<?", (run_id, index)):
            job = self.store.job(prior[0])
            if not job or job["status"] != "COMPLETED":
                raise ServiceError("PART_ORDER", 409)
        job = self.store.job(row["child"])
        if job:
            return self.service.view(job)
        job = {"id": row["child"], "owner": run["owner"], "installation": run["installation"],
               "creation": run["creation"], "binding": {**run["binding"], "frames": row["frames"]},
               "created": int(self.service.clock()), "status": "UPLOADING", "uploaded_bytes": 0,
               "revoked": False, "deadline": run["deadline"], "error_code": None, "known_failure": False,
               "attempt_count": 0, "validated": False, "uploaded_sha256": None, "completed_at": None,
               "parent_id": run_id, "part_index": index, "first_frame": row["first_frame"]}
        self.store.db.execute("INSERT INTO jobs VALUES(?,?,?,?)", (job["id"], job["owner"], job["created"], json.dumps(job)))
        return self.service.view(job)

    def first_byte(self, job):
        run = self.load(job["parent_id"])
        if run["deadline"] is None:
            run["deadline"] = int(self.service.clock())+86400
            self.save(run)
        job["deadline"] = run["deadline"]

    def active(self, owner, excluding=None):
        for row in self.store.db.execute("SELECT payload FROM composite_runs WHERE owner=?", (owner,)):
            run = json.loads(row[0])
            if run["id"] != excluding and not run["revoked"] and self.view(run)["status"] != "COMPLETED":
                raise ServiceError("ACTIVE_JOB_LIMIT", 429)

    def view(self, run):
        parts = []
        for row in self.store.db.execute("SELECT * FROM composite_parts WHERE run=? ORDER BY part", (run["id"],)):
            job = self.store.job(row["child"])
            parts.append({"index": row["part"], "id": row["child"], "first_frame": row["first_frame"],
                          "frames": row["frames"], "status": job["status"] if job else "PENDING"})
        status = "CANCELLED" if run["revoked"] else "COMPLETED" if all(p["status"] == "COMPLETED" for p in parts) else "PARTIAL" if any(p["status"] == "COMPLETED" for p in parts) else "UPLOADING"
        return {"id": run["id"], "status": status, "deadline": run["deadline"], "parts": parts,
                "reserved_micro_usd": run["reserved_micro_usd"], "window_policy": POLICY,
                "source_digest": run["binding"]["source_digest"], "source_version": run["binding"]["source_version"],
                "frames": run["binding"]["frames"]}

    def cancel(self, principal, run_id):
        run = self.owned(principal, run_id)
        self.cancel_run(run, "CANCELLED_BY_OWNER")
        return self.view(run)

    def cancel_run(self, run, code, cleanup=True):
        children = []
        keep_completed = code == "DEADLINE_EXCEEDED" and self.service._grant_valid(run)
        # Parent and every child fence are one durable transition. External cleanup
        # follows the commit and remains recoverable in each attempt's outbox.
        with self.store.transaction():
            if not run["revoked"] or run.get("cancel_reason") != code:
                run.update(revoked=True, status="CANCELLED", cancel_reason=code)
                self.save(run)
            for row in self.store.db.execute("SELECT child FROM composite_parts WHERE run=?", (run["id"],)):
                job = self.store.job(row[0])
                if not job:
                    continue
                children.append(job)
                if keep_completed and job["status"] == "COMPLETED":
                    continue
                if not job["revoked"] or job["status"] != "CANCELLED":
                    job.update(revoked=True, status="CANCELLED", error_code=code, known_failure=False)
                    self.store.save_job(job)
                    self.store.erase_audio(job["id"])
                    self.store.db.execute("DELETE FROM results WHERE job=?", (job["id"],))
        if cleanup:
            for job in children:
                self.service._cleanup(job)

    def sweep(self):
        now = int(self.service.clock())
        for row in self.store.db.execute("SELECT payload FROM composite_runs").fetchall():
            run = json.loads(row[0])
            grant_valid = self.service._grant_valid(run)
            if run["revoked"] or not grant_valid:
                reason = run.get("cancel_reason", "CANCELLED_BY_OWNER") if grant_valid else "GRANT_REVOKED"
                self.cancel_run(run, reason, cleanup=False)
            elif now >= (run["deadline"] or run["created"]+86400) and self.view(run)["status"] != "COMPLETED":
                self.cancel_run(run, "DEADLINE_EXCEEDED", cleanup=False)
            if now-run["created"] >= 30*86400 and not self.store.db.execute(
                    "SELECT 1 FROM jobs WHERE id IN (SELECT child FROM composite_parts WHERE run=?)", (run["id"],)).fetchone():
                with self.store.transaction():
                    self.store.db.execute("INSERT OR IGNORE INTO tombstones VALUES(?,?)", (run["id"], now))
                    self.store.db.execute("DELETE FROM composite_parts WHERE run=?", (run["id"],))
                    self.store.db.execute("DELETE FROM composite_runs WHERE id=?", (run["id"],))
