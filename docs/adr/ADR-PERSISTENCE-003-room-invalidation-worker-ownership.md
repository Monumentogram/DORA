# ADR-PERSISTENCE-003: Room invalidation belongs to the journal worker

Date: 2026-10-07. Scope: owner-authorized Stage 8.6A remediation of final-deletion
close hangs. Validation is recorded separately; this decision does not close Stage 8.6.

## Cause

The real before-end deletion fault leaves SQLCipher's sole physical connection
owned by the operation thread. Room 2.8.4 can concurrently run invalidation on
its default query executor. Invalidation holds Room's close barrier while its
`onAllowRefresh` callback checks `isOpen`; DORA's guarded SQLCipher helper verifies
connection policy with SQL at that boundary. The background thread waits for the
retained connection. The owning thread then calls `RoomDatabase.close`, waiting
for the same close barrier. Neither thread can release the resource needed by the
other. A forced real-encryption probe captures both wait stacks.

This is a scheduling-dependent cycle, not proof of an API28-only platform bug.
The default executor may finish invalidation before the fault, explaining local
passes and previous successful CI. The after-end fault releases the connection
and does not create this cycle. The historical failed CI receipt identifies the
method but has no matching internal stack: its precise internal state cannot be
reconstructed retrospectively. See the [forensic record](../evidence/api28-final-deletion-8.6a/README.md).

## Decision

Set the journal's Room query executor to execute inline on the calling worker,
rejecting the main thread before executing the task. Current DAOs and journal
operations are synchronous, under the existing operation/lease ownership; there
are no admitted Flow, LiveData, suspend DAO or multi-instance observers. Finish
service invalidation within that operation rather than leaving a second thread
holding a close barrier while waiting for its connection.

Adding asynchronous DAO or observer behavior requires a new ownership review.
The executor does not move storage work to the UI or audio callback. Existing
product serial-worker boundaries remain authoritative.

No transaction is ended, rolled back or retried by this change. Pool size,
SQLCipher policy verification, encrypted payloads, deletion ordering, uncertainty
fences and reopen semantics are unchanged. In particular, before-end failure
still leaves the deliberately retained native transaction write-locked; the
test's subsequent retry must still return UNAVAILABLE. After-end failure still
reopens as UserDeleted.

## Regression and admission

The existing method is strengthened without removing assertions or changing the
138-method inventory. A test-only probe wraps and delegates the real Room refresh
entry/completion callbacks, verifies both run on the caller, and verifies the
transaction state before and after refresh. It tests ordinary refresh plus both
fault branches, normal close and the original reopen outcomes. Reflection is
limited to pinned Room 2.8.4 instrumentation and fails closed if fields change.

Before the fix, this control fails on foreign-thread ownership, before creating
the retained transaction. The separate forensic probe proves the resulting
deadlock. Device regression, full inventory, Recovery 22/22 and exact-SHA CI remain
required; a local green run is not final acceptance.

Instrumentation 1800 s, API28 job 60 min and API36 job 45 min are unchanged.
Historical records remain byte-preserved. Battery efficiency remains
DEFERRED / NON_BLOCKING_FOR_ALPHA under ADR-PERF-002. No physical campaign,
Sheet change, Stage 9, Group D, Cloud or ASR work is authorized by this record.
