# Dora MVP 1 — POC-DATA-001 synthetic control plane

Contract ID: `poc-data-control-plane-stage0-v0.1`
Backlog item: `POC-DATA-001`
Date: 18 August 2026
Owner authority: `POC-DATA-CONTROL-PLANE-SETUP-AUTH-20260818-01`
Disjoint-first authority: `POC-DATA-CONTROL-PLANE-DISJOINT-FIRST-AUTH-20260818-01`
Exact implementation base: `main@4a1dacacc52926ef3608a5952a762f00b8dafaa9` / tree `23d0fe235acff4e4693b6eabcedad1e58b8d0816`
Status: **SYNTHETIC CONTROL-PLANE DRY-RUN ONLY; `CUSTODIAN_UNASSIGNED`; REAL COLLECTION NOT AUTHORIZED**

## 1. Decision and claim ceiling

The owner authorizes preparation of the Stage 0 consent process, collection plan, manifest,
role-based access model, retention/deletion protocol, and a dry-run that uses only
repository-owned synthetic and non-sensitive data. The literal `[ФИО]` in the owner instruction
was a placeholder, not an identity assignment. This contract therefore records exactly
`CUSTODIAN_UNASSIGNED` and fails closed for every action that requires a named custodian.

This package may establish only the following facts:

- the six control-plane parts exist in documentary and machine-readable form;
- the exact synthetic manifest passes the task-scoped validator;
- the deterministic local dry-run exercises allowlisted synthetic operations, fail-closed RBAC,
  and idempotent deletion of a transient non-sensitive sentinel;
- no manifest source byte is modified by validation or dry-run;
- all result output is content-free and independent of wall clock, randomness, network, Android,
  account, model, or production storage.

It does **not** create a corpus, make consent operational, configure controlled storage, name a
custodian, admit a schema into the product, authorize collection, close `POC-DATA-001`, or produce
a PoC `PASS`. The backlog item remains `BLOCKED / NOT_READY / NOT_RUN / NOT_AUTHORIZED` for real
or purpose-recorded data.

## 2. Explicit prohibitions

The following remain forbidden by this package and by the owner instruction:

- recording or recruiting a real person, volunteer, meeting, call, room, or device;
- microphone, system-audio, Android, emulator, physical-device, acoustic, or biometric execution;
- raw audio, a real transcript, real names, participant mapping, consent forms, contact data, or
  derived sensitive data;
- cloud, network, provider, region, account, Google Sheet, shared drive, messaging, or remote
  transfer;
- model inference, training, fine-tuning, distillation, embedding enrollment, dataset creation for
  training, or provider improvement;
- production database, storage, consent ledger, deletion topology, schema, dependency, product
  feature, Alpha, RTL, Recovery, or release admission;
- public redistribution, external dataset intake, or any Legal/Security approval claim.

The repository is public. Only the closed synthetic manifest, schema, implementation source,
tests, and content-free aggregate evidence may be committed.

## 3. Consent process — prepared, not operational

Process ID: `poc-data-consent-process-stage0-v0.1`
State: `PREPARED_NOT_OPERATIONAL`

The process is a future gate for purpose-recorded adult-volunteer data. It is not final legal copy
and cannot be used while `CUSTODIAN_UNASSIGNED`, controlled storage is absent, and real collection
is unauthorized.

### 3.1. Required pre-consent packet

Before a future participant sees a recording control, an approved packet must state all of the
following in plain language and in the participant's language:

1. study operator and contact route;
2. exact Dora PoC and evaluation purpose;
3. intentional audio recording and the approved script/scenario;
4. every derivative: transcript, speaker-local labels, annotations, and aggregate metrics;
5. raw and derived access roles;
6. whether listening or other human annotation is required;
7. controlled storage, encryption, retention maximums, and deletion route;
8. every provider, artifact, and region, with `NONE` as the current default;
9. withdrawal process and the limit on retracting already published broad aggregates;
10. explicit statement that training/model improvement is not allowed;
11. voluntary participation and no product/account penalty for refusal;
12. deletion request, completion evidence, and disclosed backup expiry.

The packet must also say that one participant cannot consent for another audible person and that
the take stops if an unconsented voice or out-of-scope content is heard.

### 3.2. Consent state machine

`DRAFT -> REVIEW_REQUIRED -> APPROVED_FOR_NAMED_PLAN -> GRANTED -> REVOKED | EXPIRED`

