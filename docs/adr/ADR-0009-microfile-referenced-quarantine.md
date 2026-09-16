# ADR-0009: MICROFILE referenced quarantine and authenticated retained extents

- Status: accepted design; implementation and campaign validation pending
- Date: 2026-09-16
- Scope: isolated Stage 0 Recovery PoC, INTERNAL_ALPHA_E36_REDUCED_114
- Owner decision: explicit approval of the schema 6 proposal on 2026-09-16
- Approved proposal SHA-256: `b4731330482017700d77a1348aec436b3859a6aac47bcc1ea8deb72114ddb4c1`

## Context

MICROFILE TRU-03, COR-01, COR-04 and TRU-02 can reject an existing object that
the original committed journal still references. The old quarantine states
describe temporary, orphan or unknown objects and cannot truthfully represent
this case. STREAM already uses shared schema 5 under ADR-0008.

An appended object may contain the complete original authenticated ciphertext.
The complete appended object still fails its original journal identity and must
remain retained as evidence. A genuinely corrupted or truncated ciphertext may
have no recoverable authentic plaintext beyond the preceding unit.

## Decision

Use shared schema 6. Preserve the database path, physical table and index names,
columns and their order, original identities, destinations and historical states.
Keep every accepted STREAM schema 5 definition and sealed FATAL replay unchanged.

Only the constraints of `recovery_quarantine_intent_v4` change:

- `REFERENCED_REJECTED` binds the full actual bytes of an existing referenced
  object that fails its original identity or authentication, including an appended
  suffix. Missing objects, unsafe names and operational failures do not qualify.
- `REFERENCED_DEPENDENT` binds an existing object with verified original identity
  whose dependency on the first nonrecoverable unit or later state is established
  by an authenticated manifest and original journal ranges and generations.
  Independent earlier objects do not qualify. Independently rejected later bytes
  must be classified separately.

Both new states require MICROFILE, PRESENT bootstrap with non-null matching run
and candidate identities, and one of the four unit/manifest ciphertext/envelope
roles. They are invalid for STREAM, absent bootstrap and all other roles. Existing
states retain their existing constraints. Q01–Q05 intent, rename, directory sync,
completion commit and exact replay remain the only movement protocol.

### Migration

Exact schema 5 migrates to 6 in the SQLiteOpenHelper transaction. Supported older
versions first follow their existing route to exact 5 and then the same 5→6 step.
Fresh databases create exact 6. Unknown schema/version and downgrade are rejected;
there is no destructive fallback or foreign-key disable.

Validate exact source DDL, integrity and foreign keys before mutation. Rebuild only
quarantine with a temporary copy that preserves SQLite cell storage types and raw
bytes. Compare complete ordered snapshots, identities, destinations and unchanged
schemas before and after; ordering uses each table's full primary key. Every
failure propagates and rolls back the whole upgrade, including version assignment.

### Separate evidence reader

Normal artifact loading remains active-storage-only. A separate MICROFILE reader
may read an exact original journal-bound extent from a completed retained object
after verifying intent identity, run/bootstrap, role, destination, complete
container length and SHA-256, and the original extent's length and SHA-256.

These checks do not authenticate plaintext. The existing real AEAD operation must
still use the original envelope and AAD and return the exact expected plaintext
length; manifests must still satisfy original journal semantics. The appended
suffix remains evidence only. No object is restored, promoted or labelled healthy
because this reader succeeds, and there is no general quarantine-loading API.

## Consequences and required evidence

Original committed C, processing intents, the independent byte oracle, fixtures,
seeds, recipes and loss bounds remain unchanged. TRU-03 may retain the full original
authenticated prefix. COR-01 and other genuine corruption can retain product FAIL
even when disposition conforms to the diagnostic scenario.

Before campaign admission, validate fresh 6 and every supported upgrade, exact
typed history, every migration rollback point, unknown schema and downgrade
rejection, both candidates on the shared database, STREAM ADR-0008/sealed replay,
MICROFILE Q01–Q05 interruption/replay, and retained-extent substitution rejection.
Bind the resulting source/APKs/build/CI and required fresh preflights. Reassess
historical evidence applicability and use new canonical attempts for the four
affected recipes. This decision itself grants no runtime credit or stage closure.
