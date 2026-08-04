# Dora MVP 1 — Dataset Governance

Task: `POC-DATA-001` foundation
Version: 1.1
Date: 5 August 2026
Owner decisions effective: 4 August 2026 (`OD-03`, `OD-04`, `OD-08`, `OD-09`)
Status: owner Stage 0 policy approved; consent process, custodian and controlled storage pending; **no audio dataset is created by this change**

## 1. Scope

This document governs audio, transcript, annotation and derived evaluation data used by Dora Stage 0 PoC work. It does not authorize collection of real meetings, model training, public dataset release or cloud upload.

The default order is:

1. deterministic synthetic signal and generated text;
2. scripted, purpose-recorded test data created specifically for the named PoC under explicit consent;
3. externally licensed public data only after license/provenance review;
4. real meetings only under a future, separately approved research/legal process — prohibited at the current stage.

## 2. Data classes

| Class | Description | Current permission | GitHub rule |
|---|---|---|---|
| `SYNTHETIC_SIGNAL` | generated silence, tones, bounded noise, deterministic PCM frames | allowed in a scoped task after generator/seed manifest | small non-personal fixtures or generator source may be committed; large audio stays outside Git |
| `GENERATED_TEXT` | scripted RU/EN/mixed transcripts and labels with no real person/organization | allowed | may be committed after secret/PII/copyright review |
| `PURPOSE_RECORDED` | adults read or enact approved scripts specifically for Dora evaluation | owner-authorized in principle by `OD-03`, but blocked until consent, custodian, controlled storage, access and deletion controls are operational | raw audio/transcript never Git/LFS/Actions |
| `PUBLIC_LICENSED` | external dataset with exact version and compatible terms | blocked until IP/data review | manifest metadata only unless redistribution is explicitly approved |
| `REAL_MEETING` | authentic workplace/private meeting or call | prohibited in Stage 0 preparation and public repository | never Git/LFS/Actions; future collection needs separate owner/Legal/research approval |
| `DERIVED_SENSITIVE` | transcript, speaker intervals/embeddings, errors, annotations from a person | follows the source’s restrictions | never public merely because audio was removed |

Model weights and reference media are not datasets under this policy; they follow the IP Asset Policy in addition to any data terms.

## 3. Permitted purposes

Every sample has exactly one or more predeclared purposes:

- capture/sample-integrity and recovery testing;
- VAD/silence/max-cap evaluation;
- ASR RU/EN/mixed evaluation;
- diarization and correction-load evaluation;
- decision/task/source-grounding evaluation;
- offline/VPN transport testing with a non-sensitive fixture;
- battery/thermal calibration;
- search scale testing with generated data.

The following are not implied by a PoC purpose:

- model training, fine-tuning, distillation or embedding enrollment;
- human review beyond named annotators;
- provider quality improvement;
- public release, redistribution or publication of clips/transcripts;
- marketing/demo use;
- biometric identity or cross-conversation speaker recognition;
- reuse by another project or company.

Any new purpose requires a compatibility review, updated consent/terms when needed and a new immutable manifest version.

## 4. Consent requirements

Purpose-recorded test data may be collected only when all participants are adults and the consent record states in plain language:

1. who operates the study and whom to contact;
2. that audio is recorded intentionally for a named Dora PoC;
3. the script/scenario and whether other voices/noise are present;
4. which derivatives will be created: transcript, speaker labels, annotations and aggregate metrics;
5. who may access raw and derived data;
6. whether human annotation/listening is required;
7. storage location, encryption and maximum retention;
8. whether any provider/region receives an artifact — default is none;
9. how to withdraw and what cannot be retracted after an already published aggregate result;
10. that training/model improvement is **not** permitted without separate future consent;
11. that participation is voluntary and refusal has no product/account penalty;
12. how deletion is requested and evidenced.

Consent to use Dora or to evaluate a recording flow is not consent to train a model. Consent from one participant does not cover another voice in the room. A researcher must stop or invalidate a take if an unconsented person becomes audible.

The public manifest contains only an opaque `consentReference`; signed forms and participant mapping stay in controlled private evidence.

## 5. Collection protocol

Before collection, create a versioned collection plan with:

- PoC ID, hypothesis and non-goals;
- dataset class and approved script version;
- target language/acoustic/speaker matrix;
- named data custodian and approved annotator roles;
- capture device/profile without serial or personal account identifier;
- local/cloud boundary and proof cloud is off unless separately consented;
- output formats and encryption/storage location;
- retention/withdrawal dates and deletion checklist;
- incident contact and stop conditions;
- manifest ID and immutable hash rules.

During collection:

- use neutral fictional content with no real customer, employer, person, address, credential or confidential project;
- display explicit recording state and obtain the required per-take acknowledgement;
- record only the planned duration and acoustic condition;
- identify a take by random sample ID, not a participant name;
- keep participant mapping separate from audio/transcript;
- do not record calls/system audio or background people;
- stop and quarantine the take if consent, route or scope becomes uncertain;
- do not upload through personal consumer storage or messaging apps.

After collection:

- verify encryption, digest, manifest match and data classification;
- remove failed/unneeded takes promptly instead of retaining “just in case”;
- log access and annotation assignment by role;
- create split assignment before model/prompt tuning sees protected evaluation data;
- produce only sanitized aggregate evidence for GitHub.

## 6. Language and acoustic matrix

The corpus plan must cover the following slices without inventing availability:

### Language

- Russian (`ru`);
- English (`en`);
- mixed Russian/English within the same recording (`mixed-ru-en`);
- names/acronyms/numbers represented by fictional tokens, not real personal/company data;
- spontaneous-style scripted speech and read speech reported separately.

### Acoustic conditions

- quiet room;
- controlled office/HVAC/keyboard/traffic-like noise;
- near field and far field;
- built-in microphone and approved headset/route slices;
- remote speakerphone reproduction using licensed/synthetic content;
- 1, 2, 3, 4 and where justified 5–6 speakers;
- short turns, long turns, fast alternation and overlap;
- silence at the beginning, 89.5/90.0/90.5-second silence, resume at 89.9 seconds and continuous speech over 10 minutes;
- TV/podcast/music-like **synthetic or licensed negative** fixtures, never copied private media.

Each report names missing slices. An average cannot substitute for RU, EN, mixed, noise, distance and speaker-count results.

## 7. Train, development, test and evaluation separation

The word `train` in a split name does not grant permission to train a model.

| Split | Purpose | Who may see labels | Change rule |
|---|---|---|---|
| `development` (or legacy `train`) | tune deterministic thresholds, prompts, preprocessing and harness behavior within the approved purpose | implementation/research roles | may evolve only through a new manifest version; model training still forbidden without separate consent |
| `test` | repeated regression during candidate development | QA/evaluation role; implementation sees aggregate failures where possible | fixed for a campaign; no item migration after results are inspected |
| `evaluation` | protected blind go/no-go scoring | independent evaluator/custodian until candidate is frozen | immutable; one authorized scoring pass per candidate version unless protocol predeclares repeats |

Mandatory anti-leakage rules:

- split by complete recording/meeting-like scenario, never random utterance;
- the same participant/voice actor does not cross development, test and evaluation when speaker leakage could affect the metric;
- near-duplicate script/topic/acoustic take stays in one split;
- transformed/resampled/noisy variants inherit the source split;
- physical overlapping parts of one long recording stay in one split;
- provider/model prompts and thresholds freeze before protected evaluation;
- evaluation labels are not fed back into the same candidate and rescored as if blind;
- report split manifest digest and scoring code commit.

Exact percentages are not set here. The collection plan selects counts by required slices and statistical usefulness before recording, with owner/research review. A small corpus must be described as exploratory rather than made to look representative through percentages.

## 8. Annotation governance

- Publish an annotation guide version before labeling.
- ASR gold text defines punctuation, casing, fillers, numerals, code-switch and inaudible markers; report raw and normalized WER.
- Diarization gold defines collar, overlap handling, unknown/overlap labels and speaker-count policy.
- Decision/task data is double-annotated and adjudicated for high-risk final/cancel/amend/source relationships.
- Every label has sample ID, annotator role, guide version and timestamp; no annotator personal name in public evidence.
- Disagreement is evidence, not a reason to delete a difficult sample.
- Source ranges must stay within the referenced fixture and be validated automatically.
- Speaker label is local to one sample/conversation; no persistent voice identity.
- Human review may not add real names or confidential context to fictional scripts.

## 9. Manifest requirements

Each dataset version has an immutable manifest with at least:

| Field | Requirement |
|---|---|
| `datasetId`, `version` | stable opaque identifier and immutable version |
| `purpose` | allowed PoC IDs and excluded uses |
| `dataClass` | one class from section 2 |
| `origin` | generator/version or controlled collection/external source |
| `licenseId`, `termsDigest` | exact data rights; separate from code/model license |
| `consentReference` | opaque reference or `not-applicable` for synthetic data |
| `trainingAllowed` | `false` by default; true requires separate consent/decision not present now |
| `publicRedistributionAllowed` | `false` by default |
| `sampleId` | random non-identifying ID |
| `contentSha256` | digest stored only where it does not create public linkability |
| `languageSlice` | RU, EN, mixed or non-speech |
| `acousticSlice` | noise/distance/route/overlap metadata |
| `speakerCountBucket` | count/range without identity |
| `split` | development, test or evaluation |
| `parentSampleId` | source for transforms; same split enforced |
| `storageClass`, `evidenceLocator` | controlled location; no signed/private URL |
| `accessRoles` | roles, not public personal names |
| `createdAt`, `expiresAt` | timestamps fixed before collection |
| `deletionState` | active, withdrawal-pending, expired, deleted or quarantined |
| `notes` | content-free limitation/provenance note |

The Git-safe manifest is a redacted projection. It omits participant mapping, consent form, private locator, raw content digest when linkable and any personal/device identifier.

## 10. Access control

