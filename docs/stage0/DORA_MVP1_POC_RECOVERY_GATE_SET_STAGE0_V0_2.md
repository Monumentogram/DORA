# Dora MVP 1 — POC-RECOVERY-001 Gate Set `stage0-v0.2`

Status: **Proposed owner-remediated contract — implementation verification and accountable review required; execution prohibited**\
Date: 12 August 2026\
Decision: `DEC-044` / `OD-14` governance-protocol amendment\
Machine Gate Set: `docs/stage0/poc-recovery-gate-set-stage0-v0.2.json`\
Machine protocol: `docs/stage0/poc-recovery-protocol-stage0-v0.2.json`\
Current authorization: `executionAllowed=false`

## 1. Scope, versioning and non-scope

This prospective Gate Set remediates the review disposition `CHANGES_REQUIRED` against reviewed
commit `87f8c00c6afce0f658678a7a09b1a394b89a2454`. The Project owner selected the protocol semantics
below for governance protocol v0.2. The selection is not evidence that the future implementation
matches the design.

`stage0-v0.2` supersedes `stage0-v0.1` for every future implementation or execution. The v0.1
Markdown and JSON files remain in the repository unchanged as superseded audit artifacts and are
not executable contracts.

The only candidates remain:

- `REC-STREAM-TINK`: public Tink `StreamingAead`, AES-GCM-HKDF streaming with durable one-segment
  lookahead; and
- `REC-MICROFILE-TINK`: public Tink `Aead`, sealed five-second AES-256-GCM TINK-variant
  microfiles plus an authenticated manifest.

This Gate Set does not add or admit a dependency, create `:poc:recovery`, authorize a harness or
run, select a production format, finalize `ADR-AUDIO-001`, define a production database, modify
production `:app`, or replace Production Legal or Production Security approval. Fifteen- and
thirty-second microfile variants remain observations/post-failure fallbacks and can never PASS.

## 2. Fixture, limits and offsets

The oracle is deterministic synthetic mono PCM16, little-endian, 16,000 samples/second and two
bytes/sample. No microphone or real audio is involved.

```text
bytesPerSecond             = 16000 * 1 * 2 = 32000
tailGateBytes              = 5.000 * 32000 = 160000
maxPlaintextBytesPerRun    = 115200000
maxStreamingSegmentsPerRun = 28236
maxManifestEntries         = 721
maxManifestPlaintextBytes  = 524288
```

All plaintext intervals use `plaintextStartInclusive` and `plaintextEndExclusive`. `A` is the
exclusive end of complete bounded writer calls that returned before `SIGKILL`; a call still in
progress is excluded. Acknowledgement may not be delayed until durability merely to lower measured
tail loss.

## 3. Deterministic binary encoding

All protocol binary records use unsigned big-endian integers, raw RFC 4122 UUID bytes for
`runId[16]`, raw 32-byte SHA-256 digests, and ASCII strings. `LP16 ASCII` means a two-byte unsigned
big-endian byte length followed by exactly that many 7-bit ASCII bytes. Non-ASCII, embedded NUL,
overlong values, integer overflow, incomplete fields and trailing bytes are rejected.

### 3.1 Manifest plaintext

`manifest.exactEncoding = DORA_RECOVERY_MANIFEST_V1_BINARY_BE` and
`manifest.status = DESIGN_SELECTED_IMPLEMENTATION_VERIFICATION_REQUIRED`.

The byte order is exact:

| Field | Encoding |
|---|---|
| magic | eight ASCII bytes `DORARM01` |
| schema version | `U16BE`, exactly `1` |
| protocolId | `LP16 ASCII` |
| candidateId | `LP16 ASCII` |
| runId | 16 raw bytes |
| generation | `U64BE` |
| previousManifestCiphertextSha256 | 32 raw bytes; all zero only for generation 1 |
| committedEndExclusive | `U64BE` |
| entryCount | `U32BE` |
| entries | exactly `entryCount` records in the order below |

Each entry is encoded as:

| Field | Encoding |
|---|---|
| unitIndex | `U32BE` |
| plaintextStartInclusive | `U64BE` |
| plaintextEndExclusive | `U64BE` |
| cadenceSeconds | `U32BE` |
| ciphertextBytes | `U64BE` |
| ciphertextSha256 | 32 raw bytes |
| keyEnvelopeBytes | `U64BE` |
| keyEnvelopeSha256 | 32 raw bytes |
| ciphertextRelativeName | `LP16 ASCII` |
| keyEnvelopeRelativeName | `LP16 ASCII` |

Generation starts at 1 and increments by exactly 1. The previous digest is the SHA-256 of the exact
previous manifest ciphertext bytes. Entries begin at unit 0, are strictly increasing, have no gap,
duplicate, reorder or removal, and their plaintext ranges form one contiguous prefix starting at
zero. `committedEndExclusive` is zero for no entries or equals the last entry's
`plaintextEndExclusive`. `entryCount <= 721`, plaintext length is at most 512 KiB, and trailing
bytes are forbidden.

### 3.2 Streaming checkpoint plaintext

The checkpoint uses `DORA_RECOVERY_STREAM_CHECKPOINT_V1_BINARY_BE`: magic `DORARC01`, `U16BE(1)`,
`LP16(protocolId)`, `LP16(candidateId)`, `runId[16]`, `U64BE(generation)`, the 32-byte previous
checkpoint-ciphertext SHA-256, `U32BE(q)`, `U64BE(ciphertextPrefixBytes)`,
`U64BE(committedEndExclusive)`, `U64BE(streamKeyEnvelopeBytes)`, the 32-byte stream-key-envelope
SHA-256, `LP16(streamCiphertextRelativeName)` and `LP16(streamKeyEnvelopeRelativeName)`, with no
trailing bytes. Generation and digest-chain rules match the manifest. The checkpoint is valid only
when its values satisfy the streaming equations in section 4.1.

### 3.3 Exact associated-data schemas

Each schema starts with the listed eight-byte domain magic followed by `U16BE(1)`. The same field
name always has the encoding defined above.

1. `DORA_RECOVERY_STREAM_AAD_V1_BINARY_BE` / `DORASA01`:
   `protocolId`, `candidateId`, `runId`, `U64BE(streamGeneration=1)`,
   `U64BE(plaintextStartInclusive=0)`, `U64BE(plaintextEndExclusive=115200000)`, and the all-zero
   32-byte genesis previous-checkpoint digest. This AAD is supplied once when the ciphertext stream
   is created and never changes per segment.
2. `DORA_RECOVERY_MICROFILE_AAD_V1_BINARY_BE` / `DORAMA01`:
   `protocolId`, `candidateId`, `runId`, `U64BE(manifestGeneration)`, `U32BE(unitIndex)`,
   `U64BE(plaintextStartInclusive)`, `U64BE(plaintextEndExclusive)`, `U32BE(cadenceSeconds)`, and
   `previousManifestCiphertextSha256[32]`.
3. `DORA_RECOVERY_PUBLICATION_AAD_V1_BINARY_BE` / `DORACP01`:
   `protocolId`, `candidateId`, `runId`, `LP16(publicationKind)` where the only values are
   `MANIFEST` and `CHECKPOINT`, `U64BE(generation)`, `U32BE(terminalUnitIndex)` where
   `0xffffffff` represents an empty prefix, `U64BE(plaintextStartInclusive=0)`,
   `U64BE(plaintextEndExclusive=committedEndExclusive)`, and the applicable previous manifest or
   checkpoint ciphertext digest.
4. `DORA_RECOVERY_KEY_ENVELOPE_AAD_V1_BINARY_BE` / `DORAKE01`:
   `protocolId`, `candidateId`, `runId`, `LP16(targetKind)` where the only values are `STREAM`,
   `MICROFILE`, `MANIFEST`, and `CHECKPOINT`, `U64BE(generation)`, `U32BE(unitIndex)` with
   `0xffffffff` when not applicable, `U64BE(plaintextStartInclusive)`,
   `U64BE(plaintextEndExclusive)`, `U32BE(cadenceSeconds)` with zero when not applicable, and the
   applicable previous manifest/checkpoint digest.

