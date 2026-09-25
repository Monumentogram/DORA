# DORA Alpha ASR 5.4 bounded POCO campaign — Stage 0 v0.1

25 September 2026. **PASS / BOUNDED_POCO_48_CLIP_CAMPAIGN_COMPLETE**.

All 48 intended clips completed once with valid scored evidence and verified device cleanup.
Execution PASS is separate from quality: **RU normalized WER 30.5000% — FAIL;
EN normalized WER 18.4300% — FAIL**. Full POC-ASR-001 remains
BLOCKED / NOT_READY. Model disposition belongs to 5.5, which was NOT_RUN.

## Authority, scope and fixed inputs

The owner explicitly authorized importing the exact original 5.1C transfer, running only the
bounded 5.4 campaign on the agreed physical POCO M5 and publishing sanitized aggregates in one
atomic commit/push to `chat/alpha-asr-runner-scope`, without PR or merge. Starting HEAD:
`e91b45eefbad4609deb8a83d0823d7b090830700`. The prior missing-corpus blocker is resolved; its private historical
record is retained. This report supplements rather than rewrites historical 5.3C evidence.

The archive was exactly 2501103 bytes with SHA-256
`6340c832280ba21345d4e5c9f30b82e67d44f905b2f9ae7cc7bde4a0d74a85ee`. All 58 indexed files were verified before execution. Transfer index:
`e2df8bbbdca979804e75e9d48f007ab4fb9d6b0aa650c9f07c9d7e9046945db4`. The unchanged 5.1B validator returned
PASS / METADATA_CONTRACT_VALID. Inventory:
`ad454b162fab7dea96c28612a374fcae79040346dae5ee2916b3c4482b3ec041`; selected manifest:
`5d9e12fa76e9db54bc69bbd22396dbec733348db97bba353361ac701af64061c`. All 48 audio byte/hash, exact UTF-8 reference and original
duration bindings passed. The original provider/probe duration bindings were checked against the
frozen manifest; runtime PCM duration was separately measured. MP3 codec padding means these two
duration definitions need not be identical. No re-download, rematerialization, transcoding of
stored input, reselection, replacement, reference rewrite or substitution occurred.

Private import, source archive, original lifecycle record and laptop copy evidence stay in
owner-only local storage outside Git. Retention remains
ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS. The Downloads original is preserved.

## Frozen implementation and decoding

The separate test-only adapter is `dora-alpha-asr-campaign-v0.1`; the historical runner still
means MODEL_INIT_ONLY / LIFECYCLE_HOLD_ONLY. See the
[aggregate evidence](../evidence/poc-asr-001/alpha-asr-campaign-stage0-v0.1.json),
[complete resolved decoding profile](../evidence/poc-asr-001/alpha-asr-decoding-profile-stage0-v0.1.json)
and [measurement protocol](../evidence/poc-asr-001/alpha-asr-measurement-protocol-stage0-v0.1.json).

Decoding canonical SHA-256: `cd53fda162402675f54875de470b01c303f0ede9a2e0cefd6f1056e8fa381d39`.
Measurement canonical SHA-256: `0f1e456fb4b4b0da28a209abe94e42fe7a0ca599e5c5c794db16a6abe478b116`.
The complete private freeze canonical SHA-256 is `3ce91e9053f64172a1e4e54c2e3e7c6c0dd8650d42a0cad706f3cde0a5df2d9e`;
the public evidence includes every implementation file's exact frozen SHA-256.
Canonical digests use UTF-8 JSON, sorted keys, compact separators, non-ASCII preserved,
no non-finite numbers and no final newline. File hashes are exact working-file byte hashes.

Code/config froze at 2026-09-25T11:00:52.940543+00:00; first selected corpus access was
2026-09-25T11:01:22.697934+00:00. The successful synthetic preflight preceded this freeze and selected access was
zero. One generated three-second silence completed full inference, private hypothesis capture,
timing/memory/thermal measurement, journal close/reopen and cleanup without a quality score.
A pinned upstream MP3 exercised decoding only. No synthetic inference had to be retried.
No campaign implementation/configuration changed after freeze.

