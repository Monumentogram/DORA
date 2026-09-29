# ADR-0014: Alpha media preflight before provider dispatch

Status: OWNER-AUTHORIZED PROSPECTIVE PRODUCT BOUNDARY; Stage-0 implementation only.
Date: 2026-09-29. Baseline: `a3da33da6a0af6e6e56f54ad6dddcc3071961e7b`.

The Project Owner explicitly assigns locally decidable media integrity to DORA
when closing the remaining 6.2D technical cases. This extends
[ADR-0009](ADR-0009-alpha-cloud-execution-boundary.md); it does not implement or
admit the production backend, execute 6.3, or change measured provider quality.

DORA's controlled upload/worker boundary must validate actual immutable bytes
before provider Start, and before upload where it owns the bytes. One canonical
parser is shared with local corpus validation. The prospective dispatch profile
is RIFF/WAVE, PCM signed 16-bit little-endian, mono, 16000 Hz, duration 0.5–600 s.
The 0.5 s lower bound follows the current standard Transcribe
[service quota](https://docs.aws.amazon.com/general/latest/gr/transcribe.html),
not its separate billing increment. Native-rate source acquisition can use the
same structural parser, but cannot bypass the dispatch profile.

RIFF and data extents must match physical bytes, chunks and padding must fit,
fmt must precede exactly one data chunk, and all PCM fields and frames must be
consistent. PCM fmt may be 16 bytes or 18 bytes with zero extension size; the
existing authored synthetic WAV uses the latter. Only inert JUNK chunks are
additionally accepted. Other ancillary/embedded extent formats are outside this
bounded upload profile. The detected WAV type must agree with the declaration.
Existing error codes are retained; malformed media is never repaired silently.

The Stage-0 dispatch gateway binds validated bytes, digest, duration and format
through a verified upload receipt to the adapter. Transport adapters must verify
the exact source object and derive wire media fields from that binding. A test
callback alone does not establish deployed enforcement. Future production
integration must use this gate and retain consent, identity, security, retry,
budget and deletion controls independently.

Historical AWS observations remain immutable: wrong-format completed (raw FAIL),
truncated completed with unproved extent (raw INCOMPLETE), and the old duplicate
test did not establish a collision. New local rejection evidence can satisfy
current wrong-format/truncated/minimum-duration product cases, without claiming
AWS rejects those declarations. Missing-input/access adjudications require their
retained evidence; generic BadRequest is not reclassified by message guessing.

A new valid same-name collision still requires prospective publication, budget
proof, two bounded live synthetic Starts and cleanup. No quality recording is
rerun. RU 17/179 and EN 37/181, OD-62D-EN-01, owner-only scope, second-speaker
revalidation, and all previously recorded limitations remain unchanged.
