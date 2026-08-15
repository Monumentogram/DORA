# POC-OFFLINE-001 static readiness and call-surface contract — Stage 0 v0.1

Contract ID: `poc-offline-readiness-stage0-v0.1`

Disposition: `PROSPECTIVE_READINESS_CONTRACT_READY`

Contract artifact: `CONTRACT_COMPLETE`

Backlog item: `POC-OFFLINE-001 = TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED`

Input class: `SYNTHETIC_ONLY`

Recorded: `2026-08-16T00:41:33.4825352+03:00`, `Europe/Moscow`

The exact machine mirror is
[`docs/evidence/poc-offline-001/readiness-contract-stage0-v0.1.json`](../evidence/poc-offline-001/readiness-contract-stage0-v0.1.json).
Any Markdown/JSON semantic mismatch invalidates both artifacts.

This document completes only a reversible docs/evidence readiness contract. It does not implement
or execute an Offline harness, admit a dependency or model, run a device/emulator/network/GMS
campaign, authorize real data, or make a product, support-matrix, Legal, Security or production
claim.

## 1. Frozen source, authority and scope

The source snapshot is public GitHub `main`
`51892efb4568d2ae9f94df63de99370dc7d91923`, tree
`292324f628a911d69746d0f12a758c80930d969c`, with a valid verified GitHub signature. Exact-main
push CI run `31909284263` completed successfully: required `android-bootstrap=success`; the green
`search-smoke` job is unrelated Search-only evidence. Open PR count was zero and no snapshot drift
was detected before the contract branch was created.

The complete applicable corpus was read in repository precedence: Technical Plan; Design Spec;
Product Decisions and owner records; accepted ADRs; Test Strategy; Backlog; Stage Status; PoC
Gates and Execution Order; Privacy Threat Model; Dataset Governance; IP Policy; the prospective VPN
contract as downstream non-evidence; Stage 0 Owner Decisions; the device matrix; README and
CONTRIBUTING.

The Project owner standing delegation permits ordinary reversible project and documentation
decisions. This scope is only the versioned docs/evidence readiness contract. It excludes Legal,
Security and formal-human approval; implementation; dependency/model admission; device, emulator,
network, GMS, cloud, VPN, provider or Recovery execution; credentials, billing, real data and
production admission. Reviewer/model is `OpenAI Codex / GPT-5`, organization `OpenAI`, role
`AI POC-OFFLINE readiness contract implementation agent`, and `formalReviewer=false`.

Every machine authority flag is `false`:

| Flag | Value |
|---|---|
| `implementationAllowed` | `false` |
| `implementationAllowedByThisPackage` | `false` |
| `executionAllowed` | `false` |
| `measuredExecutionAllowed` | `false` |
| `harnessExecutionAllowed` | `false` |
| `networkExecutionAllowed` | `false` |
| `deviceExecutionAllowed` | `false` |
| `emulatorExecutionAllowed` | `false` |
| `modelExecutionAllowed` | `false` |
| `gmsExecutionAllowed` | `false` |
| `cloudExecutionAllowed` | `false` |
| `vpnExecutionAllowed` | `false` |
| `providerExecutionAllowed` | `false` |
| `recoveryExecutionAllowed` | `false` |
| `dependencyAdmission` | `false` |
| `productionAdmission` | `false` |
| `productionApiAdmission` | `false` |
| `realDataAllowed` | `false` |
| `credentialsAllowed` | `false` |
| `billingAllowed` | `false` |
| `featureFlagActivationAllowed` | `false` |
| `legalApprovalClaimed` | `false` |
| `securityApprovalClaimed` | `false` |
| `formalHumanApprovalClaimed` | `false` |
| `mergeAuthorized` | `false` |

Included scope is the tracked-tree manifest/source/catalog/lockfile inventory, current local-flow
evidence classification, prospective offline scenarios/evidence, content-free app-attributed call
ledger, readiness blockers and Backlog/Stage Status locators. Android/host implementation, any
runtime campaign, external artifact or dependency, real data, production admission and closure of
`POC-VPN-001` or `VPN-FND-P1-001` are excluded.

## 2. Normative requirements