Field omission, alternate ordering, platform-endian integers, JSON/text substitution or AAD reuse
for another target is invalid. The manifest/checkpoint digest chain is locally useful for
crash/split-brain reconciliation, but it is not a global anti-rollback anchor.

## 4. Candidate crypto and key contract

Every selected construction has status
`DESIGN_SELECTED_IMPLEMENTATION_VERIFICATION_REQUIRED`. A later accountable Engineering/Security
review must verify the exact implementation before execution.

### 4.1 Streaming AEAD and durable lookahead

The non-deprecated public construction is:

- `AesGcmHkdfStreamingParameters.builder()`;
- input key size 16 bytes;
- derived AES-GCM key size 16 bytes;
- `AesGcmHkdfStreamingParameters.HashType.SHA256`;
- ciphertext segment size 4096 bytes;
- a fresh one-entry keyset for every run, built from those parameters; and
- primitive creation with `KeysetHandle.getPrimitive(RegistryConfiguration.get(), StreamingAead.class)`.

`StreamingAeadKeyTemplates` is deprecated and forbidden. The stream has one fresh input key and
one HKDF-derived AES-GCM key for the ciphertext stream. Segment uniqueness comes from the nonce
prefix, segment index and last flag; the protocol does not claim a separate derived AES key for
each segment.

`checkpointModel = DURABLE_ONE_SEGMENT_LOOKAHEAD`. Let `q` be the number of fully output,
non-final 4096-byte ciphertext segments durably present in the prefix:

```text
ciphertextPrefixBytes = q * 4096
R(q) = 0                              when q < 2
R(q) = 4056 + (q - 2) * 4080          when q >= 2
```

The last durable non-final segment is sacrificial lookahead and is never included in committed
prefix `C`. The maximum bounded streaming tail is 8160 bytes, or 0.255 seconds. Streaming has
`passEligibleCadencesSeconds=[]`, `maxPlaintextBytesPerRun=115200000` and
`maxSegmentsPerRun=28236`.

Recovery requests 4056 plaintext bytes first and then 4080-byte blocks. Only bytes returned by a
`read()` call that completed successfully contribute to `R`. The entire caller buffer belonging
to a read that throws is discarded, irrespective of mutations made before the exception.
`read()==-1` is authenticated normal EOF. `IOException`, `GeneralSecurityException`, truncation,
authentication failure and any other exception are never reclassified as EOF.

### 4.2 Five-second microfiles

The public parameters are `AesGcmParameters` with a 32-byte AES key, 12-byte IV, 16-byte tag and
`AesGcmParameters.Variant.TINK`. The machine name is `AES256_GCM_TINK_IV12_TAG16`. Every microfile
gets one fresh one-entry keyset and primitive creation uses
`KeysetHandle.getPrimitive(RegistryConfiguration.get(), Aead.class)`.

The only PASS-eligible cadence is five seconds. A full unit is 160000 plaintext bytes and 160033
ciphertext bytes: five TINK-prefix bytes, 12 IV bytes, plaintext and 16 tag bytes. Fifteen- and
thirty-second variants remain explicitly `passEligible=false`.

Every manifest and streaming checkpoint publication is authenticated and encrypted with the same
exact `AES256_GCM_TINK_IV12_TAG16` public `Aead` parameter construction and a fresh one-entry
keyset per publication generation. Its publication AAD is the exact `DORACP01` schema; the
encrypted keyset uses the exact `DORAKE01` key-envelope AAD.

### 4.3 Android Keystore and encrypted keysets

- `AndroidKeysetManager` and `getOrGenerateNewAeadKey` are forbidden.
- The alias is exactly
  `android-keystore://dora.poc.recovery.v1.<lowercase-run-uuid>`.
- New-run creation calls only `AndroidKeystoreKmsClient.generateNewAeadKey(alias)`. An existing
  alias yields `KEY_REF_COLLISION`; the existing key is not deleted or reused.
- Recovery calls only `AndroidKeystoreKmsClient.getAead(alias)`. Missing, invalidated or unusable
  aliases yield `KEY_UNAVAILABLE`; recovery never generates a replacement key.
