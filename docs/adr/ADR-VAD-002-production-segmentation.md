# ADR-VAD-002: frame-authoritative product segmentation

Status: PENDING_FINAL_PUBLICATION. User-authorized Stage 8.4 implementation;
external acceptance remains mandatory. Parent:4073107108604136425071a51e63703374a660b8.

The owner's Stage8.4 implementation contract supersedes prospective monolithic
files, wall-clock timers and duplicated pre-roll audio in technical-plan14.2.
Stage8.2/8.3 encrypted canonical units remain the sole audio source. Technical
segments group those units; their cap is9,600,000 frames. Capture epochs change
on microphone acquisition; technical IDs also change at caps without touching
AudioRecord. A cap inside a block splits its storage ranges exactly.

The existing canonical queue is drained by recording controls. After canonical
acceptance, a bounded observer copies the borrowed PCM before its owner clears
it. This avoids adding inference, allocation of native state, or waits to the
AudioRecord reader. A dedicated observer worker owns the model and recurrent
state. Unknown/missing/stale/failed observations break silence continuity.
Canonical persistence and technical rotation never wait for VAD.

The serialized recording control owner alone owns the semantic reducer. The
worker emits bounded generation-tagged classifications and uncertainty, never
semantic transitions. Pause/Resume fences update the reducer synchronously;
late worker output cannot mutate its state. Stop seals any unevaluated canonical
tail as a typed unknown range without waiting for inference. The PCM observation
queue holds at most40 blocks (each at most32,000 bytes); the result queue holds
at most256 classifications. Overflow on either queue invalidates continuity.

`ml:vad-api` contains pure Kotlin frame values, engine port, streaming window
adapter, deterministic semantic reducer, technical range planner and bounded
observer. No Android/JNI/clock is used by the reducer. `ml:vad-sherpa` owns the
exact admitted runtime through its public Java API, with a narrow reflective
binding so credential-free CI compiles all repository implementation code.
CI's unprovisioned product reports RUNTIME_UNAVAILABLE; scripted fakes exist
only in tests. Physical builds must explicitly select the private real runtime,
verify AAR/model before Gradle consumption and report exact installed APK hash.

Use sherpa `Vad.compute`, not its higher-level `acceptWaveform` segmentation.
The pinned Silero model requires512 new samples plus64 context samples. The
provider retains only that bounded context, passes normalized S16LE input and
clears it on reset. This avoids sherpa's hidden onset/hysteresis/maximum-speech
policy and unbounded accumulated speech copies. All builder values and the
fact that segmentation-related builder values are unused by compute are frozen
in a separately calibrated, hashable SegmentationProfile.

The semantic clock is classified canonical frames:90seconds=1,440,000 frames.
The first continuously negative frame starts it prospectively; short-pause
hysteresis changes state labels only. A positive observation cancels it even
before a new onset could be established. A boundary closes semantic content,
never the microphone/session. No-speech silence never opens empty segments.
Semantic content can span technical rotations. Pause cancels accumulated
silence without closing semantic content; Resume resets neural/adapter state.
Pre-roll references32,000 original frames, clamped to recording start and the
current continuity epoch so stale pre-Pause/gap audio is not reintroduced.
Semantic pre-roll is also a processing source view: a newly opened semantic
view may reference the previous view's silence tail. It never assigns a second
canonical identity to those frames or increases logical duration.

Overlap is a frozen1.5-2.0second source range preceding a technical rotation,
clamped to the same capture epoch. It is processing context, not canonical
ownership, byte duplication, duration, ASR dedup or Cloud authorization.

Segmentation metadata uses additive SQLCipher/Room schema migration. Old
recordings without rows are NOT_EVALUATED, never retroactively segmented.
Metadata only describes committed canonical ranges; crash recovery does not
restore neural/silence state. Metadata failure is typed and cannot stop valid
canonical recording. Strict SAVED still requires all canonical writes. Closed
metadata is immutable/idempotent and validated against source bounds.

Calibration-only synthetic fixture categories:clean speech, natural short
pauses, silence, HVAC, keyboard, moderate environmental noise, near/far speech.
Freeze exact thresholds/profile/fixture hashes before untouched final tests.
Final gates include deterministic boundaries/endurance, encrypted PCM
equivalence, fault/race/recovery regression, actual POCO microphone VAD,
90second acoustic cases, >10minute rotation, screen-off, resources, independent
review, exact-SHA CI, privacy audit and Sheet readback. No threshold relaxation.

No ASR, Cloud, diarization,8.4C,8.5 or security restoration is implemented.
PERF-REC-001 remains deferred/non-blocking Alpha; security restoration OPEN.