| ID | Exact requirement |
|---|---|
| `OFF-REQ-001` | Local is the default; cloud is off; no account, network, GMS or cloud configuration is a prerequisite for local mode. |
| `OFF-REQ-002` | Every required local scenario has zero app-attributed forbidden runtime network attempts; build, Gradle and Actions downloads are explicitly not runtime evidence. |
| `OFF-REQ-003` | Approved local capture, storage, history, search, tasks, edit, copy and export flows remain usable without account, GMS or network. |
| `OFF-REQ-004` | The future campaign freezes fresh-install, model-absent and model-installed scenario families before execution. |
| `OFF-REQ-005` | A required model-dependent processing job with `MODEL_NOT_INSTALLED` enters processing state `WAITING_MODEL`; an optional summary request on an incapable device with an installed model enters processing/capability state `PENDING_CAPABILITY`; these per-request states are orthogonal to local readiness, never become a login wall, generic failure or data loss, and never block non-model local flows. |
| `OFF-REQ-006` | A model-installed scenario may use only an exact `EVALUATION_APPROVED` runtime with digest, license, provenance, API and ABI compatibility, and 16-KiB package, load and run evidence already included in the frozen harness or APK, plus an exact `EVALUATION_APPROVED` weight or data package with digest, license, provenance and runtime compatibility, preinstalled or activated through an approved signed offline side-load; executable runtime code is never side-loaded or downloaded during the offline run. |
| `OFF-REQ-007` | A queued non-deletion cloud upload or processing intent remains durably `WAITING_NETWORK` while connectivity is denied; an idempotent remote-deletion operation for an existing cloud copy remains durably `DELETE_PENDING`; neither emits an outbound request while denied, blocks local work or replaces local canonical truth, and local deletion completion remains distinct from remote status. |
| `OFF-REQ-008` | Reconnect produces exactly one logical job, result, application and economic effect; transport attempts may repeat, while duplicate or corrupted logical effects remain zero. |
| `OFF-REQ-009` | Process death or reboot preserves local data and durable queued work, never starts the microphone or any new, stale-consent or otherwise ineligible network/cloud work, and may resume only previously authorized eligible work after current consent, profile and runtime constraints are revalidated; every resumed or later reconnect path remains idempotent. |
| `OFF-REQ-010` | Login, AccountManager or OIDC, GMS, DNS, remote configuration, analytics or crash providers, and provider availability cannot gate local core; safe local defaults are present. |
| `OFF-REQ-011` | The call ledger is content-free, app-UID and process attributed, and monitor calibrated; pre-run and post-run canaries run in separate declared calibration windows, remain visible in the raw monitor total, are excluded from the measured scenario total by exact event IDs, and prove per-call-kind coverage before a zero-count claim. |
| `OFF-REQ-012` | Only synthetic or generated inputs are used; real meeting data, private audio, model weights and raw packet traces never enter Git or Actions, and public evidence is sanitized and hashed. |
| `OFF-REQ-013` | UI and evidence distinguish `На устройстве`, `Ожидает модель` and `Ожидает сеть`; local offline mode is neutral rather than degraded, and external share traffic after explicit handoff is outside Dora attribution. |
| `OFF-REQ-014` | Every proof label is bounded to the exact disposable Stage 0 harness and device slices and grants no feature, dependency, support-matrix or production admission. |

## 3. Recorded conflicts and fail-closed rules

| ID / name | Exact issue | Contract rule |
|---|---|---|
| `OFF-CFL-001` `DEVICE_SCOPE` | Technical PoC 9 names D1-D4, Execution Order requires D4 plus at least D1 or D2, and Device Matrix maps Offline to D1, D2, D4, D5 and D7. | Do not narrow silently; full PASS remains blocked until a versioned pre-run DEC or ADR freezes the mandatory device set. |
| `OFF-CFL-002` `TEMPORAL_SCOPE` | The Technical Plan says local functions work indefinitely but does not define a finite campaign duration. | Test structural absence of token, lease, login, remote-config and catalog-expiry dependencies with bounded clock-advance and reboot scenarios; make no indefinite or support claim. |
| `OFF-CFL-003` `RETRY_ENERGY` | The approved failure gate forbids a battery-draining retry loop but supplies no Offline numeric energy threshold. | Require finite retry, no tight loop and no attempt while denied; quantitative energy judgment requires a separately approved pre-run threshold and applicable POC-BATTERY evidence. |
| `OFF-CFL-004` `ORDERING` | Offline precedes VPN, while the existing VPN kernel and I2 loopback contain only synthetic reconnect and idempotency precedent and explicitly leave integrated Offline reconnect unevidenced. | Do not depend on or count VPN I2 as Offline evidence; a future Offline-owned minimal queue and reconnect oracle is required. |
| `OFF-CFL-005` `EXPORT_ATTRIBUTION` | DEC-017 allows explicit clipboard, SAF and Sharesheet handoff, after which an external app or provider may use its own network. | Attribute only Dora UID and process; prove core copy/export with clipboard and a local SAF sink, and classify post-handoff external traffic outside the Dora call count. |

