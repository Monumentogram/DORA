# POC-RECOVERY-001 governance remediation v0.6

Source reviewed commit: `eca48ba62acd79007884710395cc40ea21a02611`\
Active Gate Set: `poc-recovery-stage0-v0.6`\
Active protocol: `poc-recovery-protocol-stage0-v0.6`\
Scope: governance documents, evidence and static validators only\
`formalReviewer=false`; `implementationAllowed=false`; `executionAllowed=false`

The recorded advisory review was performed by GPT-5.6 Sol, OpenAI, in the role of AI documentary
advisory reviewer on 12 August 2026. Its disposition was `CHANGES_REQUIRED`. It is non-formal
evidence and does not close `REC-RDY-02` or assign an accountable reviewer.

| Finding | Severity | Disposition | Evidence |
|---|---:|---|---|
| `REC-REV-20260812-01` ambiguous inherited KEY-04 oracle | P1 | `CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE` | Gate Set §4–5; protocol `faultCampaign.activeEffectiveFaultMatrixV06` and `effectiveKey04Routing` |
| `REC-REV-20260812-02` accountable Engineering/Security reviewer unassigned | P0 governance | `OPEN_BLOCKING` | `readiness.json`, `review-roles.json` and the independent review task |

The machine-readable ledger is
`docs/evidence/poc-recovery-001/review-findings-v0.5.json`.

## Effective KEY-04 correction

Protocol v0.6 leaves the immutable v0.3 row untouched and replaces only its effective active
semantics. `KEY-04` now requires a durable run row and final confirmation; matching confirmation
path/type/recorded ciphertext length/SHA-256; an existing usable alias reached through the approved
Builder/`getAead` path; exact active-protocol confirmation AAD; controller replacement of the
underlying alias key with another valid AEAD key before recovery while retaining the prior
ciphertext bytes and recorded identity; no recovery-created/replaced key; and an
`Aead.decrypt(existingConfirmationCiphertext, exactAad)` authentication/AAD failure.

Its only result is `KEY_UNAVAILABLE_KEY_MISMATCH`. Successful decrypt and every post-decrypt
plaintext/parser/identity mismatch are forbidden `KEY-04` interpretations. The latter remains
`KCF-07` and returns `CORRUPT_KEY_CONFIRMATION`. Stored ciphertext path/type/length/hash failures
remain pre-decrypt `CORRUPT_KEY_CONFIRMATION`; missing/invalidated/unusable aliases remain
`KEY_UNAVAILABLE`; and a structurally valid later envelope authentication failure remains
`KEY_ENVELOPE_AUTH_FAILURE`.

## Immutable history and active matrix

All 15 v0.1–v0.5 Gate Set Markdown/Gate JSON/protocol JSON artifacts are SHA-256 pinned in the
active Gate Set and evidence index. They remain byte-identical, superseded and non-executable. The
active matrix contains exactly 46 unique effective IDs and exactly one `KEY-04`; no historical row
is counted as an additional active row.

Counts remain unchanged: Phase A is 184 injections, the full physical D1/D2/D5 profile is 138
injections, and the hard-kill campaign remains 120 attempts per candidate under a separate
denominator. D1/D5 are deferred and Phase A cannot PASS.

## Fail-closed outcome

The accountable recovery Engineering/Security reviewer remains unassigned. Future actual graph,
package and release-R8 evidence plus scoped Product/IP disposition remain open. Production Legal
and Production Security remain pending. No dependency, runtime wiring, production `:app` change,
`:poc:recovery`, harness, recovery/device test, kill campaign, benchmark or measured execution was
added or run.
