# DEC-044 — POC-RECOVERY-001 pre-PoC experiment decision

Status: **Proposed experiment — protocol v0.4 and owner-approved prospective JSR-305 exclusion policy; implementation verification, accountable review and execution pending**\
Recorded for: **Project owner**\
Recorded on: **2026-08-12**\
Gate Set: `poc-recovery-stage0-v0.4`\
Scope: governance-only remediation and readiness for `POC-RECOVERY-001` only\
Execution authorized: **no**

## Context

`POC-RECOVERY-001` must determine whether Dora can preserve an authenticated, contiguous
committed prefix after abrupt process death without losing any committed byte and while limiting
uncommitted tail loss to five seconds. The Technical Plan names Tink Streaming AEAD and sealed
AEAD microfiles as candidates, but evidence must precede a final audio/container decision.

The v0.3 package at reviewed commit `c61603d30c01c72347aa205c247729ad534c2882` received four
final advisory findings `REC-GOV-V03-001`–`004`. The Project owner has now scoped the prospective
v0.4 governance remediation below. This record remains a **Proposed experiment decision**, not `ADR-AUDIO-001`, a
production architecture decision, dependency admission, or permission to implement or execute a
harness. Gate Set/protocol v0.1, v0.2 and v0.3 remain unchanged SHA-256-pinned superseded audit artifacts and are
non-executable.

## Approved prospective dependency policy; no artifact admission

All eight publisher-closure coordinates are authenticity-verified. The signed published
`com.google.code.findbugs:jsr305:3.0.2` POM declares Apache-2.0 while its exact release-source
POM/LICENSE declares BSD-3-Clause. The conflict remains unresolved and this decision does not
interpret either declaration.

The Project owner acting as Stage 0 Product/IP reviewer approves `REC-JSR305-EXCLUDE-001`,
conditioned Option A, as a prospective policy for a later separately scoped harness: exclude exact
`com.google.code.findbugs:jsr305:3.0.2` only on exact `tink-android:1.23.0`, require zero resolved
JSR-305 components in every covered compile, runtime, benchmark, test and packaging configuration,
and use exactly three R8 `-dontwarn` rules for `Nullable`, `GuardedBy` and `ThreadSafe`. Kotlin/JVM/D8
probes pass without the artifact; a bare exclusion fails the repository-pinned AGP R8, while the
exact narrow rule passes with all Tink classes preserved. No `compileOnly` or replacement artifact
is required, alternate paths are forbidden, and any reappearance fails closed for implementation
verification and execution.

The narrow-rule result is not a full-closure release claim. When all seven remaining closure JARs
are R8 program inputs, no JSR-305 missing class remains but the probe fails independently on
`javax.lang.model.element.Modifier` from `error_prone_annotations:2.41.0`. A later exact AGP graph
must resolve any such independent missing class without broadening this policy before readiness.

The owner disposition treats JSR-305 as excluded and does not select Apache-2.0 or BSD-3-Clause or
approve use or distribution of that artifact. It approves only the prospective policy and reviewed
governance package. This experiment decision remains `Proposed`: the disposition does not add a
dependency, create a graph, authorize a harness or implementation, or authorize execution. A
future exact implementation graph/build/package report remains mandatory and must fail readiness
if the forbidden coordinate appears by any path, a broad `dontwarn` is used or any R8 missing class
remains unresolved.

The policy boundary is only the future `:poc:recovery` module: all its resolvable compile, runtime,
unit-test, `androidTest`, benchmark and release configurations, packaging/runtime-artifact inputs,
dependency locks and dependency-verification metadata. Existing buildscript/AGP/UTP/lint/tooling
and app/capture/search lockfiles are outside that boundary. They contain base tooling/test Tink and
JSR-305 paths, so this decision makes no repository-wide absence claim and does not treat those
paths as recovery admission evidence.

Exact JetBrains annotations LICENSE and NOTICE bytes at immutable commit
`f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c` are verified for the governance packet. The NOTICE must
be preserved in a future Stage 0 notices packet if the artifact enters a separately approved actual
graph. This is not Production Legal, redistribution or production/dependency admission.

## Proposed experiment

One future isolated recovery harness would compare exactly two candidates against the same
synthetic input, kill controller, journal, recovery oracle and evidence schema:

1. `REC-STREAM-TINK`: Tink `StreamingAead` through public API only, with
   `DURABLE_ONE_SEGMENT_LOOKAHEAD`. The selected non-deprecated construction uses
   `AesGcmHkdfStreamingParameters`: 16-byte input key, 16-byte derived AES-GCM key, HKDF-SHA256,
   4096-byte ciphertext segments and `RegistryConfiguration.get()`. One fresh keyset and one
   HKDF-derived AES key serve the whole ciphertext stream; nonce prefix, segment index and last
   flag provide segment uniqueness. `StreamingAeadKeyTemplates` is forbidden. The design status is
   `DESIGN_SELECTED_IMPLEMENTATION_VERIFICATION_REQUIRED`.
