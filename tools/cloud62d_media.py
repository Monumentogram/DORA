"""Provider-independent, bounded PCM16 RIFF/WAVE preflight.

Only structural media facts leave this module.  Provider adapters must call
``preflight_media`` before upload or job dispatch.
"""

from __future__ import annotations

import hashlib
import struct


PREFLIGHT_VERSION = 'dora-alpha-media-preflight-v0.1'
MAX_WAV_BYTES = 120_000_044
MIN_STANDARD_BATCH_DURATION_US = 500_000
MAX_DISPATCH_DURATION_US = 600_000_000


def _require(ok: bool, code: str) -> None:
    if not ok:
        raise ValueError(code)


def parse_pcm16_wav(
    data: bytes,
    *,
    expected_sample_rate_hz: int | None = 16000,
    min_duration_us: int | None = None,
    max_duration_us: int | None = None,
) -> dict:
    """Validate exact RIFF extent and canonical PCM16 mono chunks.

    ``expected_sample_rate_hz=None`` is for preserved native-rate corpus
    sources; dispatch requires 16000 Hz.  Duration bounds are explicit so a
    provider-specific minimum is never inferred from an earlier trial.
    """
    _require(type(data) is bytes and 12 <= len(data) <= MAX_WAV_BYTES, 'INVALID_WAV_SIZE')
    _require(data[:4] == b'RIFF' and data[8:12] == b'WAVE', 'INVALID_WAV_CONTAINER')
    _require(struct.unpack_from('<I', data, 4)[0] + 8 == len(data), 'TRUNCATED_OR_TRAILING_WAV')
    _require(expected_sample_rate_hz is None or
             type(expected_sample_rate_hz) is int and expected_sample_rate_hz > 0,
             'MEDIA_SAMPLE_RATE_MISMATCH')
    _require(min_duration_us is None or type(min_duration_us) is int and min_duration_us >= 0,
             'WAV_DURATION_OUT_OF_RANGE')
    _require(max_duration_us is None or type(max_duration_us) is int and max_duration_us >= 0,
             'WAV_DURATION_OUT_OF_RANGE')
    _require(min_duration_us is None or max_duration_us is None or
             min_duration_us <= max_duration_us, 'WAV_DURATION_OUT_OF_RANGE')

    offset = 12
    seen: set[bytes] = set()
    fmt = None
    data_size = None
    while offset < len(data):
        _require(offset + 8 <= len(data), 'TRUNCATED_WAV_CHUNK')
        name, size = struct.unpack_from('<4sI', data, offset)
        offset += 8
        end = offset + size
        _require(end <= len(data) and end + (size & 1) <= len(data), 'TRUNCATED_WAV_CHUNK')
        _require(name not in seen, 'DUPLICATE_WAV_CHUNK')
        # Metadata/list containers can imply another playable extent.  The
        # admitted profile has only fmt/data and inert alignment JUNK chunks.
        _require(name in (b'fmt ', b'data', b'JUNK'), 'INVALID_WAV_STRUCTURE')
        seen.add(name)
        if name == b'fmt ':
            _require(data_size is None and size in (16, 18), 'INVALID_WAV_STRUCTURE' if data_size is not None
                     else 'UNSUPPORTED_WAV_FORMAT')
            # PCM WAVEFORMATEX may carry cbSize=0; the authored synthetic
            # fixture uses this valid form. No extension data is interpreted.
            _require(size == 16 or data[offset + 16:offset + 18] == b'\x00\x00',
                     'UNSUPPORTED_WAV_FORMAT')
            fmt = struct.unpack_from('<HHIIHH', data, offset)
        elif name == b'data':
            _require(fmt is not None, 'INVALID_WAV_STRUCTURE')
            data_size = size
        offset = end + (size & 1)
    _require(offset == len(data) and fmt is not None and data_size is not None,
             'INVALID_WAV_STRUCTURE')

    codec, channels, rate, byte_rate, block_align, bits = fmt
    _require(codec == 1 and channels == 1 and bits == 16 and block_align == 2
             and byte_rate == rate * 2 and 8000 <= rate <= 192000,
             'UNSUPPORTED_WAV_FORMAT')
    _require(expected_sample_rate_hz is None or rate == expected_sample_rate_hz,
             'EVALUATION_WAV_REQUIRES_16000_HZ')
    _require(data_size > 0 and data_size % block_align == 0, 'INVALID_PCM_LENGTH')
    frames = data_size // block_align
    duration_us = frames * 1_000_000 // rate
    _require((min_duration_us is None or duration_us >= min_duration_us) and
             (max_duration_us is None or duration_us <= max_duration_us),
             'WAV_DURATION_OUT_OF_RANGE')
    return {'sha256': hashlib.sha256(data).hexdigest(), 'frames': frames,
            'sample_rate_hz': rate, 'duration_us': duration_us, 'bytes': len(data)}


def preflight_media(
    data: bytes,
    declared_format: str,
    declared_sample_rate_hz: int,
    *,
    min_duration_us: int | None = MIN_STANDARD_BATCH_DURATION_US,
    max_duration_us: int = MAX_DISPATCH_DURATION_US,
) -> dict:
    """Validate the fixed cloud profile before any provider call."""
    metadata = parse_pcm16_wav(data, expected_sample_rate_hz=16000,
                                min_duration_us=min_duration_us,
                                max_duration_us=max_duration_us)
    _require(declared_format == 'wav', 'MEDIA_FORMAT_MISMATCH')
    _require(type(declared_sample_rate_hz) is int and declared_sample_rate_hz == 16000,
             'MEDIA_SAMPLE_RATE_MISMATCH')
    return metadata