These ambiguities block only the affected execution/PASS scope. They do not block a truthful
prospective contract that preserves them explicitly.

## 4. Current implementation and evidence inventory

| ID | Surface | Status | Evidence | Limitation |
|---|---|---|---|---|
| `OFF-SUR-001` | Production app | `PLACEHOLDER_ONLY` | The `:app` module is the Stage 00 shell with Home, History, Tasks and Settings placeholders and no functional recording action or product flow. | It is not an integrated offline product harness. |
| `OFF-SUR-002` | Capture | `EXISTING_ISOLATED_INCONCLUSIVE` | The isolated `:poc:capture` module contains AudioRecord, foreground-service, file, sanitized evidence export and deletion behavior. | It is not connected to `:app`; its formal Stage 0B result is `INCONCLUSIVE` and physical evidence is limited to exploratory D2. |
| `OFF-SUR-003` | Local storage and recovery | `PARTIAL_RUNTIME_RECOVERY_BLOCKED` | Capture writes PoC WAV and run identifiers; Search owns a separate Room database; Recovery supplies pure contracts. | There is no canonical product database, encrypted audio runtime, durable job or outbox, or authorized Recovery runtime. |
| `OFF-SUR-004` | History and search | `EXISTING_ISOLATED_INCONCLUSIVE` | The isolated `:poc:search` module provides generated-data Room FTS4 repositories and benchmark harnesses. | There is no functional History UI or capture integration, and the authoritative Search verdict is `INCONCLUSIVE` with recommendation `BLOCKED`. |
| `OFF-SUR-005` | Tasks, protocol and edit | `ABSENT` | Only the Stage 00 Tasks placeholder exists. | No task, decision or protocol entities, repository, UI or processing flow exists; `POC-DECISION-001` is `BLOCKED`. |
| `OFF-SUR-006` | Copy and export | `PARTIAL_EVIDENCE_EXPORT_ONLY` | Capture can create a sanitized evidence ZIP in cache and hand it to a private FileProvider and ACTION_SEND. | This is not the DEC-017 conversation copy/export implementation; clipboard, local SAF and History integration are absent. |
| `OFF-SUR-007` | Local model and processing | `ABSENT_BLOCKED` | No ASR, VAD, diarization or decision runtime, model manager, model file or installed-versus-absent model flow is present. | No exact model or native artifact is `EVALUATION_APPROVED`. |
| `OFF-SUR-008` | Queue and reconnect | `HOST_PRECEDENT_ONLY_BLOCKED` | The pure JVM VPN contract kernel and hermetic numeric-loopback transport subset exercise bounded synthetic idempotency behavior. | There is no Android client, outbox, WorkManager, sync record or integrated Offline reconnect, and `POC-VPN-001` remains `NOT_RUN` and `NOT_AUTHORIZED`. |
| `OFF-SUR-009` | Call monitoring and device matrix | `ABSENT_NOT_RUN` | Static manifests and source contain no app INTERNET or ACCESS_NETWORK_STATE permission and no production runtime client dependency. | No calibrated app-attributed OS or firewall monitor, D4 no-GMS execution, airplane run, merged-APK proof or packet and call ledger exists. |

The frozen tree has exactly 157 tracked Android files, seven source main manifests, nine dependency
lockfiles and zero tracked native/model artifacts by a case-insensitive frozen-tree suffix scan for
`.so`, `.aar`, `.tflite`, `.onnx`, `.gguf`, `.model` and `.bin`. The pinned build-tool file
`android/gradle/wrapper/gradle-wrapper.jar` is outside this native/model suffix set. No source manifest
declares `android.permission.INTERNET` or `ACCESS_NETWORK_STATE`. Capture alone declares
`RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` and `POST_NOTIFICATIONS`, sets
cleartext traffic and backup off, and declares a single non-exported private FileProvider scoped to
`cacheDir/exports`.

