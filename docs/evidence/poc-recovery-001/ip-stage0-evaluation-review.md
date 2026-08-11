# POC-RECOVERY-001 exact Stage 0 evaluation package review

Status: **PACKAGE READY — PRODUCT/IP AND INDEPENDENT ENGINEERING/SECURITY REVIEW PENDING**\
Prepared: 12 August 2026\
Evaluation candidate: `com.google.crypto.tink:tink-android:1.23.0`\
Execution allowed: **no**

## Review conclusion

The evidence packet is sufficiently exact to present for review, but it is not approved for
evaluation execution, dependency admission, redistribution or production. The owner authorized
preparation of this package only.

The exact root JAR/POM and seven external transitive coordinates are hashed and inventoried. The
root is a Java JAR with no native/JNI entries and contains a shaded protobuf-java 4.33.6 runtime.
All eight external POMs declare Apache-2.0; the embedded protobuf runtime uses BSD-3-Clause. No
LICENSE or NOTICE entry was found inside any reviewed JAR, and no NOTICE file exists at the
reviewed Tink or protobuf tagged repository root. The exact tagged license texts and hashes are in
`license-notice-inventory.json`.

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

## Crypto/protocol review remains blocking

The independent reviewer must resolve at least these questions before execution:

1. Can public `StreamingAead` v1.23.0 expose an authenticatable committed prefix after abrupt
   truncation without internal APIs, and how is EOF distinguished from authentication failure?
2. Is the Proposed `AES128_GCM_HKDF_4KB` parameter set appropriate for this experiment, and which
   non-deprecated public construction path freezes those exact parameters?
3. What exact Tink `Aead` template, manifest encoding, generation/AAD binding and anti-rollback
   rule are used for microfiles?
4. Does the Android Keystore wrapping and run/segment key hierarchy prevent reuse, replacement of
   a missing key and key material leakage while making key loss distinguishable from corruption?
5. Are data/manifest/SQLite ordering, filesystem sync/rename semantics and all 12 hard-kill
   barriers sufficient to support the normative commit point on API 28+ devices?
6. Do quarantine, idempotency and cleanup remain fail-closed under every matrix case?

## SQLite provenance boundary

The future journal may use only platform `android.database.sqlite` for PoC-local split-brain
tests. The API 36 Google APIs x86_64 r07 emulator archive is pinned by official SHA-1 and computed
SHA-256 and reuses only the existing immutable system-image provenance. Physical D2 has exact
sanitized firmware identity, but its runtime SQLite version, compile-options digest, effective
journal/synchronous modes and fresh exact-commit preflight are not yet recorded. D1/D5 are
unavailable. This recovery-only platform boundary does not admit a production schema or component.

## Requested reviewer dispositions

| Reviewer | Requested disposition | Current state |
|---|---|---|
| Project owner / Stage 0 Product/IP | approve, reject or request changes to exact evaluation scope, license/NOTICE/provenance package | pending |
| Independent recovery Engineering/Security | approve, reject or revise template/key/commit/recovery/kill/fault protocol | unassigned, blocking |
| Project owner / execution | after all other prerequisites, separately set `executionAllowed=true` for a named phase/commit | withheld |
| Production Legal | production/redistribution assessment | unassigned; not required for package preparation, required before production |
| Production Security | production admission assessment | separate future gate; not replaced |

Until those dispositions and later implementation/preflight evidence exist,
`tools/check_poc_recovery_run_readiness.py` must fail closed.
