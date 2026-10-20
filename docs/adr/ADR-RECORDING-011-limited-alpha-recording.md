# ADR-RECORDING-011 — Limited five-minute Alpha

Status: owner-authorized implementation, 2026-10-20. Acceptance remains pending
verification. Baseline: `6f5e87317b163e4567ffa76da3cd3191bcfac826` (PR99, open draft).

The owner's Stage 8 Alpha fast-track decision introduces a separate, versioned
`STAGE8_ALPHA_LIMITED_RECORDING_READY` profile. It does not close historical
Stage8.6 or convert LONG01/LONG02 failures into passes.

## Runtime

Every product logical recording accepts at most 4,800,000 canonical PCM frames
(300 seconds at the unchanged 16kHz mono PCM16 format). Capture admission trims
the boundary block under the same monitor as Pause, before queue ownership
transfers. The session independently clips its input. The authenticated recording
writer rejects an append whose contiguous end exceeds the limit. There is no
configurable production limit or release bypass.

Pause, resume, technical chunks and transport units retain the cumulative frame
count. Restart uses the authenticated recovery prefix; uncommitted lost tail is
not reported as saved audio. Sources already at or above the limit remain
readable through existing authorized recovery/read interfaces but cannot acquire
new microphone continuation in this profile. Historical sources are not trimmed,
deleted, finalized or migrated by this change.

At the boundary the reader stops. The control owner confirms native release,
drains admitted input and invokes existing Stop/Finalize. Unconfirmed shutdown,
write failure or uncertain finalization remains interrupted. Only verified durable
completion publishes SAVED. Elapsed and remaining time count audio frames, not
wall time. The existing 256,000 outstanding-frame budget remains unchanged.

## Reuse and verification

Reuse encrypted Room/SQLCipher, Keystore, AEAD/AAD, REC-I3, OriginalAudioReference,
source ownership, segmentation and authentication. No schema, encryption,
durability, tombstone or recovery algorithm changes are admitted.

Boundary and race tests cover capture admission, direct session input, the scoped
writer, pause/resume at 4:59, restart, failures and independent recordings. Real
encrypted adapters on API28/API36 establish authenticated reopen/readback and
recovery. Old long-recording tests remain unchanged; tests whose premise requires
more than five minutes are explicitly deferred in the limited profile. Their
baseline evidence and the original acceptance contracts remain historical.

## Separate gates

Software acceptance and Stage9 implementation admission are distinct from signed
Alpha release. Stage9 may consume the accepted OriginalAudioReference and secure
reader contracts for independent development; this ADR implements no ASR, text
versioning, Cloud transport, account or provider runtime.

Full limited Alpha acceptance additionally requires one <=5-minute screen-off
smoke on a separate safe physical device. Until available:
`PHYSICAL_ALPHA_SMOKE = NOT_RUN / SAFE_DEVICE_UNAVAILABLE`.

`DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE` remains OPEN and blocks signed user Alpha.
No physical POCO operation is authorized. `LONG02_ROOT_CAUSE = NOT_PROVEN`,
`LONG02_PHYSICAL_PROTECTION = NOT_INSTALLED`, `STAGE_8_6 = NOT_READY`.

Hour/three-hour recording, full device/reliability matrices, industrial persistence,
large-catalog performance, battery, 200 physical cycles and 99.5% reliability are
Beta/Reliability backlog, not prerequisites of this new limited software profile.
