# Dora MVP 1 — POC-RECOVERY-001 prospective Gate Set v0.6

Gate Set: `poc-recovery-stage0-v0.6`\
Protocol: `poc-recovery-protocol-stage0-v0.6`\
Date: 12 August 2026\
Status: **Proposed governance-only KEY-04 oracle correction; implementation and execution blocked**\
Decision context: Proposed `DEC-044`; owner constraints `OD-14`

## 1. Scope and authority

This Gate Set closes documentary finding `REC-REV-20260812-01` by replacing only the effective
semantics of inherited fault row `KEY-04`. It does not edit historical protocol v0.3 or any other
v0.1–v0.5 artifact. All unchanged v0.5 semantics are incorporated by the immutable protocol and
Gate Set hashes in section 3.

This document and its JSON contracts are prospective governance only:

- `implementationAllowed=false`;
- `executionAllowed=false`;
- no dependency or production admission;
- no runtime dependency, `:poc:recovery` module or harness exists;
- no device test, kill campaign, benchmark or measured execution is authorized;
- future actual recovery graph/package/R8 evidence and Product/IP disposition remain pending;
- accountable recovery Engineering/Security review remains unassigned;
- Production Legal and Production Security remain pending;
- D1 and D5 remain deferred.

No AI system, Codex or GPT model is assigned as the accountable Engineering/Security reviewer.

## 2. Normative files and inheritance

The only active contracts are:

- this Markdown Gate Set;
- `docs/stage0/poc-recovery-gate-set-stage0-v0.6.json`; and
- `docs/stage0/poc-recovery-protocol-stage0-v0.6.json`.

The v0.6 Gate Set inherits the exact v0.5 Gate JSON with SHA-256
`3d1051c85076dda1d8b0c20812c08b7343a7c611ad8c6381d9e80d41724bef93`. The v0.6 protocol
inherits the exact v0.5 protocol JSON with SHA-256
`9b469562aa8deff2f94402b6fe5093fb832d76ec48f5ef0fd081b76b322c3e9c`. The sole semantic
override is the active effective `KEY-04` row in section 4. Active identity, evidence metadata and
the materialized 46-row effective matrix are supporting governance changes.

If an unchanged v0.5 rule conflicts with this exact `KEY-04` override, the override governs only
`KEY-04`. All other v0.5 rules remain intact.

## 3. Immutable superseded audit artifacts

The following 15 historical Markdown/Gate JSON/protocol JSON files are byte-identical superseded
audit artifacts. They are non-executable and cannot contribute additional rows to the active v0.6
matrix.

| Version | Artifact | SHA-256 |
|---|---|---|
| v0.1 | `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md` | `d891e033e3e58455dbafd03be5a41ca64cafda93182424357035c37d769ae46e` |
| v0.1 | `poc-recovery-gate-set-stage0-v0.1.json` | `78c1a8289f90b51a376b023673dc00b6cb35386b5b0a2dda9432b50b20216e11` |
| v0.1 | `poc-recovery-protocol-stage0-v0.1.json` | `b853295e6c66815c61566e930d30dafa0dfc72e805bb5ba38158688e084ead81` |
| v0.2 | `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_2.md` | `d4fab2f47872f0b6c1c04c5b0b1022b047ae8782eb0130cd2f66825294455180` |
| v0.2 | `poc-recovery-gate-set-stage0-v0.2.json` | `f6384c7b1d4d493218a600722ddf0116f454e8356e7e247da74f03256cc69110` |
| v0.2 | `poc-recovery-protocol-stage0-v0.2.json` | `cfa06e624cbc0da37b68188d7b1739cdfb5ca12beeedc21f408897dc41b2081f` |
| v0.3 | `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_3.md` | `7d24e5aa0c2dd0c65ef8def12e687d39f5d0bfc30a222be51f29bffd02c772a9` |
| v0.3 | `poc-recovery-gate-set-stage0-v0.3.json` | `25a05a4d136f90e6b62005943585a27161517ea337573b71b5b1aaeca16bb80f` |
| v0.3 | `poc-recovery-protocol-stage0-v0.3.json` | `376c6bec9d6632ff0824465ee890f953445c0843716b8a1b3a044f322d03a0c9` |
| v0.4 | `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_4.md` | `03521bce76d123d463c86980f1db10b43667b39cea5114810977c8d4940dad0f` |
| v0.4 | `poc-recovery-gate-set-stage0-v0.4.json` | `f89d5dff7bcfdcc7f96efd4d1c195b0054e262976db3b722b384c50e4440804c` |
| v0.4 | `poc-recovery-protocol-stage0-v0.4.json` | `cfe9d19e7b0e409c1be6a33c4cd240ebdc03e014f0b1abe1b0776aae1ede5eaa` |
| v0.5 | `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_5.md` | `ce4980468bfeb7bddadfa58b3ba71b702de4562cc8e264908d19f92fa7638f9c` |
| v0.5 | `poc-recovery-gate-set-stage0-v0.5.json` | `3d1051c85076dda1d8b0c20812c08b7343a7c611ad8c6381d9e80d41724bef93` |
| v0.5 | `poc-recovery-protocol-stage0-v0.5.json` | `9b469562aa8deff2f94402b6fe5093fb832d76ec48f5ef0fd081b76b322c3e9c` |

