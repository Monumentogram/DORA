# Dora MVP 1 — POC-RECOVERY-001 Gate Set `stage0-v0.4`

Status: **Prospective governance-only amendment; implementation and execution blocked**
Decision: Proposed `DEC-044`, owner constraints `OD-14`
Machine Gate Set: `docs/stage0/poc-recovery-gate-set-stage0-v0.4.json`
Machine protocol: `docs/stage0/poc-recovery-protocol-stage0-v0.4.json`
Reviewed v0.3 commit: `c61603d30c01c72347aa205c247729ad534c2882`
`executionAllowed=false`; `implementationAllowed=false`

## 1. Version and authority boundary

This amendment closes final advisory findings `REC-GOV-V03-001` through
`REC-GOV-V03-004`. It does not add a dependency, create `:poc:recovery`, implement a harness,
change production `:app`, run recovery/device/kill/benchmark work, admit a dependency, or authorize
implementation or execution.

Protocol v0.4 inherits every unchanged v0.3 semantic by exact immutable reference. The v0.3
protocol SHA-256 is
`376c6bec9d6632ff0824465ee890f953445c0843716b8a1b3a044f322d03a0c9`.
The v0.1, v0.2 and v0.3 Markdown, Gate Set and protocol files are unchanged superseded,
non-executable audit artifacts. Their nine exact SHA-256 values are recorded in the v0.4 machine
Gate Set and `evidence-index.json`. A digest mismatch blocks validation.

Codex prepared this remediation but is not a formal accountable reviewer. The accountable recovery
Engineering/Security reviewer, Product/IP reviewer for a future actual graph, Production Legal and
Production Security remain unassigned where recorded as `null`. A later owner record is required
for implementation scope and a separate later owner record is required for execution.

## 2. Durable run-key confirmation

### 2.1 Ninth file family and no-overwrite rule

The ninth and only new file family is:

| Identity | Exact relative name |
|---|---|
| final | `key-confirmation/run.kc` |
| mapped temp | `key-confirmation/run.kc.tmp` |

Both names are part of the final/temp allowlist and receive the same canonical-containment, `lstat`,
regular-file, no-symlink, exclusive-create and final-collision checks as the eight inherited file
families. New objects use `O_CREAT|O_EXCL|O_WRONLY|O_CLOEXEC`. An existing temp or final is never
overwritten. Recovery never promotes a temp by name.

### 2.2 Exact bounded binary plaintext and AAD

All integers are unsigned big-endian. `LP16(x)` is `u16be(byteLength)` followed by exactly that many
UTF-8 bytes. `runId` is the 16 RFC 4122 network-order UUID bytes. The canonical alias is exactly
`android-keystore://dora.poc.recovery.v1.<lowercase-run-uuid>`, 76 ASCII/UTF-8 bytes. Its digest is
the 32 raw bytes of SHA-256 over those exact alias bytes.

Plaintext schema `DORA_RECOVERY_KEY_CONFIRMATION_PLAINTEXT_V1_BINARY_BE`:

```text
ASCII "DORAKC01" [8]
|| u16be(1)
|| LP16(protocolId)
|| LP16(candidateId)
|| runId [16]
|| canonicalAliasSha256 [32]
```

AAD schema `DORA_RECOVERY_KEY_CONFIRMATION_AAD_V1_BINARY_BE`:

```text
ASCII "DORAKA01" [8]
|| u16be(1)
|| LP16(protocolId)
|| LP16(candidateId)
|| runId [16]
|| canonicalAliasSha256 [32]
```

`protocolId` is the exact active ASCII protocol ID and is capped at 96 UTF-8 bytes. `candidateId`
is exactly `REC-STREAM-TINK` or `REC-MICROFILE-TINK` and is capped at 64 UTF-8 bytes. Each encoding
is capped at 222 bytes. Wrong magic/schema, over-cap value, noncanonical identifier, cross-run or
cross-candidate value and every trailing byte are rejected. These separate magic values make the
decrypted plaintext a deterministic known value and domain-separate it from AAD.

### 2.3 Exact 13-step new-run bootstrap

The key-confirmation ciphertext is created immediately after the new run alias and before any
encrypted keyset, candidate ciphertext, checkpoint or manifest:

1. `KC-01`: prove the canonical alias and key-reference namespace absent; otherwise
   `KEY_REF_COLLISION`.
