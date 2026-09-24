# DORA Alpha ASR 5.1 — bounded data scope

Task: `POC-DATA-001` / operational Alpha substage `5.1`  
Profile: `dora-alpha-asr-data-v0.1`  
Date: 24 September 2026\
Base: `main@55940df0c95e919a00708ae57e1b8aa23d89b6de`  
State: **5.1 PASS (bounded Alpha data only) / 5.1C PASS (ACTUAL_48_CLIP_CORPUS_MATERIALIZED) / 5.2 NOT_STARTED**

## R2 candidate update — 18 September 2026

Historical admission chronology; current materialization status is recorded in sections 9-10.

The original 3.0 RU/EN entries in the historical v0.1 proposal were never
`EVALUATION_APPROVED`, no 3.0 bytes were downloaded and no 3.0 ASR/device run occurred.

They are now `SUPERSEDED_AS_CANDIDATE`.

The current proposed candidates are:

- `Common Voice Spontaneous Speech 5.0 - Russian`, locale `ru`,
  MDC ID `cmu5mg3pr00simh07epeylc55`;
- `Common Voice Spontaneous Speech 5.0 - English`, locale `en`,
  MDC ID `cmu5nqn1h00vwmi07b4dbk085`.

The exact admission state is governed by
[DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md](DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md),
[DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1.md](DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1.md), and
[DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md](DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md).

The Project Owner policy decisions and internal Stage-0 Legal/IP + Engineering/Security review are approved. Both exact 5.0 candidates are `EVALUATION_APPROVED` for bounded internal Alpha evaluation only. The formerly remaining pre-download engineering gate, `SYNTHETIC_STORAGE_ACCESS_DELETION_DRY_RUN`, passed on 24 September 2026 under task 5.1A-S; see the admission record and its sanitized evidence.

Nothing in this update authorizes dataset download, 5.1B, model selection, runner implementation
or device execution.

## 1. Purpose

Prepare the smallest useful RU/EN evaluation set for the first bounded local-ASR run without
reopening the completed Alpha Recovery campaign and without creating a general-purpose speech
corpus.

This scope exists only to unblock the operational Alpha path `5.1 -> 5.3 -> 5.4 -> 5.5`.
It does not authorize production code, model training, model download, device execution, a full
`POC-DATA-001` PASS, a full `POC-ASR-001` PASS, or Stage 0 go/no-go.

## 2. Evidence already reused

No new work is required for:

- the existing synthetic/generated-text metadata validator and its host evidence;
- the existing synthetic ASR scoring oracle;
- the accepted Alpha Recovery checks in PR #86.

The existing `SyntheticManifestValidator` is intentionally **not** extended here. Its closed
catalogue admits only `SYNTHETIC_SIGNAL` and `GENERATED_TEXT`; treating that validator as proof
for `PUBLIC_LICENSED` audio would be a false claim.

## 3. Data class and forbidden inputs

The only candidate data class for this bounded pilot is `PUBLIC_LICENSED`.

The following are out of scope:

- `REAL_MEETING`;
- new `PURPOSE_RECORDED` collection;
- customer, employer, family or other private recordings;
- call/system audio;
- training, fine-tuning, distillation or speaker enrollment;
- persistent speaker identity or attempts to identify dataset speakers;
- raw audio, transcripts, participant metadata or selected clips in Git, Git LFS or GitHub Actions.

The Project Owner has assigned `Project Owner / Data Custodian` and approved
`LOCAL_PRIVATE_CONTROLLED_STORAGE` with `CUSTODIAN_ONLY` access. The required Data/ASR reviewer
roles are assigned to the Project Owner and the internal review is approved. The concrete
controlled-storage instance passed the synthetic access/deletion dry-run on 24 September 2026.
The separately authorized 5.1C continuation has now materialized the actual pilot in that
controlled storage. The 5.1B contract and validator remain unchanged; see
[the 5.1B contract](DORA_ALPHA_ASR_DATA_MANIFEST_CONTRACT_STAGE0_V0_1.md).

## 4. Current proposed public source

Use one current dataset family/release for both languages:

| Locale | Exact candidate | MDC ID | Task / format | Licence | Published size | State |
|---|---|---|---|---|---:|---|
| `ru` | Common Voice Spontaneous Speech 5.0 - Russian | `cmu5mg3pr00simh07epeylc55` | ASR / MP3 | CC0-1.0 | 88.72 MB | `EVALUATION_APPROVED / ACTUAL_PILOT_MATERIALIZED` |
| `en` | Common Voice Spontaneous Speech 5.0 - English | `cmu5nqn1h00vwmi07b4dbk085` | ASR / MP3 | CC0-1.0 | 519.05 MB | `EVALUATION_APPROVED / ACTUAL_PILOT_MATERIALIZED` |

Both are part of `sps-corpus-5.0-2026-09-11` and published by MDC on 17 September 2026.