- Keysets are serialized and parsed only with the four-argument non-deprecated
  `TinkProtoKeysetFormat.serializeEncryptedKeyset(..., RegistryConfiguration.get())` and
  `parseEncryptedKeyset(..., RegistryConfiguration.get())` forms. Key-envelope AAD is mandatory.
- A fresh keyset exists per streaming run, per microfile, and per authenticated manifest/checkpoint
  generation. Secret keysets are never serialized cleartext.

Exact classifications are:

| Classification | Meaning |
|---|---|
| `KEY_UNAVAILABLE` | expected alias is missing, invalidated or unusable before envelope authentication |
| `KEY_UNAVAILABLE_KEY_MISMATCH` | the controller-injected or otherwise evidenced key under the same alias is not the original run key |
| `CORRUPT_KEY_ENVELOPE` | envelope framing/size/protobuf is malformed before a valid authenticated parse |
| `KEY_ENVELOPE_AUTH_FAILURE` | structurally bounded envelope fails authentication/AAD with the expected available alias and no replacement-key evidence |
| `KEY_REF_COLLISION` | new-run alias already exists when creation is attempted |

No classification permits key replacement, silent deletion or treating unavailable key material
as ordinary ciphertext corruption.

## 5. Commit and oracle contract

The semantic commit point is the successful return of platform SQLite `endTransaction()` after
all applicable data, key-envelope and manifest/checkpoint publication steps are durable. A
controller commit event is emitted only after that return. It is evidence and an independent
cross-check, not part of the definition of commit.

`C` is reconstructed from the gap-free intersection of durable SQLite unit rows and the
authenticated manifest/checkpoint. The controller ledger cross-checks that reconstruction. The
following invariants are mandatory for every valid attempt and applicable fault row:

```text
0 <= C <= R <= A
committedLossBytes = 0
bytes [0,R) are byte-for-byte equal to the synthetic controller oracle
returnedBytesAuthenticatedPercent >= 100.0
```

The threshold field is named `returnedBytesAuthenticatedPercentMinimum=100.0`. Recovery beyond
`C` is allowed only when those bytes independently authenticate and remain one contiguous oracle-
equal prefix.

The external Phase A controller ledger records the generation, publication-ciphertext SHA-256,
`C`, and controller event state. It is the only rollback anchor outside the app snapshot. Without
that ledger the protocol claims only crash/split-brain rollback detection; it does not claim a
global cryptographic anti-rollback guarantee against rollback of a complete internally consistent
snapshot.

## 6. File, path, quarantine and SQLite durability

### 6.1 Roots and path safety

The exact roots are:

```text
active:     context.noBackupFilesDir/poc-recovery/v1/runs/<runId>/
quarantine: context.noBackupFilesDir/poc-recovery/v1/quarantine/<runId>/
```

`runId` is the canonical lowercase UUID string matching `runId[16]`. Every stored relative name
must match the protocol allowlist, resolve canonically beneath its root, and pass `lstat` checks on
each existing component. Symlinks are forbidden. Ciphertext, key-envelope and publication objects
must be regular files; unexpected types are fail-closed. Existing final paths are never
overwritten.

Only public `android.system.Os.open`, `fsync`, `rename` and `close` are used for filesystem
durability. New files use exclusive creation and no-follow behavior. Under the single-writer
contract, destination existence checked by `lstat` is a collision and blocks `rename`.

### 6.2 Publication order

For every newly published immutable object, the order is:

1. temp key-envelope write → key-envelope file `fsync` → rename → parent-directory `fsync`;
2. temp ciphertext write → ciphertext file `fsync` → rename → parent-directory `fsync`;
3. temp manifest/checkpoint write → publication file `fsync` → rename → parent-directory `fsync`;
4. SQLite transaction → durable commit and successful `endTransaction()` return; and
5. controller evidence event.

The streaming ciphertext file is initially created and published under this rule while its file
descriptor remains open; later append checkpoints `fsync` that append-only descriptor before a
new immutable checkpoint publication. No existing stream or generation path is replaced.

### 6.3 SQLite profile and rows

The only database API is `android.database.sqlite`. Room, SQLCipher, WorkManager, a bundled SQLite,
production tables and migrations remain forbidden. The exact profile is:

