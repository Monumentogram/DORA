# Stage 13 owner Alpha — daily-use UX stabilization

Scope: the owner's bounded Stage 13 Alpha request, from Alpha v9 source
`49ca2070f15a618d145ded307fc87249f382b22c`. This is not full Stage 13 acceptance.

## Findings and bounded correction

The transcript dialog cleared its visible content on `ON_PAUSE`, as required by
ADR-0022 and ADR-SIGNAL-002, but only reloaded a selected recording on `ON_RESUME`.
The history view has no selected identity, so returning left its list empty until
the user explicitly refreshed. This was reproduced by the new Compose regression.
Source tracing also found that the selected-recording reload was
discarded when an old cancelled operation still owned `actions.busy`; its later
completion was correctly rejected by the existing generation fence, leaving the
editor empty. This delayed-worker interleaving is inferred from the control flow,
not separately qualified with a stalled native worker.

The correction keeps a pending foreground refresh until the existing
operation finishes, then reloads either the selected recording or transcript
history. A completed load with no transcript must not loop. No automatic
recognition, save, microphone start or Cloud submission is introduced. Saved edits
and selected versions continue to come from the encrypted repository. Unsaved
drafts remain foreground-only and clear on background/rotation under the existing
accepted contract; this change does not promise draft recovery across those events.

## Daily-path inspection

| Path | Existing behavior / scope |
|---|---|
| Record / pause / resume / save | Explicit preparation, recording, pause, finalization, saved and interruption states; timer is captured frames. Capture/controller/native code unchanged. |
| Local / Cloud recognition | Worker-owned inference/network and revisioned publication; visible busy/cancellation messages and persisted part checkpoints. Lifecycle refresh correction restores visibility after return. |
| Edit / history | Explicit save and close confirmation; machine history and authoritative edits remain separate. Foreground lifecycle correction must restore history and saved edits. |
| Library / search | Debounced, generation-fenced worker query; bounded display projections; only selected rendered text is searched. No audio scan added. |
| Export / delete | Existing off-main export/deletion paths, saved-text precondition, picker/operation feedback and retry semantics retained. |

This UI correction requires no SQLCipher/schema, package, signer, model, native
engine, capture or resource-token changes. Existing v9 synthetic 24-hour results remain historical
evidence; this UI change does not require rerunning those trials. It makes no new
RAM, storage throughput, native ASR quality or physical-device performance claim.

## Verification

**Scoped UX regression: PASS, 2026-11-02.** The focused Compose regression opens
encrypted saved text, saves a correction, reopens it,
checks the model-missing state, opens history, backgrounds the Activity, and
requires history to return automatically.

| Check | Executed result |
|---|---|
| RED on unchanged UI, API36 | The added final history-return assertion timed out after 30 seconds; earlier edit/save/reopen assertions passed. One test, one expected failure. |
| GREEN, retained `DoraIsolatedAlpha36`, API36 | Five UI tests passed in 76.934 seconds. |
| GREEN, retained `DoraIsolatedAlpha28`, API28 | The same five UI tests passed in 74.842 seconds. |
| Full `:app:testDebugUnitTest` | 165 tests across 34 suites; zero failures. |
| `spotlessCheck detekt` | PASS after applying the formatter's requested wrap. |
| `tools/test_pin_free_local_access.py` | Three host tests passed. |

The five instrumented classes are `TranscriptUiTest`, `TextExportVersionUiTest`,
`TextExportLifecycleTest`, `Stage11DeletionUiTest` and `RecordingSearchUiTest`.
These exercise saved correction/history, displayed Local/Cloud version export,
picker snapshot/recreation, cancellation/confirmed deletion and selected-text
search/edit/restart. All fixtures are synthetic. Emulator operations checked
`ro.kernel.qemu`, API and exact AVD; APK installation used `-r -t`, with no wipe,
uninstall, profile reset or personal-device operation.

Commands from `android/`:

```text
gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
gradlew.bat spotlessCheck detekt
```

Instrumentation uses `adb -s <verified-emulator> shell am instrument -w -r -e class`
with the five fully qualified classes and
`com.monumentogram.dora.debug.test/androidx.test.runner.AndroidJUnitRunner`.
The checked debug APK SHA-256 is
`ae644ea1f36c4d9194ab8294b7bd637cc10adecc69acfe1ff95eb2015c84ef77`;
test APK SHA-256 is
`9da61a4e9ddb5caae652597393b05b2e80310dc202a884b3442ce03748d82761`.
These are local debug UI evidence, not the signed release artifact. Build logs
contain existing Compose-test deprecation warnings; they do not indicate failed tests.

The parent release gate still requires the signed v9 upgrade/data-preservation
check, final-source independent review and all seven required CI workflows.
Physical Samsung results remain separate from these emulator UI results. Recording
and native ASR mechanisms were not modified by this correction, so no additional
30-minute microphone or 24-hour synthetic trial was started for this UI change.
