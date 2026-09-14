# REC-I3: per-connection SQLite configuration (review candidate)

Owner scope: attempt03 diagnosis and minimal correction, 14 September 2026.
Base: `ad9d985fd0ddbf10026d53a8cc4a101e043e1f13`.

Attempt03 remains EXECUTED / FAIL / CONSUMED. Its diagnostic observed WAL,
synchronous=2, wal_autocheckpoint=100, foreign_keys=1; no connection ID was observed.
The singleton rawQuery assignment is not a configuration for the entire Android
SQLite pool. Replace it with execPerConnectionSQL on API 33+, preserving pre-open
WAL/FULL and setForeignKeyConstraintsEnabled(true). Diagnostic readback and its
strict assertions remain unchanged. Schema and migrations are unchanged.

After the concrete compatibility review finding, the owner explicitly accepted
API 33+ for this **current PoC**, with refusal on API 28–32.
The helper rejects before preparing the journal directory or opening its database.
Application minSdk remains 28. Finished-application API 28–29 support remains a
requirement; its implementation and device verification are a separate task.
This is not an admission of a reduced production support matrix.

The API exists since 30, but examined AOSP Android 11/12 native implementations
reject SQLITE_ROW from per-connection PRAGMA execution. Examined Android 13–16
implementations accept and drain PRAGMA rows. API 32 source was not established in
this review; it is conservatively outside the owner-approved current-PoC boundary.
This is not a claim that every API 32 firmware has the older behavior. No exception
is swallowed and no PRAGMA requirement is relaxed to cross the compatibility gap.

- https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android11-release/core/jni/android_database_SQLiteConnection.cpp
- https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android12-release/core/jni/android_database_SQLiteConnection.cpp
- https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android13-release/core/jni/android_database_SQLiteConnection.cpp

The helper becomes internal only to let an isolated regression use the real helper
without closing or replacing the process-wide production singleton. The regression
pins a primary connection in a write transaction, executes precompiled read-only
PRAGMAs from another thread before releasing primary, and repeats with a new helper
after close. It also tests pre-filesystem refusal on API 28–32. JVM tests cover the
API policy; host SQLite verification retains schema/migration/FK checks and updates
the source guard to require the per-connection API.

Android regression execution is NOT authorized in this task. Compilation/JVM/static
checks cannot establish a corrected Android PASS. Future admission requires reviewed
source bytes, a source identity which includes this patch, freshly built/pinned APKs,
a separate execution identity and explicit runtime authorization. Existing V9/V10/V11
and FROZEN11 historical contracts/validators/evidence are not rewritten as successors.

Platform references:
- https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#execPerConnectionSQL(java.lang.String,%20java.lang.Object[])
- https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/database/sqlite/SQLiteConnection.java
- https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/database/sqlite/SQLiteConnectionPool.java

These explain platform behavior; they do not identify attempt03's individual native
connections or constitute a trace of its exact emulator framework binary.
