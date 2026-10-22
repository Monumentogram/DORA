# Isolated owner Alpha pilot implementation plan

> **For agentic workers:** Use superpowers:executing-plans; independent tool/test domains use superpowers:dispatching-parallel-agents. Track actual outcomes in the owner-local execution ledger.

**Goal:** Produce a signed operator-free owner pilot and evidence for separate physical admission.
**Architecture:** Release-derived `ownerPilot`, ordinary saved-recording actions over existing authenticated ports, variant-specific artifact and emulator checks.
**Tech stack:** Kotlin/Compose, Android API28–36, SQLCipher/Keystore, Gradle, Python host tools.
**Spec:** `docs/adr/ADR-RECORDING-013-isolated-owner-alpha-pilot.md` and the owner's 2026-10-21 software-only decision.

## Global constraints

- Start at 52e4ce8bdf074ec021ae52c3c38ad9d26c24e128; branch codex/isolated-owner-alpha-pilot; stack on codex/alpha-security-release-preflight.
- Package com.monumentogram.dora.alpha.pilot, code 1, name 0.1.0-owner-alpha.1.
- No POCO commands or changes, historical reads, merge/rebase/force-push or distribution.
- All existing security/restoration/reliability gates retain their status.
- Do not depend on PR107 or change Recovery/schema/crypto/cap.

## Review focus

- Fresh authentication invalidating UI state: no auto-reload loop or stale callback publication.
- Partial/corrupt/locked readback: never report complete verification; clear borrowed PCM.
- Deletion cancelled or interrupted: exact one-use identity and verified durable outcome.
- Large font/compact screen: vertical scrolling, reachable actions and honest status.
- CI versus local candidate: actual pilot variant and distinct signing/provenance evidence.

### Task 1: Ordinary recording acceptance

Files: app recording/SavedRecordingVerification.kt and test; SavedRecordingActions.kt; RecordingRecoveryCard.kt.
Interfaces: verify(session: ProductAudioSession, identity: AudioIdentity): AudioResult<VerifiedRecording>; VerifiedRecording(identity, frames, units).

- [ ] Add failing tests for exact identity/contiguous full finalized readback, partial/corrupt/count rejection, revocation and buffer clearing.
- [ ] Implement bounded verification over existing OriginalAudioPort; run app JVM tests.
- [ ] Add ordinary metadata/readback/deletion actions using runtime authentication and confirmation; explicit discovery; protect callback publication and background state.
- [ ] Validate encrypted/UI flow through Task 3, including large font.

### Task 2: Variant and artifact custody

Files: app/build.gradle.kts, app lockfile, ownerPilot resources; tools/owner_alpha_pilot_* and host tests.
Interfaces: ownerPilot release fallback; unsigned intermediate; owner-local build/audit CLI and exact variant graph.

- [ ] Add pilot identity/build type and test-only signing selection; retain release overrides and exclude operator sources.
- [ ] Write/run negative artifact tests before implementing actual manifest/DEX/certificate/provenance verifier.
- [ ] Export ownerPilot dependency graph and SBOM; verify private model/AAR/native bytes and alignment.
- [ ] Generate separate owner-local signing identity, verify ACL/pin, execute two independent clean builds after final source commit and compare signed outputs.

### Task 3: Variant-specific synthetic verification

Files: androidTestOwnerPilot, tools/run_owner_pilot_device.py and negatives, owner-alpha-pilot workflow, exact inventory.
Interfaces: actual ownerPilot target/test APKs, emulator-only admission, exact receipt at source SHA.

- [ ] Exercise real system credentials, release override denials, finalized/partial verification and deletion, cancellation/revocation/stale sessions.
- [ ] Exercise ordinary user actions at compact width/large font, separate UID/storage, first-run and microphone denial.
- [ ] Bound emulator runner and cleanup, reject physical serial before any query, keep test PIN out of output.
- [ ] Run API28/API36 and enable inherited mandatory Stage8/isolated workflows on this branch without weakening their checks.

### Task 4: Review and handoff

Files: ISOLATED_OWNER_ALPHA_PILOT_PHYSICAL_PROTOCOL.md, additive decision/status record; owner-local RESULT and evidence digest manifest.

- [ ] Specify exact future user10/serial10, topology/package checks, recording workflow and STOP conditions; no physical execution.
- [ ] Run formatting, analysis, lint, host/JVM suites and all applicable exact-SHA CI jobs.
- [ ] Independent branch review; fix all P0/P1/P2 with regression evidence.
- [ ] Audit public diff/PR/logs/artifacts; keep APK/signing/private inputs owner-local.
- [ ] Publish stacked draft PR, attach it, preserve prior PRs/gates, report all requested evidence and next separate owner installation decision.
