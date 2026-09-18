# DORA Alpha ASR 5.1A — bounded data admission

Task: `5.1A — Bounded Alpha ASR Data Admission`  
Backlog: `POC-DATA-001`  
Date: 18 September 2026  
Branch baseline: `chat/alpha-asr-data-scope@50206462a7f4097637fdbed8e7d3c030ceec7e97`  
Candidate profile: `dora-alpha-asr-data-admission-v0.1`  
Result: **BLOCKED_OWNER_DECISION**

## 1. Scope

This record determines whether the two already proposed Mozilla Common Voice datasets may be
used as the bounded RU/EN input for DORA Alpha ASR evaluation.

In scope:

- exact candidate identity and provenance;
- official Mozilla / Mozilla Data Collective licence and use terms;
- mapping to current DORA Dataset Governance, IP Asset Policy and privacy controls;
- the minimum decisions required before any dataset bytes are retrieved.

Out of scope:

- dataset download;
- sample selection or a pilot manifest;
- model/runtime selection;
- ASR runner implementation;
- Android/device execution;
- Recovery;
- Stage 1 or production admission.

No dataset bytes, raw audio, transcript excerpts, contributor metadata, credentials or private
storage locators are added by this task.

## 2. Governing DORA contract

The following existing repository rules are controlling for this admission:

1. `PUBLIC_LICENSED` data is an external dataset with an exact version and compatible terms and
   is blocked until IP/data review
   ([Dataset Governance](DORA_MVP1_DATASET_GOVERNANCE.md)).
2. Anything beyond synthetic data reaches corpus-backed `READY` only after the exact purpose,
   slices and counts are frozen; exact external rights are approved; a custodian, controlled
   storage and access roles are named; an immutable manifest exists; retention/deletion is defined;
   and the controlled-store deletion path has been exercised.
3. `OD-08` makes controlled non-public storage a hard boundary for retained raw audio. Until that
   storage is operational, Stage 0 remains synthetic-only for retained audio.
4. Stage 0 data use is evaluation-only by default. Training, fine-tuning, model improvement,
   speaker identification and public corpus publication remain prohibited without separate
   authority.
5. `GOV-IP-001` requires exact provenance and exact data rights. Only the named Product/Legal/IP
   and technical owners may move an external artifact from `PROPOSED` to
   `EVALUATION_APPROVED`; engineering does not self-approve legal/data-rights questions.
6. Public evidence may contain content-free aggregate metrics and approved provenance summaries.
   Raw audio, raw transcript/source excerpts, private locators and linkable personal-data digests
   remain outside public Git.

The existing `SyntheticManifestValidator` is not changed and is not evidence for
`PUBLIC_LICENSED` data.

## 3. Candidate RU dataset

| Field | Verified state |
|---|---|
| Proposed canonical name | `Common Voice Spontaneous Speech 3.0 - Russian` |
| Language / locale | Russian / `ru` |
| Version | `3.0` |
| Task | ASR |
| Format | MP3 |
| Catalogue size | 62.64 MB |
| Licence shown by official Common Voice catalogue | `CC0-1.0` |
| Official family release identifier | `sps-corpus-3.0-2026-03-09` is evidenced on official 3.0 datasheets for this release family; candidate-specific page still required |
| Exact current MDC dataset ID | **UNKNOWN** |
| Exact candidate MDC page | **UNKNOWN / not resolved from the current official registry** |
| Exact downloadable archive name/digest | **UNKNOWN / NOT_RETRIEVED** |
| Candidate state | `PROPOSED` |

The official Common Voice catalogue snapshot identifies the Russian 3.0 entry with the name,
locale, task, format, size and CC0 licence above. However, the current Mozilla Data Collective
registry has already advanced this language to a current 5.0 entry. The current official registry
contains Russian 5.0 dataset ID `cmu5mg3pr00simh07epeylc55`; that is a different release and is
**not substituted** for the proposed 3.0 candidate.

Because the immutable MDC dataset ID / exact archive identity for Russian 3.0 is not currently
resolved, DORA's exact-provenance requirement is not satisfied.