The exact 5.2 model is `ggml-base-q5_1.bin`, 59707625 bytes, SHA-256
`422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`. Runtime is whisper.cpp v1.9.4 commit `927cfce34f31707e17f2bff35c349632fb9e2c3a`.
The five already verified 5.3C shared libraries are unchanged. The new executable links them and
the miniaudio decoder from that same source tree; its exact binary hash is in aggregate evidence.
The upstream miniaudio header retains its MIT-0/public-domain licensing text in the private
pinned source. Neither model nor native binary nor third-party header is committed.

Resolved settings start from WHISPER_SAMPLING_GREEDY, four threads, CPU context, temperature 0
and temperature increment 0. Translation and language detection are disabled; locale is the
manifest's explicit ru/en. Every clip gets a fresh process/context with no prior context or
initial prompt. All other resolved fields, including timestamp, suppression, greedy, beam,
callback and VAD defaults, are published in the canonical profile. RU/EN differ only by language.
No corpus tuning, output selection or successful-clip retry occurred.

Reproduce the isolated build with NDK 28.2.13676358, CMake 3.22.1 and Ninja 1.10.2:

~~~text
cmake -S tools/alpha_asr_campaign_native -B PRIVATE_BUILD -G Ninja
  -DCMAKE_MAKE_PROGRAM=PINNED_NINJA
  -DCMAKE_TOOLCHAIN_FILE=NDK_ROOT/build/cmake/android.toolchain.cmake
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared
  -DCMAKE_BUILD_TYPE=Release -DWHISPER_SOURCE=PINNED_SOURCE
  -DCANDIDATE_LIB_DIR=VERIFIED_5_3C_LIB_DIRECTORY
cmake --build PRIVATE_BUILD --parallel 2
~~~

Pass the first command as one argument vector. CMake fixes armv8-a, warnings-as-errors and
16-KiB ELF maximum-page alignment. All three executable PT_LOAD entries have 0x4000 alignment.
This is static evidence; the measured phone has 4096-byte pages and provides no 16-KiB runtime proof.

## Execution, scoring and retained attempts

The operator entry point is [run_alpha_asr_campaign.py](../../tools/run_alpha_asr_campaign.py).
`preflight PRIVATE_CONFIG` must complete before `campaign PRIVATE_CONFIG`. The private JSON
configuration identifies `prior`, `work`, `git`, `sdk`, `executable`, `archive` and `corpus`;
the active configuration and executable identity must match the frozen values. Work, corpus,
archive and config paths are restricted to the owner-only local private root, outside worktrees.
Never point these tools at a new corpus or rerun this completed campaign.

Execution followed exact selected-manifest order: RU 24, then EN 24. Each input/reference binding
was reverified before use. The task-owned device directory contains only required native artifacts,
model and one current clip. Every case's artifacts were retrieved and hash-verified before deletion.
The host verifies owned process absence before cleanup and never signals an unverified remote PID.

Primary attempts 48; successful 48; failed/invalid/timed out/cancelled/retried 0. All attempts and
individual measurements remain private and were reconciled against the reopened SQLite journal.
There is no retry for quality; this run stops on first infrastructure failure. A 600-second host
timeout has a 605-second native safety alarm. Any severe thermal observation would be preserved
and stop the campaign; none occurred. Missing telemetry or a shared defect also stops execution.

The hypothesis is the exact concatenation of native segment text, with no correction or trimming.
The unchanged 5.3A normalizer supplies all four raw/normalized reference/hypothesis token arrays
unchanged to the existing Java oracle. Language S/D/I are summed, never averaged as per-case WER.

