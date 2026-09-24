# DORA Alpha ASR 5.1B - deterministic pilot manifest contract

Task: `5.1B - deterministic ASR pilot manifest contract + validator`\
Profile: `dora-alpha-asr-pilot-manifest-v0.1`\
Date: 24 September 2026\
State: **PASS - MANIFEST_CONTRACT_AND_VALIDATOR_READY**\
Base: `a50a1707524ebb07300de8c31fc64f689f71062f`

## 1. Authority and claim ceiling

This is the narrow host tooling authorized by the Project Owner's 5.1B task.
It follows the [Alpha data scope](DORA_ALPHA_ASR_DATA_SCOPE_STAGE0_V0_1.md),
[5.1A admission](DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md),
[internal review](DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md),
[Dataset Governance](DORA_MVP1_DATASET_GOVERNANCE.md) and
[IP Asset Policy](DORA_MVP1_IP_ASSET_POLICY.md).

`5.1B = PASS` means only `MANIFEST_CONTRACT_AND_VALIDATOR_READY`.
It does not mean corpus download/materialization, actual decoding, terms acceptance
or ASR quality evaluation.

The separate executable is
[alpha_asr_pilot_manifest.py](../../tools/alpha_asr_pilot_manifest.py).
Its closed field sets and constants are the executable profile; no extra JSON Schema
dependency or duplicate schema engine is introduced. Existing `SyntheticManifestValidator`,
its schema/tests, and the `POC-ASR-001` synthetic scoring oracle remain unchanged.

The scoring oracle proves only its existing pretokenized edit-count/WER aggregation
and prematched timestamp aggregation mechanics. It supplies no tokenizer,
normalization policy, quality threshold, inference or device evidence.

## 2. Two controlled-private inputs

1. **Candidate inventory:** all rows of the exact pinned provider `test` splits,
   represented as metadata before duration/reference/decode eligibility filtering.
   A later authorized materializer must establish and audit completeness against
   the pinned archives and split. No preselected subset may be substituted.
2. **Selected pilot manifest:** exactly the first 24 eligible RU and first 24 eligible
   EN rows under section 5, including the source metadata unchanged.

Actual inputs stay in `LOCAL_PRIVATE_CONTROLLED_STORAGE`, outside Git, worktrees,
public evidence and CI. Tests generate synthetic metadata in memory and transient
OS-temp JSON files. Fake hashes/paths are not Common Voice provenance; no actual
input is created in 5.1B.

The validator compares supplied inputs. It cannot prove inventory completeness,
provider authenticity, terms acceptance, real audio hashes, reference presence or
actual decoding from metadata. A self-consistent fabricated inventory does not become
authentic because it passes. Later materialization must supply that evidence and
freeze the inventory before any ASR result is inspected.

## 3. Closed input profile

Every listed field is required; unknown fields at every object level are rejected.
No null, default, ignored extension or coercion is accepted. Numeric contract fields
are JSON integers, never booleans, decimal or exponent tokens. SHA-256 fields are
exactly 64 lowercase hex characters, without prefix; all-zero placeholders fail.
`PENDING_DOWNLOAD_VERIFICATION` is a current status, not a valid future input digest.

### Common root fields

| Field | Exact value or rule |
|---|---|
| `schemaVersion` | Integer `1` |
| `profileId` | Inventory: `dora-alpha-asr-candidate-inventory-v0.1`; selected: `dora-alpha-asr-pilot-manifest-v0.1` |
| `purpose` | `BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY` |
| `dataClass` | `PUBLIC_LICENSED` |
| `datasets` | Exactly two dataset objects, in RU then EN order |
| `campaignId` | `campaign-` plus 32 lowercase hex characters; fixed before selection |
| `storageClass` | `LOCAL_PRIVATE_CONTROLLED_STORAGE` |
| `trainingAllowed` | Boolean `false` |
| `publicRedistributionAllowed` | Boolean `false` |
| `selectionAlgorithm` | `dora-alpha-asr-selection-v0.1` |

Common fields except `profileId` must match between inputs. Campaign identity does
not affect selection keys; aggregate digests include it to bind the same campaign.
There is no wall clock or randomness.

### Dataset objects

| Field | Rule |
|---|---|
| `locale` | `ru` or `en` at its fixed array position |
| `datasetId` | RU: `cmu5mg3pr00simh07epeylc55`; EN: `cmu5nqn1h00vwmi07b4dbk085` |
| `version` | `5.0` |
| `release` | `sps-corpus-5.0-2026-09-11` |
| `licenseId` | `CC0-1.0` |
| `termsReference` | Opaque `terms-` plus 32 lowercase hex characters referencing controlled applicable terms evidence |
| `termsSha256` | SHA-256 of exact terms evidence, populated after authorized acceptance |
| `archiveIdentity` | Provider archive basename matching `[A-Za-z0-9][A-Za-z0-9._-]{0,240}\.tar\.gz`; never a locator or URL |
| `archiveSha256` | SHA-256 of exact authorized downloaded archive |

