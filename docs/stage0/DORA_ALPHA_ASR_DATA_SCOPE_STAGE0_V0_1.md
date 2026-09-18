# DORA Alpha ASR 5.1 — bounded data scope

Task: `POC-DATA-001` / operational Alpha substage `5.1`  
Profile: `dora-alpha-asr-data-v0.1`  
Date: 18 September 2026  
Base: `main@55940df0c95e919a00708ae57e1b8aa23d89b6de`  
State: **PREPARATION_ONLY / BLOCKED_BEFORE_AUDIO_RETRIEVAL**

## R2 candidate update — 18 September 2026

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

The Project Owner policy decisions and internal Stage-0 Legal/IP + Engineering/Security review are approved. Both exact 5.0 candidates are `EVALUATION_APPROVED` for bounded internal Alpha evaluation only. The single remaining pre-download engineering gate is `SYNTHETIC_STORAGE_ACCESS_DELETION_DRY_RUN`.

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
`LOCAL_PRIVATE_CONTROLLED_STORAGE` with `CUSTODIAN_ONLY` access. The concrete controlled-storage
instance has not yet passed the required synthetic access/deletion dry-run, and the required
Data/ASR reviewer roles remain unassigned. **No audio may be retrieved until the remaining
admission gates are satisfied**.

## 4. Current proposed public source

Use one current dataset family/release for both languages:

| Locale | Exact candidate | MDC ID | Task / format | Licence | Published size | State |
|---|---|---|---|---|---:|---|
| `ru` | Common Voice Spontaneous Speech 5.0 - Russian | `cmu5mg3pr00simh07epeylc55` | ASR / MP3 | CC0-1.0 | 88.72 MB | `EVALUATION_APPROVED / DOWNLOAD_BLOCKED_STORAGE_DRY_RUN` |
| `en` | Common Voice Spontaneous Speech 5.0 - English | `cmu5nqn1h00vwmi07b4dbk085` | ASR / MP3 | CC0-1.0 | 519.05 MB | `EVALUATION_APPROVED / DOWNLOAD_BLOCKED_STORAGE_DRY_RUN` |

Both are part of `sps-corpus-5.0-2026-09-11` and published by MDC on 17 September 2026.

The Project Owner internal Stage-0 review recorded on 18 September 2026 moves both exact candidates to `EVALUATION_APPROVED` for this bounded Alpha evaluation only. Download remains blocked until the storage dry-run passes.

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
   `SHA-256("dora-alpha-asr-v0.1\\0" + locale + "\\0" + upstreamRelativePath)`.
3. Sort ascending by that digest.
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
   **NOT_RUN / BLOCKING**.
7. Applicable MDC/Data Consumer License acceptance occurs only in the later authorized download
   transaction and must be retained as controlled evidence.
8. Archive identity/SHA-256 is captured immediately after authorized download and before ASR use.
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
- 5.0 RU/EN candidates: `EVALUATION_APPROVED / DOWNLOAD_BLOCKED_STORAGE_DRY_RUN`;
- data retrieval: `NOT_RUN`;
- selected-set manifest: `NOT_CREATED`;
- owner-controlled decisions: `APPROVED`;
- internal Stage-0 Legal/IP review: `APPROVED`;
- internal Stage-0 Engineering/Security review: `APPROVED`;
- custodian/storage/access policy: `APPROVED`;
- `SYNTHETIC_STORAGE_ACCESS_DELETION_DRY_RUN`: `NOT_RUN / BLOCKING`;
- 5.1A: `BLOCKED_STORAGE_DRY_RUN`;
- 5.4 device ASR: `NOT_AUTHORIZED`.

Do not begin 5.1B, 5.2, dataset/model download or device execution until the admission record's
mandatory gates are closed.
