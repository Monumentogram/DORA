# DORA Cloud Alpha privacy, retention and control v0.2

Baseline `cccf85f982436af0bf9675d738dfe2dd61308842`; branch `chat/alpha-asr-runner-scope`; assessed 2026-09-28.

**11.1C = PARTIAL / OWNER_DECISIONS_APPLIED_PC02_SC01_RESOLVED_RESIDUAL_PRIVACY_AND_RETENTION_PREREQUISITES**.

[Machine-readable contract](../contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.json); [accepted architecture ADR](../adr/ADR-0011-alpha-transcribe-privacy-retention-key-custody.md). v0.2 is the sole current successor; [v0.1](DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.md) remains immutable history.

## 1. Authority and decisions

| Authority | Reconciliation |
|---|---|
| OD-11C-01..12 | All twelve instructions are explicit owner decisions, attributable to this task/date/baseline; preserved verbatim in Appendix A and JSON. Approval scope is product/architecture, not a Legal signature or AWS contract execution. |
| Version chain | v0.2 succeeds v0.1 prospectively; prior package, frozen 6.2C and accepted scope/gaps/closeout remain byte-identical. |
| Technical Plan §§13/24/28 and ADR-0009/0010 | Owner-approved Alpha amendment now selects installation credentials, DORA revocable ingress, Frankfurt, Transcribe candidate and customer KMS/S3 architecture. Broad OIDC/Keycloak/presigned-URL recommendations are not mandatory consumer IdP or unrevocable upload authority for this Alpha. |
| DEC-001/010/011/012 | Internal audience/access, candidate, region and audio/transcript policy are now decided. Legal identity/controller relationship/sign-off and the explicitly enumerated remaining non-content/copy periods are not silently approved. Full-market/public-release decisions remain untouched. |
| DEC-009/013/014/015/017/039 and ASR/storage contracts | Retain local original until explicit deletion; automatic retention OFF; original audio is ASR input; preserve consent modes, immutable text/edits, local independence and exact local/remote deletion axes. Export-temp exception unchanged. |
| RDY-013 and ADR-0011 | The accepted bounded custody ADR supplies the previously missing architectural record. No independently credentialed security reviewer is demanded by CONTROL criterion 1/4 or required-evidence wording; reviewer is Codex internal technical design review, acceptance is Project Owner OD-11C-12. |
| Frozen PRIVACY criterion 5 | Qualified owner approval for actual scope and privacy/legal decision are still required; the Project Owner expressly distinguishes these from this product/architecture authorization. PC-01 retains only that approval/applicability record. |
| Frozen RETENTION criteria 2-4 | Truthful limitations and scoped receipts are allowed; no universal physical-erasure certificate is invented as a new criterion. Actual periods still require approval, and OD-11C-04 independently requires a 24-hour audio ceiling. |
| Review boundary | No runtime penetration test, independent certification, human reviewer name, attorney/DPO signature, AWS account effective policy or contractual acceptance is claimed. Runtime controls remain testable later gates. |

### Applied owner decisions

| ID | Status | Applied decision |
|---|---|---|
| OD-11C-01 | APPROVED_BY_PROJECT_OWNER | Closed invitation-only technical/product Alpha; only Owner and individually authorized testers; no public registration/release/anonymous access/production. |
| OD-11C-02 | APPROVED_BY_PROJECT_OWNER | One audio/content-processing region: eu-central-1. No VPN/geolocation routing, cross-region fallback, silent migration or multi-region processing. Required bound services verified; global administrative controls separately disclosed. |
| OD-11C-03 | APPROVED_BY_PROJECT_OWNER | Amazon Transcribe is the 11.1C candidate only. Standard batch/file path considered; streaming capability is documentary context, not added product scope or 6.2D admission. |
| OD-11C-04 | APPROVED_BY_PROJECT_OWNER | Local original until explicit deletion, automatic retention OFF. Cloud audio cleanup immediately once result is durably ingested; every unsuccessful/unfinished case has a hard 24-hour ceiling, never a minimum. |
| OD-11C-05 | APPROVED_BY_PROJECT_OWNER | DORA owns accepted transcript/version persistence until explicit deletion; audio-only deletion preserves text/edits/versions. No service-managed durable transcript store. |
| OD-11C-06 | APPROVED_BY_PROJECT_OWNER | DORA-controlled private same-region transient S3 input/output; no audio archive, cross-region replication or audio-preserving versioning. Explicit deletion plus independent deadline failsafe; lifecycle alone is insufficient. |
| OD-11C-07 | APPROVED_BY_PROJECT_OWNER | Service improvement/training/unrelated human review forbidden. Transcribe opt-out POLICY_REQUIRED; POLICY_EFFECTIVE_RUNTIME_VERIFICATION = NOT_RUN, mandatory before first audio. |
| OD-11C-08 | APPROVED_BY_PROJECT_OWNER | Conditional exception evaluated against AWS facts, not applied blindly: job quota is 90 days/non-adjustable, but terminal jobs are explicitly deletable earlier. Do not call all jobs mandatory 90-day retention. Independent immutable CloudTrail metadata constraint is separately evidenced. |
| OD-11C-09 | APPROVED_BY_PROJECT_OWNER | Explicit customer output bucket and KMS output encryption; delete transient output after durable DORA ingestion; no reliance on default service-managed output or its URI/job expiry. |
| OD-11C-10 | APPROVED_BY_PROJECT_OWNER | No long-term/transregional/archival audio backups; no content resurrection. Durable tombstones required; provider-internal erasure limits disclosed. Non-audio copy/ledger retirement bounds remain specifically unapproved. |
| OD-11C-11 | APPROVED_BY_PROJECT_OWNER | No raw audio/content-bearing debug logs or raw secrets; content-free IDs/states/times/error classes only. Minimize transcript/sensitive content. No general log retention period is invented. |
| OD-11C-12 | APPROVED_BY_PROJECT_OWNER | Owner accepts installation identity, separable accounts, expiring/refreshable/revocable replay-protected credentials, DORA authorization boundary, TLS/at-rest/KMS custody and both ledgers. ADR-0011 records accepted design; independent certification/runtime evidence not fabricated. |

## 2. Current AWS fact package

Read-only current official documentation, retrieved 2026-09-28. No AWS account/API calls. Facts are distinct from owner policy, inferred consequences and runtime proof. Live documents can change; this package records identity/section/retrieval date and bounded facts, not a claim of archived full-page bytes.