No production runtime Firebase/Play Services, account/auth SDK, Retrofit/OkHttp/Ktor/Volley,
WorkManager or model/native engine coordinate is present. `java.net.http` and `jdk.httpserver` are
confined to the isolated VPN host-loopback harness. Android lint/UTP dependencies, configured
Gradle repositories, Actions transfers and the Google-APIs Search test image are build/test
infrastructure, not on-device offline evidence. All these facts have evidence class
`OBSERVED_NOT_EXECUTION_EVIDENCE`.

## 5. Prospective proof layers and state catalog

| ID | Layer | Proof | Current status |
|---|---|---|---|
| `OFF-LYR-001` | `STATIC_TREE_INVENTORY` | Tracked manifests, components, source imports, dependency coordinates, lockfiles and forbidden artifact absence. | `OBSERVED_NOT_EXECUTION_EVIDENCE` |
| `OFF-LYR-002` | `DISPOSABLE_INTEGRATED_LOCAL_HARNESS` | One synthetic record-process-edit-search-task-copy-export path with explicit model-present and model-absent states. | `ABSENT_NOT_AUTHORIZED` |
| `OFF-LYR-003` | `CALIBRATED_APP_ATTRIBUTED_CALL_MONITOR` | Complete Dora UID and process call ledger with pre-run and post-run canaries under airplane and OS or firewall denial. | `ABSENT_NOT_AUTHORIZED` |
| `OFF-LYR-004` | `PHYSICAL_DEVICE_AND_RECONNECT_CAMPAIGN` | Frozen mandatory physical matrix including D4 no-GMS and separately approved reconnect execution. | `BLOCKED_NOT_AUTHORIZED` |

The five state axes are orthogonal:

- Local (5): `FRESH_LOCAL_DEFAULT`, `LOCAL_READY`, `LOCAL_OPERATION_RUNNING`,
  `LOCAL_OPERATION_SUCCEEDED`, `LOCAL_OPERATION_FAILED_SCOPED`.
- Processing/capability per request (7): `PROCESSING_NOT_REQUESTED`, `PROCESSING_QUEUED`,
  `PROCESSING_ACTIVE`, `WAITING_MODEL`, `PENDING_CAPABILITY`, `PROCESSING_SUCCEEDED`,
  `PROCESSING_FAILED_SCOPED`.
- Connectivity (4): `NETWORK_DENIED`, `AIRPLANE_MODE`, `AVAILABLE`, `RECONNECTING`.
- Model (3): `MODEL_NOT_INSTALLED`, `MODEL_INSTALLED_APPROVED`,
  `MODEL_UNAVAILABLE_OR_INVALID`.
- Queue (13): `LOCAL_ONLY`, `PENDING_UPLOAD`, `WAITING_NETWORK`, `UPLOADING`,
  `REMOTE_PROCESSING`, `RESULT_AVAILABLE`, `APPLIED`, `DELETE_PENDING`, `DELETED_REMOTE`,
  `CONFLICT`, `FAILED_RETRYABLE`, `FAILED_FINAL`, `CANCELLED`.

The queue catalog role is `OFFLINE_HARNESS_OBSERVER_SUBSET`. Its exact canonical mapping is:

| Queue state | Mapping |
|---|---|
| `LOCAL_ONLY` | maps no remote intent |
| `PENDING_UPLOAD` | maps a durable local outbound intent before eligibility |
| `WAITING_NETWORK` | maps denied or unavailable connectivity without a transport attempt |
| `UPLOADING` | maps a separately authorized in-flight remote send |
| `REMOTE_PROCESSING` | maps accepted remote processing |
| `RESULT_AVAILABLE` | maps a candidate awaiting local validation |
| `APPLIED` | maps one validated application to local truth without overwriting user edits |
| `DELETE_PENDING` | maps an idempotent remote-deletion operation queued or awaiting receipt while local deletion status remains separate |
| `DELETED_REMOTE` | maps only a verified remote-deletion receipt within its declared scope |
| `CONFLICT` | maps a durable content-free reconciliation state that cannot overwrite local user truth |
| `FAILED_RETRYABLE` | maps a durable finite retry state |
| `FAILED_FINAL` | maps an explicit terminal remote failure |
| `CANCELLED` | maps revoked or explicitly cancelled non-deletion remote work |

