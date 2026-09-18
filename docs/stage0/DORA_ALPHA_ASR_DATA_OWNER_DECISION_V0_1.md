# DORA Alpha ASR Data Owner Decision v0.1

Task: `5.1A-L — Legal/IP Review Preparation`  
Date: 18 September 2026  
State: **APPROVED_BY_PROJECT_OWNER**  
Admission record: [DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md](DORA_ALPHA_ASR_DATA_ADMISSION_STAGE0_V0_1.md)  
Legal/IP packet: [DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md](DORA_ALPHA_ASR_DATA_LEGAL_IP_REVIEW_V0_1.md)

## 1. Decision scope

The Project Owner explicitly approves the owner-controlled values below for:

`BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY`

This approval is limited to the Stage-0 Alpha data path. It does **not** constitute Legal/IP
approval, `EVALUATION_APPROVED`, production admission, dataset download authorization before
remaining pre-download gates, redistribution, public hosting, re-identification or unlimited
retention.

`OWNER_APPROVED != EVALUATION_APPROVED`

## 2. Exact datasets and purpose

**APPROVED**

Use as the exact proposed Alpha evaluation inputs:

- `Common Voice Spontaneous Speech 5.0 - Russian`
  - locale: `ru`
  - MDC ID: `cmu5mg3pr00simh07epeylc55`
- `Common Voice Spontaneous Speech 5.0 - English`
  - locale: `en`
  - MDC ID: `cmu5nqn1h00vwmi07b4dbk085`

Purpose:

`BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY`

No production admission, training/fine-tuning/model improvement, speaker identity, public
redistribution or re-hosting is implied.

## 3. Pilot size

**APPROVED**

`48 clips total = 24 RU + 24 EN`

Do not expand this pilot in 5.1A/5.1B.

## 4. Custodian

**APPROVED**

Assign the Alpha role:

`Project Owner / Data Custodian`

Responsibilities:

- accept applicable MDC/Data Consumer terms only after the mandatory Legal/IP review has cleared
  the exact evaluation use;
- control dataset access;
- preserve exact provenance and the controlled manifest;
- enforce the approved retention window;
- initiate deletion at expiry/termination;
- record deletion result and unresolved failures.

This is a role assignment for this bounded Alpha scope only.

## 5. Storage class

**APPROVED**

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

No concrete machine path is approved by this document. Instantiation of a compliant concrete
location remains an operational pre-download control.

## 6. Access

**APPROVED**

`CUSTODIAN_ONLY`

No additional person, automation or service receives raw dataset access until explicitly
authorized. A later ASR test process must be separately authorized as a bounded,
custodian-controlled process before receiving selected pilot inputs.

## 7. Retention

**APPROVED**

`ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS`

Policy:

- retain raw pilot copies only while the 5.1-5.5 Alpha ASR evaluation is active;
- after final 5.5 assessment, delete raw pilot dataset copies within 30 calendar days;
- delete earlier when no longer required;
- a stricter MDC/account/Data Consumer License requirement wins;
- derived non-sensitive aggregate metrics and approved non-linkable provenance hashes may remain as
  DORA evidence;
- no indefinite retention.

## 8. Pre-download synthetic deletion/access dry-run

**APPROVED AS REQUIRED CONTROL**

`PRE_DOWNLOAD_SYNTHETIC_DELETION_DRY_RUN = REQUIRED`

Before downloading any real pilot dataset bytes, the approved
`LOCAL_PRIVATE_CONTROLLED_STORAGE` must demonstrate:

1. a controlled directory/storage object can be created;
2. access is restricted to the approved role/process;
3. a synthetic non-sensitive fixture can be written;
4. its SHA-256 can be recorded;
5. the fixture can be deleted;
6. absence can be verified;
7. no raw fixture remains in the Git repository, worktree, PR/CI artifact or public/shared evidence.

This owner decision requires the dry-run; it does not claim that the dry-run has already occurred.

No Android/device test is involved.

## 9. External restrictions accepted as constraints

The owner accepts these as mandatory constraints of the proposed use:

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

The owner approval above does not replace `GOV-IP-001`.

A named Legal/IP reviewer must still interpret the exact Common Voice/MDC terms,
redistribution/attribution and dataset compatibility for this bounded evaluation.

### Engineering/Security

The general `GOV-IP-001` policy also names Engineering/Security reviewer functions for artifact
state transitions and provenance. This owner decision does not silently assign those roles.

For this dataset task, general governance does not state that Legal/IP and Engineering/Security
must be different people. A package-specific independence rule exists for Recovery, but no such
distinct-reviewer rule was found for this Common Voice dataset scope.

Any person serving multiple reviewer roles must be explicitly named for each role; prior Recovery
assignments do not carry over automatically.

### Production admission

`NOT_APPLICABLE_TO_5.1A`

Production Legal/Security/dependency admission is a later gate and is not imposed on this bounded
Stage-0 internal evaluation.

## 11. Owner decision record

The Project Owner explicitly approved:

```text
DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1

DATASET_USE = APPROVE
PILOT_SIZE_48 = APPROVE
CUSTODIAN = Project Owner / Data Custodian
STORAGE_CLASS = LOCAL_PRIVATE_CONTROLLED_STORAGE
ACCESS = CUSTODIAN_ONLY
RETENTION = ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS
PRE_DOWNLOAD_SYNTHETIC_DELETION_DRY_RUN = REQUIRED
```

Approval boundary:

- only `BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY`;
- no Legal/IP approval;
- no production use;
- no redistribution/public hosting;
- no re-identification;
- no raw data in Git/CI/public evidence;
- no unlimited retention;
- no download until the remaining pre-download gates close.