**RU admission: BLOCKED.**

## 4. Candidate EN dataset

| Field | Verified state |
|---|---|
| Proposed canonical name | `Common Voice Spontaneous Speech 3.0 - English` |
| Language / locale | English / `en` |
| Version | `3.0` |
| Task | ASR |
| Format | MP3 |
| Catalogue size | 459.05 MB |
| Licence shown by official Common Voice catalogue | `CC0-1.0` |
| Official family release identifier | `sps-corpus-3.0-2026-03-09` is evidenced on official 3.0 datasheets for this release family; candidate-specific page still required |
| Exact current MDC dataset ID | **UNKNOWN** |
| Exact candidate MDC page | **UNKNOWN / not resolved from the current official registry** |
| Exact downloadable archive name/digest | **UNKNOWN / NOT_RETRIEVED** |
| Candidate state | `PROPOSED` |

The official Common Voice catalogue snapshot identifies the English 3.0 entry with the name,
locale, task, format, size and CC0 licence above. The current Mozilla Data Collective Common Voice
catalogue now surfaces Spontaneous Speech 5.0 English as the current release. That newer release is
outside this admission scope and is not silently substituted for 3.0.

Because the immutable MDC dataset ID / exact archive identity for English 3.0 is not currently
resolved, DORA's exact-provenance requirement is not satisfied.

**EN admission: BLOCKED.**

## 5. Official source and terms findings

Official sources checked for this record:

- Common Voice dataset catalogue: <https://commonvoice.mozilla.org/en/datasets>
- Common Voice Legal Terms, effective 31 October 2025:
  <https://commonvoice.mozilla.org/terms>
- Mozilla Data Collective Data Consumer Terms of Use, last updated 6 May 2026:
  <https://mozilladatacollective.com/terms/consumers>
- Mozilla Data Collective datasets catalogue:
  <https://mozilladatacollective.com/datasets>
- Mozilla Data Collective public dataset registry maintained by
  `Mozilla-Data-Collective/dataset-schema-registry`.

Verified common terms relevant to both candidates:

- Common Voice states that Common Voice datasets are made available to the public through MDC
  under CC0 unless otherwise specified.
- The official 3.0 Common Voice catalogue classifies both candidates as `CC0-1.0`, `ASR`,
  `MP3`.
- CC0 permits evaluation/research use; DORA does not require a new participant-consent process to
  merely evaluate a provider-released `PUBLIC_LICENSED` dataset.
- Common Voice asks consumers not to post, distribute or mirror Common Voice datasets on other
  platforms/services. Current MDC Common Voice datasheets also prohibit speaker identification
  and re-hosting/re-sharing.
- MDC requires a Data Consumer account for platform use and requires the applicable Data Consumer
  License to be reviewed and accepted before dataset access/download.
- MDC requires reasonable safeguards against unauthorized access/use/disclosure and forbids
  re-identification.
- MDC access does not create broader rights: actual dataset rights come from the applicable Data
  Consumer License plus MDC supplemental terms.
- On MDC account termination, the consumer must cease use and, subject to the applicable dataset
  licence, delete or destroy copies in its possession.

For these exact 3.0 RU/EN candidates, the current per-dataset MDC pages and immutable file
identities were not resolved. Candidate-specific notices or additional terms beyond the verified
Common Voice/MDC terms therefore remain **UNKNOWN**, not assumed absent.

## 6. Allowed and limited use

### Allowed by external terms, subject to exact-candidate resolution

- bounded ASR evaluation/research;
- a private working copy needed for the authorized evaluation, protected by reasonable security
  safeguards;
- internal calculation of quality/performance results;
- content-free aggregate reporting consistent with DORA governance.

### Forbidden or limited

- no attempt to identify or re-identify speakers;
- no public re-hosting, re-sharing or mirroring of dataset bytes;
- no dataset bytes in Git, Git LFS, GitHub Actions artifacts or public evidence;
- no use outside the applicable Data Consumer License / dataset terms;
- no DORA training, fine-tuning or model-improvement claim under this Stage 0 scope;
- no public raw transcript/source excerpts;
- no assumption that CC0 alone satisfies DORA's internal data/IP admission.