- A named data custodian approves and reviews access.
- Access is least-privilege, time-bounded and role-based: collector, annotator, evaluator, security auditor or custodian.
- Raw audio access and annotation-only access are separate.
- Use managed encrypted storage and encrypted transport; local copies require encrypted device/storage and screen lock.
- No shared credentials, public links, personal email attachments or messaging-app transfer.
- Access events record sample IDs/roles/actions without content.
- Export/download is disabled unless required by the protocol; each copy inherits retention/deletion.
- Departing or reassigned personnel lose access promptly.
- External provider access requires separate contract/consent/provider/region gate.
- Public GitHub collaborators never gain dataset access merely through repository access.

Until a custodian and controlled storage are named, purpose-recorded collection is blocked.

Under `OD-08`, controlled non-public storage is also a hard boundary for retained raw traces and audio. Until it is configured, Stage 0 uses only synthetic data; local ad hoc folders, GitHub Actions artifacts and personal cloud storage are not substitutes.

## 11. Retention

Retention must be set before collection. Indefinite storage and “until useful” are prohibited. The following maximums were approved by the Project owner in `OD-09` on 4 August 2026:

- raw purpose-recorded audio: delete no later than 90 days after the named PoC closes;
- derived transcript/annotations: delete no later than 180 days after the named PoC closes;
- withdrawal request: complete deletion within 30 days unless a shorter legal/consent term applies;
- temporary exports, local working copies and failed takes: delete as soon as the operation/review completes, with a maximum defined in the collection plan;
- synthetic generators/source: may be retained with the repository when original, non-personal and license-safe;
- consent/deletion evidence: retain only for the period approved by Legal, separate from research content.

The shorter applicable mandatory period wins. These maximums do not authorize collection: purpose-recorded data still requires the controls in sections 4, 5, 10 and 16.

## 12. Deletion and withdrawal

A deletion request or expiry must cover:

1. raw audio and every transformed copy;
2. transcripts, annotations, embeddings/features and reviewer notes;
3. caches, temporary exports and local downloads;
4. split/index entries and private manifests;
5. controlled backups according to disclosed expiry;
6. provider copies if a separately consented cloud test occurred;
7. participant mapping and access grants where no longer required;
8. derived sample variants linked through `parentSampleId`.

The custodian records opaque sample ID, scope, request/expiry time, completion time, backup expiry and unresolved failures. Public aggregate results already produced are reviewed for re-identification risk; future use stops immediately. Dora does not promise physical flash overwrite.

## 13. Training prohibition

Stage 0 dataset use is evaluation only by default.

Without a new, separate research consent and owner/Legal approval, data must not be used to:

- fine-tune or train ASR, VAD, diarization, speaker, LLM or embedding models;
- create voiceprints or enroll identities;
- improve a third-party provider’s service;
- select examples for human/model training outside the named PoC;
- publish a corpus or derived benchmark set;
- retain “hard examples” after the approved retention period.

This prohibition applies even when the data is already accessible to an engineer or when training would happen only locally.

## 14. Public reporting

Allowed public output:

- aggregate WER/DER/F1/latency/energy/memory/file-size metrics by sufficiently broad slice;
- synthetic sample IDs and generator versions;
- content-free error categories and limitations;
- schema-conforming benchmark results and exact code commit;
- license/provenance summary approved for disclosure.

Forbidden public output:

- audio, transcript, source excerpt or recognizable waveform;
- participant demographics so narrow that identity can be inferred;
- consent form, participant mapping or contact;
- private dataset locator, signed URL or account/provider credential;
- small-cell/example reporting that lets a person recognize their contribution;
- public digest when it enables confirmation against a known personal file.

## 15. Incident handling

If unconsented speech, personal data, a secret, scope drift or unauthorized access is discovered:

1. stop collection/processing and quarantine the affected sample outside public systems;
2. do not copy content into an issue, PR or log;
3. notify the data custodian/security owner with an opaque incident reference;
4. determine affected copies, access and outbound transfers;
5. delete or preserve only as directed by the approved incident/legal process;
6. rotate credentials if a secret is involved;
7. record the control failure and prevention action without sensitive content;
8. require a new review before resuming collection.

## 16. Definition of Ready for a corpus-backed PoC

A PoC using anything beyond synthetic data is `READY` only when:

- owner policy `OD-03`, `OD-04`, `OD-08` and `OD-09` is approved — **satisfied 4 August 2026**;
- exact hypothesis, slices and sample counts are frozen;
- consent form/process and collection script are approved;
- custodian, controlled storage and access roles are named;
- immutable manifest/split and annotation guide exist;
- exact external dataset/model licenses are approved for evaluation;
- retention, withdrawal and deletion have been dry-run with synthetic entries;
- benchmark logging/publication is content-free;
- no raw data path points into the repository or CI artifact directory.

## 17. Stage 0A exit

The owner-approved governance foundation defines allowed data, consent, purpose, language/acoustic coverage, immutable development/test/evaluation separation, access, retention, deletion, public reporting and the training prohibition. The actual corpus remains uncreated. `POC-DATA-001` remains blocked on consent-process approval, a named custodian, controlled non-public storage, access/deletion dry-run and an immutable manifest—not on `OD-03`/`OD-04`/`OD-08`/`OD-09`.
