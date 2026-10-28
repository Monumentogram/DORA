# Permanent owner-local Whisper Model — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the calendar-based disabling and deletion of the already verified owner-provided Whisper SMALL q8 model while preserving integrity checks and offline functionality.

**Architecture:** Keep `PinnedModelStore` an atomic, hash-and-size-pinned, app-private, no-backup model store. Remove the expiration clock and deletion path from it and the native recognition gate. The model remains optional and external to the APK; this decision does not authorize redistribution, public rollout or broader retention of personal audio.

**Tech Stack:** Kotlin, Android Compose, JUnit 4, Gradle, JNI whisper.cpp.

**Spec:** User's explicit indefinite owner-local model instruction on 2026-10-28; predecessor ADR-0022. Superseding ADR-0027 and Product Decision DEC-049 accompany the implementation.

## Global Constraints

- Starting point: `codex/stage18-1e-live-deployment` SHA `59ca965e35a305ece8b6e17e0d679bf199a2b264`; develop only on an isolated branch, never commit to `main` or the AWS branch.
- Preserve exact model size `264464607` and SHA-256 `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`.
- No model weights, signing keys, original audio or private receipts in Git, CI, logs or APK.
- Preserve foreground-owned cancellation, all encrypted recordings/edits/versions, and AWS consent, request, retry and cleanup paths.
- Never delete a valid installed model just because the date passed. Manual user deletion/uninstall and genuine corruption remain separate conditions.

## Review Focus

- Correct installed model when reopening app after November 2: `installed()` returns the original file unchanged.
- Invalid replacement while a valid model exists: original bytes and path remain unchanged.
- Changed digest or size: imported candidate is rejected without corrupting the admitted model.
- Interrupted import: its transient `.pending` file is removed without replacing a good model; a stale file left by process death cannot cause deletion of an admitted model.
- Cancellation of running JNI after date: user cancellation remains effective without calendar-based cancellation.

---

### Task 1: Pin the permanent model lifecycle with tests

**Files:** Modify `android/app/src/test/kotlin/com/monumentogram/dora/transcript/PinnedModelStoreTest.kt`.

**Interfaces:** `PinnedModelStore(directory: File, expectedBytes: Long, expectedDigest: ByteArray)`, `install(InputStream): Boolean`, `installed(): File?`.

- [ ] Write tests for valid model remaining installed after repeated access and new store instance; for invalid replacement preserving model; for interrupted import cleanup and stale `.pending` not deleting the model.
- [ ] Execute targeted test; require expected failing test/compilation under the old clock-bound signature.

### Task 2: Remove expiration and stale UI copy

**Files:** Modify `LocalAsrModel.kt`, `WhisperNative.kt`, `TranscriptScreen.kt` under `android/app/src/main/kotlin/com/monumentogram/dora/transcript/`.

- [ ] Remove `EXPIRES`, `withinRetention`, injected clock, date-based delete and native deadline cancellation. Preserve SHA-256/size, atomic import and user cancellation.
- [ ] Change only the visible expiry explanation; keep timestamp-accuracy disclaimer and optional import.
- [ ] Run `:app:testDebugUnitTest` (and release where available), static analysis and Android build. Observe green after red.

### Task 3: Record owner scope and verify non-regression

**Files:** Create `docs/adr/ADR-0027-permanent-owner-local-whisper.md`; modify `docs/DORA_MVP1_PRODUCT_DECISIONS.md`.

- [ ] Record that only optional owner-local model custody has no calendar deadline; do not change personal-audio, AWS, dataset, or public-release retention policies.
- [ ] Run full documented Android checks and check diff for no AWS/recording/data-schema changes.
- [ ] Commit exact scoped files, open Draft PR against the product branch; do not merge without owner instruction.
- [ ] After CI success, separately prepare an installed-in-place APK and physical POCO verification before claiming the phone is fixed.
