# DORA Alpha ASR Data Legal/IP Review v0.1

Task: `5.1A-R3 — Project Owner Alpha Data Review`  
Date: 18 September 2026  
State: **INTERNAL_ALPHA_REVIEW_APPROVED / STORAGE_DRY_RUN_PENDING**  
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

Therefore, for this exact bounded Alpha scope the Project Owner explicitly assigns the Project
Owner role to all three Stage-0 reviewer functions:

```text
PROJECT_OWNER = Product Owner
PROJECT_OWNER = Legal/IP Reviewer
PROJECT_OWNER = Stage-0 Engineering/Security Reviewer
SCOPE = BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY
RU_MDC_ID = cmu5mg3pr00simh07epeylc55
EN_MDC_ID = cmu5nqn1h00vwmi07b4dbk085
```

This assignment is Stage-0 Alpha only. It grants no production authority, redistribution,
public re-hosting/re-sharing, or re-identification authority. No separate external reviewer is
required by the applicable Data/ASR governance for this scope.

## 9. Internal Alpha review

The Project Owner, acting in the explicitly assigned Stage-0 roles above, reviews the exact
Mozilla/MDC evidence and records:

1. **CC0-1.0 — ACCEPT.** Both exact candidates are published as CC0-1.0.
2. **ASR evaluation use — ACCEPT.** The dataset purpose expressly includes evaluating ASR models;
   DORA narrows its use to bounded internal evaluation.
3. **MDC/Data Consumer terms — ACCEPT for this scope.** DORA will stay within the applicable
   Data Consumer License and MDC supplemental terms.
4. **No speaker re-identification — ACKNOWLEDGE.** DORA will not attempt identity matching or
   persistent voice identity.
5. **No public re-hosting/re-sharing — ACKNOWLEDGE.** Dataset bytes remain private and are never
   published or mirrored.
6. **Reasonable safeguards — ACCEPT.** The owner-approved controlled-storage/access policy is
   designed for this requirement.
7. **Controlled private storage — ACCEPT.** Only `LOCAL_PRIVATE_CONTROLLED_STORAGE` may hold raw
   pilot bytes; its operational dry-run remains a separate pre-download gate.
8. **Custodian-only access — ACCEPT.**
9. **Raw audio public evidence — PROHIBITED.**
10. **Raw transcript/source excerpts public evidence — PROHIBITED.**
11. **Retention — ACCEPT.** Raw pilot copies are retained only through the 5.1-5.5 evaluation and
    deleted within 30 calendar days after final 5.5 assessment, or earlier if external terms
    require.
12. **Provenance — ACCEPT.** Actual archive identity and SHA-256 are captured immediately after an
    authorized download and before ASR use. No pre-download digest is fabricated.

No unresolved term in the reviewed public Mozilla/MDC material prevents this bounded Alpha
approval. Any additional dataset-specific click-through presented during authorized access must
match this approved scope; a materially conflicting term fails closed before bytes are used.

## 10. Recorded decision

```text
DORA_ALPHA_ASR_DATA_LEGAL_IP_DECISION_V0_1

REVIEWER_NAME = Project Owner
REVIEWER_ROLE = Legal/IP Reviewer; Stage-0 Engineering/Security Reviewer
REVIEW_DATE = 2026-09-18

RU_MDC_ID = cmu5mg3pr00simh07epeylc55
EN_MDC_ID = cmu5nqn1h00vwmi07b4dbk085

INTERNAL_ALPHA_EVALUATION = APPROVE
CC0_COMPATIBILITY = ACCEPT
MDC_TERMS_COMPATIBILITY = ACCEPT
NO_REIDENTIFICATION = ACKNOWLEDGE
NO_PUBLIC_REHOSTING = ACKNOWLEDGE
CONTROLLED_STORAGE = ACCEPT
RETENTION_POLICY = ACCEPT
EVIDENCE_BOUNDARY = ACCEPT
PROVENANCE_CONTROLS = ACCEPT
ENGINEERING_SECURITY_BOUNDARY = ACCEPT
RAW_DATA_PUBLIC_EVIDENCE = PROHIBITED
PRODUCTION_ADMISSION = NOT_REVIEWED
FINAL_DECISION = APPROVE

CONDITIONS =
- SYNTHETIC_STORAGE_ACCESS_DELETION_DRY_RUN must PASS before dataset download.
- Applicable MDC/Data Consumer License text must be accepted by the authorized custodian at the
  later access/download step and must not materially conflict with this reviewed scope.
- Archive identity and SHA-256 must be captured immediately after authorized download and before
  ASR use.

DECISION_NOTES =
Bounded internal Stage-0 Alpha evaluation only. No production use, redistribution, public
re-hosting/re-sharing, re-identification, model training/fine-tuning, or indefinite retention.
```

This decision moves the exact RU/EN dataset candidates to `EVALUATION_APPROVED` for the bounded
Stage-0 Alpha evaluation only. Dataset download remains blocked until the single approved
synthetic storage/access/deletion dry-run passes.

Production Legal/Security remains not reviewed and is not implied by this decision.
