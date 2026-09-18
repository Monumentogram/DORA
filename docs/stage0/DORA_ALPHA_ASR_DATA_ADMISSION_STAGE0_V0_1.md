# DORA Alpha ASR 5.1A — bounded data admission

Task: `5.1A-R2 — Exact Common Voice 5.0 Candidate Freeze + Owner Decision Packet`  
Backlog: `POC-DATA-001`  
Date: 18 September 2026  
Predecessor: `9bd272d000eb6cf9b22f911967decd7f613cfa1a`  
Candidate profile: `dora-alpha-asr-data-admission-v0.2`  
Result: **BLOCKED_LEGAL_IP**

## 1. Scope

This revision freezes the current Mozilla Data Collective Common Voice Spontaneous Speech 5.0
Russian and English datasets as the proposed inputs for the bounded DORA Alpha ASR evaluation.

It preserves the chronology of the previous 3.0 proposal and does not authorize:

- dataset download;
- sample materialization or the 5.1B manifest/validator;
- model selection or download;
- ASR runner implementation;
- Android/device execution;
- Recovery work;
- production admission or Stage 1.

No dataset bytes, audio, transcript excerpts, contributor metadata, credentials or private storage
locators are added by this task.

## 2. Candidate chronology

The first 5.1/5.1A documents proposed:

- `Common Voice Spontaneous Speech 3.0 - Russian`;
- `Common Voice Spontaneous Speech 3.0 - English`.

Those 3.0 entries were never `EVALUATION_APPROVED`, no 3.0 dataset bytes were downloaded, and no
3.0 ASR/device execution occurred.

Effective with this R2 candidate freeze, both 3.0 candidates become:

`SUPERSEDED_AS_CANDIDATE`

They remain historical evidence of the earlier proposal. This document does not rewrite history to
claim that 5.0 was always selected.

The new proposed candidates are the current official 5.0 RU/EN datasets below.

## 3. Governing DORA contract

Current repository governance requires:

1. `PUBLIC_LICENSED` data must have an exact version and compatible terms and remains blocked
   until IP/data review
   ([Dataset Governance](DORA_MVP1_DATASET_GOVERNANCE.md)).
2. A corpus-backed PoC must freeze its hypothesis/slices/counts, name a custodian, controlled
   storage and access roles, approve exact external dataset rights for evaluation, define
   retention/deletion, and complete the required synthetic deletion/access dry-run before use.
3. `OD-08` makes controlled non-public storage a hard boundary for retained raw audio.
4. Stage 0 data use is evaluation-only by default; DORA training/fine-tuning/model-improvement and
   persistent speaker identity remain out of scope.
5. `GOV-IP-001` states that only named Product/Legal/IP and Engineering/Security reviewers may
   move an artifact to `EVALUATION_APPROVED` or `ADMITTED`.
6. `GOV-IP-001` assigns the Product owner to product scope/risk/fallback and to naming the
   Legal/IP reviewer; the Legal/IP reviewer interprets exact terms, redistribution, attribution
   and dataset/consent compatibility.
7. Public evidence may contain content-free aggregate metrics and approved provenance summaries.
   Raw audio/transcript/source excerpts, private locators and linkable personal-data digests remain
   outside public Git.

The existing `SyntheticManifestValidator` remains unchanged and is not evidence for
`PUBLIC_LICENSED` data.

## 4. Exact RU candidate

| Field | Frozen candidate state |
|---|---|
| Canonical name | `Common Voice Spontaneous Speech 5.0 - Russian` |
| Locale | `ru` |
| Version | `5.0` |
| Release family | `sps-corpus-5.0-2026-09-11` |
| Release date | `2026-09-17` |
| MDC dataset ID | `cmu5mg3pr00simh07epeylc55` |
| Canonical URL | `https://mozilladatacollective.com/datasets/cmu5mg3pr00simh07epeylc55` |
| MDC slug | `common-voice-spontaneous-speech-5-0-russ-883d3f86` |
| Task | `ASR` |
| Catalogue format | `MP3` |
| Archive/file name | `PENDING_DOWNLOAD_VERIFICATION` — do not infer the archive name from the slug |
| Published size | `88.72 MB` |
| License | `CC0-1.0` |
| Archive SHA-256 | `PENDING_DOWNLOAD_VERIFICATION` — post-authorized-download provenance gate |
| Candidate state | `PROPOSED` |

