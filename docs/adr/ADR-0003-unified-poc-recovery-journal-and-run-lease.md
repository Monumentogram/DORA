# ADR-0003: Unified PoC Recovery journal and shared run lease

Status: Selected for bounded REC-I3 implementation; independent/accountable review pending\
Date: 5 September 2026\
Authority: OWNER-AUTH-BATCH-20260819-01 / OD-15 bounded REC-I3 implementation\
Related: POC-RECOVERY-001, REC-I3, active protocol `poc-recovery-protocol-stage0-v0.6`

## Context

The reviewed REC-I3 bootstrap slice creates the run alias, key confirmation and bootstrap row. The
next candidate sequence must publish a microfile and cumulative manifest, then commit their exact
identities under the protocol's single-writer and SQLite semantic-commit rules.

The bootstrap currently owns a private process-wide same-run lease. A separate candidate guard
would allow bootstrap and candidate controllers to mutate the same run concurrently. The bootstrap
journal is a PoC-only schema-version-1 database containing only the run bootstrap table. A separate
candidate database would prevent bootstrap, unit and publication identities from sharing foreign
keys or one SQLite transaction and would add a cross-database split-brain boundary.

Changing the version-1 schema without advancing its version is unsafe: an already-created v1
database would not receive the new tables. Destructive recreation is forbidden, even for the PoC.

## Decision

1. Move the process-wide run lease to one internal coordination boundary keyed by canonical run ID.
   Bootstrap and candidate controllers use the same guard. Same-run overlap is rejected across
   controller/storage instances; different runs remain independent. This is an in-process claim and
   does not claim a kernel or multi-process lock.
2. Use one PoC Recovery SQLite database for bootstrap, unit and publication identities. Preserve
   the exact existing path `poc-recovery/v1/recovery-journal-v1.db` and bootstrap table
   `recovery_run_bootstrap_v1`; schema version advances through SQLite `user_version`, not a renamed
   database file or replacement table.
3. Advance its schema from version 1 to version 2. Fresh creation installs the bootstrap, unit and
   publication tables. The only admitted upgrade is an explicit non-destructive `1 -> 2` migration
   that first validates the expected v1 bootstrap table, preserves every row and creates the two new
   tables. Every other upgrade and every downgrade fail closed. There is no delete/recreate or
   fallback path.
4. Add a non-destructive unique index on bootstrap `(run_id, candidate_id)`. Unit and publication
   rows use composite restrictive foreign keys to that identity, so a candidate row cannot cross a
   run's selected candidate. A
   microfile unit row and its cumulative manifest publication row commit in one
   `beginTransactionNonExclusive` transaction; only successful `endTransaction` return is the
   semantic commit.
5. Bootstrap and candidate journals obtain one process-shared helper/database boundary. Per-run
   leases protect filesystem namespaces; SQLite's single-writer transaction serialization protects
   the unified database when different runs reach their non-exclusive write transactions
   concurrently. Do not add an unsynchronized second helper or database path.
6. Keep the database under the fixed no-backup PoC Recovery root with WAL,
   `synchronous=FULL`, `wal_autocheckpoint=0`, foreign keys, strict database/sidecar path checks and
   protocol-exact row constraints.
7. Treat the schema and migration as PoC evidence only. This decision does not admit a production
   schema, migration, storage dependency or runtime claim. Device migration and effective PRAGMA
   proof remain blocked until the Recovery preflight gate.

## Consequences

Positive:

- bootstrap and candidate work cannot overlap for one run inside the process;
- candidate rows cannot refer to a missing bootstrap row;
- unit and manifest identities share one atomic SQLite commit;
- an existing PoC v1 bootstrap row survives the exact v2 migration;
- later reconciliation has one authoritative journal rather than cross-database state.

Costs and limits:

- the reviewed bootstrap controller and journal require narrow additive changes and renewed source
  hashes/review;
- schema-v2 and migration logic require host tests plus later device runtime evidence;
- process-wide locking does not coordinate another process;
- filesystem publication still precedes SQLite semantic commit, so crash remainders remain for the
  future reconciliation/quarantine slice.

## Rejected alternatives

- A candidate-local guard was rejected because it does not exclude bootstrap mutation.
- A separate candidate database was rejected because SQLite cannot provide the required shared
  foreign keys or one transaction across the two files.
- Adding tables while retaining schema version 1 was rejected because existing databases would
  retain the old shape.
- Deleting or recreating a v1 database was rejected because it would destroy durable bootstrap
  evidence and violate the fail-closed PoC contract.

## Verification required before implementation completion

- cross-bootstrap/candidate same-run exclusion and different-run independence;
- different-run concurrent transaction attempts serialize on the one shared SQLite writer;
- fresh-v2 schema identity, strict constraints and foreign keys;
- exact v1-to-v2 migration with byte-equivalent bootstrap-row preservation;
- unsupported upgrade/downgrade failure without destructive fallback;
- one-transaction unit plus manifest insertion and failed-`endTransaction` non-commit behavior;
- Android compilation only as source evidence, with device/runtime claims explicitly withheld.
