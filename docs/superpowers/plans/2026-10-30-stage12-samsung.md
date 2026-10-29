# Stage 12 and Samsung Alpha release-track implementation plan

> Execute inline with superpowers:executing-plans. The owner explicitly requests autonomous execution; no additional design approval round. Independent whole-branch review follows verification.

**Goal:** Deliver an upgrade-compatible signed Preview v7 for owner Samsung testing, preserving truthful Stage 12 acceptance boundaries.
**Architecture:** Retain Signal UI, SQLCipher/Keystore, current schemas, pinned ARM64 Whisper and manual Cloud action identity. Add bounded failure/upgrade evidence and fix only reproduced daily-use defects.
**Tech Stack:** Kotlin/Compose, Android API 28–36, Gradle/JVM17, Python host gates.
**Spec:** Owner Stage 12 request; Technical Plan Stage 12 security, ADR-0010 internal Alpha overlay, ADR-0021 PIN-free access, ADR-0024 Preview identity, ADR-0028 HTTPS ingress.

## Global constraints

- Base `5957acb6e54f733ae5224c27b73a4b55eea4360e`, dependent on Draft #124 then #123. No merge.
- Package `com.monumentogram.dora.alpha.preview`; existing owner certificate; versionCode 7. Production/Pilot identities unchanged.
- No uninstall, data clear, profile reset or signing-key change. Physical install requires owner's exact-artifact permission.
- Synthetic tests only. No live AWS resource mutation or new paid resources. No secrets/model weights in public artifacts.
- Preserve 300-second recording cap and model quality limitations. No endurance campaign.
- Samsung physical acceptance and POCO user10 upgrade remain pending unless actually executed with permission.

## Review focus

- A lost final upload acknowledgement must resume the same action without duplicate versions.
- Network failure before send or during polling must preserve Local history, selected version, edits and audio.
- Provider refusal/full storage must report failure, never a successful export.
- APK replacement/reboot must preserve Keystore, encrypted data, settings and imported model.
- Artifact identity/ABI/page alignment and public contents must be checked before upload.

## Task 1: Failure regression coverage

- [x] Add `CloudFailureRecoveryTest.kt` using real encrypted Android session and a bounded transport fault fixture. Assert failure/retry, same action, one Cloud version, preserved edits/audio and reopen.
- [x] Add test-only `FaultDocumentsProvider.java` and `TextExportProviderTest.kt`; exercise ContentResolver open refusal/ENOSPC, success UTF-8, cancellation and retry.
- [x] Run targeted instrumentation; no production runtime defect reproduced. Test harness defects repaired and verified.

## Task 2: Upgrade and package qualification

- [x] Add `AlphaUpgradeTest.kt` prepare/verify phases: synthetic audio, Local/Cloud versions, edits/selections, settings and optional pinned model. Baseline app -> candidate install -r -> restart/reboot/offline verification; no clear/uninstall.
- [x] Advance Preview identity and artifact/version-label guards to v7. Keep schema/native/signing identity unchanged.
- [x] Run unit/host/static gates, signed APK audit and bounded API36 instrumentation. Record exact fixture/build differences.

## Task 3: Release evidence and publication

- [x] Document Stage 12 scope mismatch, compatibility, privacy/deletion/incident/upgrade runbooks and remaining gates. AWS deletion follow-up: Issue125.
- [ ] Independent whole-branch review; repair findings; publish Draft PR based on #124 and wait for mandatory CI.
- [ ] Publish approved owner Alpha APK/checksum to the existing GitHub prerelease channel after artifact audit. Provide phone-only installation instructions and explicit Samsung pending status.

## Scope reconciliation

The Technical Plan calls Stage 12 security; ADR-0010 and the accepted first-Alpha documents also route integrated Alpha exits through Stage 12. Neither authorizes silently renaming the stage or waiving all 14 Cloud exits. This task implements the overlapping protection/failure/upgrade checks and a separate Samsung release-track. Full Stage12 PASS requires its outstanding security/integrated evidence, not just a signed APK.
