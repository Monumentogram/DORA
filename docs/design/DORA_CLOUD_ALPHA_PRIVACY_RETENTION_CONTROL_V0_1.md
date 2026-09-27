# DORA Cloud Alpha privacy, retention and control design v0.1

Baseline: `5015e1cd7db7c37315dba41503f6962e3db82408`. Branch: `chat/alpha-asr-runner-scope`. Date: 2026-09-27.

**11.1C = PARTIAL / DESIGN_PACKAGE_COMPLETE_WITH_BLOCKING_APPROVAL_AND_POLICY_DECISIONS**. Design deliverable complete with explicit unresolved decisions; admission is blocked. Qualified approval: **NOT AVAILABLE**.

Human-readable authority for this proposed design package; the [machine-readable evidence](../contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.json) carries identical records. New proposals are not owner-adopted architecture and do not supersede existing DEC/ADRs.

## 1. Authority, scope and review
| Authority | Reconciliation |
|---|---|
| ADR-0009 / ADR-0010 | DORA backend is mandatory for Cloud file ASR; AWS platform selected; exact service/model/region remains unadmitted. No identity provider selected. |
| Technical Plan §§13/24/28 versus ADR-0009 and frozen 6.2C | Earlier OIDC/Keycloak, storage stack, presigned URL and 24-hour recommendations are not Alpha admission. Propose installation credentials and revocable ingress below; adoption requires SC-01, with no silent change to the plan. |
| DEC-009/039 and approved ASR contracts | No installation-time consent. ALWAYS / ASK_EACH_RECORDING / MANUAL_ONLY and recording grants remain separate. The Alpha overlay refines local-first preference only; Local remains optional. |
| DEC-001/010/011/012 | Market/legal roles, actual location and Cloud periods remain unresolved. AWS selection does not resolve them; PC-01/02 and RT-01/02 block closure. |
| DEC-013 / storage contract | Original audio retained until explicit deletion; automatic retention OFF; unapproved numeric catalog unavailable. Audio-only and whole-recording scopes stay separate from remote deletion. |
| DEC-014/015/017 | ASR does not authorize training/human review. Recording/offline/installed Local need no Cloud account. Explicit exports remain separately warned transfers; export-temp one-hour maximum is inherited only for that temp, never generalized to Cloud. |
| Stage 0 threat model §§11/13 versus later DEC-013/017 | Older broad deletion/temp and managed-policy recommendations are interpreted through the later explicit scoped contracts: no automatic remote erase from local success, no shortening export-temp lifecycle via conversation deletion, no managed-policy automatic-retention enablement. |
| 6.1 / repaired 6.2 / frozen 6.2C | 34 capability mappings, 58 dispositions and all frozen criteria/14 exits remain unchanged. This is an additive evidence/status overlay, not a waiver. |
| Review authority | Codex is the document author and performs an internal consistency review; formalReviewer=false. No human, qualified Legal/Privacy or Security signature is supplied. |

No implementation plan or runtime starts here. The current overlay below supersedes only prospective status, not frozen 6.2C, accepted 6.1/6.2 or historical PoC evidence. The 6.2 repair is the accepted baseline.

## 2. Gate-by-gate evidence and exact frozen criteria

### CLD-ADM-PRIVACY-001
Before: **BLOCKED**. After: **BLOCKED**. Blocking: true.
Dependencies (unchanged): CLD-ADM-SCOPE-001, CLD-ADM-CONSENT-001.
Required before: BEFORE_CLOUD_RUNTIME_IMPLEMENTATION, BEFORE_PROVIDER_ADMISSION, BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE.

Frozen acceptance criteria (verbatim):
1. Approved data-flow inventory states what leaves device, destination/service role, purpose and applicable controller/processor responsibilities
2. Versioned user-visible disclosure covers provider/subprocessors, data location, retention, deletion and revocation; revocation is not retroactive erasure
3. ASR processing consent does not authorize training, human review or unrelated artifacts
4. Provider/region changes cannot silently expand grants; no location inference from VPN
5. Record qualified owner approval for actual scope; this gate contract supplies no legal approval

| Criterion | Assessment | Evidence / limitation | Remaining decision |
|---|---|---|---|
| 1 | DESIGN_COMPLETE_APPROVAL_BLOCKED | Data-flow inventory and role responsibilities are enumerated; actual legal roles, entity and approved scope profile remain PC-01/02. | PC-01; PC-02 |
| 2 | PARTIAL_DISCLOSURE_RELEASE_BLOCKED | RU/EN 0.1 is usable only as a truthful unavailable-state notice. Complete active-consent disclosure requires real provider/subprocessor/location/period fields, not placeholders. | PC-01; PC-02; RT-01; RT-02 |
| 3 | DESIGN_EVIDENCE_AVAILABLE | Explicit purpose prohibition and outbound allowlist; actual provider data-use/human-access compatibility remains PC-02. | PC-02 |
| 4 | DESIGN_EVIDENCE_AVAILABLE | Grant compatibility/reconsent and no-VPN rules specified; runtime enforcement remains NOT_RUN. |  |
| 5 | QUALIFIED_APPROVAL_NOT_AVAILABLE | No attributable qualified-owner approval for actual scope is supplied by baseline or this authoring task. | PC-01 |

Required evidence (verbatim):
- Reviewed disclosure text/version, data-flow map and privacy/legal decision
- Candidate/provider terms and data-use/subprocessor/location evidence bound to scope, then revalidated for selected provider

Versioned design evidence exists; required approved/accepted and scope-bound evidence is not available. No gate closure claimed.
Next evidence: PC-01, PC-02.

### CLD-ADM-RETENTION-001
Before: **PARTIALLY_SATISFIED**. After: **PARTIALLY_SATISFIED**. Blocking: true.
Dependencies (unchanged): CLD-ADM-SCOPE-001.
Required before: BEFORE_CLOUD_RUNTIME_IMPLEMENTATION, BEFORE_PROVIDER_ADMISSION, BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE.

Frozen acceptance criteria (verbatim):
1. Preserve approved local default: original audio until explicit deletion; automatic retention OFF with unapproved numeric catalog unavailable
2. Approve actual periods/triggers for DORA objects, provider copies, job metadata, transcripts, caches, logs, backups and replicas; no arbitrary TTL from recommendations
3. Define deletion propagation, receipt scope, failure/retry, late callbacks, restore suppression and source-unavailable behavior
4. Consent revocation != deletion; local deletion != proof of remote deletion; disclose limits of provider receipts
5. Audio-only deletion preserves transcripts/edits; whole recording deletion uses exact disclosed cascade

| Criterion | Assessment | Evidence / limitation | Remaining decision |
|---|---|---|---|
| 1 | SATISFIED_BY_EXISTING_AUTHORITY | DEC-013 local default retained verbatim in meaning; automatic retention OFF and catalog unavailable. |  |
| 2 | BLOCKED_PERIODS_NOT_APPROVED | 56 artifact/holder cells identify storage, purpose, trigger and applicable unresolved period decisions; no Cloud TTL invented. | RT-01; RT-02 |
| 3 | DESIGN_COMPLETE_APPROVAL_BLOCKED | Lifecycle profiles and deletion ledger cover propagation/retry/receipts/callbacks/restore/source loss; holder guarantees and bounds require approval. | RT-01; RT-02 |
| 4 | DESIGN_EVIDENCE_AVAILABLE | Revocation, local deletion and provider receipt scope are explicitly separate. |  |
| 5 | DESIGN_EVIDENCE_AVAILABLE | Audio-only preserves transcripts/edits; exact local and separately authorized remote whole-recording cascades specified. |  |

Required evidence (verbatim):
- Approved per-artifact/per-holder lifecycle matrix
- Versioned Cloud period/trigger decision plus backup/replica and receipt semantics

Versioned design evidence exists; required approved/accepted and scope-bound evidence is not available. No gate closure claimed.
Next evidence: RT-01, RT-02.

### CLD-ADM-CONTROL-001
Before: **OPEN**. After: **BLOCKED**. Blocking: true.
Dependencies (unchanged): CLD-ADM-PRIVACY-001, CLD-ADM-RETENTION-001.
Required before: BEFORE_CLOUD_RUNTIME_IMPLEMENTATION, BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE.

