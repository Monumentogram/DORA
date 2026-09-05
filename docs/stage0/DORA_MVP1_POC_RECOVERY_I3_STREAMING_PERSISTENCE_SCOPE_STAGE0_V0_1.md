# DORA MVP 1 — REC-I3 streaming persistence scope Stage 0 v0.1

Status: **Owner-confirmed governance scope; implementation blocked pending CLEAN review of this exact governance commit**  
Baseline: `3c63ab09874f4d089e4363985aa8b5c99900c122` / tree `718eae8d8d619d17c25ac9d025e0e24db3d52f9e`  
Authority: `OD-15`, `DEC-045`, `DEC-046`, ADR-0005, Gate Set/protocol v0.7  
Design packet: SHA-256 `9f8e3a6e4d20faf0d744310b83ca46bd34d5c6798785e1197e6b61fb8ae81011`

## Goal and non-goals

Implement and verify the bounded synthetic REC-STREAM-TINK persistence slice: schema v4 migration, checkpoint persistence, sealed outcomes, rejected observations, retained ACTIVE ranges, one-descriptor source gateway, exact replay/collision handling, and K12-PERSISTENCE. Preserve the source file and C; create no streaming processing intent.

This scope excludes device/emulator execution, Recovery preflight, real process death, hard-kill/fault/Phase A/measured campaign, power-loss or physical durability claims, production schema/storage/controller, new dependency/provider/module, final container selection, consumer intent, microphone/network/GMS behavior, cross-process denial, range retirement/cleanup, PASS/READY, production admission, and merge.

## Governance candidate

Before source work, exactly these eight governance files must form one internally consistent commit and receive an independent CLEAN critical review:

```text
docs/adr/ADR-0005-poc-recovery-streaming-persistence-and-range-quarantine.md
docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_7.md
docs/stage0/poc-recovery-gate-set-stage0-v0.7.json
docs/stage0/poc-recovery-protocol-stage0-v0.7.json
docs/stage0/DORA_MVP1_POC_RECOVERY_I3_STREAMING_PERSISTENCE_SCOPE_STAGE0_V0_1.md
docs/DORA_MVP1_PRODUCT_DECISIONS.md
docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md
docs/DORA_MVP1_STAGE_STATUS.md
```

All v0.1-v0.6 Gate/protocol files are immutable. The governance baseline and every later evidence record must pin `3c63ab09874f4d089e4363985aa8b5c99900c122` / `718eae8d8d619d17c25ac9d025e0e24db3d52f9e` and the three v0.6 hashes recorded by Gate Set v0.7.

## Eligible implementation paths after CLEAN governance review

```text
android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryJournalDatabase.kt
android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryQuarantineIntent.kt
android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryQuarantineJournal.kt
android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryReconciliationOutcomes.kt
android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryStreamingPersistence.kt
android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryStreamingJournal.kt
android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryStreamingSource.kt
android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingReconciliationController.kt
```

## Eligible test and validation paths

```text
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryJournalSchemaPlanTest.kt
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryQuarantineIntentTest.kt
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryQuarantineControllerTest.kt
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryReconciliationSourceTest.kt
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryReconciliationStorageTest.kt
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryStreamingPersistenceTest.kt
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryStreamingJournalTest.kt
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryStreamingSourceTest.kt
android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingReconciliationControllerTest.kt
tools/validate_poc_recovery_governance.py
tools/test_poc_recovery_i3_governance.py
tools/verify_rec_i3_streaming_sqlite.py
```

No other source, test, tool, dependency, workflow, or evidence path is eligible without a successor scope.

## Required red-first proof

Tests must first fail for missing schema-v4 objects/migrations and missing persistence/controller behavior. Coverage must include exact v1/v2 DDL preservation; fresh v4 plus 1→2→3→4, 2→3→4, and 3→4; byte-exact v3 row migration; every migration failpoint returning exact v3; q0/q1/q2/q3/q28236 checkpoint bounds and chain/split-brain cases; all witness/oracle/rejected-observation/outcome/range encoding mutations; Tink header/segment/authentication/EOF and append 1/4096/8192; extent maximum, one-over, overflow/narrowing; 8160 acceptance and 8161 rejection; all seven persisted diagnostic rows; B<E, B=E, EOF and fabricated/missing/wrong range cases; E<S; collision, sink and ambiguous-commit outcomes; gateway/lease ordering; source immutability; and absence of streaming intent, C advancement, metadata adoption, retirement, microphone, network, or GMS behavior.

## Verification and evidence

Run focused tests, the repository-documented Android aggregate checks, `python tools/verify_rec_i3_streaming_sqlite.py`, `python -m unittest tools/test_poc_recovery_i3_governance.py -v`, and `python tools/validate_poc_recovery_governance.py`. Evidence must be deterministic and synthetic, pin exact paths/commits/trees/commands/counts/hashes, contain no plaintext, keys, private audio, raw database/WAL, ciphertext, rejected plaintext digests, mismatch offsets, or mismatch bytes, and state that host evidence is not Android durability/campaign evidence. Independent critical review and exact-head CI are required before any later conditional merge consideration.
