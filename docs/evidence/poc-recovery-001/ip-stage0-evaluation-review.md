# POC-RECOVERY-001 exact Stage 0 evaluation package review

Status: **AUTHENTICITY PENDING — OWNER-REMEDIATED PROTOCOL v0.3 RE-REVIEW BLOCKED**\
Prepared: 12 August 2026\
Evaluation candidate: `com.google.crypto.tink:tink-android:1.23.0`\
Execution allowed: **no**

## Review conclusion

The supply-chain packet is more exact but remains `AUTHENTICITY_PENDING`; it is not approved for
evaluation execution, dependency admission, redistribution or production. Protocol v0.2 at
reviewed commit `70cf26125dbecbb347311ca0bb9ce1ad5c637e18` received `CHANGES_REQUIRED` findings
F-01–F-06; owner-remediated protocol v0.3 requires repeat review. This document does not identify a
sanitized prior reviewer or claim formal independence for Codex.

The exact root JAR/POM and seven external transitive coordinates are hashed and inventoried. The
root is a Java JAR with no native/JNI entries and contains a shaded protobuf-java 4.33.6 runtime.
All eight external POMs declare Apache-2.0; the embedded protobuf runtime uses BSD-3-Clause. Exact
per-coordinate license text, copyright, NOTICE, signer and source-correspondence records are in
`dependency-ip-authenticity-v0.3.json`. All 16 publisher checksums matched and all 16 detached
OpenPGP signatures verified cryptographically. AndroidX and Error Prone have authoritative
upstream signer trust; six coordinates remain pending because keyserver identity alone is not an
upstream trust binding and/or exact source correspondence is unresolved.

This absence does not waive future distribution obligations. If a dependency is ever admitted,
the resolved APK graph must be rescanned and the applicable full license texts/attributions must be
provided in the distribution and license surface. Production Legal is unassigned.

## Artifact and composition result

| Item | Exact result |
|---|---|
| Tink release | `v1.23.0`, commit `1bedd75ae7161017c5f45b020395a72bbd40645d` |
| Root JAR | 3,320,451 bytes; SHA-256 `c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e` |
| Root POM | 4,083 bytes; SHA-256 `a2d27e7207e6a25764859b62924fc7b972f41884ce272cead9b946c15a1f410f` |
| Published external closure | 8 coordinates including root |
| Root class entries | 1,878 |
| Shaded protobuf | 4.33.6; 540 entries; BSD-3-Clause |
| Native/JNI/shared libraries | none in root or inventoried external JARs |
| Publisher checksums | 16/16 JAR/POM checksum files matched |
| Detached OpenPGP signatures | 16/16 cryptographically verified with BouncyCastle 1.84 |
| Upstream signer trust | 2/8 confirmed; 6/8 `AUTHENTICITY_PENDING` |
| Gradle/runtime graph | not created; prohibited in this task |

Because the repository pins Kotlin 2.2.10 while AndroidX annotation metadata declares Kotlin
stdlib 1.7.10, the future recovery configuration may resolve a different Kotlin version than the
publisher POM closure. This is not silently normalized. A future exact lock and delta review is a
pre-execution blocker.

## Security snapshot

The 12 August 2026 GitHub Advisory Database exact-version queries returned no published advisory
matching any of the eight exact Maven package versions. Relevant history remains visible:

- Tink generic coordinate `CVE-2020-8929` / `GHSA-g5vf-v6wf-7w2r` affects `<1.5.0`;
- Gson `CVE-2022-25647` affects `<2.8.9`;
- Kotlin stdlib `CVE-2020-29582` affects `<=1.4.20`; and
- Kotlin stdlib `CVE-2022-24329` affects `<=1.5.32`.

The exact packaged versions are outside those recorded ranges. Upstream releases also document an
old Streaming AEAD segment-counter overflow fix, an HKDF direct-buffer memory fix and an Android
Keystore large-input fix, all predating v1.23.0. These checks reduce known-version uncertainty; an
empty query is not proof of security and does not approve the recovery protocol.

## Owner-remediated crypto/protocol design remains verification-blocking

Protocol v0.3 selects, but does not implement or prove:

1. public AES-GCM-HKDF `StreamingAead` with 16-byte input/derived key, SHA-256, 4096-byte segments,
   one derived AES key per stream and `DURABLE_ONE_SEGMENT_LOOKAHEAD`;
2. exact 4056/4080 read accounting, `q`/`R` equations, exception-buffer discard and authenticated
   `read()==-1` EOF semantics;
3. `AES256_GCM_TINK_IV12_TAG16` five-second microfiles and exact
   `DORA_RECOVERY_MANIFEST_V1_BINARY_BE`;
4. four exact big-endian/LP16 AAD schemas, the non-deprecated Keystore Builder access path,
   key-confirmation ciphertext and five-level error precedence;
5. exact 9/13/21 publication sequences, `final + ".tmp"` namespace, five reconciliation states,
   collision/no-overwrite and all final/temp containment/lstat/regular/no-symlink checks;
6. successful SQLite `endTransaction()` return as semantic commit, WAL/FULL SQLite, deterministic
   UNIQUE processing intents and controller-ledger rollback scope; and
7. candidate-specific K01–K12 barriers and the 33-row matrix including exact KEY-01–KEY-07.

Each selected item has status `DESIGN_SELECTED_IMPLEMENTATION_VERIFICATION_REQUIRED`. A distinct
accountable recovery Engineering/Security reviewer must verify or revise the exact v0.3 contract
and later implementation evidence before execution.

## SQLite provenance boundary

The future journal may use only platform `android.database.sqlite` for PoC-local split-brain
tests. The API 36 Google APIs x86_64 r07 emulator archive is pinned by official SHA-1 and computed
SHA-256 and reuses only the existing immutable system-image provenance. Physical D2 has exact
sanitized firmware identity, but fresh emulator and D2 runtime `sqlite_version()`,
`sqlite_source_id()`, canonical compile-options digest and effective
WAL/FULL/zero-autocheckpoint/foreign-key values are not recorded. D1/D5 are unavailable. This
recovery-only platform boundary does not admit a production schema or component.

## Requested reviewer dispositions

| Reviewer | Requested disposition | Current state |
|---|---|---|
| Project owner / Stage 0 Product/IP | close or reject each authenticity/source path, then approve/reject the exact v0.3 package | assigned; authenticity blocked; final approval fields null |
| Distinct accountable recovery Engineering/Security | verify, reject or revise selected v0.3 construction/key/commit/recovery/barrier/fault protocol; later verify implementation evidence | unassigned, blocking; current Codex remediation not claimed independent |
| Project owner / execution | after all other prerequisites, separately set `executionAllowed=true` for a named phase/commit | withheld |
| Production Legal | production/redistribution assessment | unassigned; not required for package preparation, required before production |
| Production Security | production admission assessment | separate future gate; not replaced |

Until those dispositions and later implementation/preflight evidence exist,
`tools/check_poc_recovery_run_readiness.py` must fail closed.
