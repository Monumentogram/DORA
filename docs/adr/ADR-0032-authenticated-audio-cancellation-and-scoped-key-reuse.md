# ADR-0032 — Authenticated audio cancellation and scoped key reuse

Date: 2026-11-02. Status: accepted implementation direction within the owner's
[P0 issue 132](https://github.com/Monumentogram/DORA/issues/132) scope; software
qualification is IN_PROGRESS and physical performance is PENDING.
Baseline: signed Preview10 source `45e1e09`; stacked on PR130 -> PR128 -> PR127.

## Context and retained contracts

Long finalization and first playback include a full authenticated source read.
Each encrypted unit currently reopens and validates the same run-key primitive
for its confirmation, envelope and payload operations. Closing a loading player
previously retired its UI generation without cancelling that full read; the
serial persistence worker therefore delayed vault retirement and new recording.
Playback also prepared a five-minute PCM window before its first sample.

The [technical amendment](../DORA_MVP1_TECHNICAL_PLAN.md#alpha-v9-technical-amendment--2026-10-30--in_progress)
and [ADR-0031](ADR-0031-twenty-four-hour-recording.md) still require full-source
authentication before a handle can admit playback. Every later range checks live
authority, deletion and exact source metadata and authenticates its selected
ciphertext. A stored reference or metadata snapshot never becomes persistent
permission. [Original-source lifecycle](ADR-AUDIO-002-original-audio-lifecycle.md)
and [serialized runtime ownership](ADR-PERSISTENCE-002-authenticated-product-audio-runtime.md)
remain authoritative, with the current foreground-authority policy from
[ADR-0021](ADR-0021-owner-no-app-pin-local-access.md).

## Decision

1. Reuse a successfully validated, nonexportable run-key primitive only within
   one synchronous RECONCILIATION attempt on its exact backend/vault instance,
   calling thread and run. Bootstrap and publication do not reuse a validated
   primitive through this optimization.
   Every use still checks live key existence and performs the real provider
   operation with unchanged ciphertext and AAD. Failure or deletion invalidates
   the reusable primitive; scope exit releases it in `finally`. An escaped
   wrapper cannot operate after exit or on another thread. Recovery may perform
   a fresh validation after a failure, but cannot reuse the failed primitive.
   This introduces no process-wide key cache, exported key bytes, replacement
   key creation or StrongBox downgrade.
2. Add cooperative cancellation to authenticated opening. Check before source
   acquisition, at authenticated-unit boundaries and before publication. Check
   cancellation before retaining a failed read as permanent source loss.
   Cancellation is transient and cannot create a deletion tombstone or grant
   partial-source authority. Workers retain ownership until borrowed PCM is
   wiped and leases are released; cancelling a Future is not a substitute for
   this completion boundary. Close successful handles rejected by a late
   cancellation, revocation or interrupted caller. Metadata-only handle closure
   is idempotent and safe from another thread; actual vault retirement remains
   serialized and must complete before the recording writer opens its vault.
3. Limit initial and subsequent playback PCM windows to 80,000 frames, or five
   seconds at 16 kHz, with at most one owned consumer window and one prefetch.
   Selected ranges remain fully authenticated before playback receives them.
   Pause, seek, Cancel during loading and player disposal retire the active
   playback generation. Local Whisper keeps its existing five-minute windows,
   model, native runtime and quality gates.
4. Add opt-in, bounded scalar diagnostics for finalization, authenticated open,
   range read and playback preparation. Record fixed operation/stage/status
   enums, counts, elapsed time and actual completed/total units. Keep only the
   bounded latest operation snapshots, isolate active measurement attribution
   by thread and retire running state on success, cancellation and failure.
   No diagnostic accepts source identities, aliases, paths, URIs, content,
   ciphertext, key material or exception text. Nested stage durations overlap
   and must not be summed as independent elapsed time.

Encrypted formats, source identity, schema, durable deletion, permission checks,
package, signer and admitted native/model payloads are unchanged by this decision.
No plaintext audio cache or file is introduced. Cancellation preserves the saved
recording, transcript versions, selections and user edits.

## Verification and limits

The expected structural change for the same four-unit synthetic read is
12 -> 4 validated key opens and 36 -> 20 provider cipher calls. The three
mandatory decryptions per unit remain: twelve across that fixture. The reduction
removes repeated validation/probe work; it does not remove full-source or selected
range authentication. These are structural expectations to verify with real
pipeline counters, not a claim about Samsung latency or native recognition.

Required evidence includes cancellation before/during opening, cancellation
racing corruption, unchanged permanent-loss/deletion fences, key failure and
scope isolation, late-handle cleanup, interruption ownership, exact seek/tail
boundaries, prefetch zeroization and loading Cancel/disposal followed by a new
recording without process restart. Run bounded real-encryption and UI regressions
on API28/API36, plus the applicable existing recording, transcript/history,
search, export, deletion and process-recovery suites. Keep synthetic writer
handoff, actual microphone capture and real process death labeled separately.

Software checks, exact-source CI, independent review, signed runtime and retained
data/model upgrade evidence remain separate release gates. Physical targets are
finalization in 15–30 seconds, first playback within five seconds and prompt
cancelled-open retirement. Their status is PENDING until a renewed owner-authorized
comparison on the same retained Samsung source records the actual stages and
result. Remaining required decryptions may prevent these targets; that does not
authorize weaker authentication. No personal-device operation, AWS change,
multi-hour endurance campaign or merge follows from this decision.

Stage13 overall and Stage12 remain PARTIAL; issue125 and prior native failures,
quality limitations and device/endurance/security/privacy gates remain visible.
Local Whisper percent/audio-time progress, cancellation feedback and state
recovery are a separate deferred UX task, governed by real progress denominators
and the existing durable attempt/checkpoint and privacy contracts.

See the [design](../superpowers/specs/2026-11-02-samsung-audio-performance-design.md),
[implementation plan](../superpowers/plans/2026-11-02-samsung-audio-performance.md)
and [Stage13 working qualification](../STAGE13_RESULT.md).
