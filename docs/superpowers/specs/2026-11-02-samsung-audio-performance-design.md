# Samsung authenticated audio performance

Owner scope: issue #132, continuing signed v10 at `45e1e09`, stacked on PR130.
The owner requests implementation autonomously within the existing session scope;
physical Samsung work needs a fresh explicit authorization. No AWS changes, merge,
personal-data cleanup, new keys, StrongBox downgrade or multi-hour endurance campaign.

## Design and alternatives

The existing full-source authentication contract (ADR0031) remains. Removing that
scan or relying on a persistent plaintext/key cache would change the security boundary
and is not selected. Changing storage/encryption formats is also outside this fix.
Use three bounded changes: one validated run-key primitive per synchronous single-run
reconciliation attempt; cooperative cancellation of full-source opening; five-second
playback windows. Preserve five-minute ASR windows and all authenticated metadata,
confirmation, envelope, payload, AAD, source identity and live authority checks.

The primitive must be nonexportable, fully validated before admission, and unavailable
outside its exact thread/vault/run/attempt. A recorded failure or deletion invalidates
reuse. Reused operations still check alias existence and execute actual provider cipher
initialization/decryption, so missing/invalidation/authentication failures remain failures.
No primitive survives the attempt's finally block; bootstrap/publication are not cached.

Cancellation is transient: check before acquisition, between authenticated units and
before publication; it must not create retained corruption/loss. Keep worker ownership
until borrowed buffers are wiped and leases released. Close successful late results
discarded after cancellation/revocation/interruption. Closing a loading player must
release the queue for the next recording without killing the process.

## Measurement

Bounded, opt-in scalar diagnostics expose fixed operation/stage enums, counts, elapsed
nanoseconds and completed/total units. Never log identifiers, aliases, paths, audio,
transcripts, ciphertext, keys or exception text. Cover FINALIZE, authenticated open,
range preparation, Keystore open/info/probe/decrypt and AudioTrack construction/ready.
Keep stage totals explicitly nested rather than summing overlapping measurements.

Compare identical deterministic encrypted fixtures before/after on API28/API36, with
exact counters and wall-time boundaries. Measure the same retained Samsung30:30 source
only after renewed permission; emulator time cannot prove StrongBox targets. Targets
are finalization15–30seconds, first playback≤5seconds and prompt canceled-open retirement.
Remaining mandatory decrypts/full verification may prevent those targets; disclose that
with evidence rather than weakening authentication. Capture/recovery compatibility and
an unchanged imported model are release gates. Local Whisper progress percent/audio-time,
cancel and restored state is a separate Stage13 backlog item, not part of this P0 fix.