2. `REC-MICROFILE-TINK`: individually sealed Tink `Aead` microfiles plus an authenticated,
   generation-numbered manifest. The selected public template is
   `AES256_GCM_TINK_IV12_TAG16`: 32-byte AES key, 12-byte IV, 16-byte tag, TINK variant and one fresh
   keyset per microfile. Five seconds is the only PASS-eligible cadence; a full unit is 160000
   plaintext and 160033 ciphertext bytes. Fifteen- and thirty-second cadences are observations or
   post-failure fallbacks only.

The authenticated manifest encoding is selected as
`DORA_RECOVERY_MANIFEST_V1_BINARY_BE`: magic `DORARM01`, schema 1, LP16 ASCII protocol/candidate,
raw 16-byte run ID, monotonic generation/previous-ciphertext digest, committed end and at most 721
strictly ordered gap-free entries, with a 512 KiB plaintext cap and no trailing bytes. Exact field
order is normative in the exact v0.3 base inherited by Gate Set/protocol v0.4.

No candidate is preferred in advance. A final `ADR-AUDIO-001` may be proposed only after valid
evidence and cannot infer production admission from a Stage 0 result.

## Frozen safety invariants

- Every committed plaintext byte must recover as part of one authenticated contiguous prefix.
  Loss of committed bytes is always exactly zero.
- Every attempt satisfies `0 <= C <= R <= A`; `[0,R)` is byte-for-byte equal to the synthetic
  controller oracle and every returned byte authenticated. The threshold is
  `returnedBytesAuthenticatedPercentMinimum=100.0`.
- Tail loss is computed from writer-accepted synthetic PCM bytes to the end of the recovered
  authenticated prefix and must be no more than `5.000` seconds (`160000` bytes at mono PCM16,
  16 kHz) on every valid hard kill.
- For streaming, `ciphertextPrefixBytes=q*4096` and recoverable `R` is zero for `q<2`, otherwise
  `4056+(q-2)*4080`. The last durable non-final segment is sacrificial and excluded from `C`; the
  selected design bound is 8160 bytes/0.255 seconds. Recovery counts only bytes returned by a
  successfully completed `read()`, discards the full caller buffer on exception, and treats only
  `read()==-1` as authenticated normal EOF.
- Each candidate has 120 base hard-kill attempts. At least 100 confirmed valid hard kills are
  required, with all invalid attempts retained and explained. Candidate-caused failures are valid
  results and may never be reclassified as invalid.
- Recovery must reject unauthenticated bytes, distinguish key unavailability from corruption,
  quarantine ambiguous/orphaned state without silent deletion, converge idempotently and never
  restart the microphone automatically.
- Android Keystore aliases are exactly
  `android-keystore://dora.poc.recovery.v1.<lowercase-run-uuid>`. New-run creation uses only
  `generateNewAeadKey(alias)`; new-run access and recovery use only
  `new AndroidKeystoreKmsClient.Builder().setKeyUri(alias).build().getAead(alias)`.
  `AndroidKeysetManager`,
  `getOrGenerateNewAeadKey()` and replacement-key creation during recovery are forbidden. Secret
  keysets use non-deprecated four-argument `TinkProtoKeysetFormat` encrypted serialization/parsing
  with mandatory exact key-envelope AAD and `RegistryConfiguration.get()`.
- Separate deterministic big-endian LP16-ASCII AAD schemas bind stream, microfile,
  manifest/checkpoint and key-envelope objects to protocol, candidate, run, applicable
  generation/unit/range and previous publication digest.
- Before every encrypted keyset/ciphertext/checkpoint/manifest publication, the run alias must
  create durable ninth-family ciphertext `key-confirmation/run.kc` through the exact Builder AEAD
  path, exclusive temp/write/file-fsync/rename/directory-fsync and SQLite run-row commit. The
  plaintext/AAD use separate `DORAKC01`/`DORAKA01` bounded big-endian schemas. Publication is
  forbidden until the 13-step bootstrap's successful `endTransaction()` return.
- Required v0.4 key classifications distinguish `INCOMPLETE_KEY_BOOTSTRAP`,
  `KEY_CONFIRMATION_MISSING`, `CORRUPT_KEY_CONFIRMATION`,
  `KEY_UNAVAILABLE_KEY_MISMATCH` and later `KEY_ENVELOPE_AUTH_FAILURE`. Existing temp/final paths
  are never overwritten; “or” outcomes are forbidden.
- A PoC-local platform `android.database.sqlite` journal is allowed only for DB/file split-brain
  and reconciliation tests. Room, SQLCipher, WorkManager, production schema and production
  migrations are prohibited.
- The semantic commit point is the successful return of SQLite `endTransaction()` after durable
  data and authenticated manifest/checkpoint publication. The later controller event is evidence,
  not part of commit. `C` comes from durable SQLite plus authenticated publication; the controller
  ledger independently cross-checks it and is the external rollback anchor.