| ID / AWS document | Section / version | Verified fact | Limitation |
|---|---|---|---|
| AWS-TERMS: [AWS Service Terms](https://aws.amazon.com/service-terms/) | 1.9, 1.11, 1.14, 50.3-50.4; updated 2026-09-11 | Transcribe is within the AI-content improvement-use clause; Organizations opt-out is supported. Customer notice/consent duties remain. Terms incorporate the DPA; account contracting party is account-specific. | This task records published terms, not execution/acceptance of a contract or identification of DORA's actual legal party. |
| AWS-REGION: [Amazon Transcribe endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/transcribe.html) | Batch and streaming endpoints; job-record quota | Frankfurt eu-central-1 exposes batch and streaming HTTPS endpoints. The job-record retention quota is 90 days and is marked non-adjustable. | A non-adjustable automatic quota is not a prohibition on explicit DeleteTranscriptionJob; it is not input-audio retention or a quality result. |
| AWS-LANG: [Supported languages and language-specific features](https://docs.aws.amazon.com/transcribe/latest/dg/supported-languages.html) | Russian and English rows | ru-RU and en-US/en-GB support batch and streaming. Russian lacks custom language models, redaction and Call Analytics in this table. | First Alpha needs standard file ASR only; no diarization, redaction, custom model or streaming scope added. Language support does not prove RU/EN quality. |
| AWS-OPT: [Opting out of using your data for service improvement](https://docs.aws.amazon.com/transcribe/latest/dg/opt-out.html) | Default use and opt-out | Transcribe defaults to storing/using processed voice inputs for improvement; use an AWS Organizations opt-out policy to opt out. | Support for policy does not prove an effective policy on DORA's account. |
| AWS-ORG: [AI services opt-out policies](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_ai-opt-out.html) | Scope and historical content | Policies can cover one or all supported AI services and have an account effective-policy view. Opt-out removes historical improvement content, excluding data still needed to provide service functions. | No deletion completion deadline, receipt for each internal copy, or 24-hour service-function audio bound is stated here. |
| AWS-OPT-SYNTAX: [AI services opt-out policy syntax and examples](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_ai-opt-out_syntax.html) | services.transcribe.opt_out_policy; inheritance | transcribe is a supported service key; @@assign = optOut selects opt-out. Child-policy controls determine allowed overrides. | Review effective policy and inheritance for the actual processing account before audio; no policy was created/read in this task. |
| AWS-INPUT: [Media API](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_Media.html) | MediaFileUri | Batch media is an S3 object; the input bucket must be in the request's AWS Region. The URI shape supports s3 and HTTP(S) S3 locators. | HTTP(S) syntax does not authorize arbitrary non-S3 sources. DORA design uses same-account, same-region private buckets; broader cross-account cases are not required. |
| AWS-START: [StartTranscriptionJob API](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_StartTranscriptionJob.html) | Media, OutputBucketName, OutputEncryptionKMSKeyId, JobExecutionSettings | Batch requires prior S3 upload. Explicit OutputBucketName chooses customer output; omission uses service-managed output. OutputEncryptionKMSKeyId accepts a KMS key ARN; caller and supplied data-access role need permission. | No deployment, role, bucket, region enablement or successful job is verified; request fields are documentary capability evidence. |
| AWS-OUTPUT: [Data input and output](https://docs.aws.amazon.com/transcribe/latest/dg/how-input.html) | Output; deletion-support note | Customer-bucket transcripts remain until customer removal. Service-managed output expires with the job at 90 days. Streaming returns results over the stream; support handles requests concerning stored content. | Service-managed expiry is excluded from DORA durable storage; no automatic S3 cleanup after DORA ingestion is promised by AWS. |
| AWS-URI: [Transcript API](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_Transcript.html) | TranscriptFileUri | Service-managed output access URIs last 15 minutes and can be refreshed with a job query; explicit output bucket returns its location. | URI expiry is not object deletion. DORA's own S3 output has its own authorization/lifecycle. |
| AWS-DELETE-JOB: [DeleteTranscriptionJob API](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_DeleteTranscriptionJob.html) | Response Elements; BadRequestException | Explicit job deletion is available, returns HTTP 200 with an empty body, and can reject a non-terminal job. | This is not a documented hard-cancel API or all-copy erasure receipt. Delete S3 objects separately; do not claim AWS-internal audio/backup purge by 24 hours. |
| AWS-ENCRYPT: [Data encryption](https://docs.aws.amazon.com/transcribe/latest/dg/data-encryption.html) | At rest, in transit, key management, encryption context | Transcribe documents TLS 1.2, encrypted EBS with its default key, S3 input encryption and customer symmetric KMS output encryption. Encryption context is non-secret and visible in CloudTrail. | A customer output KMS key does not imply customer custody of every internal Transcribe key or prevent the service from reading audio. |
| AWS-TLS: [Data protection in Amazon Transcribe](https://docs.aws.amazon.com/transcribe/latest/dg/data-protection.html) | Protection and shared responsibility | TLS 1.2 is required and TLS 1.3 recommended. Customers control configuration/content; AWS protects underlying infrastructure. Sensitive tags/names/URLs should be avoided. | These controls are documented, not runtime-tested. |
| AWS-IAM: [Amazon Transcribe identity-based policy examples](https://docs.aws.amazon.com/transcribe/latest/dg/security_iam_id-based-policy-examples.html) | S3 input/output policies; encryption keys; confused deputy | Input roles need scoped S3 read and KMS decrypt; output roles need S3 write and the configured key permissions. Transcribe role trust can restrict SourceAccount/SourceArn. | Example wildcard permissions are not DORA policy. Resolve real ARNs and positive/negative least-privilege tests later; no API call occurs here. |
| AWS-FAQ: [Amazon Transcribe FAQs](https://aws.amazon.com/transcribe/faqs/) | Data Privacy | AWS describes authorized employee access, deletion APIs/support, regional storage and improvement-related out-of-region copies; it states opt-out prevents the latter. | No zero-human-access claim, no 24-hour internal audio/replica deletion promise. FAQ region wording refers to Support; current Service Terms and guide identify Organizations as opt-out mechanism. |
| AWS-DPA: [AWS Data Processing Addendum](https://d1.awsstatic.com/legal/aws-dpa/aws-dpa.pdf) | 1.1, 2-4, 6, 12.1, 14, 16; live 10-page PDF | AWS is processor to a customer that may be controller or processor. Access is limited to service/law purposes; personnel confidentiality applies. Regional transfer exceptions remain; deletion uses service controls. | The post-termination 90-day request window is not a per-job purge SLA. Actual party/lawful role requires PC-01; no claim this task executed the DPA. |
| AWS-SUBPROCESSORS: [AWS Sub-processors](https://aws.amazon.com/compliance/sub-processors/) | 1; 2(b), 2(c); current public list | A100 ROW GmbH is listed for Frankfurt infrastructure. Improvement entities apply unless opted out. Customer-initiated support entities process content only if the customer agrees to share it. | Regional infrastructure is not a guarantee every metadata/support activity stays in Frankfurt. No support-content sharing is authorized; published relevant categories are disclosed and changes require review. |
| AWS-S3-REGION: [Amazon S3 endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/s3.html) | General-purpose S3 endpoints | S3 eu-central-1 endpoints are listed. | Use HTTPS only; bucket/account configuration and availability to the actual account remain future verification. |
| AWS-KMS-REGION: [AWS KMS endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/kms.html) | Regional endpoints | KMS eu-central-1 HTTPS endpoints are listed. | Use a single-region customer-managed symmetric key; no multi-region key replica is approved. |
| AWS-S3-DELETE: [Amazon S3 user guide](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html) | Strong consistency; Regions; data consistency model | S3 provides strong object PUT/DELETE/read consistency and regional storage; S3 itself uses internal replication. Customer versioning can preserve versions. | Logical object absence is not a per-medium physical-erasure certificate. New writes must be fenced; disable transient-audio versioning, cross-region replication and archival copies. |
| AWS-S3-LIFECYCLE: [Expiring objects](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-expire-general-considerations.html) | Important considerations and Note | S3 may remove an expired object after its expiration date; expiry and deletion are not simultaneous. | Lifecycle alone cannot prove a hard 24-hour ceiling. DORA requires explicit deletion, deadline reconciliation and a separate failsafe; runtime proof remains later. |
| AWS-MULTIPART: [AbortMultipartUpload API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html) | Operation description | In-flight parts may still finish during abort; repeated abort and ListParts verification can be necessary. | Fence ingress, reconcile multipart remnants and retain pending/failed status until the scoped parts inventory is empty. |
| AWS-KMS-AUDIT: [Logging AWS KMS API calls with AWS CloudTrail](https://docs.aws.amazon.com/kms/latest/developerguide/logging-using-cloudtrail.html) | KMS event coverage and excluded fields | CloudTrail records KMS usage/management events, including decrypt; certain sensitive cryptographic fields are omitted. Trails may exclude KMS events. | DORA forbids such audit exclusion and keeps encryption context content-free. No claim a trail exists or that this proves S3 object-level deletion. |
| AWS-CT-HISTORY: [Working with CloudTrail event history](https://docs.aws.amazon.com/awscloudtrail/latest/userguide/view-cloudtrail-events.html) | Default event history and limitations | Regional management-event history is enabled by default, immutable and covers 90 days; trail settings do not change it and KMS events cannot be excluded. | A distinct independently documented metadata constraint, not an extension of Transcribe's 90 days. Does not include S3 data events or set retention for DORA logs/trails. |
| AWS-CT-REGION: [AWS CloudTrail endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/ct.html) | Service endpoints | CloudTrail eu-central-1 HTTPS endpoints are listed. | Regional audit capability only; no trail, account or runtime read. |
| AWS-IAM-REGION: [IAM endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/iam-service.html) | Service endpoints | The Frankfurt entry uses iam.amazonaws.com, the shared AWS IAM endpoint. | AWS infrastructure IAM is not a consumer identity provider; keep content out of administrative names/policies; do not assert all administrative metadata is Frankfurt-local. |
| AWS-ORG-REGION: [AWS Organizations endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/ao.html) | Service endpoints | Organizations is global per partition; Frankfurt uses organizations.us-east-1.amazonaws.com. | The opt-out is an administrative control, not a second audio-processing region. No audio/transcript is sent to Organizations; global administration must be disclosed in legal scope. |

### Candidate and configuration boundary

| Field | Value |
|---|---|
| platform | AWS |
| service | Amazon Transcribe |
| mode | STANDARD_BATCH_FILE_ASR |
| region | eu-central-1 |
| region_policy | ONE_REGION_NO_FALLBACK_NO_MIGRATION_NO_MULTI_REGION_CONTENT_PROCESSING |
| languages_documented | ru-RU; en-US; en-GB |
| english_locale_quality_admission | DEFERRED_TO_6.2D |
| streaming | DOCUMENTED_AVAILABLE_NOT_SELECTED_FOR_FIRST_ALPHA |
| excluded_features | Medical; HealthScribe; Call Analytics; diarization; PII redaction; custom language models; custom vocabulary; multi-language quality claims |
| input | DORA-controlled private S3, same account and eu-central-1 |
| output | Explicit DORA-controlled private S3 transient bucket; separate durable DORA transcript store |
| key_custody | Customer-managed single-region symmetric KMS keys for DORA S3 input/output; provider internal EBS uses provider default key |
| bound_regional_services | Amazon Transcribe; Amazon S3; AWS KMS; AWS CloudTrail |
| global_administration | AWS IAM; AWS Organizations |
| administrative_data_boundary | No recording audio/transcript in IAM/Organizations; no assertion all AWS administration/billing/support metadata is regional. PC-01 qualified scope review covers actual legal applicability. |
| additional_runtime_stack | No compute/database/queue product selected here; any additional deployment dependency must independently prove eu-central-1 compatibility before use, with no fallback. |
| provider_admission | NOT_RUN |
| account_configuration_verification | NOT_RUN |
| quality_benchmark | NOT_RUN |

Opt-out is **POLICY_REQUIRED**; **POLICY_EFFECTIVE_RUNTIME_VERIFICATION = NOT_RUN**. Required effective value is `services.transcribe.opt_out_policy = optOut`; inspect effective account policy and inheritance before audio. No policy was created. Historical opt-out cleanup covers improvement copies, not data required for service functions.

## 3. Exact blocker reassessment

| ID | Before | After | Resolved evidence | Minimal remaining issue |
|---|---|---|---|---|
| PC-01 | QUALIFIED_APPROVAL_NOT_AVAILABLE | BLOCKED_EXTERNAL_APPROVAL | Audience/access/region/candidate/audio policy/security-owner acceptance resolved by OD-11C-01..12. | Attributable qualified privacy/legal scope decision is absent. It must bind actual responsible party/contact, applicable controller/processor/contracting roles and reviewed disclosure; this is the narrow approval record, not an unresolved choice of Alpha audience/region. |
| PC-02 | BLOCKING_SCOPE_EVIDENCE_MISSING | RESOLVED | Candidate/service/region, improvement/opt-out, inputs/outputs, roles/subprocessors, human-access limits, deletion and documented limitations bound in 27 current AWS fact records. | No remaining broad provider-fact-pack blocker. Actual legal applicability is PC-01; the specific undocumented internal-audio deadline is RT-01. Configuration/effective-policy proof and quality checks are assigned to later gates. |
| RT-01 | BLOCKING_PERIOD_DECISION | PARTIALLY_RESOLVED | Local lifetime, immediate success cleanup, 24-hour audio ceiling, distinct durable transcript, customer output and earlier terminal-job deletion now explicit. CloudTrail event-history constraint is separately documented. | Two residuals: (a) no reviewed official source establishes a <=24-hour maximum for service-function/internal audio copies or a hard stop for non-terminal Transcribe jobs; obtain authoritative bounded evidence without weakening OD-11C-04; (b) approve exact retention/expiry triggers for DORA operational/security logs, minimal job/consent/identity records and failed/unaccepted transient transcript output, including explicit acceptance or resolution of the documented unknown provider-internal non-audio log/receipt/result-remnant periods. Owner minimization/architecture approval does not specify those periods. |
| RT-02 | BLOCKING_COPY_AND_RECEIPT_DECISION | PARTIALLY_RESOLVED | No long-term/archival/cross-region audio copy, no deleted-audio version retention, mandatory tombstone/restore fencing and exact receipt limitations are decided. No impossible physical-erasure receipt is required by the frozen criterion. | Only non-audio backup/replica inclusion and maximum restorable horizon, plus approved tombstone/receipt retirement condition remain. Mandatory tombstones cannot expire while a restorable copy/callback exists; neither indefinite retention nor a fabricated purge deadline is approved. Internal-audio compatibility is counted once under RT-01. |
| SC-01 | QUALIFIED_DESIGN_ACCEPTANCE_NOT_AVAILABLE | RESOLVED | OD-11C-12 accepts the bounded design; internal technical review and ADR-0011 define identity, KMS custody, decrypt principals, authorization/revocation and ledgers with RDY controls/risks. | No separate credentialed Security signature or runtime proof is mandated for this bounded design-entry record. Numeric credential/nonce/lease parameters and implementation race tests are later AUTH/UPLOAD/CRYPTO/SECRETS/SECURITY verification, not fabricated results. Privacy/retention residuals remain under SC-02. |
| SC-02 | BLOCKED_BY_PRIVACY_AND_RETENTION | BLOCKED_BY_FROZEN_CRITERION | CONTROL architecture acceptance resolved and old absence of owner acceptance removed. | Frozen CONTROL dependencies still require PRIVACY and RETENTION. Exact residuals are PC-01 qualified scope approval, RT-01 provider audio ceiling/non-content periods and RT-02 non-audio restore/ledger horizon. No runtime/quality/circular prerequisite is added. |

## 4. All 15 frozen criteria

Criterion text is copied verbatim. SATISFIED here means this individual design/policy criterion; gate closure also requires every criterion, required evidence and unchanged dependencies. Runtime verification remains separate.

### CLD-ADM-PRIVACY-001

Before: **BLOCKED**; after: **BLOCKED**. Dependencies unchanged: CLD-ADM-SCOPE-001, CLD-ADM-CONSENT-001.


| Index | Criterion verbatim | Previous assessment | New assessment | Evidence | Authority | Blockers | Owner resolves | AWS resolves | Runtime later |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Approved data-flow inventory states what leaves device, destination/service role, purpose and applicable controller/processor responsibilities | DESIGN_COMPLETE_APPROVAL_BLOCKED | PARTIALLY_SATISFIED | Approved operational DF-01..08 and OD-01/02/03 scope exist; the actual legal responsibility binding belongs to the missing qualified decision. | OD-11C-01; OD-11C-02; OD-11C-03; AWS-DPA | PC-01 | false | false | true |
| 2 | Versioned user-visible disclosure covers provider/subprocessors, data location, retention, deletion and revocation; revocation is not retroactive erasure | PARTIAL_DISCLOSURE_RELEASE_BLOCKED | PARTIALLY_SATISFIED | RU/EN 0.2 now identifies Transcribe, Frankfurt, relevant subprocessors, approved lifetimes and truthful limitations. Active consent remains disabled until qualified scope and remaining periods are fixed. | OD-11C-02; OD-11C-04; OD-11C-05; AWS-SUBPROCESSORS; AWS-OUTPUT | PC-01; RT-01; RT-02 | false | false | true |
| 3 | ASR processing consent does not authorize training, human review or unrelated artifacts | DESIGN_EVIDENCE_AVAILABLE | SATISFIED | Purpose prohibition and mandatory effective opt-out are explicit; unrelated human review/support content sharing disabled. Required operational employee access is disclosed, not described as user-authorized human review. | OD-11C-07; AWS-TERMS; AWS-OPT; AWS-ORG; AWS-DPA |  | true | true | true |
| 4 | Provider/region changes cannot silently expand grants; no location inference from VPN | DESIGN_EVIDENCE_AVAILABLE | SATISFIED | Single-region grant binding; no silent provider/region expansion or VPN inference; explicit redecision/reconsent for changed scope. | OD-11C-02; OD-11C-03 |  | true | false | true |
| 5 | Record qualified owner approval for actual scope; this gate contract supplies no legal approval | QUALIFIED_APPROVAL_NOT_AVAILABLE | BLOCKED | Project Owner product/architecture decisions exist; no attributable qualified privacy/legal actual-scope approval is supplied. Frozen criterion does not automatically equate the two. | OD-11C-01; OD-11C-12 | PC-01 | false | false | true |

Required evidence (verbatim):

- Reviewed disclosure text/version, data-flow map and privacy/legal decision
- Candidate/provider terms and data-use/subprocessor/location evidence bound to scope, then revalidated for selected provider

### CLD-ADM-RETENTION-001

Before: **PARTIALLY_SATISFIED**; after: **PARTIALLY_SATISFIED**. Dependencies unchanged: CLD-ADM-SCOPE-001.


| Index | Criterion verbatim | Previous assessment | New assessment | Evidence | Authority | Blockers | Owner resolves | AWS resolves | Runtime later |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Preserve approved local default: original audio until explicit deletion; automatic retention OFF with unapproved numeric catalog unavailable | SATISFIED_BY_EXISTING_AUTHORITY | SATISFIED | Approved local until-explicit-delete rule and automatic-retention OFF remain unchanged. | OD-11C-04 |  | true | false | true |
| 2 | Approve actual periods/triggers for DORA objects, provider copies, job metadata, transcripts, caches, logs, backups and replicas; no arbitrary TTL from recommendations | BLOCKED_PERIODS_NOT_APPROVED | PARTIALLY_SATISFIED | 56 lifecycle cells now apply exact owner periods/prohibitions and separately verified provider constraints. Internal provider audio ceiling and enumerated non-content/non-audio periods remain unresolved. | OD-11C-04; OD-11C-05; OD-11C-06; OD-11C-08; OD-11C-09; OD-11C-10; OD-11C-11; AWS-REGION; AWS-DELETE-JOB; AWS-CT-HISTORY | RT-01; RT-02 | false | false | true |
| 3 | Define deletion propagation, receipt scope, failure/retry, late callbacks, restore suppression and source-unavailable behavior | DESIGN_COMPLETE_APPROVAL_BLOCKED | SATISFIED | Deletion/outbox/receipt/retry/late-result/restore/source-loss behavior is fully defined, including failure and unverified-provider branches. This criterion requires defined semantics, not an unsupported physical-erasure certificate; periods remain criterion 2. | OD-11C-10; OD-11C-12; AWS-S3-DELETE; AWS-MULTIPART; AWS-DELETE-JOB |  | true | true | true |
| 4 | Consent revocation != deletion; local deletion != proof of remote deletion; disclose limits of provider receipts | DESIGN_EVIDENCE_AVAILABLE | SATISFIED | Revocation, local erase and remote receipt axes remain separate. API absence/receipt is scoped and internal-provider media are excluded from unproved erasure claims. | OD-11C-10; AWS-ORG; AWS-DPA |  | true | true | true |
| 5 | Audio-only deletion preserves transcripts/edits; whole recording deletion uses exact disclosed cascade | DESIGN_EVIDENCE_AVAILABLE | SATISFIED | Audio-only deletion retains accepted transcripts/edits/versions; whole-recording exact local and separately confirmed remote cascades preserved. | OD-11C-05; OD-11C-09 |  | true | false | true |

Required evidence (verbatim):

- Approved per-artifact/per-holder lifecycle matrix
- Versioned Cloud period/trigger decision plus backup/replica and receipt semantics

### CLD-ADM-CONTROL-001

Before: **BLOCKED**; after: **BLOCKED**. Dependencies unchanged: CLD-ADM-PRIVACY-001, CLD-ADM-RETENTION-001.


| Index | Criterion verbatim | Previous assessment | New assessment | Evidence | Authority | Blockers | Owner resolves | AWS resolves | Runtime later |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Reviewed design defines caller/installation identity and server-issued credentials, optional future accounts, ownership, refresh/revocation and replay protection; no identity provider chosen here | DESIGN_COMPLETE_ACCEPTANCE_BLOCKED | SATISFIED | Owner-accepted reviewed installation identity, credential refresh/revocation/replay/ownership design; optional accounts separate. Exact implementation parameter profile is required before AUTH/UPLOAD runtime acceptance. | OD-11C-12 |  | true | false | true |
| 2 | Resolve payload protection, encryption at rest, key custody/worker decrypt access and audit; no unproved E2EE claim | DESIGN_COMPLETE_ACCEPTANCE_BLOCKED | SATISFIED | ADR-0011 resolves TLS, customer KMS/S3 envelope boundary, managed Transcribe plaintext/internal keys, least-privilege roles and audit; no E2EE or immediate crypto-erasure claim. | OD-11C-12; AWS-ENCRYPT; AWS-IAM; AWS-KMS-AUDIT |  | true | true | true |
| 3 | Specify independent server authorization, upload authority revocation, consent ledger and durable deletion ledger | DESIGN_COMPLETE_ACCEPTANCE_BLOCKED | SATISFIED | Four independent checks, revocable DORA ingress and durable consent/deletion ledgers are specified and owner-accepted; no claim a running Transcribe job can be synchronously hard-cancelled. | OD-11C-12; AWS-DELETE-JOB |  | true | true | true |
| 4 | Bounded threat/design review dispositions RDY-011/012/013/018 and identifies testable controls, owners and residual risks; unresolved blocking risks prevent entry | DESIGN_REVIEW_INTERNAL_ONLY | PARTIALLY_SATISFIED | Bounded internal review dispositions all four RDY risks with controls/owners/residuals. RDY-012/013 design acceptance closed; legal/retention residuals in RDY-011/018 still prevent entry. | OD-11C-12; AWS-ORG; AWS-DELETE-JOB | SC-02 | false | false | true |
| 5 | Recording, offline use and Local ASR do not require a Cloud account | SATISFIED_BY_EXISTING_AUTHORITY | SATISFIED | Recording, offline use and installed Local ASR require no Cloud account/identity/network. | OD-11C-12 |  | true | false | true |

Required evidence (verbatim):

- Accepted bounded Alpha security/identity/key-custody design with reviewer and risk dispositions
- Approved consent/deletion ledger and ownership model; runtime proof remains separate

## 5. Data-flow and privacy inventory

| id | objects | destination | purpose | authorization | stored_as | responsibility |
|---|---|---|---|---|---|---|
| DF-01 | Opaque installation public-key reference, challenge proof, requested credential action; no account name/email/hardware ID | DORA identity/control boundary; invited installation only | Register a Cloud caller, issue/rotate/revoke credential; optional Cloud setup only | Explicit Cloud setup; local core never enrolls automatically; enrollment is not ASR consent | Protected identity binding and credential verifier; v0.2 lifecycle matrix; only enumerated RT-01/02 residuals | DORA authenticates Owner-authorized installation and issues/revokes credentials; qualified legal-role binding remains PC-01. |
| DF-02 | Disclosure version/digest, grant/revoke event, policy, exact selected recording snapshot, opaque source/action identifiers, language/encoding/size/checksum and prompt history needed for reconciliation | DORA control plane and consent ledger | Authorize precisely scoped original-audio ASR and deduplicate one logical action | Current informed grant plus authenticated owner; no audio before grant persistence; minimum metadata, no local file path/title/transcript | Consent/job/identity metadata; v0.2 lifecycle matrix; only enumerated RT-01/02 residuals | DORA independently validates ownership, purpose, destination and current grant; IDs/hashes remain sensitive, not anonymous |
| DF-03 | Original audio bytes/technical chunks and bound checksum/length; no Local transcript or edits | DORA revocation-enforcing ingress -> private same-account eu-central-1 S3 transient input with customer KMS | Temporary authorized input for RU/EN file ASR | Separate upload authorization; exact owner/source/job/part/consent epoch; validate before every chunk/retry; no public object or reusable unrevocable bearer URL | Transient input audio, no separate canonical Cloud original; immediate success cleanup; hard 24-hour maximum from first Cloud byte. | DORA controls access/lifecycle; AWS infrastructure processor/subprocessor role subject to PC-01 qualified applicability; AWS fact package supplied |
| DF-04 | Bound original audio, encoding/language/configuration, opaque provider correlation; only adapter-required minimum | DORA job dispatcher -> candidate standard Amazon Transcribe batch in eu-central-1 | Perform the authorized ASR job | Fresh processing authorization; exact provider/region scope; no training/human-review or unrelated processing grants | Provider service-function copies, terminal-deletable job record and explicit customer S3 output; current AWS facts and v0.2 lifecycle apply. Internal-audio deadline compatibility remains RT-01. | DORA checks consent/job/deletion epoch before dispatch; Transcribe necessarily reads audio. Mandatory effective opt-out before audio. No guaranteed hard-cancel/internal 24-hour erasure proven (RT-01). |
| DF-05 | Normalized transcript, timestamps/availability, job/provenance/status/error class and result integrity metadata | Transcribe -> explicit eu-central-1 DORA S3 transient output -> DORA durable accepted transcript -> owning installation | Return immutable Cloud result; validate source/generation and preserve edits locally | Provider-response authenticity and job association; independent retrieval authorization; deleted/stale generations cannot publish | Accepted DORA transcript until explicit deletion; transient output cleaned after verified durable ingestion. Local edits are not uploaded. Failed-output maximum remains RT-01. | DORA validates output, access and local handoff; provider receipt is not local activation |
| DF-06 | Cancel/revoke/delete request, exact scope and opaque operation/receipt references; result-received acknowledgement | DORA consent/deletion ledgers -> relevant storage/provider -> authenticated installation | Stop new work, reconcile deletion and disclose holder-specific results | Independent delete permission; revoked processing consent never blocks a separately authenticated erasure/status request; no new inference | Durable non-content consent/deletion evidence; v0.2 lifecycle matrix; only enumerated RT-01/02 residuals | DORA owns outbox/retries/status; each holder attests only its own disclosed scope |
| DF-07 | Source network address and connection metadata observable to serving network/TLS endpoints; minimal request version, size/duration counters and categorical status | DORA/AWS network and operational boundaries | Connection delivery, quota/abuse protection and content-free operation | Disclose unavoidable metadata; disable content capture at ingress; diagnostics exporter remains separate opt-in; no GPS, device serial or account contacts | Operational/audit records only per approved v0.2 lifecycle matrix; only enumerated RT-01/02 residuals; no body/token/URL logging | DORA content-free logging; AWS service network/admin metadata disclosed. Single content-processing region does not imply all global IAM/Organizations metadata is Frankfurt-local; no source-IP/VPN legal inference. |
| DF-08 | Explicitly selected export text/audio/file or previewed content-free diagnostics | User-selected clipboard, external app or storage destination | User-requested export/support action, outside ASR pipeline | Separate DEC-017 confirmation/warning each transfer; diagnostics scope separate; ASR consent grants neither | External copies controlled by recipient; DORA-managed temp stays under DEC-017 | DORA discloses loss of control; recipient may use network independently; not a Cloud-ASR grant |

- DF-01..08 remain the outbound allowlist: original audio and minimum technical/consent/job metadata only. No local transcript/edit history as input, unrelated artifacts, summaries/embeddings/contacts or model-training payload.
- Closed internal invitation-only Alpha for Owner-authorized testers. Owner access approval is neither recording participant permission nor qualified legal approval. Actual legal responsibility/contact and applicable lawful processing remain the PC-01 sign-off record.
- Content path is one region, eu-central-1, with no fallback or migration. Transcribe, S3, KMS and CloudTrail endpoint capabilities are documented. Global administrative IAM/Organizations metadata and contract/service-law exceptions are disclosed; this is not an all-AWS-data-residency guarantee.
- AWS-DPA documents processor-to-customer roles; DORA retains operational responsibility for purpose, disclosure, independent authorization, minimization, erasure/status and incident handling. Actual contractual/legal applicability is not inferred from VPN, IP, language or chosen region.
- Frankfurt infrastructure list identifies A100 ROW GmbH. AWS improvement entities are excluded through mandatory effective opt-out; support content sharing is not authorized. Necessary authorized service access is disclosed; no zero-human-access promise. Unrelated human content review is not ASR consent.
- Provider/region/purpose/subprocessor/retention profile is bound to disclosure and grant. Any changed scope requires compatibility review and fresh consent where expanded; no silent widening. Published AWS list/version and selected configuration must be revalidated later.
- Revocation stops new client bytes immediately and server authorization after acknowledgement; offline intent cannot instantly reach the server. Fence DORA ingress and dispatch. Existing Transcribe work may continue because no hard-cancel guarantee is established; do not imply revocation retroactively erases data.
- Local deletion is not remote deletion evidence; remote deletion has an independently confirmed scope. Receipts list exact objects/jobs/parts and exclusions; service-internal replicas/backups are not certified by API success.
- Recording/offline/installed Local require no Cloud account. Missing original prevents reprocessing, never substitutes local text. Explicit exports/diagnostics remain separately authorized under DEC-017; recipient copies remain outside DORA deletion control.

### User-visible disclosure 0.2

Finished unavailable-state copy. No active grant button and no placeholder active consent. Qualified approval and exact residual periods remain required.

#### CLOUD-DISCLOSURE-RU-0.2

Облачная расшифровка ещё не включена. Первая Alpha закрытая: доступ получают владелец проекта и лично приглашённые им тестировщики. Для облачной расшифровки выбран кандидат Amazon Transcribe через инфраструктуру DORA в регионе AWS Europe (Frankfurt), eu-central-1. Автоматического перехода в другой регион нет; VPN не определяет регион или юридические условия. Сервис ещё не прошёл технический допуск.

Для расшифровки предполагается передавать исходное аудио и минимальные сведения об установке, задании, языке, формате, размере, целостности, разрешении и статусе. Локальные тексты и правки для этого не отправляются. DORA управляет доступом и хранением своего результата; AWS обрабатывает запрос. В опубликованном списке инфраструктуры Frankfurt указана A100 ROW GmbH. У AWS есть глобальные административные сервисы; выбор Frankfurt не означает, что любые служебные сведения AWS находятся только там. Перед включением облака должны быть утверждены ответственные стороны и юридические условия.

Оригинал на устройстве хранится до вашего явного удаления; автоматическое удаление выключено. Облачное рабочее аудио должно удаляться сразу после успешной расшифровки и надёжного сохранения результата DORA; предельное время любой неуспешной или незавершённой обработки — 24 часа от начала хранения в облаке. Это требование DORA, а не подтверждённая гарантия для всех внутренних копий Amazon Transcribe. Поэтому отправка остаётся выключенной. Временный файл результата удаляется после надёжного приёма DORA; принятый текст, версии и правки сохраняются отдельно до явного удаления.

AWS указывает автоматический срок job records 90 дней, но завершённое задание можно удалить раньше; DORA должна делать это после приёма результата и сверки очистки. Это не срок хранения аудио. Отдельная история управляющих событий AWS CloudTrail содержит последние 90 дней; она не является копией аудио. Сроки отдельных журналов DORA, служебных записей и неаудио резервных копий ещё требуют решения. DORA не использует долгосрочный облачный архив аудио, межрегиональные копии или версии S3 для сохранения удалённого аудио.

Разрешение на ASR не разрешает обучение, улучшение сервиса, постороннюю обработку или просмотр содержимого людьми. Перед первой отправкой обязательно подтверждается действующий AWS opt-out для Transcribe. Он не означает удаления данных, необходимых самому сервису для обработки. AWS допускает необходимый доступ уполномоченных сотрудников; DORA не разрешает передачу содержимого в поддержку или отдельный human review. Обработчик должен читать аудио: это не сквозное шифрование, скрывающее его от сервиса.

Отзыв разрешения останавливает новые отправки; без связи серверный отзыв ожидает доставки и подтверждения. Уже начатая обработка у поставщика может продолжаться. Отзыв не удаляет ранее отправленные данные. Удаление запрашивается отдельно, а локальное удаление не доказывает удаление в облаке. Подтверждение относится только к указанным объектам и заданиям; стирание всех внутренних резервных копий поставщика этим не доказано.

Удаление только аудио сохраняет принятые расшифровки, версии и правки. Удаление всего разговора охватывает локальное аудио, тексты, версии, правки, производные данные, поиск и связанные кэши; облачные копии удаляются в отдельно подтверждённой области. Внешние экспорты остаются у получателей, правила временного экспорта DORA не меняются. Запись, работа офлайн и установленное локальное распознавание не требуют облачного аккаунта. Сейчас разрешение на отправку не запрашивается.

Actions: Понятно, Продолжить без облака. Grant enabled: **false**.

#### CLOUD-DISCLOSURE-EN-0.2

Cloud transcription is not enabled yet. The first Alpha is closed to the Project Owner and individually invited testers. Amazon Transcribe is the candidate service behind DORA in AWS Europe (Frankfurt), eu-central-1. There is no automatic region fallback; VPN routing determines neither region policy nor legal conditions. Technical admission has not been completed.

The proposed flow sends original audio plus minimal installation, job, language, format, size, integrity, permission and status information. Local transcripts and edits are not sent as transcription input. DORA controls access and its durable result; AWS processes the request. AWS lists A100 ROW GmbH for Frankfurt infrastructure. AWS also has global administrative services; Frankfurt is not a promise that all AWS metadata stays there. Responsible parties and legal applicability must be approved before enabling Cloud.

The local original remains until explicit deletion; automatic deletion is off. Cloud working audio must be removed immediately after successful transcription and durable DORA result ingestion. Unsuccessful or unfinished processing has a maximum of 24 hours from first Cloud storage. This is DORA policy, not a verified guarantee for every internal Transcribe copy; uploads therefore remain disabled. Transient output is deleted after durable DORA ingestion; accepted transcripts, versions and edits remain separately until explicit deletion.

AWS documents automatic job-record retention of 90 days, but terminal jobs can be deleted earlier; DORA must do so after result ingestion and cleanup reconciliation. This is not audio retention. Separately, AWS CloudTrail management-event history covers 90 days and is not an audio copy. Some DORA log/metadata periods and non-audio backup policies still require decisions. DORA prohibits long-term Cloud audio archives, cross-region audio copies and S3 versions that preserve deleted transient audio.

ASR permission does not authorize training, service improvement, unrelated processing or human content review. Effective AWS Transcribe opt-out must be verified before any audio. Opt-out does not erase data required to provide service functions. AWS allows necessary authorized employee access; DORA does not authorize support-content sharing or a separate human-review workflow. The processor must read the audio, so this is not end-to-end encryption hiding it from the service.

Revocation stops new sends; offline server revocation waits for delivery and acknowledgement. Provider work already started may continue. Revocation does not erase prior transfers. Request deletion separately; local deletion is not proof of Cloud deletion. Receipts cover only declared objects/jobs, not certified erasure of all provider-internal backups.

Audio-only deletion preserves accepted transcripts, versions and edits. Whole-recording deletion covers local audio, text, versions, edits, derivatives, search and associated caches, with Cloud copies handled in a separately confirmed scope. External exports remain with recipients; DORA export-temp rules are unchanged. Recording, offline use and installed Local ASR need no Cloud account. No upload permission is requested now.

Actions: Understood, Continue without Cloud. Grant enabled: **false**.

Release conditions:

- PC-01 qualified actual-party/contact/role/applicability approval and signed reviewed version
- RT-01 internal-audio deadline compatibility and exact remaining non-content/failed-output periods
- RT-02 non-audio copies and tombstone/receipt retirement policy
- Separate provider/runtime admission including effective opt-out, endpoint/role/key configuration and tested consent/deletion enforcement

## 6. Per-artifact/per-holder lifecycle

All 56 prior cells are retained and reassessed. Each row includes one full semantics profile below; JSON expands every profile field into every row. Aliases do not represent extra canonical copies. NOT_STORED is design policy, not an observed AWS inventory.

| Field | Audio deadline policy |
|---|---|
| authority | OD-11C-04; OD-11C-06; OD-11C-09 |
| clock_origin | First accepted Cloud audio byte, persisted durably before upload; inherited by every part/copy/retry/job of that attempt. |
| success_trigger | Validated transcript committed durably in DORA; client acknowledgement not required to retain working audio. |
| max_hours | 24 |
| runtime_status | NOT_RUN |
| policy_breach_handling | At original deadline, fence all DORA audio writes/dispatch; explicit delete and multipart reconciliation; no retry resets deadline. Cleanup must be scheduled early enough for verified completion; independent deadline watchdog and lifecycle backstop. Unconfirmed deadline outcome is an incident, not success or an authorized grace period. |
| provider_limit | DeleteTranscriptionJob can reject active jobs; deleting input/revoking role does not prove already-read provider bytes disappeared. No hard-cancel/24-hour internal-media guarantee found. RT-01 blocks admission until compatibility is documented. |
| receipt_limits | S3 logical namespace absence and job terminal deletion are scoped evidence. No physical-storage overwrite or provider-backup certificate is claimed. |

### Semantics profile LOCAL_CONTENT

| Field | Required behavior |
|---|---|
| deletion_propagation | Use OP-EXPLICIT-AUDIO or OP-EXPLICIT-CONVERSATION with local/remote axes separate; reconcile source jobs and unsent uploads before erasure. No remote request without its own approved scope. |
| retry_failure | Persist exact completed/remaining category bitmap before irreversible steps; retry only remaining idempotent steps; failure remains visible. |
| receipt_semantics | Local inventory reconciliation only; no physical overwrite promise; crypto-erasure only with verified per-artifact key deletion. |
| backup_replica_behavior | Private DB/audio/keysets excluded from Android Auto Backup/device transfer per Technical §24.2; existing external exports are not remotely controlled. |
| late_callback_behavior | Reject cancelled/deleted generations and prevent new publication; audio-only deletion preserves existing transcript/edit objects. |
| restore_suppression | Tombstone/source-unavailable state outranks delayed work. Reinstall/key loss cannot be treated as a successful restoration. |
| source_unavailable_behavior | Use USER_DELETED, RETENTION_DELETED, MISSING, CORRUPT or KEY_UNAVAILABLE accurately; no transcript-as-audio fallback. |

### Semantics profile REMOTE_CONTENT

| Field | Required behavior |
|---|---|
| deletion_propagation | Persist scoped tombstone/outbox; fence DORA writers and dispatch, explicitly delete customer S3 objects, reconcile multipart parts and terminal Transcribe jobs separately. Provider internal copies are an explicitly unverified category, not silently marked erased. |
| retry_failure | No retry extends the original 24-hour audio deadline. Independent deadline worker plus explicit delete; lifecycle is only a safety net. Unknown outcome remains pending/failed. Breach is a policy incident and blocks new Cloud audio, not a retention extension. |
| receipt_semantics | Customer object/part absence plus authenticated operation response covers that namespace only. Job delete receipt covers the named terminal job. No assertion of internal-media/backup physical purge. |
| backup_replica_behavior | No DORA long-term audio backup, archival tier, cross-region replication or audio-preserving versioning. Provider internal same-region replicas exist; physical purge timing not established. Non-audio copies follow RT-02. |
| late_callback_behavior | Tombstone/deadline fences publication. Late result is erased within confirmed scope; retain pending cleanup and never recreate recording or restart ASR. Already running managed service work is not claimed instantly cancelled. |
| restore_suppression | Recover authoritative deletion ledger first; reconcile every restored object against tombstones before serving or processing. Unknown/stale ledger fails closed. |
| source_unavailable_behavior | Never reconstruct original from transcript; cancel dependent ASR with explicit source reason. Separately authorized deletion/status work survives source loss. |

### Semantics profile LEDGER

| Field | Required behavior |
|---|---|
| deletion_propagation | Keep only approved minimum non-content scope/receipt and suppression record; erase linkage/records only after policy and suppression obligations permit it. |
| retry_failure | Atomic durable append/outbox and idempotent transition; never acknowledge revoke/delete before durable acceptance; unavailable ledger denies processing, not local operation. |
| receipt_semantics | Acknowledgement of ledger persistence is distinct from holder deletion receipt; show pending/failed/receipt separately. |
| backup_replica_behavior | RT-02 defines ledger replica/backup and suppression horizon; stale restores cannot shorten it. No indefinite-retention assumption. |
| late_callback_behavior | Use monotonic recording/deletion epoch; a late result cannot clear a tombstone or restore a grant. |
| restore_suppression | Authoritative tombstone/revocation state must be at least as current as restored data; otherwise quarantine until reconciliation. |
| source_unavailable_behavior | Opaque operation status remains retrievable under ownership control even when audio is absent; no content retained as proof. |

### Semantics profile LOG

| Field | Required behavior |
|---|---|
| deletion_propagation | Apply approved content-free log lifecycle and applicable erasure/redaction; no audio/text/body/token/private URL or object-key retention in diagnostics. |
| retry_failure | Track purge failure as operational action; do not hide failure behind log rotation or export it with content. |
| receipt_semantics | Document bounded log categories/time intervals removed; no claim about unapproved provider-internal logs. |
| backup_replica_behavior | the enumerated v0.2 RT-01/02 residuals must include every log sink, archive and replica; forbid unapproved sink export. |
| late_callback_behavior | Log only categorical stale-result event, never payload or persistent recording identifier in general logs. |
| restore_suppression | Apply expiry/purge ledger before making archived logs available. |
| source_unavailable_behavior | No original or transcript reconstruction; logs cannot be used as a content recovery channel. |

### Semantics profile COPY_SYSTEM

| Field | Required behavior |
|---|---|
| deletion_propagation | Apply confirmed deletion scope across inventories, object versions, replicas and backup restores; backup-specific limitations remain RT-02. |
| retry_failure | Retry tracked incomplete holder categories; copy-unreachable or unknown inventory blocks complete claim. |
| receipt_semantics | Receipt states live replicas versus backups separately, with verified coverage and any expiry still pending. |
| backup_replica_behavior | Every copy must have approved source categories, location, key custody, period and restore procedure; not configured by this package. |
| late_callback_behavior | Fence replica writers and reject stale generations; later discovered copy joins the original deletion operation. |
| restore_suppression | Restore deletion/revocation ledger first and reconcile before availability; unreadable/stale ledger blocks serving. |
| source_unavailable_behavior | Never resurrect user-deleted audio or fabricate a missing source; source-loss taxonomy stays exact. |

### Semantics profile NO_STORAGE

| Field | Required behavior |
|---|---|
| deletion_propagation | No permitted copy in this holder; discovery is a scope violation, stop transfer and create a scoped remediation/deletion record. |
| retry_failure | Do not create the forbidden copy on retry; unexpected-copy remediation stays tracked until verified. |
| receipt_semantics | Not applicable is a design prohibition, not evidence that deployed storage is empty. |
| backup_replica_behavior | Excluded with its source; no backup or replica may introduce this data. |
| late_callback_behavior | Reject any callback attempting to create the prohibited artifact. |
| restore_suppression | Do not restore an excluded artifact; quarantine unexpected copies. |
| source_unavailable_behavior | No substitution or reconstruction from this holder. |

| id | artifact | holder | stored | purpose | retention_trigger | period_or_unresolved_decision | delete_trigger | semantics_profile | policy_authority | aws_evidence_ids | blocking_decision_ids |
|---|---|---|---|---|---|---|---|---|---|---|---|
| LC-01-1 | original_audio | ANDROID_LOCAL | YES_CANONICAL | Retain verifiable source and permit explicitly authorized ASR/reprocessing. | Local artifact creation / recording lifecycle; pending upload does not shorten local original lifetime. | Until explicit scoped deletion; audio-only preserves accepted text/versions/edits. Local automatic audio retention OFF. Related caches follow their artifact deletion; DEC-017 export-temp exception is unchanged. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOCAL_CONTENT | OD-11C-04; OD-11C-05 |  |  |
| LC-01-2 | original_audio | DORA_CONTROL_PLANE | ALIAS_UPLOAD_OBJECT | Retain verifiable source and permit explicitly authorized ASR/reprocessing. | First Cloud-accepted audio byte; durable ingress deadline persisted before accepting bytes. | OWNER_APPROVED: delete immediately after successful transcription plus verified durable DORA result ingestion; otherwise delete no later than 24 hours from first Cloud-accepted audio byte. Deadline never resets on retry, restart, copy or job resubmission; 24 hours is a ceiling, not a minimum. | Success + durable DORA ingestion -> immediate cleanup; explicit remote delete/cancel/abandonment -> cleanup; original 24-hour deadline -> mandatory deletion even if provider job is unfinished. | REMOTE_CONTENT | OD-11C-04; OD-11C-06 | AWS-INPUT; AWS-S3-DELETE; AWS-S3-LIFECYCLE; AWS-MULTIPART |  |
| LC-01-3 | original_audio | AWS_PROVIDER | SERVICE_FUNCTION_COPIES_POSSIBLE; IMPROVEMENT_COPIES_PROHIBITED_BY_REQUIRED_OPT_OUT | Retain verifiable source and permit explicitly authorized ASR/reprocessing. | Provider ingestion/processing; same original audio deadline, not a separate clock. | OWNER_POLICY <=24h applies; PROVIDER_COMPATIBILITY_UNPROVEN (RT-01): reviewed sources do not bound service-function/internal audio copy lifetime or guarantee stopping a non-terminal job. Effective opt-out excludes improvement use only. No claim of known >24h retention; absence of a documented bound is not proof of incompatibility, and admission remains blocked. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | REMOTE_CONTENT | OD-11C-04; OD-11C-07; OD-11C-10 | AWS-ORG; AWS-FAQ; AWS-DELETE-JOB | RT-01 |
| LC-01-4 | original_audio | BACKUP_REPLICA_SYSTEMS | NO_DORA_LONG_TERM_ARCHIVE_VERSION_OR_CROSS_REGION_COPY; PROVIDER_INTERNAL_REPLICAS_NOT_ZERO | Retain verifiable source and permit explicitly authorized ASR/reprocessing. | No DORA audio backup creation allowed; AWS-internal replica follows source processing. | DORA forbidden copies: NOT_STORED. Provider-internal audio: OWNER_POLICY <=24h applies; PROVIDER_COMPATIBILITY_UNPROVEN (RT-01): reviewed sources do not bound service-function/internal audio copy lifetime or guarantee stopping a non-terminal job. Effective opt-out excludes improvement use only. No claim of known >24h retention; absence of a documented bound is not proof of incompatibility, and admission remains blocked. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10 | AWS-S3-DELETE; AWS-ORG | RT-01 |
| LC-02-1 | upload_objects | ANDROID_LOCAL | LOCAL_PENDING_SOURCE_NO_EXTRA_CANONICAL | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. | Local artifact creation / recording lifecycle; pending upload does not shorten local original lifetime. | Until explicit scoped deletion; audio-only preserves accepted text/versions/edits. Local automatic audio retention OFF. Related caches follow their artifact deletion; DEC-017 export-temp exception is unchanged. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOCAL_CONTENT | OD-11C-04; OD-11C-05 |  |  |
| LC-02-2 | upload_objects | DORA_CONTROL_PLANE | YES_TEMPORARY | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. | First Cloud-accepted audio byte; durable ingress deadline persisted before accepting bytes. | OWNER_APPROVED: delete immediately after successful transcription plus verified durable DORA result ingestion; otherwise delete no later than 24 hours from first Cloud-accepted audio byte. Deadline never resets on retry, restart, copy or job resubmission; 24 hours is a ceiling, not a minimum. | Success + durable DORA ingestion -> immediate cleanup; explicit remote delete/cancel/abandonment -> cleanup; original 24-hour deadline -> mandatory deletion even if provider job is unfinished. | REMOTE_CONTENT | OD-11C-04; OD-11C-06 | AWS-INPUT; AWS-S3-DELETE; AWS-S3-LIFECYCLE; AWS-MULTIPART |  |
| LC-02-3 | upload_objects | AWS_PROVIDER | YES_INFRASTRUCTURE_UNDER_DORA | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. | First Cloud-accepted audio byte; durable ingress deadline persisted before accepting bytes. | OWNER_APPROVED: delete immediately after successful transcription plus verified durable DORA result ingestion; otherwise delete no later than 24 hours from first Cloud-accepted audio byte. Deadline never resets on retry, restart, copy or job resubmission; 24 hours is a ceiling, not a minimum. | Success + durable DORA ingestion -> immediate cleanup; explicit remote delete/cancel/abandonment -> cleanup; original 24-hour deadline -> mandatory deletion even if provider job is unfinished. | REMOTE_CONTENT | OD-11C-04; OD-11C-06 | AWS-INPUT; AWS-S3-DELETE; AWS-S3-LIFECYCLE; AWS-MULTIPART |  |
| LC-02-4 | upload_objects | BACKUP_REPLICA_SYSTEMS | NO_DORA_LONG_TERM_ARCHIVE_VERSION_OR_CROSS_REGION_COPY; PROVIDER_INTERNAL_REPLICAS_NOT_ZERO | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. | No DORA audio backup creation allowed; AWS-internal replica follows source processing. | DORA forbidden copies: NOT_STORED. Provider-internal audio: OWNER_POLICY <=24h applies; PROVIDER_COMPATIBILITY_UNPROVEN (RT-01): reviewed sources do not bound service-function/internal audio copy lifetime or guarantee stopping a non-terminal job. Effective opt-out excludes improvement use only. No claim of known >24h retention; absence of a documented bound is not proof of incompatibility, and admission remains blocked. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10 | AWS-S3-DELETE; AWS-ORG | RT-01 |
| LC-03-1 | provider_temporary_copies | ANDROID_LOCAL | NO | Bounded ASR execution only; no service improvement/training or human review. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-03-2 | provider_temporary_copies | DORA_CONTROL_PLANE | NO | Bounded ASR execution only; no service improvement/training or human review. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-03-3 | provider_temporary_copies | AWS_PROVIDER | SERVICE_FUNCTION_COPIES_POSSIBLE; IMPROVEMENT_COPIES_PROHIBITED_BY_REQUIRED_OPT_OUT | Bounded ASR execution only; no service improvement/training or human review. | Provider ingestion/processing; same original audio deadline, not a separate clock. | OWNER_POLICY <=24h applies; PROVIDER_COMPATIBILITY_UNPROVEN (RT-01): reviewed sources do not bound service-function/internal audio copy lifetime or guarantee stopping a non-terminal job. Effective opt-out excludes improvement use only. No claim of known >24h retention; absence of a documented bound is not proof of incompatibility, and admission remains blocked. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | REMOTE_CONTENT | OD-11C-04; OD-11C-07; OD-11C-10 | AWS-ORG; AWS-FAQ; AWS-DELETE-JOB | RT-01 |
| LC-03-4 | provider_temporary_copies | BACKUP_REPLICA_SYSTEMS | SERVICE_FUNCTION_COPIES_POSSIBLE; IMPROVEMENT_COPIES_PROHIBITED_BY_REQUIRED_OPT_OUT | Bounded ASR execution only; no service improvement/training or human review. | Provider ingestion/processing; same original audio deadline, not a separate clock. | OWNER_POLICY <=24h applies; PROVIDER_COMPATIBILITY_UNPROVEN (RT-01): reviewed sources do not bound service-function/internal audio copy lifetime or guarantee stopping a non-terminal job. Effective opt-out excludes improvement use only. No claim of known >24h retention; absence of a documented bound is not proof of incompatibility, and admission remains blocked. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-04; OD-11C-07; OD-11C-10 | AWS-ORG; AWS-FAQ; AWS-DELETE-JOB | RT-01 |
| LC-04-1 | provider_jobs_metadata | ANDROID_LOCAL | MINIMAL_LOCAL_JOB_MAPPING | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. | Explicit Cloud enrollment/grant/job/deletion event; retain minimum ownership/status binding; revocation invalidates authority independently of record retention. | OWNER_APPROVED durable minimal ledger and no resurrection. Preserve active grants/operations and pending cleanup; RT-01 residual: approve exact post-operation retention. RT-02 residual: retirement must follow verified absence of every restorable copy/job/callback; no arbitrary TTL or indefinite retention approval. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOCAL_CONTENT | OD-11C-10; OD-11C-11; OD-11C-12 |  | RT-01; RT-02 |
| LC-04-2 | provider_jobs_metadata | DORA_CONTROL_PLANE | YES_MINIMAL_JOB_MAPPING | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. | Explicit Cloud enrollment/grant/job/deletion event; retain minimum ownership/status binding; revocation invalidates authority independently of record retention. | OWNER_APPROVED durable minimal ledger and no resurrection. Preserve active grants/operations and pending cleanup; RT-01 residual: approve exact post-operation retention. RT-02 residual: retirement must follow verified absence of every restorable copy/job/callback; no arbitrary TTL or indefinite retention approval. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | REMOTE_CONTENT | OD-11C-10; OD-11C-11; OD-11C-12 |  | RT-01; RT-02 |
| LC-04-3 | provider_jobs_metadata | AWS_PROVIDER | YES_PROVIDER_JOB | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. | Transcribe job creation; terminal success/failure permits explicit DeleteTranscriptionJob. | 90-day non-adjustable automatic job-record quota; earlier explicit deletion supported. DORA deletes terminal record after durable ingestion/failure and cleanup reconciliation. TRANSCRIBE_JOB_RECORD = PROVIDER_90_DAY_AUTO_EXPIRY_EARLIER_DELETE_REQUIRED; blanket PROVIDER_MANDATED_90_DAYS is not justified because OD-11C-08 condition 3 fails for deletable jobs. | Terminal job + accepted result/cleanup reconciliation or confirmed user deletion; non-terminal rejection remains visible and retried when terminal, with RT-01 audio ceiling independently enforced. | REMOTE_CONTENT | OD-11C-08 | AWS-REGION; AWS-DELETE-JOB; AWS-OUTPUT |  |
| LC-04-4 | provider_jobs_metadata | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-05-1 | transcripts | ANDROID_LOCAL | YES_CANONICAL_VERSION | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. | Local artifact creation / recording lifecycle; pending upload does not shorten local original lifetime. | Until explicit scoped deletion; audio-only preserves accepted text/versions/edits. Local automatic audio retention OFF. Related caches follow their artifact deletion; DEC-017 export-temp exception is unchanged. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOCAL_CONTENT | OD-11C-04; OD-11C-05 |  |  |
| LC-05-2 | transcripts | DORA_CONTROL_PLANE | YES_DISTINCT_DORA_DURABLE_ACCEPTED_RESULT_AND_TRANSIENT_INGESTION | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. | Durable validated result commit creates accepted DORA transcript; receipt loss does not delay audio deletion. | Accepted DORA transcript/required versions: until explicit user whole-recording/text deletion; audio-only preserves them. Temporary unaccepted/error output must be cleaned on reconciliation; RT-01 residual: approve maximum when durable ingestion never succeeds. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | REMOTE_CONTENT | OD-11C-05; OD-11C-09 |  | RT-01 |
| LC-05-3 | transcripts | AWS_PROVIDER | DORA_CUSTOMER_S3_TRANSIENT_OUTPUT; SERVICE_MANAGED_OUTPUT_NOT_SELECTED | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. | Output object creation; successful durable DORA transcript ingestion triggers immediate deletion. | Customer S3 output: remove immediately after durable DORA ingestion; failed/unaccepted output maximum remains RT-01. Service-managed output is NOT_STORED by intended configuration; accidental creation is a policy incident. Provider-internal result remnants have no published per-copy purge deadline in reviewed sources. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | REMOTE_CONTENT | OD-11C-05; OD-11C-09 | AWS-START; AWS-OUTPUT; AWS-URI | RT-01 |
| LC-05-4 | transcripts | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-06-1 | transcript_versions_edits | ANDROID_LOCAL | YES_CANONICAL_HISTORY | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. | Local artifact creation / recording lifecycle; pending upload does not shorten local original lifetime. | Until explicit scoped deletion; audio-only preserves accepted text/versions/edits. Local automatic audio retention OFF. Related caches follow their artifact deletion; DEC-017 export-temp exception is unchanged. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOCAL_CONTENT | OD-11C-04; OD-11C-05 |  |  |
| LC-06-2 | transcript_versions_edits | DORA_CONTROL_PLANE | NO_USER_EDITS_OR_LOCAL_HISTORY | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-06-3 | transcript_versions_edits | AWS_PROVIDER | NO_USER_EDITS_OR_LOCAL_HISTORY | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-06-4 | transcript_versions_edits | BACKUP_REPLICA_SYSTEMS | NO_USER_EDITS_OR_LOCAL_HISTORY | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-07-1 | caches | ANDROID_LOCAL | YES_SCOPED | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. | Local artifact creation / recording lifecycle; pending upload does not shorten local original lifetime. | Until explicit scoped deletion; audio-only preserves accepted text/versions/edits. Local automatic audio retention OFF. Related caches follow their artifact deletion; DEC-017 export-temp exception is unchanged. DEC-017 export-temp one-hour maximum is inherited only for that temp, never generalized to original audio, Cloud objects or other caches. Preserve its safe-release / warned Delete-now / one-hour / startup cleanup lifecycle; whole-recording deletion must not prematurely shorten that separately authorized export-temp lifecycle. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOCAL_CONTENT | OD-11C-04; OD-11C-05 |  |  |
| LC-07-2 | caches | DORA_CONTROL_PLANE | CONDITIONAL_TEMPORARY | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. | Original source deadline for audio; result creation for text. | Audio scratch/chunks: OWNER_APPROVED: delete immediately after successful transcription plus verified durable DORA result ingestion; otherwise delete no later than 24 hours from first Cloud-accepted audio byte. Deadline never resets on retry, restart, copy or job resubmission; 24 hours is a ceiling, not a minimum. Content debug/cache archives NOT_STORED. Non-audio unfinished result cache follows the explicit failed-output RT-01 decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | REMOTE_CONTENT | OD-11C-04; OD-11C-06; OD-11C-09; OD-11C-11 |  | RT-01 |
| LC-07-3 | caches | AWS_PROVIDER | SERVICE_FUNCTION_COPIES_POSSIBLE; IMPROVEMENT_COPIES_PROHIBITED_BY_REQUIRED_OPT_OUT | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. | Provider ingestion/processing; same original audio deadline, not a separate clock. | OWNER_POLICY <=24h applies; PROVIDER_COMPATIBILITY_UNPROVEN (RT-01): reviewed sources do not bound service-function/internal audio copy lifetime or guarantee stopping a non-terminal job. Effective opt-out excludes improvement use only. No claim of known >24h retention; absence of a documented bound is not proof of incompatibility, and admission remains blocked. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | REMOTE_CONTENT | OD-11C-04; OD-11C-07; OD-11C-10 | AWS-ORG; AWS-FAQ; AWS-DELETE-JOB | RT-01 |
| LC-07-4 | caches | BACKUP_REPLICA_SYSTEMS | NO_DORA_CONTENT_CACHE_BACKUP | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-08-1 | operational_logs | ANDROID_LOCAL | YES_CONTENT_FREE | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. | Categorical log/audit event; no request body, transcript, audio, credential or sensitive encryption context. | OWNER_APPROVED minimization/no audio/no content debug/no secrets. RT-01 residual: approve actual period and purge trigger for this content-free DORA log category; no 24-hour/90-day rule is generalized to it. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOG | OD-11C-11; OD-11C-12 |  | RT-01 |
| LC-08-2 | operational_logs | DORA_CONTROL_PLANE | YES_CONTENT_FREE | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. | Categorical log/audit event; no request body, transcript, audio, credential or sensitive encryption context. | OWNER_APPROVED minimization/no audio/no content debug/no secrets. RT-01 residual: approve actual period and purge trigger for this content-free DORA log category; no 24-hour/90-day rule is generalized to it. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOG | OD-11C-11; OD-11C-12 |  | RT-01 |
| LC-08-3 | operational_logs | AWS_PROVIDER | CONDITIONAL_SERVICE_LOG | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. | Provider service operational event. | Content-free operational/network metadata may exist. General provider-internal log period NOT_DOCUMENTED by reviewed Transcribe sources; no 90-day audio/job/log generalization. RT-01 needs bounded acceptance of the disclosed limitation. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOG | OD-11C-11 | AWS-TLS; AWS-FAQ; AWS-DPA | RT-01 |
| LC-08-4 | operational_logs | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-09-1 | audit_security_logs | ANDROID_LOCAL | YES_CONTENT_FREE | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. | Categorical log/audit event; no request body, transcript, audio, credential or sensitive encryption context. | OWNER_APPROVED minimization/no audio/no content debug/no secrets. RT-01 residual: approve actual period and purge trigger for this content-free DORA log category; no 24-hour/90-day rule is generalized to it. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOG | OD-11C-11; OD-11C-12 |  | RT-01 |
| LC-09-2 | audit_security_logs | DORA_CONTROL_PLANE | YES_RESTRICTED_CONTENT_FREE | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. | Categorical log/audit event; no request body, transcript, audio, credential or sensitive encryption context. | OWNER_APPROVED minimization/no audio/no content debug/no secrets. RT-01 residual: approve actual period and purge trigger for this content-free DORA log category; no 24-hour/90-day rule is generalized to it. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOG | OD-11C-11; OD-11C-12 |  | RT-01 |
| LC-09-3 | audit_security_logs | AWS_PROVIDER | CONTENT_FREE_SERVICE_OPERATION_METADATA; CLOUDTRAIL_MANAGEMENT_HISTORY | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. | Management event time; default AWS CloudTrail event history. | CloudTrail management-event history: immutable trailing 90 days, independently evidenced under OD-11C-08 metadata-only conditions. Not Transcribe-job-derived and not a DORA log/archive policy. Other provider-internal diagnostic/receipt copies: period NOT_DOCUMENTED in reviewed sources; RT-01 records this limitation for approval. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LOG | OD-11C-08; OD-11C-11 | AWS-CT-HISTORY; AWS-KMS-AUDIT | RT-01 |
| LC-09-4 | audit_security_logs | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-10-1 | consent_records | ANDROID_LOCAL | YES_PRIVATE_RECEIPT | Prove exact disclosure and selected grant/revocation state; no audio or transcript. | Explicit Cloud enrollment/grant/job/deletion event; retain minimum ownership/status binding; revocation invalidates authority independently of record retention. | OWNER_APPROVED durable minimal ledger and no resurrection. Preserve active grants/operations and pending cleanup; RT-01 residual: approve exact post-operation retention. RT-02 residual: retirement must follow verified absence of every restorable copy/job/callback; no arbitrary TTL or indefinite retention approval. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LEDGER | OD-11C-10; OD-11C-11; OD-11C-12 |  | RT-01; RT-02 |
| LC-10-2 | consent_records | DORA_CONTROL_PLANE | YES_AUTHORITATIVE_LEDGER | Prove exact disclosure and selected grant/revocation state; no audio or transcript. | Explicit Cloud enrollment/grant/job/deletion event; retain minimum ownership/status binding; revocation invalidates authority independently of record retention. | OWNER_APPROVED durable minimal ledger and no resurrection. Preserve active grants/operations and pending cleanup; RT-01 residual: approve exact post-operation retention. RT-02 residual: retirement must follow verified absence of every restorable copy/job/callback; no arbitrary TTL or indefinite retention approval. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LEDGER | OD-11C-10; OD-11C-11; OD-11C-12 |  | RT-01; RT-02 |
| LC-10-3 | consent_records | AWS_PROVIDER | NO_FULL_LEDGER | Prove exact disclosure and selected grant/revocation state; no audio or transcript. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-10-4 | consent_records | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_LEDGER_COPY | Prove exact disclosure and selected grant/revocation state; no audio or transcript. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-11-1 | deletion_records_receipts | ANDROID_LOCAL | YES_DURABLE_LOCAL_RECEIPT | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. | Explicit Cloud enrollment/grant/job/deletion event; retain minimum ownership/status binding; revocation invalidates authority independently of record retention. | OWNER_APPROVED durable minimal ledger and no resurrection. Preserve active grants/operations and pending cleanup; RT-01 residual: approve exact post-operation retention. RT-02 residual: retirement must follow verified absence of every restorable copy/job/callback; no arbitrary TTL or indefinite retention approval. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LEDGER | OD-11C-10; OD-11C-11; OD-11C-12 |  | RT-01; RT-02 |
| LC-11-2 | deletion_records_receipts | DORA_CONTROL_PLANE | YES_AUTHORITATIVE_LEDGER | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. | Explicit Cloud enrollment/grant/job/deletion event; retain minimum ownership/status binding; revocation invalidates authority independently of record retention. | OWNER_APPROVED durable minimal ledger and no resurrection. Preserve active grants/operations and pending cleanup; RT-01 residual: approve exact post-operation retention. RT-02 residual: retirement must follow verified absence of every restorable copy/job/callback; no arbitrary TTL or indefinite retention approval. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LEDGER | OD-11C-10; OD-11C-11; OD-11C-12 |  | RT-01; RT-02 |
| LC-11-3 | deletion_records_receipts | AWS_PROVIDER | CONTENT_FREE_SERVICE_OPERATION_METADATA; CLOUDTRAIL_MANAGEMENT_HISTORY | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. | Management event time; default AWS CloudTrail event history. | CloudTrail management-event history: immutable trailing 90 days, independently evidenced under OD-11C-08 metadata-only conditions. Not Transcribe-job-derived and not a DORA log/archive policy. Other provider-internal diagnostic/receipt copies: period NOT_DOCUMENTED in reviewed sources; RT-01 records this limitation for approval. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LEDGER | OD-11C-08; OD-11C-11 | AWS-CT-HISTORY; AWS-KMS-AUDIT | RT-01 |
| LC-11-4 | deletion_records_receipts | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_LEDGER_COPY | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-12-1 | backups | ANDROID_LOCAL | NO_PRIVATE_DB_AUDIO_KEYSETS | Only an explicitly approved recoverability policy; never an implicit permanent content copy. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-12-2 | backups | DORA_CONTROL_PLANE | CONDITIONAL_DORA_POLICY | Only an explicitly approved recoverability policy; never an implicit permanent content copy. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-12-3 | backups | AWS_PROVIDER | CONDITIONAL_PROVIDER_POLICY | Only an explicitly approved recoverability policy; never an implicit permanent content copy. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-12-4 | backups | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_EXPLICIT_INVENTORY | Only an explicitly approved recoverability policy; never an implicit permanent content copy. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-13-1 | replicas | ANDROID_LOCAL | NO_EXTRA_REPLICA | Only explicitly approved availability copies under the same purpose and deletion scope. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-13-2 | replicas | DORA_CONTROL_PLANE | CONDITIONAL_DORA_POLICY | Only explicitly approved availability copies under the same purpose and deletion scope. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-13-3 | replicas | AWS_PROVIDER | CONDITIONAL_PROVIDER_POLICY | Only explicitly approved availability copies under the same purpose and deletion scope. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-13-4 | replicas | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_EXPLICIT_INVENTORY | Only explicitly approved availability copies under the same purpose and deletion scope. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |
| LC-14-1 | identity_credentials | ANDROID_LOCAL | YES_PRIVATE_KEY_AND_CREDENTIAL | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. | Explicit Cloud enrollment/grant/job/deletion event; retain minimum ownership/status binding; revocation invalidates authority independently of record retention. | OWNER_APPROVED durable minimal ledger and no resurrection. Preserve active grants/operations and pending cleanup; RT-01 residual: approve exact post-operation retention. RT-02 residual: retirement must follow verified absence of every restorable copy/job/callback; no arbitrary TTL or indefinite retention approval. Credential validity itself must be finite and key/epoch-bound; exact access/refresh/nonce/lease values are implementation profile evidence at AUTH/UPLOAD, not an absent Stage 0 owner acceptance. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LEDGER | OD-11C-10; OD-11C-11; OD-11C-12 |  | RT-01; RT-02 |
| LC-14-2 | identity_credentials | DORA_CONTROL_PLANE | YES_IDENTITY_AND_PROTECTED_VERIFIER | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. | Explicit Cloud enrollment/grant/job/deletion event; retain minimum ownership/status binding; revocation invalidates authority independently of record retention. | OWNER_APPROVED durable minimal ledger and no resurrection. Preserve active grants/operations and pending cleanup; RT-01 residual: approve exact post-operation retention. RT-02 residual: retirement must follow verified absence of every restorable copy/job/callback; no arbitrary TTL or indefinite retention approval. Credential validity itself must be finite and key/epoch-bound; exact access/refresh/nonce/lease values are implementation profile evidence at AUTH/UPLOAD, not an absent Stage 0 owner acceptance. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | LEDGER | OD-11C-10; OD-11C-11; OD-11C-12 |  | RT-01; RT-02 |
| LC-14-3 | identity_credentials | AWS_PROVIDER | NO_DORA_CALLER_SECRET | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. | No creation permitted | NOT_APPLICABLE / NOT_STORED in the approved bounded Alpha flow; no new copy or retention right. | No storage allowed; remediate unexpected copy | NO_STORAGE | OD-11C-05; OD-11C-06; OD-11C-11; OD-11C-12 |  |  |
| LC-14-4 | identity_credentials | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_SERVER_IDENTITY_RECORD_NO_DEVICE_PRIVATE_KEY | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. | Only explicitly approved non-audio copy creation; forbid long-term audio backup/archive/cross-region copy and deleted-audio version preservation. | Audio copies controlled by DORA: prohibited as above; active same-region service replication is disclosed, with audio deadline compatibility in RT-01. Non-audio transcript/ledger/log/identity copies: RT-02 residual is approved inclusion and maximum restorable horizon; no such optional copy may be enabled without that decision. | Explicit confirmed artifact/holder scope or approved lifecycle expiry; local and remote axes remain independent. | COPY_SYSTEM | OD-11C-06; OD-11C-10; OD-11C-12 | AWS-S3-DELETE; AWS-DPA | RT-02 |

## 7. Accepted bounded control design

OD-11C-12 accepts the architecture. [ADR-0011](../adr/ADR-0011-alpha-transcribe-privacy-retention-key-custody.md) records the consequential choices. SC-01 is closed; current gate remains dependency-blocked.

### Identity and ownership

Owner-accepted design: optional Cloud enrollment creates a random installation key pair in app-private/Keystore-backed storage and proves possession against a one-use server challenge over authenticated TLS. DORA issues an opaque installation principal and server-issued access/rotating refresh credentials bound to that key. Random client ID alone is not identity; enrollment creates no account and grants no recording consent. Do not assume hardware attestation or select an external IdP.

Enrollment additionally requires an unconsumed Project Owner invitation/allowlist authorization. No anonymous/public registration. Invitation verification, key proof and server identity binding precede Cloud use; membership is not recording consent.

The server assigns owner identity from verified credentials and binds a newly registered logical recording/source to that owner. Reject arbitrary owner/tenant in request bodies. Content hashes support integrity only, never cross-owner deduplication or ownership. Every job, upload, result, consent and deletion operation carries owner and generation binding.

Access credentials are limited to operation/audience, expire under an approved parameter profile and are checked against current revocation/key epoch on each action. Refresh consumes a rotating single-use handle plus proof of the bound installation key; atomically issue successor, detect reuse, revoke the affected family and require explicit re-enrollment/recovery. Store only protected verifiers where feasible. No permanent privileged AWS secret reaches Android.

Sign/prove each sensitive request using a reviewed standard scheme binding method, target, body digest, owner context, action ID and server-issued one-use nonce. Server replay cache/nonce expiry and idempotency are distinct: identical retry returns same action; changed payload with same idempotency key is rejected. No bespoke crypto protocol/library is approved. Finite credential/nonce/lease parameters must be versioned and tested at AUTH/UPLOAD/SECRETS before implementation activation; the frozen Stage 0 design criterion does not prescribe numerical values.

Credential revocation/key loss stops new Cloud actions and fences outstanding upload/worker leases. Persist already accepted deletion operations independently. A lost key/reinstall does not inherit remote ownership or silently mint access to previous data: local data remains local; automated remote identity recovery is not enabled; separately verified privacy requests remain within PC-01 legal procedure. UI distinguishes unable to authenticate from remote deletion complete.

Future accounts are optional, separately scoped links to installation principals; merging ownership requires explicit verified action, not matching email/IP. No account schema, OIDC provider, login wall, AWS account or endpoint is created. Recording/offline/installed Local continue without Cloud identity, network or GMS.

### Payload protection and key custody

Owner-accepted Alpha transport: authenticated TLS with existing Technical §24.3 minimum TLS 1.2; validate hostname/certificate and destination allowlist, disable cleartext, do not add pinning without rotation/recovery design. Local wrapped recording keys never leave the device. A separately authorized uploader reads original audio and sends the approved encoding inside TLS to a DORA-controlled ingress; no claim of Cloud E2EE.

At rest, use envelope encryption with independently scoped object/version data keys and a DORA-controlled key-management boundary on the selected AWS platform. Wrap keys under separately permissioned key-encryption keys; bind owner/object/version/purpose as authenticated context. ADR-0011 selects customer-managed single-region symmetric KMS/SSE-KMS for DORA S3 input/output and separates DORA envelope storage from managed Transcribe internals. Exact deployed roles/keys/configuration remain future technical/runtime verification.

Only the authorized upload ingress and leased ASR/result worker path may handle necessary plaintext. Ordinary API authorization/query roles have no content decrypt grant. A worker receives a short-lived job-scoped identity/lease; key unwrap requires current owner/job/consent/deletion epoch, permitted purpose and object generation. No wildcard list/decrypt access; revoke lease on cancellation or policy revocation. Provider inference necessarily accesses the supplied audio and is separately disclosed.

Worker plaintext stays in bounded memory or explicitly inventoried encrypted temporary workspace; crash dumps, debug payloads, core dumps and content logs are forbidden. Delete scratch and release credentials on exit, cancellation and restart reconciliation. This is a design requirement, not proof against compromised live workers or a guarantee of memory zeroization.

Separate key administrators, data readers and incident auditors; privileged break-glass content access is disabled for ordinary operation and cannot be justified by ASR consent. Any exceptional human access requires a separately lawful reviewed procedure, explicit scope, dual authorization where adopted and content-free audit. AWS-DPA/FAQ allow necessary authorized service access; DORA forbids separate content review and support-content sharing. No employee-free inference guarantee is claimed.

Record categorical authorization/unwrap/rotation/revocation events and restricted ledger references without keys, tokens, plaintext, private URLs, object keys or content-derived hashes in general logs. Cryptographic erasure is claimed only after every scoped wrapped key/copy and fallback restoration route are verified; shared keys, backups or unverified provider copies prevent that claim.

Custodian is DORA Backend/Security under Project Owner acceptance. Use separate customer-managed single-region symmetric KMS boundaries for transient input, transient output and durable DORA payloads. Local original keys never leave Android. SSE-KMS uses data-key envelope handling by AWS; do not claim client encryption whose ciphertext Transcribe cannot consume.

DORA ingress may accept authorized plaintext over TLS and write encrypted input; dispatcher may start scoped jobs; Transcribe service principal transcribe.amazonaws.com may assume only the scoped data-access role with SourceAccount/SourceArn restrictions and necessary input decrypt/output encrypt rights; result-ingester alone reads output into DORA custody. Ordinary control API and key administrators get no blanket payload-read role.

DORA per-job/source epochs govern dispatch and DORA worker leases; KMS does not natively evaluate the application consent ledger. Managed Transcribe may already hold plaintext. Its default-key encrypted internal EBS is a separate provider custody boundary, not customer-key-controlled content. This limitation feeds RT-01 rather than a false E2EE claim.

Audit key usage/rotation/policy and role changes through KMS/CloudTrail plus restricted DORA authorization events; keep context/names/tags content-free. DORA custodian rotates keys using a versioned operational profile, revokes compromised access immediately, and verifies all dependent ciphertext before retiring old key material. Rotation does not automatically erase prior copies; disabling a shared key is not per-record erasure. Numeric rotation/profile settings are later CRYPTO/SECRETS evidence.

### Independent server authorization and upload revocation

UPLOAD: authenticate installation/key epoch; verify recording/source ownership, source generation, exact current consent/disclosure/provider/region, quota, size/checksum/part and operation scope, no cancellation/tombstone. Issue only a bounded upload session for that owner/source/job/part; a credential alone is not consent.

PROCESS: independently recheck owner, complete source integrity, grant epoch/scope, admitted provider profile, quota, deletion/cancel state and unique action before job dispatch and worker lease/decrypt. An uploaded object does not authorize processing.

RETRIEVE: authenticate requester and owner; validate job/result/source association and deletion epoch; return only that owner's permitted immutable version or status. Revoking ASR consent need not erase already produced results, but does not grant access through a revoked identity. Non-enumerating denial for cross-owner/nonexistent records.

DELETE: authenticate owner or separately approved privacy-request authority; require explicit artifact/holder scope and idempotent operation identity. Do not require a still-active ASR processing grant or existing source audio. Persist tombstone/outbox before acknowledgement; reject cross-owner delete and arbitrary provider object locators.

Choose revocation-enforcing DORA storage ingress/session as the accepted abstraction, not a plain long-lived presigned URL. Check fresh authorization at every bounded chunk and fence/close active streams when server revocation commits; abort multipart completion/dispatch and cleanup residual accepted parts through a separate authorized deletion operation. Bytes accepted before that point cannot be unsent. Client revocation immediately stops local network writes; offline server propagation is explicitly unconfirmed.

The ordinary API application need not proxy large audio. A separately controlled data ingress may enforce sessions ahead of storage; an AWS mechanism is acceptable only if documentary design review proves the needed revocation boundary. Expiry alone or an unchecked signed URL is insufficient. The accepted design uses DORA-enforced session/epoch checks, not S3 signed-URL revocation. Concrete race/lease/part tests remain UPLOAD/AUTH runtime gates; no runtime result is asserted.

Consent/deletion ledger unavailable, stale or unreconciled => deny new upload/processing/retrieval of uncertain content. Preserve local capture and queued explicit deletion; show inability to confirm. Credentials/quotas/retries cannot silently change a consent scope. No numeric TTL is invented here.

Managed-service cancellation limit: revoke DORA upload/dispatch/retrieval authority and fence writers; stop sending bytes, delete customer input as scoped, reject late results and delete a terminal Transcribe job. Do not invoke or promise an undocumented hard-cancel API; a non-terminal rejection stays pending and RT-01 prevents incompatible admission.

### Consent ledger

DORA control plane owns the authoritative restricted durable ledger; Android keeps its own durable grant/prompt/revocation outbox and server acknowledgement. Bind owner principal + installation key epoch + logical recording/source generation + disclosure ID/hash/language + purpose/artifact + provider/service/location profile + retention policy version + action/batch selected snapshot + grant basis + server sequence/time + client intent time. Public audit/telemetry contains none of the private content IDs or grant document contents.

Keep policy state ALWAYS / ASK_EACH_RECORDING / MANUAL_ONLY separate from recording authorization PENDING / INHERITED_ALWAYS / EXPLICITLY_APPROVED / MANUAL_USER_ACTION / DEFERRED and from revocation validity. One logical recording spans all chunks. ASK prompt-presented history persists; a batch authorizes only selected members of its presented snapshot. A reconnect, restart or policy toggle cannot lift DEFERRED or reset automatic prompts.

Append transitions: register disclosed scope -> informed grant -> authorize selected recording/action; explicit revoke or incompatible scope change -> increment consent epoch -> fence uploads/dispatch -> reconcile cancellation/status. Leaving ALWAYS invalidates unstarted work relying only on its inherited grant; existing explicit grants retain only their unrevoked disclosed scope. MANUAL action suffices only after the complete current disclosure is accepted, never through the disabled 0.2 notice.

Publish processing eligibility only after atomic durable grant acceptance and current-state validation. A revoked grant cannot be edited back into existence; a new explicit grant has new identity/epoch. Tamper-evident sequence/version and authorized append, separation of ledger admin/readers, backup reconciliation and attributable categorical audit are required; this is not a claim that append-only data should be retained forever (RT-01/02).

### Deletion ledger and exact cascades

DORA owns a durable deletion operation/outbox independent of content rows and processing jobs. Android persists local operation/category bitmap and remote request/receipt references across process death. Bind owner, confirmed scope/version, target source/recording generation, operation/idempotency ID, local/remote axes, holder/category inventory, deletion epoch, attempt/last categorical failure, verified receipt and exclusions. Retain only minimum non-content private linkage under RT-01/02; do not log it publicly.

Local OP-EXPLICIT-AUDIO: fence/cancel audio-dependent jobs and queued sends; delete scoped local original/per-audio key/audio caches only; keep transcript versions/edits/FTS and exact unavailable reason. Remote scope remains ABSENT or NOT_REQUESTED unless separately confirmed. An independently confirmed remote audio-only request targets uploaded original, multipart parts, provider input copies and audio caches; existing transcript results are preserved within their approved lifecycle. Unsupported provider separation blocks that operation until disclosed alternative scope is explicitly chosen.

Local OP-EXPLICIT-CONVERSATION: tombstone -> cancel/reconcile jobs -> revoke queued uploads -> delete local canonical audio, transcript versions/edits/derivatives/FTS/scoped caches -> reconcile each category; retain minimal operation evidence. Existing external exports are untouched; DORA export temp follows DEC-017 independently. Separately confirmed remote whole-recording scope covers upload objects/parts, provider input/result copies, job payload/results, DORA accepted and temporary transcripts and relevant caches/replicas; backups and required audit/consent/deletion records follow explicitly disclosed RT-01/02 rather than a false immediate-all-copies claim.

Remote user-visible states remain exactly ABSENT / NOT_REQUESTED / PENDING / FAILED / RECEIPT. Durable request accepted => PENDING; unverified/failing holder step => FAILED with retry remaining; verified authenticated receipt for exact declared scope => RECEIPT. A receipt may exclude backups and cannot label them erased. Internal per-holder/category progress is orthogonal. UI dismissal/restart never erases pending operation; local success never promotes remote state.

Persist deletion epoch and outbox atomically before destructive steps/acknowledgement. Retry same operation after ownership and scope validation; reconcile unknown remote outcome before resubmission. A late provider callback checks tombstone and cannot recreate/publish/activate deleted content. If provider creates late residual storage, record and erase within the existing confirmed scope; do not silently launch ASR again.

Restore tombstones/revocations before restoring/serving content; compare every live/backup/replica object generation with current authoritative ledger. Unknown or stale ledger => quarantine, no serving/processing. Suppression records cannot expire before the approved maximum restorable-copy/job/callback horizon; RT-02 retains only the non-audio copy/retirement horizon decision. Provider receipt limitations are defined and accepted as limitations, not evidence of purge. No automatic indefinite retention or premature ledger compaction.

Source-unavailable taxonomy remains USER_DELETED / RETENTION_DELETED / MISSING / CORRUPT / KEY_UNAVAILABLE. Missing original blocks reprocessing, never substitutes local transcript; preserve existing text/edits after audio-only deletion. Pending remote erasure/status can still proceed without audio. Reinstall/key loss does not prove remote erasure; account/key recovery procedure is separately blocked.

If an applicable legal hold conflicts with requested deletion, stop the affected holder operation, disclose restricted scope and qualified decision, and preserve visible unresolved status. This package asserts no law requiring retention and invents no hold, exception, duration or approval.

OD-11C-04 cleanup after success depends on durable DORA ingestion, not a possibly lost Android ACK. Losing a client ACK never extends Cloud audio lifetime. Delete the provider job independently from S3 input/output, and preserve durable accepted DORA text on audio-only cleanup.

No legal-hold exception to the 24-hour audio ceiling is approved. If an actual binding requirement conflicts, block the affected Cloud configuration and obtain explicit lawful decision; do not silently keep audio or report compliant deletion.

## 8. Bounded RDY review

Reviewer: OpenAI Codex internal technical design review; owner acceptance OD-11C-12. No independent certification or human signature is claimed.

| id | threat | control | owner | verification_point | residual_risk | blocks_before_6_3 | disposition | design_review | blocking_decision_ids |
|---|---|---|---|---|---|---|---|---|---|
| RDY-011 | Unlawful/undisclosed transfer; incorrect legal role or VPN-derived region. | Invited audience, single Frankfurt region, scope-bound consent, candidate fact pack, opt-out prerequisite and truthful RU/EN disclosure. | Product + qualified Privacy/Legal | PC-01 qualified actual-scope approval before PRIVACY/6.3; later consent/privacy runtime tests before audio. | Only qualified privacy/legal responsibility/applicability/disclosure approval remains; audience, candidate and region are no longer undecided. | true | OWNER_SCOPE_RESOLVED_QUALIFIED_APPROVAL_RESIDUAL | Codex internal technical design review; accepted architecture OD-11C-12; no independent certification. | PC-01 |
| RDY-012 | One global consent flag or stale queue authorizes unrelated artifacts, provider/region changes, replay or a cross-owner action. | Owner/source-bound append-only consent ledger, orthogonal grant validity, current epoch at upload/process, exact batch snapshot and single ASK prompt history. | Backend architecture + Security + Android contract owner | Accepted reviewed design here; AUTH/CONSENT-RUNTIME/UPLOAD adversarial identity/replay/revocation cases before audio. | Compromised client and offline server-notification delay remain; finite parameter/race verification is later runtime evidence, not a missing external design signer. | false | BOUNDED_DESIGN_ACCEPTED_RUNTIME_NOT_RUN | Codex internal technical design review; accepted architecture OD-11C-12; no independent certification. |  |
| RDY-013 | Worker/operator plaintext exposure, overly broad decrypt privilege, false E2EE/erasure promises. | ADR-0011: TLS, customer KMS/S3 envelope boundary, scoped Transcribe role and DORA ingester, provider internal-key/plaintext disclosure, audit and role separation. | Security + Backend/key custodian | Accepted ADR/design here; CRYPTO/SECRETS/SECURITY configuration/adversarial tests before audio. | Transcribe necessarily processes plaintext and controls its internal key/copies; no E2EE or instant erasure. Audio deadline compatibility remains RT-01, not an absent qualified Security signature. | true | CUSTODY_DESIGN_ACCEPTED_PROVIDER_RETENTION_RESIDUAL | Codex internal technical design review; accepted architecture OD-11C-12; no independent certification. | RT-01 |
| RDY-018 | Local delete loses remote pending operation or a late callback/restore resurrects content. | Durable independent deletion ledger/outbox, exact local/remote scopes, per-holder receipts, deletion epoch fences and restore-before-serve reconciliation. | Storage + Backend + Privacy | Ledger/tombstone design accepted; RT-01/02 period/copy decisions before RETENTION/6.3; later DELETE/RETENTION-RUNTIME restart/restore tests. | Non-audio copy/retirement horizon and specified record periods remain; provider/connection failures can delay scoped confirmation. No resurrection or hidden success allowed. | true | LEDGER_DESIGN_ACCEPTED_PERIOD_AND_RESTORE_HORIZON_RESIDUAL | Codex internal technical design review; accepted architecture OD-11C-12; no independent certification. | RT-01; RT-02 |

## 9. Effective status and future verification

| Status | Count |
|---|---|
| SATISFIED | 5 |
| BLOCKED | 3 |
| PARTIALLY_SATISFIED | 1 |
| OPEN | 5 |
| NOT_RUN | 25 |

| Boundary | Value |
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
| 11.1C | PARTIAL / OWNER_DECISIONS_APPLIED_PC02_SC01_RESOLVED_RESIDUAL_PRIVACY_AND_RETENTION_PREREQUISITES |

Five unsatisfied 6.3 predecessors remain: CLD-ADM-PRIVACY-001, CLD-ADM-RETENTION-001, CLD-ADM-CONTROL-001, CLD-ADM-EVALUATION-001, CLD-ADM-PROVIDER-001. ADMISSION remains the output of 6.3, not an input. No dependency cycle or demand to run 6.2D inside this task.

Later, separately scoped verification must cover account effective opt-out, regional deployed endpoints/buckets, finite identity/nonce/lease profiles, exact IAM/KMS policies, ingress revocation, cleanup under deadline, restart/race/restore/receipt tests, provider quality/latency/timestamps and replacement feasibility. No such result is claimed here.

## 10. Provenance and validation contract

Source hashes below bind raw Git blobs at this exact baseline; the JSON also binds this MD and ADR. Current output files use UTF-8 LF bytes. No circular output self-hash.

| Source | Path | Commit | SHA-256 |
|---|---|---|---|
| GATES | docs/contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json | cccf85f982436af0bf9675d738dfe2dd61308842 | 3a03cffa43a0f2743da54b0d47f050f4ac06a260f8e63b66ef60869107a8e350 |
| GATES_MD | docs/contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.md | cccf85f982436af0bf9675d738dfe2dd61308842 | d9f8082076d5294bd2b5ee3071bb97b951b824dad60feb012fdff0caad704951 |
| GAPS | docs/contracts/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.json | cccf85f982436af0bf9675d738dfe2dd61308842 | a3c36d48877fa0e8e385392da3e6e2c19fe49ff99c1ca45535dc225baa391149 |
| GAPS_MD | docs/stage0/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.md | cccf85f982436af0bf9675d738dfe2dd61308842 | bb5d14f4fbd563f0794df5f322c20ce754e3d87a730060323950a5ec5af8356b |
| PREVIOUS | docs/evidence/alpha-readiness-6.2-closeout-v0.1.json | cccf85f982436af0bf9675d738dfe2dd61308842 | d295e6826c0c7d6e8e3c8818b7683357d97803f42f0b1c4f3d5ff8c2dd8f3cd8 |
| ADR9 | docs/adr/ADR-0009-alpha-cloud-execution-boundary.md | cccf85f982436af0bf9675d738dfe2dd61308842 | e614c1817d68c03fb31f4e026bfda7a81099b8ea72deff3a82aea8a4bb8ec2ca |
| ADR10 | docs/adr/ADR-0010-first-alpha-scope-and-aws-platform.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 38fefa83a22326d46f63754350a0547d56ee0dba7f7fc5c780b71619ca76fc2c |
| DECISIONS | docs/DORA_MVP1_PRODUCT_DECISIONS.md | cccf85f982436af0bf9675d738dfe2dd61308842 | c1ff89e407efff9ec03fd2453a5ae730b22a769084ec04ce7eb204a61d8f2cf5 |
| STORAGE | docs/design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 4724f574dc2575445c6b1d50e30691061f79c09d815035163892f5d5d6478273 |
| READINESS | docs/DORA_MVP1_IMPLEMENTATION_READINESS.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 5763a13642a88f81f4347ec01bd4e0788385a051767081c99e9547cb4a0aa5e7 |
| BACKLOG | docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 1762847d310ff981bfbf33e71cb00d13fb9a0495f6c083db7fe4e0bf5298c6c2 |
| STATUS | docs/DORA_MVP1_STAGE_STATUS.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 6e022b59dc50514beabfd13aae1ece0d4cd25e2bbf5f7347a64bc32639ef1940 |
| TECHNICAL | docs/DORA_MVP1_TECHNICAL_PLAN.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 00c4d6816265e86c82e44045a77f85c6e68b43e6094a3736e10ab44eed259a59 |
| DESIGN | docs/DORA_MVP1_DESIGN_SPEC.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 8bc88bd5b9cba036bbc5758957a4a2c1fdb64a053a319cf4b2f9ba544ea81f2b |
| TEST | docs/DORA_MVP1_TEST_STRATEGY.md | cccf85f982436af0bf9675d738dfe2dd61308842 | cfa2d09387ed4bb80f0ff71a7138930c7a642fdad75c37b0b150774b8d74fbe8 |
| THREATS | docs/stage0/DORA_MVP1_PRIVACY_DATA_FLOW_THREAT_MODEL.md | cccf85f982436af0bf9675d738dfe2dd61308842 | d9b793eb5bb9a0c4ef64a03b815fc16425ca923921c98e539811f048853bb251 |
| ASR_DATA | docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 2f6b275f6bd413c58c7ac3495e590491438a16472bb2c0125cb6b69b5b05e009 |
| ASR_PRODUCT | docs/product/DORA_ASR_USER_SCENARIOS_V0_1.md | cccf85f982436af0bf9675d738dfe2dd61308842 | 68ae0749024ac0acfa791c35894b8e1d30c61351daa67f0144e12604962bf537 |
| SCOPE | docs/contracts/DORA_ALPHA_SCOPE_V0_1.json | cccf85f982436af0bf9675d738dfe2dd61308842 | f8ab5373e5dee3fc96e4229d333f4c178baee5b4b67f8a896a21b15965b762be |
| PRIOR_11C_JSON | docs/contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.json | cccf85f982436af0bf9675d738dfe2dd61308842 | 1002e0af262bff52c86e551ba64722c096ca41173b66e44b66421dcf5322290c |
| PRIOR_11C_MD | docs/design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.md | cccf85f982436af0bf9675d738dfe2dd61308842 | dcc9392a09d8d4cca44ce8f4b9cd061ca21aede4903410602c2c5b787aebc40a |

Owner instruction SHA-256: `7867c6e490864bb301fd5e36504805247373262127ed61e825b3426d5b0ebf88`. Prior JSON SHA-256: `1002e0af262bff52c86e551ba64722c096ca41173b66e44b66421dcf5322290c`; prior MD SHA-256: `dcc9392a09d8d4cca44ce8f4b9cd061ca21aede4903410602c2c5b787aebc40a`.

Required checks: source/frozen/history byte preservation; 12/12 verbatim owner instructions; 15/15 verbatim criterion records with prior/current/authority/evidence; 6/6 unique blocker dispositions; 56 lifecycle cells with exact residuals; AWS URL/date/source coverage; accepted-design/qualified-approval separation; MD/JSON parity; acyclic dependencies; no runtime/provider promotion; local links; Stage00; docs-only allowlist; diff whitespace. Actual execution counts/publication/readback are reported after validation, never pre-certified here.

## 11. Limits, publication and next prerequisite

- Project Owner decisions are approved; qualified privacy/legal approval is absent. No human signature or AWS contract acceptance is fabricated.
- Public documentation identifies capabilities and limitations, not actual account effective policy/resources/configuration. No AWS runtime calls, resources or audio.
- PC-02 and SC-01 close; PC-01, RT-01, RT-02 and SC-02 retain only the enumerated residuals. Three gate statuses therefore remain unchanged.
- The 24-hour ceiling is binding DORA policy; all-copy provider compatibility is not proven by published evidence reviewed here.
- v0.2 unavailable-state disclosure is usable now; active consent/upload remains disabled.
- Streaming, quality, timestamps, latency, replacement and provider admission remain outside 11.1C.
- No default 24-hour/90-day period is generalized to transcripts, logs or backups.

| Non-execution | Value |
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

Only remaining prerequisites: PC-01 qualified actual-scope privacy/legal approval; RT-01 documented Transcribe internal-audio <=24h compatibility plus enumerated log/record/failed-output periods; RT-02 non-audio copy and tombstone/receipt retirement horizon. SC-02 is the resulting dependency readback. No 6.2D/6.3 starts.

One atomic docs-only commit, push same branch, independent refetch equality and existing Sheet update/readback are required. This pre-publication artifact does not certify future external actions.

## Appendix A. Verbatim Project Owner decisions

Attribution: explicit Project Owner instruction in this task; APPROVED_BY_PROJECT_OWNER; 2026-09-28; baseline `cccf85f982436af0bf9675d738dfe2dd61308842`. No human name or qualified signature is invented.

## OD-11C-01 — ALPHA AUDIENCE

First Alpha is a **closed internal/invitation-only Alpha**.

Access is limited to:

- Project Owner;
- testers individually invited/authorized by Project Owner.

Not approved:

- public registration;
- public release;
- anonymous public access;
- unrestricted production use.

The Alpha is for technical/product validation.

Do not represent internal Alpha as public production availability.

Decision text SHA-256: `f3046ed2d7d122be3c5291b23625866987d468aab1ef5467ba77be3d3416d74b`.

## OD-11C-02 — USER GEOGRAPHY AND CLOUD REGION POLICY

Tester physical location is not used to dynamically select processing region.

VPN location must never be treated as legal/data-residency authority.

For first Alpha:

**exactly one Cloud processing region is allowed.**

No automatic cross-region fallback.

No silent region migration.

No multi-region processing.

The Project Owner approves:

`AWS Region = eu-central-1 (Europe / Frankfurt)`

for the first Alpha, subject to verification in current official AWS documentation that every actually required Alpha service/function is available there.

If a required service is not supported in `eu-central-1`, do NOT silently choose another region.

Return the incompatibility as a blocker.

Decision text SHA-256: `d441b8fbb659309d548fb77dab226f599f4c5a1c68eeeca144f23d1a211ca6f6`.

## OD-11C-03 — CLOUD ASR CANDIDATE

For purposes of 11.1C privacy/retention/control fact binding:

`Amazon Transcribe`

is the **candidate Alpha Cloud ASR service**.

This is NOT yet `CLD-ADM-PROVIDER-001` admission.

It does NOT constitute 6.2D provider/model quality admission.

11.1C may use Amazon Transcribe facts to close privacy/retention/security prerequisites.

Quality, RU/EN acceptance, timestamps, latency, model/provider acceptance and replacement feasibility remain 6.2D.

Do not conflate candidate identification with provider admission.

Decision text SHA-256: `4ce75c4fe95fd2c4e70c3e110d4905904a24e3a5e96961d0bc10c4a918d81054`.

## OD-11C-04 — AUDIO RETENTION PRINCIPLE

DORA Cloud is **not a long-term store for original audio**.

Canonical original audio remains local on the device until explicit deletion by the user.

Approved local rule remains:

`original audio retained until explicit deletion`

`automatic retention = OFF`

For transient Cloud audio:

### Successful processing

After successful transcription and successful durable ingestion of the required result into DORA:

- Cloud working audio must be deleted as soon as technically safe;
- it must not remain merely for convenience.

### Failed / cancelled / abandoned processing

Cloud working audio must have an enforced upper bound:

**24 hours maximum**

unless a shorter technically safe period is used.

The 24-hour bound is a safety ceiling, not a minimum.

No Cloud audio retention beyond 24 hours is approved for first Alpha.

If a provider architecture makes this impossible, that provider/configuration is incompatible with this owner decision and must remain unadmitted.

Decision text SHA-256: `552100fc9440fa0d4d64580696c39e9e94d28b52849e544483228369941ffdbe`.

## OD-11C-05 — TRANSCRIPT LIFECYCLE

Transcript is a distinct logical artifact from original audio.

Deleting Cloud working audio does NOT automatically delete an accepted DORA transcript.

DORA transcript may remain until explicit user deletion according to the approved DORA deletion model.

Audio-only deletion preserves:

- transcript;
- user edits;
- transcript versions where required by product contract.

Whole-recording deletion follows the exact approved cascade.

Do not use Amazon Transcribe service-managed output as DORA's durable transcript store.

DORA owns durable transcript persistence.

Decision text SHA-256: `7ee7fe38c21c065714d3009004d32125cc96dcce882cd798b402f37c88822738`.

## OD-11C-06 — TRANSIENT S3

For Amazon Transcribe batch processing, use a DORA-controlled S3 design for transient input/output where technically applicable.

Approved Alpha principles:

- transient input audio bucket/object;
- transient Transcribe output object where applicable;
- no long-term audio archive in S3;
- no cross-region replication;
- no long-term backup of transient audio;
- no use of S3 object versioning to preserve deleted transient audio;
- lifecycle/failsafe deletion must enforce the approved maximum;
- explicit application deletion remains required; lifecycle is a safety net, not the sole deletion mechanism.

Do not treat an S3 lifecycle timer as proof that a prior deletion succeeded.

Decision text SHA-256: `55f5fda97f68954d1ddefd4eefe681085debe32315b6a42aac4bfebc40c7ea9f`.

## OD-11C-07 — PROVIDER SERVICE-IMPROVEMENT USE

DORA Alpha ASR consent does **not** authorize AWS to use user audio for:

- model training;
- service development/improvement;
- unrelated human review.

Therefore:

Amazon Transcribe service-improvement participation is **not permitted for DORA Alpha**.

A verified effective AWS AI-services opt-out policy covering Amazon Transcribe is REQUIRED before the first real Cloud audio.

Record this as a mandatory configuration/admission prerequisite.

Do not infer that an Organization policy exists merely because AWS supports one.

Separate:

`POLICY_REQUIRED`

from

`POLICY_EFFECTIVE_RUNTIME_VERIFICATION = NOT_RUN`

Actual AWS-account effective-policy verification belongs before first real audio/provider/runtime admission.

Decision text SHA-256: `f70a31c3e7196cea9717576d5eeaf1ee47fe57bc94f93bc75a94e3caf2ef64ee`.

## OD-11C-08 — PROVIDER-MANDATED METADATA

A provider-mandated service/job metadata retention period may differ from the 24-hour AUDIO rule.

The Project Owner accepts provider-mandated metadata retention **only if**:

1. authoritative AWS documentation proves the period;
2. the retained object is metadata/job record and not retained original audio content;
3. it cannot reasonably be shortened by customer configuration;
4. it is explicitly disclosed in the Alpha privacy/retention package;
5. no DORA-controlled copy is retained longer merely to match the provider's technical record period.

For Amazon Transcribe, independently verify the currently documented job-record period.

If official AWS documentation currently establishes a non-adjustable 90-day Transcribe job-record period, record:

`TRANSCRIBE_JOB_RECORD = PROVIDER_MANDATED_90_DAYS`

as an approved bounded provider constraint.

Do NOT generalize 90 days to:

- input audio;
- DORA transcript;
- S3 input;
- S3 output;
- DORA logs;
- backups.

Decision text SHA-256: `0ad1ffe7b91ad7af84dfd8a1846226b69d6cf43163510b0b7651252307a789b6`.

## OD-11C-09 — OUTPUT STORAGE

For Alpha, do not rely on Amazon Transcribe service-managed output as durable storage.

Where Amazon Transcribe allows customer-controlled S3 output:

use the customer/DORA-controlled bucket design.

After DORA has durably accepted the transcript:

the transient output object must be deleted according to the short-lived Cloud-processing lifecycle.

Do not keep a service-managed transcript merely because AWS permits it to remain until job expiry.

Decision text SHA-256: `d435d4130a2ae9bc70408093a9015ac9d9f4bcf54d5dd4a9610e3c7306ba0446`.

## OD-11C-10 — BACKUP / REPLICATION

Original/transient Cloud audio:

- no long-term DORA backup;
- no cross-region replication;
- no archival tier;
- no restore after logical deletion.

For deletion safety, DORA must maintain a durable deletion/tombstone state sufficient to suppress reintroduction of deleted content.

A restore operation must not resurrect content whose logical recording/audio has an effective deletion state.

For provider-controlled internal replicas/backups:

document the exact provider guarantee or limitation.

Do not claim deletion from a provider backup unless AWS documentation/contract actually establishes it.

Decision text SHA-256: `3d97c67e585c2bec3471d9cea566d28005eda1443e405c083997e158cbec3479`.

## OD-11C-11 — LOGGING

DORA application/operational/security logs must not contain raw audio.

Logs should minimize:

- transcript content;
- sensitive user content;
- raw authorization secrets.

Prefer:

- opaque recording/job IDs;
- states;
- timestamps;
- error classes;
- bounded non-content technical metadata.

Any content-bearing debug logging must be disabled for Alpha unless separately approved.

Decision text SHA-256: `693cc44dc2cd53edfb82299e4acad8326882647803e38d8fe66d760a77d5ba99`.

## OD-11C-12 — SECURITY ARCHITECTURE

The Project Owner approves the bounded Alpha security architecture already designed in 11.1C, subject to factual AWS compatibility.

Principles:

### Local independence

Recording, offline operation and installed Local ASR do NOT require a Cloud account.

### Client identity

Use installation/caller identity suitable for Alpha.

Do not require a public consumer identity provider merely to start Alpha.

Future user accounts remain separable.

### Credentials

Android must not contain permanent AWS credentials.

DORA backend/control plane issues or mediates bounded server-side authorization.

Credentials/tokens must support:

- expiry;
- refresh;
- revocation;
- replay protection.

### AWS boundary

Android does not obtain unrestricted persistent AWS authority.

DORA backend/control plane remains the policy enforcement boundary between Android and AWS.

### Authorization

Server-side authorization is independently enforced for:

- upload;
- processing;
- retrieval;
- deletion.

A previously issued upload authorization must be revocable.

### Encryption

Transport encryption is mandatory.

Encryption at rest is mandatory.

Where AWS KMS/customer-managed key integration is available and appropriate for DORA-controlled S3/Transcribe output, use a DORA-controlled/customer-managed KMS design rather than making unsupported E2EE claims.

Do not claim end-to-end encryption if the Transcribe worker must decrypt audio to process it.

### Key custody

Define:

- key owner;
- permitted service principals;
- decrypt boundary;
- audit path;
- rotation/revocation expectations.

### Ledgers

Maintain durable:

- consent ledger;
- deletion ledger/tombstone.

Runtime proof remains separate.

Decision text SHA-256: `153e4291dd74bcebf3a13e5cd2edda2cca49409291b7c114bc44597b80164d29`.
