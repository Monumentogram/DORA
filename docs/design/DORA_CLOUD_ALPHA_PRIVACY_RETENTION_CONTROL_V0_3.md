# DORA — Cloud Alpha privacy, retention and control v0.3

11.1C = PARTIAL. RT-01 / RT-02 = RESOLVED; RETENTION = SATISFIED. PC-01 remains the primary prerequisite; SC-02 depends only on PRIVACY. This is documentation/design closure, not runtime admission.

[Previous v0.2](DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.md) · [Frozen gates](../contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json) · [ADR-0012](../adr/ADR-0012-alpha-retention-periods-and-provider-copy-limits.md) · [Machine contract](../contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_3.json)

## Retention matrix index

All 56 existing cells are classified; multiple states identify separate components/outcomes within one cell, not a competing matrix. Full per-cell controls follow below.

| Cell | Artifact | Holder | Lifecycle states |
|---|---|---|---|
| LC-01-1 | original_audio | ANDROID_LOCAL | UNTIL_EXPLICIT_USER_DELETE |
| LC-01-2 | original_audio | DORA_CONTROL_PLANE | IMMEDIATE_POST_SUCCESS_DELETE, MAX_24_HOURS |
| LC-01-3 | original_audio | AWS_PROVIDER | PROVIDER_DOCUMENTED_TRIGGER |
| LC-01-4 | original_audio | BACKUP_REPLICA_SYSTEMS | NOT_STORED, PROVIDER_DOCUMENTED_TRIGGER |
| LC-02-1 | upload_objects | ANDROID_LOCAL | UNTIL_EXPLICIT_USER_DELETE |
| LC-02-2 | upload_objects | DORA_CONTROL_PLANE | IMMEDIATE_POST_SUCCESS_DELETE, MAX_24_HOURS |
| LC-02-3 | upload_objects | AWS_PROVIDER | IMMEDIATE_POST_SUCCESS_DELETE, MAX_24_HOURS |
| LC-02-4 | upload_objects | BACKUP_REPLICA_SYSTEMS | NOT_STORED, PROVIDER_DOCUMENTED_TRIGGER |
| LC-03-1 | provider_temporary_copies | ANDROID_LOCAL | NOT_STORED |
| LC-03-2 | provider_temporary_copies | DORA_CONTROL_PLANE | NOT_STORED |
| LC-03-3 | provider_temporary_copies | AWS_PROVIDER | PROVIDER_DOCUMENTED_TRIGGER |
| LC-03-4 | provider_temporary_copies | BACKUP_REPLICA_SYSTEMS | PROVIDER_DOCUMENTED_TRIGGER |
| LC-04-1 | provider_jobs_metadata | ANDROID_LOCAL | MAX_30_DAYS |
| LC-04-2 | provider_jobs_metadata | DORA_CONTROL_PLANE | MAX_30_DAYS |
| LC-04-3 | provider_jobs_metadata | AWS_PROVIDER | PROVIDER_DOCUMENTED_DEFAULT_WITH_EARLY_DELETE |
| LC-04-4 | provider_jobs_metadata | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS, PROVIDER_DOCUMENTED_TRIGGER |
| LC-05-1 | transcripts | ANDROID_LOCAL | UNTIL_EXPLICIT_USER_DELETE |
| LC-05-2 | transcripts | DORA_CONTROL_PLANE | UNTIL_EXPLICIT_USER_DELETE, IMMEDIATE_POST_SUCCESS_DELETE, MAX_24_HOURS |
| LC-05-3 | transcripts | AWS_PROVIDER | IMMEDIATE_POST_SUCCESS_DELETE, MAX_24_HOURS, NOT_STORED, PROVIDER_DOCUMENTED_TRIGGER |
| LC-05-4 | transcripts | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS, PROVIDER_DOCUMENTED_TRIGGER |
| LC-06-1 | transcript_versions_edits | ANDROID_LOCAL | UNTIL_EXPLICIT_USER_DELETE |
| LC-06-2 | transcript_versions_edits | DORA_CONTROL_PLANE | NOT_STORED |
| LC-06-3 | transcript_versions_edits | AWS_PROVIDER | NOT_STORED |
| LC-06-4 | transcript_versions_edits | BACKUP_REPLICA_SYSTEMS | NOT_STORED |
| LC-07-1 | caches | ANDROID_LOCAL | UNTIL_EXPLICIT_USER_DELETE, NOT_APPLICABLE |
| LC-07-2 | caches | DORA_CONTROL_PLANE | IMMEDIATE_POST_SUCCESS_DELETE, MAX_24_HOURS |
| LC-07-3 | caches | AWS_PROVIDER | PROVIDER_DOCUMENTED_TRIGGER |
| LC-07-4 | caches | BACKUP_REPLICA_SYSTEMS | NOT_STORED |
| LC-08-1 | operational_logs | ANDROID_LOCAL | MAX_30_DAYS |
| LC-08-2 | operational_logs | DORA_CONTROL_PLANE | MAX_30_DAYS |
| LC-08-3 | operational_logs | AWS_PROVIDER | PROVIDER_DOCUMENTED_TRIGGER |
| LC-08-4 | operational_logs | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS, PROVIDER_DOCUMENTED_TRIGGER |
| LC-09-1 | audit_security_logs | ANDROID_LOCAL | MAX_90_DAYS |
| LC-09-2 | audit_security_logs | DORA_CONTROL_PLANE | MAX_90_DAYS |
| LC-09-3 | audit_security_logs | AWS_PROVIDER | MAX_90_DAYS, PROVIDER_DOCUMENTED_TRIGGER |
| LC-09-4 | audit_security_logs | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS, PROVIDER_DOCUMENTED_TRIGGER |
| LC-10-1 | consent_records | ANDROID_LOCAL | ACTIVE_LIFETIME_PLUS_90_DAYS |
| LC-10-2 | consent_records | DORA_CONTROL_PLANE | ACTIVE_LIFETIME_PLUS_90_DAYS |
| LC-10-3 | consent_records | AWS_PROVIDER | NOT_STORED |
| LC-10-4 | consent_records | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS |
| LC-11-1 | deletion_records_receipts | ANDROID_LOCAL | 120_DAYS_AFTER_LOGICAL_DELETE |
| LC-11-2 | deletion_records_receipts | DORA_CONTROL_PLANE | 120_DAYS_AFTER_LOGICAL_DELETE |
| LC-11-3 | deletion_records_receipts | AWS_PROVIDER | MAX_90_DAYS, PROVIDER_DOCUMENTED_TRIGGER |
| LC-11-4 | deletion_records_receipts | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS |
| LC-12-1 | backups | ANDROID_LOCAL | NOT_STORED |
| LC-12-2 | backups | DORA_CONTROL_PLANE | MAX_30_DAYS, NOT_STORED |
| LC-12-3 | backups | AWS_PROVIDER | MAX_30_DAYS, PROVIDER_DOCUMENTED_TRIGGER, NOT_STORED |
| LC-12-4 | backups | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS, PROVIDER_DOCUMENTED_TRIGGER, NOT_STORED |
| LC-13-1 | replicas | ANDROID_LOCAL | NOT_STORED |
| LC-13-2 | replicas | DORA_CONTROL_PLANE | MAX_30_DAYS, UNTIL_EXPLICIT_USER_DELETE, NOT_STORED |
| LC-13-3 | replicas | AWS_PROVIDER | PROVIDER_DOCUMENTED_TRIGGER |
| LC-13-4 | replicas | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS, PROVIDER_DOCUMENTED_TRIGGER, NOT_STORED |
| LC-14-1 | identity_credentials | ANDROID_LOCAL | ACTIVE_LIFETIME_PLUS_90_DAYS |
| LC-14-2 | identity_credentials | DORA_CONTROL_PLANE | ACTIVE_LIFETIME_PLUS_90_DAYS |
| LC-14-3 | identity_credentials | AWS_PROVIDER | NOT_STORED |
| LC-14-4 | identity_credentials | BACKUP_REPLICA_SYSTEMS | MAX_30_DAYS, NOT_STORED |

## branch

chat/alpha-asr-runner-scope

## sources

### GATES

| Field | Value |
|---|---|
| path | docs/contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 3a03cffa43a0f2743da54b0d47f050f4ac06a260f8e63b66ef60869107a8e350 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### GATES_MD

| Field | Value |
|---|---|
| path | docs/contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | d9f8082076d5294bd2b5ee3071bb97b951b824dad60feb012fdff0caad704951 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### GAPS

| Field | Value |
|---|---|
| path | docs/contracts/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.json |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | a3c36d48877fa0e8e385392da3e6e2c19fe49ff99c1ca45535dc225baa391149 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### GAPS_MD

| Field | Value |
|---|---|
| path | docs/stage0/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | bb5d14f4fbd563f0794df5f322c20ce754e3d87a730060323950a5ec5af8356b |
| hash_basis | raw Git blob, no working-tree newline conversion |


### PREVIOUS

| Field | Value |
|---|---|
| path | docs/evidence/alpha-readiness-6.2-closeout-v0.1.json |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | d295e6826c0c7d6e8e3c8818b7683357d97803f42f0b1c4f3d5ff8c2dd8f3cd8 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### ADR9

| Field | Value |
|---|---|
| path | docs/adr/ADR-0009-alpha-cloud-execution-boundary.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | e614c1817d68c03fb31f4e026bfda7a81099b8ea72deff3a82aea8a4bb8ec2ca |
| hash_basis | raw Git blob, no working-tree newline conversion |


### ADR10

| Field | Value |
|---|---|
| path | docs/adr/ADR-0010-first-alpha-scope-and-aws-platform.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 38fefa83a22326d46f63754350a0547d56ee0dba7f7fc5c780b71619ca76fc2c |
| hash_basis | raw Git blob, no working-tree newline conversion |


### DECISIONS

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_PRODUCT_DECISIONS.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 9710352aafe0c193e9ac499e3b8221d925c8a89b4fe3509465ca774302e7eab1 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### STORAGE

| Field | Value |
|---|---|
| path | docs/design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 4724f574dc2575445c6b1d50e30691061f79c09d815035163892f5d5d6478273 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### READINESS

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_IMPLEMENTATION_READINESS.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | e9e6bd83216b1265b0681b9845b13fca16d4e0c50dd01accdb2e35cfe1095db6 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### BACKLOG

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | ce47bc7214f5a2e7308b5fa5aa47dd0cf1d7b872c8485ac67927d5e8870a3f7d |
| hash_basis | raw Git blob, no working-tree newline conversion |


### STATUS

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_STAGE_STATUS.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | c1bf3860b421de425dfce3621011ebff04c5be8ae150e6d0c12019caf02ee1f5 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### TECHNICAL

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_TECHNICAL_PLAN.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 00c4d6816265e86c82e44045a77f85c6e68b43e6094a3736e10ab44eed259a59 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### DESIGN

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_DESIGN_SPEC.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 8bc88bd5b9cba036bbc5758957a4a2c1fdb64a053a319cf4b2f9ba544ea81f2b |
| hash_basis | raw Git blob, no working-tree newline conversion |


### TEST

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_TEST_STRATEGY.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | cfa2d09387ed4bb80f0ff71a7138930c7a642fdad75c37b0b150774b8d74fbe8 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### THREATS

| Field | Value |
|---|---|
| path | docs/stage0/DORA_MVP1_PRIVACY_DATA_FLOW_THREAT_MODEL.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | d9b793eb5bb9a0c4ef64a03b815fc16425ca923921c98e539811f048853bb251 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### ASR_DATA

| Field | Value |
|---|---|
| path | docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 2f6b275f6bd413c58c7ac3495e590491438a16472bb2c0125cb6b69b5b05e009 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### ASR_PRODUCT

| Field | Value |
|---|---|
| path | docs/product/DORA_ASR_USER_SCENARIOS_V0_1.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 68ae0749024ac0acfa791c35894b8e1d30c61351daa67f0144e12604962bf537 |
| hash_basis | raw Git blob, no working-tree newline conversion |


### SCOPE

| Field | Value |
|---|---|
| path | docs/contracts/DORA_ALPHA_SCOPE_V0_1.json |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | f8ab5373e5dee3fc96e4229d333f4c178baee5b4b67f8a896a21b15965b762be |
| hash_basis | raw Git blob, no working-tree newline conversion |


### PRIOR_11C_JSON

| Field | Value |
|---|---|
| path | docs/contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.json |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | 1002e0af262bff52c86e551ba64722c096ca41173b66e44b66421dcf5322290c |
| hash_basis | raw Git blob, no working-tree newline conversion |


### PRIOR_11C_MD

| Field | Value |
|---|---|
| path | docs/design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | dcc9392a09d8d4cca44ce8f4b9cd061ca21aede4903410602c2c5b787aebc40a |
| hash_basis | raw Git blob, no working-tree newline conversion |


### PRIOR_V02_JSON

| Field | Value |
|---|---|
| path | docs/contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.json |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | cf9bf1a1e43725313a2f74e283f5a058b376c4b9f9032f441effa55ce68de8e5 |
| hash_basis | raw Git blob; no newline conversion |


### PRIOR_V02_MD

| Field | Value |
|---|---|
| path | docs/design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | c3d17a9602731f6639fcab5831529ee64bd0dc81b75e6231df4468247814e025 |
| hash_basis | raw Git blob; no newline conversion |


### ADR11

| Field | Value |
|---|---|
| path | docs/adr/ADR-0011-alpha-transcribe-privacy-retention-key-custody.md |
| commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| sha256 | c366875e12d84f35b602de8ab92ac4c7e588d6908e4f3fec2a122c129b6d92b5 |
| hash_basis | raw Git blob; no newline conversion |



## gate_results

### CLD-ADM-PRIVACY-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-PRIVACY-001 |
| before | BLOCKED |
| after | BLOCKED |
| blocking | true |
| required_evidence_disposition | Existing scope/provider/design evidence preserved; only qualified actual-scope Privacy/Legal approval PC-01 remains. |
| rationale | Frozen wording/evidence/dependencies preserved; only retention and consequent dependency readback changed. Runtime/quality evidence not claimed. |

#### frozen_gate

| Field | Value |
|---|---|
| gate_id | CLD-ADM-PRIVACY-001 |
| name | Privacy and disclosure policy |
| purpose | Resolve processing scope before implementation and data transfer. |
| owner_domain | Product / Privacy / Legal |
| current_status | BLOCKED |
| blocking | true |
| blocking_reason | No approved Alpha Cloud disclosure/legal/data-use package; DEC-010/011 remain Proposed and CLOUD-02 is BLOCKED. |
| next_action | 11.1C, CLOUD-02, BE-LEGAL-001, BE-CONSENT-001: produce and review the required evidence; No approved Alpha Cloud disclosure/legal/data-use package; DEC-010/011 remain Proposed and CLOUD-02 is BLOCKED. |

##### required_before

- BEFORE_CLOUD_RUNTIME_IMPLEMENTATION
- BEFORE_PROVIDER_ADMISSION
- BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD
- BEFORE_INTERNAL_ALPHA_ACCEPTANCE
- BEFORE_PUBLIC_RELEASE

##### acceptance_criteria

