# Stage13 owner Alpha stabilization вЂ” working qualification

Date2026-11-02. Branch `codex/stage13-owner-alpha-stabilization`, based on published Alpha v9 source `49ca2070f15a618d145ded307fc87249f382b22c`. Stacked dependency PR128 -> PR127 remains intact; no merge.

## Implemented scope

- Transcript history now reloads when the app returns to foreground. A pending refresh waits for the existing worker to settle; selected recording refresh follows the same path. Existing privacy clearing of unsaved drafts remains unchanged. Actual Compose RED/GREEN and related regression: [UX evidence](STAGE13_UX_STABILIZATION.md).
- Composite Cloud resume quotes check whether remaining unstarted allocations can move into the current UTC day's existing budget. An exhausted day is rejected before another audio upload. No new charge is reserved by a quote and already-started attempts are not recharged. Backend91/91 tests pass; this source fix is **not deployed to production**. [Cloud evidence and live runbook](STAGE13_CLOUD_READINESS.md).
- Immutable, bounded Whisper controls compare exact original PCM with isolated context using the same model/runtime. No production decoder/model/window change or improved full-recording quality is claimed. [Whisper evidence](STAGE13_WHISPER_QUALITY.md).
- Candidate identity advances to Owner Preview10, retaining package/signing and unchanged database/native/storage payload contracts. The existing seven mandatory CI runs include the Stage13 branch; no historical assertions or inventories removed.
- [Samsung procedure and observations](STAGE13_SAMSUNG_ACCEPTANCE.md) records the owner's later explicit device authorization. Physical trial is in progress; initial v9 startup and microphone service are observed on actual ARM64 API36.

## Evidence boundaries and current matrix

This working record precedes final candidate qualification. New-source and inherited checks are labeled separately. Final exact-candidate receipts must accompany any distributed APK; a prepared build is not release authorization.

| Direction | Current result |
|---|---|
|Stage13 overall|PARTIAL; canonical supported matrix, endurance, security/privacy gates remain open.|
|Samsung physical|PENDING; actual v9 install/start/recording observed, remaining sheet incomplete.|
|Signed APK runtime|v9 inherited PASS in documented translated API36 environment; v10 pending.|
|30-minute recording|v9 inherited PASS; unchanged recording engine; actual Samsung trial in progress.|
|24-hour architecture|Inherited PASS_SYNTHETIC_ENCRYPTED_STORAGE_24H; unchanged mechanism, no repeat; long finalize/delete limitations retained.|
|Whisper RU functional|Inherited PASS plus bounded controls documented separately.|
|Whisper RU quality|PARTIAL; retained full-recording14/18, unchanged strict gate.|
|Whisper EN functional|Inherited PASS.|
|Whisper EN quality|PARTIAL; retained exact16/18, bounded17/18; these are markers, not WER.|
|Amazon Cloud software|PASS for scoped backend91/91; Android integrated regression and release CI separately recorded.|
|Amazon Cloud live|PENDING for current candidate and long30/60minute qualification; no live request or production deployment performed.|
|UX regression|Fresh API36 scoped PASS; API28 scoped PASS;165 app unit tests and268 relevant host tests PASS; exact-candidate gates pending.|
|Data preservation|v9 inherited update/reboot PASS; v9->v10 retained-fixture check pending.|
|CI|v9 inherited7/7; new source pending7 required workflows.|
|Review|Pending independent whole-change review.|
|APK|v10 not published; existing [v9 release](https://github.com/Monumentogram/DORA/releases/tag/personal-alpha-20261102-v9).|
|Draft PR|Stage13 creation pending; parent [PR128](https://github.com/Monumentogram/DORA/pull/128).|

## Unchanged limitations

Stage12 remains PARTIAL; AWS deletion issue125 remains OPEN and mandatory before public product release. No production AWS resource, KMS key, configuration or budget changed. Long Cloud is disabled;30/60minute estimates exceed the unchanged daily limit, so a monthly$20 budget alone is insufficient authorization. Owner authorization for Samsung does not authorize Cloud spending.

Historical native crash and degraded VAD runs remain recorded, alongside later v9 PASS. Emulator success is not Samsung acceptance. No24-hour capture/native-ASR claim, full WER claim, or full Stage13 PASS follows from this limited Alpha work. Raw device audio, transcript content, models, credentials and sensitive logs stay outside public source and artifacts.


## CI scope correction and retained candidate evidence

The published v9 qualification contains seven runs across six workflows: push
runs for owner-preview, stage9-local-asr, stage12-alpha, version-label and
pin-free-local-access; pull-request runs for stage18-cloud-asr and
pin-free-local-access. Preserve this exact path/event matrix rather than infer
seven distinct workflows.

The initial Stage13 candidate a0b2a37 mistakenly enabled two extra push triggers.
The optional AndroidCI bootstrap failed its historical Stage7.3 identity gate:
expected version4 while the unchanged inherited alpha-release.properties is
version5. Run41438081587/job125252779073 is retained as a failed optional execution.
Only these two added triggers (AndroidCI and redundant Stage18push) are restored
to their baseline scope; no historical test, validator, assertion or required run
is removed or weakened. Independent review confirmed this applicability correction.

At a0b2a37, two clean owner-signed Preview10 builds were byte-identical, artifact
identity/native audit passed, and the actual retained v9 fixture passed before
update, after update and after reboot. All saved audio, versions, edits, selection,
model and tested settings/installation key remained intact. Initial post-build
audit failed decoding aapt output under Windows cp1251; rerunning only the audit
with UTF-8 passed without changing the source or APK. Final workflow-corrected
source requires fresh build/CI binding; later continuity must retain this original
execution provenance instead of claiming an unperformed direct upgrade trial.
