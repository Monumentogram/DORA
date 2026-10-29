"""Permanent authority with real P-256 proofs, injected clocks and durable SQLite."""
from contextlib import closing
import sqlite3
import unittest
import uuid
from datetime import datetime, timezone

from cryptography.hazmat.primitives import serialization
from fastapi.testclient import TestClient

from dora_cloud.auth import Auth
from dora_cloud.http_server import create_app
from dora_cloud.service import Service
from dora_cloud.store import Store
import test_service as fixtures


def epoch(date):
    return int(datetime.fromisoformat(date).replace(tzinfo=timezone.utc).timestamp())


class PermanentAccessTests(unittest.TestCase):
    setUp = fixtures.ServiceTests.setUp
    tearDown = fixtures.ServiceTests.tearDown
    invite = fixtures.ServiceTests.invite
    proof = fixtures.ServiceTests.proof
    request = fixtures.ServiceTests.request
    create = fixtures.ServiceTests.create
    upload = fixtures.ServiceTests.upload
    get = fixtures.ServiceTests.get

    def trust(self, install=None, owner="owner-a", recover_expired=False):
        install = install or self.install
        public = self.identities[install].public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.auth.trust(install, owner, public, recover_expired=recover_expired)

    def restart(self):
        self.client.close()
        self.store.close()
        self.store = Store(self.path, self.key)
        self.auth = Auth(self.store, self.clock)
        self.service = Service(self.store, self.aws, self.policy, self.clock)
        self.service.sweep()
        self.client = TestClient(create_app(self.service, self.auth), base_url="https://test")

    def test_future_dates_full_cloud_cycle_and_restart(self):
        self.now[0] = epoch("2026-10-29")
        expiry = epoch("2026-11-04T23:59:59")
        self.store.db.execute("UPDATE installations SET expires=?", (expiry,))
        self.trust()
        dates = ("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05",
                 "2027-01-01", "2030-01-01", "2100-01-01")
        for date in dates:
            with self.subTest(date=date):
                self.now[0] = epoch(date)
                self.restart()
                response = self.request("POST", "/v1/session", {})
                self.assertEqual(200, response.status_code, response.text)
                session = response.json()
                self.assertEqual(self.now[0] + 900, session["expires_at"])
                self.tokens[self.install] = session["access_token"]
                # A new client session after restart uses the same public identity.
                self.action = str(uuid.uuid4())
                self.creation.update(action_id=self.action, consent_granted_at=self.now[0]*1000)
                self.create()
                self.assertEqual(200, self.upload().status_code)
                attempt = self.store.attempts(self.action)[0]
                self.aws.jobs[attempt["name"]] = "COMPLETED"
                result = self.get().json()
                self.assertEqual("COMPLETED", result["status"])
                self.assertEqual("Synthetic test.", result["result"]["text"])
                self.restart()
                self.assertEqual(result, self.get().json())
                row = self.store.db.execute("SELECT * FROM installations WHERE id=?", (self.install,)).fetchone()
                self.assertEqual(expiry, row["expires"])
                self.assertIsNotNone(row["trusted_at"])
                self.assertEqual(1, self.store.db.execute("SELECT count(*) FROM recordings WHERE id=?", (self.recording,)).fetchone()[0])

    def test_permanent_authority_keeps_proof_session_and_consent_controls(self):
        self.trust()
        self.now[0] = epoch("2100-01-01")
        self.tokens[self.install] = self.request("POST", "/v1/session", {}).json()["access_token"]
        body = b"{}"
        headers = self.proof("POST", "/v1/session", body)
        self.assertEqual(401, self.client.post("/v1/session", content=b"{ }", headers=headers).status_code)
        self.assertEqual(200, self.client.post("/v1/session", content=body, headers=headers).status_code)
        self.restart()
        self.assertEqual(401, self.client.post("/v1/session", content=body, headers=headers).status_code)
        for delta in (-61, 61):
            self.assertEqual(401, self.request("POST", "/v1/session", {}, timestamp=self.now[0]+delta).status_code)
        unknown = self.proof("POST", "/v1/session", body)
        unknown["X-Dora-Installation"] = str(uuid.uuid4())
        self.assertEqual(401, self.client.post("/v1/session", content=body, headers=unknown).status_code)
        self.tokens[self.install] = self.request("POST", "/v1/session", {}).json()["access_token"]
        self.assertEqual(200, self.request("POST", "/v1/recordings", self.binding).status_code)
        for change in ({"consent_source": "ALWAYS"}, {"consent_granted_at": 0}):
            self.assertEqual(403, self.request("POST", "/v1/jobs", {**self.creation, **change}).status_code)
        self.now[0] += 900
        self.assertEqual(401, self.request("POST", "/v1/recordings", self.binding).status_code)
        self.assertEqual(200, self.request("POST", "/v1/session", {}).status_code)
        self.auth.revoke(self.install)
        self.restart()
        self.assertEqual(401, self.request("POST", "/v1/session", {}).status_code)
        self.assertEqual(401, self.request("POST", "/v1/jobs", self.creation).status_code)
        with self.assertRaises(ValueError):
            self.trust(recover_expired=True)
        self.assertEqual([], self.aws.starts)

    def test_migration_pins_owner_key_is_idempotent_and_expiry_needs_owner_recovery(self):
        before = dict(self.store.db.execute("SELECT * FROM installations WHERE id=?", (self.install,)).fetchone())
        with self.assertRaises(ValueError):
            self.trust(owner="different-owner")
        other_key = self.identities[self.other].public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
        with self.assertRaises(ValueError):
            self.auth.trust(self.install, "owner-a", other_key)
        with self.assertRaises(ValueError):
            self.auth.trust(str(uuid.uuid4()), "owner-a", other_key, recover_expired=True)
        self.now[0] = before["expires"]
        with self.assertRaises(ValueError):
            self.trust()
        self.assertEqual(401, self.request("POST", "/v1/session", {}).status_code)
        self.trust(recover_expired=True)
        trusted_at = self.store.db.execute("SELECT trusted_at FROM installations WHERE id=?", (self.install,)).fetchone()[0]
        self.now[0] += 1
        self.trust()
        after = dict(self.store.db.execute("SELECT * FROM installations WHERE id=?", (self.install,)).fetchone())
        self.assertEqual(trusted_at, after.pop("trusted_at"))
        before.pop("trusted_at")
        self.assertEqual(before, after)
        self.assertEqual(200, self.request("POST", "/v1/session", {}).status_code)

    def test_old_schema_upgrade_never_implicitly_trusts_an_installation(self):
        self.client.close()
        self.store.close()
        with closing(sqlite3.connect(self.path)) as db:
            db.execute("ALTER TABLE installations DROP COLUMN trusted_at")
            db.execute("ALTER TABLE installations DROP COLUMN revoked_at")
        self.store = Store(self.path, self.key)
        self.auth = Auth(self.store, self.clock)
        rows = self.store.db.execute("SELECT trusted_at FROM installations").fetchall()
        self.assertTrue(all(row[0] is None for row in rows))
        self.trust()
        self.restart()
        self.assertIsNotNone(self.store.db.execute("SELECT trusted_at FROM installations WHERE id=?", (self.install,)).fetchone()[0])

    def test_revoked_permanent_job_is_fenced_and_cleaned(self):
        self.trust()
        self.create()
        self.assertEqual(200, self.upload().status_code)
        self.auth.revoke(self.install)
        self.service.sweep()
        self.assertTrue(self.store.job(self.action)["revoked"])
        self.assertEqual([], self.store.chunks(self.action))
        self.assertEqual(401, self.get().status_code)

    def test_source_binding_history_retains_90_days_after_late_revocation(self):
        self.trust()
        self.assertEqual(200, self.request("POST", "/v1/recordings", self.binding).status_code)
        self.now[0] += 365 * 86400
        self.service.sweep()
        self.auth.revoke(self.install)
        retired_at = self.now[0]
        for days in (0, 89):
            self.now[0] = retired_at + days * 86400
            self.auth.revoke(self.install)  # Repeating revoke must not extend retention.
            self.restart()
            self.assertEqual(1, self.store.db.execute("SELECT count(*) FROM recordings WHERE id=?", (self.recording,)).fetchone()[0])
        self.now[0] = retired_at + 90 * 86400
        self.service.sweep()
        self.assertEqual(0, self.store.db.execute("SELECT count(*) FROM recordings WHERE id=?", (self.recording,)).fetchone()[0])
        self.assertIsNotNone(self.store.db.execute("SELECT 1 FROM tombstones WHERE id=?", (self.recording,)).fetchone())


if __name__ == "__main__":
    unittest.main()