Frozen acceptance criteria (verbatim):
1. Reviewed design defines caller/installation identity and server-issued credentials, optional future accounts, ownership, refresh/revocation and replay protection; no identity provider chosen here
2. Resolve payload protection, encryption at rest, key custody/worker decrypt access and audit; no unproved E2EE claim
3. Specify independent server authorization, upload authority revocation, consent ledger and durable deletion ledger
4. Bounded threat/design review dispositions RDY-011/012/013/018 and identifies testable controls, owners and residual risks; unresolved blocking risks prevent entry
5. Recording, offline use and Local ASR do not require a Cloud account

| Criterion | Assessment | Evidence / limitation | Remaining decision |
|---|---|---|---|
| 1 | DESIGN_COMPLETE_ACCEPTANCE_BLOCKED | Installation-key and server-issued credential proposal covers ownership, rotating refresh, revocation, replay and optional accounts; acceptance/parameters missing. | SC-01 |
| 2 | DESIGN_COMPLETE_ACCEPTANCE_BLOCKED | TLS, envelope custody, worker decrypt and audit defined as proposed design; qualified review/ADR still required, no E2EE claim. | SC-01 |
| 3 | DESIGN_COMPLETE_ACCEPTANCE_BLOCKED | Four independent authorization checks, revocable ingress and durable consent/deletion ledgers specified. | SC-01; SC-02 |
| 4 | DESIGN_REVIEW_INTERNAL_ONLY | All four RDY risks have controls/owners/verification/residuals; internal author review does not constitute qualified design acceptance. | PC-01; PC-02; RT-01; RT-02; SC-01; SC-02 |
| 5 | SATISFIED_BY_EXISTING_AUTHORITY | ADR-0009/0010, DEC-015 and approved ASR contracts preserve recording/offline/Local without a Cloud account. |  |

Required evidence (verbatim):
- Accepted bounded Alpha security/identity/key-custody design with reviewer and risk dispositions
- Approved consent/deletion ledger and ownership model; runtime proof remains separate

Versioned design evidence exists; required approved/accepted and scope-bound evidence is not available. No gate closure claimed.
Next evidence: SC-01, SC-02.

CONTROL is BLOCKED because PRIVACY and RETENTION block its dependencies and its design acceptance is absent. EVALUATION and PROVIDER remain OPEN; ADMISSION remains BLOCKED and is the output of 6.3. No circular requirement to implement runtime before 6.3 is introduced.

## 3. Data-flow and responsibility inventory
| ID | Objects leaving a boundary | Destination / service role | Purpose | Authorization | Stored as / policy | Responsibility |
|---|---|---|---|---|---|---|
| DF-01 | Opaque installation public-key reference, challenge proof, requested credential action; no account name/email/hardware ID | DORA identity/control boundary | Register a Cloud caller, issue/rotate/revoke credential; optional Cloud setup only | Explicit Cloud setup; local core never enrolls automatically; enrollment is not ASR consent | Protected identity binding and credential verifier; RT-01/02 | DORA authenticates possession, limits abuse and owns revocation; actual legal entity/role PC-01 |
| DF-02 | Disclosure version/digest, grant/revoke event, policy, exact selected recording snapshot, opaque source/action identifiers, language/encoding/size/checksum and prompt history needed for reconciliation | DORA control plane and consent ledger | Authorize precisely scoped original-audio ASR and deduplicate one logical action | Current informed grant plus authenticated owner; no audio before grant persistence; minimum metadata, no local file path/title/transcript | Consent/job/identity metadata; RT-01/02 | DORA independently validates ownership, purpose, destination and current grant; IDs/hashes remain sensitive, not anonymous |
| DF-03 | Original audio bytes/technical chunks and bound checksum/length; no Local transcript or edits | DORA-controlled revocation-enforcing upload ingress and object storage on the selected AWS platform | Temporary authorized input for RU/EN file ASR | Separate upload authorization; exact owner/source/job/part/consent epoch; validate before every chunk/retry; no public object or reusable unrevocable bearer URL | Upload objects, explicitly not an extra canonical Cloud recording; RT-01/02 | DORA controls access/lifecycle; AWS infrastructure processor/subprocessor role subject to PC-01/02 |
| DF-04 | Bound original audio, encoding/language/configuration, opaque provider correlation; only adapter-required minimum | DORA worker -> CloudAsrProvider -> separately admitted AWS service | Perform the authorized ASR job | Fresh processing authorization; exact provider/region scope; no training/human-review or unrelated processing grants | Provider temporary copies, jobs and result; PC-02 / RT-01/02 | DORA instructs and supervises processing; actual AWS contracting/service/subprocessor roles require evidence |
| DF-05 | Normalized transcript, timestamps/availability, job/provenance/status/error class and result integrity metadata | AWS adapter -> DORA result store -> owning installation | Return immutable Cloud result; validate source/generation and preserve edits locally | Provider-response authenticity and job association; independent retrieval authorization; deleted/stale generations cannot publish | Transient Cloud transcript plus local immutable result; RT-01/02 for Cloud | DORA validates output, access and local handoff; provider receipt is not local activation |
| DF-06 | Cancel/revoke/delete request, exact scope and opaque operation/receipt references; result-received acknowledgement | DORA consent/deletion ledgers -> relevant storage/provider -> authenticated installation | Stop new work, reconcile deletion and disclose holder-specific results | Independent delete permission; revoked processing consent never blocks a separately authenticated erasure/status request; no new inference | Durable non-content consent/deletion evidence; RT-01/02 | DORA owns outbox/retries/status; each holder attests only its own disclosed scope |
| DF-07 | Source network address and connection metadata observable to serving network/TLS endpoints; minimal request version, size/duration counters and categorical status | DORA/AWS network and operational boundaries | Connection delivery, quota/abuse protection and content-free operation | Disclose unavoidable metadata; disable content capture at ingress; diagnostics exporter remains separate opt-in; no GPS, device serial or account contacts | Operational/audit records only per approved RT-01/02; no body/token/URL logging | DORA configures minimization; providers may observe network metadata; no inference of legal residence or region from IP/VPN |
| DF-08 | Explicitly selected export text/audio/file or previewed content-free diagnostics | User-selected clipboard, external app or storage destination | User-requested export/support action, outside ASR pipeline | Separate DEC-017 confirmation/warning each transfer; diagnostics scope separate; ASR consent grants neither | External copies controlled by recipient; DORA-managed temp stays under DEC-017 | DORA discloses loss of control; recipient may use network independently; not a Cloud-ASR grant |


## 4. Privacy policy and disclosure boundaries
- This inventory is an allowlist. All other outbound artifact classes are prohibited by this Alpha design: local transcript/edit history as ASR input, summaries/tasks/protocols, embeddings/biometrics, contacts, participant identities, precise location, raw diagnostics and training/research data. New flow requires versioned scope/disclosure and review.
- Recording uses explicit visible Start and remains independent of Cloud eligibility. Existing participant acknowledgement is not a legal permission; actual recording/lawful-basis decisions remain PC-01.
- DORA has operational responsibility for purpose limitation, identity/authorization, minimization, instructions to processors, disclosure versions, erasure/status, incident handling and access oversight. Whether DORA is controller or processor for an actual internal/customer context is unresolved PC-01; a customer/user is not automatically a legally valid controller for all recorded participants.
- A scope profile must name the actual DORA entity/contact, applicable controller and processors, AWS contractual party, exact service roles, subprocessors and their processing/support countries; data, key, log, backup and replica locations are separately disclosed. AWS is not treated as one universal entity or one guaranteed location.
- ASR permission authorizes necessary processing only. Training/model or service improvement, human content review/support sharing and unrelated artifact processing are not authorized. No such paths are enabled by this package; provider terms/configuration that cannot honor this block the flow (PC-02). A DPA, platform choice or opt-out web page alone is insufficient proof.
- Grant scope binds artifact, purpose, provider/service, region/location profile, disclosure digest, retention/deletion policy version and selected recording/source. A changed provider/region/subprocessor purpose or retention scope invalidates affected future work until compatibility is explicitly reviewed and any expanded scope is newly disclosed/accepted. Never switch due to VPN or infer legal residency from language, IP or VPN.
- Revocation is not retroactive erasure. Stop new bytes and pending work, fence running upload sessions and request cancellation of in-flight processing; already transferred bytes and cancellation outcome remain visible. Deletion needs a separately authorized operation. Client offline revocation cannot magically reach a disconnected server: local sends stop immediately, durable revocation sync is prioritized, server-acknowledged revocation cuts off further server authorization, and the unacknowledged interval is disclosed.
- No cloud account is needed for recording, offline storage/history or installed Local ASR. A Cloud installation identity is not proof of participant consent. Cloud failure, missing Local package or unavailable approval cannot block recording and preservation within the approved local scope.
- Data subjects may request information/deletion through the eventual disclosed controller contact; process ownership and verified requester procedure must be named in PC-01. Do not invent an address, jurisdiction, legal basis or completed rights-request workflow.

