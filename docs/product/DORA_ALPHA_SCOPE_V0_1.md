# DORA first internal Alpha scope v0.1

Date: 2026-09-27. Status: APPROVED_BY_PROJECT_OWNER.

Repository: `Monumentogram/DORA`; branch: `chat/alpha-asr-runner-scope`.
Exact pre-change local and fetched remote HEAD: `5b2a43f34ef83efb2521bcbb07613e122baa50cd`; working tree: CLEAN.

Human-readable scope authority; [machine-readable scope](../contracts/DORA_ALPHA_SCOPE_V0_1.json) carries the same statuses, capability rows and normative paragraphs.

## Decision and status boundary

| Item | Status |
|---|---|
| `6.1` | `PASS / ALPHA_SCOPE_AND_SUPPORTED_ENVIRONMENT_FROZEN` |
| `AWS` | `SELECTED_ALPHA_PLATFORM` |
| `AWS_TECHNICAL_ADMISSION` | `NOT_RUN` |
| `FIRST_REAL_AUDIO_ADMISSION` | `NOT_READY` |
| `CLOUD_RUNTIME` | `NOT_IMPLEMENTED` |
| `CLOUD_ALPHA_ACCEPTANCE` | `NOT_RUN` |
| `CLOUD_IMPLEMENTATION_ADMISSION` | `NOT_READY` |
| `CLD-ADM-SCOPE-001` | `SATISFIED` |
| `6.2` | `NEXT_READINESS_STEP / NOT_CLOSED` |
| `6.2D` | `BLOCKED / TECHNICAL_ADMISSION_NOT_RUN` |
| `6.3` | `BLOCKED` |
| `18.1A` | `PASS / MARKET_AND_PRICING_DATASET_READY` |
| `18.1B` | `PASS / TCO_AND_ALPHA_PROVIDER_ECONOMICS_READY` |

6.1 publication/closure conditions are specified below; these status values do not self-certify a future push or Sheet write.

## Capability dispositions

IN means required intended Alpha behavior, not implemented/PASS. OPTIONAL means a user option with its stated acceptance obligations. DEFERRED remains future roadmap work. OUT_OF_SCOPE states unsupported/not-required boundaries of this Alpha.

