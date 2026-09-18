# DORA Alpha ASR Data Legal/IP Review v0.1

Task: `5.1A-L — Legal/IP Review Preparation`  
Date: 18 September 2026  
State: **REVIEW_PREPARED / LEGAL_IP_REVIEWER_UNASSIGNED**  
Admission record: [DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md](DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md)  
Owner decision: [DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1.md](DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1.md)

## 1. Scope

Review only whether the exact Mozilla Data Collective datasets below may be used for:

`BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY`

Pilot:

- 48 clips total;
- 24 Russian;
- 24 English.

Not in this review:

- production use or production admission;
- public redistribution or hosting;
- model selection;
- ASR runner implementation;
- Android/device execution;
- Recovery;
- training/fine-tuning/model improvement.

No dataset bytes have been downloaded.

## 2. Exact artifacts

### Russian

- name: `Common Voice Spontaneous Speech 5.0 - Russian`
- locale: `ru`
- version: `5.0`
- MDC ID: `cmu5mg3pr00simh07epeylc55`
- canonical URL:
  `https://mozilladatacollective.com/datasets/cmu5mg3pr00simh07epeylc55`
- license: `CC0-1.0`
- task: ASR
- published size: 88.72 MB
- archive name: `PENDING_DOWNLOAD_VERIFICATION`
- archive SHA-256: `PENDING_DOWNLOAD_VERIFICATION`

### English

- name: `Common Voice Spontaneous Speech 5.0 - English`
- locale: `en`
- version: `5.0`
- MDC ID: `cmu5nqn1h00vwmi07b4dbk085`
- canonical URL:
  `https://mozilladatacollective.com/datasets/cmu5nqn1h00vwmi07b4dbk085`
- license: `CC0-1.0`
- task: ASR
- published size: 519.05 MB
- archive name: `common-voice-spontaneous-speech-5-0-engl-97a82389.tar.gz`
- archive SHA-256: `PENDING_DOWNLOAD_VERIFICATION`

The archive hashes are intentionally not invented before authorized download.

## 3. Authoritative sources

Review against only these Mozilla/MDC sources:

1. Common Voice / MDC dataset catalogue:
   `https://commonvoice.mozilla.org/en/datasets`
2. EN canonical dataset page:
   `https://mozilladatacollective.com/datasets/cmu5nqn1h00vwmi07b4dbk085`
3. RU canonical URL and immutable ID from Mozilla Data Collective's official public
   `dataset-schema-registry`:
   `Mozilla-Data-Collective/dataset-schema-registry@6be2d48cfae2f7d7c15df05e1d6fdfc97d965339`
4. Mozilla Data Collective Data Consumer Terms of Use, last updated 6 May 2026:
   `https://mozilladatacollective.com/terms/consumers`

Any additional Data Consumer License/click-through text shown at authorized access/download must be
reviewed and retained in controlled evidence before dataset bytes are used.

## 4. Known licence and use terms

Known from the authoritative sources:

- both exact 5.0 candidates are listed as `CC0-1.0`;
- intended use includes training and evaluating ASR models; DORA uses only bounded evaluation here;
- Common Voice forbids attempts to determine speaker identity;
- Common Voice forbids re-hosting or re-sharing the dataset;
- MDC requires a Data Consumer Account;
- before access/download, the Data Consumer must review and accept the applicable Data Consumer
  License;
- MDC supplemental terms apply in addition to that Data Consumer License;
- dataset use must remain within the applicable licence;
- re-identification is prohibited;
- scraping/mirroring/redistribution and bypassing access controls are prohibited except where the
  applicable Data Consumer License expressly permits an action;
- reasonable safeguards against unauthorized access, use or disclosure are required;
- on MDC account termination, use must cease and copies must be deleted/destroyed subject to the
  applicable Data Consumer License;
- CC0 itself does not impose an attribution requirement; DORA nevertheless preserves exact
  provenance;
- any additional candidate-specific notice shown at access/download remains a required review item
  before use.

## 5. DORA handling already approved by Project Owner

### Raw audio

Only `LOCAL_PRIVATE_CONTROLLED_STORAGE`:

- outside Git and Git worktrees;
- never Git/Git LFS;
- never CI artifact;
- never public/shared evidence;
- no public/shared Drive location;
- custodian-controlled access only.

### Raw transcripts/source excerpts

Same public-disclosure restriction:

- never public Git/PR/CI evidence;
- retain only in controlled storage when needed for the bounded evaluation.

### Durable evidence

May contain only safe metadata/evidence consistent with Dataset Governance, including:

- MDC dataset IDs and exact version/release identity;
- archive identity and SHA-256 after authorized download;
- opaque selected clip IDs and hashes only when their publication is non-linkable and allowed;
- WER;
- RTF;
- RAM/PSS;
- thermal observations;
- battery/energy metrics;
- aggregate result counts and content-free error categories.

Do not publish raw audio, raw transcript/source excerpts, contributor demographics, private
storage locators, credentials or signed URLs.

## 6. Retention

Owner-approved policy:

`ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS`

- retain raw pilot copies only while 5.1-5.5 evaluation is active;
- delete raw copies within 30 calendar days after final 5.5 assessment;
- delete earlier when no longer required;
- any stricter applicable MDC/account/Data Consumer License requirement wins;
- derived non-sensitive aggregate metrics and approved non-linkable provenance hashes may remain.

## 7. Prohibited actions

At minimum:

- speaker identification or re-identification;
- public redistribution, mirroring, re-hosting or re-sharing;
- unrelated product use;
- production admission through this review;
- training/fine-tuning/model improvement under this scope;
- indefinite/uncontrolled retention;
- raw dataset/audio/transcript in Git, CI or public evidence;
- bypass of MDC access controls or license-acceptance flow.

## 8. Exact DORA authority

The controlling `GOV-IP-001` rule states:

> Only named Product/Legal/IP and Engineering/Security reviewers may move an artifact to
> `EVALUATION_APPROVED` or `ADMITTED`.

The same policy assigns roles as follows:

- Product owner: approves product scope, market/channel and risk/fallback; **names Legal/IP
  reviewer**.
- Legal/IP reviewer: interprets exact terms, redistribution, attribution and dataset/consent
  compatibility.
- Engineering owner: verifies exact artifact, build/digest/dependencies/replacement and technical
  obligations.
- Security owner: reviews supply-chain/security/evidence-handling obligations.

It also states that engineering must not substitute its own legal approval.

### Engineering/Security boundary for this review

A named Engineering/Security reviewer function is part of the general `GOV-IP-001` artifact
state/provenance process. It is separate from the Legal/IP interpretation requested by this packet.

No general `GOV-IP-001` rule was found requiring the Legal/IP reviewer and Engineering/Security
reviewer to be different people for datasets. The explicit `distinct accountable
Engineering/Security reviewer` language found in the policy is Recovery-specific and is not
silently generalized to Common Voice.

Therefore:

- one person may hold multiple reviewer roles only if the Project Owner explicitly assigns that
  person to each role;
- a prior Recovery reviewer has no authority here merely because they reviewed Recovery;
- this packet does not assign any reviewer.

## 9. Reviewer assignment status

`LEGAL_IP_REVIEWER = UNASSIGNED`

No current DORA Data/ASR governance record names a Legal/IP reviewer for these two Common Voice
datasets.

The Project Owner must explicitly assign a named human to:

`Legal/IP Reviewer — Common Voice 5.0 RU/EN bounded internal Alpha ASR evaluation`

If the same human will also satisfy the Engineering/Security reviewer function, that second role
must be explicitly assigned as well. Role separation is not mandated by the general dataset policy,
but role assignment cannot be inferred.

## 10. Legal/IP decision form

Do not pre-fill approval.

```text
DORA_ALPHA_ASR_DATA_LEGAL_IP_DECISION_V0_1

REVIEWER_NAME =
REVIEWER_ROLE = Legal/IP Reviewer
REVIEW_DATE =

RU_MDC_ID = cmu5mg3pr00simh07epeylc55
EN_MDC_ID = cmu5nqn1h00vwmi07b4dbk085

INTERNAL_ALPHA_EVALUATION =
[APPROVE / REJECT]

CC0_COMPATIBILITY =
[ACCEPT / REJECT]

MDC_TERMS_COMPATIBILITY =
[ACCEPT / REJECT]

NO_REIDENTIFICATION =
[ACKNOWLEDGE]

NO_PUBLIC_REHOSTING =
[ACKNOWLEDGE]

CONTROLLED_STORAGE =
[ACCEPT / REJECT]

RETENTION_POLICY =
[ACCEPT / REJECT]

RAW_DATA_PUBLIC_EVIDENCE =
[PROHIBITED]

PRODUCTION_ADMISSION =
[NOT_REVIEWED]

CONDITIONS =
DECISION_NOTES =
```

An `APPROVE` decision in this form resolves the Legal/IP interpretation only for this exact
bounded Alpha scope. It does not itself prove the synthetic storage deletion/access dry-run,
authorize production use, or supply post-download archive hashes.
