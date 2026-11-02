# Stage 13 — bounded Whisper quality diagnosis

2026-11-02. Scope: retained public synthetic RU/EN evidence and exact-input
controls, on the v9 source baseline `49ca207`. No model, decoder setting,
native library, window policy, fixture text or strict quality assertion changes.

## Result and localization

**Functional PASS / Quality PARTIAL** remains the retained v9 result. Six native
300-second calls per language completed with nonempty output and durable history;
separate process-restart readbacks passed. The original combined EN test and both
separate strict quality tests remain failed. These marker counts are not WER.

| Measurement on full 30-minute fixture | EN | RU |
|---|---:|---:|
| Exact markers | 16/18 | 14/18 |
| Bounded one-character markers | 17/18 | 14/18 |
| Sum of within-window ordered prefixes | 17/18 | 10/18 |
| Change to full-fixture marker count in this task | No new full run | No new full run |

RU readback authenticates the fixed source and reproduces direct-native-return
counts. Missing marker indices `1,7,13,16` are in windows `0,2,4,5`, each beginning
145 seconds into its window and ending before 149 seconds. All 12 RU start/end
markers are exact. Present markers occur once in their expected windows and order.
The prefix stops at a missing midpoint; it does not demonstrate reordering.
The eight words of the missing phrases are absent even within the existing
one-character bound. Loss predates transcript publication; the retained evidence
does not support a paging, reopen or application-window clipping fix.

EN's missing marker is the last phrase of window 2. Its exact 870–900-second PCM
tail was already recognized with the unchanged runtime/model in a 57.029-second
short integration test. That is evidence of input-context-dependent output, not
proof of a specific decoder seek/filter branch. The first EN marker has a
one-character substitution and is separately counted.

## PCM, VAD, language and native audit

- The long harness imports all fixed PCM directly into the protected writer.
  Capture/Silero VAD is not used to choose or remove fixture ranges.
  `LocalRecognitionRun` uses contiguous fixed 4,800,000-frame ranges;
  `ProtectedPcm` validates exact contiguous callbacks and final count, and converts
  signed little-endian PCM16 using division by 32768. Silence remains in input.
  Thus this fixture cannot diagnose physical microphone/VAD quality, but a VAD
  omission is not supported for its four interior RU deficits.
- JNI copies the entire float array, passes its complete count to `whisper_full`,
  and concatenates every returned segment in index order. Output caps fail
  explicitly; retained parts are far below them. Native segment timestamps,
  per-segment text and internal seek/no-speech traces were not retained. The
  returned concatenation alone cannot distinguish skipped speech from gross
  misrecognition/repetitive substitution.
- Language is explicitly `ru` or `en`; detection and translation are disabled.
  There is no evidence of an automatic-language-selection defect in these runs.
- The pinned SMALL q8_0 model is 264,464,607 bytes, SHA-256
  `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`.
  Settings remain CPU-only, four threads, flash attention, greedy, temperature 0,
  temperature increment 0, no initial prompt, `no_context=true`. Context is
  recreated for each 300-second call. As documented in the
  [v9 audit](ALPHA_V9_WHISPER_QUALITY.md), rolling history within a full call is
  still different from an isolated control. No parameter change is justified
  by the retained evidence alone.

## Reproducible exact-input controls

`tools/prepare_whisper_context_diagnostic.py` admits only the two pinned v9
public-synthetic PCM hashes. It validates the unchanged manifest layout and
prepares each omitted marker's original 300-second window, a 30-second context,
and exact phrase range. RU contexts are `[135,165)`, `[735,765)`, `[1335,1365)`,
`[1635,1665)` seconds; EN is `[870,900)`. Within each selected range, no silence is
added, samples removed, audio resynthesized, or normalization applied. Optional PCM output must be in a
new directory outside the repository. Reports contain only counts, ranges,
digests, RMS and peak levels, never transcript or marker text.

```text
python tools/prepare_whisper_context_diagnostic.py --fixture-directory <retained-fixture-dir> --language ru --output-directory <new-external-dir> --write-pcm
python -m unittest tools/test_prepare_whisper_context_diagnostic.py tools/test_build_stage9_whisper_adapter.py
```

