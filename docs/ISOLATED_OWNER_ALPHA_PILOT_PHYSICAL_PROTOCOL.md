# Isolated owner Alpha pilot — prospective physical protocol

Status: **NOT_RUN / OWNER_INSTALLATION_DECISION_REQUIRED**.
This software-only task grants no phone access. Do not execute this document until
the owner approves an exact APK, device/user tuple and bounded session. The phone
and historical DORA remain unchanged during software preparation.

## 1. Admission and custody

Use only the owner-local signed `com.monumentogram.dora.alpha.pilot` APK whose final
source SHA, APK SHA256, certificate, model/AAR pins, SBOM and two clean-build receipts
were accepted. Version 1 / `0.1.0-owner-alpha.1` is independent of the other DORA
packages. The admitted pilot certificate SHA256 is
`a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.
Do not install CI/debug/test-signed outputs or the instrumentation APK on POCO.
No signing secrets, PIN, private identifiers or audio in public reports.

Before future installation, separately approve fresh read-only device/package/user
metadata queries. Reconfirm physical device identity, API/firmware, exact full
Secondary **userId 10 AND user serial 10**, current user and complete user topology.
Historical observations are not current evidence. STOP if deleted/recreated,
restricted, managed, guest/demo/profile, locked, ambiguous, or either number differs.
Do not create a replacement user, delete user10 or touch user0 to make checks pass.
Record package code paths/signers/versions for production/debug/diagnostic DORA by
metadata only, plus absence or exact prior identity of the pilot package.

Installation permission must explicitly cover this independent package for user10
only. Do not replace `com.monumentogram.dora`, `.debug` or `.alpha.test`, use shared
UID, downgrade, clear app data, change their enabled state or open their processes.
Fresh installation is the default; a pre-existing pilot requires a separate update
decision and data-preservation plan. Distinct package code prevents an update of the
three existing package identities; it does not prove historical vault/key continuity.

The pilot itself uses ordinary product security, without an operator admission form.
Host installation/user checks are therefore mandatory, not an in-app attestation of
full-secondary topology. Never invoke cross-user APIs or access another app's Context.

## 2. Security prerequisites

User10 needs its own secure system credential. Inspect only after explicit physical
diagnostic permission; credential setup/change, if required, needs separate approval
and is performed personally by the owner in Settings. Never pass PIN through chat,
ADB or logs. User0 credential/debug markers are outside this protocol.

Verify actual package, signer, version, UID, private data namespace and user10-only
installation. Recheck the three historical package identities against their baseline.
Check software-ready report, exact-SHA mandatory CI and independent review. STOP on
any identity mismatch, gate failure, unknown previous data or attempted package
replacement. Keep a private receipt, not a copy of historical storage.

## 3. Bounded ordinary user workflow

1. Switch only to the admitted user10 under the separate owner permission. Confirm
   user ID/serial again. Launch only DORA Owner Alpha from its normal launcher.
2. Confirm first-run identity/version and idle state; microphone/FGS must be off.
   Open the recording screen. Attempt authentication cancellation: no vault access
   or capture may follow. Complete the real Android/App Lock prompt deliberately.
3. Deny microphone permission once and verify recording does not start. Grant only
   after the owner chooses the recording scenario. No test bypass or hidden command.
4. Use a short synthetic spoken/test signal without bystanders/private meeting data.
   Personally warn all participants and check the acknowledgement. Verify available
   storage. Select Start; verify notification and live recording state.
5. Under the approved bounded scenario, turn the screen off, return, pause, lock and
   reauthorize. Resume must use valid foreground authority; a pre-lock stale grant
   must not resume. No automatic restart after lock/process death/relaunch.
6. Verify cumulative audio-time accounting across Pause/Resume. In the separately
   approved cap scenario, reach at most 4,800,000 frames (five minutes); no hour-long
   scenario. Wait for confirmed stop/finalization; uncertain state is not SAVED.
7. Open saved recordings, unlock, then explicitly select “Проверить сохранённые
   записи”. Discovery uses ordinary authenticated Recovery on **pilot-local data**.
   Note local RecordingId, completion and durable frame count privately. Do not
   confuse a catalog count or partial prefix with complete authenticated audio.
8. For a finalized record select “Проверить аудио”, complete real authentication and
   require exact-frame full-readback success. This verifies/decrypts transient PCM;
   it does not play or export sound. For partial/unavailable sources report the exact
   state; do not invent complete verification, repair historical data or add an engine.
9. Background/lock clears displayed private entries/results. Reauthorization does
   not resurrect an old success as permission. Reload explicitly before another action.
10. Test deletion cancellation, then reload and select “Удалить аудио…”. Approve the
    existing exact-record confirmation only for the task-created pilot source. Require
    “Аудио удалено. Удаление проверено.” and check state after an authenticated reopen.
    The runtime checks tombstone, absent audio artifacts and key references. Failure
    remains visible; no physical-overwrite promise, unrelated content deletion or export.
11. Repeat critical controls at compact display/200% font. Scroll all content; Start,
    Pause/Resume, Stop, saved state, verify and delete must remain reachable. Do not
    count emulator accessibility evidence as POCO visual acceptance.
12. End with microphone/FGS off and DORA locked. Retain only agreed content-free
    receipts. Removing the pilot package or its data requires explicit cleanup scope.

## 4. Failure and STOP

STOP on wrong user/serial/package/signer, changed topology, missing system credential,
auth bypass, stale-session access, unexpected microphone start, cap excess, storage
failure, unsupported state, readback mismatch, incomplete deletion, inaccessible
critical controls or any action affecting historical packages/users/keys.

Do not repair by uninstall/reinstall, downgrading, resetting credentials, deleting
Keystore material, copying a vault, broad file cleanup or launching historical DORA.
Preserve the failing pilot state and minimal private evidence; request a new narrowly
scoped decision. Re-running a failed physical check needs its own recorded disposition.
Software-signing backup/restore verification is separate custody evidence; loss of the
pilot signer prevents ordinary signed updates and must not trigger destructive reinstall.

## 5. Evidence and gate separation

Record exact source/APK/certificate/dependency/model identity; current topology and
user10/serial10 admission; actual UI/security outcomes, frames/completion, authenticated
verification and verified deletion; failure attempts; package-identity before/after;
microphone final state and physical limitations. Keep source IDs/device identifiers
private. No raw audio, PIN, keys, screenshots containing private catalog or private paths
in Git/Actions. Report only consented aggregate evidence publicly.

All physical checks are currently NOT_RUN. The next owner decision is permission for
fresh metadata preflight and exact independent APK installation plus a bounded user10
scenario. Credential changes, microphone recording, deletion and cleanup need named
scope. Installation is not distribution approval.

Unchanged: security restoration OPEN; Stage8.6 NOT_READY; LONG02 root cause NOT_PROVEN;
production upgrade safety UNPROVEN. This is an owner-only isolated pilot, not production
equivalence and not proof of historical user0 preservation.
