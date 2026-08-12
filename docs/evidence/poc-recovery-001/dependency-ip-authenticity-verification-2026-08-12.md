# POC-RECOVERY-001 dependency authenticity verification — 2026-08-12

Status: **EXACT GOVERNANCE PACKET AUTHENTICITY/LICENSE/NOTICE VERIFIED; F-06 CLOSED ON IMMUTABLE EVIDENCE; FUTURE ACTUAL GRAPH BLOCKED**
Scope: governance evidence only. No Gradle dependency, recovery module, harness, device run,
measurement, dependency admission or production admission was created.

## Method and trust boundary

The eight-coordinate published closure was downloaded again from its recorded publisher
repository. Every JAR and POM was checked against its recorded SHA-256 and publisher SHA-256 or
SHA-1 file. Each detached signature was then verified over the artifact bytes—not merely parsed
for an issuer ID—by `tools/OpenPgpDetachedSignatureVerifier.java` on OpenJDK 17.0.10 with
Bouncy Castle 1.83.

The verifier dependencies were downloaded only to a temporary directory and checked against
publisher SHA-256 before use:

| verifier artifact | SHA-256 |
|---|---|
| `bcprov-jdk18on:1.83` | `82cf3a2af766c3bc874f6d36b9f20a8b99a8f09762dc776e8a227a45d8daaafb` |
| `bcpg-jdk18on:1.83` | `4077fd4517761c98a81944c70a376ce73f4eb3e44c03db1eb5d699fc28ab48aa` |
| `bcutil-jdk18on:1.83` | `ee7d0eb4e74de70a735f7fb36b604dd5c6ad35720d50b914604db042114a0185` |

Public keys were retrieved by full fingerprint over HTTPS from Ubuntu keyserver. A keyserver UID
is treated only as self-certified identity metadata. It is not publisher authorization. Each
coordinate is accepted only through either coordinate-specific upstream key publication or the
multisource route below. Full primary and signing-subkey fingerprints are recorded; no short key
ID substitutes for a fingerprint. The online validator also requires the recorded identity email
in the full-fingerprint index response. The Gson `op=get` key export exposes no primary UID to the
crypto library, so its identity check uses the full-fingerprint index email; this remains metadata
only and is not counted as publisher binding.

The reproducible command for artifact/checksum/signature verification is:

```text
python3 tools/verify_poc_recovery_dependency_inventory.py --online
```

That command downloads all evidence to a temporary directory, compiles the pinned verifier,
requires `verified=true`, compares the full primary and signing fingerprints and removes the
temporary directory.

## Coordinate conclusions

| coordinate | primary / signing fingerprint | source correspondence | classification |
|---|---|---|---|
| `com.google.crypto.tink:tink-android:1.23.0` | `73976C9C39C1479B84E2641A5A68A2249128E2C6` / same | signed source JAR: 790 Java entries; 531 byte-identical Git blobs at official `v1.23.0` commit `1bedd75ae7161017c5f45b020395a72bbd40645d`; remaining 258 protobuf Java files and one `Version.java` are exactly the two generated categories declared by the tagged Bazel/release scripts; zero unexplained entries | `AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE` |
| `com.google.code.findbugs:jsr305:3.0.2` | `7616EB882DAF57A11477AAF559A252FB1199D873` / same | all 31 signed-source-JAR Java entries are byte-identical Git blobs at release-source commit `d7734b13c61492982784560ed5b4f4bd6cf9bb2c`; commit author name/email matches the full-fingerprint key UID | `AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE` |
| `com.google.code.gson:gson:2.13.2` | `C7BE5BCC9FEC15518CFDA882B0F3710FA64900E7` / same | signed source JAR: 84/85 Java entries are byte-identical blobs at official tag commit `686fad782d969d8f15c7581a5435a208b810caa7`; the sole remaining `GsonBuildConfig.java` exactly equals the tagged template after the declared `${project.version}` → `2.13.2` substitution | `AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE` |
| `org.jetbrains.kotlin:kotlin-stdlib:1.7.10` | primary `2FBA29D08D2E25EE84C132C30729A0AFF8999A87`; signing subkey `6F538074CCEBF35F28AF9B066A0975F8B1127B83` | all 177 source entries are byte-identical blobs in the official `v1.7.10` libraries/core trees at `ea836fd46a1fef07d77c96f9d7e8d7807f793453` | `AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE` |
| `org.jetbrains.kotlin:kotlin-stdlib-common:1.7.10` | primary `2FBA29D08D2E25EE84C132C30729A0AFF8999A87`; signing subkey `6F538074CCEBF35F28AF9B066A0975F8B1127B83` | 145/146 source entries are byte-identical official-tag blobs; the sole remaining `KotlinVersion.kt` exactly applies its declared build-time patch `1.7.255` → `1.7.10` | `AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE` |
| `org.jetbrains:annotations:13.0` | `2E3A1AFFE42B5F53AF19F780BCF4173966770193` / same | all 16 source entries are byte-identical blobs in official IntelliJ historical source tree `37dcd9bae242dc44ebc6b06f5d841003b3e03423` at commit `f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c`; the published POM copies that exact source path | `AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE` |