- Approved data-flow inventory states what leaves device, destination/service role, purpose and applicable controller/processor responsibilities
- Versioned user-visible disclosure covers provider/subprocessors, data location, retention, deletion and revocation; revocation is not retroactive erasure
- ASR processing consent does not authorize training, human review or unrelated artifacts
- Provider/region changes cannot silently expand grants; no location inference from VPN
- Record qualified owner approval for actual scope; this gate contract supplies no legal approval

##### required_evidence

- Reviewed disclosure text/version, data-flow map and privacy/legal decision
- Candidate/provider terms and data-use/subprocessor/location evidence bound to scope, then revalidated for selected provider

##### dependencies

- CLD-ADM-SCOPE-001
- CLD-ADM-CONSENT-001

##### roadmap_tasks

- 11.1C
- CLOUD-02
- BE-LEGAL-001
- BE-CONSENT-001

##### current_evidence

### Item 1

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_PRODUCT_DECISIONS.md |
| section | DEC-010; DEC-011; DEC-014; DEC-039 |
| baseline_commit | 1107d4d9eef80691372800da0a17e609eba81957 |


### Item 2

| Field | Value |
|---|---|
| path | docs/adr/ADR-0009-alpha-cloud-execution-boundary.md |
| section | Retention and deletion |
| baseline_commit | 1107d4d9eef80691372800da0a17e609eba81957 |



#### criterion_assessments

### CLD-ADM-PRIVACY-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-PRIVACY-001 |
| index | 1 |
| criterion_verbatim | Approved data-flow inventory states what leaves device, destination/service role, purpose and applicable controller/processor responsibilities |
| previous_assessment | PARTIALLY_SATISFIED |
| new_assessment | PARTIALLY_SATISFIED |
| evidence | Approved operational DF-01..08 and OD-01/02/03 scope exist; the actual legal responsibility binding belongs to the missing qualified decision. |
| owner_decision_resolves_it | false |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-01
- OD-11C-02
- OD-11C-03
- AWS-DPA

###### blocking_decision_ids

- PC-01


### CLD-ADM-PRIVACY-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-PRIVACY-001 |
| index | 2 |
| criterion_verbatim | Versioned user-visible disclosure covers provider/subprocessors, data location, retention, deletion and revocation; revocation is not retroactive erasure |
| previous_assessment | PARTIALLY_SATISFIED |
| new_assessment | PARTIALLY_SATISFIED |
| evidence | Retention periods/limitations now approved and RU/EN v0.3 addendum supplied. Only PC-01 qualified actual-scope/disclosure approval remains. This is dependency readback, not new legal approval. |
| owner_decision_resolves_it | false |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-02
- OD-11C-04
- OD-11C-05
- AWS-SUBPROCESSORS
- AWS-OUTPUT

###### blocking_decision_ids

- PC-01


### CLD-ADM-PRIVACY-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-PRIVACY-001 |
| index | 3 |
| criterion_verbatim | ASR processing consent does not authorize training, human review or unrelated artifacts |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Purpose prohibition and mandatory effective opt-out are explicit; unrelated human review/support content sharing disabled. Required operational employee access is disclosed, not described as user-authorized human review. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | true |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-07
- AWS-TERMS
- AWS-OPT
- AWS-ORG
- AWS-DPA

###### blocking_decision_ids

None.


### CLD-ADM-PRIVACY-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-PRIVACY-001 |
| index | 4 |
| criterion_verbatim | Provider/region changes cannot silently expand grants; no location inference from VPN |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Single-region grant binding; no silent provider/region expansion or VPN inference; explicit redecision/reconsent for changed scope. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-02
- OD-11C-03

###### blocking_decision_ids

None.


### CLD-ADM-PRIVACY-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-PRIVACY-001 |
| index | 5 |
| criterion_verbatim | Record qualified owner approval for actual scope; this gate contract supplies no legal approval |
| previous_assessment | BLOCKED |
| new_assessment | BLOCKED |
| evidence | Project Owner product/architecture decisions exist; no attributable qualified privacy/legal actual-scope approval is supplied. Frozen criterion does not automatically equate the two. |
| owner_decision_resolves_it | false |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-01
- OD-11C-12

###### blocking_decision_ids

- PC-01


#### next_evidence_required

- PC-01

#### blocking_decision_ids

- PC-01


### CLD-ADM-RETENTION-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-RETENTION-001 |
| before | PARTIALLY_SATISFIED |
| after | SATISFIED |
| blocking | false |
| required_evidence_disposition | Both frozen required-evidence items supplied by OD-11C-13..22, same 56-cell matrix, AWS fact limitations, ADR-0012 and guarded 30/120-day proof. |
| rationale | Frozen wording/evidence/dependencies preserved; only retention and consequent dependency readback changed. Runtime/quality evidence not claimed. |

#### frozen_gate

| Field | Value |
|---|---|
| gate_id | CLD-ADM-RETENTION-001 |
| name | Retention and deletion policy |
| purpose | Resolve every stored copy and its deletion semantics. |
| owner_domain | Privacy / Backend / Storage / Project Owner |
| current_status | PARTIALLY_SATISFIED |
| blocking | true |
| blocking_reason | Local policy is approved, but Cloud periods, provider retention, backup behavior and remote receipts remain unresolved under DEC-012. |
| next_action | 11.1C, 8.2C, CLOUD-02, BE-DELETE-001: produce and review the required evidence; Local policy is approved, but Cloud periods, provider retention, backup behavior and remote receipts remain unresolved under DEC-012. |

##### required_before

- BEFORE_CLOUD_RUNTIME_IMPLEMENTATION
- BEFORE_PROVIDER_ADMISSION
- BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD
- BEFORE_INTERNAL_ALPHA_ACCEPTANCE
- BEFORE_PUBLIC_RELEASE

##### acceptance_criteria

- Preserve approved local default: original audio until explicit deletion; automatic retention OFF with unapproved numeric catalog unavailable
- Approve actual periods/triggers for DORA objects, provider copies, job metadata, transcripts, caches, logs, backups and replicas; no arbitrary TTL from recommendations
- Define deletion propagation, receipt scope, failure/retry, late callbacks, restore suppression and source-unavailable behavior
- Consent revocation != deletion; local deletion != proof of remote deletion; disclose limits of provider receipts
- Audio-only deletion preserves transcripts/edits; whole recording deletion uses exact disclosed cascade

##### required_evidence

- Approved per-artifact/per-holder lifecycle matrix
- Versioned Cloud period/trigger decision plus backup/replica and receipt semantics

##### dependencies

- CLD-ADM-SCOPE-001

##### roadmap_tasks

- 11.1C
- 8.2C
- CLOUD-02
- BE-DELETE-001

##### current_evidence

### Item 1

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_PRODUCT_DECISIONS.md |
| section | DEC-012; DEC-013 |
| baseline_commit | 1107d4d9eef80691372800da0a17e609eba81957 |


### Item 2

| Field | Value |
|---|---|
| path | docs/design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md |
| section | Normative principles and defaults; Remote deletion states and transitions |
| baseline_commit | 1107d4d9eef80691372800da0a17e609eba81957 |



#### criterion_assessments

### CLD-ADM-RETENTION-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-RETENTION-001 |
| index | 1 |
| criterion_verbatim | Preserve approved local default: original audio until explicit deletion; automatic retention OFF with unapproved numeric catalog unavailable |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Approved local until-explicit-delete rule and automatic-retention OFF remain unchanged. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-04

###### blocking_decision_ids

None.


### CLD-ADM-RETENTION-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-RETENTION-001 |
| index | 2 |
| criterion_verbatim | Approve actual periods/triggers for DORA objects, provider copies, job metadata, transcripts, caches, logs, backups and replicas; no arbitrary TTL from recommendations |
| previous_assessment | PARTIALLY_SATISFIED |
| new_assessment | SATISFIED |
| evidence | 56/56 existing cells classified; DORA 24h/30d/90d/active+90d periods, explicit transcript/audio success cleanup and early terminal job deletion applied. Frozen criterion 2 permits approved periods/triggers. OD-11C-14 accepts strongest documented internal-provider purpose/control/limitation with no invented purge TTL. Every holder/artifact has explicit backup/replica inclusion, horizon, deletion/receipt, restore and retirement rules. No audio backup; DORA non-audio restorable horizon<=30d<120d tombstone. Guarded expiry and callback non-creation cover unresolved provider exposure without claiming physical purge. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | true |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-13
- OD-11C-14
- OD-11C-15
- OD-11C-16
- OD-11C-17
- OD-11C-18
- OD-11C-19
- OD-11C-20
- OD-11C-21
- OD-11C-22
- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-05
- RET-AWS-06
- RET-AWS-07
- RET-AWS-08
- RET-AWS-09
- RET-AWS-10
- RET-AWS-11
- RET-AWS-12
- RET-AWS-13
- RET-AWS-14
- RET-AWS-15

###### blocking_decision_ids

None.


### CLD-ADM-RETENTION-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-RETENTION-001 |
| index | 3 |
| criterion_verbatim | Define deletion propagation, receipt scope, failure/retry, late callbacks, restore suppression and source-unavailable behavior |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Every holder/artifact has explicit backup/replica inclusion, horizon, deletion/receipt, restore and retirement rules. No audio backup; DORA non-audio restorable horizon<=30d<120d tombstone. Guarded expiry and callback non-creation cover unresolved provider exposure without claiming physical purge. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | true |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-20
- OD-11C-21
- RET-AWS-05
- RET-AWS-07
- RET-AWS-11
- RET-AWS-14
- RET-AWS-15

###### blocking_decision_ids

None.


### CLD-ADM-RETENTION-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-RETENTION-001 |
| index | 4 |
| criterion_verbatim | Consent revocation != deletion; local deletion != proof of remote deletion; disclose limits of provider receipts |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Revocation remains distinct from artifact deletion; local completion is not remote receipt. Provider internal purge timing is unpublished and explicitly accepted/disclosed under OD-11C-14. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | true |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-14
- OD-11C-18
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04

###### blocking_decision_ids

None.


### CLD-ADM-RETENTION-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-RETENTION-001 |
| index | 5 |
| criterion_verbatim | Audio-only deletion preserves transcripts/edits; whole recording deletion uses exact disclosed cascade |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Audio-only deletion retains accepted transcripts/edits/versions; whole-recording exact local and separately confirmed remote cascades preserved. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-05
- OD-11C-09

###### blocking_decision_ids

None.


#### next_evidence_required

None.

#### blocking_decision_ids

None.


### CLD-ADM-CONTROL-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-CONTROL-001 |
| before | BLOCKED |
| after | BLOCKED |
| blocking | true |
| required_evidence_disposition | Accepted ADR-0011/control design and four RDY dispositions preserved; dependency PC-01 / PRIVACY alone prevents CONTROL satisfaction. |
| rationale | Frozen wording/evidence/dependencies preserved; only retention and consequent dependency readback changed. Runtime/quality evidence not claimed. |

#### frozen_gate

| Field | Value |
|---|---|
| gate_id | CLD-ADM-CONTROL-001 |
| name | Cloud security and ownership design |
| purpose | Close the pre-code trust/key/identity design questions. |
| owner_domain | Security / Backend architecture |
| current_status | OPEN |
| blocking | true |
| blocking_reason | ADR-0009 specifies responsibilities, but does not resolve credential mechanism, key custody, consent/deletion ledgers or bounded design review. |
| next_action | 11.1C, BE-AUTH-001, BE-API-001, BE-DELETE-001: produce and review the required evidence; ADR-0009 specifies responsibilities, but does not resolve credential mechanism, key custody, consent/deletion ledgers or bounded design review. |

##### required_before

- BEFORE_CLOUD_RUNTIME_IMPLEMENTATION
- BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD
- BEFORE_CLOUD_RUNTIME_INTEGRATION
- BEFORE_INTERNAL_ALPHA_ACCEPTANCE
- BEFORE_PUBLIC_RELEASE

##### acceptance_criteria

- Reviewed design defines caller/installation identity and server-issued credentials, optional future accounts, ownership, refresh/revocation and replay protection; no identity provider chosen here
- Resolve payload protection, encryption at rest, key custody/worker decrypt access and audit; no unproved E2EE claim
- Specify independent server authorization, upload authority revocation, consent ledger and durable deletion ledger
- Bounded threat/design review dispositions RDY-011/012/013/018 and identifies testable controls, owners and residual risks; unresolved blocking risks prevent entry
- Recording, offline use and Local ASR do not require a Cloud account

##### required_evidence

- Accepted bounded Alpha security/identity/key-custody design with reviewer and risk dispositions
- Approved consent/deletion ledger and ownership model; runtime proof remains separate

##### dependencies

- CLD-ADM-PRIVACY-001
- CLD-ADM-RETENTION-001

##### roadmap_tasks

- 11.1C
- BE-AUTH-001
- BE-API-001
- BE-DELETE-001

##### current_evidence

### Item 1

| Field | Value |
|---|---|
| path | docs/DORA_MVP1_IMPLEMENTATION_READINESS.md |
| section | RDY-011; RDY-012; RDY-013; RDY-018 |
| baseline_commit | 1107d4d9eef80691372800da0a17e609eba81957 |


### Item 2

| Field | Value |
|---|---|
| path | docs/adr/ADR-0009-alpha-cloud-execution-boundary.md |
| section | Identity principle; Audio data plane |
| baseline_commit | 1107d4d9eef80691372800da0a17e609eba81957 |



#### criterion_assessments

### CLD-ADM-CONTROL-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-CONTROL-001 |
| index | 1 |
| criterion_verbatim | Reviewed design defines caller/installation identity and server-issued credentials, optional future accounts, ownership, refresh/revocation and replay protection; no identity provider chosen here |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Owner-accepted reviewed installation identity, credential refresh/revocation/replay/ownership design; optional accounts separate. Exact implementation parameter profile is required before AUTH/UPLOAD runtime acceptance. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-12

###### blocking_decision_ids

None.


### CLD-ADM-CONTROL-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-CONTROL-001 |
| index | 2 |
| criterion_verbatim | Resolve payload protection, encryption at rest, key custody/worker decrypt access and audit; no unproved E2EE claim |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | ADR-0011 resolves TLS, customer KMS/S3 envelope boundary, managed Transcribe plaintext/internal keys, least-privilege roles and audit; no E2EE or immediate crypto-erasure claim. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | true |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-12
- AWS-ENCRYPT
- AWS-IAM
- AWS-KMS-AUDIT

###### blocking_decision_ids

None.


### CLD-ADM-CONTROL-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-CONTROL-001 |
| index | 3 |
| criterion_verbatim | Specify independent server authorization, upload authority revocation, consent ledger and durable deletion ledger |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Four independent checks, revocable DORA ingress and durable consent/deletion ledgers are specified and owner-accepted; no claim a running Transcribe job can be synchronously hard-cancelled. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | true |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-12
- AWS-DELETE-JOB

###### blocking_decision_ids

None.


### CLD-ADM-CONTROL-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-CONTROL-001 |
| index | 4 |
| criterion_verbatim | Bounded threat/design review dispositions RDY-011/012/013/018 and identifies testable controls, owners and residual risks; unresolved blocking risks prevent entry |
| previous_assessment | PARTIALLY_SATISFIED |
| new_assessment | PARTIALLY_SATISFIED |
| evidence | Accepted bounded RDY review preserved. RDY-013/018 retention residuals resolved under OD-11C-13..22. RDY-011 / PC-01 remains; CONTROL still depends on blocked PRIVACY. SC-02 has no RT dependency. |
| owner_decision_resolves_it | false |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-12
- OD-11C-14
- OD-11C-20
- OD-11C-21

