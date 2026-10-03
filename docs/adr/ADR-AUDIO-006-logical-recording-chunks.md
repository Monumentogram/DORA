# ADR-AUDIO-006: logical recording and technical source views

Status: PENDING_FINAL_PUBLICATION. Stage 8.4C only. Baseline:
e7370bae6ee4d862d2a04e9d52c53bbeb68c6f6f. The owner's Stage 8.4C request
is the implementation specification; frozen ASR and ADR-AUDIO-001/002 and
ADR-VAD-002 semantics remain authoritative.

A logical recording is the user's product object owned by RecordingId. Original
audio is its exact finalized OriginalAudioReference (including AudioIdentity,
encoding version, digest and frames). A storage unit is internal encrypted
persistence, never a consent unit. A technical chunk is a persisted technical
segment ID and source view opened by Start, Resume or CAP. A semantic segment is
an independent VAD view and may cross technical boundaries. Overlap is processing
context referencing existing source frames, never additional canonical audio.

Derive immutable projections from existing SQLCipher v3 segmentation rows inside
OriginalAudioPort.withAvailable's existing source lease. Publish only a complete
validated result. No second journal, materialization, PCM copy, migration, hash,
random ID, new source identity or mutable latest-source selection is introduced.
Malformed/incomplete metadata returns a typed incomplete result without partial
chunks. No rows returns NOT_EVALUATED and the exact valid source; no fabricated
boundaries. Partial Recovery never creates a finalized reference.

Pair each OPEN with exactly one CLOSE; require matching IDs, epoch, profile and
overlap. Sorted canonical ranges partition [0, source.frames). Start begins at 0;
CAP continues the same epoch after an exact 600-second close; Resume uses a fresh
epoch after PAUSE or an exact CAP coincident with Pause. Final CLOSE may be STOP,
PAUSE (Stop while paused) or CAP (Stop exactly at cap). No empty terminal chunk
is required. Epoch starts equal their persisted first technical ID. Semantic
ranges and degraded observations retain their separate identities and flags.

Frame endpoints are half-open. Exact frame timestamps use 62500 integer ns per
frame; arbitrary timestamp lookup floors within a frame, with checked overflow.
Existing AudioTimeline.durationUs remains its documented floor projection.
Chunk-local coordinates start at processingFirstFrame, including overlap.
Lookup returns exactly one canonical owner and every processing view; final
endpoints are valid mappings but own no frame.

AuthorizationUnitId wraps RecordingId only. Pure consent contract state consumes
one automatic ASK opportunity per recording, DEFERRED retains that consumption,
ALWAYS and explicit MANUAL actions scope the recording. Batches deduplicate by
RecordingId. This state is not a persisted consent grant or PCM access authority.
Every future admission or late callback still requires exact withAvailable;
deletion, stale provenance and revoked access cannot be bypassed by these models.

Implementation and publication must retain all prior Recovery, recording, VAD,
encrypted persistence and CI gates. Acceptance requires deterministic 1/3/8h,
restart/process-death tests, physical POCO exact-source readback, independent
review with zero unresolved P0/P1/P2, exact-SHA CI and Sheet verification.
8.5 remains NOT_STARTED, Stage 8 IN_PROGRESS, PERF-REC-001 deferred/non-blocking,
DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE OPEN. No Cloud/backend/upload is admitted.
