# ADR-0008: Streaming recovery from a surviving authenticated checkpoint prefix

- Status: Accepted for the owner's bounded STREAM repair; runtime admission remains separate
- Date: 2026-09-15
- Decision owner: Project owner
- Applies to: Stage 0 REC-STREAM-TINK only
- Authority: explicit owner decision `STREAM-TRU01-OWNER-DECISION-20260915`, accepting the distinct `P <= E < S` proof, implementation, migration and checks

## Context

The immutable v0.7 contract and ADR-0005 require `P <= S <= E` before decrypting. The inherited TRU-01 case expects a committed prefix to survive a bounded truncation of an uncommitted tail. The observed fixture has `P=475136`, `S=479232`, `E=477232`, `C=469176`, and `A=480000`. The old controller authenticates its checkpoint but rejects the source before a public Tink read because `E<S`. A real host Tink reproduction returns `R=473256` through positive completed public reads; the subsequent call fails authentication. Its tail loss is 6744 bytes, within the unchanged 8160-byte bound.

The owner explicitly resolved this conflict by accepting a separate surviving-checkpoint-prefix proof. This ADR is an additive clarification. Historical protocol files, original failed evidence, sealed outcomes and source witnesses are not rewritten. It does not adopt the separate MICROFILE quarantine proposal.

## Decision

The original `VERIFIED_SAME_DESCRIPTOR` proof remains exactly `P<=S<=E`, requiring both checkpoint-prefix and full pre-fault-prefix SHA-256 equality on the single opened bounded descriptor.

Add `VERIFIED_SURVIVING_CHECKPOINT_PREFIX` only for `P<=E<S`. It requires the unchanged exact run, checkpoint chain, authenticated checkpoint/envelopes, controller witness and oracle, plus exact `[0,P)` SHA-256 equality from that same descriptor. The source is frozen at E and hashed fully as before. No hash of the unavailable `[E,S)` bytes is inferred. Original S and its full pre-fault SHA-256 remain in the witness and outcome. The proof name describes only the surviving checkpoint prefix.

Only after one of these disjoint proofs succeeds may the existing public Tink reader run. Positive completed reads, oracle equality, `C<=R<=A`, the 8160-byte loss bound, boundary arithmetic and diagnostic precedence after intersection remain mandatory. Checkpoint C never advances. No metadata, semantic commit or processing intent is adopted. Source files are not modified. The exact non-empty authentication-failure range remains `[B(R),E)` and is retained in place with ACTIVE read denial.

Missing or unequal checkpoint prefix, `E<P`, unsafe source, invalid checkpoint, altered witness and all operational errors retain their existing closed behavior. The new proof may also accompany existing post-intersection diagnostics; it does not convert those diagnostics into VALID.

## Schema 5 and historical preservation

Advance the shared journal's `user_version` to 5 at its unchanged path. Keep all physical table/index names and all columns. The only DDL change expands the outcome source-proof enum and replaces the two VALID/POST_INTERSECTION intersection predicates with the exact disjunction above. PRE_INTERSECTION FATAL rows remain legal unchanged, including historical `E<S` rows. MICROFILE table DDL and behavior remain unchanged.

The v4 DDL constant is preserved for exact migration preflight and historical schema verification. The v5 outcome DDL is derived by exactly two replacements of the frozen old intersection predicate and one enum extension; a count guard rejects unexpected source DDL drift. Exact v5 open compares the complete object set and all SQL, using only the new outcome statement.

SQLiteOpenHelper owns the single create/upgrade transaction. Existing exact v1/v2/v3 upgrades first reach exact v4, then the same v4-to-v5 migration runs. Direct v4-to-v5 is supported. Unknown upgrades and all downgrades remain rejected. There is no destructive fallback.

Before mutation, require exact v4 objects/SQL, SQLite integrity and foreign-key checks. Compute ordered, column-complete source digests that bind each SQLite storage type and raw CAST-as-BLOB bytes. Create temporary outcome/range copies and verify their independent digests before dropping range then outcome. Recreate the outcome with v5 constraints and the unchanged range, restore outcome before range, recreate the unchanged index, and compare both complete destination digests. Drop temporary copies and require exact v5 schema, integrity and foreign keys. Every error escapes to framework rollback. No historical row is relabeled or granted a new proof during migration.

Identity encoding domains and column order remain unchanged. The source-proof enum spelling already enters the outcome hash, so the new proof has a distinct identity while every historical identity remains byte-exact. Its child range binds that identity. Replay finds an existing sealed witness before authentication or fresh admission: an old FATAL outcome remains FATAL and is never replaced by a new VALID outcome. Fresh campaign execution requires a separately admitted attempt namespace.

## Verification and applicability

Host checks cover real Tink truncation, exact controller admission and hash-only replay, typed journal codec readback, proof tampering, SQL bounds, preservation of old FATAL rows and rollback at every mutation. Host SQLite is not Android fsync, power-loss or device migration evidence.

The shared database version/create/upgrade path changes for both candidates even though the new semantic proof is STREAM-only. Historical journal/SQLite preflight observations cannot be reused as proof of schema5 creation or migration. Before a new runtime packet, run fresh affected journal/platform preflight and an Android schema5 migration check on the exact built source/APKs. Unchanged Keystore/Tink preflight applicability must be separately traced against the final integrated source; this ADR grants no blanket reuse, source equivalence, campaign completion or product PASS.

Existing failed attempts and host UNCERTAIN statuses remain immutable. Any new evidence is recorded as a new attempt and may support a separately reviewed current-candidate applicability assessment. The affected STREAM extent paths include surviving-prefix truncation; full-source append/identity paths remain covered by regression tests. Final integration, exact-source review, required build checks and CI remain separate admission steps.