```text
PRAGMA journal_mode=WAL
PRAGMA synchronous=FULL
PRAGMA wal_autocheckpoint=0
PRAGMA foreign_keys=ON
single writer
beginTransactionNonExclusive()
```

A fresh emulator and D2 preflight must record and verify effective values, `sqlite_version()`,
`sqlite_source_id()`, compile-option count and the SHA-256 of exact `PRAGMA compile_options` rows
sorted lexicographically, UTF-8 encoded, joined by LF and terminated by one LF. Any mismatch blocks
execution.

Every unit row stores exact relative ciphertext and key-envelope names, byte lengths and SHA-256
digests, not an abstract `fileIdentity`. It also stores run/candidate, unit index, inclusive/exclusive
plaintext range, cadence, manifest/checkpoint generation, state and `processingIntentId`. Every
manifest/checkpoint publication row likewise stores publication kind, generation, committed end,
exact publication relative name/byte length/SHA-256, exact key-envelope relative name/byte
length/SHA-256, previous publication ciphertext digest and state. No row may substitute an inode,
URI, object reference or other abstract file identity for these values.

`processingIntentId` is the 32 raw bytes:

```text
SHA-256(
  LP16(protocolId) || LP16(candidateId) || runId[16] ||
  U32BE(unitIndex) || U64BE(plaintextStartInclusive) ||
  U64BE(plaintextEndExclusive) || ciphertextSha256[32]
)
```

The SQLite column has a mandatory `UNIQUE` constraint. Public evidence may render the value as
lowercase hexadecimal but the database identity is the raw 32-byte digest.

### 6.4 Quarantine transaction

Quarantine is exactly:

1. SQLite intent commit;
2. source-to-quarantine `rename`;
3. `fsync` of both source and destination directories; and
4. SQLite completion commit.

Recovery reconciles every interrupted boundary idempotently. It never copies/open-as-healthy,
follows a symlink, overwrites a quarantine destination or silently deletes an ambiguous object.

## 7. Hard-kill campaign and observable barriers

The campaign remains 12 strata × 10 base attempts = 120 base attempts per candidate, with at least
100 valid hard kills and at least eight valid attempts per stratum. Phase A retains 72 emulator
and 48 D2 base slots; a full separately authorized campaign retains D1/D2/D5 allocation. Invalid
attempts, at most one explicit `-R1` replacement for an eligible external invalidator, and the
20-per-candidate replacement cap remain unchanged. Candidate failures are always valid outcomes.

Every barrier is triggered by a harness-owned public wrapper/callback or public API return; no
Tink internals, reflection or timing-only sleeps are allowed.

| Stratum | `REC-STREAM-TINK` observable barrier | `REC-MICROFILE-TINK` observable barrier |
|---|---|---|
| `K01` | bounded plaintext write returned; no complete downstream 4096-byte ciphertext segment callback yet | bounded plaintext accepted into unit buffer; `Aead.encrypt()` not called |
| `K02` | harness-owned downstream ciphertext `OutputStream` callback has received the next segment bytes and is paused before delegating the write | `MICROFILE_AFTER_AEAD_RETURN_BEFORE_TEMP_WRITE`: `Aead.encrypt()` returned the complete ciphertext in harness memory; temp ciphertext file has not been created or written |
| `K03` | downstream full-segment write and public plaintext write returned; stream file not synced for this segment | temp ciphertext write completed; ciphertext file not synced |
| `K04` | append-only ciphertext `fsync` returned for `q`; checkpoint temp does not exist | temp ciphertext `fsync` returned; final ciphertext rename not started |
| `K05` | durable stream prefix is present and the stream final name is already published; checkpoint-key-envelope publication has not started | ciphertext rename and ciphertext parent-directory `fsync` returned; manifest-key-envelope publication has not started |
| `K06` | checkpoint key envelope is durable; checkpoint temp ciphertext write returned; checkpoint file not synced | manifest key envelope is durable; manifest temp ciphertext write returned; manifest file not synced |
| `K07` | checkpoint temp `fsync` returned; checkpoint final rename not started | manifest temp `fsync` returned; manifest final rename not started |
| `K08` | checkpoint rename and parent-directory `fsync` returned; SQLite transaction not begun | manifest rename and parent-directory `fsync` returned; SQLite transaction not begun |
| `K09` | exact SQLite rows written in an open non-exclusive transaction; `endTransaction()` not called | same candidate-specific unit/manifest rows written; `endTransaction()` not called |
| `K10` | `endTransaction()` returned and controller commit event is durably acknowledged; next plaintext call not accepted | same semantic commit/event boundary; next unit not accepted |
| `K11` | next bounded plaintext call is active after at least one prior semantic commit | next microfile unit is active after at least one prior semantic commit |
| `K12` | recovery runs from immutable `K12-STREAM-V0.2` seed snapshot and pauses after quarantine intent commit, before rename | recovery runs from immutable `K12-MICROFILE-V0.2` seed snapshot at the same quarantine boundary |