### Actual user-visible notice v0.1
These are finished RU/EN texts for the currently unavailable Cloud state. They are not an approved Legal notice for an enabled upload. There is no enabled grant button; placeholder facts must never be shown as an active consent request.

#### CLOUD-DISCLOSURE-RU-0.1

Облачная расшифровка пока недоступна. Для первой Alpha выбрана платформа AWS, но конкретный сервис, места обработки и хранения, сроки хранения копий и ответственные за обработку ещё не утверждены. Поэтому разрешение на отправку сейчас не запрашивается, и DORA не должна отправлять запись. Запись и сохранение на устройстве не требуют облачного аккаунта; распознавание на устройстве доступно при установленном локальном пакете.

Когда облачная обработка будет допущена, перед разрешением DORA покажет, кто обрабатывает данные, какие сервисы и субподрядчики участвуют, страны и регионы, сроки каждой копии и ограничения удаления. Для расшифровки планируется отправлять исходное аудио через инфраструктуру DORA и служебные данные: идентификатор установки и задания, язык, формат, размер, контрольную сумму, запись разрешения и статус операции. Сетевые узлы также видят адрес подключения и технические сведения. Локальные расшифровки и ваши правки не заменяют исходное аудио и не отправляются для этой обработки.

Обработка на сервере требует доступа обработчика к содержимому аудио. Это не сквозное шифрование, скрывающее запись от сервера. Разрешение на расшифровку не разрешает обучение или улучшение моделей, просмотр содержимого людьми либо обработку других материалов. Смена сервиса или региона не расширяет разрешение автоматически; VPN не определяет допустимое место обработки.

Отзыв разрешения останавливает новые отправки с устройства. Без связи сервер узнает об отзыве только после доставки запроса; до подтверждения остановка серверной работы не гарантирована. Уже отправленные данные нельзя забрать назад, а отзыв не является их удалением. Удаление запрашивается отдельно; локальное удаление не подтверждает удаление в облаке. Подтверждение поставщика относится только к указанным в нём копиям, а не автоматически к резервным копиям и репликам.

На устройстве оригинал хранится до явного удаления; автоматическое удаление выключено. Удаление только аудио сохраняет расшифровки и правки, но повторная расшифровка без оригинала невозможна. Удаление всего разговора охватывает локальное аудио, расшифровки, версии, правки, производные данные, поиск и связанные кэши; внешние экспортированные копии остаются у получателя, облачные копии отслеживаются отдельно. Правила временных экспортов DORA не меняются. Физическая перезапись памяти не гарантируется.

Actions: Понятно, Продолжить без облака. Grant action enabled: false.

#### CLOUD-DISCLOSURE-EN-0.1

Cloud transcription is not available yet. AWS is the selected Alpha platform, but the exact service, processing and storage locations, copy retention periods and responsible parties have not been approved. DORA therefore does not ask for upload permission now and must not send the recording. Recording and saving on the device do not require a Cloud account; on-device recognition is available with an installed Local package.

Before an admitted Cloud flow asks for permission, DORA will identify the responsible parties, services and subprocessors, countries and regions, each copy lifetime and deletion limitations. The planned transcription flow sends original audio through DORA-controlled infrastructure with installation/job identifiers, language, encoding, size, checksum, permission record and operation status. Network endpoints also observe connection addresses and technical metadata. Local transcripts and your edits are neither substitutes for the original audio nor inputs to this Cloud transcription.

Server processing requires the processor to access audio content. This is not end-to-end encryption that hides it from the server. Transcription permission does not authorize model training or improvement, human content review or unrelated processing. A service or region change does not automatically expand permission; VPN routing does not determine lawful processing locations.

Revocation stops new sends from this device. While offline, the server learns of revocation only when the request arrives; server processing may continue until acknowledgement. Already transferred data cannot be unsent, and revocation does not erase it. Request deletion separately. Local deletion is not proof of Cloud deletion. A provider receipt covers only its declared copies, not automatically backups or replicas.

The original stays on your device until explicit deletion; automatic deletion is off. Audio-only deletion preserves transcripts and edits, but retranscription requires the original. Whole-recording deletion covers local audio, transcripts, versions, edits, derivatives, search and related caches. External exported copies remain with their recipient and Cloud copies are tracked separately. DORA export-temp rules are unchanged. Physical storage overwrite is not guaranteed.

Actions: Understood, Continue without Cloud. Grant action enabled: false.

### Release rule for a future active disclosure
Before enabling “Разрешить облачную обработку” / “Allow Cloud processing”, resolve every field below in a reviewed immutable disclosure/profile and link it to the exact grant. No placeholder, generic AWS label or inferred period is acceptable. The disabled notice is never stored as a grant. These are required inputs to later approved text, not invented values.
- DORA legal entity/contact and actual controller/processor roles
- Internal Alpha audience/markets and qualified approval reference
- Named AWS contractual entity, exact candidate service/configuration and subprocessor version
- Processing/support/storage/key/log/backup/replica countries and regions
- Per-holder approved periods/triggers and all deletion/receipt exceptions
- Training/service-improvement and human-access restrictions supported by actual terms/configuration
- Approved identity/key-custody/credential and upload-revocation design
- Disclosure language/version/digest and selected recording/batch/policy scope

## 5. Retention and deletion lifecycle
All storage classifications are **prospective design**, not deployed storage evidence (implementation NOT_RUN). The matrix covers all 14 artifacts × 4 holders = 56 cells, including explicit exclusions. DORA backend and AWS infrastructure may describe the same physical upload object; alias rows must not double-count copies. BACKUP_REPLICA_SYSTEMS is an inventory boundary, not an extra service selection.

Local default: **original audio retained until explicit deletion**. Automatic retention **OFF**; numeric catalog unavailable. Cloud numeric periods are **UNRESOLVED / BLOCKING**. Inherited Stage 0 research periods and DEC-017 export-temp lifetime are not Cloud defaults.

Each cell applies the full named semantics profile below, with the same fields expanded in JSON. That reference defines deletion propagation, retry/failure, receipt, backup/replica, late callback, restore suppression and source-unavailable behavior for that specific cell; it is not a waiver.

### Lifecycle semantics: LOCAL_CONTENT
| Field | Required behavior |
|---|---|
| deletion_propagation | Use OP-EXPLICIT-AUDIO or OP-EXPLICIT-CONVERSATION with local/remote axes separate; reconcile source jobs and unsent uploads before erasure. No remote request without its own approved scope. |
| retry_failure | Persist exact completed/remaining category bitmap before irreversible steps; retry only remaining idempotent steps; failure remains visible. |
| receipt_semantics | Local inventory reconciliation only; no physical overwrite promise; crypto-erasure only with verified per-artifact key deletion. |
| backup_replica_behavior | Private DB/audio/keysets excluded from Android Auto Backup/device transfer per Technical §24.2; existing external exports are not remotely controlled. |
| late_callback_behavior | Reject cancelled/deleted generations and prevent new publication; audio-only deletion preserves existing transcript/edit objects. |
| restore_suppression | Tombstone/source-unavailable state outranks delayed work. Reinstall/key loss cannot be treated as a successful restoration. |
| source_unavailable_behavior | Use USER_DELETED, RETENTION_DELETED, MISSING, CORRUPT or KEY_UNAVAILABLE accurately; no transcript-as-audio fallback. |


### Lifecycle semantics: REMOTE_CONTENT
| Field | Required behavior |
|---|---|
| deletion_propagation | Freeze confirmed holder/artifact scope in deletion ledger; fence writes, cancel work and delete each known object/version/part/cache/result in that scope; no silent expansion to preserved transcript scope. |
| retry_failure | Durable outbox; same operation identity; reconcile timeout/unknown result before retry, bounded approved retry profile; remain PENDING/FAILED rather than claim success. |
| receipt_semantics | Verified holder response binds scope, generation, objects/categories, time and exclusions; API acceptance/job deletion is not proof of all content/backup erasure. |
| backup_replica_behavior | RT-02 blocks actual copy policy; enumerate replicas and snapshots and their expiry/deletion limits. No undisclosed backup-complete claim. |
| late_callback_behavior | Check deletion epoch before persist/publish; stale callbacks discarded, unexpected recreated copies requeued for deletion under original scope. |
| restore_suppression | Recover authoritative deletion ledger first; reconcile every restored object against tombstones before serving or processing. Unknown/stale ledger fails closed. |
| source_unavailable_behavior | Never reconstruct original from transcript; cancel dependent ASR with explicit source reason. Separately authorized deletion/status work survives source loss. |


