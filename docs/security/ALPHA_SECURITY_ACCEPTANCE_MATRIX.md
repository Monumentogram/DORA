# Alpha security acceptance matrix

Successor of PR105, baseline `d56a64f079d7bf9acf1d548545dfbb9c38858bde`.
Contract: `DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE.json` remains unchanged and OPEN.
PASS below must always identify its evidence domain. The final physical contract column concerns historical user 0; software tests cannot silently close it.

| Control | Expected result and verification | Device/user; emulator; user 10 | User 0 needed | Existing evidence / remaining action | Final physical status |
| --- | --- | --- | --- | --- | --- |
| android_credential_restored | Owner sets secure credential in Settings; read actual secure state | POCO user 0; emulator supports synthetic check; user 10 establishes only its own credential | Yes | ADR-DEV-002 records exception, no restoration proof; owner Phase 2 | NOT_RUN |
| no_device_lock_marker_removed | Exact private debug marker absent/disabled; no data clear | POCO user 0; synthetic emulator possible; user 10 cannot prove user 0 marker state | Yes | Release source always denies; inspect/remove only after separate permission | NOT_RUN |
| no_prompt_marker_removed | Exact prompt bypass marker absent/disabled | Same boundary as preceding row | Yes | Release implementation always denies; owner Phase 2 | NOT_RUN |
| dora_restarted | Approved installed DORA starts locked after marker removal | POCO user 0; emulator/process-death supplementary; user 10 only isolated | Yes | Isolated restart smoke inherited; restart user 0 requires approval | NOT_RUN |
| is_device_secure_true | KeyguardManager.isDeviceSecure true for exact user | POCO user 0; emulator and secondary support independent checks | Yes | Secure-emulator tests; no current user 0 credential probe performed | NOT_RUN |
| normal_app_lock_prompt_verified | Real credential prompt; wrong/cancel does not grant reader | POCO user 0; API28/36 synthetic system credentials; isolated user 10 separate | Yes | AndroidAppLock/AndroidAuthProof tests; owner Phase 3 real prompt | NOT_RUN |
| background_and_device_lock_revoke | Old audio reader delivers zero PCM after background/device lock; foreground does not restore handle | POCO user 0; API28/36 tests; user 10 isolated runtime | Yes | Session generation, protected reader tests and inherited isolated smoke; owner Phase 3 | NOT_RUN |
| resume_requires_fresh_proof | Revoked recording epoch cannot Resume until fresh foreground proof; active writer follows accepted policy | POCO user 0; API28/36; user 10 isolated | Yes | RecordingAuthority/ResumeGrant negatives and inherited smoke; owner Phase 3 | NOT_RUN |
| release_overrides_impossible | Both actual release methods deny all packages/flags/marker combinations, no marker read/grant; no exported activation route | Host production APK + API28/36 release-derived isolated artifact; applicable to user 10 | No for software; exact deployed identity still required | Source stubs + release JVM matrix; successor adds release-derived execution and candidate DEX checks; see current RESULT for executed evidence | NOT_RUN (physical restoration closure) |
| same_encrypted_vault_readable | Approved original encrypted vault opens existing keys/database and completes authenticated baseline readback | Historical POCO user 0 only; new emulator/user 10 vaults cannot substitute | Yes | No historical vault access in this task; separate owner Phase 4 protocol required | NOT_RUN |

## Negative authorization coverage

| Case | Meaningful assertion / test family | Evidence boundary |
| --- | --- | --- |
| No secure lock | AndroidProductAudioRuntimeTest.noCredentialCannotCreateStorage; no vault creation | API28/API36 synthetic credential-state runner |
| Wrong authorization / cancel | AppLockSession failed proof/cancel cannot capture or deliver; real prompt cancellation in inherited user 10 smoke | Wrong physical user 0 credential NOT_RUN |
| App close / process death | New AppLockSession has no authority; process-death runners require authenticated reopen and no automatic microphone | JVM + emulator |
| Background/foreground | Old session reader denies and callback count stays zero even after fresh grant | AndroidProductAudioRuntimeTest extended scenario |
| Screen lock | AppLockSession security epoch and AndroidAppLock screen-off receiver revoke; old proof fails | JVM/source; user 0 physical NOT_RUN |
| Stale session / epoch / proof revocation | AppLockSessionTest, RecordingAuthorityTest and ResumeRequestsTest | JVM; encrypted runtime tests |
| Recovery return / revoked Resume | LogicalRecoveryRuntimeTest lease fencing; AlphaRecordingRuntimeTest authenticated capped continuation | API28/API36 |
| Deleted recording / stale deletion proof | Tombstone fences reference/reader after reopen; stale confirmation cannot authorize deletion | AndroidProductAudioRuntimeTest + journal/runtime tests |
| Missing microphone permission | NativeCaptureLifetimeTest verifies denied permission before native creation/authority and platform revocation during creation: zero frames, no live capture thread | Real capture-adapter JVM tests with platform boundary fixtures; UI presentation checked separately |

The final RESULT links exact-SHA executed runs and distinguishes any NOT_RUN cases. No row claims that a UI label alone proves audio denial.

## Inherited physical evidence

Owner reports `PASS / ISOLATED_ALPHA_PHYSICAL_SMOKE_VERIFIED`, POCO M5 API34, Secondary User ID/serial 10, package `com.monumentogram.dora.alpha.test`, APK SHA256 `4a0e98a9a7e3cd4ee095d3ea236234d0c4ca27504739215c669614e5a236b8b5`: 4,800,000 frames, 61 authenticated units, screen-off, Pause/Resume, cap finalization, restart/readback/deletion, microphone OFF and FGS absent. This is inherited evidence, not repeated in this task, and is not transferable to the production APK or historical user 0 vault.
