"""Synthetic HTTP integration: real keys, signatures, SQLite and encryption."""
import base64
import hashlib
import io
import json
import tempfile
import unittest
import uuid
import wave
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from dora_cloud.auth import Auth
from dora_cloud.http_server import create_app
from dora_cloud.service import Service, Policy
from dora_cloud.store import Store
from dora_cloud.aws_adapter import ProviderError


def wav(frames=8000, rate=16000):
    f = io.BytesIO()
    with wave.open(f, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"\x00\x01" * frames)
    return f.getvalue()


class FakeAWS:
    """Only the external service boundary is replaced; no production bypass."""
    def __init__(self):
        self.jobs = {}
        self.inputs = {}
        self.starts = []
        self.puts = []
        self.deleted = []
        self.start_error = None
        self.poll_error = None
        self.cleanup_error = False

    def put(self, attempt, audio):
        self.inputs[attempt["name"]] = audio
        self.puts.append(attempt["name"])

    def start(self, attempt, job):
        self.starts.append(attempt["name"])
        self.jobs[attempt["name"]] = "IN_PROGRESS"
        if self.start_error:
            raise self.start_error
        return "IN_PROGRESS"

    def status(self, attempt, job):
        if self.poll_error:
            raise self.poll_error
        return self.jobs.get(attempt["name"])

    def result(self, attempt, job):
        return {"text": "Synthetic test.", "segments": [
            {"text": "Synthetic", "start_ms": None, "end_ms": None, "confidence": None}]}

    def delete_input(self, attempt):
        if self.cleanup_error:
            raise ProviderError("PROVIDER_UNAVAILABLE")
        self.inputs.pop(attempt["name"], None)

    def delete_output(self, attempt):
        if self.cleanup_error:
            raise ProviderError("PROVIDER_UNAVAILABLE")

    def delete_job(self, attempt):
        self.jobs.pop(attempt["name"], None)
        self.deleted.append(attempt["name"])


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "state.sqlite"
        self.key = b"k" * 32
        self.now = [1800000000]
        self.clock = lambda: self.now[0]
        self.aws = FakeAWS()
        self.store = Store(self.path, self.key)
        self.policy = Policy(daily_reserve_usd="1.00", per_minute_reserve_usd="0.03")
        self.service = Service(self.store, self.aws, self.policy, self.clock)
        self.auth = Auth(self.store, self.clock)
        self.client = TestClient(create_app(self.service, self.auth), base_url="https://test")
        self.identities = {}
        self.install = self.invite("owner-a")
        self.other = self.invite("owner-b")
        self.tokens = {}
        for install in (self.install, self.other):
            self.tokens[install] = self.request("POST", "/v1/session", {}, install).json()["access_token"]
        self.recording = str(uuid.uuid4())
        self.action = str(uuid.uuid4())
        self.binding = {"recording_id": self.recording, "source_digest": "a" * 64,
                        "source_version": 1, "frames": 8000}
        self.creation = {"action_id": self.action, "recording_id": self.recording,
                         "language": "ru-RU", "consent_version": "dora-cloud-manual-v1",
                         "consent_source": "MANUAL_USER_ACTION", "consent_granted_at": self.now[0] * 1000}

    def tearDown(self):
        self.client.close()
        self.store.close()
        self.tmp.cleanup()

    def invite(self, owner):
        install = str(uuid.uuid4())
        private = ec.generate_private_key(ec.SECP256R1())
        public = private.public_key().public_bytes(serialization.Encoding.PEM,
                                                   serialization.PublicFormat.SubjectPublicKeyInfo)
        self.identities[install] = private
        self.auth.invite(install, owner, public, self.now[0] + 1000000)
        return install

    def proof(self, method, path, body, install=None, nonce=None, timestamp=None):
        install = install or self.install
        time = str(timestamp if timestamp is not None else self.now[0])
        nonce = nonce or str(uuid.uuid4())
        message = f"DORA-ASR-V1\n{method}\n{path}\n{time}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
        signature = self.identities[install].sign(message, ec.ECDSA(hashes.SHA256()))
        headers = {"X-Dora-Installation": install, "X-Dora-Time": time,
                   "X-Dora-Nonce": nonce, "X-Dora-Signature": base64.b64encode(signature).decode(),
                   "Content-Type": "application/json"}
        if install in getattr(self, "tokens", {}):
            headers["Authorization"] = "Bearer " + self.tokens[install]
        return headers

    def request(self, method, path, payload=None, install=None, **kwargs):
        body = b"" if payload is None else json.dumps(payload, separators=(",", ":")).encode()
        return self.client.request(method, path, content=body,
                                   headers=self.proof(method, path, body, install, **kwargs))

    def create(self):
        self.assertEqual(200, self.request("POST", "/v1/recordings", self.binding).status_code)
        r = self.request("POST", "/v1/jobs", self.creation)
        self.assertEqual(200, r.status_code, r.text)
        return r.json()

    def upload(self, data=None, offset=0, final=True):
        return self.request("POST", f"/v1/jobs/{self.action}/audio",
                            {"offset": offset, "data_base64": base64.b64encode(data if data is not None else wav()).decode(),
                             "final": final})

    def get(self):
        return self.request("GET", f"/v1/jobs/{self.action}")

    def test_signature_tampering_replay_expiry_and_session_binding(self):
        path = "/v1/recordings"
        body = json.dumps(self.binding).encode()
        headers = self.proof("POST", path, body)
        self.assertEqual(200, self.client.post(path, content=body, headers=headers).status_code)
        self.assertEqual(401, self.client.post(path, content=body, headers=headers).status_code)
        headers = self.proof("POST", path, body)
        self.assertEqual(401, self.client.post(path, content=body + b" ", headers=headers).status_code)
        self.assertEqual(401, self.request("POST", path, self.binding, timestamp=self.now[0]-61).status_code)
        headers = self.proof("POST", path, body, self.other)
        headers["Authorization"] = "Bearer " + self.tokens[self.install]
        self.assertEqual(401, self.client.post(path, content=body, headers=headers).status_code)
        self.now[0] += 901
        self.assertEqual(401, self.request("POST", path, self.binding).status_code)
        self.assertEqual(200, self.request("POST", "/v1/session", {}).status_code)

    def test_recording_binding_is_immutable_and_cross_owner_denied(self):
        self.create()
        self.assertEqual(200, self.request("POST", "/v1/recordings", self.binding).status_code)
        self.assertEqual(409, self.request("POST", "/v1/recordings", {**self.binding, "source_digest": "b"*64}).status_code)
        self.assertEqual(404, self.request("POST", "/v1/recordings", self.binding, self.other).status_code)
        self.assertEqual(404, self.request("GET", f"/v1/jobs/{self.action}", install=self.other).status_code)
        self.assertEqual(404, self.request("POST", "/v1/jobs", {**self.creation, "action_id": str(uuid.uuid4())}, self.other).status_code)

    def test_consent_is_required_source_bound_and_old_persisted_consent_works(self):
        self.request("POST", "/v1/recordings", self.binding)
        for change in ({"consent_source": "ALWAYS"}, {"consent_version": "obsolete"},
                       {"consent_granted_at": (self.now[0] + 61)*1000}, {"consent_granted_at": 0}):
            self.assertIn(self.request("POST", "/v1/jobs", {**self.creation, **change}).status_code, (403, 422))
        missing = self.creation.copy()
        del missing["consent_source"]
        self.assertEqual(422, self.request("POST", "/v1/jobs", missing).status_code)
        self.creation["consent_granted_at"] -= 86400000
        self.assertEqual(200, self.request("POST", "/v1/jobs", self.creation).status_code)
        self.assertEqual([], self.aws.puts)

    def test_chunks_no_dispatch_until_complete_exact_idempotency_and_bounds(self):
        self.create()
        data = wav()
        r = self.upload(data[:100], final=False)
        self.assertEqual(100, r.json()["uploaded_bytes"])
        self.assertEqual([], self.aws.puts)
        self.assertEqual(100, self.upload(data[:100], final=False).json()["uploaded_bytes"])
        self.assertEqual(409, self.upload(b"bad", final=False).status_code)
        self.assertEqual(409, self.upload(data[101:], offset=101).status_code)
        self.assertEqual(200, self.upload(data[100:], offset=100).status_code)
        self.assertEqual(1, len(self.aws.starts))
        self.assertEqual(200, self.upload(data[100:], offset=100).status_code)
        self.assertEqual(1, len(self.aws.starts))

    def test_bad_wav_wrong_rate_short_oversize_and_nonfinal_complete_never_dispatch(self):
        for data, frames in ((wav(rate=8000), 8000), (wav()[:-2], 8000),
                             (wav(7999), 7999), (b"not-wav", 8000)):
            with self.subTest(frames=frames, size=len(data)):
                self.recording, self.action = str(uuid.uuid4()), str(uuid.uuid4())
                self.binding.update(recording_id=self.recording, frames=frames)
                self.creation.update(action_id=self.action, recording_id=self.recording)
                self.create()
                self.assertIn(self.upload(data).status_code, (400, 422))
                self.request("POST", f"/v1/jobs/{self.action}/cancel", {})
        self.assertEqual([], self.aws.puts)
        self.assertEqual(422, self.request("POST", "/v1/recordings", {**self.binding, "recording_id": str(uuid.uuid4()), "frames": 4800001}).status_code)

    def test_action_idempotency_and_one_active_owner(self):
        first = self.create()
        self.assertEqual(first, self.request("POST", "/v1/jobs", self.creation).json())
        self.assertEqual(409, self.request("POST", "/v1/jobs", {**self.creation, "language": "en-US"}).status_code)
        self.assertEqual(429, self.request("POST", "/v1/jobs", {**self.creation, "action_id": str(uuid.uuid4())}).status_code)

    def test_completed_result_is_encrypted_stable_and_survives_cleanup_restart(self):
        self.create()
        self.upload()
        name = self.aws.starts[0]
        self.aws.jobs[name] = "COMPLETED"
        result = self.get().json()
        self.assertEqual("COMPLETED", result["status"])
        self.assertEqual("a"*64, result["result"]["source_digest"])
        self.assertEqual("standard-batch:eu-central-1:ru-RU", result["result"]["model"])
        self.assertIsNone(result["result"]["segments"][0]["start_ms"])
        self.assertFalse(result["cleanup_pending"])
        self.assertEqual({}, self.aws.inputs)
        self.assertIn(name, self.aws.deleted)
        self.assertEqual(result, self.get().json())
        self.client.close()
        self.store.close()
        self.assertNotIn(b"Synthetic test.", self.path.read_bytes())
        self.assertNotIn(wav()[44:100], self.path.read_bytes())
        self.store = Store(self.path, self.key)
        self.service = Service(self.store, self.aws, self.policy, self.clock)
        self.auth = Auth(self.store, self.clock)
        self.client = TestClient(create_app(self.service, self.auth), base_url="https://test")
        self.assertEqual(result, self.get().json())

    def test_uncertain_start_poll_retry_never_resubmits(self):
        self.create()
        self.aws.start_error = ProviderError("PROVIDER_TIMEOUT", uncertain=True)
        self.assertEqual("QUEUED", self.upload().json()["status"])
        for _ in range(3):
            self.request("POST", f"/v1/jobs/{self.action}/retry", {})
        self.assertEqual(1, len(self.aws.starts))
        self.assertEqual("IN_PROGRESS", self.get().json()["status"])

    def test_absent_ambiguous_start_is_not_failure_or_permission_for_new_start(self):
        self.create()
        self.aws.start_error = ProviderError("PROVIDER_UNAVAILABLE", uncertain=True)
        self.upload()
        self.aws.jobs.clear()
        for _ in range(3):
            state = self.request("POST", f"/v1/jobs/{self.action}/retry", {}).json()
            self.assertEqual("QUEUED", state["status"])
        self.assertEqual(1, len(self.aws.starts))

    def test_only_known_failure_explicit_retry_and_two_attempt_cap(self):
        self.create()
        self.upload()
        self.aws.jobs[self.aws.starts[0]] = "FAILED"
        self.assertEqual("FAILED", self.get().json()["status"])
        self.service.sweep()
        self.assertEqual(1, len(self.aws.starts))
        self.request("POST", f"/v1/jobs/{self.action}/retry", {})
        self.request("POST", f"/v1/jobs/{self.action}/retry", {})
        self.assertEqual(2, len(self.aws.starts))
        self.aws.jobs[self.aws.starts[-1]] = "FAILED"
        self.assertFalse(self.get().json()["retryable"])
        self.request("POST", f"/v1/jobs/{self.action}/retry", {})
        self.assertEqual(2, len(self.aws.starts))

    def test_poll_transient_error_does_not_become_terminal(self):
        self.create()
        self.upload()
        for code in ("PROVIDER_TIMEOUT", "PROVIDER_RATE_LIMITED", "PROVIDER_UNAVAILABLE"):
            self.aws.poll_error = ProviderError(code)
            self.assertEqual("IN_PROGRESS", self.get().json()["status"])
        self.assertEqual(1, len(self.aws.starts))

    def test_cancel_fences_upload_and_late_results_cleanup_persists(self):
        self.create()
        self.upload()
        self.aws.cleanup_error = True
        state = self.request("POST", f"/v1/jobs/{self.action}/cancel", {}).json()
        self.assertEqual("CANCELLED", state["status"])
        self.assertTrue(state["cleanup_pending"])
        self.assertEqual(403, self.upload().status_code)
        self.aws.jobs[self.aws.starts[0]] = "COMPLETED"
        self.aws.cleanup_error = False
        self.service.sweep()
        state = self.request("POST", f"/v1/jobs/{self.action}/cancel", {}).json()
        self.assertIsNone(state["result"])
        self.assertFalse(state["cleanup_pending"])
        self.assertEqual({}, self.aws.inputs)

    def test_watchdog_deadline_and_revoked_installation_independent_of_polling(self):
        self.create()
        self.upload(wav()[:100], final=False)
        self.now[0] += 86401
        self.service.sweep()
        with self.store.serialized():
            job = self.store.job(self.action)
            self.assertEqual("CANCELLED", job["status"])
            self.assertEqual("DEADLINE_EXCEEDED", job["error_code"])
            self.assertEqual([], self.store.chunks(self.action))
        self.assertEqual([], self.aws.starts)

    def test_current_installation_expiry_and_revoke_deny_session(self):
        self.auth.revoke(self.install)
        self.assertEqual(401, self.request("POST", "/v1/session", {}).status_code)
        self.assertEqual(401, self.request("POST", "/v1/recordings", self.binding).status_code)

    def test_cost_reservation_blocks_dispatch_and_daily_actions_limit(self):
        self.service.policy = Policy(daily_reserve_usd="0.0001", per_minute_reserve_usd="0.03")
        self.create()
        self.assertEqual(429, self.upload().status_code)
        self.assertEqual([], self.aws.puts)
        self.request("POST", f"/v1/jobs/{self.action}/cancel", {})
        for _ in range(9):
            self.creation["action_id"] = str(uuid.uuid4())
            self.assertEqual(200, self.request("POST", "/v1/jobs", self.creation).status_code)
            self.request("POST", f"/v1/jobs/{self.creation['action_id']}/cancel", {})
        self.creation["action_id"] = str(uuid.uuid4())
        self.assertEqual(429, self.request("POST", "/v1/jobs", self.creation).status_code)

    def test_duplicate_parallel_final_only_starts_once(self):
        self.create()
        with ThreadPoolExecutor(max_workers=4) as pool:
            responses = list(pool.map(lambda _: self.upload(), range(4)))
        self.assertEqual([200]*4, [r.status_code for r in responses])
        self.assertEqual(1, len(self.aws.starts))

    def test_http_query_body_limit_and_plain_http_fail_closed(self):
        self.assertEqual(400, self.request("POST", "/v1/session?x=1", {}).status_code)
        body = b"x" * 102401
        self.assertEqual(413, self.client.post("/v1/session", content=body,
                                             headers=self.proof("POST", "/v1/session", body)).status_code)
        with TestClient(create_app(self.service, self.auth), base_url="http://test") as client:
            self.assertEqual(400, client.post("/v1/session", content=b"{}",
                                             headers=self.proof("POST", "/v1/session", b"{}")).status_code)

    def test_restart_after_final_chunk_commit_can_finalize_same_attempt(self):
        self.create()
        original = self.service._validate_audio
        def interrupted(*args):
            raise RuntimeError("simulate process dying after chunk commit")
        self.service._validate_audio = interrupted
        self.assertEqual(503, self.upload().status_code)
        self.service._validate_audio = original
        self.assertEqual(200, self.upload().status_code)
        self.assertEqual(1, len(self.aws.starts))

    def test_duplicate_final_after_completed_cleanup_returns_same_result(self):
        self.create()
        self.upload()
        self.aws.jobs[self.aws.starts[0]] = "COMPLETED"
        state = self.get().json()
        replay = self.upload()
        self.assertEqual(200, replay.status_code)
        self.assertEqual(state, replay.json())

    def test_metadata_and_consent_retention_retires_source_without_id_reassignment(self):
        self.create()
        self.request("POST", f"/v1/jobs/{self.action}/cancel", {})
        self.now[0] += 102*86400
        self.service.sweep()
        self.assertIsNone(self.store.job(self.action))
        self.assertIsNone(self.store.db.execute("SELECT * FROM recordings WHERE id=?", (self.recording,)).fetchone())
        self.assertIsNone(self.store.db.execute("SELECT * FROM installations WHERE id=?", (self.install,)).fetchone())
        self.assertIsNotNone(self.store.db.execute("SELECT * FROM tombstones WHERE id=?", (self.action,)).fetchone())

    def test_grant_revocation_sweeps_active_job_without_client(self):
        self.create()
        self.upload()
        self.auth.revoke(self.install)
        self.service.sweep()
        state = self.store.job(self.action)
        self.assertEqual("CANCELLED", state["status"])
        self.assertEqual({}, self.aws.inputs)

    def test_boolean_source_version_is_not_an_integer_version(self):
        response = self.request("POST", "/v1/recordings", {**self.binding, "source_version": True})
        self.assertEqual(422, response.status_code)

    def test_opening_ledger_with_wrong_custody_key_fails_before_serving(self):
        with self.assertRaises(ValueError):
            incorrect = Store(self.path, b"z"*32)
            incorrect.close()

    def test_cancel_and_final_upload_race_never_publishes_after_cancel_commit(self):
        self.create()
        with ThreadPoolExecutor(max_workers=2) as pool:
            pending = [pool.submit(self.upload), pool.submit(self.request, "POST", f"/v1/jobs/{self.action}/cancel", {})]
            responses = [future.result() for future in pending]
        self.assertIn(responses[0].status_code, (200, 403))
        self.assertEqual(200, responses[1].status_code)
        self.assertEqual("CANCELLED", self.store.job(self.action)["status"])
        self.assertEqual(403, self.upload().status_code)

    def test_resource_namespace_cannot_retarget_existing_ledger(self):
        self.store.bind_namespace({"account": "one", "input": "original"})
        self.store.bind_namespace({"account": "one", "input": "original"})
        with self.assertRaises(ValueError):
            self.store.bind_namespace({"account": "one", "input": "replacement"})

    def test_pending_cleanup_and_uncertain_attempt_survive_restart(self):
        self.create()
        self.aws.start_error = ProviderError("PROVIDER_TIMEOUT", uncertain=True)
        self.upload()
        deadline = self.store.job(self.action)["deadline"]
        self.client.close()
        self.store.close()
        self.store = Store(self.path, self.key)
        self.service = Service(self.store, self.aws, self.policy, self.clock)
        self.auth = Auth(self.store, self.clock)
        self.client = TestClient(create_app(self.service, self.auth), base_url="https://test")
        self.request("POST", f"/v1/jobs/{self.action}/retry", {})
        self.assertEqual(1, len(self.aws.starts))
        self.assertEqual(deadline, self.store.job(self.action)["deadline"])
        self.aws.cleanup_error = True
        self.assertTrue(self.request("POST", f"/v1/jobs/{self.action}/cancel", {}).json()["cleanup_pending"])
        self.aws.jobs[self.aws.starts[0]] = "COMPLETED"
        self.aws.cleanup_error = False
        self.service.sweep()
        self.assertFalse(self.request("POST", f"/v1/jobs/{self.action}/cancel", {}).json()["cleanup_pending"])

    def test_deadline_expiring_during_result_download_fences_publication(self):
        self.create()
        self.upload()
        self.aws.jobs[self.aws.starts[0]] = "COMPLETED"
        original = self.aws.result
        def delayed_result(attempt, job):
            self.now[0] = job["deadline"]+1
            return original(attempt, job)
        self.aws.result = delayed_result
        self.assertEqual(403, self.get().status_code)
        self.assertEqual("CANCELLED", self.store.job(self.action)["status"])
        self.assertIsNone(self.store.db.execute("SELECT * FROM results WHERE job=?", (self.action,)).fetchone())


if __name__ == "__main__":
    unittest.main()