###### blocking_decision_ids

- SC-02


### CLD-ADM-CONTROL-001

| Field | Value |
|---|---|
| gate_id | CLD-ADM-CONTROL-001 |
| index | 5 |
| criterion_verbatim | Recording, offline use and Local ASR do not require a Cloud account |
| previous_assessment | SATISFIED |
| new_assessment | SATISFIED |
| evidence | Recording, offline use and installed Local ASR require no Cloud account/identity/network. |
| owner_decision_resolves_it | true |
| aws_evidence_resolves_it | false |
| runtime_evidence_required_later | true |
| runtime_evidence_is_required_for_this_design_assessment | false |

###### authority

- OD-11C-12

###### blocking_decision_ids

None.


#### next_evidence_required

- SC-02

#### blocking_decision_ids

- SC-02


## blocking_decisions

### PC-01

| Field | Value |
|---|---|
| id | PC-01 |
| previous_status | QUALIFIED_APPROVAL_NOT_AVAILABLE |
| status | BLOCKED_EXTERNAL_APPROVAL |
| resolved_evidence | Audience/access/region/candidate/audio policy/security-owner acceptance resolved by OD-11C-01..12. |
| remaining_issue | Attributable qualified privacy/legal scope decision is absent. It must bind actual responsible party/contact, applicable controller/processor/contracting roles and reviewed disclosure; this is the narrow approval record, not an unresolved choice of Alpha audience/region. |
| owner | Project Owner + qualified Privacy/Legal scope approver |
| blocks_11_1C | true |
| required_next_evidence | Attributable qualified privacy/legal scope decision is absent. It must bind actual responsible party/contact, applicable controller/processor/contracting roles and reviewed disclosure; this is the narrow approval record, not an unresolved choice of Alpha audience/region. |
| deadline | Before affected gate closure / 6.3 |

#### evidence_ids

- OD-11C-01
- OD-11C-02
- OD-11C-03
- OD-11C-12
- AWS-DPA
- AWS-TERMS

#### depends_on_blocker_ids

None.


### PC-02

| Field | Value |
|---|---|
| id | PC-02 |
| previous_status | RESOLVED |
| status | RESOLVED |
| resolved_evidence | Candidate/service/region, improvement/opt-out, inputs/outputs, roles/subprocessors, human-access limits, deletion and documented limitations bound in 27 current AWS fact records. |
| remaining_issue | None for provider fact-pack blocker; v0.3 accepts exact provider retention limitations under OD-11C-14. Qualified actual-scope approval remains PC-01; configuration/quality verification remains later gates. |
| owner | Provider relationship + Privacy |
| blocks_11_1C | false |
| required_next_evidence | None for this blocker; later-gate verification is separate. |
| deadline | RESOLVED_IN_THIS_DOCS_REASSESSMENT |

#### evidence_ids

- OD-11C-02
- OD-11C-03
- OD-11C-07
- AWS-TERMS
- AWS-REGION
- AWS-ORG
- AWS-FAQ
- AWS-DPA
- AWS-SUBPROCESSORS

#### depends_on_blocker_ids

None.


### RT-01

| Field | Value |
|---|---|
| id | RT-01 |
| previous_status | PARTIALLY_RESOLVED |
| status | RESOLVED |
| resolved_evidence | 56/56 existing cells classified; DORA 24h/30d/90d/active+90d periods, explicit transcript/audio success cleanup and early terminal job deletion applied. Frozen criterion 2 permits approved periods/triggers. OD-11C-14 accepts strongest documented internal-provider purpose/control/limitation with no invented purge TTL. |
| remaining_issue | None under frozen retention criteria; accepted provider limitations are disclosed, not physical erasure evidence. |
| owner | Project Owner + Privacy + Backend/Storage + AWS relationship owner |
| blocks_11_1C | false |
| required_next_evidence | None for bounded retention design closure; runtime execution/verification remains later gates. |
| deadline | RESOLVED_IN_THIS_DOCS_REASSESSMENT |

#### evidence_ids

- OD-11C-13
- OD-11C-14
- OD-11C-15
- OD-11C-16
- OD-11C-17
- OD-11C-18
- OD-11C-19
- OD-11C-20
- OD-11C-21
- OD-11C-22
- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-05
- RET-AWS-06
- RET-AWS-07
- RET-AWS-08
- RET-AWS-09
- RET-AWS-10
- RET-AWS-11
- RET-AWS-12
- RET-AWS-13
- RET-AWS-14
- RET-AWS-15

#### depends_on_blocker_ids

None.


### RT-02

| Field | Value |
|---|---|
| id | RT-02 |
| previous_status | PARTIALLY_RESOLVED |
| status | RESOLVED |
| resolved_evidence | Every holder/artifact has explicit backup/replica inclusion, horizon, deletion/receipt, restore and retirement rules. No audio backup; DORA non-audio restorable horizon<=30d<120d tombstone. Guarded expiry and callback non-creation cover unresolved provider exposure without claiming physical purge. |
| remaining_issue | None under frozen retention criteria; accepted provider limitations are disclosed, not physical erasure evidence. |
| owner | Project Owner + Privacy + Backend/Storage |
| blocks_11_1C | false |
| required_next_evidence | None for bounded retention design closure; runtime execution/verification remains later gates. |
| deadline | RESOLVED_IN_THIS_DOCS_REASSESSMENT |

#### evidence_ids

- OD-11C-13
- OD-11C-14
- OD-11C-15
- OD-11C-16
- OD-11C-17
- OD-11C-18
- OD-11C-19
- OD-11C-20
- OD-11C-21
- OD-11C-22
- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-05
- RET-AWS-06
- RET-AWS-07
- RET-AWS-08
- RET-AWS-09
- RET-AWS-10
- RET-AWS-11
- RET-AWS-12
- RET-AWS-13
- RET-AWS-14
- RET-AWS-15

#### depends_on_blocker_ids

None.


### SC-01

| Field | Value |
|---|---|
| id | SC-01 |
| previous_status | RESOLVED |
| status | RESOLVED |
| resolved_evidence | OD-11C-12 accepts the bounded design; internal technical review and ADR-0011 define identity, KMS custody, decrypt principals, authorization/revocation and ledgers with RDY controls/risks. |
| remaining_issue | None for accepted architecture; ADR-0011 and OD-11C-12 preserved. SC-02 now depends only on PC-01 / PRIVACY; runtime verification remains later gates. |
| owner | Project Owner acceptance + Codex internal technical reviewer |
| blocks_11_1C | false |
| required_next_evidence | None for this blocker; later-gate verification is separate. |
| deadline | RESOLVED_IN_THIS_DOCS_REASSESSMENT |

#### evidence_ids

- OD-11C-12
- AWS-ENCRYPT
- AWS-IAM
- AWS-TLS
- AWS-KMS-AUDIT

#### depends_on_blocker_ids

None.


### SC-02

| Field | Value |
|---|---|
| id | SC-02 |
| previous_status | BLOCKED_BY_FROZEN_CRITERION |
| status | BLOCKED_BY_PRIVACY_ONLY |
| resolved_evidence | RETENTION now SATISFIED; RT-01/02 removed from current dependencies. Accepted CONTROL architecture remains unchanged. |
| remaining_issue | Only PC-01 / CLD-ADM-PRIVACY-001 qualified Privacy/Legal actual-scope approval; frozen CONTROL dependency prevents satisfaction. |
| owner | Backend architecture + Privacy + Project Owner |
| blocks_11_1C | true |
| required_next_evidence | PC-01 qualified actual-scope Privacy/Legal approval and resulting PRIVACY dependency readback. |
| deadline | Before affected gate closure / 6.3 |

#### evidence_ids

- OD-11C-12
- OD-11C-13
- OD-11C-14
- OD-11C-20
- OD-11C-21

#### depends_on_blocker_ids

- PC-01


## effective_gate_statuses

| Field | Value |
|---|---|
| CLD-ADM-ARCH-001 | SATISFIED |
| CLD-ADM-CONSENT-001 | SATISFIED |
| CLD-ADM-DATA-001 | SATISFIED |
| CLD-ADM-SCOPE-001 | SATISFIED |
| CLD-ADM-GAPS-001 | SATISFIED |
| CLD-ADM-PRIVACY-001 | BLOCKED |
| CLD-ADM-RETENTION-001 | SATISFIED |
| CLD-ADM-CONTROL-001 | BLOCKED |
| CLD-ADM-EVALUATION-001 | OPEN |
| CLD-ADM-PROVIDER-001 | OPEN |
| CLD-ADM-ADMISSION-001 | BLOCKED |
| CLD-ADM-API-001 | OPEN |
| CLD-ADM-AUTH-001 | NOT_RUN |
| CLD-ADM-CONSENT-RUNTIME-001 | NOT_RUN |
| CLD-ADM-SECRETS-001 | NOT_RUN |
| CLD-ADM-UPLOAD-001 | NOT_RUN |
| CLD-ADM-CRYPTO-001 | NOT_RUN |
| CLD-ADM-RETENTION-RUNTIME-001 | NOT_RUN |
| CLD-ADM-DATA-RUNTIME-001 | NOT_RUN |
| CLD-ADM-FAILURE-001 | NOT_RUN |
| CLD-ADM-QUEUE-001 | NOT_RUN |
| CLD-ADM-BACKGROUND-001 | NOT_RUN |
| CLD-ADM-ADAPTER-001 | NOT_RUN |
| CLD-ADM-COST-001 | NOT_RUN |
| CLD-ADM-OBSERVABILITY-001 | NOT_RUN |
| CLD-ADM-SECURITY-001 | NOT_RUN |
| CLD-ADM-RESULT-001 | NOT_RUN |
| CLD-ADM-MERGE-001 | NOT_RUN |
| CLD-ADM-LOCAL-001 | NOT_RUN |
| CLD-ADM-OFFLINE-001 | NOT_RUN |
| CLD-ADM-DELETE-001 | NOT_RUN |
| CLD-ADM-HARNESS-001 | NOT_RUN |
| CLD-ADM-UX-001 | NOT_RUN |
| CLD-ADM-HISTORY-001 | NOT_RUN |
| CLD-ADM-EXPORT-001 | NOT_RUN |
| CLD-ADM-OPERATIONS-001 | NOT_RUN |
| CLD-ADM-SUPPLY-001 | OPEN |
| CLD-ADM-EXIT-001 | NOT_RUN |
| CLD-ADM-RELEASE-001 | OPEN |


## boundary_statuses

| Field | Value |
|---|---|
| 6.1 | PASS / ALPHA_SCOPE_AND_SUPPORTED_ENVIRONMENT_FROZEN |
| AWS | SELECTED_ALPHA_PLATFORM |
| AWS_TECHNICAL_ADMISSION | NOT_RUN |
| FIRST_REAL_AUDIO_ADMISSION | NOT_READY |
| CLOUD_RUNTIME | NOT_IMPLEMENTED |
| CLOUD_ALPHA_ACCEPTANCE | NOT_RUN |
| CLOUD_IMPLEMENTATION_ADMISSION | NOT_READY |
| CLD-ADM-SCOPE-001 | SATISFIED |
| 6.2 | PASS / ALPHA_READINESS_GAPS_DISPOSITIONED |
| 6.2D | BLOCKED / TECHNICAL_ADMISSION_NOT_RUN |
| 6.3 | BLOCKED |
| 18.1A | PASS / MARKET_AND_PRICING_DATASET_READY |
| 18.1B | PASS / TCO_AND_ALPHA_PROVIDER_ECONOMICS_READY |
| CLD-ADM-GAPS-001 | SATISFIED |
| 11.1C | PARTIAL / RETENTION_SATISFIED_PC01_QUALIFIED_PRIVACY_APPROVAL_REMAINS |


## non_execution

| Field | Value |
|---|---|
| android_runtime | NOT_CHANGED |
| runtime_device_tests | NOT_RUN / NOT_REQUIRED |
| aws_runtime_api_calls | 0 |
| real_cloud_audio | 0 |
| device_campaigns | 0 |
| asr_inference | 0 |
| recovery | NOT_TOUCHED |
| pr86 | NOT_TOUCHED |
| stage_6_2D | NOT_RUN |
| stage_6_3 | NOT_RUN |
| ci | NOT_RUN_IN_THIS_DOCS_TASK |


## closure

| Field | Value |
|---|---|
| post_publication_receipt | Final task report and existing Google Sheet identify the containing commit, parent, remote HEAD and readback. This pre-publication package does not certify future external actions. |

### required

- internal_consistency_validation
- one_atomic_docs_only_commit
- push_same_branch
- refetch_exact_remote_head
- existing_google_sheet_update
- all_changed_cells_api_readback


## candidate_profile

| Field | Value |
|---|---|
| platform | AWS |
| service | Amazon Transcribe |
| mode | STANDARD_BATCH_FILE_ASR |
| region | eu-central-1 |
| region_policy | ONE_REGION_NO_FALLBACK_NO_MIGRATION_NO_MULTI_REGION_CONTENT_PROCESSING |
| english_locale_quality_admission | DEFERRED_TO_6.2D |
| streaming | DOCUMENTED_AVAILABLE_NOT_SELECTED_FOR_FIRST_ALPHA |
| input | DORA-controlled private S3, same account and eu-central-1 |
| output | Explicit DORA-controlled private S3 transient bucket; separate durable DORA transcript store |
| key_custody | Customer-managed single-region symmetric KMS keys for DORA S3 input/output; provider internal EBS uses provider default key |
| administrative_data_boundary | No recording audio/transcript in IAM/Organizations; no assertion all AWS administration/billing/support metadata is regional. PC-01 qualified scope review covers actual legal applicability. |
| additional_runtime_stack | No compute/database/queue product selected here; any additional deployment dependency must independently prove eu-central-1 compatibility before use, with no fallback. |
| provider_admission | NOT_RUN |
| account_configuration_verification | NOT_RUN |
| quality_benchmark | NOT_RUN |

### languages_documented

- ru-RU
- en-US
- en-GB

### excluded_features

- Medical
- HealthScribe
- Call Analytics
- diarization
- PII redaction
- custom language models
- custom vocabulary
- multi-language quality claims

### bound_regional_services

- Amazon Transcribe
- Amazon S3
- AWS KMS
- AWS CloudTrail

### global_administration

- AWS IAM
- AWS Organizations


## opt_out_control

| Field | Value |
|---|---|
| policy_status | POLICY_REQUIRED |
| effective_policy_runtime_verification | NOT_RUN |
| required_effective_service | transcribe |
| required_effective_value | optOut |
| review_inheritance_and_account_binding | true |
| deadline | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD |
| historical_deletion_scope | Improvement copies only; service-function copies are excluded by AWS-ORG. |
| absence_failure | Deny audio; never infer opt-out from a document or platform selection. |

### example_not_deployed

#### services

##### transcribe

###### opt_out_policy

| Field | Value |
|---|---|
| @@assign | optOut |





### later_gates

- CLD-ADM-PROVIDER-001
- CLD-ADM-CONSENT-RUNTIME-001
- CLD-ADM-SECURITY-001