Consent revocation while offline cancels or revokes only a queued non-deletion upload or processing
intent, performs no send, preserves local canonical data and is covered by `OFF-SYN-022`. Explicit
local deletion without a remote copy is covered by `OFF-SYN-023`. Explicit whole-conversation
deletion with a remote copy is covered by `OFF-SYN-024`: local deletion completes within its scope
and one idempotent remote deletion remains `DELETE_PENDING`. Consent revocation with a remote copy
is covered separately by `OFF-SYN-025`: local canonical data is preserved, non-deletion sends are
revoked and one idempotent remote deletion remains `DELETE_PENDING`. Only a separately authorized
reconnect and verified receipt may produce `DELETED_REMOTE` as covered by `OFF-SYN-026`. Local
completion and remote pending, failed or receipt status remain separate under DEC-012,
BE-DELETE-001 and the storage contract. These scenarios define required future proof only and do
not prove a backend, provider receipt or execution.

This catalog is prospective; no state persistence or transition implementation exists.

## 6. Exact invariants

| ID | Invariant |
|---|---|
| `OFF-INV-001` | Local readiness is orthogonal to each per-request processing/capability state, connectivity, model and remote queue state. |
| `OFF-INV-002` | The default state requires no account and has cloud disabled. |
| `OFF-INV-003` | Network-denied and airplane states produce zero Dora-attributed outbound attempts. |
| `OFF-INV-004` | No transport attempt executes while network is denied; a durable constrained schedule may remain persisted, and every retry budget is positive, finite and non-busy-looping. |
| `OFF-INV-005` | Missing or invalid model state cannot block capture, storage, history, search, tasks, edit, copy or export. |
| `OFF-INV-006` | A model run is eligible only with one exact `EVALUATION_APPROVED` runtime with digest, license, provenance, API and ABI compatibility, and 16-KiB package, load and run evidence already included in the frozen harness or APK, plus one exact compatible `EVALUATION_APPROVED` weight or data package already installed or activated under a separate signed-package approval; executable runtime side-load is forbidden. |
| `OFF-INV-007` | Local canonical data and user edits are never replaced by remote or model candidates. |
| `OFF-INV-008` | Process death and reboot never start the microphone or new, stale-consent or otherwise ineligible network/cloud work; previously authorized durable work may resume only after current consent, profile and runtime constraints are revalidated. |
| `OFF-INV-009` | Reconnect preserves one logical queue identity and yields at most one result application and economic effect. |
| `OFF-INV-010` | Call and flow evidence is content-free and excludes URL, IP address, account, path, transcript, audio, credential and raw key. |
| `OFF-INV-011` | Clipboard and local SAF remain Dora-owned proof paths; external Sharesheet traffic after explicit handoff is separately attributed. |
| `OFF-INV-012` | Static absence evidence, isolated PoCs, emulator networking and host loopback are never labelled as a physical Offline PASS. |

## 7. Deterministic synthetic scenario matrix

Every scenario is currently `NOT_RUN`. Later execution requires an immutable fixture manifest,
execution profile, fault schedule and separate authority.