| Capability ID | Capability | Disposition | Requirement / limitation | Roadmap | Depends on capabilities |
|---|---|---|---|---|---|
| ALPHA-REC-001 | Microphone recording | IN | User-initiated Start, Pause, Resume and Stop; foreground recording; durable original audio. No automatic microphone restart after force-stop/reboot. | 8.1, 8.2, 8.3, 8.5, 8.6 | None |
| ALPHA-REC-002 | Accepted Alpha Recovery | IN | Reuse accepted 0D.6 ALPHA CLOSED / FULL OPEN evidence in its exact boundary; no repeat without a new factual risk. Product integration remains unproven. | 8.1, 8.5, 6.2 | ALPHA-REC-001 |
| ALPHA-REC-003 | Bounded VAD and technical segmentation | IN | Preserve logical recording identity across technical chunks. Existing VAD/segmentation requirements remain subject to 6.2 applicability/gap disposition; no new thresholds, evidence or waiver here. | 6.2, 8.4, 8.4C | ALPHA-REC-001 |
| ALPHA-LANG-001 | Russian and English | IN | RU and EN separately. No guaranteed mixed RU/EN or additional-language quality claim. | 6.2D, 9.2C, 12.1C | None |
| ALPHA-ASR-001 | Cloud ASR on selected AWS platform | IN | Cloud-only supported. Android -> DORA backend/control plane -> AWS adapter -> AWS service; exact service/configuration/model require 6.2D admission. | 6.2D, 7.2C, 9.2C, 18.1C | ALPHA-REC-001, ALPHA-CONSENT-001, ALPHA-DATA-001, ALPHA-LANG-001 |
| ALPHA-ASR-002 | Local ASR installation and use | OPTIONAL | Only the exact bounded Stage 5 Small q8_0 candidate on POCO M5 within its proven scope. No Local installation required for Cloud-only. Optional refers to user installation, not waiver of package/integrity/offline acceptance tests. | 9.1C, 11.2C | ALPHA-REC-001, ALPHA-DATA-001, ALPHA-LANG-001 |
| ALPHA-OFFLINE-001 | Offline recording and deferred processing | IN | Keep original audio without network or Local model; no unauthorized upload. Persist pending/DEFERRED across restart. Reconnect applies current consent; available Local may process only by contract. | 8.2C, 9.2D, 11.2C | ALPHA-REC-001, ALPHA-CONSENT-001 |
| ALPHA-CONSENT-001 | Recording authorization | IN | ALWAYS / ASK_EACH_RECORDING / MANUAL_ONLY; one logical recording is one authorization unit; exact selected batch snapshot and retry/revocation invariants unchanged. | 9.2D, 9.3C, 11.1C | None |
| ALPHA-DATA-001 | Transcript versions and provenance | IN | RU/EN transcript, source-audio identity, immutable raw outputs, processing/provider/model/version identity, explicit unavailable metadata, and active transcript version. | 7.2D, 9.2E | None |
| ALPHA-TIME-001 | Timestamp availability | IN | Expose timestamps only in actually proven scope and mark unavailable/unreliable explicitly. Stage 5 timestamp quality NOT_EVALUABLE; no word-level accuracy promise; Cloud timestamp evidence still required in 6.2D. | 6.2D, 9.2E, 12.1C | ALPHA-DATA-001 |
| ALPHA-EDIT-001 | User edits and safe activation | IN | Preserve edits/history. USER EDIT > CLOUD ASR > LOCAL ASR is proposal priority; ambiguous mapping requires review; edited text requires explicit acceptance against latest edit revision; stale proposals cannot overwrite it. | 9.4, 9.4C | ALPHA-DATA-001 |
| ALPHA-REPROCESS-001 | Original-audio reprocessing | IN | Reprocess original audio, never transcript text. Missing/deleted audio blocks reprocessing honestly. Retry retains action/idempotency identity; explicit reprocess creates a new action. | 9.2E | ALPHA-DATA-001, ALPHA-EDIT-001, ALPHA-CONSENT-001 |
| ALPHA-HISTORY-001 | Local recording history | IN | Basic local history consumes the active version while retained historical versions and edits remain accessible under deletion policy. | 10.1, 10.3C | ALPHA-DATA-001, ALPHA-EDIT-001 |
| ALPHA-SEARCH-001 | Local lexical search | IN | Basic lexical/local search of current content; active-version changes and deletion reconcile the index. No embeddings required. Search/scale gaps remain in 6.2. | 6.2, 10.2, 10.3C | ALPHA-HISTORY-001 |
| ALPHA-EXPORT-001 | Explicit active-version export | IN | Explicit user export of a revision-consistent active transcript with edits/provenance; preserve existing export/temp-copy contract. Cloud consent is not export authorization. | 11.3, 11.3C | ALPHA-HISTORY-001, ALPHA-EDIT-001 |
| ALPHA-DELETE-001 | Deletion and privacy | IN | User-controlled scoped deletion and approved retention/privacy policies. Local original audio retained until explicit deletion; automatic retention OFF. Cloud periods/receipts/control policy remain unresolved in 11.1C. | 11.1, 11.1C, 8.2C | ALPHA-DATA-001 |
| ALPHA-OPS-001 | Cloud safety and operational controls | IN | Authorization/ownership, bounded retries, quotas/cost accounting, content-free diagnostics, deletion/cancellation and stop controls remain admission/acceptance requirements. Billing UI is deferred. | 7.2E, 11.1C, 18.1C, 18.2C, 12.1C | ALPHA-CONSENT-001, ALPHA-DATA-001 |
| ALPHA-DIAR-001 | Automatic diarization and speaker identity | DEFERRED | All automatic speaker separation/identity, including advanced diarization, and speaker-management product flows remain Stage 13 work; not required for this minimal Alpha. No implicit AWS diarization admission. | 13, 13.1, 13.2 | None |
| ALPHA-PROTOCOL-001 | Meeting protocol generation | DEFERRED | Source-grounded meeting timeline/protocol remains in the full MVP roadmap. | 14, 14.1, 14.2 | None |
| ALPHA-DECISION-001 | Decision intelligence | DEFERRED | Decision extraction, reconciliation and revision graph remain future product scope. | 15, 15.1, 15.2 | None |
| ALPHA-TASK-001 | Task extraction | DEFERRED | Tasks, promises, assignees and deadline extraction remain future product scope. | 16, 16.1, 16.2 | None |
| ALPHA-SUMMARY-001 | Advanced summaries | DEFERRED | Structured and advanced meeting summaries remain future product scope. | 17, 17.1, 17.2 | None |
| ALPHA-LLM-001 | LLM processing | DEFERRED | No LLM is needed for the minimal recording/transcript/history path. Future protocol/decision/task/summary consumers require their own admission. | 14, 15, 16, 17, 6.2D | None |
| ALPHA-EMBED-001 | Embeddings and semantic/vector search | DEFERRED | Lexical search is sufficient for this Alpha. Retain a replaceable future embeddings boundary; no vector runtime/model admission. | 10, 19 | None |
| ALPHA-BIOMETRIC-001 | Voice biometrics | DEFERRED | Future separate product/privacy admission; do not create identity templates for Alpha. | 19 | None |
| ALPHA-CONNECT-001 | Connectors | DEFERRED | Future roadmap only; no connector integration required for Alpha. | 19 | None |
| ALPHA-TEAM-001 | Team sync | DEFERRED | Future roadmap only; no shared/team data plane required for Alpha. | 19 | None |
| ALPHA-BILLING-001 | Billing | DEFERRED | Customer billing is future scope; internal usage/quota/cost controls remain IN. | 18, 19 | None |
| ALPHA-RELEASE-001 | Public-store release | DEFERRED | Stage 19 remains required for public/full MVP release; internal Alpha does not waive release controls. | 19, 19.1, 19.2 | None |
| ALPHA-MIXED-001 | Guaranteed mixed RU/EN | OUT_OF_SCOPE | No mixed-language guarantee until separate evidence and versioned scope change. | 6.2D | None |
| ALPHA-DEVICE-001 | All-device/OEM or production support | OUT_OF_SCOPE | No evidence extension beyond POCO M5 / Android 14 API 34 / arm64-v8a; minSdk 28 is only implementation compatibility target. | 19 | None |
| ALPHA-INPUT-001 | Call/system/playback audio capture | OUT_OF_SCOPE | Alpha input is microphone only; no hidden recording or call/system capture. | 19 | None |
| ALPHA-MULTI-001 | Second active provider or adapter implementation | OUT_OF_SCOPE | One active primary AWS platform for Alpha. Implementing a second adapter is not required; future replacement/addition must remain possible. | 18 | None |
| ALPHA-REALTIME-001 | Direct Android-to-provider realtime route | OUT_OF_SCOPE | Normal Alpha file ASR uses the DORA control plane. ADR-0009 future scoped realtime exception is preserved but not admitted. | 18 | None |