## current_architecture_decision

| Field | Value |
|---|---|
| path | docs/adr/ADR-0011-alpha-transcribe-privacy-retention-key-custody.md |
| sha256 | c366875e12d84f35b602de8ab92ac4c7e588d6908e4f3fec2a122c129b6d92b5 |
| status | APPROVED_BY_PROJECT_OWNER |
| scope | Bounded Stage 0 Alpha architecture; not legal/runtime/provider admission |

### authority_ids

- OD-11C-12


## rdy_risk_dispositions

### RDY-011

| Field | Value |
|---|---|
| id | RDY-011 |
| threat | Unlawful/undisclosed transfer; incorrect legal role or VPN-derived region. |
| control | Invited audience, single Frankfurt region, scope-bound consent, candidate fact pack, opt-out prerequisite and truthful RU/EN disclosure. |
| owner | Product + qualified Privacy/Legal |
| verification_point | PC-01 qualified actual-scope approval before PRIVACY/6.3; later consent/privacy runtime tests before audio. |
| residual_risk | Only qualified privacy/legal responsibility/applicability/disclosure approval remains; audience, candidate and region are no longer undecided. |
| blocks_before_6_3 | true |
| disposition | OWNER_SCOPE_RESOLVED_QUALIFIED_APPROVAL_RESIDUAL |
| design_review | Codex internal technical design review; accepted architecture OD-11C-12; no independent certification. |

#### blocking_decision_ids

- PC-01


### RDY-012

| Field | Value |
|---|---|
| id | RDY-012 |
| threat | One global consent flag or stale queue authorizes unrelated artifacts, provider/region changes, replay or a cross-owner action. |
| control | Owner/source-bound append-only consent ledger, orthogonal grant validity, current epoch at upload/process, exact batch snapshot and single ASK prompt history. |
| owner | Backend architecture + Security + Android contract owner |
| verification_point | Accepted reviewed design here; AUTH/CONSENT-RUNTIME/UPLOAD adversarial identity/replay/revocation cases before audio. |
| residual_risk | Compromised client and offline server-notification delay remain; finite parameter/race verification is later runtime evidence, not a missing external design signer. |
| blocks_before_6_3 | false |
| disposition | BOUNDED_DESIGN_ACCEPTED_RUNTIME_NOT_RUN |
| design_review | Codex internal technical design review; accepted architecture OD-11C-12; no independent certification. |

#### blocking_decision_ids

None.


### RDY-013

| Field | Value |
|---|---|
| id | RDY-013 |
| threat | Worker/operator plaintext exposure, overly broad decrypt privilege, false E2EE/erasure promises. |
| control | ADR-0011: TLS, customer KMS/S3 envelope boundary, scoped Transcribe role and DORA ingester, provider internal-key/plaintext disclosure, audit and role separation. |
| owner | Security + Backend/key custodian |
| verification_point | Owner-approved design here; later CRYPTO/DELETE/RETENTION-RUNTIME/SECURITY configuration, race, restore and callback verification before audio. |
| residual_risk | Provider physical purge timing and non-terminal cancellation are not guaranteed; accepted disclosed limits under OD-11C-14. DORA explicit deadlines, 30-day horizon, guarded 120-day tombstones and failure visibility require later runtime proof. |
| blocks_before_6_3 | false |
| disposition | BOUNDED_DESIGN_ACCEPTED_RETENTION_RESOLVED_RUNTIME_NOT_RUN |
| design_review | Codex internal technical design review; accepted architecture OD-11C-12; no independent certification. |

#### blocking_decision_ids

None.


### RDY-018

| Field | Value |
|---|---|
| id | RDY-018 |
| threat | Local delete loses remote pending operation or a late callback/restore resurrects content. |
| control | Durable independent deletion ledger/outbox, exact local/remote scopes, per-holder receipts, deletion epoch fences and restore-before-serve reconciliation. |
| owner | Storage + Backend + Privacy |
| verification_point | Owner-approved design here; later CRYPTO/DELETE/RETENTION-RUNTIME/SECURITY configuration, race, restore and callback verification before audio. |
| residual_risk | Provider physical purge timing and non-terminal cancellation are not guaranteed; accepted disclosed limits under OD-11C-14. DORA explicit deadlines, 30-day horizon, guarded 120-day tombstones and failure visibility require later runtime proof. |
| blocks_before_6_3 | false |
| disposition | BOUNDED_DESIGN_ACCEPTED_RETENTION_RESOLVED_RUNTIME_NOT_RUN |
| design_review | Codex internal technical design review; accepted architecture OD-11C-12; no independent certification. |

#### blocking_decision_ids

None.


## schema_version

1.2

## contract_version

0.3

## package_id

cloud-alpha-privacy-retention-control-v0.3

## assessment_date

2026-09-28

## baseline_commit

99976ce21a5ee2fde408e1bee78b12902a675b81

## task

11.1C_BOUNDED_RETENTION_CLOSURE

## result

PARTIAL / RETENTION_SATISFIED_PC01_QUALIFIED_PRIVACY_APPROVAL_REMAINS

## design_status

OWNER_APPROVED_RETENTION_POLICY_AND_ACCEPTED_PROVIDER_LIMITATIONS_RUNTIME_NOT_RUN

## approval_status

PROJECT_OWNER_APPROVAL_AVAILABLE_QUALIFIED_PRIVACY_LEGAL_APPROVAL_NOT_AVAILABLE

## version_provenance

| Field | Value |
|---|---|
| previous_version | 0.2 |
| previous_commit | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| previous_contract | docs/contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.json |
| previous_contract_sha256 | cf9bf1a1e43725313a2f74e283f5a058b376c4b9f9032f441effa55ce68de8e5 |
| previous_human_document | docs/design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.md |
| previous_human_document_sha256 | c3d17a9602731f6639fcab5831529ee64bd0dc81b75e6231df4468247814e025 |
| relationship | v0.3 is the sole current successor retention/dependency package. v0.1 and v0.2 remain immutable historical evidence. Unchanged accepted architecture is inherited, never a competing matrix. |


## inherited_authority

| Field | Value |
|---|---|
| source | PRIOR_V02_JSON / ADR11, hash-bound at baseline |
| precedence | OD-11C-13..22 and this v0.3 matrix replace only retention/residual sentences in inherited sections, disclosures and ADR-0011. Identity, encryption/key custody, ingress, region, local behavior and owners are not redesigned. Old RT blockers in historical text are not current. |
| local_contract | DEC-013 local original until explicit delete; automatic retention OFF; allowed numeric catalog remains empty/unapproved. DEC-017 local export temp safe release / warned Delete now / hard one-hour maximum / startup recovery remains separate. No local edit/history upload is newly authorized. |
| scope | Only docs/research/governance. AWS/Android implementation, evaluation, provider quality, 6.2D and 6.3 remain unexecuted. |

### unchanged_sections

- owner_authority decisions OD-11C-01..12 except explicit supersession below
- candidate_profile
- opt_out_control
- data_flows
- privacy_rules
- control_design architecture and four authorization checks


## owner_authority

| Field | Value |
|---|---|
| source_kind | PROJECT_OWNER_USER_INSTRUCTION |
| request_sha256 | c8bae1ec2ec6c0fca04eea10c3251dc36db2a58a580160138da4c78331d7849d |
| verbatim_line_endings | LF canonicalization only; request hash binds original bytes |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| human_name_not_invented | true |

### decisions

### OD-11C-13

| Field | Value |
|---|---|
| id | OD-11C-13 |
| title | DORA-CONTROLLED CLOUD AUDIO DEADLINE |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | 8212f63c0012dd21bf2ad187c9d20b82ad7e2765e9c01c86b8fddde1244dd671 |
| applied_as | All DORA-controlled Cloud audio: explicit post-success deletion after usable terminal result, durable ingestion and integrity checks; hard maximum 24 hours for every outcome, inherited original clock, no retry reset. Lifecycle is fallback only. |

## OD-11C-13 — DORA-CONTROLLED CLOUD AUDIO DEADLINE

The previous 24-hour Alpha ceiling is clarified.

The **hard ≤24-hour requirement applies to all DORA-controlled Cloud audio objects**, including:

- DORA-controlled S3 input audio;
- retry/requeue copies controlled by DORA;
- failed/cancelled/abandoned DORA-controlled audio objects;
- any other customer-controlled temporary audio object.

Rules:

### Successful terminal processing

After:

1. Amazon Transcribe reaches a usable terminal result;
2. required transcript result is durably ingested into DORA;
3. required integrity/state checks succeed;

DORA must explicitly delete the transient input audio as soon as technically safe.

Do not wait for the 24-hour ceiling.

### Failed / cancelled / abandoned processing

DORA-controlled transient audio:

`HARD MAXIMUM = 24 HOURS`

from the applicable upload/create/start timestamp defined in the implementation contract.

No DORA-controlled audio may remain beyond that ceiling merely for debugging, retry convenience or later analysis.

### Deletion mechanism

Primary mechanism:

`explicit DeleteObject / explicit application deletion`

S3 Lifecycle is a safety fallback only.

Lifecycle asynchronous execution must not be represented as a hard deletion receipt.


### OD-11C-14

| Field | Value |
|---|---|
| id | OD-11C-14 |
| title | AWS INTERNAL SERVICE-FUNCTION COPIES |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | f22290be8691c42eee6b614e93cb367ac09338261209d24584d4cdee7684293d |
| applied_as | Accept and disclose strongest documented provider purpose/deletion controls and undocumented internal purge timing; never imply customer-controlled physical 24-hour erasure of hidden AWS copies. Mandatory opt-out and shortest available configuration remain. |

## OD-11C-14 — AWS INTERNAL SERVICE-FUNCTION COPIES

The Project Owner explicitly clarifies that the DORA 24-hour customer-controlled deadline **does NOT assert an unsupported physical ≤24-hour guarantee over undocumented AWS-internal service-function copies** that are not under DORA object control.

For such provider-controlled copies:

- DORA must use the shortest/most restrictive available provider configuration;
- AI service-improvement opt-out remains mandatory;
- no training/service-improvement use is authorized;
- DORA must document the actual AWS contractual/documented retention or deletion trigger;
- DORA must disclose any provider-controlled limitation honestly;
- DORA must not claim an erasure SLA that AWS does not publish or contractually guarantee.

If authoritative AWS documentation provides a trigger such as retention only as necessary to provide/support the service, bind that exact provider trigger/limitation.

Do not invent a numeric TTL.

This clarification supersedes only any earlier interpretation that **all hidden AWS-internal service-function copies** must have a customer-enforceable 24-hour physical deletion guarantee.

It does NOT weaken the 24-hour limit for DORA-controlled S3/audio objects.


### OD-11C-15

| Field | Value |
|---|---|
| id | OD-11C-15 |
| title | TRANSIENT TRANSCRIPT OUTPUT |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | 769ca88c244f5559fd420b297de5bc87c7084d55b711ce36574823ad66313e56 |
| applied_as | DORA transient transcript: delete immediately after safe durable ingestion; all unaccepted/failed output <=24 hours from creation. Durable accepted transcript remains a separate until-explicit-delete object. |

## OD-11C-15 — TRANSIENT TRANSCRIPT OUTPUT

Amazon Transcribe output used during processing is transient.

DORA durable transcript storage is a separate application object.

For DORA-controlled transient transcription output:

### Accepted result

After durable ingestion into the DORA transcript/versioning store:

delete the transient output as soon as technically safe.

### Failed / rejected / unaccepted output

Maximum retention:

`24 HOURS`

No failed/unaccepted transcript output is retained indefinitely.

Do not use Transcribe service-managed output as the durable application transcript store.


### OD-11C-16

| Field | Value |
|---|---|
| id | OD-11C-16 |
| title | DORA OPERATIONAL METADATA |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | bd95b3823e8a3f227c7ecee97d1a787b55aecf51e3c6e152c6e746569dc83486 |
| applied_as | Ordinary DORA operations metadata <=30 days; job orchestration clock is terminal completion, other logs/errors clock is event creation. Shorter sufficient periods prevail; no content logs. |

## OD-11C-16 — DORA OPERATIONAL METADATA

The following ordinary DORA operational metadata has an approved retention maximum:

`30 DAYS`

unless a shorter period is sufficient.

Examples:

- Cloud job orchestration state after terminal completion;
- non-security operational logs;
- retry diagnostics;
- bounded technical error records;
- non-content processing metadata.

Raw audio must never be embedded in these records.

Transcript/user-content logging is prohibited except where a separately approved product object requires it.


### OD-11C-17

| Field | Value |
|---|---|
| id | OD-11C-17 |
| title | SECURITY / AUDIT RECORDS |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | 4dfdae0bc94c580a0bb148d7497e5d266a850ea026ecbddacaaec03367fc9d44 |
| applied_as | DORA security/audit history <=90 days from event creation; an active enforcement record is separated from audit history. No audio or reusable secrets in audit. |

## OD-11C-17 — SECURITY / AUDIT RECORDS

Security and authorization audit records have an approved retention maximum:

`90 DAYS`

unless existing authority requires longer for an active object.

This category includes bounded technical records necessary to establish:

- authentication/authorization events;
- credential issuance/revocation;
- access-control decisions;
- security-relevant administrative actions;
- consent-processing audit events.

Do not store raw audio in security/audit logs.

Do not store reusable credentials/secrets in logs.

Where a record is required to enforce an active authorization rather than merely audit it, the active authoritative state may exist while the authorization is active; the **audit history** is 90 days.


### OD-11C-18

| Field | Value |
|---|---|
| id | OD-11C-18 |
| title | CONSENT RECORD LIFECYCLE |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | de95bb3c79f2c48ed03e732f3e9862890d98977852c0db956dcfbbd4394fa756 |
| applied_as | The authoritative consent record is retained while ANY related Cloud-derived artifact OR processing authority is active; a revoked grant never remains valid merely because artifacts exist. Only after ALL are finally deleted/terminated does the 90-day historical clock start. Revocation is separately recorded and does not delete artifacts. |

## OD-11C-18 — CONSENT RECORD LIFECYCLE

Consent is not treated as an ordinary short-lived operational log.

For a recording/cloud-processing authorization:

the authoritative consent record must exist while any corresponding Cloud-derived DORA artifact or processing authority remains active.

After the relevant recording/artifacts/authorization are finally deleted or terminated:

retain the historical consent/audit record for:

`90 DAYS`

Then it may expire unless another approved legal/security requirement applies.

Consent revocation:

- stops future unauthorized processing;
- does not itself delete existing artifacts;
- is separately recorded.


### OD-11C-19

| Field | Value |
|---|---|
| id | OD-11C-19 |
| title | INSTALLATION / CREDENTIAL RECORDS |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | 4cb42e3f417319ca11a0ab0463da1244cdb9fcd71501c719f98e412af42d625c |
| applied_as | Installation/credential authority lasts while active; after final revocation/removal retain history for 90 days, then expire absent another approved requirement. Destroy secrets when their authority ends; history cannot preserve reusable key material. |

## OD-11C-19 — INSTALLATION / CREDENTIAL RECORDS

Active installation identity and credential-control state exists for the lifetime of that active installation/credential.

After final revocation/removal:

retain security/audit history for:

`90 DAYS`.

Secrets/private key material must not be retained merely because audit metadata remains.