### Lifecycle semantics: LEDGER
| Field | Required behavior |
|---|---|
| deletion_propagation | Keep only approved minimum non-content scope/receipt and suppression record; erase linkage/records only after policy and suppression obligations permit it. |
| retry_failure | Atomic durable append/outbox and idempotent transition; never acknowledge revoke/delete before durable acceptance; unavailable ledger denies processing, not local operation. |
| receipt_semantics | Acknowledgement of ledger persistence is distinct from holder deletion receipt; show pending/failed/receipt separately. |
| backup_replica_behavior | RT-02 defines ledger replica/backup and suppression horizon; stale restores cannot shorten it. No indefinite-retention assumption. |
| late_callback_behavior | Use monotonic recording/deletion epoch; a late result cannot clear a tombstone or restore a grant. |
| restore_suppression | Authoritative tombstone/revocation state must be at least as current as restored data; otherwise quarantine until reconciliation. |
| source_unavailable_behavior | Opaque operation status remains retrievable under ownership control even when audio is absent; no content retained as proof. |


### Lifecycle semantics: LOG
| Field | Required behavior |
|---|---|
| deletion_propagation | Apply approved content-free log lifecycle and applicable erasure/redaction; no audio/text/body/token/private URL or object-key retention in diagnostics. |
| retry_failure | Track purge failure as operational action; do not hide failure behind log rotation or export it with content. |
| receipt_semantics | Document bounded log categories/time intervals removed; no claim about unapproved provider-internal logs. |
| backup_replica_behavior | RT-01/02 must include every log sink, archive and replica; forbid unapproved sink export. |
| late_callback_behavior | Log only categorical stale-result event, never payload or persistent recording identifier in general logs. |
| restore_suppression | Apply expiry/purge ledger before making archived logs available. |
| source_unavailable_behavior | No original or transcript reconstruction; logs cannot be used as a content recovery channel. |


### Lifecycle semantics: COPY_SYSTEM
| Field | Required behavior |
|---|---|
| deletion_propagation | Apply confirmed deletion scope across inventories, object versions, replicas and backup restores; backup-specific limitations remain RT-02. |
| retry_failure | Retry tracked incomplete holder categories; copy-unreachable or unknown inventory blocks complete claim. |
| receipt_semantics | Receipt states live replicas versus backups separately, with verified coverage and any expiry still pending. |
| backup_replica_behavior | Every copy must have approved source categories, location, key custody, period and restore procedure; not configured by this package. |
| late_callback_behavior | Fence replica writers and reject stale generations; later discovered copy joins the original deletion operation. |
| restore_suppression | Restore deletion/revocation ledger first and reconcile before availability; unreadable/stale ledger blocks serving. |
| source_unavailable_behavior | Never resurrect user-deleted audio or fabricate a missing source; source-loss taxonomy stays exact. |


### Lifecycle semantics: NO_STORAGE
| Field | Required behavior |
|---|---|
| deletion_propagation | No permitted copy in this holder; discovery is a scope violation, stop transfer and create a scoped remediation/deletion record. |
| retry_failure | Do not create the forbidden copy on retry; unexpected-copy remediation stays tracked until verified. |
| receipt_semantics | Not applicable is a design prohibition, not evidence that deployed storage is empty. |
| backup_replica_behavior | Excluded with its source; no backup or replica may introduce this data. |
| late_callback_behavior | Reject any callback attempting to create the prohibited artifact. |
| restore_suppression | Do not restore an excluded artifact; quarantine unexpected copies. |
| source_unavailable_behavior | No substitution or reconstruction from this holder. |