For `K12-STREAM-V0.2`, the seed has `q=3`, `A=12216`, authenticated checkpoint
`C=R=8136`, one unauthenticated appended object and no controller mutation after snapshot digest.
The canonical result after interruption/restart is `[0,8136)`, one stable processing intent for
that range, one quarantine completion for the appended object and no duplicate row/file.

For `K12-MICROFILE-V0.2`, the seed has two committed five-second entries
`C=R=320000`, `A=360000`, one 40000-byte uncommitted temp unit and no controller mutation after
snapshot digest. The canonical result is `[0,320000)`, exactly two stable processing intents, one
quarantine completion for the temp unit and no duplicate row/file. Each attempt records the
immutable seed snapshot SHA-256 before recovery begins.

## 8. Mandatory fault matrix

Fault cases are outside the hard-kill denominator. Every applicable Phase A row runs three times
on the pinned emulator and once on D2 per candidate. Full-campaign repetitions remain one each on
D1, D2 and D5.

| ID | Injection | Required fail-closed outcome |
|---|---|---|
| `COR-01` | bit flip in committed ciphertext | affected authentication fails; earlier prefix preserved; affected/later state quarantined |
| `COR-02` | alter manifest/checkpoint ciphertext or tag | generation rejected; last valid jointly durable prefix or explicit corruption |
| `COR-03` | alter a SQLite filename, length, digest or range | split-brain; data not relabeled committed; no deletion |
| `COR-04` | swap a microfile ciphertext with another entry/key reference | digest/AAD/envelope binding rejects the swap; no cross-unit decrypt or skip |
| `COR-05` | duplicate, gap, reorder or remove a manifest entry/unit | strict parser rejects the generation; no implicit sorting, compaction or later-unit promotion |
| `COR-06` | replay an object across run or generation | run/generation/range/previous-digest AAD rejects it; no healthy classification |
| `TRU-01` | truncate an uncommitted tail | committed prefix preserved; tail explicitly classified |
| `TRU-02` | truncate a committed unit | immediate FAIL for committed loss; never a shorter healthy relabel |
| `TRU-03` | append unauthenticated bytes | appended bytes rejected/quarantined; prefix unchanged |
| `KEY-01` | missing/invalidated/unusable run alias | `KEY_UNAVAILABLE`; artifacts retained |
| `KEY-02` | missing run key-envelope/reference | `KEY_UNAVAILABLE`; no replacement key |
| `KEY-03` | missing candidate data key envelope | scoped unavailable prefix; no skip across a hole |
| `KEY-04` | replace key material under the same alias | `KEY_UNAVAILABLE_KEY_MISMATCH`; replacement is never accepted or regenerated |
| `KEY-05` | swap key envelopes between targets | digest/AAD failure as `KEY_ENVELOPE_AUTH_FAILURE` or structural corruption; no keyset use |
| `SPL-01` | file durable, SQLite row absent | authenticated orphan remains uncommitted and is reconciled/quarantined |
| `SPL-02` | SQLite committed row, file missing | explicit split-brain and FAIL for committed loss |
| `SPL-03` | manifest/checkpoint ahead of SQLite | generation retained for reconciliation; never implicit commit |
| `SPL-04` | SQLite ahead of authenticated publication | explicit split-brain; last jointly durable prefix only |
| `SPL-05` | temp file/generation remains | deterministic temp classification; no final-path overwrite |
| `RBK-01` | replace current manifest with an older valid generation | external ledger detects rollback; no global anti-rollback claim without that ledger |
| `RBK-02` | restore a full older valid app snapshot | external ledger detects full-snapshot rollback; local chain alone is explicitly insufficient |
| `PAR-01` | malformed/oversized/trailing-byte manifest or unsafe/symlink/path-traversal name | `MALFORMED_MANIFEST`, `OVERSIZED_MANIFEST` or `UNSAFE_PATH`; no unsafe object opened |
| `QUA-01` | unknown/orphan regular file | intent and hashes recorded; move only to contained app-private quarantine |
| `QUA-02` | hard kill during a quarantine move | next recovery converges to one item without loss/duplicate |
| `QUA-03` | interrupt each intent→rename→directory-fsync→completion boundary | deterministic reconciliation reaches exactly one completion or explicit retry state |
| `IDE-01` | run recovery twice without mutation | byte-identical prefix and stable classifications/intent IDs |
| `IDE-02` | hard kill recovery, then recover again | canonical uninterrupted result; no duplicate/missing intent |
| `EVT-01` | kill after successful SQLite `endTransaction()` return but before controller event | local `C` remains committed and reconstructible; ledger reports event gap, never downgrades commit |
| `CLN-01` | hard kill during explicit cleanup | rerun converges without silent committed/quarantine evidence loss |
| `CLN-02` | deny cleanup deletion | retryable cleanup state; verdict/evidence retained |
| `CLN-03` | explicitly delete test keys after retention | subsequent access reports key unavailability; receipt contains references only |

