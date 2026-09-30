# 7.3 — Internal Alpha release v0.1

Status: **PASS / INTERNAL_ALPHA_APK_BUILD_CI_DEVICE_READY** for the measured
owner-only release below. The evidence-only publication commit requires its own
exact-SHA CI and final Sheet/PR readback in the post-publication receipt.

## Scope and authority

Owner-authorized release verification on `stage/7-alpha-foundation`, starting at
`d48e41a65c377dc17d9ac3a0384d687061bc33c6`. Preserve ADR-0015 and the historical
7.1 release (alpha.1 / code 2). This next product release is
`com.monumentogram.dora`, `0.1.0-alpha.2`, code **4**. Code 3 remains a historical
disposable probe and cannot be built by the current product builder.
Debug retains its separate application ID and suffix. The frozen certificate is
`e77c9af533e603c0fa7c5c2000eb74b9514d001d98c3e5845cc2bae905be76bf`.

## Design and implementation plan

Use writing-plans, executing-plans and TDD inline under the explicit autonomous
owner request. The required two commits supersede separate design/plan commits.
Reuse the existing isolated worktree. No dependency upgrade or product runtime.

One properties file supplies the version to Gradle and the owner-local builder.
The builder checks clean Git source before and after compilation, then verifies
the signed APK before publishing it and a path-free receipt. Signing material
stays outside every checkout and never enters Gradle or CI.

An existing-toolchain Gradle init script exports the actual app release runtime
components, artifact hashes and relationships. A standard-library Python tool
reconciles those components against the app's release locks, rejects unresolved
and dynamic versions, and emits deterministic CycloneDX 1.6 JSON. The approved
graph is versioned separately from the final source-bound SBOM. No new plugin.
Release evidence validation checks relationships across source, SBOM, APK, CI,
backup restore, device identity and the explicitly limited migration disposition.

- [x] Tests first: builder rejects reused identity/dirty or changed source;
  SBOM rejects missing/extra components, changed relationships, dynamic versions,
  unresolved dependencies and wrong source/version; release rejects stale CI,
  wrong signer/SBOM/device/artifact/permissions and premature runtime claims.
- [x] Implement `android/alpha-release.properties`, builder checks,
  `tools/alpha_release_graph.init.gradle`, `tools/alpha_release_sbom.py` and
  `tools/validate_alpha_release.py`; add mandatory CI without removing checks.
- [x] Resolve and lock release configurations; review the graph and establish
  `docs/contracts/DORA_ALPHA_RELEASE_RUNTIME_GRAPH_V0_1.json`; no library upgrades.
- [x] Run host/Gradle checks and independent review; commit implementation,
  push and require exact-SHA android-bootstrap and search-smoke success.
- [x] Build/sign twice from clean implementation SHA; freeze SBOM; verify
  manifest, signer, native ELF/ZIP alignment and secret boundaries.
- [x] Verify POCO M5 baseline provenance; upgrade 2 to 4, preserve UID and
  firstInstallTime, cold-launch/read back and compare pulled APK bytes. Retain v4.
- [ ] Reconcile Sheet with exact readback; publish docs/evidence-only closure;
  verify its own exact-SHA CI, final Sheet HEAD and PR #87 metadata.

Review focus: unsigned intermediate accidentally published; source changing
during build; SBOM graph plausible but not matching resolution; historical code
3 mistaken for product; self-referential final evidence SHA. A commit cannot
contain its own SHA: final evidence-head CI/Sheet reconciliation are retained in
the post-publication local receipt and final RESULT, never fabricated in Git.

Independent review found a runtime file-dependency omission. A real synthetic
Gradle fixture reproduced it for direct and local-module JAR dependencies;
the exporter now rejects both (RED then GREEN). Ordinary locked resolution
adds lock constraints to the root graph, so the approved inventory comes from
normal locked resolution after lock generation; artifact hashes and versions
were unchanged. Local detekt used relative arguments with identical input-set
assertions to avoid the Windows command-length limit; CI uses the normal task.
Deferred minor: tests cover builder helper gates, but a mocked full publication
orchestration test is not included. Actual owner builds remain mandatory.

## Signing custody and acceptance

The separate owner-local remediation must have verified an encrypted offline
backup, restoration, frozen certificate, private-key signing proof and temporary
copy cleanup before implementation. Public evidence contains outcomes and the
public certificate only. Password and backup location are never published.
Same-host signed APK repeatability and POCO acceptance are required; historical
rebuild byte identity is optional and must not be inferred from a signing proof.

## Non-execution

7.3C and Stage 8 NOT_STARTED. Recording, VAD, product audio storage, transcript
persistence, merge, auth/backend/object-storage/AWS-adapter runtime NOT_IMPLEMENTED.
Recovery integration and audio upload NOT_RUN; real product audio NOT_USED;
AWS NOT_CALLED, spend 0 BY THIS TASK; FIRST_REAL_PRODUCT_AUDIO NOT_READY.
main unchanged; PR #86 untouched; PR #87 OPEN / DRAFT / UNMERGED.
Stage 7 and Group C remain IN PROGRESS. Next separate task after PASS is 7.3C.

## Measured release and provenance

