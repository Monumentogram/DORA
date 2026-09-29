import base64
import os
import sys
import types
import unittest
from io import BytesIO
from pathlib import Path
from unittest.mock import patch

import engineering_runtime as runtime


class RuntimeBoundaryTests(unittest.TestCase):
    def test_put_scope_binds_exact_authored_audio_and_headers(self):
        audio = (Path(__file__).parent / "fixtures" / "engineering-synthetic-ru.wav").read_bytes()
        environment = {"DORA_BUCKET": "input", "DORA_JOB_NAME": "job-s0", "DORA_ACCOUNT": "123456789012"}
        params = {"Bucket": "input", "Key": "synthetic-ru.wav", "BodyBase64": base64.b64encode(audio).decode(),
                  "ServerSideEncryption": "AES256", "ExpectedBucketOwner": "123456789012"}
        runtime._scope({"service": "s3", "method": "put_object", "params": params}, environment)
        for changed in ({"BodyBase64": base64.b64encode(b"different").decode()},
                        {"BodyBase64": "not-base64!"},
                        {"BodyBase64": base64.b64encode(b"x" * 1048577).decode()},
                        {"ServerSideEncryption": "aws:kms"}, {"ContentType": "audio/wav"},
                        {"ExpectedBucketOwner": "000000000000"}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                runtime._scope({"service": "s3", "method": "put_object", "params": {**params, **changed}}, environment)

    def test_handler_rejects_bad_put_before_sdk_client(self):
        calls = []
        boto3 = types.ModuleType("boto3")
        boto3.client = lambda *args, **kwargs: calls.append(args)
        botocore = types.ModuleType("botocore")
        config = types.ModuleType("botocore.config")
        config.Config = lambda **kwargs: object()
        environment = {"DORA_BUCKET": "input", "DORA_JOB_NAME": "job-s0", "DORA_ACCOUNT": "123456789012", "AWS_REGION": "eu-central-1"}
        base = {"Bucket": "input", "Key": "synthetic-ru.wav", "BodyBase64": base64.b64encode(b"wrong").decode(),
                "ServerSideEncryption": "AES256", "ExpectedBucketOwner": "123456789012"}
        with patch.dict(sys.modules, {"boto3": boto3, "botocore": botocore, "botocore.config": config}), patch.dict(os.environ, environment):
            for params in (base, {**base, "ServerSideEncryption": "aws:kms"}, {**base, "ContentType": "audio/wav"}):
                self.assertEqual(runtime.handler({"service": "s3", "method": "put_object", "params": params}, None)["error"]["type"], "ValueError")
        self.assertEqual(calls, [])

    def test_rejects_service_and_operation_outside_narrow_allowlist(self):
        called = []
        def factory(*args, **kwargs):
            called.append(args)
            raise AssertionError("must not create client")
        for service, method in (("iam", "delete_role"), ("s3", "put_bucket_policy"), ("transcribe", "list_transcription_jobs")):
            with self.subTest(service=service, method=method), self.assertRaises(ValueError):
                runtime.dispatch({"service": service, "method": method, "params": {}}, factory)
        self.assertEqual(called, [])

    def test_get_body_is_bounded_and_encoded(self):
        class Client:
            def get_object(self, **kwargs):
                return {"Body": BytesIO(b"abc")}
        result = runtime.dispatch({"service": "s3", "method": "get_object", "params": {"Bucket": "test", "Key": "synthetic-ru.wav"}}, lambda *args, **kwargs: Client())
        self.assertEqual(result["result"]["BodyBase64"], base64.b64encode(b"abc").decode())
        class BigClient:
            def get_object(self, **kwargs):
                return {"Body": BytesIO(b"x" * 1048577)}
        with self.assertRaises(ValueError):
            runtime.dispatch({"service": "s3", "method": "get_object", "params": {}}, lambda *args, **kwargs: BigClient())

    def test_start_uses_one_sdk_attempt(self):
        seen = []
        class Client:
            def start_transcription_job(self, **kwargs):
                return {"TranscriptionJob": {"TranscriptionJobName": "test"}}
        runtime.dispatch({"service": "transcribe", "method": "start_transcription_job", "params": {}}, lambda service, config: (seen.append(config.retries["total_max_attempts"]) or Client()))
        self.assertEqual(seen, [1])


if __name__ == "__main__":
    unittest.main()