2. `KC-02`: call `AndroidKeystoreKmsClient.generateNewAeadKey(alias)`.
3. `KC-03`: obtain the AEAD only through
   `new AndroidKeystoreKmsClient.Builder().setKeyUri(alias).build().getAead(alias)`.
4. `KC-04`: encode the exact plaintext/AAD and call `Aead.encrypt(plaintext, aad)`.
5. `KC-05`: exclusive-create `key-confirmation/run.kc.tmp`; an existing temp or final blocks.
6. `KC-06`: write the complete ciphertext.
7. `KC-07`: `fsync` the temp descriptor.
8. `KC-08`: collision-check and rename temp to final without overwrite.
9. `KC-09`: `fsync` the `key-confirmation` parent directory.
10. `KC-10`: write the SQLite run row with exact relative name, byte length, SHA-256, canonical
    alias SHA-256 and confirmation state.
11. `KC-11`: mark the non-exclusive SQLite transaction successful.
12. `KC-12`: successful `endTransaction()` return is the durable bootstrap semantic commit.
13. `KC-13`: only now may the controller event be emitted and any inherited 9/13/21 candidate
    publication sequence start. The event is not part of semantic commit.

The required SQLite run-row fields are `runId`, `candidateId`,
`keyConfirmationRelativeName`, `keyConfirmationBytes`, `keyConfirmationSha256`,
`canonicalAliasSha256` and `keyConfirmationState`. Abstract file identity is forbidden.

Every inherited `K01`–`K12` barrier has one new common prerequisite: `KC-12` returned successfully
and the final/row identities validate. This does not change the 120-attempt hard-kill denominator or
the inherited candidate-specific barrier markers.

### 2.4 v0.4 key taxonomy and reconciliation

Classification is singular and ordered:

1. occupied alias/reference/temp/final during new-run bootstrap → `KEY_REF_COLLISION`;
2. alias exists with no confirmation and no durable row → `INCOMPLETE_KEY_BOOTSTRAP`;
3. durable row points to missing final → `KEY_CONFIRMATION_MISSING`;
4. length/hash/parser/magic/schema/trailing mismatch → `CORRUPT_KEY_CONFIRMATION` before decrypt;
5. bytes/hash parse but decrypt, AAD or exact known plaintext fails →
   `KEY_UNAVAILABLE_KEY_MISMATCH`;
6. valid confirmation followed by missing later alias/reference/envelope → `KEY_UNAVAILABLE`;
7. valid confirmation followed by malformed later envelope → `CORRUPT_KEY_ENVELOPE`;
8. valid confirmation followed by later envelope authentication failure →
   `KEY_ENVELOPE_AUTH_FAILURE`.

An alias with neither confirmation nor durable row is an incomplete bootstrap, is not
execution-eligible and is never replaced. A temp-only confirmation is quarantined through the
durable quarantine transaction. A final without a row is an authenticated-orphan candidate: verify
it fail-closed, never infer commit, then quarantine it. A row whose final is missing is split-brain
`KEY_CONFIRMATION_MISSING`. Confirmation length/hash/parser failure is
`CORRUPT_KEY_CONFIRMATION`; valid bytes that cannot prove the expected alias/run/candidate are
`KEY_UNAVAILABLE_KEY_MISMATCH`.

The confirmation moves with run evidence during quarantine. Quarantine order remains SQLite intent
commit → rename without overwrite → source-directory `fsync` → destination-directory `fsync` →
SQLite completion commit. Silent deletion and alias deletion during reconciliation are forbidden.
Test-alias deletion is permitted only after explicit retention and an auditable cleanup receipt; it
never licenses replacement during recovery.

### 2.5 Added mandatory fault rows

The 33 inherited v0.3 rows remain. v0.4 adds 12 rows: six bootstrap kill points (`KCB-01` through
`KCB-06`) at alias-before-temp, temp-write-before-fsync, fsync-before-rename,
rename-before-directory-fsync, directory-fsync-before-SQLite-commit and
commit-before-controller-event; and six confirmation faults (`KCF-01` through `KCF-06`) for missing,
bit-flipped, truncated, cross-run/candidate swap, alias replacement with unchanged confirmation and
temp/final collision.

Every one of the 45 rows uses exactly three pinned API 36 x86_64 emulator repetitions and one
physical D2 repetition: 135 emulator + 45 D2 = 180 mandatory injections, separate from the hard-kill
denominator. This is a prospective test contract only; no such execution is authorized here.

