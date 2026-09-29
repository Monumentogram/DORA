import json
import io
import sys
import tempfile
import types
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

import engineering_sandbox as s0


class FakeTranscribe:
    def __init__(self):
        self.requested = []

    def start_transcription_job(self, **request):
        raise TimeoutError("response lost")

    def get_transcription_job(self, **request):
        self.requested.append(request["TranscriptionJobName"])
        return {"TranscriptionJob": {"TranscriptionJobName": request["TranscriptionJobName"], "TranscriptionJobStatus": "IN_PROGRESS"}}


class FakeRuntime:
    def __init__(self, transcribe):
        self.transcribe = transcribe

    def client(self, service, config=None):
        assert service == "transcribe"
        return self.transcribe


class SandboxS0Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.private = Path(self.tmp.name)

    def test_lost_start_reply_is_reconciled_and_stays_unknown_in_common_ledger(self):
        root = self.private / "sandbox"
        common = self.private / "shared"
        root.mkdir()
        c = {"deadline": (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat(),
             "job_prefix": "d62d-test-", "bucket": "d62d-test-input", "account": "123456789012"}
        (root / "config.json").write_text(json.dumps(c))
        (root / "input-proof.json").write_text(json.dumps({"config_sha256": "b" * 64, "duration": 3.0}))
        transcribe = FakeTranscribe()
        with patch.object(s0, "ROOT", root), patch.object(s0, "LEDGER_ROOT", common), patch.object(s0, "assume", return_value=(FakeRuntime(transcribe), None)):
            s0.start()
        binding = json.loads((root / "dispatch-binding.json").read_text())
        attempt = Path(binding["attempt"])
        self.assertTrue(attempt.is_relative_to(common))
        self.assertFalse(attempt.is_relative_to(root))
        self.assertFalse((attempt / "result.json").exists())
        reconciliation = json.loads((root / "start-reconcile.json").read_text())
        self.assertEqual(reconciliation["status"], "UNKNOWN")
        self.assertEqual(transcribe.requested, ["d62d-test-s0"])

    def test_budget_guard_includes_ancillary_reserve_inside_ten_dollars(self):
        budget = s0.budget_guard()
        self.assertEqual(budget["existing_usd"], "8.4069")
        self.assertEqual(budget["ancillary_usd"], "0.9000")
        self.assertEqual(budget["prospective_usd"], "9.3069")
        self.assertEqual(budget["diagnostic_max_usd"], "0.12")

    def test_lambda_role_propagation_retry_is_narrow_and_bounded(self):
        class RolePending(Exception):
            response = {"Error": {"Code": "InvalidParameterValueException", "Message": "The role defined for the function cannot be assumed by Lambda."}}
        class OtherError(Exception):
            response = {"Error": {"Code": "AccessDeniedException", "Message": "Denied"}}
        class Lambda:
            def __init__(self):
                self.calls = 0
            def create_function(self, **kwargs):
                self.calls += 1
                if self.calls == 1:
                    raise RolePending()
                return {"FunctionName": kwargs["FunctionName"]}
            def get_function_configuration(self, **kwargs):
                return {"State": "Active", "LastUpdateStatus": "Successful"}
        client = Lambda()
        with patch.object(s0.time, "sleep"):
            s0.create_function_ready(client, {"FunctionName": "test"})
        self.assertEqual(client.calls, 2)
        class Denied(Lambda):
            def create_function(self, **kwargs):
                raise OtherError()
        with self.assertRaises(OtherError):
            s0.create_function_ready(Denied(), {"FunctionName": "test"})

    def test_runtime_bridge_preserves_sdk_error_response(self):
        original = {"Error": {"Code": "BadRequestException", "Message": "The requested job couldn't be found."}}
        class FakeClientError(Exception):
            def __init__(self, response, operation):
                self.response, self.operation = response, operation
        botocore = types.ModuleType("botocore")
        exceptions = types.ModuleType("botocore.exceptions")
        exceptions.ClientError = FakeClientError
        class Invocation:
            def invoke(self, **kwargs):
                return {"Payload": io.BytesIO(json.dumps({"error": {"type": "ClientError", "response": original}}).encode())}
        with patch.dict(sys.modules, {"botocore": botocore, "botocore.exceptions": exceptions}):
            with self.assertRaises(FakeClientError) as captured:
                s0.RuntimeClient(Invocation(), "test-function", "transcribe").get_transcription_job(TranscriptionJobName="test")
        self.assertEqual(captured.exception.response, original)

    def test_input_controls_require_exact_encryption_and_lifecycle(self):
        encryption = {"Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]}
        lifecycle = {"Rules": [{"ID": "SyntheticFallback", "Status": "Enabled", "Filter": {"Prefix": ""},
                                "Expiration": {"Days": 1}, "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 1}}]}
        desired = {"encryption": encryption, "lifecycle": lifecycle}
        snap = {"get_bucket_encryption": {"ServerSideEncryptionConfiguration": encryption},
                "get_bucket_lifecycle_configuration": lifecycle}
        s0.verify_input_controls(snap, desired)
        restrictive = {"Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"},
                                   "BlockedEncryptionTypes": {"EncryptionType": "SSE-C"}}]}
        s0.verify_input_controls({**snap, "get_bucket_encryption": {"ServerSideEncryptionConfiguration": restrictive}}, desired)
        bad = {**snap, "get_bucket_encryption": {"ServerSideEncryptionConfiguration": {"Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "aws:kms"}}]}}}
        with self.assertRaises(AssertionError):
            s0.verify_input_controls(bad, desired)
        bad = {**snap, "get_bucket_lifecycle_configuration": {"Rules": []}}
        with self.assertRaises(AssertionError):
            s0.verify_input_controls(bad, desired)

    def test_job_listing_consumes_all_pages_and_scopes_exact_prefix(self):
        class Jobs:
            def list_transcription_jobs(self, **kwargs):
                if "NextToken" not in kwargs:
                    return {"TranscriptionJobSummaries": [{"TranscriptionJobName": "d62d-test-a"}, {"TranscriptionJobName": "other"}], "NextToken": "page2"}
                return {"TranscriptionJobSummaries": [{"TranscriptionJobName": "d62d-test-b"}]}
        self.assertEqual([j["TranscriptionJobName"] for j in s0.list_scoped_jobs(Jobs(), "d62d-test-")], ["d62d-test-a", "d62d-test-b"])

    def test_bucket_inventory_detects_items_on_later_pages(self):
        class Bucket:
            def list_objects_v2(self, **kwargs):
                return {"Contents": [{"Key": "other"}], "IsTruncated": False} if kwargs.get("ContinuationToken") else {"Contents": [], "IsTruncated": True, "NextContinuationToken": "next"}

            def list_object_versions(self, **kwargs):
                return {"Versions": [], "DeleteMarkers": [], "IsTruncated": False}

            def list_multipart_uploads(self, **kwargs):
                return {"Uploads": [], "IsTruncated": False}
        inventory = s0.bucket_inventory(Bucket(), {"bucket": "test", "account": "123456789012"})
        self.assertEqual(inventory["objects"], ["other"])
        self.assertFalse(s0.bucket_is_empty(inventory))

    def test_runtime_trust_only_allows_lambda_service(self):
        trust = s0.build_trust()
        self.assertEqual(trust["Statement"][0]["Principal"], {"Service": "lambda.amazonaws.com"})
        self.assertNotIn("Condition", trust["Statement"][0])

    def test_exact_job_get_overrides_list_omission_for_cleanup(self):
        class Jobs:
            def list_transcription_jobs(self, **kwargs):
                return {"TranscriptionJobSummaries": []}

            def get_transcription_job(self, **kwargs):
                return {"TranscriptionJob": {"TranscriptionJobName": kwargs["TranscriptionJobName"], "TranscriptionJobStatus": "COMPLETED"}}
        names = [j["TranscriptionJobName"] for j in s0.jobs_for_cleanup(Jobs(), "d62d-test-", "d62d-test-s0")]
        self.assertEqual(names, ["d62d-test-s0"])

    def test_exact_job_missing_requires_known_not_found_error(self):
        class Error(Exception):
            response = {"Error": {"Code": "BadRequestException", "Message": "The requested job couldn't be found."}}

        class Jobs:
            def list_transcription_jobs(self, **kwargs):
                return {"TranscriptionJobSummaries": []}

            def get_transcription_job(self, **kwargs):
                raise Error()
        self.assertEqual(s0.jobs_for_cleanup(Jobs(), "d62d-test-", "d62d-test-s0"), [])
        Error.response = {"Error": {"Code": "AccessDeniedException", "Message": "Denied"}}
        with self.assertRaises(Error):
            s0.jobs_for_cleanup(Jobs(), "d62d-test-", "d62d-test-s0")


if __name__ == "__main__":
    unittest.main()
