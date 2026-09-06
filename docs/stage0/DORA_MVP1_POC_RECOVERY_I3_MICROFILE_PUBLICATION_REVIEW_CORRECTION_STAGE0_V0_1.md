# Dora MVP 1 — REC-I3 sequential microfile publication review correction

Correction ID: `rec-i3-sequential-microfile-publication-review-correction-stage0-v0.1`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / OD-15\
Implementation checkpoint: `2621242bdc5691768f14b128cbf7cf238d336ea0` / tree `d4088f967e5745c1f086244c72236f68ae36d0d0`

This additive correction closes two bounded publication-model omissions found during independent
review. It preserves the active v0.3 protocol inherited by v0.6, the existing unified PoC journal
v2 decision, and every claim ceiling in the predecessor scope.

1. Every manifest publication row carries the explicit typed `MANIFEST` publication kind required
   by the active protocol. The Kotlin row model, SQLite column, strict `CHECK`, Android write and
   readback paths, continuation validation, host-SQLite verifier and focused tests must all retain
   that exact value. Because v2 is an unmerged candidate schema, this correction changes the v2
   creation and v1-to-v2 migration DDL in place; it does not introduce a v2-to-v3 migration.
2. A publication call accepts only non-empty plaintext whose length is no greater than
   `cadenceSeconds * 32000` bytes: 160000 for 5 seconds, 480000 for 15 seconds, and 960000 for 30
   seconds. Every stored unit range must satisfy the same exact width bound for its stored cadence.
   The controller validates both new input and prior rows before opening the run alias or mutating
   crypto, filesystem or SQLite state. SQLite enforces the stored-row relation as a second boundary.
3. Five seconds remains the only PASS-eligible cadence. The 15- and 30-second variants remain
   observation or post-failure fallback inputs with `passEligible=false`; their larger bounded
   calls do not acquire a tail-loss PASS claim. Full-unit scheduling, terminal short-unit meaning,
   capture timing and campaign validity remain outside this low-level writer slice. This correction
   therefore enforces the frozen byte-rate/cadence maximum without inventing a caller-controlled
   terminal flag or a timing claim.

Behavioral tests must first fail against the implementation checkpoint because the publication-row
model lacks `publicationKind` and because an over-cadence input/prior row reaches alias access.
Green evidence must cover exact limits, one-byte oversize rejection before alias access, strict
`MANIFEST` persistence/readback, and host SQLite rejection of wrong kind and oversized stored
ranges. All ten readiness blockers remain open, `fullRecI3Completed=false`, and no device,
emulator, preflight, fault campaign, measurement, PASS, production schema admission or merge is
claimed.