### Per-artifact / per-holder matrix
| Cell | Artifact | Holder | Stored in proposed design | Purpose | Retention start / trigger | Period / unresolved decision | Delete trigger | Semantics profile |
|---|---|---|---|---|---|---|---|---|
| LC-01-1 | original_audio | ANDROID_LOCAL | YES_CANONICAL | Retain verifiable source and permit explicitly authorized ASR/reprocessing. | Durable creation/finalization; no age-based automatic deletion at this baseline | Until explicit deletion under approved local scoped operation; audio-only deletion preserves transcripts/edits. Automatic audio retention OFF; no approved numeric catalog. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOCAL_CONTENT |
| LC-01-2 | original_audio | DORA_CONTROL_PLANE | ALIAS_UPLOAD_OBJECT | Retain verifiable source and permit explicitly authorized ASR/reprocessing. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-01-3 | original_audio | AWS_PROVIDER | ALIAS_PROVIDER_TEMP | Retain verifiable source and permit explicitly authorized ASR/reprocessing. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-01-4 | original_audio | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Retain verifiable source and permit explicitly authorized ASR/reprocessing. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-02-1 | upload_objects | ANDROID_LOCAL | LOCAL_PENDING_SOURCE_NO_EXTRA_CANONICAL | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOCAL_CONTENT |
| LC-02-2 | upload_objects | DORA_CONTROL_PLANE | YES_TEMPORARY | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-02-3 | upload_objects | AWS_PROVIDER | YES_INFRASTRUCTURE_UNDER_DORA | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-02-4 | upload_objects | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Transport and temporarily stage the exact authorized original source; incomplete multipart fragments included. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-03-1 | provider_temporary_copies | ANDROID_LOCAL | NO | Bounded ASR execution only; no service improvement/training or human review. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-03-2 | provider_temporary_copies | DORA_CONTROL_PLANE | NO | Bounded ASR execution only; no service improvement/training or human review. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-03-3 | provider_temporary_copies | AWS_PROVIDER | CONDITIONAL_SERVICE_COPY | Bounded ASR execution only; no service improvement/training or human review. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-03-4 | provider_temporary_copies | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_PROVIDER_COPY | Bounded ASR execution only; no service improvement/training or human review. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-04-1 | provider_jobs_metadata | ANDROID_LOCAL | MINIMAL_LOCAL_JOB_MAPPING | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOCAL_CONTENT |
| LC-04-2 | provider_jobs_metadata | DORA_CONTROL_PLANE | YES_MINIMAL_JOB_MAPPING | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-04-3 | provider_jobs_metadata | AWS_PROVIDER | YES_PROVIDER_JOB | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-04-4 | provider_jobs_metadata | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Track action/attempt/provider association, outcome, idempotency and cancellation; no payload in metadata. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-05-1 | transcripts | ANDROID_LOCAL | YES_CANONICAL_VERSION | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. | Durable creation/finalization; no age-based automatic deletion at this baseline | Until explicit deletion under approved local scoped operation; audio-only deletion preserves transcripts/edits. Automatic audio retention OFF; no approved numeric catalog. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOCAL_CONTENT |
| LC-05-2 | transcripts | DORA_CONTROL_PLANE | YES_TEMPORARY_RESULT | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-05-3 | transcripts | AWS_PROVIDER | CONDITIONAL_SERVICE_RESULT | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-05-4 | transcripts | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Deliver immutable Cloud result and preserve local active/proposed text without replacing edits. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-06-1 | transcript_versions_edits | ANDROID_LOCAL | YES_CANONICAL_HISTORY | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. | Durable creation/finalization; no age-based automatic deletion at this baseline | Until explicit deletion under approved local scoped operation; audio-only deletion preserves transcripts/edits. Automatic audio retention OFF; no approved numeric catalog. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOCAL_CONTENT |
| LC-06-2 | transcript_versions_edits | DORA_CONTROL_PLANE | NO_USER_EDITS_OR_LOCAL_HISTORY | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-06-3 | transcript_versions_edits | AWS_PROVIDER | NO_USER_EDITS_OR_LOCAL_HISTORY | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-06-4 | transcript_versions_edits | BACKUP_REPLICA_SYSTEMS | NO_USER_EDITS_OR_LOCAL_HISTORY | Preserve local raw result versions, edit history, provenance and explicit activation decisions; no Cloud sync in Alpha. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-07-1 | caches | ANDROID_LOCAL | YES_SCOPED | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. DEC-017 export temp is a separate sub-class with inherited safe-release/warned Delete-now/one-hour/startup lifecycle; never shorten it via whole-recording deletion. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOCAL_CONTENT |
| LC-07-2 | caches | DORA_CONTROL_PLANE | CONDITIONAL_TEMPORARY | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-07-3 | caches | AWS_PROVIDER | CONDITIONAL_SERVICE_CACHE | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | REMOTE_CONTENT |
| LC-07-4 | caches | BACKUP_REPLICA_SYSTEMS | NO_DORA_CONTENT_CACHE_BACKUP | Bounded necessary processing/read cache only; include audio decoder/worker scratch and result caches in inventory. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-08-1 | operational_logs | ANDROID_LOCAL | YES_CONTENT_FREE | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOG |
| LC-08-2 | operational_logs | DORA_CONTROL_PLANE | YES_CONTENT_FREE | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOG |
| LC-08-3 | operational_logs | AWS_PROVIDER | CONDITIONAL_SERVICE_LOG | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOG |
| LC-08-4 | operational_logs | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Content-free health, quota, latency and categorical failure diagnostics; not a content archive. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-09-1 | audit_security_logs | ANDROID_LOCAL | YES_CONTENT_FREE | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOG |
| LC-09-2 | audit_security_logs | DORA_CONTROL_PLANE | YES_RESTRICTED_CONTENT_FREE | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOG |
| LC-09-3 | audit_security_logs | AWS_PROVIDER | CONDITIONAL_SERVICE_AUDIT | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | LOG |
| LC-09-4 | audit_security_logs | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_REMOTE_COPY | Restricted action/control audit without speech/text/secrets; sensitive ledger identity joins kept separately. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-10-1 | consent_records | ANDROID_LOCAL | YES_PRIVATE_RECEIPT | Prove exact disclosure and selected grant/revocation state; no audio or transcript. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Approved ledger minimization/expiry after replay/restore/suppression obligations end; content deletion does not silently destroy its pending operation record. Exact period and conflict/legal-hold rule remain RT-01/02. | LEDGER |
| LC-10-2 | consent_records | DORA_CONTROL_PLANE | YES_AUTHORITATIVE_LEDGER | Prove exact disclosure and selected grant/revocation state; no audio or transcript. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Approved ledger minimization/expiry after replay/restore/suppression obligations end; content deletion does not silently destroy its pending operation record. Exact period and conflict/legal-hold rule remain RT-01/02. | LEDGER |
| LC-10-3 | consent_records | AWS_PROVIDER | NO_FULL_LEDGER | Prove exact disclosure and selected grant/revocation state; no audio or transcript. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-10-4 | consent_records | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_LEDGER_COPY | Prove exact disclosure and selected grant/revocation state; no audio or transcript. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Approved ledger minimization/expiry after replay/restore/suppression obligations end; content deletion does not silently destroy its pending operation record. Exact period and conflict/legal-hold rule remain RT-01/02. | COPY_SYSTEM |
| LC-11-1 | deletion_records_receipts | ANDROID_LOCAL | YES_DURABLE_LOCAL_RECEIPT | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Approved ledger minimization/expiry after replay/restore/suppression obligations end; content deletion does not silently destroy its pending operation record. Exact period and conflict/legal-hold rule remain RT-01/02. | LEDGER |
| LC-11-2 | deletion_records_receipts | DORA_CONTROL_PLANE | YES_AUTHORITATIVE_LEDGER | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Approved ledger minimization/expiry after replay/restore/suppression obligations end; content deletion does not silently destroy its pending operation record. Exact period and conflict/legal-hold rule remain RT-01/02. | LEDGER |
| LC-11-3 | deletion_records_receipts | AWS_PROVIDER | YES_PROVIDER_OPERATION_RECORD | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Approved ledger minimization/expiry after replay/restore/suppression obligations end; content deletion does not silently destroy its pending operation record. Exact period and conflict/legal-hold rule remain RT-01/02. | LEDGER |
| LC-11-4 | deletion_records_receipts | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_LEDGER_COPY | Resume scoped deletion, communicate holder status and suppress resurrection; no audio or transcript. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Approved ledger minimization/expiry after replay/restore/suppression obligations end; content deletion does not silently destroy its pending operation record. Exact period and conflict/legal-hold rule remain RT-01/02. | COPY_SYSTEM |
| LC-12-1 | backups | ANDROID_LOCAL | NO_PRIVATE_DB_AUDIO_KEYSETS | Only an explicitly approved recoverability policy; never an implicit permanent content copy. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-12-2 | backups | DORA_CONTROL_PLANE | CONDITIONAL_DORA_POLICY | Only an explicitly approved recoverability policy; never an implicit permanent content copy. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-12-3 | backups | AWS_PROVIDER | CONDITIONAL_PROVIDER_POLICY | Only an explicitly approved recoverability policy; never an implicit permanent content copy. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-12-4 | backups | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_EXPLICIT_INVENTORY | Only an explicitly approved recoverability policy; never an implicit permanent content copy. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-13-1 | replicas | ANDROID_LOCAL | NO_EXTRA_REPLICA | Only explicitly approved availability copies under the same purpose and deletion scope. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-13-2 | replicas | DORA_CONTROL_PLANE | CONDITIONAL_DORA_POLICY | Only explicitly approved availability copies under the same purpose and deletion scope. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-13-3 | replicas | AWS_PROVIDER | CONDITIONAL_PROVIDER_POLICY | Only explicitly approved availability copies under the same purpose and deletion scope. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-13-4 | replicas | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_EXPLICIT_INVENTORY | Only explicitly approved availability copies under the same purpose and deletion scope. | Creation or first accepted byte/event; per-case policy must distinguish upload abandonment, job terminal state, verified client result receipt and explicit deletion | UNRESOLVED / BLOCKING RT-01 and RT-02; no numeric lifetime or maximum deletion lag approved; this is not permission for indefinite storage. | Explicit separately confirmed scope; whole-recording local cascade does not silently authorize remote erasure. Remote copy erasure needs approved remote scope; future expiry only after RT-01 approval. | COPY_SYSTEM |
| LC-14-1 | identity_credentials | ANDROID_LOCAL | YES_PRIVATE_KEY_AND_CREDENTIAL | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. | Explicit optional Cloud enrollment / credential issuance; rotation, revocation or key-loss event | UNRESOLVED / BLOCKING RT-01/02 for identity record retention and SC-01 for credential/nonce/refresh validity; no indefinite credential or record lifetime. | Explicit installation Cloud deregistration, verified compromise/revocation or approved credential expiry; revoke active credentials and upload leases, preserve already accepted deletion work. Device private keys excluded from backup; no silent ownership transfer on reinstall. | LEDGER |
| LC-14-2 | identity_credentials | DORA_CONTROL_PLANE | YES_IDENTITY_AND_PROTECTED_VERIFIER | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. | Explicit optional Cloud enrollment / credential issuance; rotation, revocation or key-loss event | UNRESOLVED / BLOCKING RT-01/02 for identity record retention and SC-01 for credential/nonce/refresh validity; no indefinite credential or record lifetime. | Explicit installation Cloud deregistration, verified compromise/revocation or approved credential expiry; revoke active credentials and upload leases, preserve already accepted deletion work. Device private keys excluded from backup; no silent ownership transfer on reinstall. | LEDGER |
| LC-14-3 | identity_credentials | AWS_PROVIDER | NO_DORA_CALLER_SECRET | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. | No creation permitted | NOT_APPLICABLE: forbidden copy in proposed design | No storage allowed; remediate unexpected copy | NO_STORAGE |
| LC-14-4 | identity_credentials | BACKUP_REPLICA_SYSTEMS | CONDITIONAL_SERVER_IDENTITY_RECORD_NO_DEVICE_PRIVATE_KEY | Authenticate optional Cloud installation; local private key never leaves device. Protected server verifier/principal are not an ASR consent or proof of a person. | Explicit optional Cloud enrollment / credential issuance; rotation, revocation or key-loss event | UNRESOLVED / BLOCKING RT-01/02 for identity record retention and SC-01 for credential/nonce/refresh validity; no indefinite credential or record lifetime. | Explicit installation Cloud deregistration, verified compromise/revocation or approved credential expiry; revoke active credentials and upload leases, preserve already accepted deletion work. Device private keys excluded from backup; no silent ownership transfer on reinstall. | COPY_SYSTEM |


## 6. Bounded identity, key custody, authorization and ledgers
Design proposals below are reviewable choices within the existing boundary. SC-01 blocks their adoption/implementation; no identity provider, AWS service or product dependency is selected.