## Authority and precedence

The Project Owner explicitly approved this first internal Alpha scope and AWS selection on 2026-09-27. This is a prospective scope overlay on the broader MVP, not removal of full-MVP requirements. ADR-0010 and the versioned owner record document the decision; the Product Decisions register links it.

The approved ASR scenarios/Gherkin/data contract and ADR-0009 remain authoritative. Their historical provider-not-selected wording describes their baselines. This decision selects AWS at platform level only; exact AWS service, region, configuration, model and deployment remain unadmitted. Frozen 6.2C MD/JSON remains byte-identical.

The Technical Plan broader MVP includes speaker/protocol/decision/task/summary features. This explicit first-Alpha amendment defers them to roadmap 13-17, preserving their full-MVP requirements and gates. Stage 18 supplies mandatory Alpha Cloud services before Stage 12; Stage 19 retains full-MVP/public-release admission.


## Cloud, Local and offline behavior

Cloud recognition is required in Alpha and preferred when online and currently authorized. Cloud-only is supported without Local installation. Android -> DORA backend/control plane -> AWS adapter -> AWS service. Audio storage stays DORA-controlled; the API process need not proxy every byte. No concrete storage, queue, identity or AWS service is admitted by platform selection.

Offline microphone recording and durable original audio are mandatory even without a Local model, account, network or GMS. No network means preserve audio and pending/deferred state. Reconnect is a scheduling event, never permission. Available Local follows the approved user choice between on-device processing and waiting.