| ID | Title | Preconditions | Actions | Exact expected outcome |
|---|---|---|---|---|
| `OFF-SYN-001` | Fresh install local default | synthetic profile; no account; cloud off; network denied; model absent | launch local mode; inspect available local surfaces | `FRESH_LOCAL_DEFAULT`; zero Dora-attributed calls; no login or GMS gate |
| `OFF-SYN-002` | Synthetic capture and local save under airplane mode | deterministic synthetic PCM; `AIRPLANE_MODE`; explicit user start | capture; finalize; save locally | `LOCAL_OPERATION_SUCCEEDED`; durable local source; zero Dora-attributed calls |
| `OFF-SYN-003` | Open saved local source after process death | `OFF-SYN-002` complete; process killed; network denied | restart; open local source | source remains available; no microphone auto-start; zero Dora-attributed calls |
| `OFF-SYN-004` | Generated history and search | fictional generated conversation; network denied | open history; search local index; open result | `LOCAL_OPERATION_SUCCEEDED`; canonical mapping preserved; zero Dora-attributed calls |
| `OFF-SYN-005` | Local task protocol and manual edit | fictional local conversation; network denied | create local task; edit protocol field; reopen | user truth persists; source remains local; zero Dora-attributed calls |
| `OFF-SYN-006` | Clipboard copy | fictional local result; network denied | copy approved local representation | copy completes locally; external network not invoked by Dora; content excluded from evidence |
| `OFF-SYN-007` | Local SAF export sink | fictional local result; local test document provider; network denied | export through SAF; verify local bytes and cleanup | export completes locally; zero Dora-attributed calls; no provider or cloud claim |
| `OFF-SYN-008` | Sharesheet attribution boundary | fictional local result; explicit user handoff | prepare Dora temp; handoff to external target; observe Dora process only | Dora call count remains scoped to Dora UID; external target traffic excluded; DEC-017 temp lifecycle preserved |
| `OFF-SYN-009` | Model absent capability wait | model absent; network denied; local source available | request model-dependent processing; continue non-model flows | processing state `WAITING_MODEL`; local state remains ready or succeeds for non-model flows; no login or download attempt |
| `OFF-SYN-010` | Approved installed model Russian synthetic input | exact `EVALUATION_APPROVED` runtime frozen in harness or APK; exact compatible `EVALUATION_APPROVED` installed weight package; synthetic RU fixture; network denied | run bounded local processing | successful local load and inference; structurally valid local result; zero Dora-attributed calls; quality scoring out of scope |
| `OFF-SYN-011` | Approved installed model English synthetic input | exact `EVALUATION_APPROVED` runtime frozen in harness or APK; exact compatible `EVALUATION_APPROVED` installed weight package; synthetic EN fixture; network denied | run bounded local processing | successful local load and inference; structurally valid local result; zero Dora-attributed calls; quality scoring out of scope |
| `OFF-SYN-012` | Approved installed model mixed synthetic input | exact `EVALUATION_APPROVED` runtime frozen in harness or APK; exact compatible `EVALUATION_APPROVED` installed weight package; synthetic mixed RU-EN fixture; network denied | run bounded local processing | successful local load and inference; structurally valid local result; zero Dora-attributed calls; quality scoring out of scope |
| `OFF-SYN-013` | Queue cloud intent while denied | synthetic non-deletion upload or processing intent; valid future consent profile; `NETWORK_DENIED` | enqueue intent; advance deterministic scheduler | `WAITING_NETWORK`; zero transport attempts; local canonical state unchanged |
| `OFF-SYN-014` | Repeated local work while cloud waits | `OFF-SYN-013` pending; network denied | search; edit; task; copy; export | all eligible local flows remain usable; queue identity stable; zero Dora-attributed calls |
| `OFF-SYN-015` | Process death with queued intent | `OFF-SYN-013` pending; network denied | kill process; restart | same `WAITING_NETWORK` intent restored; no send while denied; no new or stale-consent work; local state digest preserved |
| `OFF-SYN-016` | Reboot with queued intent | `OFF-SYN-013` pending; network denied | reboot; open app | same `WAITING_NETWORK` intent restored; microphone remains off; no send while denied; local state digest preserved |
| `OFF-SYN-017` | Authorized reconnect success | same queued intent; separate future network execution authority; connectivity becomes `AVAILABLE`; current consent, profile and constraints revalidate | reconcile; resume eligible work; send bounded attempts; validate result; apply once | only the previously authorized eligible intent resumes; one logical job; one result; one application; one economic effect; zero duplicates |
| `OFF-SYN-018` | Reconnect lost response and duplicate trigger | `OFF-SYN-017` profile; scripted lost response; duplicate scheduler trigger | replay same logical operation; reconcile receipt | transport attempt may repeat; one logical result and application; no corruption or second effect |
| `OFF-SYN-019` | Forbidden dependency canaries | no account; no GMS; DNS denied; remote config analytics and provider unavailable; frozen Dora UID and process set; declared calibration and measured windows | run one pre-run canary for every call kind in the calibration window; close calibration window; exercise every eligible local flow in the measured window; close measured window; run one post-run canary for every call kind in the calibration window | all canaries appear in the raw monitor total with exact calibration event IDs; calibration events are excluded from the measured scenario total; per-call-kind coverage has no gap; local flows remain usable; measured Dora-attributed forbidden call count is zero |
| `OFF-SYN-020` | Clock advance and expiry independence | local canonical data; network denied; deterministic future clock | advance beyond frozen token lease catalog and remote-config intervals; restart; exercise local flows | safe local defaults persist; no login or remote-config gate; no indefinite support claim |
| `OFF-SYN-021` | Installed model optional capability unavailable | exact `EVALUATION_APPROVED` runtime frozen in harness or APK; exact compatible `EVALUATION_APPROVED` installed weight package; device fails the frozen optional-summary capability predicate; network denied | request optional summary; continue required local flows | processing/capability state `PENDING_CAPABILITY`; local state remains ready or succeeds for required flows; no cloud fallback or login gate; zero Dora-attributed calls |
| `OFF-SYN-022` | Consent revoke while offline without a remote copy | durable queued non-deletion upload or processing intent; `NETWORK_DENIED`; current consent is revoked; no remote copy exists | apply revocation; restart queue observer; exercise local flows | non-deletion queue state `CANCELLED`; zero transport attempts; local canonical data preserved; local flows remain usable |
| `OFF-SYN-023` | Explicit local deletion while offline | fictional local conversation; `NETWORK_DENIED`; no remote copy configured | confirm scoped local deletion; reconcile local indexes and derivatives; restart | local deletion contract outcome is truthful; zero Dora-attributed calls; no remote deletion receipt claim; unrelated local data preserved |
| `OFF-SYN-024` | Explicit local and remote deletion while offline | fictional local conversation with an existing remote copy; `NETWORK_DENIED`; explicit whole-conversation deletion confirmed | complete scoped local deletion; create or preserve idempotent remote-deletion operation; restart | local deletion completes truthfully within scope; remote state `DELETE_PENDING`; one stable deletion identity; zero transport attempts; local completion and remote status remain separate |
| `OFF-SYN-025` | Consent revoke with remote copy while offline | fictional local conversation with an existing remote copy; `NETWORK_DENIED`; cloud consent revoked; no local deletion requested | revoke non-deletion sends; preserve local canonical data; create or preserve idempotent remote-deletion operation; restart | local canonical data remains available; non-deletion work is `CANCELLED`; remote deletion is `DELETE_PENDING` with one stable identity; zero transport attempts; local and remote status remain separate |
| `OFF-SYN-026` | Reconcile remote deletion after reconnect | `OFF-SYN-024` or `OFF-SYN-025` deletion pending; separate future backend/provider and network execution authority; connectivity `AVAILABLE`; current deletion scope and constraints revalidate; deterministic receipt or failure branch frozen | send or replay the same deletion identity; reconcile response or receipt | exactly one remote deletion operation; `DELETED_REMOTE` only after a verified scoped receipt, otherwise `DELETE_PENDING`, `FAILED_RETRYABLE` or `FAILED_FINAL`; local outcome remains unchanged and separate; zero duplicate deletion effects |

