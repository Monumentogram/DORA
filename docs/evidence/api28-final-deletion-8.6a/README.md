# Stage 8.6A final-deletion close-cycle investigation

Baseline: `60a38e13739c04f18cffc21783329b633cf68a8f`, PR #99, branch
`stage/8.6-poco-recording-acceptance`. This is persistence remediation, not
physical POCO acceptance. Battery deferral and every historical receipt remain
unchanged. The final exact-SHA CI result must be read separately from Actions;
this pre-publication record does not predeclare its success.

## Execution and last proven historical progress

CI run [37293125932](https://github.com/Monumentogram/DORA/actions/runs/37293125932)
ended the API28 credential suite at 1800.001 seconds. Nine methods completed;
the last started method was
`EncryptedAudioVaultDeletionFailureTest#actualFinalDeletionTransactionCompletionBeforeAndAfterRemainTruthful`.
Emulator and instrumentation were alive. The receipt contains no attributed
Java stack, so the exact internal wait of that historical execution is unknown.
API36 passed its full inventory and Recovery 22/22 in that same run.

The prior successful [run 37269152815](https://github.com/Monumentogram/DORA/actions/runs/37269152815)
on `95834de7472cfb64f47e1b111bd1a2f024f79e32` finished the API28 credential suite in
644.562 seconds; the affected method took approximately 7.361 seconds. The Android
tree through the task baseline was unchanged. This alone does not establish
causality or excuse the failed run.

Locally the unchanged exact method and complete three-method class initially
passed (9.766 and 29.547 instrumentation seconds). Local API28 uses the installed
default x86_64 image, whereas CI uses google_apis x86_64. Local reproduction proves
the real Android/SQLCipher cycle, not identical scheduling or hardware to CI.

## Proven mechanism

The test runs both BEFORE and AFTER actual SQLCipher `endTransaction` faults.
It uses real Android Keystore, encryption, journal, filesystem unlink/fsync and
readback. The selected fault is armed only at the final deletion completion.
The BEFORE branch deliberately retains the actual native transaction and sole
connection on the test/operation thread. The journal becomes uncertain; catalog
leases are released normally and are not the blocked resource.

Room's background refresh holds `CloseBarrier` before calling `onAllowRefresh`.
That callback checks the open database, whose guarded helper performs SQLCipher
policy queries. The query waits for the connection held by the test. The test
then calls close, which waits for the refresh's barrier before closing the helper.
This circular wait cannot complete while both states persist. It is neither
Keystore authentication, fsync, a missing UI callback nor slow Gradle/boot.

`forced-before-trace.txt` captures both stacks, and
`forced-before-probe.kt.txt` contains a formatting-only copy of the diagnostic
source; its line numbers differ from the original captured stack. An observed SQL wait
latch establishes the ordering; there is no arbitrary delay used as a fix.
The diagnostic-only host terminates this probe at 60 seconds. That diagnostic
limit never changes the accepted suite's 1800-second budget. Probe 01 missed the
closing stack because its filter did not match the generated JVM method suffix;
probe 02 corrected the filter. Both attempts remain in the ledger.

Room source authority: Google Maven
`androidx.room:room-runtime-android:2.8.4` sources JAR, SHA-256
`86f67184adaf59862c237296ebe092bc45923e37d8d74dccd5a031bb7c5a09ae`.
Relevant implementation: `TriggerBasedInvalidationTracker.notifyInvalidation`,
`CloseBarrier.close`, and `RoomDatabase.close`. The cycle is API-neutral;
asynchronous scheduling allows API36 or earlier API28 runs to avoid the overlap.
No claim is made that every historical timeout had this exact internal stack.

## Remediation and regression

[ADR-PERSISTENCE-003](../../adr/ADR-PERSISTENCE-003-room-invalidation-worker-ownership.md)
admits inline worker-only Room service execution for the current synchronous DAO
model. Close, transaction completion, rollback, encrypted connection policy and
pool size are unchanged. The main-thread guard rejects work rather than silently
dispatching it elsewhere.

The existing method now observes actual Room refresh entry and completion, checks
worker ownership and unchanged transaction state, then retains all original
uncertainty, close, reopen, retry and file-absence assertions. BEFORE remains
write-locked and retry returns UNAVAILABLE; AFTER reopens as UserDeleted.
The new control fails before the fix because entry runs on `arch_disk_io_1`
rather than the instrumentation caller. This is a causal ownership assertion,
not just an elapsed-time test. AFTER is the completed-transaction control.

`regression-green-01` is the first implementation check. The following three
targeted runs use the final test APK: the completion-callback thread assertion
was added and the temporary forensic class removed. The production executor did
not change between these green iterations. Full API28/API36 and Recovery receipts
bind their own exact APK hashes; they must all identify this final test APK.

No test method was removed or added to the frozen 138-method inventory. The helper
contains no `@Test`, no product mock and no skipped callback. All local attempts,
including failed setup/build attempts, are summarized in `local-validation.json`.
Consecutive targeted runs characterize this fix; they are not a statistical
reliability campaign or a replacement for full API28/API36 and Recovery.

The ledger's `originalLocalReceiptSha256` identifies the original local receipt
bytes. Published copies normalize CRLF line endings to LF without changing JSON
values. For each published copy, `publishedReceipt` names the file and
`publishedReceiptSha256` identifies those LF bytes. The original hashes and
local receipts are preserved; the two byte representations are not claimed to
be identical.

## Unchanged acceptance boundaries

- Instrumentation 1800 s; API28 aggregate job 60 min; API36 job 45 min.
- Recovery 22 phases and historical 414/414 remain separate requirements/evidence.
- Battery efficiency remains DEFERRED / NON_BLOCKING_FOR_ALPHA; no new energy claim.
- Stage 8.6 remains NOT_READY / NON_BATTERY_ACCEPTANCE_GAPS_OPEN.
- POCO, its 46 preserved recordings, Sheet, Group D, Cloud/ASR and Stage 9 are untouched.
- Private VAD AAR/model/profile are unchanged and are not published here.
- Security restoration blocker remains OPEN; PERF-REC-001 deferred/non-blocking.

Next task after successful exact-SHA remediation is separately scoped non-battery
Stage 8.6 physical acceptance. No physical campaign starts in this task.