The Project Owner internal Stage-0 review recorded on 18 September 2026 moves both exact candidates to `EVALUATION_APPROVED` for this bounded Alpha evaluation only. The storage dry-run is PASS. Dataset retrieval was outside task 5.1A-S and was subsequently completed by the Project Owner under the explicitly authorized 5.1C continuation.

## 5. Minimal exploratory pilot set

After admission is satisfied, materialize exactly **48 clips**:

- 24 Russian clips;
- 24 English clips;
- official test split where the exact release supplies it;
- non-empty reference text;
- decodable audio;
- duration from 1.0 s through 20.0 s inclusive.

Selection must be deterministic and must not cherry-pick successful recognitions:

1. Build the candidate list from the pinned release/split after the filters above.
2. For every candidate compute
   `SHA-256(UTF8("dora-alpha-asr-v0.1") || 0x00 || UTF8(locale) || 0x00 || UTF8(upstreamRelativePath))`.
3. Sort ascending by the raw 32-byte digest, then exact UTF-8 path bytes for a tie.
4. Select the first 24 per locale.
5. Freeze the selected-set manifest before any ASR candidate is executed.

This 48-clip set is an **exploratory Alpha engineering pilot**, not a representative language,
accent, demographic or production-quality benchmark. Mixed RU/EN speech, noise matrices,
speaker-count coverage and statistical support claims remain outside this bounded pilot unless a
later scope explicitly adds them.

Do not retain `client_id`, demographics or other contributor metadata in the Dora pilot
manifest. Keep only the fields required to evaluate ASR and reproduce the selected set.

## 6. Gates before audio retrieval

All of the following are required. Missing evidence means blocked, not PASS.

1. Exact RU/EN MDC IDs and applicable public metadata are frozen — **satisfied**.
2. Project Owner policy values are approved — **satisfied 18 September 2026**.
3. Project Owner is explicitly assigned as Legal/IP Reviewer — **satisfied**.
4. Project Owner is explicitly assigned as Stage-0 Engineering/Security Reviewer — **satisfied**.
5. Internal exact-scope Alpha data review — **APPROVE**.
6. `SYNTHETIC_STORAGE_ACCESS_DELETION_DRY_RUN` in the approved controlled-storage class —
   **PASS on 24 September 2026**, with evidence in the admission record.
7. Applicable MDC/Data Consumer License acceptance is retained as controlled evidence from the
   authorized human access/download continuation; its evidence limitations are recorded below.
8. Actual archive identity/SHA-256 is captured and reverified before materialization and any ASR use.
9. No model/runtime or device execution occurs as part of satisfying these data gates.

## 7. Private selected-set manifest

The controlled manifest must contain, at minimum:

- dataset ID and exact release/version;
- locale and upstream split;
- opaque Dora sample ID;
- upstream relative path retained only in controlled evidence;
- audio byte length and SHA-256;
- reference-text SHA-256 plus controlled reference text;
- duration;
- `dataClass=PUBLIC_LICENSED`;
- `licenseId=CC0-1.0`;
- exact terms digest/reference;
- `consentReference=not-applicable-public-licensed-source`;
- `trainingAllowed=false`;
- `publicRedistributionAllowed=false`;
- storage class/evidence locator;
- creation/expiry/deletion state.

The 5.1B validator consumes a closed metadata projection of the candidate inventory and
selected manifest. Controlled reference text and lifecycle/consent records remain separate;
they are not fields in those two validator inputs. Its exact contract is defined in
[DORA_ALPHA_ASR_DATA_MANIFEST_CONTRACT_STAGE0_V0_1.md](DORA_ALPHA_ASR_DATA_MANIFEST_CONTRACT_STAGE0_V0_1.md).

The public repository may later contain only a Git-safe projection: release identities, counts,
licence/terms references, the aggregate selected-manifest digest, limitations and result totals.
It must not contain selected audio, raw reference transcripts, contributor metadata, private
locators or signed URLs.

## 8. Minimal verification gate for 5.1 materialization

Do **not** create a large new test matrix.

When the actual selected-set manifest exists, one deterministic validator is sufficient if it
proves all of the following:

- exactly 48 unique samples and exactly 24 per locale;
- only `ru` and `en` in this pilot;
- every row is `PUBLIC_LICENSED`, test-split, hash-complete and within the declared duration
  bounds;
- exact source/release/licence/terms pins are present;
- `trainingAllowed=false` and `publicRedistributionAllowed=false`;
- no duplicate sample ID, audio digest or upstream path;
- no forbidden contributor/demographic/private-location fields;
- the selection recomputes deterministically from the frozen candidate list;
- the aggregate manifest digest matches the recorded value.

No Android/device test is required for substage 5.1. Device execution belongs to 5.4 after 5.1,
5.2 and 5.3 pass their own gates.

## 9. Exit and claim ceiling

Current state:

- 3.0 candidates: `SUPERSEDED_AS_CANDIDATE`;
- 5.0 RU/EN candidates: `EVALUATION_APPROVED / ACTUAL_PILOT_MATERIALIZED`;
- data retrieval: `COMPLETED_BY_PROJECT_OWNER / CONTROLLED_TRANSFER_VERIFIED`;
- actual candidate inventory: `FROZEN / COMPLETE_PROVIDER_TEST_SPLITS`;
- actual 48-clip selected-set manifest: `MATERIALIZED / VALIDATOR_PASS`;
- owner-controlled decisions: `APPROVED`;
- internal Stage-0 Legal/IP review: `APPROVED`;
- internal Stage-0 Engineering/Security review: `APPROVED`;
- custodian/storage/access policy: `APPROVED`;
- `SYNTHETIC_STORAGE_ACCESS_DELETION_DRY_RUN`: `PASS`;
- 5.1A: `PASS`;
- 5.1B: `PASS / MANIFEST_CONTRACT_AND_VALIDATOR_READY`;
- 5.1C: `PASS / ACTUAL_48_CLIP_CORPUS_MATERIALIZED`;
- 5.1: `PASS / BOUNDED_ALPHA_DATA_SCOPE_ONLY`;
- 5.2: `NOT_STARTED`;
- archive hashes: `VERIFIED`;
- production admission: unchanged;
- 5.4 device ASR: `NOT_AUTHORIZED`.

Task 5.1B ends with contract/validator readiness only, supported by
[synthetic host evidence](../evidence/poc-data-001/alpha-asr-pilot-manifest-validator-local-evidence-stage0-v0.1.json).
That historical record remains tooling evidence only. Actual 5.1C materialization is recorded
separately below. Do not begin 5.2, model download or device execution without later explicit
scope and the applicable remaining gates.


## 10. 5.1C actual materialization — 24 September 2026

The Project Owner completed official MDC human access and downloaded the two exact approved
5.0 archives. Source and controlled-destination byte sizes/SHA-256 matched; only then were the
Downloads copies deleted and their absence verified. Both archives and all extracted files
remain in the approved EFS-protected, custodian-only storage outside Git/worktrees and the
checked sync/public roots. No dataset was downloaded again by the materializer.

[Sanitized actual materialization evidence](../evidence/poc-data-001/alpha-asr-actual-pilot-materialization-stage0-v0.1.json)
records actual archive basenames, bytes, hashes, decoder provenance and aggregate results.
The actual downloaded basenames differ from the earlier public catalogue display names;
owner attestation, official browser referrer, download basename, internal release/locale and
provider split counts corroborate identity. No publisher-signed checksum comparison is claimed.

| Check | RU | EN |
|---|---:|---:|
| Provider index rows | 783 | 5898 |
| Complete provider test inventory | 385 | 357 |
| Successfully probed and fully decoded | 385 | 357 |
| Eligible under unchanged 5.1B rules | 214 | 244 |
| Deterministically selected | 24 | 24 |

All archive members passed traversal/link/device/type checks before controlled extraction;
gzip integrity passed. Only exact provider test rows entered the inventory. FFmpeg/ffprobe
9.0.2 from the explicitly owner-authorized user-scope `Gyan.FFmpeg` WinGet installation proved
MP3 metadata and full decode-to-null, with file-only input and bounded execution. Measured
durations, rounded half up to milliseconds, matched provider durations for every test row.
No PCM files, ASR output or model were created. This host utility is not an admitted production
dependency.

The complete 742-row inventory was frozen before selection. The unchanged first-24-per-locale
algorithm selected 48 unique clips without substitutions. The existing validator CLI returned
PASS against the actual private inventory/manifest; all 21 existing host tests passed.
Full-inventory membership was also independently compared with every provider test row.
The validator's own `audioDecodedByValidator=false` and `inventoryCompletenessVerified=false`
remain unchanged; separate materialization evidence establishes those external facts.

Controlled reference text, consent/legal-basis and lifecycle/copy records are present separately
from the validator projection. Retention remains
`ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS`, or earlier when no longer required or
applicable terms require it. Selected audio references the existing protected extracted files.
No raw audio, transcripts, TSV, actual inventory/manifest, selected source locators, contributor
metadata, private paths or decoder binaries are published.

Terms evidence consists of the Project Owner's dated confirmation of completed official human
access/download plus retrieved [official MDC consumer terms](https://mozilladatacollective.com/terms/consumers)
and the dataset-specific conditions observed on the official pages. It does not fabricate an
acceptance-screen capture or precise acceptance timestamp. The private evidence digest binds
both manifest inputs. No newly conflicting condition was discovered.

The section 8 acceptance criteria and separate controlled lifecycle obligations are satisfied:
`5.1 = PASS` for this bounded exploratory Alpha data pilot only. Full POC-DATA/POC-ASR readiness,
ASR quality, representative-language claims, production admission and Recovery are unchanged.
`5.2 = NOT_STARTED`; `MODEL_DOWNLOAD = NOT_RUN`; `ASR_INFERENCE = NOT_RUN`.
