"""Stage-0 candidate upload/dispatch boundary; no SDK, credentials or network.

Transport callbacks remain responsible for authorization, scoped storage,
version/checksum verification and durable dispatch accounting. This module
cannot make an untrusted callback safe and is not a deployed DORA backend.
Historical negative-fixture runners do not use this prospective product path.
"""
from dataclasses import dataclass, field
from typing import Callable

from tools.cloud62d_media import preflight_media


@dataclass(frozen=True)
class ValidatedMedia:
    audio: bytes = field(repr=False)
    sha256: str
    duration_us: int
    sample_rate_hz: int
    media_format: str


@dataclass(frozen=True)
class UploadedSource:
    """A transport-verified immutable source version and its content identity."""
    input_ref: str
    sha256: str
    byte_count: int


@dataclass(frozen=True)
class SubmissionMedia:
    source: UploadedSource
    media_format: str
    sample_rate_hz: int
    duration_us: int


def dispatch_media(audio: bytes, declared_format: str, declared_sample_rate_hz: int,
                   *, upload: Callable, start: Callable):
    """Reject invalid bytes/declarations before upload, bind receipt before Start.

    No bypass flag, coercion, mutable input, hidden retry, or provider-specific
    request type. The adapter must derive its wire media fields from the
    immutable SubmissionMedia it receives, never the original declaration.
    """
    facts = preflight_media(audio, declared_format, declared_sample_rate_hz,
                            min_duration_us=500_000)
    validated = ValidatedMedia(audio, facts['sha256'], facts['duration_us'],
                               facts['sample_rate_hz'], 'wav')
    source = upload(validated)
    if (type(source) is not UploadedSource or type(source.input_ref) is not str
            or not source.input_ref or source.sha256 != facts['sha256']
            or type(source.byte_count) is not int or source.byte_count != len(audio)):
        raise ValueError('INPUT_INTEGRITY')
    return start(SubmissionMedia(source, 'wav', facts['sample_rate_hz'], facts['duration_us']))