## 8. Future machine evidence contract

The future package has these exact files, all currently absent or `NOT_RUN`:

- `execution-profile.json`
- `preflight.json`
- `fixture-manifest.json`
- `dependency-inventory.json`
- `merged-manifest-inventory.json`
- `flow-ledger.json`
- `network-call-ledger.json`
- `queue-idempotency-ledger.json`
- `run-result.json`
- `evidence-index.json`

The future result uses [`benchmark-result.schema.json`](benchmark-result.schema.json). Network call
kinds are exactly `DNS`, `SOCKET`, `HTTP`, `GMS_BIND`, `ACCOUNT_AUTH`, `REMOTE_CONFIG`,
`ANALYTICS` and `PROVIDER`.

The call ledger records `sequence`, `windowClass`, `calibrationEventId`,
`attributedToMeasuredScenario`, app-UID/process/component classes, scenario/action, call kind,
destination class and opaque endpoint ID, consent-profile digest, decision/outcome, retry state and
monotonic offset. Monitor calibration records method, attribution boundary, coverage start/end/gaps,
per-call-kind coverage, pre/post canary observation, raw monitor total, excluded calibration-event
count and measured Dora-attributed forbidden-attempt count. The flow ledger records sequence,
scenario/action, from/to states, outcome, monotonic offset, pre/post canonical-state digests and a
content-free error code. The queue ledger records exactly `operationClass`, `intentIdHash`,
`logicalKeyHash`, `jobIdHash`, `resultIdHash`, `deletionScopeDigest`, `deletionReceiptIdHash`,
`queueState`, `deletionReceiptVerificationOutcome`, `attemptCount`, `replayMarker`, `effectCount`,
`applyCount`, `remoteDeletionEffectCount`, `preLocalStateDigest` and `postLocalStateDigest`.

