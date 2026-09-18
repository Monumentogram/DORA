# DORA Alpha ASR 5.1 — bounded data scope

Task: `POC-DATA-001` / operational Alpha substage `5.1`  
Profile: `dora-alpha-asr-data-v0.1`  
Date: 18 September 2026  
Base: `main@55940df0c95e919a00708ae57e1b8aa23d89b6de`  
State: **PREPARATION_ONLY / BLOCKED_BEFORE_AUDIO_RETRIEVAL**

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

Because controlled non-public storage and a named data custodian are not yet operational, **no
audio may be retrieved by this task until the gates in section 6 are satisfied**.

## 4. Proposed public source for the first RU/EN pilot

Use one dataset family/release for both languages to reduce avoidable source differences:

| Locale | Exact candidate | Task / format | Catalogue license | Catalogue size | State |
|---|---|---|---|---:|---|
| `ru` | Mozilla Common Voice Spontaneous Speech 3.0 — Russian | ASR / MP3 | CC0-1.0 | 62.64 MB | `PROPOSED` |
| `en` | Mozilla Common Voice Spontaneous Speech 3.0 — English | ASR / MP3 | CC0-1.0 | 459.05 MB | `PROPOSED` |

Canonical metadata sources checked for this proposal:

- Mozilla Common Voice / Mozilla Data Collective dataset catalogue:
  `https://commonvoice.mozilla.org/en/datasets`;
- Common Voice Legal Terms, effective 31 October 2025:
  `https://commonvoice.mozilla.org/terms`.

The current Common Voice terms expose datasets through Mozilla Data Collective under CC0 unless
otherwise specified and impose dataset-use constraints including no speaker identification and no
re-hosting/re-sharing of the dataset. Dora therefore sets
`publicRedistributionAllowed=false` for this pilot even when the underlying licence is CC0.

This section is metadata research only. It does **not** move either dataset from `PROPOSED` to
`EVALUATION_APPROVED`.

## 5. Minimal exploratory pilot set

After section 6 is satisfied, materialize exactly **48 clips**:

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

All of the following are required. Missing evidence means `BLOCKED`, not PASS.

1. The exact RU and EN Mozilla Data Collective dataset pages/file identities are recorded.
2. The exact applicable licence/terms snapshot is retained with an immutable digest.
3. Product/Legal/IP records `EVALUATION_APPROVED` for these exact dataset releases and this ASR
   evaluation purpose.
4. A data custodian is assigned in controlled private evidence.
5. Controlled non-public storage, access and deletion procedure are operational.
6. Retrieval/storage does not use Git, Git LFS, GitHub Actions artifacts, personal messaging or an
   unapproved personal cloud location.
7. The collection/materialization plan names retention and deletion checkpoints before download.
8. No model/runtime execution occurs as part of satisfying these data gates.

## 7. Private selected-set manifest

The controlled manifest must contain, at minimum:

- dataset id and exact release/version;
- locale and upstream split;
- opaque Dora sample id;
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
- no duplicate sample id, audio digest or upstream path;
- no forbidden contributor/demographic/private-location fields;
- the selection recomputes deterministically from the frozen candidate list;
- the aggregate manifest digest matches the recorded value.

No Android/device test is required for substage 5.1. Device execution belongs to 5.4 after 5.1,
5.2 and 5.3 pass their own gates.

## 9. Exit and claim ceiling

This document starts 5.1 but does not close it.

Current result after this document:

- source family: proposed;
- data retrieval: `NOT_RUN`;
- selected-set manifest: `NOT_CREATED`;
- external data rights: `NOT_APPROVED`;
- controlled storage/custodian: `BLOCKED`;
- 5.1 overall: `BLOCKED / IN_PROGRESS`;
- 5.4 device ASR: `NOT_AUTHORIZED`.

The next evidence-producing action is to satisfy section 6 and then materialize the exact 48-clip
manifest once. Re-running or expanding Recovery, creating hundreds of ASR cases, or collecting new
private speech is not part of this task.