The two previously closed coordinates were also rechecked with the stronger crypto verifier:
AndroidX is `AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE` with primary fingerprint
`EB4C1BFD4F042F6DDDCCEC917721F63BD38B4796` and signing subkey
`A5F483CD733A4EBAEA378B2AE88979FB9B30ACF2`; Error Prone is
`AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE` with fingerprint
`EE0CA873074092F806F59B65D364ABAA39A47320`.

For `annotations:13.0`, the published POM's `66770193` keyname is recorded only as corroborating
suffix evidence. Crypto verification requires the complete
`2E3A1AFFE42B5F53AF19F780BCF4173966770193` fingerprint.

Its applicable LICENSE and NOTICE were independently fetched at exact historical-source commit
`f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c` and hashed at `2026-08-12T14:38:33Z` using `gh api`
GitHub Contents API plus `System.Security.Cryptography.SHA256`:

| object | immutable locator | bytes | SHA-256 |
|---|---|---:|---|
| LICENSE | `https://github.com/JetBrains/intellij-community/blob/f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c/LICENSE.txt` | 11,358 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| NOTICE | `https://github.com/JetBrains/intellij-community/blob/f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c/NOTICE.txt` | 127 | `0479f6a86003002dec1da1667f2f8320253c7225c6ffffc05cf7e0988bd8c72c` |

The exact JetBrains evidence is accepted for this Stage 0 governance packet. If the artifact later
enters a separately approved resolved graph, the NOTICE must be preserved in the future Stage 0
notices packet. This is not Production Legal, production/dependency admission or redistribution
approval.

## Source comparison commands

Release tree data came from the official/signer-owned GitHub API objects, for example:

```text
gh api repos/tink-crypto/tink-java/git/trees/1bedd75ae7161017c5f45b020395a72bbd40645d?recursive=1
gh api repos/google/gson/git/trees/686fad782d969d8f15c7581a5435a208b810caa7?recursive=1
gh api repos/JetBrains/kotlin/git/trees/11073d942fa7fa2542d34aec678e297002f5711d?recursive=1
gh api repos/JetBrains/kotlin/git/trees/9149eae74f20e7e66eed8bffd1b2215661ec76bd?recursive=1
gh api repos/amaembo/jsr-305/git/trees/d7734b13c61492982784560ed5b4f4bd6cf9bb2c?recursive=1
gh api repos/JetBrains/intellij-community/git/trees/37dcd9bae242dc44ebc6b06f5d841003b3e03423?recursive=1
```

Each source entry was compared with Git's exact blob identity
`SHA1("blob " + byteLength + NUL + bytes)`. Counts and generated-file explanations are stored in
`dependency-ip-authenticity-v0.3.json`.

## Unresolved license conflict

Authenticity is no longer pending and F-06 closes only after the immutable JetBrains
LICENSE/NOTICE verification above completed. The evidence conclusion nevertheless identifies an
independent boundary for the excluded JSR-305 artifact. The immutable,
signed Maven Central POM for `com.google.code.findbugs:jsr305:3.0.2` (SHA-256
`19889dbdf1b254b2601a5ee645b8147a974644882297684c798afe5d63d78dfe`) declares
Apache-2.0. The exact release-source POM (SHA-256
`5389748594bba388b874a9e12bdeaf456154daffb5f0f1f0de00c4d400604edf`) and `ri/LICENSE`
(SHA-256 `f1aaf45844a32fefb9cf7eca8088bc4fe8ee3c4518ec944c89627ef4881d073b`) declare
BSD-3-Clause and state `Copyright (c) 2007-2009, JSR305 expert group`.

The missing external fact is which terms the authorized publisher/rightsholders intend to govern
the immutable 3.0.2 artifact. Safe owner choices are:

1. obtain written publisher/rightsholder clarification and submit it to Stage 0 Product/IP;
2. keep the coordinate unadmitted and select a future exact dependency graph that excludes or
   replaces it; or
3. ask qualified Product/IP counsel whether explicitly complying with both term sets is
   acceptable.

Prospective `REC-JSR305-EXCLUDE-001` policy is already closed/approved: it keeps this coordinate
outside the future recovery graph without interpreting the conflicting terms or approving use or
distribution. Exact governance packet evidence is closed/verified. The future actual
`:poc:recovery` graph/package/R8 evidence and its scoped Product/IP disposition remain open and
blocked. This record does not issue Production Legal or production/dependency admission.

## Limitations and boundary

No byte-for-byte binary rebuild was attempted, so no coordinate is classified as
`AUTHENTICITY_VERIFIED_EXACT_REPRODUCIBLE_SOURCE`. The multisource classification is limited to
valid full-fingerprint signatures, publisher checksums, a signed source JAR, byte-exact upstream
source correspondence and enumerated tagged build transformations. It is evidence for package
review, not a dependency admission.

`executionAllowed=false`; approval identities/dates remain null; reviewers remain unassigned;
there is no recovery runtime dependency, module, harness, device execution, kill campaign or
measurement.