## 9. Gate predicates and verdict

All predicates are conjunctive; no average hides a valid failure.

| Gate | Predicate |
|---|---|
| `REC-G01-COMMITTED` | committed loss is exactly zero for every valid kill and fault case |
| `REC-G02-AUTH-PREFIX` | 100% of bytes returned by successful reads authenticate, are oracle-equal and form one gap-free prefix |
| `REC-G03-TAIL` | every valid kill has tail loss at most 160000 bytes / 5.000 seconds; streaming design bound is 8160 bytes |
| `REC-G04-KILL-COVERAGE` | 120 base dispositions, ≥100 valid/candidate, ≥8 valid/stratum and all environment minima |
| `REC-G05-IDEMPOTENCY` | stable recovery plus UNIQUE deterministic processing intents, with zero duplicate/missing intent |
| `REC-G06-FAIL-CLOSED` | corruption, truncation, key state, rollback and split-brain receive exact classifications without silent deletion |
| `REC-G07-KEYS` | exact Keystore/encrypted-keyset/AAD contract and mandatory key faults pass; no secret export/logging |
| `REC-G08-PATH-QUARANTINE` | canonical regular-file-only storage and quarantine protocol converge without path escape or data loss |
| `REC-G09-NO-AUTO-CAPTURE` | no microphone permission, open or restart |
| `REC-G10-DEVICE-VERDICT` | only a separately authorized physical D1/D2/D5 campaign can PASS |

Any unauthenticated returned byte, nonzero committed loss, path escape, secret leak, automatic
microphone start or other critical predicate produces `FAIL`. Missing counts, review,
implementation verification or required environment evidence produces `INCONCLUSIVE`. Phase A is
always `FAIL` or `INCONCLUSIVE`; PASS is forbidden. Candidate selection and final
`ADR-AUDIO-001` remain post-evidence decisions.

## 10. Current blockers and authority

- Product/IP final approval is absent; `approvedReviewer` and `approvedOn` remain null.
- A distinct accountable Engineering/Security reviewer is unassigned. The current Codex
  remediation is not asserted to be formally independent review.
- Selected constructions, parsers, barriers, durability and recovery behavior have no
  implementation or non-metric implementation-verification evidence.
- The exact future Gradle-resolved harness graph does not exist and remains a pre-execution gate.
- Fresh emulator and D2 runtime/SQLite preflight facts remain null; D1/D5 remain unavailable.
- Production Legal and Production Security remain unassigned and are not replaced.
- Project-owner execution authorization is absent.

Therefore `executionAllowed=false`. Completion of any blocker cannot change it implicitly.
