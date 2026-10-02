# ADR-AUDIO-003 — Product microphone runtime and scoped writer

Status: implementation candidate, PENDING_FINAL_PUBLICATION.
Stage: 8.3 only. Parent: a1b9a8a56fe62ceb2332a0e7147ab537f8ce3431.

## Decision

The product owns an AudioRecord adapter with the exact admitted PCM S16LE,
16000 Hz, mono format. No format fallback, VAD, semantic rotation, overlap,
transcription or cloud transport is introduced. The isolated Capture PoC is
unchanged and is not an app dependency.

The audio-priority reader performs bounded native reads, real RMS measurement,
and a bounded memory queue. A separate serialized worker forwards PCM through
ProductAudioWriterPort to the accepted encrypted vault. The transport buffer is
160000 bytes, the existing bridge maximum; it is not a product chunking policy.
No plaintext file, export, raw audio log or parallel recording store is added.
Borrowed buffers and queued blocks are zeroed after consumption or termination.
Queue exhaustion stops capture with an explicit error; it never drops audio and
continues as a successful recording.

The queue holds at most 320 native read blocks (512000 bytes, 16 seconds at the
admitted format). This headroom covers measured screen-off publication stalls;
it is not an unbounded queue or a guarantee that a stopped process retains its
in-memory tail. A worker tick drains at most 20 blocks so commands cannot starve
behind a continuously replenished producer. Pause and confirmed Stop signal the
reader immediately, then finish bounded draining and durable work off main.

Catalog snapshots bulk-read physical sources, manifests and microfiles restricted
to the exact asset under the existing lease. Every prior per-row identity, frame,
provenance and publication check remains; schema, fsync and durability barriers are
unchanged. This removes repeated SQLCipher round trips for every historical unit.

## Authority and lifecycle

Stage 7.4 security sections D/J permit a user-started service writer to continue
after UI lock. Ordinary foreground sessions still revoke with zero grace on
pause/background/screen-off, as required by ADR-PERSISTENCE-002. This ADR admits
a separate process-local exact-recording writer capability; it grants no reader,
deletion, export or catalog rights. The runtime retires the ordinary vault before
opening the exclusive writer and retains failed closes for retry.

Initial microphone start, native recorder publication and reader launch execute
under the real foreground authorization boundary. Resume consumes a fresh
foreground grant also bound to the original non-revivable writer authority.
Explicit runtime revocation stops native capture. Screen-off only revokes UI
authority; an already active service writer may finish its exact recording.
No persisted capability or background microphone restart is possible.

The unexported microphone foreground service is START_NOT_STICKY. Notifications
contain generic state and opaque per-recording action routing only. Pause stops
and releases AudioRecord. Resume goes through visible UI and real authentication.
Stop opens confirmation; dismissal leaves capture running. Confirmation releases
capture, drains accepted PCM and invokes durable finalization once. SAVED requires
successful verified finalization; uncertainty remains INTERRUPTED. A reader that
has not exited retains ownership rather than claiming safe cleanup.

## Timeline and recovery

Only accepted PCM frames advance logical time. A pause adds no frames. Resume
keeps recording/asset/session identity and creates a new physical segment at the
committed logical end. Each bounded storage unit preserves physical provenance.

Recovery discovery uses the existing encrypted Room journal, without a new schema
or plaintext index. It requires real authentication and returns bounded pages of
content-free summaries. Continuation reacquires authority and revalidates the
exact source, no pending intent, no finalization, and strict recovery of every
committed unit, including quarantine outcomes. Partial-readable audio does not
by itself authorize continuation. Process death never resumes the microphone.
An uncommitted in-memory tail can be lost; the UI must report the authenticated
durable prefix rather than claiming a completed Stop.

## Verification and limits

JVM tests cover frame accounting, repeated commands, physical provenance,
uncertain writes/finalization, exact-source authority and strict continuation.
Existing system-authentication, encrypted persistence and original-audio tests
remain mandatory on API 28/36. Physical POCO evidence is a separate required gate;
emulator results and synthetic tests cannot replace it. Repository status remains
PENDING_FINAL_PUBLICATION until external exact-SHA CI, independent review,
artifact audit and the specified Google Sheet readback all pass.