The historical v0.3 `KEY-04` row remains unchanged inside its immutable audit artifact. It is not an
additional active row and its ambiguous inherited wording is not an allowed v0.6 oracle.

## 4. Effective KEY-04

The active v0.6 matrix contains exactly one effective row with ID `KEY-04`. That row replaces the
effective meaning of the historical row without altering historical bytes.

All eight preconditions are mandatory:

1. A durable run row exists.
2. The key-confirmation final exists.
3. The key-confirmation path, type, recorded ciphertext length and recorded ciphertext SHA-256
   fully match.
4. The Android Keystore alias exists and is available through the approved Builder/`getAead` path.
5. The exact confirmation AAD is computed under the active protocol.
6. Before recovery, the fault controller replaces the underlying alias key with another valid AEAD
   key while preserving the previous confirmation ciphertext bytes and their recorded length and
   SHA-256.
7. Recovery does not create or replace the key.
8. `Aead.decrypt(existingConfirmationCiphertext, exactAad)` terminates with an
   authentication/AAD failure.

The only expected classification is:

`KEY_UNAVAILABLE_KEY_MISMATCH`

`KEY-04` allows no alternative expected result. In particular, these interpretations are forbidden:

- successful decrypt;
- malformed decrypted plaintext;
- wrong magic, schema, `protocolId`, `candidateId`, `runId` or `canonicalAliasSha256` after a
  successful decrypt;
- ciphertext path, type, recorded length or recorded SHA-256 corruption; and
- an absent, invalidated or otherwise unusable alias.

## 5. Exact routing outside KEY-04

The active v0.6 taxonomy routes neighboring failures as follows:

- successful confirmation decrypt followed by malformed plaintext or a wrong magic/schema/
  `protocolId`/`candidateId`/`runId`/`canonicalAliasSha256` is `KCF-07` and returns
  `CORRUPT_KEY_CONFIRMATION`;
- confirmation ciphertext path/type/recorded length/recorded SHA-256 mismatch returns
  `CORRUPT_KEY_CONFIRMATION` before decrypt, and decrypt is forbidden;
- a missing, invalidated or unusable alias returns `KEY_UNAVAILABLE`;
- after a fully valid confirmation, a structurally valid later key envelope whose AEAD/AAD/tag
  verification fails returns `KEY_ENVELOPE_AUTH_FAILURE`.

The v0.5 `KCF-07` behavior is preserved. `KCF-07` requires a successful decrypt and then a
post-decrypt malformed/wrong-identity plaintext failure; it is not a `KEY-04` subtype.

## 6. Active matrix and campaign counts

The machine protocol materializes one active effective matrix with exactly 46 unique IDs. It
contains `KEY-04` exactly once. Historical rows are sources/audit evidence, not extra active rows.

Counts are unchanged:

- mandatory fault rows: **46**;
- Phase A: **46 × (3 pinned emulator + 1 D2) = 184 injections**;
- full physical: **46 × (D1 + D2 + D5) = 138 injections**;
- D1 and D5 remain deferred;
- Phase A permits only `FAIL` or `INCONCLUSIVE`; PASS remains forbidden;
- full PASS requires the complete D1/D2/D5 profile;
- hard-kill campaign: **120 attempts per candidate**, with its own denominator separate from fault
  injections.

No repetition, validity criterion or failure gate is weakened.

## 7. Advisory review evidence and findings

The received review is recorded as non-formal evidence:

| Field | Value |
|---|---|
| Reviewer | GPT-5.6 Sol |
| Organization | OpenAI |
| Role | AI documentary advisory reviewer |
| Review date | 2026-08-12 |
| Reviewed commit | `eca48ba62acd79007884710395cc40ea21a02611` |
| `formalReviewer` | `false` |
| Disposition | `CHANGES_REQUIRED` |

Findings ledger:

- `REC-REV-20260812-01` — P1 — ambiguous inherited KEY-04 oracle —
  `CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE`;
- `REC-REV-20260812-02` — P0 governance — accountable Engineering/Security reviewer unassigned —
  `OPEN_BLOCKING`.

This AI documentary review does not close `REC-RDY-02` and is not formal accountable review.

## 8. Remaining gates

The canonical `REC-RDY-01..11` blockers remain fail-closed. In particular:

- a distinct accountable recovery Engineering/Security reviewer must review the exact v0.6 HEAD;
- separately scoped owner authorization is required before implementation;
- the actual recovery-only dependency graph/package/release-R8 evidence and its Product/IP
  disposition remain future work;
- emulator and D2 SQLite/Keystore/filesystem preflight remains pending;
- the isolated harness remains absent;
- a separate owner execution authorization remains required;
- D1/D5 evidence remains deferred; and
- Production Legal and Production Security remain separate pending production-admission gates.

This governance correction cannot flip any implementation, execution or production-admission flag.
