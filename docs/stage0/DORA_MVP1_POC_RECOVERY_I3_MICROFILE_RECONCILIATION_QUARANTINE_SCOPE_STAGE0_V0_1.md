# REC-I3 microfile reconciliation and quarantine scope — Stage 0 v0.1

Status: Selected for bounded REC-I3 implementation; independent/accountable review pending.

Authority: OD-15 and `OWNER-AUTH-BATCH-20260819-01`. Reviewed predecessor: `3619a9c1d285e2a1c27133467a6b987d23174570`. This record permits source and non-metric host verification only. It grants no Recovery execution, preflight, measurement, PASS, production admission, gate closure, or merge.

## Required behavior

Implement the complete bounded `REC-MICROFILE-TINK` reconciliation/quarantine boundary defined by the active protocol and the adjacent preimplementation packet. The controller owns platform observations and acquires the shared process-wide run lease before inspection or mutation. Caller-constructible confirmation results, snapshots, evidence and Booleans grant no authority.

Preserve the exact nine-step confirmation/envelope priority. Inspect confirmation before unrelated active/quarantine objects. An unsafe confirmation parent is `UNSAFE_PATH`; with a durable row and safe parent, an unsafe/non-regular confirmation leaf is pre-decrypt `CORRUPT_KEY_CONFIRMATION`. Keep the reviewed confirmation controller unchanged.

A no-row confirmation temp may be quarantined after safe inspection. A no-row confirmation final may be quarantined only after bounded descriptor reading, real existing-alias Tink decrypt with exact AAD, and exact plaintext/run identity prove the inherited KCB-05 authenticated final orphan. Every unverifiable final remains retained with exact bootstrap outcome/diagnostic. No alias is generated, replaced, or deleted.

Return the longest authenticated gap-free prefix. Validate the bootstrap root exactly, then find the maximal structurally valid contiguous journal prefix without letting malformed/ahead later rows erase earlier validity. Search manifests downward; latest invalid/missing/ahead `N` may fall back to authenticated `N-1`. Unit failure at `i` preserves only `[0,i)`. The private capability binds the actual manifest generation used, authenticated unit count/end, plaintext SHA-256, manifest SHA-256, and ordered-row digest. It authorizes consumption only and cannot satisfy either publication capability.

Prepare safe `quarantine/<runId>/objects` directories and fsync each created directory's parent after confirmation priority is known and before Q01. Quarantine follows exactly: intent commit, no-overwrite rename, source-directory fsync, destination-directory fsync, completion commit. The stable SHA-256 intent identity excludes observed state; the original recorded state remains immutable evidence while later observations drive replay. Validate complete persisted rows on every conflict/unknown transaction outcome.

`COMPLETED` is a no-op only for exact row + source absent + exact destination. Both paths present is collision even with equal bytes. Source present/destination absent or neither present is inconsistent retry, with no recreate/delete. Exact completed replay retries the same logical evidence event at least once. Existing intent destinations are not unknown sources; unreferenced/unsafe quarantine objects are retained and never recursively quarantined.

Use the same `poc-recovery/v1/recovery-journal-v1.db`, raise only `user_version` to 3, and add only the PoC quarantine-intent table selected in ADR-0004. `onUpgrade(1,3)` must validate exact v1, execute existing 1-to-2, validate exact v2, execute 2-to-3 and validate exact v3 inside the framework transaction. Direct `onUpgrade(2,3)` validates exact v2 first. Reject all unsupported versions/downgrades/malformed Recovery schemas without destructive fallback and preserve rows/platform metadata.

All returned progress/remainder data is deeply immutable. Side-effecting exceptions report conservative unknown state. Post-completion evidence failure never implies journal rollback, deletion, recreation, or filesystem reversal.

## Exact source boundary

Create:

- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryMicrofileReconciliationController.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/AndroidRecoveryMicrofileReconciliation.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryQuarantineIntent.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryQuarantineJournal.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/RecoveryReconciliationPathPolicy.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryReconciliationStorage.kt`
- focused matching tests under the existing `candidate`, `contract`, `journal`, and `storage` test packages

Modify only:

- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/AndroidRecoveryMicrofileCrypto.kt`
- `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryJournalDatabase.kt`
- `tools/verify_rec_i3_microfile_sqlite.py`
- `tools/validate_poc_recovery_governance.py`
- `tools/test_poc_recovery_i3_governance.py`
- this slice's additive evidence plus `docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md` and `docs/DORA_MVP1_STAGE_STATUS.md`

Do not edit the reviewed confirmation controller, bootstrap crypto/storage/source/tests, previous scopes/ADRs/evidence, build scripts, version catalogs, dependency locks, R8 rules, manifests, or I1/I2B code. No dependency is added.

## Required evidence

Use meaningful behavioral RED/GREEN tests for nine-step ordering; KCB-05; actual-Tink full/fallback/partial prefixes; capability forgery; malformed later rows; all five filesystem states; unsafe paths and descriptor closure; Q01-Q05 faults and exact replay truth table; observed-state changes; no recursive quarantine; same-run cross-controller exclusion and different-run independence; post-completion evidence failure; exact 721/overflow limits; and exact v1-to-v2-to-v3/direct-v2-to-v3 host SQLite migration including rollback, malformed schemas, preserved rows and platform metadata.

Run affected Recovery tests, Spotless, Detekt, lint, release compile, crypto policy, the committed host SQLite verifier, governance unit/self-tests, and exact candidate validation. Host fakes/SQLite and compiled Android adapters are not Android runtime proof. Keep all ten readiness blockers and `fullRecI3Completed=false`.
