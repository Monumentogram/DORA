# Stage 9 local ASR and transcript Alpha implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, test first, followed by independent whole-branch review.

**Goal:** Recognize saved encrypted audio locally, retain immutable machine versions and separate user edits, reopen and explicitly select versions.

**Architecture:** Extend the existing generation-bound ProductAudioSession and encrypted Room journal. Decode bounded PCM only in memory on a separate ASR worker through OriginalAudioReference. Optional pinned Whisper SMALL q8 uses a thin JNI adapter; absence never prevents recording. Full-document edits retain exact prior context and source identity; changing versions requires individual decisions for corrections outside the target context. No guessed alignment or timestamps.

**Tech stack:** Existing Kotlin/Compose, Room/SQLCipher/Keystore; pinned whisper.cpp native API.

**Spec:** Owner Stage 9 request (2026-10-23), ADR-0018, ADR-0021, docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md and docs/stage1/DORA_ALPHA_TRANSCRIPT_VERSIONING_EDIT_CONTRACT_V0_1.md.

## Global constraints

- Baseline PR #111 HEAD `4ee27ef2473b0b047ee73be0595e981d08848e94`; new branch, no merge/main changes.
- PCM S16LE, 16 kHz, mono; 300 seconds / 4,800,000 frames maximum. No persistent plaintext audio or transcript store.
- Model SHA-256 `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`, 264464607 bytes; original retention ended 2026-10-25; owner renewed custody/local testing on 2026-10-26 through 2026-11-02 inclusive (Europe/Moscow). No weights in Git/CI or broader use.
- No PIN, biometric, device-credential setup or challenge. Foreground authority remains revocable; leaving foreground cancels ASR and clears visible text.
- Existing Stage8.6 NOT_READY and physical/native qualification gates stay open. No physical POCO operation.
- No Cloud, diarization, summaries, training or renewed Stage5 benchmark.

## Review focus

Late completion after cancel/revocation; interrupted transaction and redelivery; edits on inactive versions and stale selection; audio-only deletion preserving text; wrong digest or expired optional model. Tests must exercise each boundary.

## Tasks

### Task 1: 9.1 protected ASR input and exact optional runtime

- [x] Verify Stage5 model, notices, native identities and retention; record scope limits.
- [x] Add bounded PCM extraction and cancellation tests in core/audio; observe failures before implementation.
- [x] Implement memory-only canonical PCM adapter and pinned optional model verification. Reject gaps, wrong source, failed extraction and oversize input; wipe buffers on all paths.
- [x] Build thin JNI adapter from exact runtime headers; suppress native content logging, support abort and RU/EN. Keep new-runtime qualification separate from old Stage5 results.
- [x] Verify and commit scoped runtime/input changes.

### Task 2: 9.2 asynchronous actual recognition

- [x] Add tests for queue/running/cancelled/failed/interrupted/success, model absence and late completion.
- [x] Implement saved-recording recognition worker, stable action/result identity, explicit retry, safe native lifetime and terminal errors. Never use capture/persistence thread for inference.
- [x] Verify actual native inference using approved public input on disposable emulator if available. No synthetic success used in product.

### Task 3: 9.3 encrypted versions and edits

- [x] Test immutable versions, redelivery collision, independent edits, stale revisions, per-edit selection resolution, codec roundtrip and interruption.
- [x] Extend Room schema with encrypted transcript state, migration and exact schema verification. Atomic guarded updates preserve histories and monotonic revisions. Retain source identity after audio loss.
- [x] Expose transcript operations through the existing session authority, with publish bound to current exact source. Test migration, SQLCipher reopen and stale-session denial on API28/API36.
- [x] Verify and commit the complete worker/persistence vertical slice.

### Task 4: 9.4 UI and integration

- [x] Saved-recording action opens transcript surface with real status, recognition/retry/cancel, language and optional model installation state.
- [x] Editor shows machine text versus edits, saves explicitly, copies on user action and lists/selects versions with individual conflict decisions. Warn on unsaved navigation; clear on background.
- [x] Keep transcript history reachable after audio-only deletion. Use semantic tokens and Compose lifecycle.
- [x] Run JVM, static/build checks, API28/API36 integration; review and fix important findings.
- [x] Create atomic commits and Draft PR112.
- Final-head CI and tracker verdict are recorded after the last push in PR112 and the final report; source snapshots must not predict future CI outcomes.

## Execution notes

Owner explicitly requests implementation without planning approval pauses. Native worktree creation was unavailable because the chat root is a test directory, so a Git worktree was created in the existing sibling worktree directory. Initial normative and artifact audits run independently; implementation dependencies remain sequential.

Source checkpoint30c4dfa: CI software job passed strict static/build/JVM/lint and debug/release assembly. API28/36 startup remained unobserved; successor uses explicit SDK adb and captures failed emulator startup logs. Actual speech inference remains NOT_RUN.

2026-10-26: actual RU/EN native integration passed on API28 and API36 page-size16KB, including callback cancellation, immutable history and vault reopen. Manual UI native rerun/edit/process-restart passed. Ordinary v1/v2/v3 migration coverage expanded to9 tests; full runner now20/API. See the current evidence record and exact-head PR checks.