Both retained source digests were verified and 12 RU / 3 EN controls prepared.
The report's float32 little-endian hashes describe the host expectation after
PCM16 conversion, **not captured JNI input hashes**. Original JNI inputs cannot
be retroactively hashed. Five synthetic helper tests cover signed sample
conversion, exact ranges, truncation, changed-source refusal before output and
repository-output refusal; the three adjacent native admission tests also pass.

The isolated controls use the retained `DoraStage181C36` API36 x86_64 debug
installation, explicitly fenced by emulator serial, AVD, API and `ro.kernel.qemu`.
No app/test APK was replaced. All six native artifact hashes match the retained
[x86 build receipt](evidence/stage9-asr/x86-emulator-static-build.json), and the
installed model matches its pin. This is separate from owner ARM acceptance.
The existing short integration test performs two complete recognitions followed
by cancellation/history/edit/selection/reopen assertions. Each copied control is
hash-checked in app-private staging; original staging PCM is backed up and
restored with digest verification. The encrypted fixture/history are retained.

**All four omitted RU markers were recovered exactly in both recognitions of
their isolated 30-second controls (8/8 exact observations).** All four existing
integration tests passed, including history/edit/selection/reopen/cancellation.
The retained full-window results still contain 14/18 markers; these short-control
successes do not turn that score into 18/18 or demonstrate a production fix.

| RU marker index | Source context, seconds | Exact observations | Instrumentation test time |
|---|---|---:|---:|
| 1 | [135,165) | 2/2 | 61.030s |
| 7 | [735,765) | 2/2 | 60.194s |
| 13 | [1335,1365) | 2/2 | 88.018s |
| 16 | [1635,1665) | 2/2 | 93.161s |

The runtime, model, language and fixture bytes were held fixed while input
context changed. Together with the retained EN tail control this supports
context-dependent recognition loss for both languages. It does not identify
which rolling-history, no-speech, timestamp-seek or decoding mechanism caused
the full-window omissions. Phrase-only and 300-second controls were prepared
but not rerun; no extra full-length campaign was needed for this localization.
Original staging PCM was restored and rehashed; the task-launched emulator was
stopped without wiping data or saving a snapshot.

## Performance and limits

Retained full-test times: EN 792.003s (failed final quality assertion), RU
1,050.539s. RU measured recognition-run elapsed time was 906.585s for 1,800s of
audio (ratio 0.504), sampled peak process PSS 690,119 KiB. These are API36 x86_64
test-runtime observations, not Samsung or owner ARM benchmarks. The comparable
EN peak PSS is unavailable in the retained scalar logs. Test wall time includes
storage/history assertions and must not be presented as decoder-only latency.
The new short test times above include two recognitions, model checks, protected
storage and history assertions; no comparable per-decode timing or new peak PSS
was collected. They cannot establish a speed or RAM improvement. No production
change was made, so speed/RAM improvement is **NOT_MEASURED**.

Cross-partition utterances, real-world WER, physical-device speed/thermal/RAM,
24-hour native inference and exact internal decoder cause remain unqualified.
The next separately bounded task is a same-input full-versus-short native
diagnostic retaining segment/seek
metadata and actual JNI input fingerprints. Any proposed decoder/window change
must then rerun unchanged strict RU/EN markers and crossing-utterance controls,
and compare speed/PSS before acceptance. Do not shrink windows or enable overlap
as a production fix on marker evidence alone.

Retained local evidence: `ru-boundary-analysis-20261102.{json,md}`,
`analyze-ru-existing-boundary-20261102.py`, `native-long-{ru,en}-api36.log`,
`native-{ru,en}-run-scalar-evidence.log`, `native-window2-tail30-en-api36.log`.
Raw synthetic PCM, retained transcripts, models and device logs stay outside Git.
New local evidence: `whisper-{ru,en}-controls/context-controls.json`,
`ru-context-results.json`, `ru-context-marker{1,7,13,16}.log`, corresponding
per-process scalar logs and `whisper-context-runtime.json`. All four controls
have distinct instrumented process IDs and two exact scalar observations each.