### OD-11C-20

| Field | Value |
|---|---|
| id | OD-11C-20 |
| title | NON-AUDIO BACKUP HORIZON |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | b0ab945b22c3773798fa9d97413e294e8f572f5fd2574a2a6c80a4a36372ef26 |
| applied_as | No DORA Cloud audio backup/archive/cross-region replica. Approved durable non-audio backups have maximum restorable age 30 days, never extending source expiry; restore reconciles deletion first. |

## OD-11C-20 — NON-AUDIO BACKUP HORIZON

For first Alpha:

### Audio

Long-term backups of Cloud audio are prohibited.

No:

- archive tier;
- cross-region replica;
- long-term backup;
- recovery copy intended to resurrect deleted Cloud audio.

### Durable non-audio DORA application data

Where backup is required for durable non-audio application data:

maximum restorable backup horizon:

`30 DAYS`

unless an existing stricter contract requires less.

This may apply to durable transcripts/version metadata or other approved non-audio product objects.

The restore process must perform deletion/tombstone reconciliation before restored content becomes visible or processable.


### OD-11C-21

| Field | Value |
|---|---|
| id | OD-11C-21 |
| title | DELETION TOMBSTONE / RESTORE SUPPRESSION |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | 96262a9bd85c8cbae8f56eab247b6c1015942473de7c34663f992c7f0001ee17 |
| applied_as | Tombstone is retained 120 days after effective logical deletion and cannot retire while an unresolved copy/callback can recreate the object. Verify all restore horizons and fail closed; any documented relevant horizon >120 days requires exact owner incompatibility review. |

## OD-11C-21 — DELETION TOMBSTONE / RESTORE SUPPRESSION

Deletion/tombstone records are required to prevent resurrection.

Approved retention:

`120 DAYS AFTER EFFECTIVE LOGICAL DELETION`

provided this is not shorter than any actual restorable backup/replica/callback horizon.

Given the approved first-Alpha non-audio backup maximum of 30 days, the 120-day tombstone period deliberately exceeds the restorable backup horizon.

Rules:

- restore must check tombstone/deletion state;
- deleted audio must not be restored;
- deleted whole recordings must not reappear;
- late provider callbacks must be suppressed if the logical object was deleted;
- tombstone expiry must not occur while a restorable copy or unresolved late callback can still recreate the object.

If a documented provider-controlled callback/recovery horizon exceeds 120 days:

do not silently keep 120 days.

Return that exact incompatibility for owner review.


### OD-11C-22

| Field | Value |
|---|---|
| id | OD-11C-22 |
| title | PROVIDER JOB RECORDS |
| status | APPROVED_BY_PROJECT_OWNER |
| assessment_date | 2026-09-28 |
| source_baseline | 99976ce21a5ee2fde408e1bee78b12902a675b81 |
| authority | Explicit Project Owner instruction in this task |
| verbatim_sha256 | c6ae7f81b7a10f96029d1e2e6299e546de23b1cef276a7711474067d059cb22b |
| applied_as | Provider job record: AWS default 90 days, explicit earlier terminal deletion as soon as safe after ingestion/state-integrity/audit. Failed terminal jobs also deleted once reconciled/audited, without requiring a nonexistent transcript. No internal-media erasure proof. |

## OD-11C-22 — PROVIDER JOB RECORDS

Provider-managed Amazon Transcribe job records are distinct from DORA audio and DORA durable transcript storage.

Use authoritative AWS documentation to establish:

- default maximum/provider period;
- whether a completed terminal job can be deleted earlier;
- what `DeleteTranscriptionJob` actually deletes/controls;
- what it does NOT prove regarding AWS-internal service-function copies.

DORA policy:

delete terminal Transcribe job records as soon as operationally safe after:

- durable transcript ingestion;
- required state/integrity confirmation;
- required DORA audit entry.

Do not retain a completed provider job merely because AWS allows a longer default period.

Any AWS default such as 90 days is a provider ceiling/default, not DORA's desired minimum.



## supersession

### OD-11C-04

| Field | Value |
|---|---|
| id | OD-11C-04 |
| remains | Local original until explicit deletion; immediate safe post-success Cloud cleanup and hard 24-hour customer-controlled audio ceiling. |
| superseded | Only the interpretation in v0.2 LC-01-3 / LC-03-3 / audio_deadline_design.provider_limit that all hidden AWS service-function copies must have a customer-enforceable <=24h physical purge guarantee. |
| replacement | OD-11C-13 fixes DORA-controlled scope; OD-11C-14 accepts documented internal-provider limits without a fabricated TTL. |
| consistency | Different control domains are explicit; no DORA-controlled audio deadline is relaxed. |


### OD-11C-08

| Field | Value |
|---|---|
| id | OD-11C-08 |
| remains | Truthful provider maximum/default and receipt limits; provider job record differs from audio and durable DORA text. |
| superseded | Any reading that 90-day Transcribe default is a required minimum or that unidentified log/record periods remain unapproved after OD-11C-16..19/22. |
| replacement | 90-day provider job default with earlier terminal deletion; DORA metadata 30 days, audit 90 days, active consent/identity plus 90 days. |
| consistency | Earlier customer controls prevail; immutable CloudTrail event history is independently 90 days and is not content retention. |


### OD-11C-10

| Field | Value |
|---|---|
| id | OD-11C-10 |
| remains | No long-term/archival/cross-region audio copy, no resurrection, scoped receipts and truthful internal-replica limits. |
| superseded | v0.2 statement that non-audio copy horizon and tombstone retirement are specifically unapproved; any implied physical purge guarantee over hidden replicas. |
| replacement | OD-11C-20 approves <=30-day restorable non-audio backups; OD-11C-21 approves 120-day guarded tombstones; OD-11C-14 governs hidden provider copies. |
| consistency | Adds periods and retirement guards without creating audio backup or suppressing provider limitations. |


## aws_fact_package

### RET-AWS-01

| Field | Value |
|---|---|
| id | RET-AWS-01 |
| url | https://aws.amazon.com/service-terms/ |
| retrieval_date | 2026-09-28 |
| document_section | AWS Service Terms, updated 2026-09-11, §§1.14-1.15 and 50.3-50.4 |
| factual_statement | Transcribe AI Content may be used for service improvement under the default terms; Organizations opt-out is available. DPA is incorporated. Customer notice/consent duties remain. |
| consequence_for_dora | Mandatory effective Transcribe opt-out before first audio; no training/improvement authorization. |
| limitation | No numeric service-function physical-erasure SLA; public terms are not proof of executed actual-scope legal approval. |


### RET-AWS-02

| Field | Value |
|---|---|
| id | RET-AWS-02 |
| url | https://aws.amazon.com/transcribe/faqs/ |
| retrieval_date | 2026-09-28 |
| document_section | Data privacy: voice-input storage/use, deletion, access and regional questions |
| factual_statement | FAQ permits voice-input storage/use to provide and maintain the service and for improvement; customers can opt out of improvement. Available Delete APIs remove job-associated data/artifacts; contact Support for deletion problems. Authorized employees may access content. |
| consequence_for_dora | Bind service-purpose limitation plus applicable service-control deletion requests, with improvement prohibited and operational access disclosed. |
| limitation | FAQ purpose wording is NOT an explicit temporal promise to delete immediately when unnecessary, nor a numeric internal purge deadline. It does not certify every hidden copy erased by a job API. |


### RET-AWS-03

| Field | Value |
|---|---|
| id | RET-AWS-03 |
| url | https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_ai-opt-out.html |
| retrieval_date | 2026-09-28 |
| document_section | AI services opt-out policies: historical content deletion and service-function exception |
| factual_statement | Opt-out removes associated historical improvement content, limited to content not needed for service functions. Service-function content is excepted. |
| consequence_for_dora | Use effective account Transcribe optOut, never equate opt-out with erasure of required service copies. |
| limitation | No fixed physical purge time for the excepted internal copies. Account effective policy verification remains NOT_RUN. |


### RET-AWS-04

| Field | Value |
|---|---|
| id | RET-AWS-04 |
| url | https://d1.awsstatic.com/legal/aws-dpa/aws-dpa.pdf |
| retrieval_date | 2026-09-28 |
| document_section | AWS GDPR DPA §14, Return or Deletion of Customer Data (page 5); §12 data location |
| factual_statement | Before termination and during 90 days after termination, AWS returns/deletes Customer Data when the customer uses Service Controls to request it, subject to the Agreement. Region commitments have defined exceptions. |
| consequence_for_dora | Document customer deletion request control; distinguish customer request window from artifact retention. |
| limitation | The 90-day post-termination window is NOT a 90-day physical purge SLA and does not approve retention of DORA audio. DPA applicability/sign-off remains PC-01. |


### RET-AWS-05

| Field | Value |
|---|---|
| id | RET-AWS-05 |
| url | https://docs.aws.amazon.com/transcribe/latest/APIReference/API_DeleteTranscriptionJob.html |
| retrieval_date | 2026-09-28 |
| document_section | DeleteTranscriptionJob: description, response and BadRequestException |
| factual_statement | Deletes the named transcription job; success returns HTTP 200 and empty body. BadRequest covers nonexistent or non-terminal jobs, for example IN_PROGRESS. |
| consequence_for_dora | Explicitly delete terminal records once ingestion or failed-job reconciliation, integrity and audit are complete. Non-terminal jobs need later reconciliation. |
| limitation | Not a hard cancel; not proof of customer S3 deletion or every internal service-function/media copy purge. |


### RET-AWS-06

| Field | Value |
|---|---|
| id | RET-AWS-06 |
| url | https://docs.aws.amazon.com/general/latest/gr/transcribe.html |
| retrieval_date | 2026-09-28 |
| document_section | Amazon Transcribe quotas: number of days job records retained |
| factual_statement | Job records are retained 90 days per supported Region; quota is not adjustable. |
| consequence_for_dora | PROVIDER_DOCUMENTED_DEFAULT_WITH_EARLY_DELETE: 90-day default with DeleteTranscriptionJob earlier after terminal reconciliation. |
| limitation | Not a minimum; not an audio TTL; not a callback lifetime or physical-erasure certificate. |


### RET-AWS-07

| Field | Value |
|---|---|
| id | RET-AWS-07 |
| url | https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObject.html |
| retrieval_date | 2026-09-28 |
| document_section | DeleteObject: versioning and versionId behavior |
| factual_statement | Nonversioned deletion removes the object; versioned deletion without versionId adds a delete marker. Suspended versioning may leave older versions. |
| consequence_for_dora | Use explicit object deletion, and verify no preserved audio versions; where versions exist remove each intended version explicitly. |
| limitation | A marker is not erasure of old versions; API response does not prove physical-media overwrite/internal replica purge timing. |


### RET-AWS-08

| Field | Value |
|---|---|
| id | RET-AWS-08 |
| url | https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-expire-general-considerations.html |
| retrieval_date | 2026-09-28 |
| document_section | Expiring objects: asynchronous removal, noncurrent versions, lock and replication |
| factual_statement | Lifecycle queues removal asynchronously; expiration can be delayed. Noncurrent versions need their own action; Object Lock and pending/failed replication can inhibit relevant actions. |
| consequence_for_dora | Lifecycle safety fallback only; explicit deletion and reconciliation must complete within the DORA deadline. Do not configure lock/replication that prevents it. |
| limitation | Eligibility or billing expiry is not a hard deletion receipt or a <=24h cleanup guarantee. |


### RET-AWS-09

| Field | Value |
|---|---|
| id | RET-AWS-09 |
| url | https://docs.aws.amazon.com/transcribe/latest/dg/how-input.html |
| retrieval_date | 2026-09-28 |
| document_section | Data input and output: transcript output storage and temporary URI |
| factual_statement | Customer output S3 remains until customer removal. Default service-managed output has a temporary 15-minute URI renewable by GetTranscriptionJob; output is removed when its job expires after 90 days. |
| consequence_for_dora | Select explicit DORA customer output bucket; delete transient output after durable ingestion, cap failed/unaccepted output at 24h. |
| limitation | URI expiry is not object deletion. Service-managed output is not selected and must not become durable DORA transcript storage. |


### RET-AWS-10

| Field | Value |
|---|---|
| id | RET-AWS-10 |
| url | https://docs.aws.amazon.com/awscloudtrail/latest/userguide/view-cloudtrail-events.html |
| retrieval_date | 2026-09-28 |
| document_section | Viewing CloudTrail event history |
| factual_statement | Default regional event history is immutable trailing 90 days of management events, not data events. Trail changes do not change event-history retention. |
| consequence_for_dora | Separate provider management-history metadata from customer audio/text and DORA tombstones. DORA audit records have their own approved 90-day policy. |
| limitation | Event history alone does not prove all object data deletes; no content should be placed in resource names or logs; other trail/Lake stores are not silently selected. |


### RET-AWS-11

| Field | Value |
|---|---|
| id | RET-AWS-11 |
| url | https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html |
| retrieval_date | 2026-09-28 |
| document_section | AbortMultipartUpload: in-flight parts and ListParts verification |
| factual_statement | In-flight parts may finish after abort; abort may need repetition. ListParts verifies no parts remain. |
| consequence_for_dora | Fence writes, abort and reconcile parts repeatedly before claiming customer upload removal. All parts inherit original audio deadline. |
| limitation | One successful abort response does not prove all concurrent parts removed. |


### RET-AWS-12

| Field | Value |
|---|---|
| id | RET-AWS-12 |
| url | https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html |
| retrieval_date | 2026-09-28 |
| document_section | S3 data consistency model and storage architecture |
| factual_statement | S3 replicates across servers for availability; data stays regional unless transfer/replication is configured. Successful deletes are strongly consistent for subsequent object reads/listing. |
| consequence_for_dora | Customer logical-namespace absence is scoped evidence; prohibit DORA cross-region audio replication and reconcile concurrent writes. |
| limitation | No public per-copy physical purge latency; consistency is not physical-media erasure or proof of no hidden internal replica. |


### RET-AWS-13

| Field | Value |
|---|---|
| id | RET-AWS-13 |
| url | https://aws.amazon.com/privacy/ |
| retrieval_date | 2026-09-28 |
| document_section | AWS Privacy Notice, updated 2026-05-18: Scope and Retention of Personal Information |
| factual_statement | Notice excludes customer content stored/processed in AWS accounts. For covered AWS Offerings personal information, retention depends on continued use, relevant purposes and legal/tax/accounting obligations. |
| consequence_for_dora | For AWS own non-content account/usage/operational personal information, disclose purpose/obligation-based provider period, separate from Customer Content and DORA-controlled logs. |
| limitation | Do not use this notice as a customer-audio retention promise. It publishes no universal numeric TTL for every internal operational log. |


### RET-AWS-14

| Field | Value |
|---|---|
| id | RET-AWS-14 |
| url | https://docs.aws.amazon.com/transcribe/latest/dg/monitoring-events.html |
| retrieval_date | 2026-09-28 |
| document_section | Monitoring Amazon Transcribe with EventBridge |
| factual_statement | Transcribe provides best-effort job state-change events including COMPLETED/FAILED, job name and status. |
| consequence_for_dora | Callbacks reconcile existing authorized job/object state; missing/retired binding never creates a recording or new processing authority. |
| limitation | Best effort gives no end-to-end maximum age for all callbacks. This task does not select or deploy an EventBridge stack. |