## 3. Immutable JetBrains LICENSE/NOTICE evidence

For `org.jetbrains:annotations:13.0`, exact historical-source commit
`f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c` supplies:

- `LICENSE.txt`: immutable locator
  `https://github.com/JetBrains/intellij-community/blob/f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c/LICENSE.txt`,
  SHA-256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`;
- `NOTICE.txt`: immutable locator
  `https://github.com/JetBrains/intellij-community/blob/f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c/NOTICE.txt`,
  SHA-256 `0479f6a86003002dec1da1667f2f8320253c7225c6ffffc05cf7e0988bd8c72c`.

The bytes were independently retrieved and hashed at `2026-08-12T14:38:33Z` using `gh api` GitHub
Contents API plus `System.Security.Cryptography.SHA256`; lengths were 11,358 and 127 bytes. Mutable
branch locators and `PENDING` evidence values are forbidden by the static verifiers.

Project-owner / Stage 0 Product/IP disposition is limited to the exact governance packet: the
JetBrains annotations license/notice evidence is accepted for Stage 0 evaluation-package review;
the applicable LICENSE and immutable upstream requirement to preserve NOTICE are recorded. If the
artifact later enters a separately approved resolved graph, that NOTICE must be preserved in the
future Stage 0 notices packet. This is not Production Legal, production admission, dependency
admission or redistribution approval. Historical `F-06` is treated as closed only after this
immutable verification.

## 4. Exact `REC-JSR305-EXCLUDE-001` boundary

Current facts are deliberately narrow:

- `tink-android:1.23.0` is not wired;
- `:poc:recovery` does not exist;
- PR #11 did not add or change lockfiles;
- existing base lockfiles of other modules contain Tink/JSR-305 through tooling, lint, UTP,
  androidTest or other test paths.

No repository-wide Gradle-graph absence is claimed. The current base inventory is context, not
recovery admission evidence.

After separate implementation authorization, `REC-JSR305-EXCLUDE-001` covers only the future
`:poc:recovery` module: every resolvable compile, runtime, unit-test, `androidTest`, benchmark,
release and packaging/runtime-artifact input, plus that module's dependency locks and dependency
verification metadata. It excludes buildscript/AGP/UTP/lint/tooling paths of other existing modules
and existing app/capture/search lockfiles.

The future root declaration must be exact `com.google.crypto.tink:tink-android:1.23.0` with a
Tink-local exclusion of `com.google.code.findbugs:jsr305:3.0.2`; the covered graph must resolve zero
JSR-305 components and package zero `javax.annotation` class definitions from that artifact. R8 may
contain exactly:

```text
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn javax.annotation.concurrent.ThreadSafe
```

No broader rule is permitted and release R8 must report no unresolved missing class. Any JSR-305
occurrence in recovery scope blocks readiness; unrelated pre-existing tooling occurrences alone do
not. The independent `javax.lang.model.element.Modifier` observation remains a future real-graph
condition and receives no broad suppression.

## 5. Three separate approval/readiness states

1. Prospective `REC-JSR305-EXCLUDE-001` policy: **CLOSED / APPROVED**.
2. Exact governance packet authenticity and LICENSE/NOTICE evidence: **CLOSED / VERIFIED**. The
   excluded JSR-305 artifact's conflicting terms are not interpreted and its use/distribution is
   not approved.
3. Future actual recovery graph/package/R8 evidence and its scoped Product/IP disposition:
   **OPEN / BLOCKED** until separately authorized implementation produces exact evidence.

`REC-RDY-11` records all three substates. The readiness checker never asks anyone to approve use of
the excluded JSR-305 artifact; it requires proven absence inside the exact recovery boundary and a
separate Product/IP disposition of the future actual graph. `executionAllowed=false`,
`implementationAllowed=false`, `approvedReviewer=null`, the formal accountable Engineering/Security
reviewer is unassigned, Production Legal/Security remain pending and D1/D5 remain deferred.

## 6. Validation and next safe action

Static governance, dependency/IP, immutable-license, base-lockfile and readiness validators are
mandatory. The readiness checker must return `BLOCKED`. Normal repository non-metric verification
and CI remain required. Recovery/device/kill/benchmark execution is forbidden.

The next safe action after this remediation is a read-only review of the exact resulting commit.
Only a later explicit owner scope may authorize implementation; no prerequisite flips either
implementation or execution authority implicitly.
