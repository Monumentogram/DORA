# Dora MVP 1 — REC-I3 SQLite host verifier addition

Scope ID: `rec-i3-sqlite-host-verifier-stage0-v0.1`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / OD-15

The sequential microfile evidence executes the production schema-v2 DDL and v1-to-v2 statements
against bundled Python `sqlite3`. To make that check reproducible, this additive verification scope
permits `tools/verify_rec_i3_microfile_sqlite.py` and its exact inclusion in the REC-I3 successor
allowlist. The verifier must read DDL from production Kotlin source, use only in-memory synthetic
rows, cover fresh v2, row-preserving migration, restrictive composite foreign keys, constraints
and failed-migration rollback, and print its host SQLite version and exact DDL digest.

This host check is not Android SQLite execution, framework-transaction proof, device preflight,
production schema admission or a Recovery readiness result. All ten blockers,
`fullRecI3Completed=false` and every execution/measurement/admission nonclaim remain unchanged.
