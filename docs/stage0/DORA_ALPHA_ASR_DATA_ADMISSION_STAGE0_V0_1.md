# DORA Alpha ASR 5.1A — bounded data admission

Task: `5.1A-L — Legal/IP Review Preparation`  
Backlog: `POC-DATA-001`  
Date: 18 September 2026  
Predecessor: `d04966b37cd95a74795d057d8c68d331fd5b91a9`  
Candidate profile: `dora-alpha-asr-data-admission-v0.3`  
Result: **BLOCKED_LEGAL_IP**

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
| State | `PROPOSED` | `PROPOSED` |

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

No named Common Voice Legal/IP reviewer is currently assigned.

No general dataset rule requires reviewer-role separation. Recovery contains a package-specific
distinct Engineering/Security requirement, which does not transfer automatically to this scope.

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

## 6. Legal/IP gate

`LEGAL_IP_REVIEWER = UNASSIGNED`

`LEGAL_IP_APPROVAL = BLOCKED`

The short review packet is:
[DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md](DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md).

A named human Legal/IP reviewer must be assigned by the Project Owner and must return an exact-scope
decision for the two MDC IDs, CC0-1.0, current MDC Data Consumer Terms, and any access-step licence
text.

Engineering cannot self-approve this legal/data-rights interpretation.

## 7. Engineering/Security gate

General `GOV-IP-001` includes Engineering/Security reviewer functions in artifact state and
provenance.

For this dataset scope:

- Engineering/Security is separate from the Legal/IP opinion;
- no general rule requires a distinct second person;
- one human may serve multiple roles only if explicitly assigned to each role;
- prior Recovery reviewer assignments do not carry over;
- exact archive digest remains `PENDING_DOWNLOAD_VERIFICATION` until an authorized download.

No Engineering/Security reviewer is silently assigned by this document.

## 8. Current mandatory pre-download gates

| Gate | State |
|---|---|
| Exact RU/EN 5.0 candidate IDs and public terms | PASS |
| Owner-controlled policy decisions | PASS |
| Named Legal/IP reviewer | BLOCKED |
| Legal/IP exact-scope decision | BLOCKED |
| Named Engineering/Security reviewer function for artifact/provenance state | BLOCKED / UNASSIGNED |
| Concrete controlled-storage instance conforming to approved class | NOT_RUN |
| Synthetic access/deletion dry-run in that controlled storage | NOT_RUN |
| Applicable Data Consumer License/click-through acceptance by authorized custodian | NOT_RUN |
| Dataset download | NOT_AUTHORIZED |
| Archive SHA-256 | PENDING_DOWNLOAD_VERIFICATION |

Production admission is not a 5.1A pre-download gate.

## 9. Current verdict

### RU

`PROPOSED / BLOCKED_LEGAL_IP`

### EN

`PROPOSED / BLOCKED_LEGAL_IP`

### Overall

**`5.1A = BLOCKED_LEGAL_IP`**

Owner-controlled decisions are closed. The dataset candidates remain `PROPOSED` because the
mandatory named Legal/IP reviewer and exact-scope decision are absent.

No dataset download, 5.1B, model work, runner implementation or device execution is authorized.

## 10. Next safe action

The next safe action is reviewer assignment and return of the exact Legal/IP decision form.

After the required reviewer function(s) approve the exact scope, instantiate the approved
`LOCAL_PRIVATE_CONTROLLED_STORAGE` and run the single synthetic access/deletion dry-run.

Do not start 5.1B in this task.
