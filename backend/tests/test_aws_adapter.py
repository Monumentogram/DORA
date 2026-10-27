import base64
import hashlib
import io
import json
import unittest

import boto3
from botocore.exceptions import EndpointConnectionError
from botocore.response import StreamingBody
from botocore.stub import Stubber

from dora_cloud.aws_adapter import AmazonProvider, ProviderError
from dora_cloud.config import LiveConfig
from test_service import wav


def config(**changes):
    return LiveConfig(**{
        "live_enabled": True, "account_id": "123456789012", "owner_id": "owner-a",
        "region": "eu-central-1", "input_bucket": "dora-test-input", "output_bucket": "dora-test-output",
        "input_kms_arn": "arn:aws:kms:eu-central-1:123456789012:key/11111111-1111-4111-8111-111111111111",
        "output_kms_arn": "arn:aws:kms:eu-central-1:123456789012:key/22222222-2222-4222-8222-222222222222",
        "ledger_path": "/private/state.sqlite", "custody_key_path": "/private/custody.key", **changes})


class AdapterTests(unittest.TestCase):
    def setUp(self):
        self.cfg = config()
        session = boto3.Session(aws_access_key_id="SYNTHETIC", aws_secret_access_key="SYNTHETIC", region_name="eu-central-1")
        self.s3, self.transcribe = session.client("s3"), session.client("transcribe")
        self.s3stub, self.tstub = Stubber(self.s3), Stubber(self.transcribe)
        self.s3stub.activate()
        self.tstub.activate()
        self.provider = AmazonProvider(self.cfg, self.s3, self.transcribe, lambda: None)
        self.attempt = {"name": "dora-00000000-0000-4000-8000-000000000001-1"}
        self.job = {"creation": {"language": "ru-RU"}, "binding": {"frames": 8000}}

    def tearDown(self):
        self.s3stub.assert_no_pending_responses()
        self.tstub.assert_no_pending_responses()
        self.s3stub.deactivate()
        self.tstub.deactivate()
        self.s3.close()
        self.transcribe.close()

    def observation(self, status="IN_PROGRESS", language="ru-RU", **changes):
        return {"TranscriptionJobName": self.attempt["name"], "TranscriptionJobStatus": status,
                "LanguageCode": language, "MediaSampleRateHertz": 16000, "MediaFormat": "wav",
                "Media": {"MediaFileUri": f"s3://dora-test-input/input/{self.attempt['name']}.wav"}, **changes}

    def test_exact_ru_en_start_private_s3_and_explicit_output_cmk_without_data_access_role(self):
        audio = wav()
        self.s3stub.add_response("put_object", {}, {
            "Bucket": "dora-test-input", "Key": f"input/{self.attempt['name']}.wav", "Body": audio,
            "ContentType": "audio/wav", "ServerSideEncryption": "aws:kms", "SSEKMSKeyId": self.cfg.input_kms_arn,
            "ExpectedBucketOwner": "123456789012", "ChecksumSHA256": base64.b64encode(hashlib.sha256(audio).digest()).decode()})
        self.provider.put(self.attempt, audio)
        for language in ("ru-RU", "en-US"):
            self.job["creation"]["language"] = language
            self.tstub.add_response("start_transcription_job", {"TranscriptionJob": self.observation(language=language)}, {
                "TranscriptionJobName": self.attempt["name"], "LanguageCode": language,
                "MediaSampleRateHertz": 16000, "MediaFormat": "wav",
                "Media": {"MediaFileUri": f"s3://dora-test-input/input/{self.attempt['name']}.wav"},
                "OutputBucketName": "dora-test-output", "OutputKey": f"output/{self.attempt['name']}.json",
                "OutputEncryptionKMSKeyId": self.cfg.output_kms_arn})
            self.assertEqual("IN_PROGRESS", self.provider.start(self.attempt, self.job))

    def test_identity_and_configuration_mismatch_rejected(self):
        for change in ({"LanguageCode": "en-US"}, {"MediaFormat": "mp3"},
                       {"TranscriptionJobName": "wrong"}, {"MediaSampleRateHertz": 8000},
                       {"Media": {"MediaFileUri": "s3://another/key"}}, {"Settings": {"ShowSpeakerLabels": True}},
                       {"IdentifyLanguage": True}):
            self.tstub.add_response("get_transcription_job", {"TranscriptionJob": self.observation(**change)},
                                    {"TranscriptionJobName": self.attempt["name"]})
            with self.assertRaisesRegex(ProviderError, "PROVIDER_IDENTITY_MISMATCH"):
                self.provider.status(self.attempt, self.job)

    def test_safe_errors_and_unknown_start_effect(self):
        for code, status, expected in (("LimitExceededException", 429, "PROVIDER_RATE_LIMITED"),
                                       ("InternalFailureException", 500, "PROVIDER_UNAVAILABLE"),
                                       ("ConflictException", 400, "START_UNCERTAIN"),
                                       ("BadRequestException", 400, "PROVIDER_REJECTED")):
            self.tstub.add_client_error("start_transcription_job", code, "private audio text must never escape", status)
            with self.assertRaises(ProviderError) as caught:
                self.provider.start(self.attempt, self.job)
            self.assertEqual(expected, caught.exception.code)
            self.assertTrue(caught.exception.uncertain)
            self.assertNotIn("private", str(caught.exception))

    def add_result(self, result, **headers):
        data = json.dumps(result).encode()
        self.s3stub.add_response("get_object", {
            "Body": StreamingBody(io.BytesIO(data), len(data)), "ContentLength": len(data),
            "ServerSideEncryption": "aws:kms", "SSEKMSKeyId": self.cfg.output_kms_arn, **headers},
            {"Bucket": "dora-test-output", "Key": f"output/{self.attempt['name']}.json", "ExpectedBucketOwner": "123456789012"})

    def document(self, **changes):
        return {"jobName": self.attempt["name"], "accountId": "123456789012", "status": "COMPLETED",
                "results": {"transcripts": [{"transcript": "Hello."}], "items": [
                    {"type": "pronunciation", "start_time": "0.00", "end_time": "0.25",
                     "alternatives": [{"content": "Hello", "confidence": "0.98"}]},
                    {"type": "punctuation", "alternatives": [{"content": "."}]}]}, **changes}

    def test_only_fixed_s3_key_read_and_real_optional_timing(self):
        self.add_result(self.document())
        result = self.provider.result(self.attempt, self.job)
        self.assertEqual("Hello.", result["text"])
        self.assertEqual({"text": "Hello", "start_ms": 0, "end_ms": 250, "confidence": 0.98}, result["segments"][0])
        self.assertIsNone(result["segments"][1]["start_ms"])
        self.assertIsNone(result["segments"][1]["confidence"])

    def test_result_identity_encryption_and_bad_timing_fail_closed(self):
        for change in ({"jobName": "wrong"}, {"accountId": "000000000000"}, {"status": "FAILED"}):
            self.add_result(self.document(**change))
            with self.assertRaises(ProviderError):
                self.provider.result(self.attempt, self.job)
        self.add_result(self.document(), SSEKMSKeyId=self.cfg.input_kms_arn)
        with self.assertRaises(ProviderError):
            self.provider.result(self.attempt, self.job)
        for value in ("NaN", "-1", "1.00", "0.00001"):
            document = self.document()
            document["results"]["items"][0]["end_time"] = value
            self.add_result(document)
            with self.assertRaises(ProviderError):
                self.provider.result(self.attempt, self.job)

    def test_cleanup_exact_keys_expected_owner_and_job(self):
        for bucket, key, method in (("dora-test-input", f"input/{self.attempt['name']}.wav", self.provider.delete_input),
                                    ("dora-test-output", f"output/{self.attempt['name']}.json", self.provider.delete_output)):
            self.s3stub.add_response("delete_object", {}, {"Bucket": bucket, "Key": key, "ExpectedBucketOwner": "123456789012"})
            method(self.attempt)
        self.tstub.add_response("delete_transcription_job", {}, {"TranscriptionJobName": self.attempt["name"]})
        self.provider.delete_job(self.attempt)

    def test_admission_failure_prevents_all_new_upload_or_start(self):
        def denied():
            raise ProviderError("LIVE_DISABLED")
        provider = AmazonProvider(self.cfg, self.s3, self.transcribe, denied)
        with self.assertRaisesRegex(ProviderError, "LIVE_DISABLED"):
            provider.put(self.attempt, wav())
        with self.assertRaisesRegex(ProviderError, "LIVE_DISABLED"):
            provider.start(self.attempt, self.job)

    def test_network_exception_is_content_free(self):
        class Network:
            def start_transcription_job(self, **kwargs):
                raise EndpointConnectionError(endpoint_url="https://secret.example/audio")
        provider = AmazonProvider(self.cfg, self.s3, Network(), lambda: None)
        with self.assertRaises(ProviderError) as caught:
            provider.start(self.attempt, self.job)
        self.assertTrue(caught.exception.uncertain)
        self.assertEqual("PROVIDER_UNAVAILABLE", str(caught.exception))

    def test_lost_delete_ack_reconciles_absence_without_raw_message_matching(self):
        self.tstub.add_client_error("delete_transcription_job", "BadRequestException", "do not inspect raw provider text", 400,
                                   expected_params={"TranscriptionJobName": self.attempt["name"]})
        self.tstub.add_response("list_transcription_jobs", {"TranscriptionJobSummaries": []},
                               {"JobNameContains": self.attempt["name"], "MaxResults": 100})
        self.provider.delete_job(self.attempt)

    def test_delete_error_does_not_claim_cleanup_when_named_job_still_exists(self):
        self.tstub.add_client_error("delete_transcription_job", "BadRequestException", "private", 400)
        self.tstub.add_response("list_transcription_jobs", {"TranscriptionJobSummaries": [{
            "TranscriptionJobName": self.attempt["name"], "TranscriptionJobStatus": "IN_PROGRESS"}]},
            {"JobNameContains": self.attempt["name"], "MaxResults": 100})
        with self.assertRaises(ProviderError):
            self.provider.delete_job(self.attempt)


if __name__ == "__main__":
    unittest.main()