- `DRAFT` and `REVIEW_REQUIRED` cannot authorize collection.
- `APPROVED_FOR_NAMED_PLAN` requires the owner-approved plan, named custodian, controlled storage,
  access roles, retention, deletion test, and applicable Legal review.
- `GRANTED` is scoped to one policy version, participant, PoC, data classes, uses, human-access
  roles, storage, provider/region, and time range. Missing scope is deny.
- revocation blocks future collection and use immediately and starts the deletion workflow.
- a per-take acknowledgement is separate from the research consent record and does not broaden it.
- public evidence may contain only an opaque consent reference; no signature, contact, or mapping.

Current machine state is fixed to `PREPARED_NOT_OPERATIONAL`, `realConsentRecordCount=0`, and
`consentReference=not-applicable` for every synthetic entry.

## 4. Collection plan — exact synthetic dry-run

Plan ID: `poc-data-synthetic-control-plane-plan-stage0-v0.1`
Plan state: `AUTHORIZED_SYNTHETIC_DRY_RUN_ONLY`

| Field | Frozen value |
|---|---|
| Hypothesis | The control plane can reject forbidden scope and deterministically validate/delete synthetic metadata without real data or external systems. |
| Non-goals | Corpus quality, consent usability, acoustic coverage, model quality, device behavior, controlled-store operations, production behavior. |
| Inputs | One canonical repository manifest plus one transient ASCII sentinel created by the local test harness. |
| Sample rows | Exactly four metadata rows: two roots, one derived child, and one deletion target; no transcript or audio bytes. |
| Data classes | `GENERATED_TEXT_METADATA`, `SYNTHETIC_SIGNAL_METADATA`; metadata only. |
| Participant/voice/meeting count | `0 / 0 / 0`. |
| Split plan | `development`, `test`, `evaluation`; a child must inherit its parent's split. |
| Clock | Frozen `2026-08-18T12:00:00Z`; no wall-clock read. |
| Seed | `2026081801`; recorded only, no randomness call. |
| Storage | Repository manifest plus a validated OS-temp directory used only during the dry-run. |
| Network/cloud/device/model | all disabled and not invoked. |
| Public output | stable status codes, counts, booleans, and SHA-256 of synthetic/control files only. |

Stop immediately and return a content-free failure if the manifest is non-canonical, a field is
unknown or missing, a data class or action is outside the allowlist, a private carrier is non-null,
training/network/real-data flags are true, a role is unknown, a custodian-gated action is requested,
lineage widens access/split/retention, or deletion cannot be verified.

## 5. Manifest contract

Normative files for this package:

- structural schema: `docs/stage0/poc-data-control-plane-stage0-v0.1.schema.json`;
- exact synthetic instance:
  `docs/evidence/poc-data-001/control-plane-synthetic-manifest-stage0-v0.1.json`;
- executable semantic validator/dry-run:
  `tools/poc_data_control_plane/src/main/java/com/monumentogram/dora/stage0/data/controlplane/PocDataControlPlane.java`.

The schema is a closed Draft 2020-12 structural contract. The Java layer additionally requires:

- strict UTF-8 without BOM, duplicate keys, non-integer JSON numbers, hidden format characters, or
  trailing content;
- canonical lexicographic object-key serialization with LF and exactly one terminal newline;
- exact authority, role, action, limitation, blocker, and deletion-scope catalogues;
- opaque IDs and timestamps with fixed formats;
- no sample bytes, sample/content-linked digest, evidence locator, signed URL, participant mapping,
  device/account identifier, consent form, or personal data; the non-null `termsDigest` is only a
  frozen synthetic control-policy profile value, not external-data rights or product admission;
- parent existence, acyclic lineage, equal split, non-widening roles/retention, and propagated
  deletion state;
- exact deny-by-default RBAC and authority flags;
- one deletion-ledger entry for every manifest row in `DELETED` state and none for active rows.

This is the canonical manifest for this **synthetic control-plane dry-run package only**. It is not
the future canonical corpus manifest and does not admit a production schema.

## 6. Role-based access model

Mode: `DENY_BY_DEFAULT`

Roles are capabilities, not people. The manifest contains no personal assignment. The only active
assignment is `SYNTHETIC_DRY_RUN_OPERATOR -> BUILT_IN_TEST_HARNESS_ONLY`.

