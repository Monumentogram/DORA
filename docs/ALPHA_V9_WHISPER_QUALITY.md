# Alpha v9 Whisper functional and quality evidence

Review date: 2026-11-02. This supplements the
[v9 qualification record](ALPHA_V9_QUALIFICATION.md). It describes retained
public-synthetic-fixture results, not new inference or a release verdict.
No model, native runtime, fixture or quality threshold was changed by this review.

## Functional completion and quality remain separate

Both languages completed six actual native calls on the API36 x86_64 debug-test
runtime: six nonoverlapping 300-second parts, 28,800,000 frames per language.
Nonempty results, durable parts, previous versions, edits, selection and exact
vault reopen were checked. This is neither owner ARM runtime nor 24-hour ASR
qualification.

| Retained result | EN | RU |
|---|---|---|
| Original inference test | Functional assertions completed; original combined test **FAILED** its final quality assertion after 792.003s | Separate functional test **PASS**, 1,050.539s |
| Separate process readback | PASS, 26.343s; zero new inference | PASS, 25.762s; zero new inference |
| Exact marker presence | 16/18 | 14/18 |
| Bounded marker presence | 17/18 | 14/18 |
| Sum of within-window bounded ordered-prefix lengths | 17/18 | 10/18 |
| Separate strict quality test | **FAIL**, 3.425s | **FAIL**, 3.024s |

The matcher permits at most one character insertion, deletion or substitution
across an entire normalized two-word marker. The strict gate still requires all
18 markers in their expected windows and global order. Readback does not rerun
recognition or replace the historical EN failure. These are marker measurements,
not word-error-rate estimates or a complete boundary-quality PASS.

## What the ordering numbers mean

`NativePublicMarkerMatcher` records individual marker presence separately from
the matched **prefix** of the expected sequence. If an early marker is missing,
later markers cannot advance that prefix even when they occur in correct order.
EN's global bounded prefix is 8/18 because its ninth marker is absent, despite
17/18 presence and 17/18 within-window matches. RU's 14/18 presence versus 10/18
within-window prefix therefore does not establish four reorderings. No metric
or threshold has been weakened; the distinction prevents an unsupported cause
claim.

## Localized EN and RU findings

The existing hash-guarded EN public-fixture diagnostic has six parts of
4,103 / 4,099 / 4,250 / 4,099 / 4,106 / 4,101 UTF-8 bytes. Its first window's
start marker has a one-character spelling substitution. Window 2's end marker is
absent while its start/midpoint markers and the other windows remain in order.
No raw transcript is reproduced here.

The missing end-marker utterance occupies frames 14,349,680–14,399,999, wholly
inside window 2 and ending exactly at 900 seconds. A retained short control used
the identical original 870–900-second PCM bytes and the unchanged model/runtime;
it recognized that marker and passed in 57.029s. Read-only byte comparison
confirmed that exact tail. This supports an input-context-dependent decoding
omission, rather than missing fixture audio; it does not identify the exact
internal decoder branch or fully exclude every application defect.

The subsequent hash-guarded RU readback performed zero new inference (quiet
retry PASS, 93.694s; the first 120s authentication timeout is retained). All four
absent markers are interior midpoint phrases in windows 0, 2, 4 and 5, beginning
145 seconds into their windows. All 12 start/end markers and the other two
midpoint markers are exact after established normalization. None of the eight
words belonging to the missing phrases appears within the existing one-character
bound. Present markers retain their expected parts and order. The per-window
prefix lengths `1,3,1,3,1,1` explain the 10/18 prefix total without evidence of
reordering. Original direct-native-return counts match retained readback: missing
output predates publication and does not indicate 300-second partition clipping.
The exact internal decoder cause remains unproven; the strict quality failure
is preserved. See [Stage 13 diagnosis](STAGE13_WHISPER_QUALITY.md) for follow-up.

## Boundary and implementation audit

`LocalRecognitionRun` reads and commits the same next incomplete part index.
Local ranges are `[index × 4,800,000, min(sourceFrames, next boundary))`.
`ProtectedPcm` requires contiguous authenticated callbacks and the exact final
frame count. The native harness checks six arrays of exactly 4,800,000 samples;
source import verifies the full fixture digest. These bounds and source checks
are distinct from a retained hash of each actual JNI input array, which was not
collected in the original runs.

The JNI adapter passes the complete sample count to `whisper_full` and appends
every returned segment in index order. Oversize/invalid output fails explicitly.
Persisted pages retain `(partIndex,pageIndex)` order and insert a separator
between parts. EN parts fit one page and are far below the output limit. Source
review found no localized range, ordering or persistence truncation bug that
explains the observed quality failures.

Each 300-second call creates a fresh context, using greedy temperature-zero
decoding without a temperature fallback. Although the adapter sets `no_context`,
the [pinned Whisper implementation](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/src/whisper.cpp)
clears past prompts at full-call entry and subsequently updates rolling history
within the call. Its timestamp seek, no-speech filtering and chunk advancement
make the full-window and isolated-tail contexts different. No retained internal
seek/filter trace proves which mechanism omitted the marker. Its terminal
100ms granularity also does not alone explain a complete 3.145-second omission.

The fixtures align complete utterances to window edges; they do not qualify
utterances crossing a 300-second partition. No overlap or stitching quality is
demonstrated. Keep quality **PARTIAL**; the RU readback narrows the next diagnostic
to interior input context, without supporting a model/settings change.

Retained evidence filenames: `native-long-en-api36.log`,
`native-long-ru-api36.log`, `native-en-run-scalar-evidence.log`,
`native-ru-run-scalar-evidence.log`, `native-en-quality-separate.log`,
`native-ru-quality-separate.log`, `native-window2-tail30-en-api36.log` and the
separate EN/RU functional process-restart logs. Audio, model files, raw
transcripts and private paths are excluded from this document.