Local is an optional user-installed capability bounded to the exact admitted Stage 5 candidate. Existing 14 Cloud exits, including optional-package installation/removal/integrity, installed Local offline behavior and edit preservation, remain mandatory future acceptance evidence; OPTIONAL is not a waiver. No second provider adapter is required.


## Consent and failure invariants

ALWAYS allows automatic processing only within valid informed consent; ASK_EACH_RECORDING prompts at most once automatically per logical recording; MANUAL_ONLY never starts autonomous upload. One logical recording is one authorization unit across all technical chunks.

Persist policy separately from recording authorization, disclosure version, grant/revocation times and prompt history. PENDING and DEFERRED send zero audio bytes. Process all/Select authorizes only the selected presented snapshot; later recordings are excluded. Presented unselected/deferred recordings are not automatically prompted again, including after restart or policy changes.

Backend independently verifies ownership and consent. Recheck every upload chunk/retry/dispatch. Leaving ALWAYS invalidates unstarted inherited-only work; policy changes never silently lift DEFERRED. Revocation stops further bytes/cancels pending work; already-sent bytes cannot be claimed erased without verified deletion.

Bounded retry, stable action/idempotency identity and reconciliation prevent duplicate winners/cost amplification. Network/provider/upload/processing/activation failure preserves original audio, successful Local versions, active transcript and edits.


## Data, edits, search, export and deletion

Original Audio -> Authorization -> immutable ASR Versions -> User Edits -> Active Transcript. Preserve exact source audio identity, processing/action/attempt provenance and provider/model/version identity. Missing provider metadata or timestamps are explicitly unknown/unavailable, never invented.

Reprocessing always uses original audio. Missing/deleted audio reports source unavailable and never substitutes transcript text. USER EDIT > CLOUD ASR > LOCAL ASR governs proposals, not silent replacement. Use semantic/audio anchors; ambiguous mappings retain corrections and require review. Activation checks the latest edit revision and deletion/cancellation state.

History, lexical search and explicit export consume a consistent active version. Active-version changes reconcile the index; deletion/late callbacks cannot resurrect content. Export follows existing explicit-export and temporary-copy rules, independently of ASR consent.

Local original audio remains until explicit deletion by default; automatic retention is OFF and the numeric period catalog remains unapproved. Audio-only deletion preserves transcripts/edits; whole-recording deletion follows the disclosed cascade. Local success is not remote success; no physical-flash overwrite claim. Actual Cloud retention, data-use, deletion receipts and privacy/control decisions remain 11.1C prerequisites.


## AWS selection and portability

AWS = SELECTED_ALPHA_PLATFORM; one active primary platform reduces Alpha integration and operational complexity. The choice consumes owner-confirmed independently reviewed 18.1A market/pricing and 18.1B economics. Some multi-provider configurations have lower raw cash TCO; AWS is not asserted cheapest overall.

The observed 18.1B Sheet evidence gives A_AWS BASE $78.82-253.14/month for 10-100 users under its assumptions. This is an economic reference range, not a quote, operational spending approval, measured scope-specific bill or production forecast. Raw research packages are not re-audited here. Refresh shortlist-critical prices/assumptions and bind actual workload/configuration in 6.2D.

Maintain provider-neutral boundaries for ASR, diarization, LLM and embeddings, plus object storage and queue/jobs where practical. AWS SDK/types/errors/request schemas/wire semantics terminate in the corresponding adapter/infrastructure boundary; core/domain contracts must not import or depend on AWS-specific types.

DORA owns logical recording/action/job identity, normalized results/errors, provenance and active-version semantics. Provider IDs are metadata rather than core identity. AWS -> Provider B must remain possible without rewriting the core product model. Infrastructure-specific behavior is documented within its boundary, with normalized lifecycle semantics; no future unused modules or second adapter are scaffolded.

