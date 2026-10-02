# ADR-AUDIO-002 — Durable original audio lifecycle

Status: implemented candidate; 8.2C = PENDING_FINAL_PUBLICATION.
Target: ORIGINAL_AUDIO_LIFECYCLE_RUNTIME_READY.
Parent: 5af0f28046237a42d2d5b8c51a3b419013d7ee8b.

## Authority and scope

The frozen ASR data/versioning contract at 00ce84bed18fe93101fe6a32c382b4ceed4a0e45
orders Original Audio → Cloud Authorization → ASR Processing Versions → User Edits →
Active Transcript. ADR-0018 and the Stage 7.2D transcript contract govern future
provenance/edits. This change admits only original-source identity and a local
dependent-work boundary. It does not persist transcripts, jobs or consent.

Privacy/retention v0.4 at 75e45a152800c9c1589db201fc71fec9a6200fef remains authority,
including its inherited v0.3 lifecycle matrix. ADR-AUDIO-001 and the accepted
Stage 8.2 encrypted persistence/deletion boundaries remain mandatory. Historical
Recovery implementation, contracts and evidence are unchanged.

## Exact source reference

OriginalAudioReference contains encoding version 1, the existing AudioIdentity,
a lowercase SHA-256 digest and total frames. Encoding version is NOT a processing
version or a mutable source revision counter. AudioAssetId is the exact immutable
source version; recordingId is its logical recording and sessionId its capture
identity. No latest-source inference is admitted. If a recording has multiple
catalog assets, acquire/inspect/extract/withAvailable fail COLLISION, preserving
all objects until an explicitly designed future version-selection contract exists.
A caller-supplied version, identity, frame count or digest that differs from the
authenticated source cannot authorize work. The reference is provenance, not a
capability, consent grant or promise that bytes will always remain available.

Canonical encoding is a sequential byte stream, hashed with SHA-256. Integers are
signed big-endian, with nonnegative values required in this domain. A string is a
4-byte byte-length followed by UTF-8 bytes, with no separator or trailing NUL.
UUID strings must match lowercase canonical 8-4-4-4-12 hex form. Fields in order:

1. String `DORA_ORIGINAL_AUDIO_REFERENCE`; int32 encoding version 1.
2. Strings ownerId, vaultId, recordingId, assetId, sessionId.
3. String PCM encoding from AudioFormat.PCM; int32 sample rate 16000; int32 channels 1.
4. Int32 number of finalized storage units.
5. For each unit in ordinal order: int32 ordinal; string unitId; int64 firstFrame;
   int64 frame count; string physicalSegmentId; int64 physicalFirstFrame;
   int64 sourceFrameOffset; int64 accepted manifest generation 1; string lowercase
   hex manifest digest.
6. Int64 total frames.

The catalog must have no pending intent, a nonempty finalization exactly equal
to its ordered committed segments, contiguous frames, unique unit IDs, matching
audio identities, canonical namespaces, consistent physical mapping and bounded
unit frame counts. Overflow rejects. The accepted encrypted manifests authenticate
the PCM and exact mapping. Full read authentication precedes initial reference
publication and every dependent admission; no separate plaintext-content digest,
filename, wall-clock timestamp, engine, model, provider or network state enters
identity. Hashing uses a stream and bounded read buffers.

## Persistence and migration

Room schema 2 adds only original_audio_reference: assetId primary key and RESTRICT
foreign key to audio_asset; version, digest, frames and nullable unavailableReason;
unique digest index. Every existing v1 table, column, index and relation remains
identical. The SQLCipher helper now admits only exact v1→v2 migration through Room,
verifies pinned v1 DDL before and v2 DDL after, and retains foreign-key checks.
Unknown/tampered schemas fail closed. No recreation, plaintext fallback, destructive
migration or replacement-key path is added.

Migration creates no source authority. Existing valid finalized audio receives
its identical canonical reference lazily after successful full authentication.
Partial/deleting/deleted objects and all Recovery rows retain their prior state.
Reference persistence uses the existing transaction-completion/readback boundary;
uncertainty cannot return Available. Rows are entirely inside the existing
SQLCipher journal. There are no unencrypted metadata sidecars.