| Role | Permitted now | Explicitly not permitted now |
|---|---|---|
| `SYNTHETIC_DRY_RUN_OPERATOR` | validate/read the public synthetic manifest; create/delete the transient sentinel; emit content-free summary | controlled store, real/purpose-recorded data, consent approval, access grants, cloud, model use |
| `SECURITY_AUDITOR` | read public manifest and content-free dry-run evidence | raw/derived content, mutate manifest, grant access, delete controlled data |
| `EVALUATOR` | read public manifest only | protected labels, real content, scoring run |
| `COLLECTOR` | none while real collection is unauthorized | collection, recording, raw access |
| `ANNOTATOR` | none while no governed corpus exists | listening, transcript/annotation access |
| `CUSTODIAN` | none because `CUSTODIAN_UNASSIGNED` | approval, access assignment, controlled deletion, export |

Unknown role, unknown action, absent assignment, missing scope, or any custodian-gated action is a
deny. Repository/GitHub access never grants dataset access. The future custodian must be an
explicitly named accountable person or approved accountable role outside the public manifest; this
contract does not invent one.

## 7. Retention and deletion protocol

### 7.1. Retention table

| Class | Current handling | Future maximum already approved by `OD-09` |
|---|---|---|
| Repository control-plane schema/source/manifest | retained as non-personal governance evidence under normal Git lifecycle; no corpus bytes | not a purpose-recorded research datum |
| Transient dry-run sentinel/temp files | delete before the dry-run returns; verify absence; no backup | end of operation |
| Purpose-recorded raw audio | not created or authorized | no later than 90 days after named PoC closure |
| Purpose-recorded transcript/annotations | not created or authorized | no later than 180 days after named PoC closure |
| Withdrawal-scoped research content | not created or authorized | complete within 30 days, or shorter applicable period |
| External/provider copies | prohibited and absent | requires separate consent/provider/region contract |

The shorter mandatory term wins. These maxima constrain a future plan and do not authorize it.

### 7.2. Deletion workflow

For future controlled data, the custodian-gated protocol is:

1. freeze new access/use and record an opaque request/event ID;
2. enumerate the source, descendants, transforms, annotations, caches, working copies, split/index
   entries, private manifests, backups, access grants, and any separately authorized provider copy;
3. revoke assignments and cancel pending processing/transfer;
4. delete the primary and every linked copy; propagate deletion to descendants;
5. verify absence at every declared location and record unresolved failures without content;
6. report local completion and backup/provider expiry independently;
7. retain only approved content-free deletion evidence.

Physical flash overwrite is not promised. A partial or failed scope remains visible and retryable.

The authorized dry-run performs only the analogous lifecycle on one transient, non-sensitive
sentinel under OS temp. It clones the caller bytes exactly once at entry and binds validation,
digests, and the content-free report to that owned snapshot. The sentinel is created with
`CREATE_NEW`, `NOFOLLOW_LINKS`, and a `DELETE_ON_CLOSE` file handle. Cleanup is therefore bound to
the successfully created handle rather than a mutable path. A pre-existing, racing, replaced, or
otherwise unverified path is rejected without deletion; its bytes are not emitted. The dry-run
verifies owned-handle bytes, absence after close, and repeat-safe absence without a second
path-based delete. It does not test controlled storage, backup, provider, participant mapping, or
real withdrawal.

## 8. Deterministic dry-run matrix

| ID | Scenario | Expected result |
|---|---|---|
| `DATA-CP-001` | Validate exact canonical synthetic manifest | `ALLOW / MANIFEST_VALID` |
| `DATA-CP-002` | Repeat validation; mutate caller bytes after owned snapshot | byte-identical owned digest/report; caller mutation isolated |
| `DATA-CP-003` | Synthetic operator reads manifest | allow |
| `DATA-CP-004` | Security auditor reads manifest | allow |
| `DATA-CP-005` | Evaluator reads manifest | allow |
| `DATA-CP-006` | Unknown/unassigned role requests any action | deny, no side effect |
| `DATA-CP-007` | Collector/annotator requests real or raw-data action | deny, no side effect |
| `DATA-CP-008` | `CUSTODIAN` requests a custodian-gated action while unassigned | deny `CUSTODIAN_UNASSIGNED` |
| `DATA-CP-009` | Any role requests network/cloud/model-training action | deny, no side effect |
| `DATA-CP-010` | Create transient synthetic sentinel through exact allowed role | allow; exact bounded bytes only |
| `DATA-CP-011` | Close owned sentinel handle; inject pre-existing/racing/replacement paths | owned file absent and idempotent; unowned path preserved; content-free reject |
| `DATA-CP-012` | Dry-run exits | temp directory absent and source manifest byte-identical |
| `DATA-CP-013` | Invalid UTF-8/BOM/duplicate key/non-canonical/oversized/deep input | stable content-free reject |
| `DATA-CP-014` | Unknown/missing field, forbidden class/flag/private carrier | stable content-free reject |
| `DATA-CP-015` | Parent cycle/split/access/retention/deletion mismatch | stable content-free reject |