### RET-AWS-15

| Field | Value |
|---|---|
| id | RET-AWS-15 |
| url | https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-rule-retry-policy.html |
| retrieval_date | 2026-09-28 |
| document_section | How EventBridge retries delivering events |
| factual_statement | Default target retries last 24 hours and up to 185 attempts; exhausted events are dropped; an optional DLQ can retain failed events. |
| consequence_for_dora | This documented default is below 120 days but is not used as a universal bound. Any later queue/archive/replay path must prove its actual horizon and obey tombstones. |
| limitation | DLQ/archive/customer replay could change exposure; no such resource is configured or silently approved here. No documented relevant >120-day horizon is established for the selected batch design. |


## provider_limitation_acceptance

| Field | Value |
|---|---|
| authority | APPROVED_BY_PROJECT_OWNER / OD-11C-14; actual strongest published rule/limitation, not a future TTL decision |
| customer_content | RET-AWS-02 service-purpose-limited storage plus RET-AWS-04 Service Controls deletion request framework; apply RET-AWS-05/07/11 at their actual scopes. Numeric internal physical purge time is not published in reviewed sources. |
| improvement | RET-AWS-01/03: use prohibited, mandatory effective opt-out; deletion exception for service-function copies explicitly retained. |
| provider_own_non_content_records | RET-AWS-13 applies only within Privacy Notice scope; service metadata outside that scope has no universal published TTL. Accept the documented source gap as provider-controlled limitation, not a fabricated DORA 30/90-day guarantee. |
| retention_criterion_reading | Frozen criterion 2 says approved actual periods/triggers, not a numeric TTL for every hidden AWS medium. Owner explicitly permits the strongest documented trigger/limitation. Therefore accepted documented purpose/control/unknown purge timing satisfies this bounded design criterion; it does not certify runtime physical deletion. |
| remaining_retention_blocker | NOT_AVAILABLE |

### known_gaps_accepted

- Exact per-copy physical purge latency for Transcribe internal service-function audio/cache/result remnants and S3 internal replicas is unpublished in reviewed sources.
- Universal period for every AWS internal technical log/receipt is unpublished; customer-content versus AWS own-personal-information scope remains explicit.
- Transcribe has no published end-to-end maximum late-callback age in reviewed event documentation; unresolved relevant work prevents tombstone retirement; unknown callbacks cannot recreate state.


## audio_deadline_design

| Field | Value |
|---|---|
| scope | All DORA-controlled Cloud audio, including customer S3, temporary/retry/requeue, failed/cancelled/abandoned and multipart bytes |
| clock_origin | Earliest customer-controlled Cloud byte acceptance or Cloud temporary object creation/start, whichever occurs first; persist before write. All descendants/parts/retries inherit that earliest timestamp; no reset. |
| max_hours | 24 |
| success_trigger | Usable terminal result + durable DORA transcript ingestion + successful required integrity/state checks: explicitly delete as soon as technically safe, never wait for 24 hours. |
| primary_delete | Explicit DeleteObject / explicit application deletion and multipart/version reconciliation; lifecycle fallback only. |
| breach | Schedule cleanup sufficiently before deadline for verified completion. Fence writes/dispatch and independently reconcile. Unconfirmed deadline is a policy incident, blocks new Cloud audio and remains pending; never a grace period. |
| provider_limit | Hidden AWS copies outside DORA object control follow accepted RET-AWS-01..05/12 limitations under OD-11C-14. No unsupported 24-hour physical purge or hard cancellation promise. |
| runtime_status | NOT_RUN |

### authority

- OD-11C-13
- OD-11C-14


## tombstone_proof

| Field | Value |
|---|---|
| dora_max_restorable_days | 30 |
| tombstone_days_after_logical_delete | 120 |
| safety_margin_days | 90 |
| clock | D is effective logical deletion, committed durably with deletion epoch before content becomes inaccessible. Tombstone earliest retirement is D+120d, never 120 days from request, receipt or backup creation. |
| horizon_definition | Every approved non-audio snapshot expires at snapshot creation+30d or earlier source expiry. No snapshot may incorporate logically deleted content after D; pre-delete live replicas stop serving at D and are reconciled or quarantined, with maximum restorable exposure D+30d. Recopy/restore never resets either clock. |
| proof | For a snapshot created at t<=D, expiry<=t+30d<=D+30d<D+120d. DORA-controlled audio backup/restore exposure is zero by prohibition. All restore paths use current deletion state before serve/process; expired snapshots, expired source records and stale live replicas fail closed. |
| pending_at_day_120 | Keep the minimal content-free tombstone while any unsafe work is unresolved, as expressly required by OD-11C-21; record incident/owner escalation. Never blindly expire, claim success, or silently accept a known >120d horizon. |
| callbacks | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| provider_comparison | Job-record default 90d and EventBridge default retry24h are <120d but do NOT bound internal copies or all callbacks. No documented relevant selected-path horizon >120d was established. Optional future replay/backup services require actual horizon inventory; incompatible >120d returns exact fact for owner review. |
| verification | Design arithmetic and fail-closed predicates only. Runtime restore, failure, pending job and callback adversarial tests remain NOT_RUN at DELETE/RETENTION-RUNTIME/SECURITY gates. |

### authority

- OD-11C-20
- OD-11C-21

### retirement_predicate

- Now >= D+120d
- All relevant scoped deletion operations and copy inventories are reconciled; no unresolved recoverable copy can recreate object
- All known relevant jobs/callbacks and replay work are reconciled or irreversibly fenced from object creation
- Authoritative current deletion state, source expiries and installation/consent epochs survive restore; stale/unknown ledger fails closed
- No documented relevant provider-controlled callback/recovery horizon exceeds 120d


## lifecycle_classification_catalog

- NOT_STORED
- UNTIL_EXPLICIT_USER_DELETE
- IMMEDIATE_POST_SUCCESS_DELETE
- MAX_24_HOURS
- MAX_30_DAYS
- MAX_90_DAYS
- ACTIVE_LIFETIME_PLUS_90_DAYS
- 120_DAYS_AFTER_LOGICAL_DELETE
- PROVIDER_DOCUMENTED_TRIGGER
- PROVIDER_DOCUMENTED_DEFAULT_WITH_EARLY_DELETE
- NOT_APPLICABLE

## lifecycle_matrix

### LC-01-1

| Field | Value |
|---|---|
| id | LC-01-1 |
| artifact | original_audio |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Retain verifiable source and permit explicitly authorized ASR/reprocessing. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- UNTIL_EXPLICIT_USER_DELETE

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Canonical local audio |
| lifecycle_state | UNTIL_EXPLICIT_USER_DELETE |
| clock_or_trigger | Explicit scoped user deletion |
| period_and_delete_rule | Local original/transcript/history unchanged; automatic audio retention OFF, numeric catalog unapproved. Audio-only preserves text/edits; whole-recording uses exact local cascade. |
| authority | OD-11C-04/05 + DEC-013 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-01-2

| Field | Value |
|---|---|
| id | LC-01-2 |
| artifact | original_audio |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Retain verifiable source and permit explicitly authorized ASR/reprocessing. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- IMMEDIATE_POST_SUCCESS_DELETE
- MAX_24_HOURS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA Cloud upload alias / usable terminal success |
| lifecycle_state | IMMEDIATE_POST_SUCCESS_DELETE |
| clock_or_trigger | Usable terminal + durable transcript ingestion + integrity/state success |
| period_and_delete_rule | Explicit application/S3 delete as soon as technically safe, bounded by original <=24h clock. |
| authority | OD-11C-13 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-08
- RET-AWS-11


### Item 2

| Field | Value |
|---|---|
| component | DORA Cloud upload alias / all outcomes including failure, cancellation and abandonment |
| lifecycle_state | MAX_24_HOURS |
| clock_or_trigger | Earliest customer-controlled Cloud byte acceptance or Cloud temporary object creation/start, whichever occurs first; persist before write. All descendants/parts/retries inherit that earliest timestamp; no reset. |
| period_and_delete_rule | Hard <=24h; no debugging/requeue/reset exception; lifecycle fallback only. |
| authority | OD-11C-13 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-08
- RET-AWS-11


#### blocking_decision_ids

None.


### LC-01-3

| Field | Value |
|---|---|
| id | LC-01-3 |
| artifact | original_audio |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Retain verifiable source and permit explicitly authorized ASR/reprocessing. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Transcribe internal service-function audio |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-01-4

| Field | Value |
|---|---|
| id | LC-01-4 |
| artifact | original_audio |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Retain verifiable source and permit explicitly authorized ASR/reprocessing. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA audio backup/archive/extra replica |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | Hidden provider audio replicas |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-02-1

| Field | Value |
|---|---|
| id | LC-02-1 |
| artifact | upload_objects |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- UNTIL_EXPLICIT_USER_DELETE

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Local pending source alias; no extra upload copy |
| lifecycle_state | UNTIL_EXPLICIT_USER_DELETE |
| clock_or_trigger | Explicit scoped user deletion |
| period_and_delete_rule | Local original/transcript/history unchanged; automatic audio retention OFF, numeric catalog unapproved. Audio-only preserves text/edits; whole-recording uses exact local cascade. |
| authority | OD-11C-04/05 + DEC-013 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-02-2

| Field | Value |
|---|---|
| id | LC-02-2 |
| artifact | upload_objects |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- IMMEDIATE_POST_SUCCESS_DELETE
- MAX_24_HOURS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA temporary upload / usable terminal success |
| lifecycle_state | IMMEDIATE_POST_SUCCESS_DELETE |
| clock_or_trigger | Usable terminal + durable transcript ingestion + integrity/state success |
| period_and_delete_rule | Explicit application/S3 delete as soon as technically safe, bounded by original <=24h clock. |
| authority | OD-11C-13 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-08
- RET-AWS-11


### Item 2

| Field | Value |
|---|---|
| component | DORA temporary upload / all outcomes including failure, cancellation and abandonment |
| lifecycle_state | MAX_24_HOURS |
| clock_or_trigger | Earliest customer-controlled Cloud byte acceptance or Cloud temporary object creation/start, whichever occurs first; persist before write. All descendants/parts/retries inherit that earliest timestamp; no reset. |
| period_and_delete_rule | Hard <=24h; no debugging/requeue/reset exception; lifecycle fallback only. |
| authority | OD-11C-13 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-08
- RET-AWS-11


#### blocking_decision_ids

None.


### LC-02-3

| Field | Value |
|---|---|
| id | LC-02-3 |
| artifact | upload_objects |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- IMMEDIATE_POST_SUCCESS_DELETE
- MAX_24_HOURS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Customer S3 audio under DORA control / usable terminal success |
| lifecycle_state | IMMEDIATE_POST_SUCCESS_DELETE |
| clock_or_trigger | Usable terminal + durable transcript ingestion + integrity/state success |
| period_and_delete_rule | Explicit application/S3 delete as soon as technically safe, bounded by original <=24h clock. |
| authority | OD-11C-13 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-08
- RET-AWS-11


### Item 2

| Field | Value |
|---|---|
| component | Customer S3 audio under DORA control / all outcomes including failure, cancellation and abandonment |
| lifecycle_state | MAX_24_HOURS |
| clock_or_trigger | Earliest customer-controlled Cloud byte acceptance or Cloud temporary object creation/start, whichever occurs first; persist before write. All descendants/parts/retries inherit that earliest timestamp; no reset. |
| period_and_delete_rule | Hard <=24h; no debugging/requeue/reset exception; lifecycle fallback only. |
| authority | OD-11C-13 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-08
- RET-AWS-11


#### blocking_decision_ids

None.


### LC-02-4

| Field | Value |
|---|---|
| id | LC-02-4 |
| artifact | upload_objects |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA audio backup/version/archive/replica |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | S3 internal copies |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-03-1

| Field | Value |
|---|---|
| id | LC-03-1 |
| artifact | provider_temporary_copies |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Bounded ASR execution only; no service improvement/training or human review. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Provider copies on Android |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-03-2

| Field | Value |
|---|---|
| id | LC-03-2 |
| artifact | provider_temporary_copies |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Bounded ASR execution only; no service improvement/training or human review. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Provider internal copies at DORA |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-03-3

| Field | Value |
|---|---|
| id | LC-03-3 |
| artifact | provider_temporary_copies |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Bounded ASR execution only; no service improvement/training or human review. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | AWS internal temporary content |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-03-4

| Field | Value |
|---|---|
| id | LC-03-4 |
| artifact | provider_temporary_copies |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Bounded ASR execution only; no service improvement/training or human review. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | AWS internal temporary replicas |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-04-1

| Field | Value |
|---|---|
| id | LC-04-1 |
| artifact | provider_jobs_metadata |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Local minimal job mapping |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Terminal completion for job mapping; event creation for ordinary logs/errors |
| period_and_delete_rule | <=30 days, shorter if sufficient; content-free only. Durable source-version association is product transcript metadata, not an excuse to retain operation logs. |
| authority | OD-11C-16 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-04-2

| Field | Value |
|---|---|
| id | LC-04-2 |
| artifact | provider_jobs_metadata |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA orchestration mapping |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Terminal completion for job mapping; event creation for ordinary logs/errors |
| period_and_delete_rule | <=30 days, shorter if sufficient; content-free only. Durable source-version association is product transcript metadata, not an excuse to retain operation logs. |
| authority | OD-11C-16 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-04-3

| Field | Value |
|---|---|
| id | LC-04-3 |
| artifact | provider_jobs_metadata |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- PROVIDER_DOCUMENTED_DEFAULT_WITH_EARLY_DELETE

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Provider job |
| lifecycle_state | PROVIDER_DOCUMENTED_DEFAULT_WITH_EARLY_DELETE |
| clock_or_trigger | AWS 90-day job-record default; earlier explicit terminal reconciliation |
| period_and_delete_rule | Delete as soon as safe after durable ingestion, required state/integrity confirmation and audit entry. Failed terminal: no nonexistent transcript prerequisite, delete after reconciliation/audit. Nonterminal API deletion may fail; keep cleanup pending, never extend DORA audio clock. |
| authority | OD-11C-22 |

###### aws_evidence_ids

- RET-AWS-05
- RET-AWS-06


#### blocking_decision_ids

None.


### LC-04-4

| Field | Value |
|---|---|
| id | LC-04-4 |
| artifact | provider_jobs_metadata |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Minimal non-audio job-state recovery; original terminal + 30d expiry still applies |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | Provider internal job metadata copies |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-05-1

| Field | Value |
|---|---|
| id | LC-05-1 |
| artifact | transcripts |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- UNTIL_EXPLICIT_USER_DELETE

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Accepted local transcript |
| lifecycle_state | UNTIL_EXPLICIT_USER_DELETE |
| clock_or_trigger | Explicit scoped user deletion |
| period_and_delete_rule | Local original/transcript/history unchanged; automatic audio retention OFF, numeric catalog unapproved. Audio-only preserves text/edits; whole-recording uses exact local cascade. |
| authority | OD-11C-04/05 + DEC-013 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-05-2

