# DORA Alpha ASR 5.1A — bounded data admission

Task: `5.1A-R3 — Project Owner Alpha Data Review`  
Backlog: `POC-DATA-001`  
Date: 18 September 2026  
Predecessor: `170f073c2b401cdae7c966fa5ea0bdc3a1744bff`  
Candidate profile: `dora-alpha-asr-data-admission-v0.4`  
Result: **BLOCKED_STORAGE_DRY_RUN**

## 1. Scope and chronology

The current proposed candidates are:

- `Common Voice Spontaneous Speech 5.0 - Russian`
  (`ru`, MDC ID `cmu5mg3pr00simh07epeylc55`);
- `Common Voice Spontaneous Speech 5.0 - English`
  (`en`, MDC ID `cmu5nqn1h00vwmi07b4dbk085`).

The previous Common Voice Spontaneous Speech 3.0 RU/EN candidates remain historical
`SUPERSEDED_AS_CANDIDATE` entries. They were never `EVALUATION_APPROVED`; no 3.0 bytes were
downloaded and no 3.0 ASR/device execution occurred.

No 5.0 dataset bytes have been downloaded.

## 2. Exact candidate facts

| Field | RU | EN |
|---|---|---|
| Name | Common Voice Spontaneous Speech 5.0 - Russian | Common Voice Spontaneous Speech 5.0 - English |
| Locale | `ru` | `en` |
| Version | `5.0` | `5.0` |
| Release family | `sps-corpus-5.0-2026-09-11` | same |
| Release date | 2026-09-17 | 2026-09-17 |
| MDC ID | `cmu5mg3pr00simh07epeylc55` | `cmu5nqn1h00vwmi07b4dbk085` |
| Task / format | ASR / MP3 | ASR / MP3 |
| Published size | 88.72 MB | 519.05 MB |
| License | CC0-1.0 | CC0-1.0 |
| Archive filename | `PENDING_DOWNLOAD_VERIFICATION` | `common-voice-spontaneous-speech-5-0-engl-97a82389.tar.gz` |
| Archive SHA-256 | `PENDING_DOWNLOAD_VERIFICATION` | `PENDING_DOWNLOAD_VERIFICATION` |
| State | `EVALUATION_APPROVED / DOWNLOAD_BLOCKED_STORAGE_DRY_RUN` | `EVALUATION_APPROVED / DOWNLOAD_BLOCKED_STORAGE_DRY_RUN` |

Archive hashes are post-authorized-download provenance evidence and are not fabricated as a
pre-download requirement.

## 3. External terms summary

Authoritative Mozilla/MDC sources establish:

- CC0-1.0 for both proposed datasets;
- intended ASR evaluation use;
- no speaker re-identification;
- no re-hosting/re-sharing;
- MDC Data Consumer Account and applicable Data Consumer License acceptance before access/download;
- MDC supplemental dataset-use terms in addition to the provider license;
- use only within the applicable licence;
- no mirroring/redistribution or access-control bypass except where expressly permitted by the
  applicable Data Consumer License;
- reasonable safeguards against unauthorized access/use/disclosure;
- cease use and delete/destroy held copies on account termination, subject to the applicable
  Data Consumer License.

CC0 itself imposes no attribution requirement; DORA still records provenance.

Any additional access-step licence text must be reviewed and retained in controlled evidence before
bytes are used.

## 4. DORA authority

`GOV-IP-001` controls the state transition.

It states that only named Product/Legal/IP and Engineering/Security reviewers may move an artifact
to `EVALUATION_APPROVED` or `ADMITTED`.

It assigns:

- Product owner: scope/risk/fallback and naming the Legal/IP reviewer;
- Legal/IP reviewer: exact terms, redistribution, attribution and dataset/consent compatibility;
- Engineering owner: exact artifact/digest and technical obligations;
- Security owner: security/evidence handling.

For this exact bounded Alpha scope, the Project Owner explicitly assigns:

- `PROJECT_OWNER = Product Owner`;
- `PROJECT_OWNER = Legal/IP Reviewer`;
- `PROJECT_OWNER = Stage-0 Engineering/Security Reviewer`.

No general dataset rule requires reviewer-role separation. Recovery contains a package-specific
distinct Engineering/Security requirement, which does not transfer to this Data/ASR scope.

The internal Alpha review is recorded as `FINAL_DECISION = APPROVE` in
[DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md](DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md).

