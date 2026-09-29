import json
import tempfile
import unittest
from pathlib import Path

from engineering_ledger import finish, reserve


HASH = "a" * 64
CONFIG_HASH = "b" * 64


def request(name="job-1", mode="ENGINEERING_SANDBOX", **changes):
    value = {
        "TranscriptionJobName": name,
        "LanguageCode": "ru-RU",
        "Media": {"MediaFileUri": "s3://private/synthetic.wav"},
        "mode": mode,
        "audio_kind": "synthetic",
        "audio_sha256": HASH,
        "config_sha256": CONFIG_HASH,
    }
    value.update(changes)
    return value


class DiagnosticLedgerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def reserve(self, req=None, **kwargs):
        return reserve(self.root, req or request(), "test hypothesis", "one setting", 3.0, **kwargs)

    def test_intent_is_durable_before_api_and_result_is_immutable(self):
        attempt = self.reserve()
        intent = json.loads((attempt / "intent.json").read_text())
        self.assertEqual(intent["request"]["TranscriptionJobName"], "job-1")
        self.assertEqual(intent["hypothesis"], "test hypothesis")
        self.assertFalse((attempt / "result.json").exists())
        finish(attempt, {"error": "BadRequest"}, created=False, completed=False)
        self.assertEqual(json.loads((attempt / "result.json").read_text())["result"], {"error": "BadRequest"})
        with self.assertRaises(FileExistsError):
            finish(attempt, {"error": "changed"}, created=False, completed=False)

    def test_thirteenth_attempt_is_rejected_across_both_modes(self):
        for number in range(12):
            mode = "ENGINEERING_SANDBOX" if number % 2 else "ADMISSION_REPRODUCTION"
            self.reserve(request(f"job-{number}", mode, audio_sha256=f"{number:064x}"))
        with self.assertRaises(ValueError):
            self.reserve(request("job-13", audio_sha256="f" * 64))
        self.assertEqual(len(list((self.root / "diagnostic-ledger").glob("attempt-*"))), 12)

    def test_invalid_mode_and_non_synthetic_audio_are_rejected(self):
        for req in (request(mode="PRODUCTION"), request(audio_kind="real"), request(audio_sha256=""), request(config_sha256="")):
            with self.subTest(req=req), self.assertRaises(ValueError):
                self.reserve(req)
        self.assertFalse((self.root / "diagnostic-ledger").exists())

    def test_audio_must_be_between_two_and_five_seconds(self):
        for duration in (-1, 0, 1.99, 5.01, 30):
            with self.subTest(duration=duration), self.assertRaises(ValueError):
                reserve(self.root, request(), "hypothesis", "delta", duration)
        self.assertFalse((self.root / "diagnostic-ledger").exists())

    def test_unknown_attempt_counts_and_blocks_duplicate_even_with_new_job_name(self):
        self.reserve()
        with self.assertRaises(ValueError):
            self.reserve(request("renamed"))

    def test_failed_request_cannot_be_replayed_with_new_job_name(self):
        attempt = self.reserve()
        finish(attempt, {"error": "BadRequest"}, created=False, completed=False)
        with self.assertRaises(ValueError):
            self.reserve(request("renamed"), reproduce_success=True)

    def test_failed_request_can_retry_after_observed_config_changes(self):
        attempt = self.reserve()
        finish(attempt, {"error": "BadRequest"}, created=False, completed=False)
        changed = request("retry-after-policy-change", config_sha256="c" * 64)
        self.assertNotEqual(self.reserve(changed), attempt)

    def test_switching_mode_cannot_replay_failed_identical_configuration(self):
        attempt = self.reserve()
        finish(attempt, {"error": "BadRequest"}, created=False, completed=False)
        with self.assertRaises(ValueError):
            self.reserve(request("renamed", mode="ADMISSION_REPRODUCTION"))

    def test_same_audio_and_settings_cannot_replay_from_new_uri(self):
        attempt = self.reserve()
        finish(attempt, {"error": "BadRequest"}, created=False, completed=False)
        moved = request("renamed", Media={"MediaFileUri": "s3://private/renamed.wav"})
        with self.assertRaises(ValueError):
            self.reserve(moved)

    def test_successful_request_can_only_be_reproduced_explicitly(self):
        attempt = self.reserve()
        finish(attempt, {"status": "COMPLETED"}, created=True, completed=True)
        with self.assertRaises(ValueError):
            self.reserve(request("renamed"))
        repeated = self.reserve(request("renamed"), reproduce_success=True)
        self.assertNotEqual(repeated, attempt)

    def test_incomplete_created_job_is_not_reproducible(self):
        attempt = self.reserve()
        finish(attempt, {"status": "IN_PROGRESS"}, created=True, completed=False)
        with self.assertRaises(ValueError):
            self.reserve(request("renamed"), reproduce_success=True)

    def test_budget_is_reserved_for_each_attempt(self):
        self.reserve()
        intent = json.loads(next((self.root / "diagnostic-ledger").glob("attempt-*/intent.json")).read_text())
        self.assertEqual(intent["reserved_seconds"], 15)
        self.assertEqual(intent["reserved_usd"], "0.01")


if __name__ == "__main__":
    unittest.main()