Actual archive identity/digests are verified later; this profile does not invent or
authenticate a supplied digest. Sample dataset ID/locale references these exact
version/licence/release facts. Root data class and restrictions cover every row.

### Inventory-only root fields

| Field | Rule |
|---|---|
| `candidateCount` | Integer equal to `candidates` length, 0 through 100000 |
| `candidates` | Candidate metadata objects; input order is not semantically significant |

### Candidate fields

| Field | Rule |
|---|---|
| `sampleId` | Opaque `sample-` plus 16 lowercase hex characters, unique throughout inventory |
| `locale`, `datasetId` | Exact paired identity from dataset objects |
| `upstreamRelativePath` | Exact provider-relative path under section 4, private only, globally unique |
| `sourceSplit` | `test` only |
| `audioByteLength` | Integer 1 through 9223372036854775807 |
| `audioSha256`, `referenceTextSha256` | Valid SHA-256 metadata; raw reference text is not an input |
| `durationMs` | Integer 0 through 9223372036854775807; eligibility range is narrower |
| `referenceTextPresent` | Boolean assertion that a non-empty reference exists |
| `decodeResult` | Materializer assertion: `VALIDATED`, `FAILED` or `NOT_RUN` |

Wrong identity/split, malformed or missing hashes, forbidden fields and unsafe paths
fail the inventory instead of silently filtering it. Well-formed rows with duration
outside 1000-20000 ms, absent reference, failed decode or decode not run remain in
inventory but are ineligible. The validator never opens sample paths.

### Selected-manifest-only root fields

| Field | Rule |
|---|---|
| `sampleCount` | Integer `48` |
| `samples` | Exactly 48 rows, RU ranks 1-24 followed by EN ranks 1-24 |
| `candidateInventorySha256` | Digest in section 6, matching supplied inventory |
| `manifestSha256` | Aggregate digest in section 6, excluding itself |

Each selected row contains all candidate fields plus exactly:

| Field | Rule |
|---|---|
| `eligibilityResult` | `ELIGIBLE`, verified from metadata predicates |
| `selectionKey` | Lowercase hex of the 32-byte key in section 5 |
| `rank` | Integer 1-24, independently numbered per locale |

Selected IDs, exact path strings and audio digests are globally unique across all
48 rows. Duplicate audio among the deterministic first 24 fails; do not skip it
and substitute the 25th. Every selected candidate field must equal its inventory row.

## 4. Relative paths and privacy

Paths are valid UTF-8, 1-1024 bytes, separated by `/` and compared exactly.
No case folding, Unicode normalization, trimming, URL decoding, OS normalization or
locale-dependent operation occurs. Different case and canonically equivalent
Unicode sequences remain distinct provider paths.

Reject absolute paths, drive/URI syntax, empty segments, `.` or `..` segments,
backslashes, NUL/control/format/surrogate characters, and Windows device-name segments
(CON, PRN, AUX, NUL, COM1-COM9, LPT1-LPT9, also before an extension).
For this Windows-hosted profile also reject leading/trailing segment whitespace,
trailing dots and these characters: colon, angle brackets, vertical bar, question mark,
asterisk, percent, at sign and double quote. An unsupported provider path requires
reviewed profile changes; never silently rewrite it or omit the row.
Path uniqueness is global across locales; cross-dataset collisions fail closed.

Unknown fields such as `client_id`, contributor names, email, demographics, credentials,
signed URLs, private locators, audio and raw transcripts fail. This constrains structure;
it is not a general PII detector or proof that an upstream basename contains no personal
information. Actual inventories and source-row manifests remain private even when valid.

Controlled reference text, consent/legal-basis reference, retention, expiry/deletion
state and copy inventory remain separate governed materialization records.
This metadata projection does not replace those governance obligations.

## 5. Eligibility and selection

Eligibility requires exact dataset identity and test split, valid hashes, positive
byte length, referenceTextPresent=true, decodeResult=VALIDATED and
`1000 <= durationMs <= 20000` inclusively.

~~~text
SHA-256(UTF8("dora-alpha-asr-v0.1") || 0x00 || UTF8(locale)
        || 0x00 || UTF8(upstreamRelativePath))
~~~