- File publication uses exact 9-step streaming setup, 13-step checkpoint and 21-step microfile
  sequences. Every temp is exactly `final + ".tmp"`; collisions block without overwrite; recovery
  uses five named temp/final states and never promotes a temp by name. All eight final + eight temp
  patterns require containment, `lstat`, regular-file and no-symlink checks. Only public
  `android.system.Os` file APIs are permitted.
- SQLite is fixed to WAL, `synchronous=FULL`, `wal_autocheckpoint=0`, `foreign_keys=ON`, one writer
  and `beginTransactionNonExclusive()`. Fresh emulator/D2 preflight records effective PRAGMAs,
  `sqlite_version()`, `sqlite_source_id()` and the canonical compile-options digest; mismatch
  blocks execution. Rows store exact relative names, lengths and SHA-256 values.

The 12 strata retain 120 base attempts/candidate and now have exact candidate-specific public
barriers. In particular, microfile K02 is
`MICROFILE_AFTER_AEAD_RETURN_BEFORE_TEMP_WRITE`; streaming K02 uses the harness-owned downstream
ciphertext `OutputStream` callback; K04–K11 fix exact publication boundaries; and K12 uses an
immutable seed plus canonical expected recovery result. The mandatory matrix now includes
45 rows: the 33 inherited rows plus six `KCB-01..06` bootstrap kill points and six `KCF-01..06`
missing/corrupt/swap/replacement/collision rows. Every row has exactly three emulator and one D2
repetition, 180 prospective injections total, separate from the hard-kill denominator.

The normative definitions, encodings, predicates, strata, invalidation rules and fault matrix are
in `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_4.md` and machine-readable
`poc-recovery-gate-set-stage0-v0.4.json` / `poc-recovery-protocol-stage0-v0.4.json`.

## Device and verdict contract

Phase A may, in principle, use the pinned API 36 emulator image and the available physical D2.
It remains execution-blocked until package review, independent Engineering/Security approval and
a later, explicit Project-owner authorization. Phase A can produce only `FAIL` or `INCONCLUSIVE`;
`PASS` is structurally forbidden because D1 and D5 are unavailable.

A full physical verdict requires a separately authorized campaign on physical D1, D2 and D5.
Purchasing D1 or D5 is not required now. Emulator evidence never substitutes for a required
physical profile.

## Review and authority boundary

- The Project owner is the Stage 0 Product/IP reviewer and the only person who may later authorize
  execution. Product/IP approval does not approve crypto engineering or security.
- A distinct accountable Engineering/Security reviewer, not the package author and not acting as
  Production Security, must verify the selected v0.4 construction, key confirmation/hierarchy/AAD,
  checkpoint/commit semantics, parsers, barriers, durability and recovery state machine before
  execution. That reviewer is currently unassigned. This Codex remediation does not claim formal
  independence.
- Production Legal is unassigned. Stage 0 evaluation review does not grant redistribution or
  production rights.
- Supply-chain authenticity is verified for all eight publisher-closure coordinates. The
  underlying `jsr305:3.0.2` Apache-2.0/BSD-3-Clause conflict remains unresolved; conditioned
  exclusion is technically proven and the Project owner / Stage 0 Product-IP reviewer has approved
  only prospective policy `REC-JSR305-EXCLUDE-001` and the reviewed governance package. JSR-305
  use/distribution, an actual future graph and dependency admission are not approved.
- Production Security approval remains a separate future gate and is not replaced by the
  recovery-scoped review.

## Explicitly forbidden by this decision

- adding Tink or another recovery dependency to any Gradle/runtime graph;
- creating `:poc:recovery`, a recovery harness, production schema or production `:app` change;
- running a kill campaign, device test, benchmark or measurement;
- using internal/reflection-based Tink APIs or silently changing candidate/template/cadence;
- using deprecated `StreamingAeadKeyTemplates`, `AndroidKeysetManager` or
  `getOrGenerateNewAeadKey`;
- treating this record as final `ADR-AUDIO-001` or dependency admission.

## Readiness state

Prospective `REC-JSR305-EXCLUDE-001` policy is closed/approved and the exact governance packet's
authenticity/LICENSE/NOTICE evidence is closed/verified. The future actual graph/package/R8
verification and its scoped Product/IP disposition remain open/blocking. The readiness checker
requires absence of excluded JSR-305 in recovery scope, not approval to use that artifact.

`executionAllowed=false` and `implementationAllowed=false`. A repeat read-only review of the exact resulting governance HEAD, a
distinct accountable Engineering/Security reviewer, separate implementation authorization,
implementation and non-metric implementation verification, an exact future Gradle graph with zero
forbidden components, a release R8 build with no unresolved missing classes, scoped Product/IP
disposition of that actual graph, fresh emulator/D2 SQLite/Keystore/filesystem preflight and a
separate Project-owner execution authorization remain absent. The formal Engineering/Security
reviewer, runtime emulator/D2 facts, Production Legal and Production Security remain null.
Completing a prerequisite never flips the flag implicitly.
