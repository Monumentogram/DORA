# Dora MVP 1 — REC-I3 sequential microfile publication slice

Scope ID: `rec-i3-sequential-microfile-publication-stage0-v0.1`\
Backlog: `POC-RECOVERY-001` / partial `REC-I3`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / OD-15\
Reviewed predecessor: `a0348fe1d76a062af7d145045ef432f0a1eadf1c` / tree `7af474369e06c1cceef2168eba224accb5ece4ba`\
Architecture decision: [ADR-0003](../adr/ADR-0003-unified-poc-recovery-journal-and-run-lease.md)

## Additive boundary

This scope continues the same coherent REC-I3 Draft PR after the independently clean run-key
bootstrap checkpoint. It adds repeatable sequential `REC-MICROFILE-TINK` unit plus cumulative
manifest publication through the exact active v0.3 `MICRO-P01`–`MICRO-P21` order inherited by
protocol v0.6. It also makes the run lease shared across bootstrap/candidate controllers and evolves
the unified PoC journal through the exact non-destructive v1-to-v2 decision in ADR-0003. The
migration preserves the existing `poc-recovery/v1/recovery-journal-v1.db` file and
`recovery_run_bootstrap_v1` table; schema version changes through SQLite `user_version` rather than
a new filename.

The candidate controller requires the matching unforgeable bootstrap publication capability before
alias access. It opens the existing run AEAD only through `RecoveryRunAeadProvider.openExisting`
with no generation fallback. It obtains unit index, plaintext start, manifest generation,
previous-manifest SHA-256 and cumulative prior entries from validated durable journal state rather
than caller continuation claims. Genesis is unit 0 / generation 1 / start 0 / zero previous digest;
later values increase exactly and the manifest retains all prior entries. Bounds, continuity,
overflow, duplicates and the 721-entry maximum fail before mutation.

For each unit, the controller:

1. creates a fresh typed microfile keyset and encrypted envelope;
2. durably publishes the exact unit envelope and ciphertext through exclusive temp creation,
   complete short writes, file fsync, immediate collision check, rename and parent fsync;
3. builds the exact cumulative `RecoveryManifest` from final unit identities;
4. creates a fresh typed manifest keyset and durably publishes its encrypted envelope and encrypted
   manifest through the same rules;
5. inserts the exact protocol unit/publication rows in one non-exclusive SQLite transaction;
6. treats only successful `endTransaction` return as `MICRO-P20` semantic commit and issues a
   private publication capability/result;
7. emits `MICRO-P21` evidence after commit, retaining the committed capability and artifacts if
   evidence emission fails.

Every earlier failure exposes no candidate publication capability, records exact durable remainder,
and closes every acquired descriptor/transaction. It never overwrites, retries, deletes, promotes a
temp by name, recreates an alias or infers rollback after commit. Android `Os.rename` is bounded to
the shared in-process same-run lease plus immediately preceding `lstat`; no atomic kernel
no-replace or multi-process claim is made.

## Exact repository scope

The implementation may add or change only:

- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/coordination/RecoveryRunSingleWriterGuard.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryMicrofilePublicationController.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/AndroidRecoveryMicrofileCrypto.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/RecoveryCandidatePathPolicy.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryCandidateStorage.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryJournalDatabase.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryMicrofileJournal.kt`
- the reviewed bootstrap controller and platform journal only for the shared lease/database boundary;
- focused tests under matching `candidate`, `coordination`, `storage` and `journal` test packages;
- this scope, ADR-0003, one additive evidence record, additive backlog/status entries, and the narrow
  REC-I3 successor validator plus its tests.

No existing REC-I1/REC-I2B crypto or contract source, build file, dependency declaration, lock,
dependency/R8 policy or historical evidence may change. If an exact processing-intent or row codec
is missing, stop that affected path and add only a separately scoped contract implementation rather
than inventing an encoding inside the writer.

## Required evidence

Behavioral host tests must establish:

- actual Tink round trips for unit, manifest and both encrypted key envelopes with exact AAD;
- exact `MICRO-P01`–`P21` order for genesis and a later unit, with cumulative prior entries retained;
- wrong bootstrap capability rejection before alias access;
- short-write completion, zero/invalid progress, safe/unsafe collisions and descriptor closure;
- no capability before successful P20; P20 failure is not commit; P21 failure retains commit;
- shared cross-bootstrap/candidate same-run exclusion and different-run independence;
- exact row names, sizes, hashes, previous digest and processing-intent identity;
- fresh schema-v2 creation, exact v1-to-v2 row-preserving migration, foreign keys and strict
  unsupported-version failure;
- a non-destructive bootstrap `(run_id, candidate_id)` unique index and composite child foreign keys
  that reject cross-candidate rows;
- one process-shared database helper and serialization of different-run non-exclusive writer
  transactions by SQLite's single-writer behavior;
- unsafe database/sidecar and artifact paths fail before unsafe open.

Run affected Recovery unit tests, formatting, Detekt, lint/release compilation, crypto policy, exact
successor governance/self-tests and candidate graph/package/R8 evidence. Platform adapters compile
as source evidence only.

## Claim ceiling

`POC-RECOVERY-001` remains `BLOCKED / NOT_READY`; all ten readiness blockers remain open and
`fullRecI3Completed=false`. Recovery preflight stays locked. This slice performs no capture/audio
callback, stream/checkpoint path, reconciliation/quarantine, external controller, device/emulator
execution, preflight, hard kill, fault campaign, measurement, PASS claim, dependency/production
schema admission, push, merge or future merged-main admission. ADR-0003 and this scope record a
selected bounded implementation choice; independent/accountable implementation review remains
pending and no owner/human acceptance is claimed by their status.
