# Dora MVP 1 — REC-I3 microfile governance and migration review correction

Correction ID: `rec-i3-microfile-governance-migration-correction-stage0-v0.1`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / OD-15\
Reviewed implementation checkpoint: `2621242bdc5691768f14b128cbf7cf238d336ea0` / tree `d4088f967e5745c1f086244c72236f68ae36d0d0`

This additive correction closes two fail-closed boundary defects found during independent review.
It preserves every frozen REC-I1/REC-I2B and first/bootstrap REC-I3 artifact and does not widen the
sequential microfile implementation scope.

1. The REC-I3 successor validator separates the complete source manifest used by evidence from the
   exact files mutable in the sequential-microfile slice. Only the bootstrap controller and Android
   bootstrap journal shared-boundary edits already declared by the scope remain mutable among the
   predecessor bootstrap files. Bootstrap crypto, adapter, storage policy/adapter and historical
   bootstrap tests are pinned byte-for-byte to the independently reviewed `a0348fe1` checkpoint.
   Current filesystem hashes, rather than self-reported historical evidence hashes, are supplied to
   bootstrap evidence validation. Negative tests mutate each frozen family and must fail the exact
   changed-path or current-hash boundary.
2. The v1-to-v2 PoC journal migration accepts only the exact v1 bootstrap table created by the
   reviewed bootstrap slice. Before any v2 DDL, it validates complete `PRAGMA table_info` metadata,
   the canonical table SQL including every type, nullability, primary-key and `CHECK` constraint,
   the absence of foreign keys and triggers, and the exact implicit primary-key index boundary.
   A same-column schema with weakened type, nullability, primary-key or constraint semantics is
   rejected. The framework-owned upgrade transaction remains the sole migration transaction, so a
   rejection leaves the v1 database unchanged and creates no partial v2 objects.

Focused governance mutation tests and the repository host-SQLite verifier must demonstrate these
rejections against real Git state and host SQLite respectively. Android SQLite execution remains
device-preflight evidence. All ten readiness blockers remain open, `fullRecI3Completed=false`, and
no device, emulator, preflight, campaign, measurement, PASS, production migration admission or
merge is claimed.
