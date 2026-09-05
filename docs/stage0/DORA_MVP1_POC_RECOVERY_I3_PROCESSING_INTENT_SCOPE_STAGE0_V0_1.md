# Dora MVP 1 — REC-I3 processing-intent contract addition

Scope ID: `rec-i3-processing-intent-contract-stage0-v0.1`  
Date: 5 September 2026  
Authority: `OWNER-AUTH-BATCH-20260819-01` / OD-15  
Predecessor: `3adacb7c2a52dba53604e7dde63bfad1feea96a3` / tree `072b9dc9abc65a2889c02f8c56142fd07d79f6ee`

The authorized sequential microfile slice requires the exact inherited v0.3 processing-intent
identity at `MICRO-P19`, but the frozen REC-I1 sources contain no implementation of that encoding.
This additive scope permits one new contract file,
`android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryProcessingIntent.kt`,
and its focused test. No existing REC-I1 source may change.

The implementation is exactly SHA-256 over
`LP16(protocolId) || LP16(candidateId) || runId[16] || U32BE(unitIndex) ||
U64BE(plaintextStartInclusive) || U64BE(plaintextEndExclusive) || ciphertextSha256[32]`.
It binds the active protocol ID, `REC-MICROFILE-TINK`, canonical typed run identity, concrete U32 unit
index, valid non-empty bounded plaintext range and exact ciphertext digest. Tests require golden
encoded bytes, an independently calculated SHA-256 result and sensitivity to every variable field.

This addition serves the PoC-only journal identity. It changes no production/admission, execution,
measurement or readiness claim; all ten blockers and `fullRecI3Completed=false` remain unchanged.
