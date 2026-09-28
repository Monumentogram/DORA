# DORA owned corpus acquisition v1

**Historical protocol.** The Owner's prospective [eight-recording amendment](DORA_CLOUD_62D_EIGHT_CLIP_PROTOCOL_V0_1.md) supersedes this document's acquisition counts, selection/reserves, noisy coverage and manual timing requirements for the nearest bounded evaluation. Do not ask for 72 recordings. Recording is deferred until the explicit message “Готов записывать”. The original private inventory, consent and history are retained.

Status: PREPARATION_READY / HUMAN_CAPTURE_AND_GOLD_REQUIRED. Scope is isolated 6.2D host tooling; no Android runtime, AWS request, provider admission or 6.3 execution. Frozen [Phase A v0.1](DORA_CLOUD_EVALUATION_PHASE_A_V0_1.md) remains immutable. This preparation is not Phase A v0.2.

## Authority and privacy boundary

The Project Owner expressly authorized creation of a separate owner-spoken corpus solely for DORA 6.2D Amazon Transcribe evaluation in eu-central-1. Common Voice/MDC remain rejected or unproven for Cloud. No third-party speech is authorized. Only the same owner speaks both languages; results cannot establish population or speaker diversity. This shortfall is recorded before selection rather than hidden by the one-speaker-per-slice preference in v0.1.

Before microphone capture, the local tool requires an unchecked confirmation of this exact statement:

> I am the speaker of these recordings and authorize DORA to process this corpus with Amazon Transcribe in eu-central-1 solely for the bounded 6.2D evaluation under the current DORA Alpha privacy/retention policy.

Attestation version is `dora-owned-corpus-attestation-v1`; UTF-8 text SHA-256 is `e5397cda98d84f558a1d9ef84bbc3711897cc7a48c9d09c749ea4e734e6acb23`. No participant identity is requested or published. A local confirmation is necessary; the preparation agent has not supplied it.

Private storage must resolve outside every Git worktree, including `.git` pointer-file worktrees. The launcher selects a sibling private directory. Root checks reject repository descendants and Git ancestors; relative artifact resolution rejects links escaping the private root. Audio, exact scripts/prompts, source/material/audio/reference hashes, references, annotation drafts, word timings, selection IDs, exclusion ledger and detailed manifest stay private. Git receives only protocol/tooling/schema, counts and whole-inventory/manifest hashes. Default location is implementation configuration, not a public absolute path. The private directory inherits the current user's Windows access permissions; it is not an encrypted vault. Do not share or synchronize it into a public location.

## Prospectively authored inventory

Source release is `dora-owned-corpus-v1.0.0`. Before any recording, thirty original READ scripts, thirty topic-only SPONTANEOUS prompts and twelve diagnostic tasks were written for this project and saved in the private canonical inventory. Whole inventory SHA-256: `9382e335345135438ac1d8be4103f7631d5182b091bcfcd7bb3ce413006e7a4b`. Serialization is UTF-8, unescaped Unicode, sorted object keys, compact separators, no nonfinite JSON numbers, and one trailing LF. Exact material SHA-256 values are private and bound again in the final manifest.

| Language | READ candidates | SPONTANEOUS candidates | NOISY candidates | Total |
|---|---:|---:|---:|---:|
| RU | 15 | 15 | 6 | 36 |
| EN | 15 | 15 | 6 | 36 |

RU scripts contain 57–61 normalized words; EN scripts contain 63–68. Content covers everyday work, instructions, travel planning, public spaces and ordinary objects. Three scripts per language contain bounded fictional names/numbers/dates; others avoid these features. No copyrighted passage, personal fact or customer content is used. READ acquisition requires 20–45 seconds; SPONTANEOUS and diagnostic acquisition requires 20–60 seconds. Speaker answers to spontaneous prompts must be composed while speaking, never read from a prepared answer. No provider output can change the text.

Diagnostics per language comprise two moderate room-noise tasks, two increased-distance tasks, one mild-reverberation task and one playback/recapture of the owner's own earlier READ recording through an ordinary speaker. Only moderate safe volume is allowed. No unrelated background speech may be captured. The acquisition category is bound to the candidate in the inventory and recording. A technically unavailable condition must be excluded honestly; six diagnostic candidates have no reserve, so a missing valid condition blocks required coverage.

Private authored inventory is already installed on the prepared host. It is intentionally not bundled in public Git; missing inventory fails closed, never regenerates different prompts. Preserve that private asset across resume. Its initializer is also private. This public protocol binds its exact whole-file hash.

## Recording and human references

The launcher opens a loopback-only recorder with microphone selection, test meter, explicit record/stop, playback and guided material. The browser retains mono PCM16 capture at its native rate and finalizes mono signed PCM16 LE WAV at exactly 16000 Hz using its offline audio context. No upload or external ASR exists in the acquisition tool. Capture provenance includes source rate, exact source and upload hashes, duration, frame count and conversion recipe hash. Native and evaluation duration must agree within 1000 microseconds. No normalization of gain, manual trim or denoising is introduced.

