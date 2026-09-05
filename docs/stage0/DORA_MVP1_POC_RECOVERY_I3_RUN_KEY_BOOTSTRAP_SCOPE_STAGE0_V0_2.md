# Dora MVP 1 — REC-I3 run-key bootstrap slice

Scope ID: `rec-i3-run-key-bootstrap-stage0-v0.2`\
Backlog: `POC-RECOVERY-001` / partial `REC-I3`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / [OD-15](DORA_MVP1_STAGE0_OWNER_DECISION_OD15.md)\
Reviewed predecessor: `00f68af1b9cee1e4d8e92110de4ae474c2bd7b64` / tree `18df912687d020503f316d886c9e5264ab35f043`

## 1. Additive boundary

This scope continues the same coherent REC-I3 Draft PR and preserves the reviewed first-slice
checkpoint. It adds the functional new-run key-confirmation bootstrap, a minimal PoC-only Android
`Os` publication adapter and a PoC-only platform SQLite run-row journal. It uses the existing typed
REC-I2B `RecoveryRunAeadProvider.createNew` path and exact key-confirmation codecs. No dependency,
production schema, production storage, Room, SQLCipher, WorkManager, backend, capture or product UI
is added or admitted.

The implementation adds focused files under the existing module's `bootstrap`, `storage` and
`journal` packages. Narrow ports make exact ordering and fault behavior observable in host tests;
the Android adapters compile against public `android.system.Os` and `android.database.sqlite` APIs.
Host observations are contract evidence only and do not prove Android filesystem, Keystore or
SQLite runtime behavior.

The prior read-only controller source and test remain byte-preserved at SHA-256
`3df585f1a3cb2fe6dfc8670a9c835db4a8c85b4b128e1ac66aa05d63a4bf3925` and
`32935757ce9369ea04b9f787e6f4769a34d5a68424fcd5f6f359890518572f93`. All earlier REC-I1/REC-I2B
source, tests, dependency graph, locks, R8 policy and evidence remain historical inputs and are not
relabeled.

## 2. Exact bootstrap contract

The controller executes exactly these semantic boundaries in order:

1. `KC01` proves the canonical alias, mandatory key-reference namespace, confirmation temp and
   confirmation final are absent. Any occupied namespace returns `KEY_REF_COLLISION`; no overwrite
   or alias replacement is attempted.
2. `KC02` creates the run alias only through `RecoveryRunAeadProvider.createNew`, whose existing
   typed boundary calls `generateNewAeadKey`.
3. `KC03` is the returned typed run AEAD obtained only through the existing Builder/`getAead` path.
4. `KC04` encodes the exact bounded `DORAKC01` plaintext and `DORAKA01` AAD and encrypts through the
   typed run AEAD.
5. `KC05` exclusive-creates `key-confirmation/run.kc.tmp` with
   `O_CREAT|O_EXCL|O_WRONLY|O_CLOEXEC`.
6. `KC06` writes the complete ciphertext, including repeated short writes.
7. `KC07` fsyncs the temp descriptor.
8. `KC08` immediately collision-checks `key-confirmation/run.kc` and renames without overwrite
   under the single-writer namespace contract.
9. `KC09` fsyncs the key-confirmation parent directory.
10. `KC10` begins a non-exclusive SQLite transaction and inserts the exact run ID, candidate ID,
    confirmation relative name, ciphertext length/SHA-256, canonical alias SHA-256 and `VALID`
    state.
11. `KC11` marks that transaction successful.
12. `KC12` is only the successful return from `endTransaction`; it constructs the private
    publication capability.
13. `KC13` then emits bootstrap evidence. Evidence failure reports a committed bootstrap with an
    event gap and retains the publication capability; it never implies rollback, deletion,
    recreation or another bootstrap attempt.

Every exception before successful `KC12` returns no publication capability and records the exact
failed boundary plus durable remainder observations. File descriptors and transactions are closed
on all paths. Unsafe, escaping, symlinked or non-regular paths fail before an unsafe object is
opened. The Android adapter enforces an immediate final collision check under the required
single-writer design; it does not claim an atomic kernel no-replace rename primitive.

## 3. Evidence and checks

Host tests cover both candidates, actual typed Tink key-confirmation encryption, exact order,
every KC boundary failure, short writes, namespace collisions, unsafe paths, descriptor closure,
transaction failures and post-commit evidence failure. Platform adapter compilation is evidence of
API compatibility only. Required final checks are the affected Recovery JVM tests, formatting,
Detekt, Android lint/compilation, the existing REC-I2B crypto-policy check, the additive Recovery
successor validator/self-tests and appropriate candidate graph/R8/package checks.

The adjacent local evidence record binds exact source hashes and check outcomes. It preserves the
first-slice 105-test and CI evidence without rerunning unchanged baselines. A narrow additive
successor validator admits only the named new scope, evidence, source/test, backlog/status and
validator files; it must preserve all predecessor pins and reject any broader Recovery path.

## 4. Claim ceiling

`POC-RECOVERY-001` remains `BLOCKED / NOT_READY`; all ten active readiness blockers remain open and
`fullRecI3Completed=false`. Recovery preflight stays locked pending full REC-I3 implementation and
review. `phaseAAllowed=false`, `executionAllowed=false`, `measuredExecutionAllowed=false` and
`productionAdmissionAllowed=false` remain exact. This slice performs no emulator/device execution,
preflight, fault or hard-kill campaign, measurement, PASS claim, production admission, PR merge or
future merged-main admission.
