# REC-I3 V11 SQLite OpenParams FULL Implementation Plan

> **Execution:** keep this bounded successor on `codex/rec-i3-v11-sqlite-openparams-full` at exact base `e0e8b0e2e4ae210dc72b4042c42c526fec003b6a`; use RED/GREEN and verify before each commit.

**Goal:** Correct the production recovery-journal open configuration so every WAL connection is opened with SQLite synchronous FULL, and emit one sanitized pre-assert observation of the four existing SQLite PRAGMAs while preserving the V10 failure evidence and all recovery assertions.

**Design:** Build `SQLiteDatabase.OpenParams` before `SQLiteOpenHelper` opens the database, set `ENABLE_WRITE_AHEAD_LOGGING` and `SYNC_MODE_FULL`, and remove the connection-local `execSQL("PRAGMA synchronous=FULL")`. Keep the existing foreign-key and `wal_autocheckpoint=0` observation unchanged. Android has no public API across the complete minSdk-28 range that applies an arbitrary PRAGMA to every pooled connection (`execPerConnectionSQL` starts at API 30), so V11 does not broaden the autocheckpoint behavior.

**Scope:** production helper construction, the existing E36 preflight's instrumentation-only SQLite diagnostic, the streaming-SQLite verifier, exact V11 governance admission/tests, and this plan only. The diagnostic does not change any instrumentation assertion, production helper/autocheckpoint behavior, schema, dependency, runner, selector, timeout, cleanup, workflow, historical evidence, or device-execution contract. Its four ordinary `rawQuery` calls are not a same-connection atomic snapshot and do not prove pool-wide behavior.

## Task 1: Lock the OpenParams contract RED-first

**Files:**

- Modify: `tools/verify_rec_i3_streaming_sqlite.py`
- Test: `tools/verify_rec_i3_streaming_sqlite.py`

- [ ] Add structural checks requiring a pre-open `SQLiteDatabase.OpenParams.Builder`, WAL open flag, and `SYNC_MODE_FULL`.
- [ ] Reject the old `setWriteAheadLoggingEnabled(true)` and connection-local `execSQL("PRAGMA synchronous=FULL")` paths.
- [ ] Run `python tools/verify_rec_i3_streaming_sqlite.py`; record the expected RED against the unchanged V10 production helper.

## Task 2: Apply the minimal production correction

**Files:**

- Modify: `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryJournalDatabase.kt`
- Test: `tools/verify_rec_i3_streaming_sqlite.py`

- [ ] Pass exact OpenParams through the API-28 `SQLiteOpenHelper` constructor.
- [ ] Leave foreign-key configuration and the existing autocheckpoint query/assertion unchanged.
- [ ] Run the focused verifier GREEN and inspect the source diff.

## Task 3: Admit only the exact V11 successor

**Files:**

- Modify: `tools/test_poc_recovery_i3_governance.py`
- Modify: `tools/validate_poc_recovery_governance.py`
- Test: `tools/test_poc_recovery_i3_governance.py`

- [ ] Add behavioral V11 source-candidate, exact-blob/path, branch/base/history, and dependency-entry admission tests plus mutation rejection.
- [ ] Record governance RED before validator support.
- [ ] Route exact V11 validation before V10 fallbacks and pin the final implementation blobs fail-closed.
- [ ] Run the affected V11 governance tests GREEN.

## Task 4: Verify and preserve evidence

**Files:**

- Verify all six bounded V11 paths only.

- [ ] Run Python compilation, the complete affected non-device verifier/governance subset, and relevant offline Kotlin compile checks.
- [ ] Prove the E36 instrumentation requirements and V10 historical evidence are unchanged.
- [ ] Exercise a future PowerShell launcher exit-code capture approach outside the repository only; do not change V11 production/governance scope.
- [ ] Commit bounded implementation/governance commits without amend/rebase, record exact heads/trees/parents/status, and write the external V11 report.

## Task 5: Admit the pre-assert SQLite diagnostic

**Files:**

- Modify: `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryE36GapiPreflightInstrumentedTest.kt`
- Modify: `tools/test_poc_recovery_i3_governance.py`
- Modify: `tools/validate_poc_recovery_governance.py`

- [ ] Collect `journal_mode`, `synchronous`, `wal_autocheckpoint`, and `foreign_keys` once each through ordinary `rawQuery`, then print `INSTRUMENTATION_SQLITE_PRAGMAS_DIAGNOSTIC` before any related assertion.
- [ ] Include only the reviewed harness/phase/API/thread/transaction/database-state/query-context fields, exact values or null, and bounded failure classifications; never emit database paths, exception text, record contents, or device-unique identifiers in this diagnostic.
- [ ] Fail after printing the full event if any query failed, and preserve the four strict WAL/FULL/zero/foreign-key requirements plus the later terminal `INSTRUMENTATION_STATUS` marker.
- [ ] Add focused governance and mutation tests covering ordering, required fields, failure handling, unchanged values, sanitization, and the exact instrumentation blob/path admission.