The immutable RU ID/URL/slug are present in Mozilla Data Collective's official public
`dataset-schema-registry`; the official Common Voice/MDC catalogue lists the same dataset as
`ru`, ASR, MP3, CC0-1.0, 88.72 MB.

The canonical RU page URL is resolved by the official registry, but its current page body/file row
was not available to this task's unauthenticated web fetch. The exact archive filename is therefore
not guessed.

## 5. Exact EN candidate

| Field | Frozen candidate state |
|---|---|
| Canonical name | `Common Voice Spontaneous Speech 5.0 - English` |
| Locale | `en` |
| Version | `5.0` |
| Release family | `sps-corpus-5.0-2026-09-11` |
| Release date | `2026-09-17` |
| MDC dataset ID | `cmu5nqn1h00vwmi07b4dbk085` |
| Canonical URL | `https://mozilladatacollective.com/datasets/cmu5nqn1h00vwmi07b4dbk085` |
| MDC slug | `common-voice-spontaneous-speech-5-0-engl-97a82389` |
| Task | `ASR` |
| Catalogue format | `MP3` |
| Archive/file name | `common-voice-spontaneous-speech-5-0-engl-97a82389.tar.gz` |
| Published size | `519.05 MB` |
| License | `CC0-1.0` |
| Archive SHA-256 | `PENDING_DOWNLOAD_VERIFICATION` — post-authorized-download provenance gate |
| Candidate state | `PROPOSED` |

The canonical MDC page publicly exposes the EN dataset ID, release date, format, size, archive
filename, CC0-1.0 licence, intended use and restrictions.

## 6. Official sources and current external terms

Authoritative sources used for this freeze:

- Common Voice / MDC current catalogue:
  `https://commonvoice.mozilla.org/en/datasets`
- MDC Common Voice organization catalogue:
  `https://mozilladatacollective.com/organization/cmfh0j9o10006ns07jq45h7xk`
- EN canonical page:
  `https://mozilladatacollective.com/datasets/cmu5nqn1h00vwmi07b4dbk085`
- RU canonical URL from the official MDC registry:
  `https://mozilladatacollective.com/datasets/cmu5mg3pr00simh07epeylc55`
- Mozilla Data Collective Data Consumer Terms of Use, last updated 6 May 2026:
  `https://mozilladatacollective.com/terms/consumers`
- official registry snapshot:
  `Mozilla-Data-Collective/dataset-schema-registry@6be2d48cfae2f7d7c15df05e1d6fdfc97d965339`

Applicable external facts:

- the 5.0 RU and EN entries are published under `CC0-1.0`;
- Common Voice 5.0 datasheets identify the intended use as training and evaluating ASR models,
  with additional CALL/language-revitalisation uses;
- DORA uses only the evaluation portion in this scope;
- Common Voice forbids attempting to determine speaker identity and forbids re-hosting/re-sharing
  the dataset;
- MDC requires a Data Consumer Account and review/acceptance of the applicable Data Consumer
  License before access/download;
- the MDC Data Consumer Terms and Appendix 1 apply in addition to the provider licence;
- MDC prohibits use outside the applicable Data Consumer License, re-identification, scraping/
  mirroring/redistribution and bypassing access controls;
- MDC requires reasonable safeguards against unauthorized access, use or disclosure;
- upon MDC account termination, the consumer must cease use and, subject to the applicable dataset
  licence, delete or destroy copies in its possession.

No archive SHA-256 is invented before download.

## 7. Allowed and limited use for this proposal

### Proposed allowed DORA use

Subject to the still-open internal approvals:

- bounded internal Alpha ASR evaluation only;
- exactly 48 pilot clips total: 24 RU + 24 EN;
- local/private processing only in the approved controlled storage/test process;
- content-free aggregate quality/performance evidence;
- provenance and non-linkable hashes required by DORA evidence controls.

### Forbidden or limited

- no speaker re-identification or persistent voice identity;
- no public redistribution, re-hosting or re-sharing;
- no dataset bytes in Git, Git LFS, GitHub Actions or public/shared evidence folders;
- no raw transcripts/source excerpts in public evidence;
- no training/fine-tuning/model-improvement claim under this Stage 0 scope;
- no use beyond the applicable dataset licence and MDC terms;
- no assumption that CC0 alone bypasses DORA's internal IP/data review.

CC0 itself does not require attribution, but DORA still requires provenance. Any additional
click-through text visible only at the authorized access/download step must be retained in
controlled evidence before bytes are used.

## 8. Governance mapping

| Requirement | RU | EN | DORA governance | Status |
|---|---|---|---|---|
| Exact dataset name | 5.0 Russian | 5.0 English | Exact source required | PASS |
| Locale | `ru` | `en` | Alpha RU/EN slices | PASS |
| Exact version | `5.0` | `5.0` | Exact version required | PASS |
| Release family | `sps-corpus-5.0-2026-09-11` | same | Exact release identity | PASS |
| Release date | 2026-09-17 | 2026-09-17 | Provenance record | PASS |
| MDC dataset ID | `cmu5mg3pr00simh07epeylc55` | `cmu5nqn1h00vwmi07b4dbk085` | Immutable provider ID | PASS |
| Canonical MDC URL | resolved | resolved | Canonical provider required | PASS |
| Task/format | ASR / MP3 | ASR / MP3 | Intended evaluation input | PASS |
| Published size | 88.72 MB | 519.05 MB | Public metadata | PASS |
| Exact archive filename | not exposed in fetched RU file row | `...engl-97a82389.tar.gz` | Verify actual archive before materialization | PENDING_DOWNLOAD_VERIFICATION |
| Archive SHA-256 | not downloaded | not downloaded | Verify immediately after authorized download | PENDING_DOWNLOAD_VERIFICATION |
| License | CC0-1.0 | CC0-1.0 | Exact data rights | PASS |
| ASR evaluation allowed externally | yes | yes | Evaluation-only Stage 0 | PASS |
| New DORA participant consent | provider-released public dataset | same | No new DORA collection | NOT_APPLICABLE |
| Speaker identification | forbidden | forbidden | DORA scope also forbids identity | PASS |
| Public redistribution/rehosting | forbidden | forbidden | Raw dataset never public Git/evidence | FAIL |
| Reasonable protection | required | required | Controlled storage/access | PASS |
| Applicable current MDC terms pinned | Data Consumer Terms 2026-05-06 | same | Exact terms review required | PASS |
| Product owner evaluation scope | not yet approved as owner record | same | Owner-controlled | BLOCKED_OWNER_DECISION |
| Legal/IP exact-term approval | no named/recorded approval | same | `GOV-IP-001` requires Legal/IP reviewer | BLOCKED_LEGAL_IP |
| Custodian | not yet assigned | same | Named custodian required | BLOCKED_OWNER_DECISION |
| Storage class | proposed only | same | Controlled non-public storage required | BLOCKED_OWNER_DECISION |
| Access | proposed only | same | Least privilege/time-bounded | BLOCKED_OWNER_DECISION |
| Retention | proposed only | same | No indefinite retention | BLOCKED_OWNER_DECISION |
| Synthetic deletion/access dry-run | not yet run | same | Required before real corpus use | BLOCKED_OWNER_DECISION |
| Derived content-free metrics | allowed | allowed | Dataset Governance public reporting | PASS |
| Raw audio handling | private controlled only | same | `OD-08` hard boundary | BLOCKED_OWNER_DECISION |

A `FAIL` on public redistribution means that operation is prohibited. It does not make the
dataset unusable for a compliant private evaluation.

## 9. Owner-controlled proposal

The companion
[DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1.md](DORA_ALPHA_ASR_DATA_OWNER_DECISION_V0_1.md)
contains the exact pending owner choices.