### Identity and ownership

Proposed design: optional Cloud enrollment creates a random installation key pair in app-private/Keystore-backed storage and proves possession against a one-use server challenge over authenticated TLS. DORA issues an opaque installation principal and server-issued access/rotating refresh credentials bound to that key. Random client ID alone is not identity; enrollment creates no account and grants no recording consent. Do not assume hardware attestation or select an external IdP.

The server assigns owner identity from verified credentials and binds a newly registered logical recording/source to that owner. Reject arbitrary owner/tenant in request bodies. Content hashes support integrity only, never cross-owner deduplication or ownership. Every job, upload, result, consent and deletion operation carries owner and generation binding.

Access credentials are limited to operation/audience, expire under an approved parameter profile and are checked against current revocation/key epoch on each action. Refresh consumes a rotating single-use handle plus proof of the bound installation key; atomically issue successor, detect reuse, revoke the affected family and require explicit re-enrollment/recovery. Store only protected verifiers where feasible. No permanent privileged AWS secret reaches Android.

Sign/prove each sensitive request using a reviewed standard scheme binding method, target, body digest, owner context, action ID and server-issued one-use nonce. Server replay cache/nonce expiry and idempotency are distinct: identical retry returns same action; changed payload with same idempotency key is rejected. No bespoke crypto protocol/library is approved. Exact credential/nonce/lease limits belong to SC-01 before design acceptance.

Credential revocation/key loss stops new Cloud actions and fences outstanding upload/worker leases. Persist already accepted deletion operations independently. A lost key/reinstall does not inherit remote ownership or silently mint access to previous data: local data remains local; separately approved identity-recovery/privacy request process is PC-01/SC-01. UI distinguishes unable to authenticate from remote deletion complete.

Future accounts are optional, separately scoped links to installation principals; merging ownership requires explicit verified action, not matching email/IP. No account schema, OIDC provider, login wall, AWS account or endpoint is created. Recording/offline/installed Local continue without Cloud identity, network or GMS.


### Payload protection and key custody

Proposed Alpha transport: authenticated TLS with existing Technical §24.3 minimum TLS 1.2; validate hostname/certificate and destination allowlist, disable cleartext, do not add pinning without rotation/recovery design. Local wrapped recording keys never leave the device. A separately authorized uploader reads original audio and sends the approved encoding inside TLS to a DORA-controlled ingress; no claim of Cloud E2EE.

At rest, use envelope encryption with independently scoped object/version data keys and a DORA-controlled key-management boundary on the selected AWS platform. Wrap keys under separately permissioned key-encryption keys; bind owner/object/version/purpose as authenticated context. Actual AWS service, algorithm/library/configuration, rotation/recovery periods and access policy are not admitted; SC-01 must record accepted custody ADR and provider configuration is later 6.2D.

Only the authorized upload ingress and leased ASR/result worker path may handle necessary plaintext. Ordinary API authorization/query roles have no content decrypt grant. A worker receives a short-lived job-scoped identity/lease; key unwrap requires current owner/job/consent/deletion epoch, permitted purpose and object generation. No wildcard list/decrypt access; revoke lease on cancellation or policy revocation. Provider inference necessarily accesses the supplied audio and is separately disclosed.

Worker plaintext stays in bounded memory or explicitly inventoried encrypted temporary workspace; crash dumps, debug payloads, core dumps and content logs are forbidden. Delete scratch and release credentials on exit, cancellation and restart reconciliation. This is a design requirement, not proof against compromised live workers or a guarantee of memory zeroization.

Separate key administrators, data readers and incident auditors; privileged break-glass content access is disabled for ordinary operation and cannot be justified by ASR consent. Any exceptional human access requires a separately lawful reviewed procedure, explicit scope, dual authorization where adopted and content-free audit. Provider human access restrictions remain PC-02, not assumed.

Record categorical authorization/unwrap/rotation/revocation events and restricted ledger references without keys, tokens, plaintext, private URLs, object keys or content-derived hashes in general logs. Cryptographic erasure is claimed only after every scoped wrapped key/copy and fallback restoration route are verified; shared keys, backups or unverified provider copies prevent that claim.


### Independent server authorization and upload revocation

UPLOAD: authenticate installation/key epoch; verify recording/source ownership, source generation, exact current consent/disclosure/provider/region, quota, size/checksum/part and operation scope, no cancellation/tombstone. Issue only a bounded upload session for that owner/source/job/part; a credential alone is not consent.

PROCESS: independently recheck owner, complete source integrity, grant epoch/scope, admitted provider profile, quota, deletion/cancel state and unique action before job dispatch and worker lease/decrypt. An uploaded object does not authorize processing.

RETRIEVE: authenticate requester and owner; validate job/result/source association and deletion epoch; return only that owner's permitted immutable version or status. Revoking ASR consent need not erase already produced results, but does not grant access through a revoked identity. Non-enumerating denial for cross-owner/nonexistent records.

DELETE: authenticate owner or separately approved privacy-request authority; require explicit artifact/holder scope and idempotent operation identity. Do not require a still-active ASR processing grant or existing source audio. Persist tombstone/outbox before acknowledgement; reject cross-owner delete and arbitrary provider object locators.

Choose revocation-enforcing DORA storage ingress/session as the proposed abstraction, not a plain long-lived presigned URL. Check fresh authorization at every bounded chunk and fence/close active streams when server revocation commits; abort multipart completion/dispatch and cleanup residual accepted parts through a separate authorized deletion operation. Bytes accepted before that point cannot be unsent. Client revocation immediately stops local network writes; offline server propagation is explicitly unconfirmed.

The ordinary API application need not proxy large audio. A separately controlled data ingress may enforce sessions ahead of storage; an AWS mechanism is acceptable only if documentary design review proves the needed revocation boundary. Expiry alone or an unchecked signed URL is insufficient. Until feasibility and parameters are reviewed under SC-01, upload admission remains blocked.

Consent/deletion ledger unavailable, stale or unreconciled => deny new upload/processing/retrieval of uncertain content. Preserve local capture and queued explicit deletion; show inability to confirm. Credentials/quotas/retries cannot silently change a consent scope. No numeric TTL is invented here.


### Consent ledger

DORA control plane owns the authoritative restricted durable ledger; Android keeps its own durable grant/prompt/revocation outbox and server acknowledgement. Bind owner principal + installation key epoch + logical recording/source generation + disclosure ID/hash/language + purpose/artifact + provider/service/location profile + retention policy version + action/batch selected snapshot + grant basis + server sequence/time + client intent time. Public audit/telemetry contains none of the private content IDs or grant document contents.

Keep policy state ALWAYS / ASK_EACH_RECORDING / MANUAL_ONLY separate from recording authorization PENDING / INHERITED_ALWAYS / EXPLICITLY_APPROVED / MANUAL_USER_ACTION / DEFERRED and from revocation validity. One logical recording spans all chunks. ASK prompt-presented history persists; a batch authorizes only selected members of its presented snapshot. A reconnect, restart or policy toggle cannot lift DEFERRED or reset automatic prompts.

Append transitions: register disclosed scope -> informed grant -> authorize selected recording/action; explicit revoke or incompatible scope change -> increment consent epoch -> fence uploads/dispatch -> reconcile cancellation/status. Leaving ALWAYS invalidates unstarted work relying only on its inherited grant; existing explicit grants retain only their unrevoked disclosed scope. MANUAL action suffices only after the complete current disclosure is accepted, never through the disabled 0.1 notice.

Publish processing eligibility only after atomic durable grant acceptance and current-state validation. A revoked grant cannot be edited back into existence; a new explicit grant has new identity/epoch. Tamper-evident sequence/version and authorized append, separation of ledger admin/readers, backup reconciliation and attributable categorical audit are required; this is not a claim that append-only data should be retained forever (RT-01/02).


### Deletion ledger and exact cascades

DORA owns a durable deletion operation/outbox independent of content rows and processing jobs. Android persists local operation/category bitmap and remote request/receipt references across process death. Bind owner, confirmed scope/version, target source/recording generation, operation/idempotency ID, local/remote axes, holder/category inventory, deletion epoch, attempt/last categorical failure, verified receipt and exclusions. Retain only minimum non-content private linkage under RT-01/02; do not log it publicly.

