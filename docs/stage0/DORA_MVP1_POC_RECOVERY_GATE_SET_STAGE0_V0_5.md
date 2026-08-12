# DORA MVP1 — POC-RECOVERY-001 prospective Gate Set v0.5

Status: **PROPOSED GOVERNANCE-ONLY CONTRACT — IMPLEMENTATION AND EXECUTION BLOCKED**\
Gate Set: `poc-recovery-stage0-v0.5`\
Protocol: `poc-recovery-protocol-stage0-v0.5`\
Reviewed source commit: `c3eae5c3fbe5cba6a96ad827441cfe4e3f1bfc55`\
Decision linkage: Proposed `DEC-044`; owner constraints `OD-14`\
`implementationAllowed=false`; `executionAllowed=false`

## 1. Scope and inheritance

This is a prospective Stage 0D governance remediation. It adds no dependency, module, harness,
production code or execution evidence. It does not authorize implementation, a device run, a hard-kill
campaign, a benchmark, dependency admission or production admission.

The v0.5 Gate Set and protocol incorporate the exact v0.4 Gate Set/protocol by immutable SHA-256
reference. The protocol overrides only its KEY classification/reconciliation and fault-campaign
profile sections; the Gate Set overrides active metadata, retained history/ledgers, the taxonomy
summary, fault/campaign counts and canonical blocker IDs. Every other v0.4
semantic, repetition, validity rule and failure gate remains unchanged. v0.1–v0.4 are non-executable,
superseded audit artifacts and must remain byte-identical:

| Version | Markdown SHA-256 | Gate JSON SHA-256 | Protocol JSON SHA-256 |
|---|---|---|---|
| v0.1 | `d891e033e3e58455dbafd03be5a41ca64cafda93182424357035c37d769ae46e` | `78c1a8289f90b51a376b023673dc00b6cb35386b5b0a2dda9432b50b20216e11` | `b853295e6c66815c61566e930d30dafa0dfc72e805bb5ba38158688e084ead81` |
| v0.2 | `d4fab2f47872f0b6c1c04c5b0b1022b047ae8782eb0130cd2f66825294455180` | `f6384c7b1d4d493218a600722ddf0116f454e8356e7e247da74f03256cc69110` | `cfa06e624cbc0da37b68188d7b1739cdfb5ca12beeedc21f408897dc41b2081f` |
| v0.3 | `7d24e5aa0c2dd0c65ef8def12e687d39f5d0bfc30a222be51f29bffd02c772a9` | `25a05a4d136f90e6b62005943585a27161517ea337573b71b5b1aaeca16bb80f` | `376c6bec9d6632ff0824465ee890f953445c0843716b8a1b3a044f322d03a0c9` |
| v0.4 | `03521bce76d123d463c86980f1db10b43667b39cea5114810977c8d4940dad0f` | `f89d5dff7bcfdcc7f96efd4d1c195b0054e262976db3b722b384c50e4440804c` | `cfe9d19e7b0e409c1be6a33c4cd240ebdc03e014f0b1abe1b0776aae1ede5eaa` |

## 2. Canonical eight-class KEY taxonomy

The taxonomy contains exactly these eight unique classifications, in canonical list order:

1. `KEY_REF_COLLISION`;
2. `INCOMPLETE_KEY_BOOTSTRAP`;
3. `KEY_CONFIRMATION_MISSING`;
4. `CORRUPT_KEY_CONFIRMATION`;
5. `KEY_UNAVAILABLE`;
6. `KEY_UNAVAILABLE_KEY_MISMATCH`;
7. `CORRUPT_KEY_ENVELOPE`;
8. `KEY_ENVELOPE_AUTH_FAILURE`.

No alias or replacement key may be generated during recovery. No outcome may be ambiguous or selected
from more than one row.

### 2.1 New-run creation

Before a new run is created, check the complete alias, mandatory key-reference,
`key-confirmation/run.kc.tmp` and `key-confirmation/run.kc` namespaces. If any namespace is occupied,
classify `KEY_REF_COLLISION`, do not overwrite anything and do not create or publish the run.

### 2.2 Exact recovery/reconciliation algorithm

Execute these checks strictly in order and stop on the first matching row:

| Step | Exact check | Classification and required action |
|---:|---|---|
| 1 | Durable run row is absent, but an alias and/or confirmation temp/final remains after interrupted bootstrap. | `INCOMPLETE_KEY_BOOTSTRAP`. Record the exact absent, temp-only or final-orphan state as subtype/evidence; the classification remains one. Never infer commit or replace the alias. |
| 2 | Durable run row exists, but key-confirmation final is absent. | `KEY_CONFIRMATION_MISSING`; fail closed and retain evidence. |
| 3 | Final exists, but exact path, type, recorded ciphertext length or recorded ciphertext SHA-256 does not match. | `CORRUPT_KEY_CONFIRMATION`. Decrypt is forbidden at this step. |
| 4 | Exact stored ciphertext identity passed, but the Android Keystore alias is absent, invalidated or unusable. | `KEY_UNAVAILABLE`; never create or replace an alias. |
| 5 | Alias is available and exact AAD was computed, but `Aead.decrypt()` returns authentication/AAD failure. | `KEY_UNAVAILABLE_KEY_MISMATCH`; a replacement key is forbidden. |
| 6 | Decrypt succeeded, but the bounded plaintext parser, magic, schema, no-trailing-bytes, `protocolId`, `candidateId`, `runId` or `canonicalAliasSha256` check fails. | `CORRUPT_KEY_CONFIRMATION`. These plaintext checks are post-decrypt only. |
| 7 | Confirmation is fully valid, but a mandatory subsequent key reference or key envelope is absent. | `KEY_UNAVAILABLE`. |
| 8 | A subsequent envelope exists, but its length, SHA-256, encoding or parser validation fails. | `CORRUPT_KEY_ENVELOPE`. |
| 9 | A subsequent envelope is structurally valid, but AEAD/AAD/tag verification fails. | `KEY_ENVELOPE_AUTH_FAILURE`. |

