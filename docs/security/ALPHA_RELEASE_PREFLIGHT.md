# Production Alpha release preflight

This is a software/release preparation successor of PR105, not security restoration or distribution acceptance. See the ten-control acceptance matrix and restoration runbook beside this document.

## Source and identity

Start: `d56a64f079d7bf9acf1d548545dfbb9c38858bde`. Branch: `codex/alpha-security-release-preflight`; draft PR base: `codex/stage8-isolated-alpha-operator`. Production: `com.monumentogram.dora`, code 5, `0.1.0-alpha.3`. Debug: `.debug`; isolated test: `.alpha.test`. The latter includes an operator Activity and uses a separate signer; production must contain neither operator classes nor instrumentation. Physical isolated evidence is not production evidence.

Read-only POCO package metadata on 2026-10-21 reported installed production code 4 / alpha.2. No production update, uninstall, launch, Recovery or historical-vault read was performed. This is an action-scope statement, not a claim that every historical ciphertext byte was measured unchanged.

## Runtime security review

AndroidDeviceSecurityPolicy checks real secure/unlocked state for foreground access. Release source sets return false unconditionally for both overrides; flags, marker combinations, package names and marker callbacks cannot grant authority. AndroidAuthProof creates disposable auth-only Keystore keys and executes a recent authenticated operation. AppLockSession consumes each result once and enforces foreground, deadline, generation and security epoch at capture and plaintext delivery.

AndroidRecordingAccessManager preserves the accepted current writer's credential/epoch authority for screen-off recording. New acquisition and Resume require foreground proof. ProductRecordingService uses process-local tokens, ownership checks and START_NOT_STICKY; process restart and reboot do not start the microphone. Notifications cannot supply arbitrary recording authority.

Existing-open storage never silently generates replacement keys/database. Vault wrapping keys remain distinct from auth-only keys and are not user-authentication-bound. SQLCipher, per-recording AEAD/AAD, tombstones, plaintext borrowing/wiping, backup exclusions and current recording limits are unchanged. This does not prove that restoring credentials on the historical POCO will preserve readable keys; that is an explicit physical gate.

## Manifest and privacy boundary

Inspect the merged candidate, not only source manifests. Expected exported surface: MainActivity launcher and the inherited AndroidX ProfileInstallReceiver protected by `android.permission.DUMP`. The profile receiver is not a recording/authentication entrypoint. Recording service, AppLock Activity, AndroidX initialization provider and Room service are private. No INTERNET, cross-user privileges, storage export permissions, boot receiver, shared UID, debuggable/testOnly flag or operator entry is admitted. Backup is disabled and cloud/device-transfer exclusions retained. TargetSdk36 platform cleartext default is false; no networking runtime is admitted.

No audio/share/export/clipboard or cloud path is introduced. Logs/receipts must be content-free, with no PIN, audio IDs, keys, tokens or private model/AAR bytes. Private artifacts remain outside Git and public Actions artifacts. Existing source is independently reviewed; absence of secret-shaped text alone is not a privacy proof.

## Repeatable owner-local build procedure

1. Use a clean checkout of the final candidate SHA on this successor branch. Set JDK17 and SDK36 environment; preserve strict dependency verification and all pinned metadata. `.pom` LF policy preserves the admitted SQLCipher digest on Windows.
2. Run `tools/build_alpha_security_candidate.py` with `--source-sha`, `--sdk`, `--private-vad-dir`, a new external `--output` directory and freshly observed `--installed-version-code`. Optional `--signing-dir` refers only to the existing owner-local custody directory. No secret value is a command argument. Without it, output remains unsigned.
3. The tool validates private input pins, source cleanliness/ancestry/branch, exports the non-file release resolution graph, performs two independent clean builds with build/configuration cache disabled and strict verification, optionally signs locally, then compares all ZIP payload entries and verifies actual DEX override denial, manifest, identities, certificate, model/native hashes and ZIP alignment. Failed invocations do not authorize use of any partial output.
4. Validate ELF segments separately with `verify_apk_native_alignment.py` and the admitted native inventory including the two private VAD libraries. Generate CycloneDX from the actual exported graph against `docs/evidence/vad-8.4-runtime/ci-release-graph.json`, locked coordinates and admitted project set; include SQLCipher native/shaded constituents plus the pinned private AAR/model and their reviewed constituent SBOM references.
5. Bind build/SBOM/manifest/DEX receipts to the same final source SHA and candidate hashes. The artifact verifier requires `--build-receipt` and, for comparison, `--compare-build-receipt`; each must match clean source and actual APK digest. These are local builder provenance records, not cryptographic remote attestations. CI writes its source/digest receipt immediately after its own build in the same exact-SHA job. Run inherited 4/4 and isolated 3/3 exact-SHA jobs; do not reuse old-SHA CI. The production artifact check is an additional mandatory step of `alpha-software`. Independently review the final diff and public artifacts before publication.

Accepted signing certificate SHA256: `e77c9af533e603c0fa7c5c2000eb74b9514d001d98c3e5845cc2bae905be76bf`; alias `dora-internal-alpha`. Owner-local key/password ACL must remain owner/system-only. The 2026-09-30 encrypted backup restoration receipt is historical accepted evidence; a fresh restoration is not claimed unless actually performed. Signing key availability does not imply current offline-backup custody confirmation.

## Upgrade and final decision

`NOT_READY / PRODUCTION_UPGRADE_SAFETY_UNPROVEN`: equal package/signer plus increasing versionCode permits Android replacement but does not prove safe startup reconciliation, schema/key continuity, historical LONG-01/LONG-02 readability or rollback. Downgrade is not a safe rollback plan. UID/private storage retention is expected Android upgrade behavior, not measured in this task. Do not test safety by installing.

`DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE = OPEN`.
`PRODUCTION_INSTALLATION = NOT_RUN`.
`SIGNED_ALPHA_RELEASE_READY = NOT_GRANTED`.
Distribution is BLOCKED pending separate owner release acceptance and physical security gates. Stage8.6 stays NOT_READY, battery efficiency and PERF-REC-001 deferred/nonblocking, historical outcomes retained. No Stage9/Cloud/Sheet work.

The final external RESULT and draft PR record executed local/CI/build/review outcomes; this source document does not pre-certify future checks. Owner next action: review the runbook and separately decide `OWNER_DECISION_REQUIRED / USER0_SECURITY_RESTORATION`.
