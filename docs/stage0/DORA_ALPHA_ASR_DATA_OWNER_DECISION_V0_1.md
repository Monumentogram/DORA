# DORA Alpha ASR Data Owner Decision v0.1

Task: `5.1A-R2 — Exact Common Voice 5.0 Candidate Freeze + Owner Decision Packet`  
Date: 18 September 2026  
State: **PROPOSED / OWNER_DECISION_REQUIRED**  
Admission record: [DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md](DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md)

## 1. Decision scope

This packet contains only owner-controlled choices needed to prepare the bounded internal Alpha
ASR data path. It does not replace the mandatory `GOV-IP-001` Legal/IP review and does not
authorize production admission.

## 2. Dataset and purpose

**RECOMMENDED VALUE**

Approve as the exact proposed Alpha evaluation inputs:

- `Common Voice Spontaneous Speech 5.0 - Russian`
  - MDC ID: `cmu5mg3pr00simh07epeylc55`
  - locale: `ru`
- `Common Voice Spontaneous Speech 5.0 - English`
  - MDC ID: `cmu5nqn1h00vwmi07b4dbk085`
  - locale: `en`

Purpose:

`BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY`

No production admission, training/fine-tuning/model improvement, speaker identity, public
redistribution or re-hosting is implied.

Decision state: **PENDING_OWNER**

## 3. Pilot size

**RECOMMENDED VALUE**

`48 clips total = 24 RU + 24 EN`

Do not expand this pilot in 5.1A/5.1B.

Decision state: **PENDING_OWNER**

## 4. Custodian

**RECOMMENDED VALUE**

Assign the Alpha role:

`Project Owner / Data Custodian`

Responsibilities:

- accept the applicable MDC/Data Consumer terms only after the mandatory Legal/IP review has
  cleared the exact evaluation use;
- control dataset access;
- preserve exact provenance and the controlled manifest;
- enforce the approved retention window;
- initiate deletion at expiry/termination;
- record deletion result and unresolved failures.

This packet does not self-assign the role.

Decision state: **PENDING_OWNER**

## 5. Storage

**RECOMMENDED VALUE**

`LOCAL_PRIVATE_CONTROLLED_STORAGE`

Required properties:

- outside the Git repository and all Git worktrees;
- outside public/shared evidence folders;
- not committed to GitHub, Git LFS or CI artifacts;
- not uploaded to public/shared Google Drive;
- encrypted storage with device/account access controls;
- no public/shared link;
- accessible only to the approved custodian/test process;
- raw audio never included in PR/CI/public evidence;
- raw transcripts/source excerpts never included in public evidence;
- copy inventory, expiry and deletion can be verified.

No concrete machine path is approved by this document.

Decision state: **PENDING_OWNER**

## 6. Access

**RECOMMENDED VALUE**

`CUSTODIAN_ONLY`

No additional person, automation or service receives raw dataset access until explicitly
authorized. The later ASR test process must be authorized as a bounded custodian-controlled
process before it receives the selected pilot inputs.

Decision state: **PENDING_OWNER**

## 7. Retention

**RECOMMENDED VALUE**

- retain raw pilot copies only while the 5.1-5.5 Alpha ASR evaluation is active;
- after the final 5.5 assessment, delete raw pilot dataset copies within **30 calendar days**;
- delete earlier when no longer required;
- a stricter MDC/account/Data Consumer License requirement always wins;
- derived non-sensitive aggregate metrics and approved non-linkable provenance hashes may remain as
  DORA evidence;
- no indefinite retention.

Decision state: **PENDING_OWNER**

## 8. Pre-download deletion/access dry-run

**RECOMMENDED VALUE**

Require one synthetic/non-sensitive dry-run in the approved
`LOCAL_PRIVATE_CONTROLLED_STORAGE` **before downloading any real pilot dataset bytes**.

The dry-run must prove:

1. the controlled directory/storage object can be created;
2. access is restricted to the approved role/process;
3. a synthetic non-sensitive fixture can be written;
4. its SHA-256 can be recorded;
5. the fixture can be deleted;
6. absence can be verified after deletion;
7. no raw fixture remains in the Git repository, worktree, PR/CI artifact or public/shared evidence.

No Android/device test is involved.

Decision state: **PENDING_OWNER**

## 9. External restrictions accepted as constraints

These are not optional owner choices; they are constraints of the proposed use:

- no attempt to identify or re-identify speakers;
- no redistribution;
- no public re-hosting/re-sharing;
- reasonable safeguards against unauthorized access, use or disclosure;
- use only within the applicable dataset licence and current MDC terms;
- accept the applicable Data Consumer License before authorized access/download;
- on MDC account termination, cease use and delete/destroy copies subject to the applicable
  dataset licence;
- no bypass of access controls.

## 10. Separately gated authority

### Legal/IP

`LEGAL_IP_APPROVAL = BLOCKED`

Existing `GOV-IP-001` requires a named Legal/IP reviewer for exact terms, redistribution,
attribution and dataset/consent compatibility. The Product owner names that reviewer; this packet
does not invent one and does not treat owner approval as Legal/IP approval.

**REQUIRED SEPARATE ACTION**

Name a Legal/IP reviewer and obtain an exact-scope disposition for:

- RU MDC ID `cmu5mg3pr00simh07epeylc55`;
- EN MDC ID `cmu5nqn1h00vwmi07b4dbk085`;
- CC0-1.0;
- MDC Data Consumer Terms last updated 6 May 2026;
- any dataset access-step licence text;
- bounded internal Alpha ASR evaluation;
- no re-identification/rehosting/redistribution;
- proposed local controlled-copy and retention/deletion handling.

### Production admission

`NOT_APPLICABLE_TO_5.1A`

Production Legal/Security/dependency admission is a later gate and does not need to be completed
for this bounded Stage-0 internal evaluation.

## 11. Approval block

The owner may approve these owner-controlled values without redesigning the policy:

```text
DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1

DATASET_USE = APPROVE
PILOT_SIZE_48 = APPROVE
CUSTODIAN = Project Owner / Data Custodian
STORAGE_CLASS = LOCAL_PRIVATE_CONTROLLED_STORAGE
ACCESS = CUSTODIAN_ONLY
RETENTION = ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS
PRE_DOWNLOAD_SYNTHETIC_DELETION_DRY_RUN = REQUIRED

This approval does not grant LEGAL_IP_APPROVAL, production admission,
dataset download before the dry-run, model download, 5.1B, 5.2 or device execution.
```

Until explicit owner approval is recorded, every field above remains proposed.
