# ADR-AUDIO-007: authenticated logical recording recovery

Status: PENDING_FINAL_PUBLICATION. Scope: owner Stage 8.5 request.
Exact baseline: b035d8e35e3fc604ae3b805a1cfd23ea66963804.
Stage 8.6 and Cloud/ASR remain NOT_STARTED. Security restoration remains OPEN.

## Baseline audit (before implementation)

- EncryptedAudioVault.recordingRecoveryPage enumerates 20 identities in asset-ID
  order, excluding deletion tombstones. Any failed candidate aborts the page.
- recordingRecovery separately leases source-state inspection, reconcile, extract,
  strict continuation and catalog inspection. It returns summary/failure/canResume
  and next ordinal, with no distinct completion or metadata state. Deletion and
  contention return failures instead of candidate observations.
- RecoveryAudioBridge.reconcile authenticates an exact pending append before
  promoting it, or strictly verifies the reserved source list before finalizing.
  Ambiguous/missing pending data remains fenced; no namespace is reused.
- bridge.extract authenticates ordered units. Missing/key-unavailable tails may
  expose an authenticated contiguous prefix; corrupt/authentication-rejected data
  rejects extraction under ADR-AUDIO-001. It never joins across a gap.
- bridge.verifyContinuation requires no pending intent/finalization and strict
  authentication of all units including quarantine outcomes. Preserve this gate.
- RecordingRecovery carries no PCM and creates no OriginalAudioReference. Keep
  that separation; only OriginalAudioLifecycle may produce a final reference.
- RecordingRecoveryCard loads only through a manual diagnostic button, displays
  summaries, requires participant acknowledgement and explicit permission/start.
  It lacks automatic authorized discovery and distinct nonresumable/deleted states.
- RecordingSession persists the first OPEN after the first PCM unit. A kill can
  leave durable audio without OPEN, or OPEN without CLOSE. Logical projection
  requires complete pairs and permits Resume only after PAUSE/CAP.
- RecordingSegmentation creates fresh observer/reducer state per session. Open
  semantic intervals are process-local; there is no durable interruption marker.
- Current durable OPEN validation requires an existing unit, so a zero-length
  OPEN cannot currently be created by the accepted writer. Prospective ordering
  must handle its zero-length crash window explicitly, retaining the evidence.

## Decision

Keep REC-I3, RecoveryAudioBridge, encrypted journal/catalog, keys and quarantine
as the only recovery system. Add a content-free recovery snapshot and a pure
metadata reconciliation planner. Hold the existing source lease across reconcile,
read, strict continuation, source inspection and metadata reconciliation.

Completion states distinguish FINALIZED, RECOVERABLE_PARTIAL,
PARTIAL_NOT_RESUMABLE, SOURCE_CORRUPT, SOURCE_UNAVAILABLE, DELETION_PENDING and
DELETED. Keep typed key/authentication/storage failures. Metadata states are
independent of canonical readability. Duration is authenticated frames / 16000;
never use UI time, RAM counters, VAD or exit information as audio evidence.

Persist technical OPEN before the first append for its chunk. Add RECOVERY as an
explicit interrupted technical CLOSE reason, TECHNICAL_ABORT for an OPEN with no
durable frames, and RECOVERY_INTERRUPTED as a semantic uncertainty observation.
All are additive string values in the existing encrypted segmentation table;
no existing START/RESUME/CAP/PAUSE/STOP meaning changes. Reconciliation is stable
under repetition, never generates new chunk IDs, and never deletes OPEN evidence.
Projection excludes only validated aborted empty chunks and accepts fresh-epoch
Resume after RECOVERY. Canonical ownership remains an exact partition.

Recovery never constructs a provisional final reference. Resume reauthenticates
the same RecordingId/AudioAssetId/session, starts a new capture epoch and VAD
state, and requires explicit participant acknowledgement and microphone permission.
Discovery performs no microphone or playback action. Deletion and revocation win.

## Tail-loss contract

ADR-AUDIO-001 freezes 160000-byte maximum storage units, not a total product
tail-loss guarantee. Technical Plan section 27 calls five seconds a starting
target; its PoC hard-kill criterion is separately scoped. ADR-AUDIO-005 freezes
a 512000-byte / 320-block admission budget spanning queued, assembled, sealed and
in-flight PCM (16 seconds of admitted canonical input). It does not bound unheard
or unadmitted wall time. UI therefore reports no numeric lost-tail guarantee.
Campaign receipts must separately report measured admitted-minus-recovered frames
and zero loss of authenticated durably committed frames.

## Acceptance

The owner's complete Stage 8.5 matrix is binding: deterministic fault and metadata
cases, API28/API36 process death, POCO process kill/explicit Resume/reboot and
permission revocation, inherited 414 Recovery, all repository regression gates,
independent review, exact-SHA four-job CI, bounded canary leak audit and Sheet
readback. No source document self-certifies the terminal PASS.
