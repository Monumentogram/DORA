# ADR-0021 — No application PIN / App Lock prompts in DORA

**Decision:** APPROVED_BY_PROJECT_OWNER / IMPLEMENTATION_PENDING  
**Date:** 2026-10-23  
**Scope:** DORA local recording and on-device content in Alpha **and all subsequent application versions**.  
**Source baseline:** `d7545e0de1ff53b3826e3b924bf58533ca04884d` (owner-only isolated Pilot v2, PR #108).  
**Authority:** Explicit Project Owner instruction: the application PIN is not needed in Alpha and must not return in future versions.

## Binding product decision (persistent future-version rule)

**DORA must not require, request, create, configure, or display a separate application PIN, passcode, pattern, biometric prompt, or Android system-credential challenge as a prerequisite for ordinary local functions.** This applies to launching DORA, starting/pausing/resuming a recording, viewing saved records, authenticating/reading encrypted audio, checking a recording, performing ordinary local deletion, and local ASR/transcript/history operations, in Alpha and later versions. Do not add it back as a default or "security improvement" in a subsequent stage.

DORA must not demand that the owner configure a secure Android screen lock merely to use these local functions. Android's own device-level lock screen, if configured by the user, remains under Android/user control and is **not** disabled or modified by this decision. Runtime microphone permission, clear participant-recording disclosure, and separate consent to Cloud/external processing remain mandatory.

**This decision is not an instruction to remove encryption or permit unrestricted background/plaintext access.** Retain SQLCipher, Android Keystore-based encrypted storage, AEAD/AAD, file ownership/sandbox isolation, backup exclusion, deletion/tombstones, fail-closed corruption handling, and appropriate in-process lifetime/foreground/recording-epoch fencing. Replace the PIN-linked AppLock proof with a product-specific, noninteractive local access authority for current user-owned foreground actions; it must not be described as proof that the human user entered a credential. Recording already started with explicit consent may continue under the existing FGS/epoch contract with screen off; never start the microphone automatically on reboot or process restart.

Do not require system user authentication for historical data continuity, create a parallel plaintext store, regenerate missing vault keys, silently reconcile protected LONG-01/LONG-02, or access user 0 during this change. Test both secure-device and no-secure-credential states using synthetic vaults on API28/API36 and the isolated Pilot environment before physical rollout.

## Security trade-off accepted by Owner

Removing an in-app authentication factor means a person with access to an already unlocked Android user session can access DORA's locally stored recordings through the UI. Device OS security and protected app storage still provide other protections, but encrypted-at-rest audio is not equivalent to app-level access authentication. Show the limitation accurately in product/security documentation, without introducing a PIN prompt to address it.

This decision does **not** remove account/OIDC authorization when a future Cloud service requires it, nor remove consent/ownership checks for external uploads or server operations. It also does not authorize Cloud runtime, production distribution, or any physical changes to historic user 0.

## Existing contracts and gates

- Supersedes the **product requirement** for an in-app credential/App Lock prompt in ADR-DEV-001, ADR-DEV-002, Stage 7.4 and `docs/security/DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE.json` **for future PIN-less local DORA builds only**.
- Do **not** relabel old App Lock acceptance evidence, old no-PIN development exceptions, or historical FAIL/NOT_RUN records as PASS. Historical proofs remain immutable.
- The pre-existing `DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE` gate remains OPEN in prior releases until governance formally supersedes its PIN-related checks for the new product profile; use an explicit `SUPERSEDED_BY_ADR_0021 / REQUALIFICATION_REQUIRED` disposition, not a false SATISFIED.
- Production user 0 encrypted-vault/Keystore continuity, LONG-01/LONG-02 protection, package-upgrade safety and signed distribution remain **separate unresolved** gates. The pilot v2 physical PASS does not transfer to a changed binary.
- No PIN prompt should reappear in future Stage 9+, Beta or released local-product versions unless the Owner explicitly reverses this decision in a new ADR.

## Implementation obligations

1. Inventory and remove every runtime and UI request for application unlock, credential setup, PIN, pattern, or biometric challenge in ordinary local flows. Include `AndroidAppLock`, `AndroidAuthProof`, `AndroidRecordingAccessManager`, `AndroidProductAudioRuntime`, recording/resume/recovery/saved-recording UI and all storage/read/delete entrypoints. Do not replace prompts with a cosmetic dialog or a no-op "authentication succeeded" event.
2. Redesign local noninteractive foreground authority with explicit revocation and epoch semantics, preserving safe ongoing capture with screen off and preventing stale plaintext borrow/automatic microphone start. Require an explicit permitted UI action for sensitive deletion but **not** a device PIN.
3. Preserve encrypted storage and Keystore keys with `setUserAuthenticationRequired(false)` where applicable; test a device without secure credential and a device with a credential, without modifying the original user 0. Missing/invalid keys fail closed; no automatic vault recreation.
4. Remove misleading copy and buttons such as "Разблокировать DORA", "Настройте PIN-код" and "Требуется разблокировка DORA" from Alpha and all future user-facing local flows. Do not remove truthful generic storage/error messages.
5. Add regression checks that validate **no app PIN/biometric prompt** across first launch, Start, Pause/Resume, saved history, authenticated readback, delete, process restart and screen-off, while retaining AEAD, owner/user isolation and foreground/epoch safety. Check actual manifest/DEX/resources of the release-derived APK; verify with API28/API36 and a new physical smoke of exact candidate after separate approval.
6. Update Stage 7.4/security contracts, acceptance matrices, ADR references, release gates and Google development tracker to reflect the new approved product scope without rewriting historical evidence.
7. Prepare isolated, owner-local candidate first, then request separate approval before installing/updating any APK or changing user credentials, accounts, package data or historic recordings.

## Current disposition

Owner decision: **APPROVED**. Code removal: **NOT_IMPLEMENTED / NOT_VERIFIED** on 2026-10-23. No APK was changed or installed by this ADR. The next Codex task must implement and test the new local-access policy; do not claim PIN-less DORA is already running until that implementation passes relevant CI, independent review and a new physical acceptance.