Plaintext magic, schema, parser and trailing-byte validation is never a pre-decrypt check. The only
pre-decrypt confirmation-content identity checks are the exact path/type and recorded ciphertext
length/SHA-256 checks in step 3.

## 3. Fault oracles and denominators

The mandatory matrix contains exactly 46 rows:

- 33 inherited v0.3 rows;
- 12 inherited v0.4 key-confirmation/bootstrap rows (`KCB-01..06`, `KCF-01..06`);
- one v0.5 row, `KCF-07`.

`KCF-07` must encrypt malformed confirmation plaintext with the correct current alias and exact AAD,
then update the recorded outer ciphertext length and SHA-256. Exact stored ciphertext identity and
decrypt must pass. The post-decrypt exact plaintext validation must return
`CORRUPT_KEY_CONFIRMATION`. A replacement key is forbidden.

The v0.5 oracle normalizes inherited bootstrap observations without changing injections:
`KCB-01..05` all classify `INCOMPLETE_KEY_BOOTSTRAP`, while retaining alias-only, temp-only,
absent or final-orphan subtype/evidence; `KCB-06` remains a valid durable bootstrap. `KCF-02/03`
fail at pre-decrypt stored ciphertext identity and forbid decrypt. `KCF-04/05` can return only
`KEY_UNAVAILABLE_KEY_MISMATCH` on `Aead.decrypt()` authentication/AAD failure; they have no
post-decrypt plaintext alternative. `KCF-01` remains `KEY_CONFIRMATION_MISSING`, and `KCF-06`
remains new-run `KEY_REF_COLLISION`.

### 3.1 Phase A profile

For each of the 46 fault rows, execute three repetitions on the pinned API 36 x86_64 emulator and one
repetition on physical D2. Therefore:

- emulator: `46 × 3 = 138` injections;
- physical D2: `46 × 1 = 46` injections;
- `phaseATotalInjections = 46 × 4 = 184`.

Phase A can return only `FAIL` or `INCONCLUSIVE`; `PASS` is forbidden. The 120-attempt hard-kill
campaign per candidate remains a distinct denominator and is not included in 184.

### 3.2 Full physical campaign

For each of the 46 fault rows, execute one repetition on each physical D1, D2 and D5:

- `fullPhysicalTotalInjections = 46 × 3 = 138`.

D1 and D5 remain deferred. Without the complete D1/D2/D5 profile, `PASS` is forbidden.

A Phase A D2 result may be reused as the full-campaign D2 repetition only when all of these are exact
matches: commit; protocol and Gate Set version; fixture digest; injection definition; device identity
and profile; fresh preflight; validity criteria. Otherwise repeat D2. With valid reuse, completing the
physical matrix requires another `46 × 2 = 92` D1/D5 injections.

No inherited repetition, validity criterion or failure gate is weakened.

## 4. Canonical readiness blockers

The Gate Set blocker list and `readiness.json` blocker IDs must be unique and exactly equal in this
order:

1. `REC-RDY-01-PRODUCT-IP-FINAL-APPROVAL`;
2. `REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW`;
3. `REC-RDY-03-STREAMING-IMPLEMENTATION-VERIFICATION`;
4. `REC-RDY-04-MICROFILE-IMPLEMENTATION-VERIFICATION`;
5. `REC-RDY-05-FUTURE-RESOLVED-GRAPH`;
6. `REC-RDY-06-DEVICE-SQLITE-PREFLIGHT`;
7. `REC-RDY-07-HARNESS-ABSENT`;
8. `REC-RDY-08-OWNER-EXECUTION-AUTHORIZATION`;
9. `REC-RDY-09-D1-D5-FULL-VERDICT`;
10. `REC-RDY-10-PRODUCTION-LEGAL-SECURITY`;
11. `REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY`.

Product/IP remains a three-state model: A, prospective policy is `CLOSED/APPROVED`; B, governance
evidence is `CLOSED/VERIFIED`; C, future actual graph/package/R8 evidence and its scoped disposition is
`OPEN/BLOCKED`. State A or B does not close C and does not admit a dependency or production use.

## 5. Fail-closed readiness and review boundary

The distinct accountable recovery Engineering/Security reviewer remains unassigned. Codex is the
package preparer and is not a formal accountable reviewer. Production Legal and Production Security
remain pending. D1/D5 remain deferred. The future actual graph/package/R8 Product/IP disposition is
pending. No runtime dependency, `:poc:recovery`, harness, recovery test, device test, hard-kill run,
benchmark or measured execution exists in this remediation.

Any implementation or execution requires a new, explicit owner scope after repeat exact-HEAD read-only
review and all applicable blockers. This Gate Set cannot flip `implementationAllowed` or
`executionAllowed` implicitly.
