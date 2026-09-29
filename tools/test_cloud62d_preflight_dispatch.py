"""Prospective Stage-0 dispatch boundary: invalid media causes zero side effects."""
import hashlib
import struct
import unittest
from unittest.mock import Mock

from tools.cloud62d_preflight_dispatch import dispatch_media, UploadedSource
from tools.test_cloud62d_media import wav_bytes, riff, FMT


class DispatchPreflightTests(unittest.TestCase):
    def run_invalid(self, audio, declared_format='wav'):
        upload, start = Mock(), Mock()
        with self.assertRaises(ValueError):
            dispatch_media(audio, declared_format, 16000, upload=upload, start=start)
        upload.assert_not_called()
        start.assert_not_called()

    def test_wrong_declaration_has_zero_uploads_and_starts(self):
        self.run_invalid(wav_bytes(), 'mp3')

    def test_all_truncated_variants_have_zero_uploads_and_starts(self):
        original = wav_bytes()
        short_riff = original[:-4]
        short_data = bytearray(short_riff)
        struct.pack_into('<I', short_data, 4, len(short_data) - 8)
        partial_frame = riff((b'fmt ', FMT), (b'data', b'\x00' * 16001))
        for damaged in (short_riff, bytes(short_data), partial_frame):
            with self.subTest(size=len(damaged)):
                self.run_invalid(damaged)

    def test_empty_near_empty_have_zero_uploads_and_starts(self):
        for frames in (0, 1, 7999):
            self.run_invalid(wav_bytes(frames))

    def test_valid_bytes_and_profile_are_bound_through_upload_and_start(self):
        audio = wav_bytes(8000)
        digest = hashlib.sha256(audio).hexdigest()
        receipt = UploadedSource('private-source-version', digest, len(audio))
        upload = Mock(return_value=receipt)
        start = Mock(return_value={'accepted': True})
        self.assertEqual(dispatch_media(audio, 'wav', 16000, upload=upload, start=start),
                         {'accepted': True})
        uploaded = upload.call_args.args[0]
        self.assertEqual(uploaded.audio, audio)
        self.assertEqual(uploaded.sha256, digest)
        sent = start.call_args.args[0]
        self.assertEqual(sent.source, receipt)
        self.assertEqual((sent.media_format, sent.sample_rate_hz, sent.duration_us),
                         ('wav', 16000, 500000))
        self.assertNotIn('audio', repr(uploaded))

    def test_bad_upload_receipt_prevents_start(self):
        audio = wav_bytes()
        for receipt in (UploadedSource('', hashlib.sha256(audio).hexdigest(), len(audio)),
                        UploadedSource('private-source', '0' * 64, len(audio)),
                        UploadedSource('private-source', hashlib.sha256(audio).hexdigest(), 1),
                        None):
            with self.subTest(receipt=receipt):
                start = Mock()
                with self.assertRaisesRegex(ValueError, 'INPUT_INTEGRITY'):
                    dispatch_media(audio, 'wav', 16000, upload=Mock(return_value=receipt), start=start)
                start.assert_not_called()

    def test_upload_failure_never_starts_or_retries(self):
        upload, start = Mock(side_effect=RuntimeError('upload failed')), Mock()
        with self.assertRaises(RuntimeError):
            dispatch_media(wav_bytes(), 'wav', 16000, upload=upload, start=start)
        self.assertEqual(upload.call_count, 1)
        start.assert_not_called()


if __name__ == '__main__':
    unittest.main()
