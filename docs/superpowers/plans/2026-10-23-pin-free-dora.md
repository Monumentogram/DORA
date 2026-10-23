# PIN-free local DORA implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement and verify each task. Owner explicitly authorized autonomous execution and independent review.

**Goal:** Implement ADR-0021 permanently without touching physical devices or historical evidence.

**Architecture:** Replace credential authentication with process-local foreground epoch capabilities. Explicit Activity actions capture capabilities; pause/screen-off revoke all existing read/action capabilities. Activated recording writers retain separate recording ownership until terminal cleanup. No credential state participates in writer authorization.

**Tech Stack:** Kotlin/JVM 17, Android API28–36, Compose, existing SQLCipher/Keystore/AEAD.

**Spec:** `docs/adr/ADR-0021-owner-no-app-pin-local-access.md` and Owner implementation request of 2026-10-23.

## Global constraints

- Start exactly at `a14d30a8172f13361fbe29b08cdb463651fc954a`; branch `codex/implement-pin-free-dora`; stacked Draft PR base `codex/adr-no-app-pin-local-access`.
- No merge, rebase, force-push, main changes, physical installation or historical vault access.
- Preserve cipher formats, keys, fail-closed behavior, permissions, participant acknowledgement, 4,800,000-frame limit and writer ownership.
- Pilot `com.monumentogram.dora.alpha.pilot`, `0.1.0-owner-alpha.3 (3)`, existing signing identity.

## Review focus

- Queued work captured before screen-off must stay revoked after foreground returns.
- Writer must survive screen-off without obtaining plaintext read authority.
- Resume must reject a stale grant and closed/replaced recording access at the actual microphone boundary.
- Missing keys in an existing vault must never trigger creation or replacement.
- Terminal writer retirement must finish before a new vault owner opens.

### Task 1: Local authority and runtime

Files: core/audio persistence/auth, persistence/runtime, recording authority and associated tests.
Interface: `LocalAccessSession.resume/pause/revoke/capture`, `Authorization.requireActive/withPlaintextDelivery`; `AndroidLocalAccess.capture(Activity)` validates resumed Activity and OS unlocked/interactive state without secure-credential prerequisite.

- [ ] Add failing no-prompt source gate and foreground/epoch tests.
- [ ] Remove AndroidAppLock, AndroidAuthProof, AppLockAuthenticationActivity and development authentication overrides; replace credential state with explicit local authority.
- [ ] Migrate coordinator and recorder handoffs; preserve one-shot Resume and writer/read separation.
- [ ] Run core audio unit and regression suites; commit reviewed changes.

### Task 2: Product UI and device regressions

Files: RecordingScreen, RecordingRecoveryCard, SavedRecordingActions, ownerPilot instrumented tests and host device runner.
Consumes: current local foreground state and explicit runtime requests from Task 1.

- [ ] Replace PIN-dependent tests with PIN_FREE_LOCAL_ACCESS assertions, recording supersession rationale.
- [ ] Remove unlock/setup UI; keep consent and RECORD_AUDIO.
- [ ] Verify Start/Pause/Resume/Stop, cap, readback/delete/recovery, screen-off, restart and negative paths on disposable API28/API36.

### Task 3: Permanent gates and Pilot v3

Files: app/build.gradle.kts, tools/owner_alpha_pilot_artifact.py, no-prompt gate, CI workflows, security disposition document.

- [ ] Enforce NO_APPLICATION_PIN_OR_BIOMETRIC_PROMPT in source and actual Manifest/DEX/resources.
- [ ] Set Pilot v3 identity and audit signing/version/native/model/SBOM parity.
- [ ] Preserve historical security records; add SUPERSEDED_BY_ADR_0021 / REQUALIFICATION_REQUIRED disposition and keep unrelated gates open.
- [ ] Produce two independent clean signed builds and compare bytes, if required existing custody inputs are available.

### Task 4: Review and publication

- [ ] Independent adversarial whole-diff review; resolve P0/P1/P2 with regression evidence.
- [ ] Privacy audit, commit/push, stacked Draft PR, exact-SHA CI SUCCESS.
- [ ] Publish precise result/evidence and gaps; request physical update separately only after software PASS.

## Execution ledger

- Baseline PR #109 HEAD verified through GitHub; isolated worktree created. Native worktree tool rejected parent non-repository directory, so Git worktree used on repository.
- Ruling: execute inline without an additional design approval round; the Owner supplied an approved detailed architecture and explicitly requested autonomous implementation.
- Ruling: cryptographic authentication of ciphertext remains distinct from removed human credential authentication.