Implementation: `351874fff41774f10298e8a186bdc78bf6bb720f` on the admitted branch,
parent `d48e41a65c377dc17d9ac3a0384d687061bc33c6`. Both builds ran `clean` on this
unchanged clean source, using the same owner key and toolchain. The signed APK
is owner-local and is not committed or uploaded by CI.

| Evidence | Observed result |
|---|---|
| Artifact | `dora-0.1.0-alpha.2-vc4.apk`, 22,760,139 bytes |
| First and repeat APK SHA-256 | `ad13ddbd2b01e4e61112ecfb889408576dd04748b9e65627abd6700d1440fa57` |
| Repeatability | BYTE_IDENTICAL; same source, toolchain and certificate |
| Runtime graph | 110 nodes: app, core:common, core:model, 107 external coordinates; no new dependency coordinates or versions |
| SBOM | CycloneDX JSON 1.6, deterministic; actual locked release runtime relationships and artifact hashes; official schema validation PASS |
| SBOM SHA-256 | `230fbb099d99fee5a2dd1f698cddeeac722de83ea0e0be0cb51583f26da165ba` |
| Owner toolchain | Temurin 17.0.20.1+1; Gradle 9.5.0; AGP 9.3.1; Kotlin 2.2.10; SDK/build-tools 36/36.0.0; minSdk 28 |
| CI | [36720820501](https://github.com/Monumentogram/DORA/actions/runs/36720820501), exact implementation SHA; android-bootstrap + search-smoke SUCCESS |

The completed CI job logs prove every required command and success result. GitHub's
step API retained 14 stale pending/in-progress entries after the successful job
completed. The CI receipt preserves those original values and explicitly derives
gate outcomes from completed command blocks and success markers, with log hashes.
No mandatory check was skipped or replaced by a local test.

## Custody and physical acceptance

**NEW_ENCRYPTED_BACKUP_CREATED_AND_RESTORE_VERIFIED.** GnuPG OpenPGP AES-256,
iterated salted S2K SHA-512 and verified MDC; wrong password rejected. A restore
from the persisted offline copy reproduced the frozen certificate and performed
RSA-3072/SHA-256 signing and verification with a negative control. Temporary
restored material was removed and absence verified; active key unchanged.
Owner confirmed the encrypted file is offline and the recovery password is in
a separate password manager. Historical restored-key APK rebuild was NOT_RUN.

Physical POCO M5 / 22071219CG, Android 14 / API 34 / arm64-v8a, firmware
V816.0.3.0.ULURUXM. Installed baseline alpha.1 / code 2 matched the frozen signer
and historical APK SHA `3cb591abc8e33a832bdf018b7cff545a8b3c39641ab74b44e8d4168cfa045d3c`.
Normal same-package `install -r` updated 2 to 4 without uninstall. UID **10306**
and firstInstallTime **2026-09-30 09:23:06** were unchanged. Force-stop proved
the old process absent; MainActivity cold-launched successfully, was foreground,
and displayed **DORA Alpha 0.1.0-alpha.2 (4)**. The pulled installed APK matched
the release SHA above. **Code 4 remains installed.** No serial is published.

## Signed APK audit and limits

The signed artifact has one expected signer, no INTERNET or RECORD_AUDIO,
no debuggable/testOnly flag, and allowBackup=false. Its sole requested permission
is the signature-level DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. MainActivity
is the launcher export; the existing AndroidX ProfileInstallReceiver is protected
by android.permission.DUMP. AndroidX Startup provider is not exported. No
unexpected export or debug-only component was found.

`libandroidx.graphics.path.so` is the sole admitted native basename, present for
arm64-v8a, armeabi-v7a, x86 and x86_64. All four ELF entries and signed APK ZIP
alignment pass 16-KiB checks. This is packaging evidence, not a new 16-KiB-device
runtime qualification. Source and APK pattern/extension scans found no private
key, signing material, Cloud token or owner signing path; scan limitations are
explicit in the build receipt.

**PRODUCT_DATA_MIGRATION = N/A / NO_PRODUCT_PERSISTENCE_SCHEMA_YET.** Product
main sources and release graph contain no product database schema. Package
continuity is proved; no database migration PASS is claimed.

Local checks: 203 Python tests including 21 Recovery governance tests, 401 JVM
tests, VPN host/loopback, formatting, detekt, instrumentation compilation, lint,
debug/release assembly, compiled boundaries and dependency inventories PASS.
CI additionally executed 7 emulator search-smoke tests. Independent review's
one Important inventory issue was fixed with a failing-then-passing integration
test. Deferred minor: no mocked full builder publication-orchestration test.

## Evidence files

- [Signing backup](../evidence/alpha-7.3-signing-backup-v0.1.json)
- [Signed build and physical device](../evidence/alpha-7.3-build-device-v0.1.json)
- [Exact implementation CI](../evidence/alpha-7.3-ci-v0.1.json)
- [CycloneDX SBOM](../evidence/alpha-7.3-sbom-v0.1.cdx.json)
- [Local verification and review](../evidence/alpha-7.3-local-v0.1.json)
- [Sheet readback](../evidence/alpha-7.3-sheet-v0.1.json)
- [Closure and post-commit publication gate](../evidence/alpha-7.3-closure-v0.1.json)