Local OP-EXPLICIT-AUDIO: fence/cancel audio-dependent jobs and queued sends; delete scoped local original/per-audio key/audio caches only; keep transcript versions/edits/FTS and exact unavailable reason. Remote scope remains ABSENT or NOT_REQUESTED unless separately confirmed. An independently confirmed remote audio-only request targets uploaded original, multipart parts, provider input copies and audio caches; existing transcript results are preserved within their approved lifecycle. Unsupported provider separation blocks that operation until disclosed alternative scope is explicitly chosen.

Local OP-EXPLICIT-CONVERSATION: tombstone -> cancel/reconcile jobs -> revoke queued uploads -> delete local canonical audio, transcript versions/edits/derivatives/FTS/scoped caches -> reconcile each category; retain minimal operation evidence. Existing external exports are untouched; DORA export temp follows DEC-017 independently. Separately confirmed remote whole-recording scope covers upload objects/parts, provider input/result copies, job payload/results, DORA temporary transcripts and relevant caches/replicas; backups and required audit/consent/deletion records follow explicitly disclosed RT-01/02 rather than a false immediate-all-copies claim.

Remote user-visible states remain exactly ABSENT / NOT_REQUESTED / PENDING / FAILED / RECEIPT. Durable request accepted => PENDING; unverified/failing holder step => FAILED with retry remaining; verified authenticated receipt for exact declared scope => RECEIPT. A receipt may exclude backups and cannot label them erased. Internal per-holder/category progress is orthogonal. UI dismissal/restart never erases pending operation; local success never promotes remote state.

Persist deletion epoch and outbox atomically before destructive steps/acknowledgement. Retry same operation after ownership and scope validation; reconcile unknown remote outcome before resubmission. A late provider callback checks tombstone and cannot recreate/publish/activate deleted content. If provider creates late residual storage, record and erase within the existing confirmed scope; do not silently launch ASR again.

Restore tombstones/revocations before restoring/serving content; compare every live/backup/replica object generation with current authoritative ledger. Unknown or stale ledger => quarantine, no serving/processing. Suppression records cannot expire before the approved maximum restorable-copy/job/callback horizon; RT-02 must prove that bound. No automatic indefinite retention or premature ledger compaction.

Source-unavailable taxonomy remains USER_DELETED / RETENTION_DELETED / MISSING / CORRUPT / KEY_UNAVAILABLE. Missing original blocks reprocessing, never substitutes local transcript; preserve existing text/edits after audio-only deletion. Pending remote erasure/status can still proceed without audio. Reinstall/key loss does not prove remote erasure; account/key recovery procedure is separately blocked.

If an applicable legal hold conflicts with requested deletion, stop the affected holder operation, disclose restricted scope and qualified decision, and preserve visible unresolved status. This package asserts no law requiring retention and invents no hold, exception, duration or approval.

## 7. Bounded RDY threat/design dispositions
Internal review by the document author, formalReviewer=false. No generic Security PASS, no accepted residual risk on behalf of the owner, and no runtime proof.
| RDY | Threat/risk | Control | Owner | Verification point | Residual risk | Blocks before 6.3 | Disposition |
|---|---|---|---|---|---|---|---|
| RDY-011 | Unlawful/undisclosed transfer; incorrect legal role or VPN-derived region. | DF allowlist, qualified scope profile, versioned disabled disclosure and deny-by-default release conditions; no VPN/locale inference. | Product + qualified Privacy/Legal | PC-01/02 before PRIVACY/6.3; later network/disclosure checks at CLD-ADM-CONSENT-RUNTIME-001 and SECURITY before first audio. | Actual entity, audience/markets, lawful basis, processor terms and locations are not approved; generic AWS documents do not close scope. | true | BLOCKED_PENDING_QUALIFIED_SCOPE_EVIDENCE |
| RDY-012 | One global consent flag or stale queue authorizes unrelated artifacts, provider/region changes, replay or a cross-owner action. | Owner/source-bound append-only consent ledger, orthogonal grant validity, current epoch at upload/process, exact batch snapshot and single ASK prompt history. | Backend architecture + Security + Android contract owner | SC-01 design/ledger acceptance before CONTROL/6.3; AUTH/CONSENT-RUNTIME/UPLOAD adversarial race/replay cases before first audio. | Client compromise can forge intent; offline server revocation is not instantaneous; credential/replay bounds and ingress feasibility need accepted review. | true | DESIGN_DISPOSITIONED_ACCEPTANCE_BLOCKED |
| RDY-013 | Worker/operator plaintext exposure, overly broad decrypt privilege, false E2EE/erasure promises. | TLS protected upload, DORA-scoped envelope/key boundary, job lease + context bound unwrap, separate admins/readers, no plaintext logs or local-key export. | Security + Backend/key custodian | SC-01 accepted payload/key-custody ADR before CONTROL/6.3; CRYPTO/SECRETS/SECURITY tests on exact runtime before first audio. | Authorized inference sees plaintext; compromised worker/operator/provider behavior not eliminated; exact service/access profile and qualified review absent. | true | PROPOSED_DESIGN_REQUIRES_ADR_AND_REVIEW |
| RDY-018 | Local delete loses remote pending operation or a late callback/restore resurrects content. | Durable independent deletion ledger/outbox, exact local/remote scopes, per-holder receipts, deletion epoch fences and restore-before-serve reconciliation. | Storage + Backend + Privacy | RT-01/02 and SC-01 ledger/copy-policy acceptance before CONTROL/6.3; DELETE/RETENTION-RUNTIME fault/restart/restore acceptance before first audio. | Provider receipt coverage and backup/tombstone horizons unresolved; unavailable service or lost installation key may delay proof. | true | DESIGN_DISPOSITIONED_PERIOD_AND_ACCEPTANCE_BLOCKED |


## 8. Exact remaining decisions
| ID | Status | Gate | Owner | Decision/action | Next evidence required | Deadline |
|---|---|---|---|---|---|---|
| PC-01 | QUALIFIED_APPROVAL_NOT_AVAILABLE | CLD-ADM-PRIVACY-001 | Project Owner + qualified Privacy/Legal scope reviewer | Name actual operating entity, internal Alpha audience/markets, controller/processor relationships and competent approver; resolve applicable DEC-001/010/011 scope and sign the reviewed inventory/disclosure version. | Dated, attributable qualified-owner privacy/legal decision with actual scope and reviewer authority; drafting authorization is not that approval. | Before PRIVACY closure and 6.3 |
| PC-02 | BLOCKING_SCOPE_EVIDENCE_MISSING | CLD-ADM-PRIVACY-001 | Privacy/Legal + provider relationship owner | Bind candidate AWS service/configuration and region profile to current contractual party, terms/DPA, subprocessors, processing/support/backup locations, training and human-access restrictions and deletion guarantees. | Scope-specific documentary fact pack with versions, source snapshots and applicability review, then separate revalidation at 6.2D; neither platform selection nor public generic pages prove actual configuration. | Before PRIVACY closure; revalidate before provider admission and first audio |
| RT-01 | BLOCKING_PERIOD_DECISION | CLD-ADM-RETENTION-001 | Project Owner + Privacy + Backend/Storage | Approve per-holder periods and start/end triggers for every applicable Cloud artifact and local non-content operational/ledger record; decide delivered, cancelled, abandoned and failed-job cases. | Versioned period catalog with rationale, numeric maximum where needed, bounded retries and receipt/ack-loss behavior; DEC-012 recommendation is not approval. | Before RETENTION closure and 6.3 |
| RT-02 | BLOCKING_COPY_AND_RECEIPT_DECISION | CLD-ADM-RETENTION-001 | Privacy + Backend/Storage + provider relationship owner | Approve backup/replica inclusion, maximum retention/deletion lag, provider receipt coverage, suppression-record lifetime and legal-hold handling. | Holder-specific documented capabilities and approved bounds; suppression horizon covers every restorable copy, job and callback, with verified expiry before ledger compaction. | Before RETENTION closure and 6.3 |
| SC-01 | QUALIFIED_DESIGN_ACCEPTANCE_NOT_AVAILABLE | CLD-ADM-CONTROL-001 | Security + Backend architecture + Project Owner | Accept or revise the proposed installation/credential, payload/key custody, authorization and ledger design; disposition RDY risks with a qualified reviewer. | Attributable bounded design review and accepted payload/key-custody ADR required by RDY-013; approved credential/nonce/lease/upload expiry and rotation parameter profile and storage-ingress revocation feasibility. No provider implementation is prerequisite to this design review. | Before CONTROL closure and 6.3 |
| SC-02 | BLOCKED_BY_PRIVACY_AND_RETENTION | CLD-ADM-CONTROL-001 | Security + Backend architecture | Reconcile accepted CONTROL design with closed PRIVACY and RETENTION decisions; no acceptance while their blocking dependencies remain. | Both prerequisite gates SATISFIED plus explicit dependency readback; any changed disclosure/period/key policy reopens affected design review. | Before CONTROL closure and 6.3 |