The backend checks RIFF size and actual chunk lengths, unique chunks, PCM format, channels, sample width, byte rate, block alignment, complete data frames and duration. It rejects truncation and trailing data. Accepted captures are immutable. A submitted invalid capture is preserved privately with its validation code and a pre-provider exclusion; it cannot be silently rerecorded to replace the same candidate. The UI prevents ordinary duration errors with timed stop. Microphone tests are ephemeral and do not create candidates.

READ starts with the frozen script in the reference editor. The owner must listen and explicitly verify it or replace it with the actual spoken text, including deviations. SPONTANEOUS/diagnostic references require full human transcription and explicit verification. `HUMAN_VERIFIED_REFERENCE=true` is never inferred from a script, draft or recording. Prior reference revisions are retained privately. Only `normalize` from the existing pinned text contract is reused: CPython 3.12 / Unicode 15.0.0. No Local model/profile/gate is inherited. Raw and normalized reference hashes bind exact UTF-8 strings.

## Deterministic selection, reserves and exclusions

Every candidate must have accepted audio and a human reference or an explicit pre-provider exclusion before selection. Duplicated source or upload hashes prevent selection until an exact duplicate exclusion is recorded. All non-excluded audio and reference hashes are reread before selection. Exclusion codes are unproven rights, other/mixed language, missing human reference, corrupt/undecodable audio, invalid format/duration, duplicate source, empty normalized reference, or reference mismatch. Exclusion cannot be based on expected difficulty or model results.

Within each language and speech class, sort ascending by `SHA256(source_release + "\n" + source_audio_SHA256)`; source hash is the preserved native-capture WAV hash, encoded in lowercase hexadecimal. Select first 12 READ and first 12 SPONTANEOUS. Remaining eligible clean candidates are ordered reserves; ordinarily three per class per language. Preflight exclusions consume their slots and naturally promote the next eligible hash rank. Noise selects first six eligible per language separately. Do not relax counts or add arbitrary replacement recordings. Selection is persisted immutably, with the exact exclusion ledger, before annotation/provider output. After selection, audio, references and exclusions are locked.

The timing subset is the first six of the selected READ clips in the same hash order per language, with at least 100 verified normalized reference words in aggregate per language. It does not choose easier words or clips. Each word of every selected timing clip needs an independent manual start/end mark. No ASR, forced alignment, interpolation or generated anchors are supplied. The annotation UI provides waveform, zoom, playback and keyboard/click marks. Partial human drafts survive reload and are explicitly non-authoritative. Final submission requires `HUMAN_ANNOTATED_BLIND_TO_AWS_OUTPUT=true` and validates exact word order, one normalized token per word, integer microseconds, `0 <= start <= end <= duration`, nondecreasing starts and ends, and no missing endpoints. Per-clip JSON and its SHA-256 stay private.

## Long fixtures and final private manifest

Finalization requires 24 quality and six diagnostic clips per language and six complete timing clips / at least 100 words per language. It builds two fixtures per language from authorized selected clean clips in frozen quality order (READ order followed by SPONTANEOUS order). Concatenate complete clips cyclically, then take exactly the prefix frames needed from the last clip. This yields exactly 4,800,000 frames / 300 seconds and 9,599,984 frames / 599.999 seconds. No silence is needed; the allowed silence budget used is zero. Every segment records its source clip/hash, source start, output start and frame count. The final segment may end within speech: that known boundary is part of the input integrity recipe. Fixture/recipe hashes are frozen before AWS execution. These repeated/derived fixtures test mechanics, full-input integrity, limits and latency, never additional independent WER samples.

The private manifest contains source release, authority, destination, conversion recipe, whole inventory hash, per-material hashes, source/upload hashes, references and hashes, timings and hashes, selected/reserve IDs, exclusion ledger, single-speaker limitation, composition recipes and audit events. Finalization makes it immutable. `validate_manifest()` rereads bound source, upload, timing, selection and composite files on resume; corruption blocks use. Only `public_summary()` is safe to publish. Schemas describe private objects; their instances must never be committed.

Preparation does not assert global AWS output absence or grant live execution. The later operator must independently verify zero prior provider attempts/results, full AWS preflight, frozen budget and successful publication/push/refetch of an exact prospective Phase A v0.2 before the first Transcribe request. A local finalized corpus alone is insufficient. v0.1 quality/timestamp/latency thresholds remain unchanged. Cloud cleanup obligations under [ADR-0012](../adr/ADR-0012-alpha-retention-periods-and-provider-copy-limits.md) are separate from private local original retention; no local corpus is automatically deleted by this recorder.

## Verification and remaining human boundary

Backend tests use synthesized WAV bytes, not the owner's voice. They exercise attestation, repository exclusion, source/final validation, immutable capture, explicit gold references, complete selection, duplicate rejection, reserves, blind complete human timing, draft non-authority, tamper detection and exact long fixtures. The actual prepared inventory still has zero recordings, zero verified references, zero timing clips and no selection/manifest. The owner must perform recording and gold annotation personally. Only after those artifacts exist can subsequent account binding and Phase A successor publication proceed.
