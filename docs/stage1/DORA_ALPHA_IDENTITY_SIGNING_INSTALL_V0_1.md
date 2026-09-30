# 7.1 — Alpha identity, signing and installation

Status: IN_PROGRESS; installation and exact-source CI evidence must pass before closure.
Scope: OWNER_ONLY_CLOSED_INTERNAL_ALPHA. Decision: [ADR-0015](../adr/ADR-0015-internal-alpha-identity-signing.md).
Machine-readable [contract](../contracts/DORA_ALPHA_IDENTITY_SIGNING_INSTALL_V0_1.json).

## Frozen identity

`com.monumentogram.dora.bootstrap` -> **`com.monumentogram.dora`**, including
namespace and app Kotlin/test packages. Baseline version **2 / 0.1.0-alpha.1**.
Debug verification is isolated at `com.monumentogram.dora.debug`. No flavors.
Code 3 is a disposable upgrade probe; next product Alpha uses code >=4 with an
incremented alpha.N name. No public version stability or store claim.

Baseline fetch verified remote `4e7742d88377d3d915be904618fc220d479ab25d` and a
clean source tree. Its only difference from the CI-validated implementation
`92f00f7dd4a18a3d4b2fdd159fdaef86fa3f8699` is five admission documents.

## Signing and owner custody

SIGNING_OWNER = PROJECT_OWNER. Dedicated RSA-3072 / SHA256withRSA PKCS12,
alias `dora-internal-alpha`, valid 2026-09-30 through 2036-09-27 UTC.
Certificate SHA-256:
`e77c9af533e603c0fa7c5c2000eb74b9514d001d98c3e5845cc2bae905be76bf`.

Keystore and password reside in an owner/system-only private local application
data directory outside Git. Authorized owner environments alone may sign.
Owner maintains an encrypted offline backup and tests restoration; backup
completion is not asserted by this task. Lost key means existing installs
cannot receive same-key updates; replacement/rotation needs a separate plan.
Future production/store signing remains separate and uncreated.

## Repeatable build and install

Prerequisites: Python 3.11+, JDK 17, pinned Gradle wrapper/dependencies, SDK and
Build Tools 36.0.0. Set ANDROID_HOME to the SDK. Local signing directory contains
`alpha.p12`, `password.txt` (one ASCII password for store/key), and
`certificate.sha256` (the public certificate pin). Provision only in private
owner storage; never copy these files into a checkout, logs or CI.

From repository root, with placeholders resolved locally:

```text
python tools/build_internal_alpha.py --signing-dir <private-signing-directory> --sdk <android-sdk>
```

Use `--offline` once all locked dependencies are cached. Output:
`android/app/build/outputs/apk/internal/dora-0.1.0-alpha.1-vc2.apk` and a public
JSON receipt. The command builds unsigned release, signs outside Gradle, checks
the contract-pinned certificate, exact package/version and 16 KiB zip alignment,
then publishes the local artifact. Missing material or wrong certificate exits
nonzero before publishing. A pre-existing output from an earlier successful run
is not evidence of the failed invocation; always require command success and
verify the receipt/hash. Do not install the unsigned release intermediate.

```text
adb devices -l
adb -s <authorized-owner-device> shell pm list packages com.monumentogram.dora
adb -s <authorized-owner-device> install <signed-alpha-apk>
adb -s <authorized-owner-device> shell am start -W -n com.monumentogram.dora/.MainActivity
adb -s <authorized-owner-device> shell dumpsys package com.monumentogram.dora
python tools/build_internal_alpha.py --signing-dir <private-signing-directory> --sdk <android-sdk> --upgrade-test
adb -s <authorized-owner-device> install -r <signed-upgrade-test-apk>
```

Stop if an existing exact package has unknown provenance; never uninstall an
unrelated app to clear a collision. Bootstrap-to-Alpha is a separate clean
installation, no PoC state migration promise. For the task-created empty Alpha
shell, test uninstall/reinstall of that exact package only. After disposable
code-3 proof, remove that task-created package and reinstall code 2; this is
explicit clean test cleanup, not a downgrade update or rollback guarantee.

Ordinary CI uses debug/test builds without the signing command or any secret.
Only owner-local verified signed output is the internal Alpha artifact. No Play,
RuStore, Firebase distribution, external user or production channel is involved.

## Evidence and boundaries

Build/device/CI evidence pending. POCO M5 model 22071219CG, Android14/API34,
arm64-v8a is the installation reference; no expanded device support claim.
Baseline unit/release assembly and Stage00 validation passed after a local Git
ownership correction confined to the worktree process. Five signing-boundary
negative tests first failed, then passed; actual signature/install/upgrade tests
are still required.

7.2 NOT_STARTED; recording/storage/ASR product flows NOT_IMPLEMENTED; AWS
NOT_CALLED; main NOT_CHANGED. PR #86 OPEN / DRAFT / UNMERGED / untouched.
Recovery clean replacement remains required before Stage 8 recording/storage
acceptance, not before 7.1. No Recovery campaign or code change.
