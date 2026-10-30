"""Composite admission uses real signatures/ledger and an isolated synthetic provider."""
import base64
import unittest

from test_service import ServiceTests, wav
from dora_cloud.service import Policy


class CompositeTests(ServiceTests):
    def plan(self, frames=1_382_400_000):
        return {**self.creation, "source_digest": self.binding["source_digest"],
                "source_version": 1, "frames": frames,
                "window_policy": "cloud-balanced-1800-v1"}

    def enable(self, daily="1.00", rate="0.0001", ancillary="0.0001"):
        # Fictional prices are injected only into the test provider's service.
        self.service.policy = Policy(daily_reserve_usd=daily,
                                    per_minute_reserve_usd=rate,
                                    ancillary_per_attempt_usd=ancillary,
                                    long_runs_enabled=True)

    def test_default_old_deployment_does_not_admit_composite_or_start_paid_work(self):
        response = self.request("POST", "/v1/runs/quote", self.plan())
        self.assertEqual(200, response.status_code)
        self.assertFalse(response.json()["enabled"])
        self.assertEqual(403, self.request("POST", "/v1/runs", self.plan()).status_code)
        self.assertEqual([], self.aws.starts)

    def test_aggregate_real_reserve_is_rejected_before_any_child_upload_or_paid_call(self):
        self.enable(daily="0.25", rate="0.03", ancillary="0.01")
        quote = self.request("POST", "/v1/runs/quote", self.plan()).json()
        self.assertEqual(48, quote["part_count"])
        self.assertEqual(43_680_000, quote["required_micro_usd"])
        self.assertFalse(quote["affordable"])
        self.assertEqual(429, self.request("POST", "/v1/runs", self.plan()).status_code)
        self.assertEqual([], self.aws.puts)
        self.assertEqual(0, self.store.db.execute("SELECT count(*) FROM reservations").fetchone()[0])

    def test_balanced_ranges_cover_small_tail_and_24h_exactly(self):
        self.enable()
        state = self.request("POST", "/v1/runs", self.plan(28_800_001)).json()
        self.assertEqual([(0, 14_400_000), (14_400_000, 14_400_001)],
                         [(p["first_frame"], p["frames"]) for p in state["parts"]])
        self.assertEqual(state, self.request("POST", "/v1/runs", self.plan(28_800_001)).json())
        self.assertEqual(409, self.request("POST", "/v1/runs", self.plan()).status_code)

    def test_child_success_restart_resume_and_first_byte_deadline_are_durable(self):
        self.enable()
        body = self.plan(16_000)
        parent = self.request("POST", "/v1/runs", body).json()
        path = f"/v1/runs/{self.action}/parts/0"
        child = self.request("POST", path, {}).json()
        child_id = child["id"]
        data = wav(16_000)
        audio_path = f"/v1/jobs/{child_id}/audio"
        payload = {"offset": 0, "data_base64": base64.b64encode(data).decode(), "final": True}
        uploaded = self.request("POST", audio_path, payload)
        self.assertEqual(200, uploaded.status_code, uploaded.text)
        self.assertEqual(1, len(self.aws.starts))
        self.aws.jobs[self.aws.starts[0]] = "COMPLETED"
        result = self.request("GET", f"/v1/jobs/{child_id}").json()
        self.assertEqual("COMPLETED", result["status"])
        self.assertEqual(0, result["first_frame"])
        deadline = self.store.job(child_id)["deadline"]
        self.now[0] += 30
        from dora_cloud.auth import Auth
        from dora_cloud.http_server import create_app
        from dora_cloud.service import Service
        from dora_cloud.store import Store
        from fastapi.testclient import TestClient
        policy = self.service.policy
        self.client.close()
        self.store.close()
        self.store = Store(self.path, self.key)
        self.service = Service(self.store, self.aws, policy, self.clock)
        self.auth = Auth(self.store, self.clock)
        self.client = TestClient(create_app(self.service, self.auth), base_url="https://test")
        repeated = self.request("POST", "/v1/runs", body).json()
        self.assertEqual(deadline, repeated["deadline"])
        self.assertEqual("COMPLETED", repeated["parts"][0]["status"])
        self.assertEqual(1, self.store.db.execute("SELECT count(*) FROM reservations").fetchone()[0])
        self.assertEqual(200, self.request("POST", audio_path, payload).status_code)
        self.assertEqual(1, len(self.aws.starts))

    def test_out_of_order_child_and_over_24h_are_rejected(self):
        self.enable()
        self.assertEqual(422, self.request("POST", "/v1/runs", self.plan(1_382_400_001)).status_code)
        self.assertEqual(200, self.request("POST", "/v1/runs", self.plan()).status_code)
        self.assertEqual(409, self.request("POST", f"/v1/runs/{self.action}/parts/1", {}).status_code)
        self.assertEqual(404, self.request("GET", f"/v1/runs/{self.action}", install=self.other).status_code)

    def test_completed_run_result_metadata_remains_readable_after_audio_deadline(self):
        self.enable()
        body = self.plan(16_000)
        self.assertEqual(200, self.request("POST", "/v1/runs", body).status_code)
        child = self.request("POST", f"/v1/runs/{self.action}/parts/0", {}).json()["id"]
        data = wav(16_000)
        self.request("POST", f"/v1/jobs/{child}/audio", {
            "offset": 0, "data_base64": base64.b64encode(data).decode(), "final": True})
        self.aws.jobs[self.aws.starts[0]] = "COMPLETED"
        self.assertEqual("COMPLETED", self.request("GET", f"/v1/jobs/{child}").json()["status"])
        self.now[0] += 86401
        self.tokens[self.install] = self.request("POST", "/v1/session", {}).json()["access_token"]
        response = self.request("GET", f"/v1/runs/{self.action}")
        self.assertEqual(200, response.status_code)
        self.assertEqual("COMPLETED", response.json()["status"])

    def test_next_child_inherits_first_byte_deadline_and_budget_retry_reserves_once(self):
        self.enable()
        self.request("POST", "/v1/runs", self.plan(28_800_001))
        child = self.request("POST", f"/v1/runs/{self.action}/parts/0", {}).json()["id"]
        self.request("POST", f"/v1/jobs/{child}/audio", {
            "offset": 0, "data_base64": base64.b64encode(b"x").decode(), "final": False})
        deadline = self.store.job(child)["deadline"]
        # This test isolates orchestration after an already validated prior child.
        job = self.store.job(child)
        job.update(status="COMPLETED")
        self.store.save_job(job)
        self.now[0] += 100
        second = self.request("POST", f"/v1/runs/{self.action}/parts/1", {}).json()["id"]
        self.assertEqual(deadline, self.store.job(second)["deadline"])
        self.now[0] = deadline
        self.tokens[self.install] = self.request("POST", "/v1/session", {}).json()["access_token"]
        self.assertEqual(403, self.request("POST", f"/v1/jobs/{second}/audio", {
            "offset": 0, "data_base64": "eA==", "final": False}).status_code)
        self.assertEqual([], self.aws.starts)

    def test_parent_cancel_route_and_existing_cancel_ledger_alias_fence_children(self):
        self.enable()
        self.request("POST", "/v1/runs", self.plan(16_000))
        child = self.request("POST", f"/v1/runs/{self.action}/parts/0", {}).json()["id"]
        result = self.request("POST", f"/v1/jobs/{self.action}/cancel", {})
        self.assertEqual(200, result.status_code)
        self.assertEqual("CANCELLED", result.json()["status"])
        self.assertEqual(403, self.request("POST", f"/v1/runs/{self.action}/parts/0", {}).status_code)
        self.assertTrue(self.store.job(child)["revoked"])
        self.assertEqual(403, self.request("POST", "/v1/runs", self.plan(16_000)).status_code)

    def test_paid_retry_consumes_additional_budget_once_and_never_restarts_uncertain_work(self):
        self.enable(daily="0.03", rate="0.01", ancillary="0.01")
        self.request("POST", "/v1/runs", self.plan(16_000))
        child = self.request("POST", f"/v1/runs/{self.action}/parts/0", {}).json()["id"]
        data = wav(16_000)
        self.request("POST", f"/v1/jobs/{child}/audio", {
            "offset": 0, "data_base64": base64.b64encode(data).decode(), "final": True})
        self.assertEqual(1, self.store.db.execute("SELECT count(*) FROM reservations").fetchone()[0])
        self.aws.jobs[self.aws.starts[0]] = "FAILED"
        self.request("GET", f"/v1/jobs/{child}")
        self.assertEqual(200, self.request("POST", f"/v1/jobs/{child}/retry", {}).status_code)
        self.assertEqual(200, self.request("POST", f"/v1/jobs/{child}/retry", {}).status_code)
        self.assertEqual(2, len(self.aws.starts))
        self.assertEqual(2, self.store.db.execute("SELECT count(*) FROM reservations").fetchone()[0])
        self.assertEqual(25_000, self.store.db.execute("SELECT sum(micro_usd) FROM reservations").fetchone()[0])

    def test_paid_retry_is_blocked_if_parent_hold_consumed_available_budget(self):
        self.enable(daily="0.02", rate="0.01", ancillary="0.01")
        self.request("POST", "/v1/runs", self.plan(16_000))
        child = self.request("POST", f"/v1/runs/{self.action}/parts/0", {}).json()["id"]
        data = wav(16_000)
        self.request("POST", f"/v1/jobs/{child}/audio", {
            "offset": 0, "data_base64": base64.b64encode(data).decode(), "final": True})
        self.aws.jobs[self.aws.starts[0]] = "FAILED"
        self.request("GET", f"/v1/jobs/{child}")
        self.assertEqual(429, self.request("POST", f"/v1/jobs/{child}/retry", {}).status_code)
        self.assertEqual(1, len(self.aws.starts))

    def test_resume_quote_recognizes_its_existing_aggregate_hold_without_charging_again(self):
        self.enable(daily="0.02", rate="0.01", ancillary="0.01")
        body = self.plan(16_000)
        self.assertEqual(200, self.request("POST", "/v1/runs", body).status_code)
        quote = self.request("POST", "/v1/runs/quote", body)
        self.assertEqual(200, quote.status_code)
        self.assertTrue(quote.json()["affordable"])
        self.assertEqual(1, self.store.db.execute("SELECT count(*) FROM reservations").fetchone()[0])

    def test_24h_manifest_has_exact_48_global_ranges_and_one_finite_fake_price_hold(self):
        self.enable()
        response = self.request("POST", "/v1/runs", self.plan())
        self.assertEqual(200, response.status_code)
        state = response.json()
        self.assertEqual(48, len(state["parts"]))
        self.assertEqual(48, len({part["id"] for part in state["parts"]}))
        self.assertEqual(1_353_600_000, state["parts"][-1]["first_frame"])
        self.assertEqual(28_800_000, state["parts"][-1]["frames"])
        self.assertEqual(2_764_800_000, sum(part["frames"] for part in state["parts"])*2)
        self.assertEqual(148_800, state["reserved_micro_usd"])
        again = self.request("POST", "/v1/runs", self.plan())
        self.assertEqual(state, again.json())
        self.assertEqual(1, self.store.db.execute("SELECT count(*) FROM reservations").fetchone()[0])
        self.assertEqual([], self.aws.starts)

    def test_first_attempt_crossing_utc_day_moves_its_unused_hold_before_paid_start(self):
        import uuid
        self.enable(daily="0.02", rate="0.01", ancillary="0.01")
        body = self.plan(16_000)
        self.assertEqual(200, self.request("POST", "/v1/runs", body).status_code)
        child = self.request("POST", f"/v1/runs/{self.action}/parts/0", {}).json()["id"]
        self.now[0] = (self.now[0]//86400+1)*86400
        self.tokens[self.install] = self.request("POST", "/v1/session", {}).json()["access_token"]
        self.assertEqual(200, self.request("POST", f"/v1/jobs/{child}/audio", {
            "offset": 0, "data_base64": base64.b64encode(wav(16_000)).decode(), "final": True}).status_code)
        self.aws.jobs[self.aws.starts[0]] = "COMPLETED"
        self.request("GET", f"/v1/jobs/{child}")
        next_run = {**body, "action_id": str(uuid.uuid4()), "consent_granted_at": self.now[0]*1000}
        quote = self.request("POST", "/v1/runs/quote", next_run).json()
        self.assertEqual(7500, quote["available_micro_usd"])
        self.assertFalse(quote["affordable"])
        self.assertEqual(429, self.request("POST", "/v1/runs", next_run).status_code)
        self.assertEqual(12_500, self.store.db.execute("SELECT sum(micro_usd) FROM reservations").fetchone()[0])

    def completed_child(self):
        self.enable()
        self.request("POST", "/v1/runs", self.plan(16_000))
        child = self.request("POST", f"/v1/runs/{self.action}/parts/0", {}).json()["id"]
        self.request("POST", f"/v1/jobs/{child}/audio", {
            "offset": 0, "data_base64": base64.b64encode(wav(16_000)).decode(), "final": True})
        self.aws.jobs[self.aws.starts[0]] = "COMPLETED"
        self.assertIsNotNone(self.request("GET", f"/v1/jobs/{child}").json()["result"])
        return child

    def test_durable_parent_cancel_intent_fences_completed_child_before_cascade_and_sweep_reconciles(self):
        child = self.completed_child()
        # Historical crash state: parent commit happened, child cascade did not.
        run = self.service.runs.load(self.action)
        run.update(revoked=True, status="CANCELLED", cancel_reason="CANCELLED_BY_OWNER")
        self.service.runs.save(run)
        response = self.request("GET", f"/v1/jobs/{child}")
        self.assertEqual(403, response.status_code)
        self.service.sweep()
        self.assertTrue(self.store.job(child)["revoked"])
        self.assertIsNone(self.store.db.execute("SELECT * FROM results WHERE job=?", (child,)).fetchone())

    def test_external_cleanup_failure_cannot_leave_parent_revoked_but_child_results_readable(self):
        child = self.completed_child()
        original = self.service._cleanup
        def crashed_cleanup(job):
            raise RuntimeError("synthetic crash after revocation commit")
        self.service._cleanup = crashed_cleanup
        response = self.request("POST", f"/v1/runs/{self.action}/cancel", {})
        self.assertEqual(503, response.status_code)
        self.service._cleanup = original
        self.assertTrue(self.service.runs.load(self.action)["revoked"])
        self.assertTrue(self.store.job(child)["revoked"])
        self.assertIsNone(self.store.db.execute("SELECT * FROM results WHERE job=?", (child,)).fetchone())
        self.service.sweep()
        self.assertIsNone(self.request("GET", f"/v1/jobs/{child}").json()["result"])

    def test_revocation_transaction_rolls_back_parent_and_children_together_on_storage_crash(self):
        child = self.completed_child()
        original = self.store.save_job
        def failed_child_commit(job):
            raise RuntimeError("synthetic storage crash during atomic cascade")
        self.store.save_job = failed_child_commit
        self.assertEqual(503, self.request("POST", f"/v1/runs/{self.action}/cancel", {}).status_code)
        self.store.save_job = original
        self.assertFalse(self.service.runs.load(self.action)["revoked"])
        self.assertFalse(self.store.job(child)["revoked"])
        self.assertEqual(200, self.request("POST", f"/v1/runs/{self.action}/cancel", {}).status_code)
        self.assertTrue(self.service.runs.load(self.action)["revoked"])
        self.assertTrue(self.store.job(child)["revoked"])

    def test_deadline_exemption_for_completed_text_never_survives_grant_revocation(self):
        child = self.completed_child()
        # The completed-child retention branch is independent of unfinished siblings.
        parent = self.service.runs.load(self.action)
        parent.update(revoked=True, status="CANCELLED", cancel_reason="DEADLINE_EXCEEDED")
        self.service.runs.save(parent)
        self.service.sweep()
        self.assertIsNotNone(self.request("GET", f"/v1/jobs/{child}").json()["result"])
        self.auth.revoke(self.install)
        self.service.sweep()
        self.assertEqual("GRANT_REVOKED", self.service.runs.load(self.action)["cancel_reason"])
        self.assertTrue(self.store.job(child)["revoked"])
        self.assertIsNone(self.store.db.execute("SELECT * FROM results WHERE job=?", (child,)).fetchone())


def load_tests(loader, tests, pattern):
    return unittest.TestSuite(loader.loadTestsFromName(name, CompositeTests)
                             for name in CompositeTests.__dict__ if name.startswith("test_"))
