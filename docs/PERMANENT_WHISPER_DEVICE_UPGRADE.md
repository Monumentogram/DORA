# Permanent Whisper — safe Owner Preview upgrade

Date: 2026-10-28. **APK prepared; installation BLOCKED pending unlocked user 10,
pre-update data checks and separate owner confirmation.**

## Exact source and artifact

- Installed source: `98a975f3aff268db605573e1cd666dbc237f3123`.
- Device branch: `stage/9-permanent-whisper-device`.
- Final application source: `acfd8a7743d8a7f414eb8f1f5bdce77fcd492289`.
- Backport source: [PR119](https://github.com/Monumentogram/DORA/pull/119),
  `4edf687352968c40017242608e4a85f64d43a467`; four production files only.
- Package: `com.monumentogram.dora.alpha.preview`, Android user **10** only.
- Version unchanged: code **4**, name **0.1.0-owner-preview.4**. Same-version `-r`
  replacement needs no downgrade flag. Do not use version text as proof of update.
- Certificate SHA-256:
  `a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.
- Candidate SHA-256:
  `284db30f699c53500ea15d8cb2db7a15f45dd581ad20fece20114001d89e4095`.
- Original APK SHA-256:
  `0370f1999fcdcdf04d116c58a07ddea212701fc1afc463b0e601daee3ea850a1`.
- Both APKs: 83,735,909 bytes. Private local artifact directory:
  `%LOCALAPPDATA%/DORA/permanent-whisper-20261028/`.
- Candidate: `dora-preview-permanent-whisper-vc4.apk`.
  Original retained: `installed-preview-v4.apk`.

The full PR working base includes Cloud additions and a later transcript codec.
Installing it over this older Preview would exceed a model-only change. Therefore
the device branch applies the exact PR model patch to the proven installed source.
Capture, encrypted persistence, Android Keystore, transcript format/history,
manifest, version and Gradle settings are byte-identical in source to the installed
baseline. Cloud source/infrastructure is not changed or added by the device patch.

The owner's 2026-10-28 request and PR119 ADR-0027 supersede the old model-expiry
field for this owner-local hotfix too. Historic custody/governance files and the
old host build helper's deadline are not rewritten; they are not packaged runtime.
The candidate is built by Gradle's existing verified-private-runtime path and signed
outside Gradle with the original key. No expiry bypass is injected into the APK.

## Completed evidence

OwnerPreview JVM tests: 108 pass. Audio release JVM tests: 255 pass. The two new
controlled-clock regression tests fail against the old installed source and pass
after the backport. Spotless, Detekt, OwnerPreview Lint and assembly pass. Final
assembly was repeated after committing the source; candidate ZIP entries match
the unsigned committed build before signing.

Original and candidate signatures verify with the same certificate. Binary
manifest and all ten native libraries are identical. JNI exports, AArch64
dependencies and 16-KiB ELF/ZIP alignment pass. Runtime assets are identical;
Whisper weights are absent. Changed ZIP entries are only `classes3.dex`, two
DEX-bound ART profiles and three signature metadata entries. Native runtime
execution on the updated phone remains a separate, unperformed check.

## Next session: preflight, then confirmation

1. List devices and explicitly select the already verified POCO serial. Re-read
   Android version/ABI, user list/current user and package state. Never select the
   emulator or rely on ADB's default device. User 0 must remain uninstalled for
   this package. If another user now has it installed, stop: APK replacement is
   package-global and can affect that user even with `--user 10`.
2. Ask owner to unlock DORA-Alpha-Test (user 10); never request/store their PIN.
   Owner has already confirmed Preview as the correct package. Launch its resolved
   activity with `--user 10` only, without force-stop while recording.
3. Record content-free baseline: record/transcript counts, accessibility of each
   original, saved manual edits, active version and version counts. Keep any
   necessary screenshots/content on the owner's computer only, outside Git.
   The installed non-debuggable app rejects `run-as`; do not weaken it or install
   a diagnostic replacement to inspect the vault.
4. Verify the installed model through existing app behavior. App-private model
   size/hash could not be inspected while locked. The separate host model has the
   exact 264,464,607 bytes and SHA-256
   `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`;
   this does not prove the phone's copy exists. Do not import/replace/remove it
   merely to satisfy preflight.
5. Re-pull installed APK if it changed, verify its certificate and version against
   the candidate, re-hash candidate and confirm no active recording/ASR or model
   import. Keep original APK. Its copy is **not a backup of encrypted user data**.
6. Present the exact candidate SHA/package/user, preflight findings and restoration
   limits to owner, then obtain separate permission to install. Existing permission
   to prepare does not authorize installation.
7. Only after approval, use `adb -s <verified-serial> install --user 10 -r <candidate>`.
   Never uninstall, clear data, add `-d`, replace the signer, change keys, remove a
   user, or install a different application ID. If Android rejects the update,
   stop and diagnose the rejection; no destructive fallback.

## Post-update acceptance

Re-pull installed package and compare its APK hash with the candidate. Verify user
10 launch and user 0 remains uninstalled. Recheck all baseline records/originals,
transcripts, edits, active versions and history; record categorical results only.

Use fresh known synthetic RU/EN samples, with no user recording sent anywhere.
Exercise real local Whisper separately in RU and EN and record actual text/latency
privately. Mark successful execution separately from recognition-quality assessment;
this smoke is not WER/benchmark acceptance. Verify offline operation without altering
AWS or user 0 networking (prefer an owner-selected offline state or a scoped
approved method), and user cancellation without publishing a cancelled result.
Test stored originals and version preservation again after normal app restart.
Do not repeat accepted Recovery campaigns or change POCO's clock.

Only after those checks may this report gain
`PASS / OWNER_DEVICE_PERMANENT_WHISPER_READY`.

## Restoration without data loss

Before 2026-11-03 00:00 Moscow (2026-11-02T21:00:00Z), the original APK has the same
package, signer, versionCode and persistence schema and is retained for a separately
approved `-r --user 10` replacement. This is a prepared recovery path, not a tested
rollback. Never uninstall or clear data. If replacement fails, preserve installation
and data and investigate.

At or after the old cutoff **do not launch or restore the old APK**: it can delete
the model. Use a forward repair built from the unchanged storage baseline with the
permanent-model fix and the same signer. Preserve user data and model while blocked.
If the issue is encrypted data/key availability, APK replacement is not proof of
repair; stop without reset. Reverting an APK never restores already lost data.