Mandatory attribution is not imposed by CC0 itself. Provenance must nevertheless be recorded by
DORA. Any candidate-specific notice/acknowledgement that may exist on the unresolved exact 3.0
datasheet remains `UNKNOWN` until that page is pinned.

## 7. DORA governance mapping

| Requirement | RU | EN | DORA governance | Status |
|---|---|---|---|---|
| Identifiable dataset family/name | Common Voice Spontaneous Speech 3.0 - Russian | Common Voice Spontaneous Speech 3.0 - English | Exact source name required | PASS |
| Exact language | `ru` | `en` | Requested Alpha language slices | PASS |
| Exact release/version label | `3.0` | `3.0` | Exact version required | PASS |
| Exact MDC dataset ID | Not resolved | Not resolved | Immutable provenance required | UNKNOWN |
| Exact archive/file identity | Not retrieved/resolved | Not retrieved/resolved | Exact artifact identity/digest required before materialization | UNKNOWN |
| Official source | Common Voice / MDC | Common Voice / MDC | Canonical provider required | PASS |
| Licence family | `CC0-1.0` | `CC0-1.0` | Exact data rights required | PASS |
| Dataset-specific terms pinned | Current exact 3.0 page unresolved | Current exact 3.0 page unresolved | Exact terms must be reviewed | BLOCKED |
| ASR evaluation permitted externally | CC0 + ASR catalogue classification | CC0 + ASR catalogue classification | Evaluation-only Stage 0 purpose | PASS |
| New DORA participant consent | Public-provider dataset; no new collection | Public-provider dataset; no new collection | Separate consent required for purpose-recorded data, not for provider-released PUBLIC_LICENSED data | NOT_APPLICABLE |
| Speaker identification/re-identification | Forbidden | Forbidden | DORA also forbids persistent speaker identity in this scope | PASS |
| Private controlled working copy allowed by external terms | Permitted for licensed use; must not become a mirror/re-share | Same | DORA additionally requires controlled storage | PASS |
| Public mirroring / repository copy | Forbidden by Common Voice/MDC conditions | Same | Raw dataset never public Git | FAIL |
| Mandatory licence attribution | CC0 does not require attribution; exact datasheet notice unresolved | Same | DORA still records provenance | UNKNOWN |
| Product/Legal/IP exact evaluation approval | Not recorded | Not recorded | Required for `EVALUATION_APPROVED` | BLOCKED |
| Data custodian assigned | No | No | Named custodian required | BLOCKED |
| Controlled storage destination approved | No | No | `OD-08` hard boundary | BLOCKED |
| Operational access roles/grants | Policy roles exist; no operational assignment | Same | Least privilege/time-bounded access required | BLOCKED |
| Concrete retention / `expiresAt` | Not frozen | Not frozen | Must be defined before data is stored; indefinite retention forbidden | BLOCKED |
| Deletion procedure | Policy defined; controlled-store path not operational/dry-run | Same | Controlled-store deletion dry-run required | BLOCKED |
| Provider termination deletion condition | Delete/destroy copies subject to dataset licence | Same | Local plan must honor shorter/applicable condition | PASS |
| Derived aggregate metrics in evidence | Allowed, content-free | Allowed, content-free | Dataset Governance public reporting rules | PASS |
| Hashes in evidence | Controlled per-file hashes allowed; public digest only when non-linkable/approved | Same | Avoid linkable public raw-content digests | PASS |
| Raw audio handling | Must be private, access-controlled, no re-share | Same | Controlled encrypted storage required; currently absent | BLOCKED |

A `FAIL` on public mirroring means that operation is prohibited; it is not a defect in the
dataset for private evaluation. It reinforces `publicRedistributionAllowed=false`.

## 8. Custodian status

**BLOCKED.**

The repository and current Alpha roadmap state that a data custodian has not been operationally
assigned for the real corpus-backed path. This task does not name one.

Required owner action: name the Data/Research Custodian responsible for access approval, split
custody, retention, deletion and evidence records for this bounded pilot.

## 9. Storage status