Privileged AWS credentials are prohibited in APK/client configuration. Credentials and provider routing belong behind the DORA control plane. Portability is a frozen requirement, not implemented replacement evidence. Provider/region/configuration/terms changes require revalidation and cannot silently expand consent.


## Supported environment and explicit limitations

Validated Alpha reference environment: POCO M5; Android 14 / API 34; arm64-v8a; Russian and English. This is the environment bound to accepted evidence, not proof that the integrated Alpha application already passes. No all-Android, all-OEM, production or public-release support claim.

Keep minSdk 28 as an implementation compatibility target only; compile/target 36 is not validated Alpha support. Stage 5 device used actual 4KiB pages. ELF/APK 16KiB alignment evidence does not prove actual 16KiB-device runtime (NOT_RUN).

Stage 5 timestamp quality remains NOT_EVALUABLE; mixed RU/EN is not guaranteed. No unlimited/8-hour recording, all-acoustic-condition or background/OEM reliability promise follows. Applicable capture, VAD, search, battery/offline and duration constraints still need 6.2 disposition; foreground recording scope does not waive Cloud queue/background acceptance gates.

The exact Small q8_0 model SHA256 is 49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f; source whisper.cpp v1.9.4 commit 927cfce34f31707e17f2bff35c349632fb9e2c3a. Stage 5 proves only its frozen artifact/configuration/holdout/device. Its private evaluation storage and 2026-10-25 retention deadline remain unchanged; no redistribution, product packaging or Cloud dataset upload permission follows.


## Reusable evidence and still-missing evidence

Reuse Stage 5 closeout 7f7f0134d8031029a3fc77236124de9e1b0ccfee: PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED; POC-ASR-001 PASS / BOUNDED_ALPHA_ONLY. RU 24/24, 36/191=18.848168%; EN 24/24, 31/333=9.309309%; bounded quality/resources/telemetry and cleanup passed. All 48 cases remain consumed; cross-corpus real-world participant equality UNKNOWN.

Reuse accepted Recovery 0D.6 ALPHA CLOSED / FULL OPEN, as referenced by Stage 5 and the current status/Sheet; do not repeat Recovery or promote it to full production admission. Reuse 6.1A/B product/data specification, 6.1C backend boundary, 6.2C frozen admission matrix and owner-confirmed 18.1A/B evidence.

Still missing: 6.2 applicable evidence/gap disposition; 11.1C actual privacy/disclosure/retention/control approvals; 6.2D safe evaluation protocol and exact AWS service/configuration/model technical admission including RU/EN/timestamp/latency/limits/terms/version/replaceability evidence; 6.3 exact-baseline development admission.

Still missing after admission: implemented Cloud ports/backend/auth/consent/storage/queue/data/edits/search/export/operations; all first-real-audio controls; deterministic and applicable platform evidence; all 14 Cloud Alpha exits. No AWS API, real Cloud audio, benchmark, device/ASR campaign, Recovery or Android/runtime implementation occurs in 6.1.


## Acceptance boundary and current gate overlay

6.1 closes documentation/scope only: approved versioned capabilities and support limits; explicit AI dispositions; bounded evidence traceability; MD/JSON agreement, unique IDs, consistent dispositions and acyclic dependencies; docs-only publication and Sheet readback. Closure result is conditional until the atomic commit is pushed, exact remote HEAD confirmed, and every changed Sheet cell successfully read back. Final task report plus Sheet provide the post-publication receipt for that commit.

CLD-ADM-SCOPE-001 = SATISFIED for specification evidence only. Criterion 1 maps to capabilities/environment; criterion 2 to ASR/Local/diarization/LLM/embeddings dispositions; criterion 3 to Stage 5 reference and timestamp/16KiB limitations. No other frozen gate status changes. The current evidence overlay references the unchanged 6.2C snapshot; unlisted requirements/statuses remain in force.

Effective 39-gate counts after this scope closure: 4 SATISFIED, 1 PARTIALLY_SATISFIED, 7 OPEN, 2 BLOCKED, 25 NOT_RUN. 6.3 remains BLOCKED by CLD-ADM-GAPS-001, CLD-ADM-PRIVACY-001, CLD-ADM-RETENTION-001, CLD-ADM-CONTROL-001, CLD-ADM-EVALUATION-001 and CLD-ADM-PROVIDER-001. CLD-ADM-ADMISSION-001 remains the output of 6.3, not a self-dependency.

