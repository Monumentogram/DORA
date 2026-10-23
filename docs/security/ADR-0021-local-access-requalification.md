# ADR-0021 local access requalification

Owner decision: permanent PIN-free local DORA in Alpha, Beta and subsequent releases.
Implementation source starts at `a14d30a8172f13361fbe29b08cdb463651fc954a`.
This record supplements history; no historical evidence or restoration checklist is relabeled.

## Call graph before and after

| Entry/boundary | Previous route | ADR-0021 route |
|---|---|---|
| Start / recovery continuation | RecordingController → AndroidRecordingAccessManager → AndroidAppLock.requestUnlock → AppLockAuthenticationActivity → AndroidAuthProof → AppLockSession | Explicit UI action → AndroidRecordingAccessManager → AndroidLocalAccess.capture(exact resumed Activity) → LocalAccessSession.Authorization → exclusive writer vault |
| UI visibility / foreground return | AndroidAppLock.state after credential proof; separate unlock button | AndroidLocalAccess foreground state; lifecycle resume permits new local actions, never revives an old authorization |
| Saved list / readback / Recovery | RecordingRecoveryCard / SavedRecordingActions → requestOpen → credential prompt → AudioRuntimeCoordinator | Same explicit actions → requestOpen → capture local capability → coordinator worker; fresh authority rechecked before publication |
| Authenticated audio verification | SavedRecordingVerification → generation-bound session → OriginalAudioLifecycle → authenticated ciphertext | Same source leases, AEAD, manifest and full-range verification; bounded plaintext borrow checks local foreground generation |
| Delete | SavedRecordingActions → credential prompt → identity-bound deletion confirmation | Local foreground capability → same explicit one-use deletion confirmation → tombstones/key/file cleanup |
| Pause | Existing capture owner controls pause | Unchanged; no credential route |
| Resume | Current AppLock proof or new system prompt → RecordingResumeGrant | Explicit foreground action → current local capability + exact RecordingAccess/epoch → one-shot RecordingResumeGrant → service ownership checks → microphone boundary |
| Screen-off | Reader proof revoked; writer required secure credential availability | Reader capability revoked; already activated writer uses only recording epoch/ownership; no new microphone start |
| Stop / retirement | RecordingAccess.close → revoke → asynchronous exclusive retirement | Unchanged, with LocalAccessDeniedException for retired handles |
| Device prerequisite | AndroidDeviceSecurityPolicy / DeviceSecurityPolicy required isDeviceSecure unless debug override | Removed. Foreground reads/actions check interactive + !isDeviceLocked, independent of whether a secure credential exists |
| Process death | Process-local credential proof lost | Process-local capability and recording ownership lost; restart cannot start capture |

Removed from all product source sets: AndroidAppLock, AndroidAuthProof,
AppLockAuthenticationActivity, AppLockSession, AndroidDeviceSecurityPolicy,
DeviceSecurityPolicy and both development overrides. No fake credential result remains.

## Security contract disposition

ADR-DEV-001, ADR-DEV-002, Stage 7.4 human-credential requirements and the PIN-specific
checks in DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE are
**SUPERSEDED_BY_ADR_0021 / REQUALIFICATION_REQUIRED** for this future local profile.
The original JSON remains OPEN, with its original false checks; it is not SATISFIED.
New evidence must establish foreground/epoch fencing, stale Resume rejection, bounded
plaintext delivery, writer screen-off continuity and no prompt on secure/insecure devices.

Historical user0 vault continuity, LONG-01/LONG-02 preservation, production upgrade
safety, signing custody, Cloud identity/authentication, explicit Cloud consent,
recording ownership and release approval remain separate gates. No physical device,
Android credential, user0 data or historical audio is changed by this implementation.

SQLCipher, AES-GCM/AAD, authenticated manifests, Keystore wrapping aliases and
`setUserAuthenticationRequired(false)` remain unchanged. Existing vault open never
falls back to creating a new vault or key when an existing key is missing/invalid.
The accepted trade-off is that access to the unlocked Android user profile permits
local DORA content access. Encryption at rest does not authenticate the human user.

## Permanent regression gate

`NO_APPLICATION_PIN_OR_BIOMETRIC_PROMPT` runs on every PR/push. Pilot CI additionally
inspects the actual compiled manifest, all DEX files and decoded resources, and runs
the release-derived variant on API28/API36. A new Owner ADR is required to reverse
this product policy. The same unconditional regression suite also runs in the
existing required `android-bootstrap` job, so failure blocks its protected-branch
status without depending on historical validator profile routing. No branch
protection setting or main branch was changed.

## Test migration

Credential challenge/proof/development-bypass acceptance tests are superseded by
LocalAccessSessionTest, AndroidLocalAccessTest and the PIN_FREE_LOCAL_ACCESS Pilot
scenario. Existing concurrency, original-source, corruption and deletion assertions
remain. Historical test evidence is preserved in Git and evidence artifacts.