| Language | Valid cases | Normalized S / D / I | Reference tokens | Normalized WER | Gate | Raw WER (diagnostic) |
|---|---:|---:|---:|---:|---|---:|
| RU | 24 | 87 / 19 / 16 | 400 | 30.5000% | FAIL (≤20%) | 38.7179% |
| EN | 24 | 44 / 8 / 2 | 293 | 18.4300% | FAIL (≤18%) | 23.5714% |

The exact integer predicates are errors×100 ≤ 20×referenceTokens for RU and
errors×100 ≤ 18×referenceTokens for EN. Both languages have complete intended coverage.
These are bounded pilot observations, with no mixed-language/noisy/speakerphone, population,
long-duration or model-disposition conclusion.

## Device and measurement observations

Physical POCO M5, Android 14/API34, arm64-v8a, RAM 3759052 KiB,
page size 4096 bytes. Battery: 61% to 62%,
USB powered throughout the start/end observations. Sanitized power/radio/brightness, available RAM
and storage snapshots are in JSON. No device settings were changed. No device serial is published.

Audio is decoded to mono float32 16000-Hz PCM. Duration uses frame count rounded half-up to integer
microseconds. Model load and inference are separately timed with steady_clock; RTF excludes
decode, model load and persistence. No per-case warmup or repeated measurement is used.

Across 48 clips, inference median/p95/max is
4.957 / 6.460 / 6.541 seconds.
RTF median/p95/max is 0.6756 / 2.0015 / 2.8373;
duration-weighted aggregate RTF is 0.5901.
Exact ranges, totals, model-load timing and language aggregates are in JSON. p95 uses nearest rank.
RTF_GATE = NOT_EVALUATED because its threshold is PROPOSED_NOT_APPROVED.

Maximum sampled PSS is 158107648 bytes; maximum sampled allocated native heap
is 728918776 bytes. Samples run every 100 ms plus synchronous phase
boundaries, spanning decode, model load, inference and release. Gaps over 1 second invalidate the
measurement. PSS uses self smaps_rollup; mallinfo.uordblks measures allocator-accounted live bytes,
not all direct mappings. These are observed sample maxima, not continuous memory high-water marks.
No OOM failure was observed; no system-wide OOM trace was collected. Trim callbacks are unavailable
to this standalone native executable. Numeric memory gates remain NOT_EVALUATED / PROPOSED_NOT_APPROVED.

Android thermal status: NONE → maximum NONE → NONE.
SEVERE-or-worse observed: false.
Thermalservice polling uses a one-second delay and rejects gaps over three seconds; the observed
maximum gap was 1.140 seconds. No temperature threshold is invented.
Timestamp quality is NOT_EVALUABLE; median/p95 gates are NOT_EVALUATED because ground truth is absent.

## Validation, cleanup and publication

99 ASR tests passed: 12 new campaign tests, 44 unchanged runner tests, 22 unchanged evaluation
contract/oracle tests and 21 manifest tests. The independent pre-corpus advisory review exposed
startup/cleanup, evidence preservation, memory coverage, configuration binding and destination
validation issues; they were corrected before synthetic PASS and corpus access. This is not a
production security/admission review.

Offline Android formatting/static analysis, base unit tests, instrumentation compilation, lint
and debug assembly passed: 197 tasks, 100 executed, 92 from cache, five up-to-date. Stage 00 artifact
validation, the unchanged production APK native verifier and zipalign also passed. No app was
installed and no unrelated device/Recovery tests ran. CI is NOT_RUN: this branch has no matching
push workflow trigger and no PR was requested. Local evidence does not claim CI success.

All 48 per-case cleanups and final task-owned model/binary/audio/result/directory cleanup were
verified. The laptop corpus and private evidence remain retained. Public audio, references,
hypotheses, token arrays, per-case rows, sample IDs, serials and private paths are zero.
Status and Backlog receive an additive amendment preserving all historical text and counts.
Production code, normalizer, oracle, 5.3 evidence, allowlist and Recovery remain unchanged.
PR #86 is untouched. One atomic branch commit/push is authorized; no PR, merge or 5.5 work occurs.
