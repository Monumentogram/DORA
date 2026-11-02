"""The encrypted ledger reaches the provider as bounded authenticated reads."""
import base64
import hashlib
import unittest

from test_service import ServiceTests, wav


class AudioStreamTests(ServiceTests):
    def test_provider_receives_seekable_bounded_stream_with_exact_length_and_checksum(self):
        self.create()
        expected = wav()
        reads = []

        def put(attempt, audio):
            self.assertFalse(isinstance(audio, (bytes, bytearray)))
            self.assertEqual(len(expected), audio.length)
            self.assertEqual(hashlib.sha256(expected).digest(), audio.sha256)
            self.assertEqual(0, audio.tell())
            digest = hashlib.sha256()
            while True:
                block = audio.read(4096)
                if not block:
                    break
                reads.append(len(block))
                digest.update(block)
            self.assertEqual(hashlib.sha256(expected).digest(), digest.digest())
            audio.seek(40)
            self.assertEqual(expected[40:60], audio.read(20))
            with self.assertRaises(ValueError):
                audio.read()

        self.aws.put = put
        response = self.upload(expected)
        self.assertEqual(200, response.status_code, response.text)
        self.assertTrue(reads)
        self.assertLessEqual(max(reads), 4096)

    def test_declared_length_must_match_actual_authenticated_request_bytes(self):
        raw = b"{}"
        headers = self.proof("POST", "/v1/session", raw)
        headers["Content-Length"] = "1"
        result = self.client.post("/v1/session", content=raw, headers=headers)
        self.assertEqual(422, result.status_code)

    def test_real_sdk_serialization_streams_without_an_unbounded_read(self):
        import boto3
        from botocore.awsrequest import AWSResponse
        from botocore.config import Config
        from dora_cloud.aws_adapter import AmazonProvider
        from test_aws_adapter import config

        expected = wav()
        seen = []
        class Response:
            def stream(self, amt=None, decode_content=False):
                yield b""
        class OfflineTransport:
            def send(inner, request):
                digest = hashlib.sha256()
                self.assertEqual(str(len(expected)), request.headers["Content-Length"].decode())
                while True:
                    part = request.body.read(16384)
                    if not part:
                        break
                    seen.append(len(part))
                    digest.update(part)
                self.assertEqual(hashlib.sha256(expected).digest(), digest.digest())
                return AWSResponse(request.url, 200, {}, Response())
            def close(inner):
                pass
        sdk = boto3.client("s3", region_name="eu-central-1", aws_access_key_id="SYNTHETIC",
                           aws_secret_access_key="SYNTHETIC", config=Config(retries={"total_max_attempts": 1}))
        sdk._endpoint.http_session = OfflineTransport()
        provider = AmazonProvider(config(), sdk, None, lambda: None)
        self.aws.put = provider.put
        self.create()
        self.assertEqual(200, self.upload(expected).status_code)
        self.assertTrue(seen)
        sdk.close()

    def test_thirty_minute_signed_upload_and_provider_reads_never_build_a_whole_wav(self):
        import struct
        frames = 28_800_000
        self.binding["frames"] = frames
        self.create()
        total = frames*2+44
        header = struct.pack("<4sI4s4sIHHIIHH4sI", b"RIFF", frames*2+36, b"WAVE", b"fmt ",
                             16, 1, 1, 16000, 32000, 2, 16, b"data", frames*2)
        expected = hashlib.sha256()
        read_lengths = []
        def put(attempt, reader):
            self.assertEqual(57_600_044, reader.length)
            actual = hashlib.sha256()
            while True:
                part = reader.read(65536)
                if not part:
                    break
                read_lengths.append(len(part))
                actual.update(part)
            self.assertEqual(expected.digest(), actual.digest())
        self.aws.put = put
        offset = 0
        while offset < total:
            count = min(65536, total-offset)
            chunk = header+b"\x00\x01"*((count-44)//2) if offset == 0 else b"\x00\x01"*(count//2)
            expected.update(chunk)
            response = self.upload(chunk, offset=offset, final=offset+count == total)
            self.assertEqual(200, response.status_code, response.text)
            offset += count
        self.assertEqual(total, self.store.job(self.action)["uploaded_bytes"])
        self.assertEqual(1, len(self.aws.starts))
        self.assertEqual(879, len(read_lengths))
        self.assertLessEqual(max(read_lengths), 65536)


def load_tests(loader, tests, pattern):
    return unittest.TestSuite(loader.loadTestsFromName(name, AudioStreamTests)
                             for name in AudioStreamTests.__dict__ if name.startswith("test_"))