**BLOCKED.**

No approved controlled non-public storage destination has been identified in the evidence
available to this task. `OD-08` therefore continues to prohibit retaining raw audio.

Required owner action: identify the controlled non-public storage class/location and confirm:

- encryption at rest and in transport;
- approved access roles;
- no public/shared link;
- no Git/Git LFS/Actions or personal messaging path;
- copy inventory and access logging;
- deletion/expiry capability.

The public admission record must retain only an opaque storage/evidence reference after approval,
not the private path or credential.

## 10. Retention and deletion status

### Retention

**BLOCKED for the concrete pilot.**

DORA has an approved retention framework and forbids indefinite retention. `OD-09` defines
maximums for purpose-recorded audio/derivatives, but this task does not reinterpret those numbers
as a candidate-specific retention period for `PUBLIC_LICENSED` copies.

The actual pilot must freeze `createdAt`, `expiresAt` and cleanup timing before download. Any
shorter applicable external/license condition wins.

### Deletion

**BLOCKED operationally.**

The repository already defines the deletion graph and content-free deletion evidence, but the
controlled-store deletion dry-run remains `NOT_RUN` and no actual controlled store exists for
this pilot. MDC additionally requires deletion/destruction of held dataset copies on account
termination subject to the applicable dataset licence.

The operational data path must be established and its synthetic deletion dry-run completed before
raw audio is retrieved.

## 11. Admission verdicts

### Russian

**BLOCKED — remains `PROPOSED`; not `EVALUATION_APPROVED`.**

External family licence/use is compatible with bounded ASR evaluation, but exact 3.0 MDC dataset
ID/archive identity is unresolved and DORA's required reviewer/custodian/storage/retention/deletion
gates are open.

### English

**BLOCKED — remains `PROPOSED`; not `EVALUATION_APPROVED`.**

External family licence/use is compatible with bounded ASR evaluation, but exact 3.0 MDC dataset
ID/archive identity is unresolved and DORA's required reviewer/custodian/storage/retention/deletion
gates are open.

## 12. Overall 5.1A verdict

**`5.1A = BLOCKED_OWNER_DECISION`**

No dataset download is authorized.

This is not a conclusion that Common Voice is unsuitable. It means that the current proposed 3.0
RU/EN candidates have not yet satisfied DORA's exact-provenance and operational data-custody gates.

## 13. Exact blockers

1. **Exact 3.0 identities:** resolve immutable MDC dataset ID, exact per-dataset page and exact
   archive identity for RU 3.0 and EN 3.0 from an official Mozilla/MDC source.
2. **Version disposition:** if historical 3.0 identities cannot be pinned, the Product owner must
   decide whether the admission candidate changes to the currently published 5.0 releases. Such a
   change is a new exact-candidate admission; 5.0 must not be silently substituted here.
3. **Legal/IP reviewer:** the Product owner must name/route the exact-candidate dataset review to
   the responsible Legal/IP role and record `EVALUATION_APPROVED` or rejection after the exact
   pages/terms are available.
4. **Data custodian:** the Product owner must name the custodian for this pilot.
5. **Controlled storage/access:** the owner/custodian must identify the approved private storage
   destination and operational access roles.
6. **Retention:** the owner/custodian must freeze the pilot-specific expiry/cleanup period before
   retrieval.
7. **Deletion:** the approved controlled store must pass the required synthetic access/deletion
   dry-run before retrieval.

No additional Recovery, Android, model or ASR runtime test is needed to resolve these blockers.

## 14. Next permitted action

While admission is blocked, the following preparation remains safe **without downloading dataset
bytes**:

- resolve the historical 3.0 MDC IDs/pages/archives from official Mozilla/MDC records;
- prepare the exact Product/Legal/IP decision packet with the official terms already identified;
- define the controlled storage/access/deletion checklist for the owner/custodian to fill;
- prepare content-free fields for the future private manifest, without selecting clips or creating
  the 5.1B validator.

Do not begin `5.1B — deterministic ASR pilot manifest contract + validator`, 5.2, dataset download,
model download or device execution until this admission is unblocked.