AWS_TECHNICAL_ADMISSION = NOT_RUN; FIRST_REAL_AUDIO_ADMISSION = NOT_READY; CLOUD_RUNTIME = NOT_IMPLEMENTED; CLOUD_ALPHA_ACCEPTANCE = NOT_RUN; CLOUD_IMPLEMENTATION_ADMISSION = NOT_READY. No technical/privacy/security/production PASS, gate waiver or implementation permission is supplied by owner platform selection.

Next task: 6.2 — close remaining Alpha readiness/gap disposition. Do not start it automatically. Then applicable 11.1C policy/design and 6.2D technical admission precede 6.3. Implementation stages 7-12 and 18 remain downstream; 13-19 are preserved.

## Dependency sequence

These are acyclic stage milestones, not a claim that later runtime gates are closed. First-real-audio additionally requires every gate tagged for that boundary in the frozen matrix.

| Milestone | Prerequisites |
|---|---|
| 6.1 | None |
| 6.2 | 6.1 |
| 11.1C_POLICY_DESIGN | 6.1 |
| 6.2D | 6.1, 6.2, 11.1C_POLICY_DESIGN |
| 6.3 | 6.2, 11.1C_POLICY_DESIGN, 6.2D |
| ALPHA_IMPLEMENTATION_7_12_AND_18 | 6.3 |
| FIRST_REAL_AUDIO | ALPHA_IMPLEMENTATION_7_12_AND_18 |
| 12_ALPHA_ACCEPTANCE | FIRST_REAL_AUDIO |
| 19_PUBLIC_RELEASE | 12_ALPHA_ACCEPTANCE |

## Authority links

- [DORA_ASR_USER_SCENARIOS_V0_1.md](DORA_ASR_USER_SCENARIOS_V0_1.md)
- [DORA_ASR_USER_SCENARIOS_V0_1.feature](DORA_ASR_USER_SCENARIOS_V0_1.feature)
- [DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md](../contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md)
- [ADR-0009-alpha-cloud-execution-boundary.md](../adr/ADR-0009-alpha-cloud-execution-boundary.md)
- [DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.md](../contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.md)
- [DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json](../contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json)
- [DORA_MVP1_TECHNICAL_PLAN.md](../DORA_MVP1_TECHNICAL_PLAN.md)
- [DORA_MVP1_IMPLEMENTATION_READINESS.md](../DORA_MVP1_IMPLEMENTATION_READINESS.md)
- [DORA_MVP1_PRODUCT_DECISIONS.md](../DORA_MVP1_PRODUCT_DECISIONS.md)
- [DORA_MVP1_IMPLEMENTATION_BACKLOG.md](../DORA_MVP1_IMPLEMENTATION_BACKLOG.md)
- [DORA_MVP1_STAGE_STATUS.md](../DORA_MVP1_STAGE_STATUS.md)
- [DORA_ASR_SMALL_Q8_STAGE5_CLOSEOUT_STAGE0_V0_1.md](../stage0/DORA_ASR_SMALL_Q8_STAGE5_CLOSEOUT_STAGE0_V0_1.md)
- [asr-small-q8-stage5-closeout-stage0-v0.1.json](../evidence/poc-asr-001/asr-small-q8-stage5-closeout-stage0-v0.1.json)
- [DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md](../design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md)

- [AWS owner decision](../stage0/DORA_AWS_SELECTED_FOR_ALPHA_OWNER_DECISION_V0_1.md)
- [ADR-0010](../adr/ADR-0010-first-alpha-scope-and-aws-platform.md)
- [Current gate evidence](../evidence/alpha-scope-6.1-closeout-v0.1.json)
- [Alpha Sheet: economic evidence, Этапы A123:J124](https://docs.google.com/spreadsheets/d/1dgZr-BGlI1v8iPEK0ay9CI9mGhiGlJvG2-tTxyJhhOc/edit)
