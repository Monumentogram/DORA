# Dora MVP 1 — REC-I3 sequential microfile publication clarification

Clarification ID: `rec-i3-sequential-microfile-publication-clarification-stage0-v0.1`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / OD-15\
Scope predecessor: `4eab3eae72b9196fbd114339b9fe96bba7705f00` / tree `c7656c9107a111032ae4d247ea267acb831d1516`

This additive clarification freezes the implementation invariants already implied by ADR-0003 and
the active Recovery protocol. It does not change their claim ceiling or authorize another feature.

1. `SQLiteOpenHelper` owns the transaction around `onUpgrade`. The exact v1-to-v2 migration executes
   its validation, unique-index creation and table creation inside that framework transaction. It
   must not call `beginTransaction`, `setTransactionSuccessful` or `endTransaction` recursively.
2. Candidate work starts only when the unified journal contains exactly one matching bootstrap row
   for the canonical run ID and `REC-MICROFILE-TINK`, with key-confirmation state `VALID`. Missing,
   duplicate, cross-candidate or non-VALID bootstrap state fails before alias access.
3. The latest candidate state must be unambiguous. Before alias, crypto or filesystem mutation, read
   and validate all committed unit/publication rows needed to prove:
   - unit indices start at zero and increase by exactly one;
   - plaintext ranges start at zero, are non-empty and remain contiguous;
   - manifest generations start at one and increase by exactly one;
   - each manifest terminal range equals the corresponding committed unit end;
   - each prior publication relative name, size, SHA-256 and previous-publication digest form the
     exact canonical chain;
   - cadence is exactly one of 5, 15 or 30 seconds;
   - numeric increments and plaintext byte additions cannot overflow;
   - the next cumulative manifest contains at most 721 entries.
4. Any duplicate latest generation, missing predecessor, row gap, disagreement between unit and
   publication state, malformed fixed-width name, or inconsistent identity is ambiguous and fails
   closed before opening the run alias or creating a file.
5. Host tests must cover valid genesis and later state plus each ambiguity family. Platform schema
   tests may verify a pure exact schema/migration plan on the host, but Android SQLite execution,
   framework transaction behavior and effective PRAGMAs remain device-preflight evidence and must
   not be claimed from host fakes.

All ten readiness blockers, the Recovery preflight lock, `fullRecI3Completed=false` and every
execution/measurement/admission nonclaim remain unchanged. No production schema, migration or
storage is admitted.