Delimiters are single NUL bytes, not backslash-zero text. Independently for RU and EN,
sort eligible rows by raw 32-byte key ascending, then exact UTF-8 path bytes ascending
for a tie. Take the first 24 per locale; insufficient count in either locale fails.
Concatenate RU then EN. Input order never breaks ties. No random/manual selection
or selection based on ASR outcomes is allowed.

## 6. Canonical JSON and digests

Use Python standard-library
`json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)`,
encoded as strict UTF-8. Keys sort by Unicode scalar order; contract keys are ASCII.
Strings preserve code points with required JSON quote/backslash escaping.
No BOM, indentation, insignificant whitespace or terminal newline is included.
Arrays preserve semantic order.

All numbers are supported signed integer tokens. Reject floats/exponents, NaN/Infinity,
negative zero, duplicate keys (including escaped aliases), invalid UTF-8, unpaired
surrogates and trailing non-JSON data. Input whitespace/key order may differ from
canonical output.

1. **Inventory digest:** copy the parsed inventory and sort only `candidates` by fixed
   locale ordinal (RU=0, EN=1), then exact UTF-8 path bytes. Hash canonical bytes of
   the entire resulting object, excluding no fields. Rows have set semantics with
   this explicit serialization order, so input permutations preserve the digest.
   Dataset arrays already have mandatory RU/EN order.
2. **Manifest digest:** remove only top-level `manifestSha256` and hash canonical bytes
   of the remaining object, including the inventory digest. Mandatory sample order is
   preserved, not normalized after validation.

Render SHA-256 as lowercase hex. Changed candidate/provenance/campaign/selected data
requires matching new digests; digest consistency does not establish authenticity.

## 7. CLI and bounded failure behavior

From repository root with Python 3.10+ and standard library only:

~~~text
python -B tools/alpha_asr_pilot_manifest.py <PRIVATE_INVENTORY_JSON> <PRIVATE_MANIFEST_JSON>
python -B -m unittest tools.test_alpha_asr_pilot_manifest -v
~~~

The first command is for later separately authorized actual-data work; 5.1B exercises it
only with generated metadata. It reads two local regular files and writes no output
file. Obvious UNC/URL inputs and symlink files fail. Do not supply network-mounted paths.
It never accesses candidate paths, archive files, MDC, models or audio. `-B` avoids
Python bytecode cache artifacts.

Success exits 0 and emits canonical JSON with result=PASS, claim=METADATA_CONTRACT_VALID,
counts, aggregate inventory/manifest digests, audioDecodedByValidator=false and
inventoryCompletenessVerified=false. It never echoes sample paths, IDs, sample hashes,
rows, unknown fields or private input locators. Failure emits only result=FAIL and a
stable code on stderr: validation exit 1, usage 2, read/internal error 3; no traceback.

Host bounds: 64 MiB per input, 100000 inventory rows, JSON nesting depth 16,
strings <=4096 code points (path limit tighter), and integer range
[-9223372036854775807, 9223372036854775807]. These are host safety bounds, not quality
claims. Bound failure requires scoped review, never truncation.

## 8. Verification and state

[Generated tests](../../tools/test_alpha_asr_pilot_manifest.py) cover 30 candidates per
locale with independently computed first-24 answers; duration endpoints and ineligible
filtering; unchanged inputs, repeat and permutation; exact identity/profile/policy/
provenance; 23/24 shortages and 24/24 selected counts; hashes, duplicates, forbidden
fields and paths; UTF-8/case; keys/ranks/cherry-picking/metadata tamper; aggregate digests;
JSON ambiguity and read-only content-free CLI exit codes.

Only new-validator host tests plus syntax/diff/privacy/scope checks are run.
Android, Gradle, Recovery, device, model and existing-validator tests are not run.
[Sanitized evidence](../evidence/poc-data-001/alpha-asr-pilot-manifest-validator-local-evidence-stage0-v0.1.json)
records validation results and implementation digests.

The current result is recorded in that evidence and the Alpha data scope document.
5.1A admission/review records retain their dated pre-5.1B snapshot.

~~~text
DATASET_DOWNLOAD = NOT_RUN
ACTUAL_CANDIDATE_INVENTORY = NOT_CREATED
ACTUAL_48_CLIP_MANIFEST = NOT_CREATED
ARCHIVE_HASHES = PENDING_DOWNLOAD_VERIFICATION
5.2 = NOT_STARTED
ASR_QUALITY_CLAIMS = NONE
~~~

Stop at tooling readiness. Production admission, full POC-DATA/POC-ASR readiness,
Recovery, model/runner selection and device execution remain unchanged.
