# ASR-SMALL-ARM82-01 — revised data authority and measured result

**INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED**. Candidate: **VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE**.
One authorized measured campaign on physical POCO M5; 28 primary attempts,
zero retries, RU 24/24 and EN 3/24. Cleanup **VERIFIED**.
The unchanged frozen evaluator produced this result and was independently reconciled with
the retained journal. No inference was repeated for publication.

## Prospective data authority

The owner authorized a narrow source revision after the accepted test-only pool blocker.
Both languages now use **Common Voice Spontaneous Speech 5.0, release
`sps-corpus-5.0-2026-09-11`, dev partition** from the same exact admitted archives.
The previous test-only impossibility proof and historical SMALL rejection remain unchanged.
The local authority field `ownerRevisionAuthorityCommit` identifies base `cfe27f84a0b843ea33f1fe7a89dd13601a555a63`;
the revision itself is authorized by the subsequent owner instruction and published here.

- RU: [1789489294410-sps-corpus-5.0-2026-09-11-ru.tar.gz](https://mozilladatacollective.com/datasets/cmu5mg3pr00simh07epeylc55), 93031233 bytes, SHA-256 `aa0910f5fef7f24afccf5bca075995f0b7a970d96da72626c80507d629d05a6f`.
- EN: [1789489472613-sps-corpus-5.0-2026-09-11-en.tar.gz](https://mozilladatacollective.com/datasets/cmu5nqn1h00vwmi07b4dbk085), 544267165 bytes, SHA-256 `390b5a54c9afe0cc01da039ad206248f85682f247dd2b27d4cc0ab9a68e860b6`.

The embedded README in each exact archive confirms **CC0-1.0** and describes `client_id`
as a hashed user UUID. Both README hashes, source-index hashes and retained MDC terms
identities are frozen in [aggregate evidence](../evidence/poc-asr-001/asr-small-arm82-01-measured-stage0-v0.1.json).
CC0 has no attribution condition; credit is retained to Mozilla Common Voice.
MDC no re-identification/no rehosting or resharing restrictions remain in force.
No license was inferred from another release. Corpus files and participant metadata stay private.

The complete dev inventory was RU71/EN548. Exact source/audio exclusions cover all BASE48
and SMALL48, including 29 unexecuted SMALL selections. All **32** previously selected
participant identities were compared across both languages in the same release; no identity
inference was performed. All three retained journals reconcile to those exclusions or the
independently verified generated-silence preflight. All 48 prior-use ledger records are covered;
no additional development/tuning set exists in the retained authority. No known prior use is omitted.

All 619 dev audio records passed the exact historical FFmpeg/FFprobe 9.0.2 validity process.
There are no repeated-audio digest groups in the complete dev inventory. Eligible records after
all exclusions: **RU51, EN408**. The unchanged hash ordering selects exactly **RU24+EN24**,
1–20 seconds inclusive, without duration balancing, manual replacement or transcript-based
selection. Selected participants: **RU4, EN19**, with zero overlap against prior participants.
This is a bounded sample, not a broad speaker-population claim.
Prior source/audio overlaps are both zero. Priority1 supplied sufficient data; no broader search
or source mixing was necessary. Temporary candidate audio copies were removed after verification.

- Data authority canonical SHA-256: `0701b85521b3fb167271c11635b59a113ff543a88d302acd867ff3069d9a5d87`.
- Eligible pool canonical SHA-256: `90a783c9b4deacbc07b4c525d34c575d023d384d8e4ac2f7604c6e6bf628e5fd`.
- Exclusion-set canonical SHA-256: `5bada500706c776048640b239e8e92253bc8874c5b0a226f168d408763796772`.
- Selected manifest canonical SHA-256: `36a40fa210aee48e7b63372f6cc89471d58bfc9ecc5db87738989086b69c0e7c`.
- Transfer index canonical SHA-256: `6d72cdee1caca61dc078d84b253afce6f20f82719e7e26b2d28f1f03995b022b`.
- Data freeze canonical SHA-256: `cf74da20a1c230fe7fcf02c672757236555d485dd0495fa3517b7c04e7e4fd90`.
- Execution freeze canonical SHA-256: `e9db2cc5e6e5f09cd6ca670d44089a13c03d8260baf1641becc0b63c48d63780`.

All 96 selected audio/reference byte identities, materialization receipts, exclusions and
source/operator/native/profile identities were durably frozen before any ASR inference.
The explicit owner instruction forbids preparation commits: the exclusive local fsync freeze
pins the clean canonical base plus successor source bytes; this single terminal commit publishes
its sanitized authority. Storage remains local, private, owner-only, outside Git; retention
deadline **2026-10-25**, no extension. The new 48 selections are now consumed and excluded from
future evaluation even if the sequencer did not execute every selection.

## ARM82 build and preflight

Model unchanged: `ggml-small-q5_1.bin`, 190085487 bytes, SHA-256
`ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`.
whisper.cpp **v1.9.4**, commit `927cfce34f31707e17f2bff35c349632fb9e2c3a`, MIT;
zero upstream patches. NDK **28.2.13676358**, Clang **19.0.1**, CMake **3.22.1**,
Ninja **1.10.2**, Release, arm64-v8a/API28, `c++_shared`.
One runtime build, one configuration; only intended performance delta:
`GGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod`.

The exact historical subdirectory CMake fixture and toolchain hashes are reused. Configure
with `cmake -S <fixture> -B <build> -G Ninja`, the pinned Android toolchain/Ninja/source
locators, and every `runtimeBuild.cmakeOptions` entry from JSON as `-Dkey=value`;
`CMAKE_EXPORT_COMPILE_COMMANDS=ON` adds audit metadata only. Build with
`cmake --build <build> --target whisper --parallel 2`; compile the unchanged native adapter
with its historical CMake source and this library directory. Private exact commands/cache/
compile commands and their hashes are retained. No second-build bit-for-bit reproducibility
claim is made: the owner required one build, whose complete recipe and outputs are pinned.

| Native file | Bytes | SHA-256 |
|---|---:|---|
| dora-asr-campaign | 2668424 | `59e46efd284e88f84641b30f3aa5d8fe4f1dcf62b8424b48edb7330637223951` |
| libc++_shared.so | 9236352 | `ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2` |
| libggml-base.so | 5904504 | `ef2fab9ca78cb998feed953755c394d46cd5182b355909032415e79ad522e049` |
| libggml-cpu.so | 4699840 | `ab61e958ed28b6ec9ef1c92bd141f99149bb02e6147c1d710824565bdb2c7a14` |
| libggml.so | 533264 | `032f86f3f42ba9dedeac0f7f0b69c818917c6ddab6da98b677f2cef6dbea4f97` |
| libwhisper.so | 7176544 | `b67a219f3610f9085a75fe4dd7c2c11a18edea60068e63c2f4b63c330d5f96e3` |

CPU disassembly contains **917 SDOT/UDOT** and **8 FP16 vector FMLA** instructions.
Actual compile commands include the intended ARM82 target, with no additional fast-math,
LTO, i8mm, SVE or OpenMP. Dependency closure is limited to the frozen CPU libraries,
shared STL and Android system libraries; no GPU/OpenMP backend dependency.
ELF64/AArch64 and API28 build admission pass. All LOAD alignments are at least 0x4000;
16-KiB stored-library package alignment/zipalign pass. POCO has **4-KiB pages**:
an actual 16-KiB-device runtime test was **not run**, and no all-device/native-production
admission follows from these checks.

Accepted baseline-compatible physical ISA evidence: `AT_HWCAP=0x119fff`, errno0;
ASIMD=2, FPHP=512, ASIMDHP=1024, ASIMDDP=1048576, all present, **PASS**.
Physical model22071219CG/device stone/API34 identity was revalidated before specialized
binary execution. No unnecessary capability probe was repeated.
CPU only, **4 threads**, GPU disabled, flash attention enabled, inherited scheduling;
greedy temperature0, explicit RU/EN, `no_context=true`, `audio_ctx=0`, full context,
fresh process/context per case. No model, decoding, normalizer, oracle or gate change.

The separate [successor binding](../../tools/asr_small_arm82_v01/alpha_asr_small_arm82_binding.py)
loads isolated instances of the exact historical evaluator/sequencer, changing identities only.
All content/source/model/native/profile and ACL checks run at the historical validation points.
Owner-only ACL predicates are batched in one host process; no native measurement-window or
device scheduling change is made. Historical v0.3 remains byte-identical.
Preflight PASS: all96 content hashes, exclusions, private storage, model, binaries, runtime,
operator, decoder, profile, gates, Java oracle, physical identity, ISA and initial thermal0.
Only generated silence decode was executed in preflight: model load/inference both zero.
The measured campaign used one generated3-second silence full-inference warmup and one
upstream codec decode-only check, then the frozen one-primary/no-retry sequence.

## Frozen quality and resources

| Language | Completed | Aggregate normalized WER | Limit | Gate |
|---|---:|---|---:|---|
| RU | 24/24 | 73/349 = 20.916905% | <=20% | FAIL |
| EN | 3/24 | 1/13 = 7.692308% | <=18% | NOT_EVALUABLE |

Overall quality: **FAIL**.
WER uses sum(S+D+I)/sum(reference tokens), not a per-case average. Raw WER is diagnostic.
Incomplete language coverage cannot establish PASS.

The sequence stopped on **overall attempt28 / EN attempt04**: native inference returned
code0, but its reference normalized to zero tokens (`INVALID_EMPTY_NORMALIZED_REFERENCE`).
The source reference passed the frozen nonempty-raw-text eligibility rule (2 raw tokens);
the unchanged scoring contract correctly rejected an empty normalized reference. This was
not a retryable build or host error. No reference was repaired and no case was replaced.
The complete RU24 aggregate independently fails quality, so the evaluator retains VALID_FAIL
despite incomplete EN/resource coverage. RU quality failure itself did not trigger early stopping.

| Resource | Observed | Frozen limit | Gate |
|---|---:|---:|---|
| Weighted RTF | NOT_EVALUABLE | <=2.0 | NOT_EVALUABLE |
| p95 RTF (rank 46/48) | NOT_EVALUABLE | <=4.0 | NOT_EVALUABLE |
| Maximum RTF | 4.613362080103359 | <=6.0 | NOT_EVALUABLE |
| Maximum cold load, us | 277978 | <=15000000 | NOT_EVALUABLE |
| Peak PSS, bytes | 360818688 | <=1610612736 | NOT_EVALUABLE |
| Peak native heap, bytes | 934189144 | <=1342177280 | NOT_EVALUABLE |
| Evidenced OOM | 0 | <=0 | NOT_EVALUABLE |
| Maximum thermal status | 0 | <3 | NOT_EVALUABLE |
| Resource-valid attempts | 27/48 | 48/48 | NOT_EVALUABLE |
| Raw telemetry windows valid | 28/48 | 48/48 | NOT_EVALUABLE |

Overall resources: **NOT_EVALUABLE**;
decisive failures: **NONE**.
Unavailable full-48 weighted/p95 statistics remain NOT_EVALUABLE. Observed maxima describe
executed attempts only; positive failures retain precedence over missing coverage.
The report does not substitute diagnostic statistics for the frozen evaluator.
Raw memory and thermal windows are valid for all28 executed cases; the strict evaluator counts
only27 resource-valid attempts because attempt28 is FAILED after the reference error.
Thus 27/48 is the frozen valid-attempt count; raw telemetry covers28/48 and required48 coverage
is incomplete. Attempt28 has no memory/thermal sampling anomaly (maximum gaps118260us and
1110000us respectively), native stderr is empty, and cleanup is VERIFIED.
Since this is a different untouched holdout, direct causal speed/quality comparisons against
the historical test partition are not established by these two campaigns alone.

## Cleanup, validation and stage effect

API invocations **1**; primary attempts **28**; retries **0**;
warmup inferences **1**; total ASR inferences **29**.
All 28 executed cases have verified cleanup; final device cleanup
**VERIFIED**, host process exited, no owned device directory remains.
Marker, journal, terminal aggregate and private measurements/audit receipts are retained.
Terminal aggregate canonical SHA-256: `259cde62898e8d06b73500a962da8c6c850debd07883476f9d1af05e9a86f220`.
Re-evaluation from the read-only retained journal matches the terminal bytes canonically.

Validation: 182 generated-input host tests; independent pre-execution code/freeze review;
Stage00 Gradle formatting/static checks, unit tests, Android-test compilation, lint and debug
assembly; Stage00 validator and APK alignment. Publication additionally checks strict JSON,
local Markdown links, privacy, exact scope and `git diff --check`. All 613 untouched baseline
files and 308 historical private campaign files retain their bytes; prior Status/Backlog bytes
are preserved under an additive current entry. No production Android code is modified.

ASR-SMALL-ARM82-01 has this measured terminal result. Stage5/GroupB remain **NOT_PASS**;
POC-ASR-001 remains **BLOCKED / NOT_READY**. The historical 5.6C.2B incomplete campaign
and its VALID_FAIL disposition remain unchanged. Recovery remains
**0D.6 = ALPHA CLOSED / FULL OPEN**. PR#86 stays OPEN, DRAFT, UNMERGED, untouched.

Scope counts: old holdout reruns0; tuning0; threshold changes0; model changes0;
decoding changes0; configuration/thread sweeps0; unauthorized retries0;
second campaign invocation0; Recovery changes0; PR#86 changes0; merges0.

**Next action:** Assess the RU quality rejection and normalized-empty reference stop, then prospectively freeze one next candidate experiment and reference-eligibility correction on a new untouched holdout; do not rerun this campaign.

Publication: one atomic result commit from `cfe27f84a0b843ea33f1fe7a89dd13601a555a63` on
`chat/alpha-asr-runner-scope`, normal push, no merge. Exact final commit/readback is supplied
in the task handoff. The [original prospective contract](DORA_ASR_SMALL_RESOURCE_POSTMORTEM_NEXT_EXPERIMENT_STAGE0_V0_1.md)
and [old test-pool blocker](DORA_ASR_SMALL_ARM82_01_EXECUTION_STAGE0_V0_1.md) remain historical authority.