## States and dependent work

Available(ref) admits that exact source within the current guarded operation.
NotFinalized cannot admit processing. DeletionPending and SourceDeleted derive
from the accepted deletion journal, with precedence over reads. StaleReference
rejects mismatched provenance. SourceUnavailable records definitive key invalidation
or authenticated corruption; missing source also returns typed unavailable.
LOCKED, KEY_UNAVAILABLE, BUSY, UNCERTAIN and retryable INCOMPLETE are AudioResult
failures, not user deletion or permanent loss. In particular, quarantine cleanup
can return INCOMPLETE once and allow the exact valid source on retry. Permanent
loss observations cannot be overwritten by successful callbacks or a reopen.

acquire, inspect, extract and withAvailable hold the same existing vault/catalog
lease across source-state validation, full authentication, reference retention and
the dependent callback. The coordinator also checks generation and App Lock epoch,
and authorizes each borrowed-buffer/callback delivery. Stale handles cannot revive
after reauthentication. Callback reentry is rejected as BUSY. Buffers follow the
existing reader's borrowed, bounded and zeroized failed-attempt contract.

Future jobs must store exact references in their own admitted encrypted journal.
Registration, dispatch, retry/reconciliation and acceptance of a late result must
each use withAvailable. Its bounded synchronous callback may commit a local
dependent decision and must not reenter audio ports, wait on another thread or
treat an earlier success as reusable permission. A callback exception does not
erase audio; the future dependent journal must reconcile its own transaction
outcome before retrying. This is not a persistent queue or cancellation worker.
DeletionPending/SourceDeleted/StaleReference/permanent unavailability require that
future work cancel or reconcile; transient failures require waiting/retry under a
new check. Remote transfer, remote cleanup and consent need separate Stage 9 gates.

## Audio-only deletion and retention

Original-only deletion uses the existing durable tombstone, key-first destruction
and remaining-step retry protocol. It never deletes source provenance or touches
transcript/edit/structured-result tables. A competing read/dependent decision either
finishes before the deletion lease or encounters its durable fence afterwards.
Uncertain deletion remains fenced until exact reconciliation. Restart, duplicate
delete, late callback and retained text cannot recreate source keys or authority.
There is no input that substitutes transcript text for audio reprocessing.

The inherited frozen LC-01-1 ANDROID_LOCAL original_audio rule retains local audio
UNTIL_EXPLICIT_USER_DELETE with automatic retention OFF. References are source
provenance under that encrypted local lifecycle, not a new payload-retention class.
No timer, TTL or competing retention matrix is introduced. The frozen minimal
tombstone retirement authority requires D+120 days AND all tombstone_proof
predicates; an unresolved copy/callback blocks retirement. This stage implements
no retirement scheduler and never expires existing tombstones. It does not claim
future remote-copy horizon predicates are satisfied. Consent revocation is separate
from deletion; local destruction is never proof of remote deletion.

## Verification and publication

Focused JVM tests cover canonical binding, malformed input, transient/permanent
states, exact references, duplicate/retry behavior, callback failure preservation,
concurrent readers/deletion and authorization revocation. Android tests use real
Room/SQLCipher/Keystore with synthetic audio, exact exported v1 migration, populated
Recovery/deletion/quarantine state, deterministic fault seams and process-death
prepare/kill/fresh-process verification. The ordinary instrumentation fallback
checks reopen only and is never counted as actual process death.

The bounded successor validator preserves the complete accepted Stage 8.2 history,
normalizes only specified additive CI steps, pins the complete device test inventory
and validates exact changed-file accounting. Existing checks remain mandatory.
The source commit cannot certify its own future CI: only an external receipt after
exact-SHA CI, artifact inspection, independent review and Sheet readback may state
PASS. API/ABI/page-size claims must match actual receipts. Physical ARM/OEM and
16-KiB runtime coverage are not inferred from x86 emulator or static native checks.

Stage 8 stays IN_PROGRESS; Stage 8.3 stays NOT_STARTED. No capture UI/service,
segmentation policy, Cloud/provider/backend, transcript merge, indexing, new native
dependency or signed release is admitted.