## 5. Owner gate

The Project Owner has now explicitly approved
[DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1.md](DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1.md):

- `DATASET_USE = APPROVE`;
- `PILOT_SIZE_48 = APPROVE`;
- `CUSTODIAN = Project Owner / Data Custodian`;
- `STORAGE_CLASS = LOCAL_PRIVATE_CONTROLLED_STORAGE`;
- `ACCESS = CUSTODIAN_ONLY`;
- `RETENTION = ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS`;
- `PRE_DOWNLOAD_SYNTHETIC_DELETION_DRY_RUN = REQUIRED`.

Scope:

`BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY`

Therefore all owner-controlled **policy decisions** in the R2 packet are closed.

This does not mean all pre-download controls are executed: a concrete compliant controlled
location must still be instantiated and the required synthetic access/deletion dry-run must pass.

`OWNER_APPROVED != EVALUATION_APPROVED`

## 6. Internal Stage-0 review gate — CLOSED

The Project Owner is explicitly assigned as Product Owner, Legal/IP Reviewer and Stage-0
Engineering/Security Reviewer for this exact bounded Alpha scope.

Review result:

- `INTERNAL_ALPHA_EVALUATION = APPROVE`;
- `CC0_COMPATIBILITY = ACCEPT`;
- `MDC_TERMS_COMPATIBILITY = ACCEPT`;
- `NO_REIDENTIFICATION = ACKNOWLEDGE`;
- `NO_PUBLIC_REHOSTING = ACKNOWLEDGE`;
- `CONTROLLED_STORAGE = ACCEPT`;
- `RETENTION_POLICY = ACCEPT`;
- `EVIDENCE_BOUNDARY = ACCEPT`;
- `PROVENANCE_CONTROLS = ACCEPT`;
- `ENGINEERING_SECURITY_BOUNDARY = ACCEPT`;
- `RAW_DATA_PUBLIC_EVIDENCE = PROHIBITED`;
- `PRODUCTION_ADMISSION = NOT_REVIEWED`;
- `FINAL_DECISION = APPROVE`.

The exact candidates are therefore `EVALUATION_APPROVED` for bounded internal Stage-0 Alpha
evaluation only.

This is not production Legal/Security approval. Exact archive SHA-256 remains
`PENDING_DOWNLOAD_VERIFICATION` until the later authorized download and is checked before ASR
use.

## 8. Current mandatory pre-download gates

| Gate | State |
|---|---|
| Exact RU/EN 5.0 candidate IDs and public terms | PASS |
| Owner-controlled policy decisions | PASS |
| Stage-0 Legal/IP reviewer assignment | PASS |
| Stage-0 Legal/IP exact-scope decision | PASS |
| Stage-0 Engineering/Security reviewer assignment and boundary | PASS |
| `SYNTHETIC_STORAGE_ACCESS_DELETION_DRY_RUN` | NOT_RUN / BLOCKING |
| Applicable Data Consumer License/click-through acceptance | PENDING_AT_AUTHORIZED_DOWNLOAD |
| Dataset download | NOT_AUTHORIZED_UNTIL_DRY_RUN |
| Archive identity/SHA-256 | PENDING_DOWNLOAD_VERIFICATION |

Production admission is not a 5.1A pre-download gate.

## 9. Current verdict

### RU

`EVALUATION_APPROVED / DOWNLOAD_BLOCKED_STORAGE_DRY_RUN`

### EN

`EVALUATION_APPROVED / DOWNLOAD_BLOCKED_STORAGE_DRY_RUN`

### Overall

**`5.1A = BLOCKED_STORAGE_DRY_RUN`**

The owner and internal Stage-0 review gates are closed. The only remaining engineering gate before
dataset download is `SYNTHETIC_STORAGE_ACCESS_DELETION_DRY_RUN`.

MDC/Data Consumer License acceptance is a transactional access condition at the later authorized
download; it must be retained as controlled evidence and must not materially conflict with the
reviewed scope. Archive identity/SHA-256 is captured immediately after that authorized download and
before ASR use.

No dataset download, 5.1B, model work, runner implementation or device execution is authorized by
this review.

## 10. Next safe action

Execute exactly one minimal synthetic storage/access/deletion dry-run in the approved
`LOCAL_PRIVATE_CONTROLLED_STORAGE`.

Do not start 5.1B until that dry-run passes.
