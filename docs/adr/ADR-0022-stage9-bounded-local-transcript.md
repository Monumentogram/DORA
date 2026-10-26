# ADR-0022 — Bounded local transcript Alpha

Date: 2026-10-23. Status: accepted for owner-scoped implementation; runtime
qualification remains open. Authority: explicit Stage9 implementation request.

## Decision

Saved encrypted recordings may be recognized through the protected
`OriginalAudioReference` port on a separate foreground-owned worker. PCM16LE,
16-kHz mono is converted in memory only, capped at 4,800,000 frames, and wiped
after inference. Every publication revalidates the exact original and journal
authority. Backgrounding cancels processing and clears visible transcript state.
ADR-0021 remains controlling: no PIN, biometric or device-credential prerequisite.

The optional pinned SMALL q8 runtime uses the exact Stage5 source/model identities
and existing retention deadline. Controlled local packaging verifies native pins,
source receipts and notices. Models stay outside Git, CI and APKs. Default builds
report unavailable ASR truthfully. See the [native admission record](../stage1/DORA_STAGE9_NATIVE_ADMISSION.md).
This authorizes integration, not production, all-device or physical acceptance.

Encrypted journal schema4 adds one transcript-state row per existing audio asset.
Bounded explicit binary serialization stores immutable machine versions, separate
append-only whole-document edits with exact before-context, job generations and
selection decisions. CAS updates and durable terminal states reject stale writes.
Reprocessing never replaces the active version or user edits. Selecting another
version requires a decision for every edit outside its context. Whole-document
anchors are START/END; timestamp availability is UNAVAILABLE and quality is
NOT_EVALUABLE, with no inferred timing or diarization.

Audio-only deletion cancels outstanding jobs and keeps transcript/source history.
Ordinary v3 journals migrate transactionally to v4. Historical diagnostic v3
gates are deliberately not migrated or relabelled: their positive read paths need
separate qualification before use with this successor. Process restart marks
unfinished jobs INTERRUPTED; retry is explicit and produces a new version identity.

## Consequences and acceptance

The Alpha editor supports explicit save/copy, version preview and active selection.
It keeps drafts only in foreground memory. Cloud, resumable long-running inference,
word timestamps and production model delivery remain outside this task.

JVM, migration/reopen/editor API28/API36, static/build and exact-SHA CI evidence
are required separately from real JNI/model inference evidence. Static ELF16KiB
alignment does not establish actual16KiB execution. POCO operations require a
later owner authorization. Stage8.6 remains NOT_READY and its deferred reliability
work is unchanged. See [Stage9 result](../evidence/stage9-asr/RESULT.md).