## 9. Public provider-document context
Read-only documentation research; no AWS API, account inspection, service selection, benchmark or EVALUATION/PROVIDER gate execution. The paraphrases below are limited observations, not approval. Public URLs may change: a qualified reviewer must archive and bind the actual applicable terms/profile before acceptance.

- **AWS-TERMS** — [Last Updated September 11, 2026](https://aws.amazon.com/service-terms/); observed 2026-09-27; sections 1.11 and 50.3-50.4. Some named AI services, including Transcribe, permit improvement-related storage/use and possible out-of-region storage unless applicable opt-out is configured. Verify exact service terms and actual scope; do not infer no-training from platform choice. Public documentary context only; no contract acceptance, service selection, configuration verification or legal opinion.

- **AWS-SUBPROCESSORS** — [Last Updated July 28, 2026](https://aws.amazon.com/compliance/sub-processors/); observed 2026-09-27; Infrastructure, support and service-specific subprocessors. AWS publishes entity/location/service-role information. The applicable subset cannot be frozen for DORA until actual service, contracting entity and location profile are identified. Not a DORA-specific subprocessor approval or assurance that data remains in one region.

- **AWS-DPA-GUIDANCE** — [Live guidance, version not exposed](https://docs.aws.amazon.com/whitepapers/latest/navigating-gdpr-compliance/aws-data-processing-addendum-dpa.html); observed 2026-09-27; DPA and transfer responsibilities. AWS guidance describes contractual safeguards and customer responsibilities for mapping and assessing transfers/configuration. It does not supply DORA legal-role or market approval. No assertion that GDPR or any jurisdiction applies to this Alpha; actual applicability requires PC-01.

- **AWS-TRANSCRIBE-OPT-OUT** — [Live developer guide, version not exposed](https://docs.aws.amazon.com/transcribe/latest/dg/opt-out.html); observed 2026-09-27; Opting out of using your data for service improvement. Transcribe documentation describes default improvement use and an AWS Organizations opt-out mechanism. This is a concrete compatibility question if that service is later proposed, not its selection. No AWS API or account/configuration inspection was performed; human-review/retention/location compatibility is not proven by this page.

## 10. Effective overlay and preserved boundaries
| Status | Count |
|---|---|
| SATISFIED | 5 |
| BLOCKED | 3 |
| PARTIALLY_SATISFIED | 1 |
| OPEN | 5 |
| NOT_RUN | 25 |

| Boundary | Current status |
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
| 11.1C | PARTIAL / DESIGN_PACKAGE_COMPLETE_WITH_BLOCKING_APPROVAL_AND_POLICY_DECISIONS |

Remaining 6.3 predecessor blockers: CLD-ADM-PRIVACY-001, CLD-ADM-RETENTION-001, CLD-ADM-CONTROL-001, CLD-ADM-EVALUATION-001, CLD-ADM-PROVIDER-001. Only CONTROL changes status relative to repaired 6.2 (OPEN → BLOCKED); this makes dependencies explicit, not a runtime regression.

## 11. Evidence bindings and validation boundary
All source SHA-256 values below are computed from the exact raw Git blobs at the baseline, never CRLF working copies. The JSON additionally binds this Markdown SHA-256; this file does not self-hash.
| Authority | Baseline path | SHA-256 |
|---|---|---|
| GATES | docs/contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json | 3a03cffa43a0f2743da54b0d47f050f4ac06a260f8e63b66ef60869107a8e350 |
| GATES_MD | docs/contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.md | d9f8082076d5294bd2b5ee3071bb97b951b824dad60feb012fdff0caad704951 |
| GAPS | docs/contracts/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.json | a3c36d48877fa0e8e385392da3e6e2c19fe49ff99c1ca45535dc225baa391149 |
| GAPS_MD | docs/stage0/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.md | bb5d14f4fbd563f0794df5f322c20ce754e3d87a730060323950a5ec5af8356b |
| PREVIOUS | docs/evidence/alpha-readiness-6.2-closeout-v0.1.json | d295e6826c0c7d6e8e3c8818b7683357d97803f42f0b1c4f3d5ff8c2dd8f3cd8 |
| ADR9 | docs/adr/ADR-0009-alpha-cloud-execution-boundary.md | e614c1817d68c03fb31f4e026bfda7a81099b8ea72deff3a82aea8a4bb8ec2ca |
| ADR10 | docs/adr/ADR-0010-first-alpha-scope-and-aws-platform.md | 38fefa83a22326d46f63754350a0547d56ee0dba7f7fc5c780b71619ca76fc2c |
| DECISIONS | docs/DORA_MVP1_PRODUCT_DECISIONS.md | c1ff89e407efff9ec03fd2453a5ae730b22a769084ec04ce7eb204a61d8f2cf5 |
| STORAGE | docs/design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md | 4724f574dc2575445c6b1d50e30691061f79c09d815035163892f5d5d6478273 |
| READINESS | docs/DORA_MVP1_IMPLEMENTATION_READINESS.md | 06dd359d8efc383446ac2d08744c467c9bed76ab89f7d256eed75c852b90cee1 |
| BACKLOG | docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md | 04bb3c9ac2fa023f6f9d331d08a0291334bd99a387863e04c16ae917a2528b30 |
| STATUS | docs/DORA_MVP1_STAGE_STATUS.md | 42a991dacfdf7275cc990f4248666db013480179c161f2f98da2b126002c8fd8 |
| TECHNICAL | docs/DORA_MVP1_TECHNICAL_PLAN.md | 00c4d6816265e86c82e44045a77f85c6e68b43e6094a3736e10ab44eed259a59 |
| DESIGN | docs/DORA_MVP1_DESIGN_SPEC.md | 8bc88bd5b9cba036bbc5758957a4a2c1fdb64a053a319cf4b2f9ba544ea81f2b |
| TEST | docs/DORA_MVP1_TEST_STRATEGY.md | cfa2d09387ed4bb80f0ff71a7138930c7a642fdad75c37b0b150774b8d74fbe8 |
| THREATS | docs/stage0/DORA_MVP1_PRIVACY_DATA_FLOW_THREAT_MODEL.md | d9b793eb5bb9a0c4ef64a03b815fc16425ca923921c98e539811f048853bb251 |
| ASR_DATA | docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md | 2f6b275f6bd413c58c7ac3495e590491438a16472bb2c0125cb6b69b5b05e009 |
| ASR_PRODUCT | docs/product/DORA_ASR_USER_SCENARIOS_V0_1.md | 68ae0749024ac0acfa791c35894b8e1d30c61351daa67f0144e12604962bf537 |
| SCOPE | docs/contracts/DORA_ALPHA_SCOPE_V0_1.json | f8ab5373e5dee3fc96e4229d333f4c178baee5b4b67f8a896a21b15965b762be |

Strict JSON, complete criterion mapping, MD/JSON parity, source hashes, dependency acyclicity, artifact-holder coverage, unchanged history and docs-only allowlist must pass before publication. Actual results belong to the final task report; no future validator result is pre-certified.

## 12. Publication, limitations and next task
- Actual data handling, AWS configuration and runtime controls are not implemented or tested.
- Disclosure 0.1 is a blocked-state notice, not permission to upload; active disclosure cannot be released with missing profile fields.
- Named owners are accountable roles, not invented people or signatures.
- No new owner-level decision is adopted: technical proposals await SC-01 and the required ADR; existing DEC/ADR approvals remain unchanged.
| Non-execution boundary | Value |
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

Final task report and existing Google Sheet identify the containing commit, parent, remote HEAD and readback. This pre-publication package does not certify future external actions.
Required publication steps: internal_consistency_validation, one_atomic_docs_only_commit, push_same_branch, refetch_exact_remote_head, existing_google_sheet_update, all_changed_cells_api_readback.

**Next task:** 11.1C owner/qualified Privacy-Legal-Security review: resolve PC-01/02, RT-01/02 and SC-01/02 with scope-specific documentary evidence; no 6.2D or 6.3 execution until separately scoped.
Not started by this package. No 6.2D, 6.3, first-audio, Stage 7+ implementation, merge or PR #86 operation.
