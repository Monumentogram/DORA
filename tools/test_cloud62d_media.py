"""Synthetic, provider-independent media preflight regressions."""

import hashlib
import io
import struct
import unittest
import wave
from pathlib import Path

from tools.cloud62d_media import parse_pcm16_wav, preflight_media
from tools.cloud62d_owned.corpus import validate_wav


def wav_bytes(frames=16000, rate=16000):
    out = io.BytesIO()
    with wave.open(out, 'wb') as writer:
        writer.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
        writer.writeframes(b'\x00\x00' * frames)
    return out.getvalue()


def riff(*chunks):
    body = b'WAVE' + b''.join(
        name + struct.pack('<I', len(payload)) + payload + (b'\x00' if len(payload) & 1 else b'')
        for name, payload in chunks
    )
    return b'RIFF' + struct.pack('<I', len(body)) + body


FMT = struct.pack('<HHIIHH', 1, 1, 16000, 32000, 2, 16)


class MediaPreflightTests(unittest.TestCase):
    def test_existing_project_synthetic_pcm_fmt18_zero_extension(self):
        path = Path(__file__).parent / 'cloud62d_aws/fixtures/engineering-synthetic-ru.wav'
        self.assertEqual(preflight_media(path.read_bytes(), 'wav', 16000)['frames'], 53120)
        for extra in (b'\x01\x00', b'\x00\x00\x00\x00'):
            with self.assertRaisesRegex(ValueError, 'UNSUPPORTED_WAV_FORMAT'):
                preflight_media(riff((b'fmt ', FMT + extra), (b'data', b'\x00\x00' * 8000)), 'wav', 16000)

    def test_corpus_validator_delegates_strict_chunk_order_and_keeps_native_rate(self):
        self.assertEqual(validate_wav(wav_bytes(48000, 48000), evaluation=False)['sample_rate_hz'],
                         48000)
        reversed_chunks = riff((b'data', b'\x00\x00'), (b'fmt ', FMT))
        with self.assertRaisesRegex(ValueError, 'INVALID_WAV_STRUCTURE'):
            validate_wav(reversed_chunks)

    def test_valid_synthetic_owner_and_long_composite_metadata(self):
        for frames in (53120, 20 * 16000, 300 * 16000, 600 * 16000):
            with self.subTest(frames=frames):
                body = wav_bytes(frames)
                result = preflight_media(body, 'wav', 16000)
                self.assertEqual(result, {
                    'sha256': hashlib.sha256(body).hexdigest(), 'frames': frames,
                    'sample_rate_hz': 16000, 'duration_us': frames * 1_000_000 // 16000,
                    'bytes': len(body),
                })

    def test_native_source_profile_accepts_other_rate_but_dispatch_rejects(self):
        body = wav_bytes(48000, 48000)
        self.assertEqual(parse_pcm16_wav(body, expected_sample_rate_hz=None)['sample_rate_hz'], 48000)
        with self.assertRaisesRegex(ValueError, 'EVALUATION_WAV_REQUIRES_16000_HZ'):
            preflight_media(body, 'wav', 16000)

    def test_detected_wav_must_match_declared_format_and_rate(self):
        body = wav_bytes()
        with self.assertRaisesRegex(ValueError, 'MEDIA_FORMAT_MISMATCH'):
            preflight_media(body, 'mp3', 16000)
        with self.assertRaisesRegex(ValueError, 'MEDIA_SAMPLE_RATE_MISMATCH'):
            preflight_media(body, 'wav', 8000)

    def test_standard_batch_minimum_is_exactly_500_ms(self):
        with self.assertRaisesRegex(ValueError, 'WAV_DURATION_OUT_OF_RANGE'):
            preflight_media(wav_bytes(7999), 'wav', 16000)
        self.assertEqual(preflight_media(wav_bytes(8000), 'wav', 16000)['frames'], 8000)
        self.assertEqual(preflight_media(wav_bytes(8001), 'wav', 16000)['frames'], 8001)

    def test_riff_declared_length_must_exactly_match_actual(self):
        body = wav_bytes()
        for damaged in (body[:-101], body + b'x', body[:4] + struct.pack('<I', len(body)) + body[8:]):
            with self.subTest(size=len(damaged)):
                with self.assertRaisesRegex(ValueError, 'TRUNCATED_OR_TRAILING_WAV'):
                    preflight_media(damaged, 'wav', 16000)

    def test_chunk_header_body_and_pad_must_fit(self):
        valid = riff((b'fmt ', FMT), (b'JUNK', b'x'), (b'data', b'\x00\x00'))
        self.assertEqual(parse_pcm16_wav(valid)['frames'], 1)
        missing_pad = valid[:valid.index(b'data') - 1] + valid[valid.index(b'data'):]
        missing_pad = missing_pad[:4] + struct.pack('<I', len(missing_pad) - 8) + missing_pad[8:]
        with self.assertRaisesRegex(ValueError, 'TRUNCATED_WAV_CHUNK|INVALID_WAV_STRUCTURE'):
            parse_pcm16_wav(missing_pad)
        for damaged in (riff((b'fmt ', FMT)) + b'X', riff((b'fmt ', FMT))[:-1]):
            with self.assertRaises(ValueError):
                parse_pcm16_wav(damaged)

    def test_alternate_embedded_audio_extents_are_rejected(self):
        for name in (b'LIST', b'fact', b'smpl', b'wavl', b'ds64'):
            body = riff((b'fmt ', FMT), (name, b'\x00\x00'), (b'data', b'\x00\x00'))
            with self.subTest(name=name):
                with self.assertRaisesRegex(ValueError, 'INVALID_WAV_STRUCTURE'):
                    parse_pcm16_wav(body)
        for magic in (b'RIFX', b'RF64'):
            body = magic + wav_bytes(8000)[4:]
            with self.assertRaisesRegex(ValueError, 'INVALID_WAV_CONTAINER'):
                preflight_media(body, 'wav', 16000)

    def test_adjusted_riff_length_cannot_hide_truncated_data_or_partial_pcm(self):
        original = wav_bytes(8000)
        truncated = original[:-2]
        truncated = truncated[:4] + struct.pack('<I', len(truncated) - 8) + truncated[8:]
        with self.assertRaisesRegex(ValueError, 'TRUNCATED_WAV_CHUNK'):
            preflight_media(truncated, 'wav', 16000)
        partial_frame = riff((b'fmt ', FMT), (b'data', b'\x00' * 16001))
        with self.assertRaisesRegex(ValueError, 'INVALID_PCM_LENGTH'):
            preflight_media(partial_frame, 'wav', 16000)

    def test_fmt_precedes_single_data_and_ambiguous_chunks_rejected(self):
        for body, code in (
            (riff((b'data', b'\x00\x00'), (b'fmt ', FMT)), 'INVALID_WAV_STRUCTURE'),
            (riff((b'fmt ', FMT), (b'fmt ', FMT), (b'data', b'\x00\x00')), 'DUPLICATE_WAV_CHUNK'),
            (riff((b'fmt ', FMT), (b'data', b'\x00\x00'), (b'data', b'\x00\x00')), 'DUPLICATE_WAV_CHUNK'),
            (riff((b'fmt ', FMT)), 'INVALID_WAV_STRUCTURE'),
        ):
            with self.subTest(code=code):
                with self.assertRaisesRegex(ValueError, code):
                    preflight_media(body, 'wav', 16000)

    def test_pcm_header_fields_must_be_coherent(self):
        bad_fields = (
            (3, 1, 16000, 32000, 2, 16), (1, 2, 16000, 64000, 4, 16),
            (1, 1, 16000, 123, 2, 16), (1, 1, 16000, 32000, 4, 16),
            (1, 1, 16000, 32000, 2, 8),
        )
        for fields in bad_fields:
            with self.subTest(fields=fields):
                body = riff((b'fmt ', struct.pack('<HHIIHH', *fields)), (b'data', b'\x00\x00'))
                with self.assertRaisesRegex(ValueError, 'UNSUPPORTED_WAV_FORMAT'):
                    preflight_media(body, 'wav', 16000)

    def test_frame_divisibility_and_duration_bounds(self):
        with self.assertRaisesRegex(ValueError, 'INVALID_PCM_LENGTH'):
            preflight_media(riff((b'fmt ', FMT), (b'data', b'\x00')), 'wav', 16000)
        with self.assertRaisesRegex(ValueError, 'INVALID_PCM_LENGTH'):
            preflight_media(wav_bytes(0), 'wav', 16000)
        with self.assertRaisesRegex(ValueError, 'WAV_DURATION_OUT_OF_RANGE'):
            preflight_media(wav_bytes(15999), 'wav', 16000, min_duration_us=1_000_000)
        with self.assertRaisesRegex(ValueError, 'WAV_DURATION_OUT_OF_RANGE'):
            preflight_media(wav_bytes(16001), 'wav', 16000, max_duration_us=1_000_000)


if __name__ == '__main__':
    unittest.main()