Proposed values are:

- datasets: the exact 5.0 RU/EN candidates in sections 4-5;
- purpose: bounded internal Alpha ASR evaluation only;
- pilot: 48 clips total, 24 RU and 24 EN;
- custodian role: `Project Owner / Data Custodian`;
- storage class: `LOCAL_PRIVATE_CONTROLLED_STORAGE`;
- access: `CUSTODIAN_ONLY`;
- retention: only while 5.1-5.5 is active, then delete raw pilot copies within 30 calendar days
  after final 5.5 assessment; stricter external requirement wins;
- deletion: one synthetic/non-sensitive controlled-storage access/deletion dry-run before any real
  dataset download.

These values are proposals, not approvals.

## 10. Legal/IP boundary

**LEGAL_IP_APPROVAL = BLOCKED.**

This is an internal DORA governance gate, not a conclusion that CC0 is incompatible.

The controlling `GOV-IP-001` rules are:

- only named Product/Legal/IP and Engineering/Security reviewers may move an artifact to
  `EVALUATION_APPROVED` or `ADMITTED`;
- the Product owner approves product scope/risk/fallback and **names Legal/IP reviewer**;
- the Legal/IP reviewer interprets exact terms, redistribution, attribution and dataset/consent
  compatibility;
- if no named reviewer is available, evaluation remains `PROPOSED` and engineering does not
  substitute its own legal approval.

The repository contains special packages where the Project owner is explicitly assigned a
Stage-0 Product/IP role (for example a Recovery-specific boundary). No equivalent existing
assignment was found for these Common Voice dataset candidates. That special-case authority is not
generalized here.

Production Legal/Security admission is a separate later gate and is **not** being imposed on this
bounded Alpha evaluation. The current blocker is the pre-existing Stage-0 exact-dataset Legal/IP
review required by `GOV-IP-001`.

## 11. Candidate admission verdicts

### Russian

- factual identity: **PASS**, except archive filename/digest are
  `PENDING_DOWNLOAD_VERIFICATION`;
- owner operational controls: **BLOCKED_OWNER_DECISION**;
- Legal/IP approval: **BLOCKED_LEGAL_IP**;
- state: `PROPOSED`, not `EVALUATION_APPROVED`.

### English

- factual identity: **PASS**, archive filename public; archive digest is
  `PENDING_DOWNLOAD_VERIFICATION`;
- owner operational controls: **BLOCKED_OWNER_DECISION**;
- Legal/IP approval: **BLOCKED_LEGAL_IP**;
- state: `PROPOSED`, not `EVALUATION_APPROVED`.

## 12. Overall 5.1A-R2 verdict

**`5.1A = BLOCKED_LEGAL_IP`**

Owner-controlled decisions are also pending, but the mandatory separate Legal/IP role means owner
approval alone cannot close 5.1A under the current governance text.

No dataset download is authorized.

## 13. Smallest compliant resolution path

Without weakening governance:

1. Project owner approves or rejects the proposed owner-controlled values in the companion packet.
2. Project owner names a Legal/IP reviewer for this exact 5.0 RU/EN dataset evaluation.
3. The named Legal/IP reviewer reviews the two exact MDC identities, CC0-1.0, the current MDC Data
   Consumer Terms and any access-step licence text, then records either
   `EVALUATION_APPROVED` for this bounded internal Alpha scope or `REJECTED`.
4. Before dataset download, the approved `LOCAL_PRIVATE_CONTROLLED_STORAGE` performs the required
   synthetic access/deletion dry-run.
5. Only then may an authorized download occur. Immediately after download, verify the actual RU/EN
   archive identities and SHA-256 values before any sample materialization or ASR run.

## 14. Next safe action

Until the steps above are satisfied, safe work is limited to decision/reviewer routing and the
synthetic controlled-storage dry-run after the owner approves that storage class.

Do not start `5.1B — deterministic ASR pilot manifest contract + validator`, 5.2, dataset
download, model download or device execution in this task.