The baseline core/audio suite had four failing ten/twenty-minute rotation scenarios
because Pilot v2 already capped product recordings at 4,800,000 frames. Product-level
fixtures now assert the accepted five-minute prefix, source continuity and VAD hash;
technical timeline rotation remains covered in the independent VAD module tests.
No product limit is increased or disabled to make these tests pass.

## Verification state

The PIN-free implementation and successor contracts are implemented. Software
acceptance requires the successful exact-head jobs and two clean signed build
receipts linked from [Draft PR 111](https://github.com/Monumentogram/DORA/pull/111).
This document alone is not a software PASS, physical Alpha acceptance, release
approval or permission to install on POCO.

The successor persistence inventory inherits only the two explicit over-five-minute
segmentation deferrals from Stage8 limited Alpha. Their historical source tests remain
unchanged. AlphaRecordingRuntimeTest and AlphaRecordingProcessDeathTest retain the
299/300-second cap and durable restart boundary; no long-test PASS is claimed.

Local verification: core/audio debug 241 and release 242 tests; Pilot JVM 86;
Recovery 414; VAD API 52 and adapter 5; formatting, Detekt, Pilot lint and both
instrumentation APK compilations passed. The broad historical Python discovery ran
1160 tests and reported 31 failures/errors, including obsolete stage topology/source
seals and three stale Pilot version fixtures (the latter fixed and rerun). This is
not represented as an all-repository PASS. Relevant Pilot/runner/version host suites
and the ADR-0021 preservation/source validator pass; device evidence is qualified
separately by its exact source, platform and scope.

The exact ADR baseline comparison ran 1,159 Python tests and reported 29
failures/errors. Comparing individual failing cases found only the already-fixed
Pilot version fixtures newly failing in the first implementation run; the other
historical failures also reproduce on the baseline. This comparison does not
convert those failures to PASS.

The first API28/API36 run passed no-system-PIN vault open and reboot/readback without
automatic capture, then exposed a foreground reacquisition failure. Two new JVM
regressions reproduced it before the fix: Android readiness can lag onResume, and
checking readiness must revoke issued handles without erasing the independent
resumed-Activity fact. A fresh explicit request from that exact live Activity can
acquire the current generation after revocation; an old handle remains revoked.
The device fixture now waits for resumed + interactive rather than synthesizing a
HOME/reorder lifecycle race. The failed runs remain historical non-acceptance evidence.

Subsequent fixture diagnostics reproduced a disabled deletion-button click: a reload
kept the old recording row visible while asynchronous work was still in progress.
The fixture now waits for the reload control to become enabled and explicitly checks
both deletion buttons are enabled. The complete secondary-user scenario then passed
locally on API28 with and without an OS PIN, including cancel/delete, screen-off
writer continuity and stale Resume rejection. These diagnostic runs do not replace
the exact-source full runner. The API36 component run at `b45ed3c` passed all 158
persistence tests in both credential states; its separate Pilot reboot/readback
failure remains non-acceptance evidence pending requalification. Readback failures
now publish only an allowlisted failure enum, never arbitrary provider text.

At `b45ed3c`, both API28 and API36 completed all 158 persistence tests without and
with an OS PIN. At `b952f1d`, the full API28 Pilot runner passed locally and in CI,
including reboot, both secondary-user credential states and verified cleanup.
These predecessor results remain scoped evidence rather than final-head acceptance.

The API36 `c5fc05f` receipt confirmed a startup configuration change destroyed the
original Activity during asynchronous open after reboot; the new Activity was
already resumed. Local authority correctly returned LOCKED. The restart fixture
now uses ActivityScenario to track and close replacements, initializes UI observation
before opening, and observes the same resumed/focused Activity and Configuration
across two frame/idle boundaries before its single open. It never retries a rejected
open or revives an old capability. A later replacement still fails the test. The
two-frame observation alone was insufficient in the next CI run. Exact-mask
diagnostics at `9884a9b` identified `CONFIG_ASSETS_PATHS` (`80000000`). Android 16
[SystemUI theme application](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/packages/SystemUI/src/com/android/systemui/theme/ThemeOverlayController.java)
can finish asynchronously after boot and enqueue resource configuration delivery.
The API36 runner therefore waits for non-null owner wallpaper colors, no deferred
theme evaluation, and completion of all observed started theme evaluations in the
current SystemUI process. It drains Android's broadcast loopers/application threads
and rechecks the same boot, owner, process and completed evaluation counts before
launching the restart scenario. Only a finite barrier result enters the receipt;
wallpaper values and raw SystemUI logs are not retained or published. A local real
reboot with this barrier passed single-attempt open/readback. This is evidence of
the diagnosed boot work completing, not a guarantee against future independent
configuration changes; exact-head CI remains the acceptance gate.