The local evidence record may say `LOCAL_PASS` for these exact implementation checks. It must also
say `pocDataOverallResult=NOT_RUN`, `pocDataReadiness=NOT_READY`, and
`collectionAuthorization=NOT_AUTHORIZED`.

## 9. Authority flags

True only in this scope:

- `controlPlanePreparationAllowed`;
- `syntheticManifestArtifactAllowed`;
- `localHostValidationAllowed`;
- `syntheticDryRunAllowed`;
- `transientSyntheticSentinelAllowed`;
- `contentFreeEvidenceAllowed`.

False and fail-closed:

- `custodianAssigned`, `consentProcessOperational`, `controlledStorageConfigured`;
- `realPeopleAllowed`, `purposeRecordedCollectionAllowed`, `realMeetingDataAllowed`;
- `rawAudioAllowed`, `derivedSensitiveDataAllowed`, `humanReviewAllowed`;
- `networkExecutionAllowed`, `cloudTransferAllowed`, `deviceExecutionAllowed`;
- `modelInferenceAllowed`, `trainingAllowed`, `modelImprovementAllowed`;
- `productionSchemaAllowed`, `productionStorageAllowed`, `dependencyAdmissionAllowed`;
- `productionAdmissionAllowed`, `pocDataReadyAllowed`, `pocDataPassAllowed`,
  `publicationAllowedByThisPackage`, `mergeAllowedByThisPackage`.

## 10. Remaining blockers and next owner gates

The synthetic control plane does not remove these blockers:

1. `DATA_CUSTODIAN_UNASSIGNED`;
2. `CONTROLLED_NON_PUBLIC_STORAGE_NOT_CONFIGURED`;
3. `CONSENT_PROCESS_PREPARED_NOT_OPERATIONAL`;
4. `REAL_COLLECTION_NOT_AUTHORIZED`;
5. `ACTUAL_CORPUS_COUNTS_SLICES_AND_SCRIPTS_NOT_FROZEN`;
6. `ACTUAL_IMMUTABLE_CORPUS_MANIFEST_AND_SPLITS_ABSENT`;
7. `ANNOTATION_AND_ADJUDICATION_GUIDES_ABSENT`;
8. `CONTROLLED_STORAGE_ACCESS_AND_DELETION_DRY_RUN_NOT_RUN`;
9. `EXTERNAL_DATA_RIGHTS_NOT_APPROVED`;
10. `PRODUCTION_LEGAL_AND_SECURITY_APPROVAL_ABSENT`.

Before any real person, voice, recording, meeting, retained raw trace, or controlled-store entry,
the owner must name the custodian, approve the exact plan/consent/script/counts/slices, identify and
verify controlled storage, approve access assignments and deletion evidence, and issue a separate
collection authorization after applicable Legal/Security review. Cloud transfer and model training
each require their own later authorization and are not bundled with collection consent.

## 11. Validation and handoff

The implementation is accepted only when:

- Java 17 compile with `--release 17 -Xlint:all -Werror` succeeds without third-party dependency;
- adversarial tests and the exact manifest CLI pass repeatedly with byte-identical output;
- RBAC denies unknown, unassigned, real-data, cloud, and training operations before side effects;
- the synthetic temp sentinel is deleted only through its owned handle, repeat absence is safe,
  cleanup absence is verified, and any pre-existing/racing/replacement path is preserved;
- validation, digests, and the report remain bound to one entry snapshot under caller mutation;
- JSON parses with duplicate-key rejection, UTF-8/LF/no-BOM checks pass, and file pins are recorded;
- diff/secret/PII/private-path/raw-content/network-endpoint scans are clean;
- an independent read-only advisory review records exact P0/P1/P2 findings with
  `formalReviewer=false`;
- no Backlog, Stage Status, Alpha, RTL, Recovery, product, or production file is changed in the
  disjoint-first implementation phase.

Publication, if separately confirmed, is Draft-PR-only. No PR merge is authorized by this
contract.
