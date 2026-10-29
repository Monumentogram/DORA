# ADR-SIGNAL-002: Signal-inspired presentation on the owner Alpha baseline

Date: 2026-10-29. Status: accepted within the owner's Stage 10 implementation scope.

The owner requests a working Android redesign with a blue accent, light/dark themes,
real recording and transcript flows, and unchanged data, identity and recognition
mechanisms. This supersedes the earlier mint-accent presentation direction; it does
not admit the deferred capture-envelope redesign from the preparation plan.

## Decision

- Keep Compose and the existing recording, recovery, transcript, Local and Cloud
  ports. Introduce a presentation-only recording library and grouped settings.
- Use semantic light/dark blue palettes. Apply the existing scalar recording level
  to a horizontal history; capture cadence, audio callbacks and storage stay intact.
- Load the library through authenticated, paged recovery and encrypted transcripts
  on a worker. Clear its presentation data on background and reject stale callbacks.
- Do not invent recording metadata. The current schema has neither user titles nor
  capture wall-clock dates. Use the active transcript's first line as a display title,
  fall back to a shortened identity, and label transcript timestamps as such.
  A dedicated recording title/date schema is outside this UI migration.
- No search field is offered because this baseline has no recording search port.
- Provide original-audio playback with AudioTrack and the existing authenticated
  ProtectedPcm reader, bounded by the current five-minute Alpha recording cap.
  No plaintext audio file is written; release/zero samples on completion or cancel.
  Pause on background, disposal, audio-focus loss and transcript operation busy state.
  Opening a vault session can revoke another session, so playback is disabled while
  transcript actions own it. Tests isolate their fixture session from the home loader.
- Preserve existing revision checks, explicit re-recognition, edit history and draft
  close confirmation. Existing background/rotation draft clearing remains; this change
  does not promise draft persistence across those security lifecycle boundaries.
- Keep application IDs, version fields, signer, native payloads, model, Cloud identity,
  backend and all encrypted formats unchanged. Model import and Cloud registration use
  their existing paths. No physical-device install or merge is part of this change.

## Consequences and validation

The library reflects real persisted data and honest operation states. RU/EN resources
cover the new UI and primary edited controls; inherited diagnostic/recovery messages
may still be Russian. Theme, large-text, recording, editor, original playback and native
recognition evidence is tracked in the Stage 10 result. Emulator evidence is separate
from POCO acceptance, which remains DEVICE_VALIDATION_PENDING.