Public evidence forbids serial/IMEI, advertising ID, account/email/contact, SSID/URL/IP/private
endpoint, absolute local path, raw audio/transcript/meeting content, credential/token/raw
idempotency key, model weight and private packet trace. The future fixture class is
`SYNTHETIC_ONLY`; no generator or execution is authorized by this contract.

## 9. Readiness blockers

| ID | Status | Exact blocker |
|---|---|---|
| `OFF-RDY-01` | `BLOCKED` | The mandatory device-scope conflict requires a versioned pre-run decision. |
| `OFF-RDY-02` | `BLOCKED` | Required physical D4 no-GMS and the rest of the frozen physical matrix are unavailable or unauthorized. |
| `OFF-RDY-03` | `BLOCKED` | An integrated disposable Offline harness covering every required local flow is absent. |
| `OFF-RDY-04` | `BLOCKED` | No exact local model runtime with digest, license, provenance, API and ABI compatibility, and 16-KiB package, load and run evidence is `EVALUATION_APPROVED` and included in a frozen harness or APK, and no exact compatible weight or data package is `EVALUATION_APPROVED` for an approved signed offline side-load. |
| `OFF-RDY-05` | `BLOCKED` | A calibrated app-attributed OS or firewall denial monitor and content-free call ledger are absent. |
| `OFF-RDY-06` | `BLOCKED` | The immutable fixture, scenario, execution profile and fault matrix are not frozen for execution. |
| `OFF-RDY-07` | `BLOCKED` | Offline-owned queue and reconnect integration is absent; VPN I2 does not close it. |
| `OFF-RDY-08` | `BLOCKED` | Retention, redaction and deletion for any raw network trace are unresolved; only synthetic sanitized evidence is currently allowed. |
| `OFF-RDY-09` | `BLOCKED` | Exact merged-manifest, runtime-dependency, APK and non-metric implementation verification for a future harness is absent. |
| `OFF-RDY-10` | `BLOCKED` | Separate implementation scope and review plus later device, network and model execution authorization are withheld. |

Production Legal, independent production Security, provider/cloud choices and production admission
remain outside Stage 0 and intentionally blank.

## 10. Proof and acceptance boundary

This artifact proves only the frozen tracked-tree static inventory/evidence classification, a
versioned prospective requirements/state/scenario/evidence contract, and the explicit conflicts,
blockers and future proof boundary.

It does not prove runtime absence of network calls; working integrated local flows; installed or
missing model behavior; D4, airplane, OS/firewall denial, kill/reboot/reconnect; battery safety;
indefinite operation; device support; readiness; execution; PASS; or production admission.

The contract artifact may be `CONTRACT_COMPLETE` only when strict duplicate-aware JSON, exact
Markdown/JSON semantic parity, links/snapshot/static counts, 25 false authority flags and explicit
Backlog/Stage Status truth all validate. Completion never changes the PoC tuple:
`TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED`.

Exact parity counts are 14 requirements, 5 conflicts, 9 inventory surfaces, 4 proof layers,
5 local states, 7 processing/capability states, 4 connectivity states, 3 model states,
13 queue states, 12 invariants, 26 scenarios, 10 readiness blockers and 25 authority flags.

Artifact-authoring findings are P0/P1/P2 = `0/0/0`; this does not close any of the ten readiness
blockers. Current evidence remains `OBSERVED_NOT_EXECUTION_EVIDENCE`; recommendation is
`BLOCKED_PENDING_SEPARATE_READINESS_DECISIONS_IMPLEMENTATION_AND_EXECUTION`.

## 11. Future decision template — intentionally blank

```yaml
decisionRecordId: ""
decisionTimestamp: ""
mandatoryDeviceSetDecision: ""
boundedDurationProfile: ""
retryAndEnergyPolicy: ""
integratedHarnessScope: ""
modelArtifactApprovalRecord: ""
monitorAndCallLedgerApproval: ""
fixtureScenarioAndFaultManifest: ""
rawTraceRetentionDecision: ""
implementationReview: ""
deviceNetworkAndModelExecutionAuthorization: ""
legalApproval: ""
securityApproval: ""
productionAdmission: ""
formalReviewer: false
```

The blank template is not permission to implement or run anything. A later authorized task must
freeze its values before any implementation or execution and must preserve every unresolved
conflict and blocker until independently closed.