| Field | Value |
|---|---|
| id | LC-05-2 |
| artifact | transcripts |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- UNTIL_EXPLICIT_USER_DELETE
- IMMEDIATE_POST_SUCCESS_DELETE
- MAX_24_HOURS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Accepted durable DORA transcript/version metadata |
| lifecycle_state | UNTIL_EXPLICIT_USER_DELETE |
| clock_or_trigger | Explicit scoped remote deletion |
| period_and_delete_rule | Durable product object is separate from transient output; audio-only deletion preserves it. |
| authority | OD-11C-05/15 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | DORA ingestion output / accepted |
| lifecycle_state | IMMEDIATE_POST_SUCCESS_DELETE |
| clock_or_trigger | Durable DORA result/version ingestion plus safe integrity check |
| period_and_delete_rule | Explicitly delete transient output as soon as technically safe. |
| authority | OD-11C-15 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-09


### Item 3

| Field | Value |
|---|---|
| component | DORA ingestion output / unaccepted or failed |
| lifecycle_state | MAX_24_HOURS |
| clock_or_trigger | Earliest creation/receipt of this transient output; descendants inherit timestamp |
| period_and_delete_rule | Hard <=24h; late/rejected output never becomes a durable application object. |
| authority | OD-11C-15 |

###### aws_evidence_ids

- RET-AWS-09


#### blocking_decision_ids

None.


### LC-05-3

| Field | Value |
|---|---|
| id | LC-05-3 |
| artifact | transcripts |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- IMMEDIATE_POST_SUCCESS_DELETE
- MAX_24_HOURS
- NOT_STORED
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Customer S3 output / accepted |
| lifecycle_state | IMMEDIATE_POST_SUCCESS_DELETE |
| clock_or_trigger | Durable DORA result/version ingestion plus safe integrity check |
| period_and_delete_rule | Explicitly delete transient output as soon as technically safe. |
| authority | OD-11C-15 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-09


### Item 2

| Field | Value |
|---|---|
| component | Customer S3 output / unaccepted or failed |
| lifecycle_state | MAX_24_HOURS |
| clock_or_trigger | Earliest creation/receipt of this transient output; descendants inherit timestamp |
| period_and_delete_rule | Hard <=24h; late/rejected output never becomes a durable application object. |
| authority | OD-11C-15 |

###### aws_evidence_ids

- RET-AWS-09


### Item 3

| Field | Value |
|---|---|
| component | Service-managed output as durable DORA store |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


### Item 4

| Field | Value |
|---|---|
| component | Provider internal result remnants |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-05-4

| Field | Value |
|---|---|
| id | LC-05-4 |
| artifact | transcripts |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Approved durable DORA transcript/version metadata |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | Provider internal result copies |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-06-1

| Field | Value |
|---|---|
| id | LC-06-1 |
| artifact | transcript_versions_edits |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- UNTIL_EXPLICIT_USER_DELETE

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Local immutable versions/user edits/history |
| lifecycle_state | UNTIL_EXPLICIT_USER_DELETE |
| clock_or_trigger | Explicit scoped user deletion |
| period_and_delete_rule | Local original/transcript/history unchanged; automatic audio retention OFF, numeric catalog unapproved. Audio-only preserves text/edits; whole-recording uses exact local cascade. |
| authority | OD-11C-04/05 + DEC-013 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-06-2

| Field | Value |
|---|---|
| id | LC-06-2 |
| artifact | transcript_versions_edits |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Upload of local user edits/history |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-06-3

| Field | Value |
|---|---|
| id | LC-06-3 |
| artifact | transcript_versions_edits |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Provider copy of local user edits/history |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-06-4

| Field | Value |
|---|---|
| id | LC-06-4 |
| artifact | transcript_versions_edits |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Backup upload of local user edits/history |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-07-1

| Field | Value |
|---|---|
| id | LC-07-1 |
| artifact | caches |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- UNTIL_EXPLICIT_USER_DELETE
- NOT_APPLICABLE

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Canonical-source-bound local cache |
| lifecycle_state | UNTIL_EXPLICIT_USER_DELETE |
| clock_or_trigger | Explicit scoped user deletion |
| period_and_delete_rule | Local original/transcript/history unchanged; automatic audio retention OFF, numeric catalog unapproved. Audio-only preserves text/edits; whole-recording uses exact local cascade. |
| authority | OD-11C-04/05 + DEC-013 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | DEC-017 export temp exception |
| lifecycle_state | NOT_APPLICABLE |
| clock_or_trigger | Safe release / separately warned Delete now / one-hour maximum / startup recovery |
| period_and_delete_rule | Existing stricter one-hour export-temp contract; the new Cloud retention catalog is not applicable to this local temp. External copies remain outside DORA control. |
| authority | DEC-017 + STORAGE §8 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-07-2

| Field | Value |
|---|---|
| id | LC-07-2 |
| artifact | caches |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- IMMEDIATE_POST_SUCCESS_DELETE
- MAX_24_HOURS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA temporary audio cache / usable terminal success |
| lifecycle_state | IMMEDIATE_POST_SUCCESS_DELETE |
| clock_or_trigger | Usable terminal + durable transcript ingestion + integrity/state success |
| period_and_delete_rule | Explicit application/S3 delete as soon as technically safe, bounded by original <=24h clock. |
| authority | OD-11C-13 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-08
- RET-AWS-11


### Item 2

| Field | Value |
|---|---|
| component | DORA temporary audio cache / all outcomes including failure, cancellation and abandonment |
| lifecycle_state | MAX_24_HOURS |
| clock_or_trigger | Earliest customer-controlled Cloud byte acceptance or Cloud temporary object creation/start, whichever occurs first; persist before write. All descendants/parts/retries inherit that earliest timestamp; no reset. |
| period_and_delete_rule | Hard <=24h; no debugging/requeue/reset exception; lifecycle fallback only. |
| authority | OD-11C-13 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-08
- RET-AWS-11


### Item 3

| Field | Value |
|---|---|
| component | DORA transient result cache / accepted |
| lifecycle_state | IMMEDIATE_POST_SUCCESS_DELETE |
| clock_or_trigger | Durable DORA result/version ingestion plus safe integrity check |
| period_and_delete_rule | Explicitly delete transient output as soon as technically safe. |
| authority | OD-11C-15 |

###### aws_evidence_ids

- RET-AWS-07
- RET-AWS-09


### Item 4

| Field | Value |
|---|---|
| component | DORA transient result cache / unaccepted or failed |
| lifecycle_state | MAX_24_HOURS |
| clock_or_trigger | Earliest creation/receipt of this transient output; descendants inherit timestamp |
| period_and_delete_rule | Hard <=24h; late/rejected output never becomes a durable application object. |
| authority | OD-11C-15 |

###### aws_evidence_ids

- RET-AWS-09


#### blocking_decision_ids

None.


### LC-07-3

| Field | Value |
|---|---|
| id | LC-07-3 |
| artifact | caches |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | DORA audio/cache backup/archive/version preservation prohibited. Hidden AWS copies separately accepted under OD-11C-14. |
| replica_inclusion | No DORA audio/cross-region replica or resurrection version. Internal AWS replicas are provider-controlled. |
| maximum_restorable_horizon | DORA-controlled audio/cache backup horizon=0 (prohibited); explicit transient deletion <=24h is not backup retention. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | AWS internal cache |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-07-4

| Field | Value |
|---|---|
| id | LC-07-4 |
| artifact | caches |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA content cache backup |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-08-1

| Field | Value |
|---|---|
| id | LC-08-1 |
| artifact | operational_logs |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Local ordinary operations log |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Event/record creation; copying does not reset clock |
| period_and_delete_rule | <=30 days, shorter if sufficient; content-free only. Durable source-version association is product transcript metadata, not an excuse to retain operation logs. |
| authority | OD-11C-16 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-08-2

| Field | Value |
|---|---|
| id | LC-08-2 |
| artifact | operational_logs |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA ordinary operations log |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Event/record creation; copying does not reset clock |
| period_and_delete_rule | <=30 days, shorter if sufficient; content-free only. Durable source-version association is product transcript metadata, not an excuse to retain operation logs. |
| authority | OD-11C-16 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-08-3

| Field | Value |
|---|---|
| id | LC-08-3 |
| artifact | operational_logs |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | AWS internal technical log / own non-content usage information |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-08-4

| Field | Value |
|---|---|
| id | LC-08-4 |
| artifact | operational_logs |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Optional content-free operational recovery copy; original creation+30d expiry wins |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | AWS internal technical-log copy |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-09-1

| Field | Value |
|---|---|
| id | LC-09-1 |
| artifact | audit_security_logs |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_90_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Local security audit |
| lifecycle_state | MAX_90_DAYS |
| clock_or_trigger | Audit event creation |
| period_and_delete_rule | <=90 days; separate active authoritative state from historical audit. No audio/text or reusable secret. |
| authority | OD-11C-17 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-09-2

| Field | Value |
|---|---|
| id | LC-09-2 |
| artifact | audit_security_logs |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_90_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA security audit |
| lifecycle_state | MAX_90_DAYS |
| clock_or_trigger | Audit event creation |
| period_and_delete_rule | <=90 days; separate active authoritative state from historical audit. No audio/text or reusable secret. |
| authority | OD-11C-17 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-09-3

| Field | Value |
|---|---|
| id | LC-09-3 |
| artifact | audit_security_logs |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_90_DAYS
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Provider management history |
| lifecycle_state | MAX_90_DAYS |
| clock_or_trigger | CloudTrail management event creation |
| period_and_delete_rule | Immutable trailing90-day event history; metadata only. Not a DORA deletion tombstone or all-object-delete evidence. |
| authority | OD-11C-17/22 + documented provider default |

###### aws_evidence_ids

- RET-AWS-10


### Item 2

| Field | Value |
|---|---|
| component | Other AWS internal security metadata |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-09-4

| Field | Value |
|---|---|
| id | LC-09-4 |
| artifact | audit_security_logs |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Nonsecret audit backup; original event + 90d expiry wins |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | AWS internal audit replica |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-10-1

| Field | Value |
|---|---|
| id | LC-10-1 |
| artifact | consent_records |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Prove exact disclosure and selected grant/revocation state; no audio or transcript. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- ACTIVE_LIFETIME_PLUS_90_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Local consent receipt |
| lifecycle_state | ACTIVE_LIFETIME_PLUS_90_DAYS |
| clock_or_trigger | After ALL corresponding Cloud-derived artifacts AND processing authority are finally deleted/terminated |
| period_and_delete_rule | Keep the authoritative consent record while ANY is active; this does not extend a revoked grant; then retain the historical record for 90 days and expire absent a separately approved requirement; no earlier history expiry. Revocation stops unauthorized future work and does not delete existing artifacts. |
| authority | OD-11C-18 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-10-2

| Field | Value |
|---|---|
| id | LC-10-2 |
| artifact | consent_records |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Prove exact disclosure and selected grant/revocation state; no audio or transcript. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- ACTIVE_LIFETIME_PLUS_90_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Authoritative DORA consent ledger |
| lifecycle_state | ACTIVE_LIFETIME_PLUS_90_DAYS |
| clock_or_trigger | After ALL corresponding Cloud-derived artifacts AND processing authority are finally deleted/terminated |
| period_and_delete_rule | Keep the authoritative consent record while ANY is active; this does not extend a revoked grant; then retain the historical record for 90 days and expire absent a separately approved requirement; no earlier history expiry. Revocation stops unauthorized future work and does not delete existing artifacts. |
| authority | OD-11C-18 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-10-3

| Field | Value |
|---|---|
| id | LC-10-3 |
| artifact | consent_records |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Prove exact disclosure and selected grant/revocation state; no audio or transcript. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Full DORA consent ledger at provider |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-10-4

| Field | Value |
|---|---|
| id | LC-10-4 |
| artifact | consent_records |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Prove exact disclosure and selected grant/revocation state; no audio or transcript. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Consent ledger backup; original final-all-inactive+90d expiry wins |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-11-1

| Field | Value |
|---|---|
| id | LC-11-1 |
| artifact | deletion_records_receipts |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- 120_DAYS_AFTER_LOGICAL_DELETE

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Local deletion receipt/tombstone |
| lifecycle_state | 120_DAYS_AFTER_LOGICAL_DELETE |
| clock_or_trigger | Effective logical deletion D, never receipt time |
| period_and_delete_rule | Retain until D+120d; retire only if all tombstone_proof predicates hold. Pending copy/callback retains minimum record, with escalation; no blind TTL. |
| authority | OD-11C-21 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-11-2

| Field | Value |
|---|---|
| id | LC-11-2 |
| artifact | deletion_records_receipts |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- 120_DAYS_AFTER_LOGICAL_DELETE

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Authoritative DORA deletion ledger |
| lifecycle_state | 120_DAYS_AFTER_LOGICAL_DELETE |
| clock_or_trigger | Effective logical deletion D, never receipt time |
| period_and_delete_rule | Retain until D+120d; retire only if all tombstone_proof predicates hold. Pending copy/callback retains minimum record, with escalation; no blind TTL. |
| authority | OD-11C-21 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-11-3

| Field | Value |
|---|---|
| id | LC-11-3 |
| artifact | deletion_records_receipts |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_90_DAYS
- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Provider management delete-event history |
| lifecycle_state | MAX_90_DAYS |
| clock_or_trigger | CloudTrail management event creation |
| period_and_delete_rule | Immutable trailing90-day event history; metadata only. Not a DORA deletion tombstone or all-object-delete evidence. |
| authority | OD-11C-17/22 + documented provider default |

###### aws_evidence_ids

- RET-AWS-10


### Item 2

| Field | Value |
|---|---|
| component | Other internal provider deletion records |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-11-4

| Field | Value |
|---|---|
| id | LC-11-4 |
| artifact | deletion_records_receipts |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Deletion ledger backup; current D+120d guarded authority always reconciled first |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-12-1

| Field | Value |
|---|---|
| id | LC-12-1 |
| artifact | backups |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Only an explicitly approved recoverability policy; never an implicit permanent content copy. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Android private DB/audio/keyset backup |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-12-2

| Field | Value |
|---|---|
| id | LC-12-2 |
| artifact | backups |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Only an explicitly approved recoverability policy; never an implicit permanent content copy. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Approved durable non-audio DORA backup |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | Cloud audio or reusable-secret backup |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-12-3

| Field | Value |
|---|---|
| id | LC-12-3 |
| artifact | backups |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Only an explicitly approved recoverability policy; never an implicit permanent content copy. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- PROVIDER_DOCUMENTED_TRIGGER
- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Customer-controlled hosted non-audio backup |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | AWS internal storage/service backup |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


### Item 3

| Field | Value |
|---|---|
| component | Customer-controlled Cloud audio backup |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-12-4

| Field | Value |
|---|---|
| id | LC-12-4 |
| artifact | backups |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Only an explicitly approved recoverability policy; never an implicit permanent content copy. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- PROVIDER_DOCUMENTED_TRIGGER
- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Approved inventoried non-audio recovery copy |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | AWS internal storage/service recovery copy |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


### Item 3

| Field | Value |
|---|---|
| component | DORA Cloud audio recovery copy |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-13-1

| Field | Value |
|---|---|
| id | LC-13-1 |
| artifact | replicas |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Only explicitly approved availability copies under the same purpose and deletion scope. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Additional local replica |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-13-2

| Field | Value |
|---|---|
| id | LC-13-2 |
| artifact | replicas |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Only explicitly approved availability copies under the same purpose and deletion scope. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- UNTIL_EXPLICIT_USER_DELETE
- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA non-audio live replica stale recovery exposure |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | Active non-audio live replica |
| lifecycle_state | UNTIL_EXPLICIT_USER_DELETE |
| clock_or_trigger | Inherits source authority/lifetime; logical deletion immediately fences serving |
| period_and_delete_rule | A live replica follows source lifecycle, never a new retention extension; once deleted, stale restorable exposure is <=30d. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 3

| Field | Value |
|---|---|
| component | DORA additional Cloud audio replica |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-13-3

| Field | Value |
|---|---|
| id | LC-13-3 |
| artifact | replicas |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Only explicitly approved availability copies under the same purpose and deletion scope. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- PROVIDER_DOCUMENTED_TRIGGER

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | AWS internal same-region storage replicas |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


#### blocking_decision_ids

None.


### LC-13-4

| Field | Value |
|---|---|
| id | LC-13-4 |
| artifact | replicas |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Only explicitly approved availability copies under the same purpose and deletion scope. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. AWS internal physical-copy lifetime unpublished; no DORA restorable route authorized, no physical 30/120d promise. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- PROVIDER_DOCUMENTED_TRIGGER
- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA stale non-audio replica recovery exposure |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | AWS internal replicas |
| lifecycle_state | PROVIDER_DOCUMENTED_TRIGGER |
| clock_or_trigger | Service-purpose storage and Service Controls deletion request at actual API scope |
| period_and_delete_rule | Accepted provider limitation: internal physical purge timing unpublished, no invented TTL; mandatory improvement opt-out. Apply provider_limitation_acceptance, including separate scope for AWS own non-content information. |
| authority | OD-11C-14 |

###### aws_evidence_ids

- RET-AWS-01
- RET-AWS-02
- RET-AWS-03
- RET-AWS-04
- RET-AWS-12
- RET-AWS-13


### Item 3

| Field | Value |
|---|---|
| component | DORA cross-region/audio resurrection replica |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-14-1

| Field | Value |
|---|---|
| id | LC-14-1 |
| artifact | identity_credentials |
| holder | ANDROID_LOCAL |
| implementation_state | NOT_RUN |
| purpose | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | ANDROID_LOCAL |
| backup_inclusion | Local Android private DB/audio/keysets excluded from backup; local user history upload not authorized. |
| replica_inclusion | No extra local replica; no Cloud expansion from this cell. |
| maximum_restorable_horizon | No DORA-controlled backup restoration of this local cell; existing canonical object follows stated source lifetime. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- ACTIVE_LIFETIME_PLUS_90_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Local installation/credential authority; private secret excluded from audit |
| lifecycle_state | ACTIVE_LIFETIME_PLUS_90_DAYS |
| clock_or_trigger | Final installation/credential revocation/removal |
| period_and_delete_rule | Active enforcement state persists while active; retain the historical nonsecret record for 90 days after final revocation/removal, then expire absent another approved requirement; no earlier history expiry. Secrets revoked/deleted independently immediately when no longer authorized. |
| authority | OD-11C-19 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-14-2

| Field | Value |
|---|---|
| id | LC-14-2 |
| artifact | identity_credentials |
| holder | DORA_CONTROL_PLANE |
| implementation_state | NOT_RUN |
| purpose | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | DORA_CONTROLLED |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- ACTIVE_LIFETIME_PLUS_90_DAYS

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | DORA installation authority/verifier; secrets excluded from history |
| lifecycle_state | ACTIVE_LIFETIME_PLUS_90_DAYS |
| clock_or_trigger | Final installation/credential revocation/removal |
| period_and_delete_rule | Active enforcement state persists while active; retain the historical nonsecret record for 90 days after final revocation/removal, then expire absent another approved requirement; no earlier history expiry. Secrets revoked/deleted independently immediately when no longer authorized. |
| authority | OD-11C-19 |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-14-3

| Field | Value |
|---|---|
| id | LC-14-3 |
| artifact | identity_credentials |
| holder | AWS_PROVIDER |
| implementation_state | NOT_RUN |
| purpose | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | NOT_STORED; no new copy authorized. |
| replica_inclusion | NOT_STORED. |
| maximum_restorable_horizon | NOT_APPLICABLE: no approved stored copy. |
| deletion_propagation | No approved storage; unexpected copy triggers scoped cleanup/inventory incident. |
| receipt_semantics | No runtime absence/deletion receipt is claimed. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | No tombstone required for a never-stored scope; if unexpected storage/deletion exists, apply guarded D+120d. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Caller device private key/reusable DORA credential at provider |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


### LC-14-4

| Field | Value |
|---|---|
| id | LC-14-4 |
| artifact | identity_credentials |
| holder | BACKUP_REPLICA_SYSTEMS |
| implementation_state | NOT_RUN |
| purpose | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. |
| resolution | APPROVED_PERIOD_TRIGGER_OR_EXPLICIT_PROVIDER_LIMITATION |
| control_domain | Mixed components are explicitly scoped; AWS holder may include DORA-controlled customer S3 and hidden AWS-controlled service copies. |
| backup_inclusion | Only approved durable non-audio, content-free ledger/audit/metadata or accepted transcript/version metadata; no secrets or local edit/history upload. Copy max30d and earlier source expiry prevail. |
| replica_inclusion | DORA non-audio replica only within accepted region/scope, inherits source lifetime, deletion epoch and <=30d stale restorable horizon; hidden AWS copies separately scoped. |
| maximum_restorable_horizon | DORA backup age<=30d, earlier source expiry wins; stale replica after logical delete D must be unrecoverable by D+30d; no clock reset. |
| deletion_propagation | Persist exact scope/epoch before mutation; local and remote axes independent. Explicitly delete DORA objects/versions, fence writers, abort/reconcile multipart, reconcile terminal provider job separately. Copies inherit expiry; provider internal deletion remains scoped request/limitation. |
| receipt_semantics | Local result != remote receipt. S3 response plus namespace/part/version absence proves only that scope; DeleteTranscriptionJob proves named job action only; lifecycle eligibility/URI expiry proves no erasure. Missing receipt stays pending/failed; no physical-media or hidden-replica purge claim. |
| retry_failure | No retry/requeue/recopy resets original timestamps. Persist content-free incomplete steps, reconcile idempotently, visible failure; deadline breach blocks new audio rather than permitting extension. |
| restore_suppression | Restore current authoritative deletion/consent/identity state first; reconcile original expiry and tombstones before visibility/processing. Stale/unknown ledger or expired snapshot fails closed; audio and whole-recording deletions never resurrect. No new restore route for NOT_STORED components. |
| late_callback_behavior | Only match a current existing job, source version, grant and epoch. Callback cannot create an absent/deleted/retired object or issue processing authority; object IDs are not reused. If no binding, reject publication and reconcile scoped cleanup. Missing source is never reconstructed from text. |
| tombstone_retirement | Minimal DORA tombstone retained D+120d and only retires under ALL tombstone_proof predicates; unresolved copy/callback prevents expiry. Restore never resets D. Provider logs are not DORA tombstones and follow their separate documented policy. |
| source_unavailable_behavior | Never reconstruct source from text; suppress ASR/publication when original/version/grant unavailable. Audio-only preserves accepted transcript/edits/search and exact source reason; whole-recording removes exact disclosed structured cascade; deletion/status work survives source loss. DEC-017 and external copies retain their separate boundary. |

#### lifecycle_states

- MAX_30_DAYS
- NOT_STORED

#### lifecycle_components

### Item 1

| Field | Value |
|---|---|
| component | Nonsecret installation/credential audit backup; original final-revocation+90d expiry wins |
| lifecycle_state | MAX_30_DAYS |
| clock_or_trigger | Snapshot creation; earlier original source expiry wins; last permissible pre-delete snapshot t<=D |
| period_and_delete_rule | Maximum restorable age 30d; after logical deletion exposure<=D+30d. Recopy cannot reset; restore deletion ledger first. Only approved durable non-audio, no local user-edit/history upload. |
| authority | OD-11C-20/21 |

###### aws_evidence_ids

None.


### Item 2

| Field | Value |
|---|---|
| component | Device private key/reusable-secret backup |
| lifecycle_state | NOT_STORED |
| clock_or_trigger | No approved storage |
| period_and_delete_rule | Prohibited/absent by bounded design; unexpected copy is an incident and requires scoped cleanup, never assumed deleted. |
| authority | OD-11C-06/10/20 + existing scope |

###### aws_evidence_ids

None.


#### blocking_decision_ids

None.


## lifecycle_coverage

| Field | Value |
|---|---|
| artifacts | 14 |
| holders | 4 |
| cells | 56 |
| classified_cells | 56 |
| unresolved_cells | 0 |
| count_basis | A mixed-domain cell may have multiple applicable states; classification counts are incidences, not an additive 56-cell partition. |

### classification_counts

| Field | Value |
|---|---|
| UNTIL_EXPLICIT_USER_DELETE | 7 |
| IMMEDIATE_POST_SUCCESS_DELETE | 6 |
| MAX_24_HOURS | 6 |
| PROVIDER_DOCUMENTED_TRIGGER | 18 |
| NOT_STORED | 19 |
| MAX_30_DAYS | 16 |
| PROVIDER_DOCUMENTED_DEFAULT_WITH_EARLY_DELETE | 1 |
| NOT_APPLICABLE | 1 |
| MAX_90_DAYS | 4 |
| ACTIVE_LIFETIME_PLUS_90_DAYS | 4 |
| 120_DAYS_AFTER_LOGICAL_DELETE | 2 |



## active_blocker_ids

- PC-01
- SC-02

## resolved_blocker_ids

- PC-02
- RT-01
- RT-02
- SC-01

## effective_status_counts

| Field | Value |
|---|---|
| SATISFIED | 6 |
| PARTIALLY_SATISFIED | 0 |
| OPEN | 5 |
| BLOCKED | 3 |
| NOT_RUN | 25 |


## remaining_6_3_predecessor_blockers

- CLD-ADM-PRIVACY-001
- CLD-ADM-CONTROL-001
- CLD-ADM-EVALUATION-001
- CLD-ADM-PROVIDER-001

## review

| Field | Value |
|---|---|
| author | OpenAI Codex project coordinator |
| reviewer | OpenAI Codex internal technical/authority review |
| formalReviewer | false |
| independent_review | Separate Codex read-only peer review completed; no outstanding Critical/Important findings. This is not external or qualified human approval. |
| qualified_privacy_legal_approval | NOT_AVAILABLE |
| qualified_independent_security_approval | NOT_AVAILABLE |
| scope | Ten explicit owner decisions,15 unchanged frozen criteria,56 existing cells,15 current AWS retention facts; CONTROL dependency only; no independent certification. |
| project_owner_acceptance | OD-11C-13..22 APPROVED_BY_PROJECT_OWNER; accepted architecture OD-11C-12 remains. |


## disclosures

### ru

| Field | Value |
|---|---|
| language | ru |
| version | 0.3 retention addendum to unchanged v0.2 candidate/scope disclosure |
| grant_action_enabled | false |
| text | Облачная обработка пока недоступна. Для закрытой Alpha выбран Amazon Transcribe в eu-central-1. Управляемое DORA облачное аудио удаляется сразу после безопасного сохранения результата и проверки состояния, во всех случаях не позднее 24 часов от исходного облачного приёма/создания; повтор не продлевает срок. Временный текст удаляется после сохранения в DORA, непринятый — не позднее 24 часов от создания. Сохранённая расшифровка остаётся до явного удаления; удаление только аудио сохраняет текст и правки. Обычные технические записи DORA хранятся до 30 дней, аудит — до 90 дней; запись о согласии хранится, пока есть связанные материалы или полномочия, затем её история хранится 90 дней. Отзыв согласия немедленно запрещает новое неавторизованное использование; хранение записи не продлевает согласие. История установки/доступа — 90 дней после окончательного отзыва; секреты для аудита не сохраняются. Резервные копии облачного аудио запрещены; восстановимые копии разрешённых данных без аудио ограничены 30 днями. Запись удаления хранится 120 дней и не удаляется при неразрешённом риске восстановления или позднего ответа. У AWS запись задания обычно хранится 90 дней, но DORA удаляет завершённое задание раньше, как только это безопасно. История управляющих событий CloudTrail — 90 дней. Для внутренних служебных копий AWS точный срок физического удаления не опубликован: DORA применяет доступные запросы удаления и обязательный отказ от улучшения сервиса, раскрывая их ограничения. Отзыв согласия не равен удалению; локальное удаление не доказывает удаление у AWS. Подтверждение API относится только к указанному объекту/заданию. Юридическое одобрение фактического объёма обработки ещё отсутствует. |


### en

| Field | Value |
|---|---|
| language | en |
| version | 0.3 retention addendum to unchanged v0.2 candidate/scope disclosure |
| grant_action_enabled | false |
| text | Cloud processing is not yet available. The closed Alpha candidate is Amazon Transcribe in eu-central-1. DORA-controlled Cloud audio is explicitly deleted as soon as safe after durable result ingestion and state checks, always within 24 hours of the original Cloud receipt/creation; retries do not reset the clock. Transient output is deleted after safe ingestion; unaccepted output expires within 24 hours of creation. Durable transcripts remain until explicit deletion; audio-only deletion preserves text and edits. Ordinary DORA operational records last at most 30 days, security audit 90 days; the authoritative consent record is retained while any related artifact or authority is active, followed by 90 days of history. Revocation stops future unauthorized processing; record retention does not extend a revoked grant. Installation/access history lasts 90 days after final revocation; secrets are not retained for audit. Cloud audio backup is prohibited; approved non-audio backups are restorable for at most 30 days. Deletion records last 120 days and cannot expire while an unresolved copy or callback could resurrect an object. AWS job records default to 90 days, but DORA deletes terminal records earlier as soon as safe. CloudTrail management-event history lasts 90 days. AWS publishes no exact physical purge period for hidden service-function copies: DORA uses available deletion controls and mandatory improvement opt-out, with their limits disclosed. Revoking consent is not deletion; local deletion does not prove AWS deletion. API confirmation applies only to the named object/job scope. Qualified actual-scope Privacy/Legal approval is still absent. |


## active_disclosure_enabled

false

## next_task

Only PC-01: qualified Privacy/Legal actual-scope approval, followed by dependent SC-02 / PRIVACY-to-CONTROL readback. Do not automatically start it. Evaluation/provider/runtime remain separate later work.

## limitations

- Retained provider source gaps are explicit accepted limitations, not future unspecified retention decisions or claims of physical purge.
- PC-01 unchanged; no new Legal signature, AWS contract execution, effective opt-out, deployment or runtime evidence.
- Architecture PC-02/SC-01 remains resolved. CONTROL still blocked by PRIVACY alone; 11.1C remains PARTIAL.
- No runtime/device test or CI run claimed by this package. All real/synthetic Cloud audio and AWS runtime calls remain zero.

## retention_architecture_decision

| Field | Value |
|---|---|
| path | docs/adr/ADR-0012-alpha-retention-periods-and-provider-copy-limits.md |
| sha256 | 94eabca845a44f31ae7d47cb0a542db29269dcf8019e6f7ba72e6ab90f9c0f99 |
| status | APPROVED_BY_PROJECT_OWNER |
| authority | OD-11C-13..22 |
