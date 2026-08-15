# POC-VPN-001 synthetic route and idempotency contract — Stage 0 v0.1

Contract ID: `poc-vpn-synthetic-api-stage0-v0.1`

Disposition: `PROSPECTIVE_CONTRACT_DOSSIER_READY`

Contract artifact: `CONTRACT_COMPLETE`

Backlog item: `POC-VPN-001 = TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED`

Input class: `SYNTHETIC_ONLY`

Recorded: `2026-08-15T13:09:26.9477947+03:00`, `Europe/Moscow`

The exact machine mirror is
[`docs/evidence/poc-vpn-001/contract-record-stage0-v0.1.json`](../evidence/poc-vpn-001/contract-record-stage0-v0.1.json).
Any Markdown/JSON semantic mismatch invalidates both artifacts. This document approves only a
versioned, reversible docs/evidence contract. It does not approve implementation, a server, a
socket, network/VPN/route/device/emulator/provider execution, measured execution, dependencies,
credentials, billing, real data, production admission or merge.

## 1. Frozen source and authority

The source snapshot is public GitHub `main`
`b583b6b3fb183b27de58d5c5630d5b964216385b`, tree
`940074daed41970dd162d5dd76754009c540b8af`, valid verified signature. Exact-main push CI
`31877423988` completed successfully: required `android-bootstrap=success`; `search-smoke=success`
is unrelated Search-only evidence. Open PR count was zero and no supplied SHA/tree/CI drift was
detected before this contract branch was created.

The complete applicable corpus was read in repository precedence: Technical Plan; Design Spec;
Product Decisions (`DEC-009/010/011/012/015/018/020/039`) and owner records; accepted ADRs;
Test Strategy; Backlog; Stage Status; privacy threat model; Dataset Governance; IP policy; Stage 0
PoC Gates/execution order; Capture/Search/Recovery evidence boundaries; README and CONTRIBUTING.
No conflict affects this prospective synthetic contract. `DEC-010`, `DEC-011` and `DEC-012` remain
Proposed; `DEC-039` remains Provisional.

Standing delegation, recorded verbatim:

> Продолжи работу. Все где нужно принимать решения типа РАЗРЕШАЮ делай сама или поручи отдельному
> агенту. Важно, чтобы процесс не останавливался из-за этого

The Project owner delegates ordinary reversible project/test-contract decisions to the OpenAI
Codex coordinator. This approves this prospective contract only. It excludes Legal, Security,
Recovery, dependencies, production, device/emulator/network/VPN/provider execution, measured
execution, real data, credentials/billing, flags and merge authority. Every authority flag in the
machine record is `false`.

Reviewer/model: `OpenAI Codex / GPT-5`

Organization: `OpenAI`

Role: `AI independent POC-VPN prospective contract advisory analyst`

`formalReviewer=false`; no human signature, Legal review or Security review is claimed.

## 2. Current implementation and evidence inventory

| Surface | Frozen fact |
|---|---|
| Production/backend API | Absent |
| `BE-API-001` synthetic server contract implementation | Absent; this document is the prospective contract, not a server |
| Multipart client | Absent |
| Transport fake / loopback HTTP server | Absent |
| VPN/route harness | Absent |
| Billing ledger | Absent; only a future synthetic economic oracle is specified |
| Provider/region runtime | Absent |
| Android Internet permission / network client dependency | Absent |
| Existing isolated PoCs | `:poc:capture`, `:poc:search`, `:poc:recovery`; none implements POC-VPN |
| Explicit non-evidence | Search emulator networking, Actions artifact transfer, build dependency downloads and capture `IdempotentStopGate` are not VPN/route/idempotency evidence |

Local capture, processing, browsing/search/export and access to an existing local result must remain
usable without account, network, GMS or cloud configuration and must never wait on this remote FSM.

## 3. Requirements ledger

| ID | Requirement |
|---|---|
| `VPN-REQ-001` | A valid preselected consent profile binds one synthetic endpoint and one synthetic region before remote work begins. |
| `VPN-REQ-002` | Absent, stale, expired or revoked profile fails closed before any request. |
| `VPN-REQ-003` | Route, network type and VPN state never select, switch or migrate endpoint or region. |
| `VPN-REQ-004` | Local-first workflows remain usable and nonblocking without account, network, GMS or cloud configuration. |
| `VPN-REQ-005` | Every mutating logical operation uses a stable content-free idempotency key bound to its canonical request digest and scope. |
| `VPN-REQ-006` | Create, init-or-refresh, upload part, complete, poll, result, cancel, delete and deletion-receipt operations form one versioned lifecycle. |
| `VPN-REQ-007` | Every part and the complete artifact bind exact byte length and SHA-256; manifest order and contiguous part ordinals are mandatory. |
| `VPN-REQ-008` | Resume reconciles durable server receipts and uploads only missing parts. |
| `VPN-REQ-009` | Expired part URLs refresh only the upload plan generation while preserving job, upload, profile, endpoint and region. |
| `VPN-REQ-010` | Retries are finite, battery-safe, process-death-safe and classified; Retry-After never overrides remaining attempt or elapsed budgets. |
| `VPN-REQ-011` | Lost responses and duplicate requests replay one canonical outcome without a second resource or economic effect. |
| `VPN-REQ-012` | A successful logical workflow commits one job, exactly one result and exactly one synthetic economic event, with zero duplicates. |
| `VPN-REQ-013` | Cancellation resolves deterministically against completion/result commit order and never creates a second terminal effect. |
| `VPN-REQ-014` | After a deletion record is accepted, `DELETE_PENDING` is monotone: cancel, profile invalidation, final errors and retry exhaustion may change only a visible deletion substatus/error, never the primary state, `deletionId` or delete idempotency binding; bounded or explicit manual replay of that same record continues until one verifiable receipt moves it to `DELETED`, and repeats create no second deletion. |
| `VPN-REQ-015` | Logs and evidence are content-free and exclude payload bytes, text, URLs, object keys, IP addresses, tokens, credentials and raw idempotency keys. |
| `VPN-REQ-016` | Fake and loopback evidence is contract-layer evidence only and cannot be reported as physical device, VPN, route, provider or production PASS. |

## 4. Prospective harness options

| ID / option | Evidence value | Blind spots | Determinism | Privacy/security risk | Prerequisites | Reversibility / disposition |
|---|---|---|---|---|---|---|
| `VPN-HRN-OPT-001` `TRANSPORT_FAKE_ORACLE` | Deterministically proves client state, retry, idempotency-key, process-death and ledger decisions without a socket. | No HTTP serialization, multipart framing, DNS, TCP, TLS, OS routing, VPN or provider compatibility. | Highest | Low: in-memory synthetic bytes and content-free evidence only. | Future client port, deterministic clock/scheduler, frozen execution profile and explicit execution authority. | High; disposable adapter. Future reference oracle. |
| `VPN-HRN-OPT-002` `HERMETIC_LOOPBACK_HTTP` | Adds real local HTTP parsing, multipart bytes, connection interruption and lost-response replay. | No physical route, real tunnel, carrier/Wi-Fi handoff, public DNS/TLS, provider or production security proof. | High with fixed seed, loopback-only bind and scripted faults. | Low only if loopback-only, synthetic-only, ephemeral and content-free. External bind is invalid. | Option 001 oracle, loopback guard, synthetic server, fixed fault script, frozen execution profile and explicit execution authority. | High; isolated disposable PoC. Use only after oracle conformance. |
| `VPN-HRN-OPT-003` `PROVIDER_SANDBOX_OR_REFERENCE_ENDPOINT` | Could later test provider-specific HTTP/TLS/upload integration assumptions. | No production region, billing, retention, deletion, availability, route or security proof. | Lower; external service and policy can change. | Higher: outbound traffic, credentials, provider logs, retention and economics. | Resolved `DEC-010/011/012`, BE-LEGAL/provider/consent/auth/delete, Security, synthetic tenant, credentials/billing guard and separate authority. | Medium; replaceable adapter only. `GATED_FUTURE_ONLY`. |

Prospective selection is the ordered combination `VPN-HRN-OPT-001` then `VPN-HRN-OPT-002`.
The fake is the semantic oracle; loopback may later add HTTP/multipart fidelity. Controlled-network,
physical D2/D4/D5 and provider layers remain gated. Selection grants no execution authority.

## 5. Consent profile, canonicalization and privacy

`ConsentProfileBinding-v0.1` contains these immutable fields:
`profileId:string`, `consentReceiptId:string`, `policyVersion:string`,
`artifactClass:enum(SYNTHETIC_BYTES)`, `purpose:enum(STAGE0_CONTRACT_TEST)`, `endpointId:string`,
`regionCode:string`, `tenantFixtureId:string`, `issuedAt:rfc3339`, `expiresAt:rfc3339`,
`revokedAt:rfc3339|null`, `endpointAllowlistDigest:sha256-hex`, and
`profileBindingSha256:sha256-hex`.

`profileBindingSha256` is SHA-256 of `DORA-CJ-v1` over every field except itself. A profile is valid
only when present, schema-valid, exact for artifact/purpose, current in `[issuedAt, expiresAt)`,
unrevoked, allowlisted and equal to every persisted job binding. Missing, stale, expired, revoked,
malformed or mismatched profiles cause `FINAL_REJECT` before transport. DNS, interface,
Wi-Fi/cellular transitions and VPN state cannot mutate endpoint or region. Synthetic labels are
`profile-synthetic-a`, `endpoint-synthetic-a`, `SYN-REGION-A`, `tenant-synthetic-a`; they do not
select RU/global production geography or provider.

`DORA-CJ-v1` is UTF-8 JSON with object keys sorted by Unicode code point, array order preserved,
JSON-escaped strings, base-10 integers without leading zero, lowercase booleans/null, no floats and
no insignificant whitespace. The canonical request digest is:

```text
SHA-256(UTF8(contractId + LF + method + LF + routeTemplate + LF + operationClass + LF
             + profileBindingSha256 + LF + bodyDescriptor))
```

For JSON, `bodyDescriptor=DORA-CJ-v1(nonvolatile request body)`. For a binary part it is
`DORA-CJ-v1({byteLength,sha256,uploadId,partNumber,planGeneration})`. Request ID, attempt number and
timestamps, `Retry-After`, part URL/expiry and connection identity are volatile and excluded.
Server IDs are opaque/content-free/immutable. Evidence records only SHA-256 of an idempotency key.

Canonical schema field catalogs:

| Schema | Exact fields |
|---|---|
| `CreateJobRequest-v0.1` | `schemaVersion:string`; `profileBindingSha256:sha256-hex`; `syntheticTenantId:string`; `fixtureId:string`; `artifactClass:SYNTHETIC_BYTES`; `purpose:STAGE0_CONTRACT_TEST`; `payloadByteLength:positive-integer`; `payloadSha256:sha256-hex` |
| `CreateJobResponse-v0.1` | `schemaVersion:string`; `jobId:opaque-id`; `state:server-state`; `endpointId:string`; `regionCode:string`; `createdAt:rfc3339`; `syntheticEconomicEffectId:opaque-id` |
| `UploadPlanRequest-v0.1` | `schemaVersion:string`; `jobId:opaque-id`; `priorUploadId:opaque-id\|null`; `requestedPlanGeneration:positive-integer`; `partSizeBytes:positive-integer`; `totalByteLength:positive-integer`; `totalSha256:sha256-hex` |
| `UploadPlanResponse-v0.1` | `schemaVersion:string`; `jobId:opaque-id`; `uploadId:opaque-id`; `planGeneration:positive-integer`; `partSizeBytes:positive-integer`; `expiresAt:rfc3339`; `endpointId:string`; `regionCode:string`; `parts:ordered-array(partNumber,urlTokenId,expectedByteLength)` |
| `UploadPartRequest-v0.1` | `uploadId:opaque-id`; `partNumber:positive-integer`; `planGeneration:positive-integer`; `byteLength:positive-integer`; `sha256:sha256-hex`; `body:synthetic-bytes` |
| `UploadPartResponse-v0.1` | `schemaVersion:string`; `uploadId:opaque-id`; `partNumber:positive-integer`; `byteLength:positive-integer`; `sha256:sha256-hex`; `partReceiptId:opaque-id` |
| `CompleteUploadRequest-v0.1` | `schemaVersion:string`; `jobId:opaque-id`; `uploadId:opaque-id`; `manifest:ordered-array(partNumber,byteLength,sha256,partReceiptId)`; `totalByteLength:positive-integer`; `totalSha256:sha256-hex` |
| `CompleteUploadResponse-v0.1` | `schemaVersion:string`; `jobId:opaque-id`; `uploadId:opaque-id`; `commitId:opaque-id`; `state:server-state` |
| `JobStatusResponse-v0.1` | `schemaVersion:string`; `jobId:opaque-id`; `state:server-state`; `revision:nonnegative-integer`; `statusEtag:opaque-id`; `retryHintClass:enum\|null` |
| `ResultResponse-v0.1` | `schemaVersion:string`; `jobId:opaque-id`; `resultId:opaque-id`; `artifactClass:SYNTHETIC_BYTES`; `byteLength:positive-integer`; `sha256:sha256-hex`; `body:synthetic-bytes` |
| `CancelResponse-v0.1` | `schemaVersion:string`; `jobId:opaque-id`; `cancelReceiptId:opaque-id`; `state:server-state`; `winningTerminalCommit:enum(CANCELLED,RESULT_READY)` |
| `DeleteResponse-v0.1` | `schemaVersion:string`; `deletionId:opaque-id`; `conversationFixtureId:opaque-id`; `state:server-state` |
| `DeletionReceiptResponse-v0.1` | `schemaVersion:string`; `deletionId:opaque-id`; `deletionReceiptId:opaque-id\|null`; `state:server-state`; `verifiedAbsent:boolean` |
| `ErrorResponse-v0.1` | `schemaVersion:string`; `errorCode:enum`; `retryClass:enum`; `operationClass:enum`; `requestDigest:sha256-hex`; `contentFreeDetailCode:string` |

Logs/evidence must exclude payload bytes, recognized text/meeting content, part URLs, object keys,
IP addresses, tokens, credentials, billing instruments, raw idempotency keys, private audio and user
identifiers.

## 6. Protocol lifecycle

| ID | Operation | Method and route | Request → response | Idempotency | Success |
|---|---|---|---|---|---|
| `VPN-OP-001` | `CREATE_JOB` | `POST /v1/processing-jobs` | `CreateJobRequest-v0.1` → `CreateJobResponse-v0.1` | Required | 201 |
| `VPN-OP-002` | `INIT_OR_REFRESH_UPLOAD` | `POST /v1/processing-jobs/{jobId}/uploads` | `UploadPlanRequest-v0.1` → `UploadPlanResponse-v0.1` | New key per plan generation | 201 |
| `VPN-OP-003` | `UPLOAD_PART` | `PUT /synthetic-upload/{uploadId}/{planGeneration}/{partNumber}` | `UploadPartRequest-v0.1` → `UploadPartResponse-v0.1` | Stable per upload/part | 200 |
| `VPN-OP-004` | `COMPLETE_UPLOAD` | `POST /v1/processing-jobs/{jobId}/uploads:complete` | `CompleteUploadRequest-v0.1` → `CompleteUploadResponse-v0.1` | Required | 202 |
| `VPN-OP-005` | `POLL_JOB` | `GET /v1/processing-jobs/{jobId}` | none → `JobStatusResponse-v0.1` | Read-only | 200 |
| `VPN-OP-006` | `FETCH_RESULT` | `GET /v1/processing-jobs/{jobId}/result` | none → `ResultResponse-v0.1` | Read-only stable result | 200 |
| `VPN-OP-007` | `CANCEL_JOB` | `POST /v1/processing-jobs/{jobId}:cancel` | none → `CancelResponse-v0.1` | Required | 202 |
| `VPN-OP-008` | `DELETE_CLOUD_COPY` | `DELETE /v1/conversations/{conversationFixtureId}/cloud-copy` | none → `DeleteResponse-v0.1` | Required | 202 |
| `VPN-OP-009` | `POLL_DELETION_RECEIPT` | `GET /v1/deletions/{deletionId}` | none → `DeletionReceiptResponse-v0.1` | Read-only stable receipt | 200 |

Every mutating logical operation uses stable `Idempotency-Key`; every transport attempt uses a new
`X-Client-Request-Id`. Scope is `(syntheticTenantId, profileBindingSha256, operationClass,
Idempotency-Key)`.

| Case | Required outcome |
|---|---|
| Same scope/key + same digest | Original application status and canonical response digest, identical IDs/effect; replay adds `Idempotency-Replayed=true`. |
| Same scope/key + different digest | `409 IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`; no resource, transition, receipt or effect. |
| Lost response | Replay same key/digest and recover the committed outcome; never create a new logical operation. |
| Duplicate completion | Original 202/`commitId`; no second queue, result or economic effect. |
| Repeated delete | Same `deletionId` and receipt; even a new key after deletion resolves to the same record without another delete effect. |
| Cross tenant/profile | No reuse/migration; non-enumerating `404 RESOURCE_NOT_FOUND_OR_NOT_AUTHORIZED`; no effect. |
| Same key, different operation class | Distinct scope, though clients still generate a fresh key for unambiguous diagnostics. |

A future hermetic server must atomically and durably commit its idempotency record, resource state
and effect, preserving them across injected process death for the full authorized run.

Multipart fixture part size is exactly 1024 bytes; no production part size is selected. Ordinals are
contiguous `1..N`; arrival order may differ but completion manifest order may not. All non-last parts
are exactly 1024 bytes; last is `(0,1024]`. Server verifies length and SHA-256 before one immutable
receipt. Same upload/ordinal/length/hash returns that receipt; different length/hash returns 409 and
cannot replace it. Missing/duplicate/noncontiguous ordinals, wrong size/digest or wrong overall
length/digest return 422 and never enter processing. Resume reconciles receipts and sends only
missing parts. Expiry creates the next plan generation with a new plan key but the same job/upload,
profile, endpoint and region; old URLs remain invalid and no URL enters evidence.

Successful-path invariants:

| ID | Expression |
|---|---|
| `VPN-INV-OUT-001` | `uniqueLogicalJobCount == 1` after successful create |
| `VPN-INV-OUT-002` | `committedResultCount in {0,1}` always and `== 1` after successful processing |
| `VPN-INV-OUT-003` | `syntheticEconomicEffectCount == 1` after first logical job commit |
| `VPN-INV-OUT-004` | `duplicateJobCount == 0` |
| `VPN-INV-OUT-005` | `duplicateResultCount == 0` |
| `VPN-INV-OUT-006` | `duplicateSyntheticEconomicEffectCount == 0` |
| `VPN-INV-OUT-007` | Endpoint and region equal the preselected profile for the full workflow |
| `VPN-INV-OUT-008` | `verifiedDeletionReceiptCount in {0,1}` always and `== 1` after successful delete |
| `VPN-INV-OUT-009` | `localWorkflowBlockedByRemoteStateCount == 0` |
| `VPN-INV-OUT-010` | `forbiddenContentLogFieldCount == 0` |

The only economic oracle is one `SYNTHETIC_PROCESSING_COMMIT`, atomically created with the first
logical create. Expected successful count is one and duplicate count zero. It has no currency,
tariff, provider-billing or production-billing meaning.

## 7. Retry contract

Exact classes are `WAIT_NETWORK`, `BACKOFF_REPLAY`, `REFRESH_UPLOAD_PLAN`,
`REPLAY_SAME_OPERATION`, `USER_ACTION_REQUIRED`, `FINAL_REJECT`.

- Preserve key/digest except plan refresh, which uses a new generation key while preserving upload
  identity and profile binding.
- Persist `nextAttemptAt` from a monotonic clock; never busy-loop or wake without a due attempt,
  valid profile and required network condition.
- Honor a valid `Retry-After` only inside both remaining attempt and elapsed budgets. Missing,
  malformed or excessive values use the bounded local policy or exhaust safely.
- Stop after `maxAttempts` or `maxElapsedMs`. Exhaustion is `FAILED_FINAL` for ordinary operations;
  an accepted deletion remains `DELETE_PENDING` with `DELETE_MANUAL_RETRY_REQUIRED` and schedules no
  further work until explicit user action grants one new positive finite attempt budget for the same
  deletion record.
- TLS trust/name, consent, schema, endpoint/region, checksum, authorization and key/payload mismatch
  are final or user-action failures and are never silently retried.
- A pending deletion never busy-loops: automatic attempts stop at the frozen budget, and lack of
  revalidation or user action leaves the durable deletion pending without wakeups.
- No production timeout, SLA, quota or tariff is defined.

The future execution profile must freeze positive finite deterministic values for
`connectTimeoutMs`, `writeTimeoutMs`, `readTimeoutMs`, `callTimeoutMs`, `maxAttempts`,
`maxElapsedMs`, `backoffBaseMs`, `backoffCapMs`, `maxPlanGenerations` and `jitterSeed`. Every current
value and the profile itself are `NOT_AUTHORIZED`.

## 8. Deterministic state machines

Client states (16): `BLOCKED_NO_PROFILE`, `READY`, `CREATING`, `WAITING_UPLOAD`, `UPLOADING`,
`WAITING_NETWORK`, `RETRY_SCHEDULED`, `COMPLETING`, `REMOTE_PROCESSING`, `RESULT_AVAILABLE`,
`RESULT_VERIFIED`, `DELETE_PENDING`, `DELETED`, `CANCEL_PENDING`, `CANCELLED`, `FAILED_FINAL`.
Terminal states are `DELETED` and `FAILED_FINAL`. `CANCELLED` is durably settled and non-retryable,
but is not deletion-terminal: only a later explicit delete under a valid profile may move it to
`DELETE_PENDING`. `DELETED` and `FAILED_FINAL` are absorbing for this logical workflow; a later new
workflow requires a new FSM instance and new keys.

The five named source sets below are exact; transitions do not use wildcard sources:

- `profileInvalidEligibleStates`: `READY`, `CREATING`, `WAITING_UPLOAD`, `UPLOADING`,
  `WAITING_NETWORK`, `RETRY_SCHEDULED`, `COMPLETING`, `REMOTE_PROCESSING`, `RESULT_AVAILABLE`,
  `RESULT_VERIFIED`, `CANCEL_PENDING`.
- `cancelEligibleStates`: `CREATING`, `WAITING_UPLOAD`, `UPLOADING`, `WAITING_NETWORK`,
  `RETRY_SCHEDULED`, `COMPLETING`, `REMOTE_PROCESSING`, `RESULT_AVAILABLE`, `RESULT_VERIFIED`.
- `retryBudgetEligibleStates`: `CREATING`, `WAITING_UPLOAD`, `UPLOADING`, `WAITING_NETWORK`,
  `RETRY_SCHEDULED`, `COMPLETING`, `REMOTE_PROCESSING`, `RESULT_AVAILABLE`, `CANCEL_PENDING`.
- `remoteResponseEligibleStates`: `CREATING`, `WAITING_UPLOAD`, `UPLOADING`, `WAITING_NETWORK`,
  `RETRY_SCHEDULED`, `COMPLETING`, `REMOTE_PROCESSING`, `RESULT_AVAILABLE`, `CANCEL_PENDING`.
- `deletionPendingProtectedStates`: `DELETE_PENDING`.

| ID | From | Event | Guard | To / resume | Retry class |
|---|---|---|---|---|---|
| `VPN-C-TR-001` | `profileInvalidEligibleStates` | `PROFILE_ABSENT_STALE_EXPIRED_REVOKED_OR_MISMATCHED` | current state is `profileInvalidEligibleStates` | `BLOCKED_NO_PROFILE` | `FINAL_REJECT` |
| `VPN-C-TR-002` | `BLOCKED_NO_PROFILE` | `VALID_PROFILE_DURABLY_SELECTED` | profile validates | `READY` | — |
| `VPN-C-TR-003` | `READY` | `START_REMOTE_SYNTHETIC_WORK` | preflight PASS and separate execution authorization | `CREATING` | — |
| `VPN-C-TR-004` | `CREATING` | `CREATE_COMMITTED` | canonical outcome verified | `WAITING_UPLOAD` | — |
| `VPN-C-TR-005` | `CREATING` | `WAIT_NETWORK` | budget remains | `WAITING_NETWORK`; resume `CREATING` | — |
| `VPN-C-TR-006` | `CREATING` | `BACKOFF_REPLAY` | budget remains | `RETRY_SCHEDULED`; resume `CREATING` | — |
| `VPN-C-TR-007` | `WAITING_UPLOAD` | `UPLOAD_PLAN_READY` | binding and generation verified | `UPLOADING` | — |
| `VPN-C-TR-008` | `WAITING_UPLOAD` | `WAIT_NETWORK` | budget remains | `WAITING_NETWORK`; resume `WAITING_UPLOAD` | — |
| `VPN-C-TR-009` | `WAITING_UPLOAD` | `BACKOFF_REPLAY` | budget remains | `RETRY_SCHEDULED`; resume `WAITING_UPLOAD` | — |
| `VPN-C-TR-010` | `UPLOADING` | `PART_RECEIPT_VERIFIED_MORE_MISSING` | receipt reconciled | `UPLOADING` | — |
| `VPN-C-TR-011` | `UPLOADING` | `ALL_PART_RECEIPTS_VERIFIED` | manifest complete | `COMPLETING` | — |
| `VPN-C-TR-012` | `UPLOADING` | `WAIT_NETWORK` | budget remains | `WAITING_NETWORK`; resume `UPLOADING` | — |
| `VPN-C-TR-013` | `UPLOADING` | `BACKOFF_REPLAY` | budget remains | `RETRY_SCHEDULED`; resume `UPLOADING` | — |
| `VPN-C-TR-014` | `UPLOADING` | `UPLOAD_URL_EXPIRED` | plan-generation budget remains | `WAITING_UPLOAD` | — |
| `VPN-C-TR-015` | `COMPLETING` | `COMPLETE_COMMITTED` | `commitId` verified | `REMOTE_PROCESSING` | — |
| `VPN-C-TR-016` | `COMPLETING` | `WAIT_NETWORK` | budget remains | `WAITING_NETWORK`; resume `COMPLETING` | — |
| `VPN-C-TR-017` | `COMPLETING` | `BACKOFF_REPLAY` | budget remains | `RETRY_SCHEDULED`; resume `COMPLETING` | — |
| `VPN-C-TR-018` | `REMOTE_PROCESSING` | `POLL_PENDING` | revision nondecreasing | `REMOTE_PROCESSING` | — |
| `VPN-C-TR-019` | `REMOTE_PROCESSING` | `POLL_RESULT_READY` | stable `resultId` | `RESULT_AVAILABLE` | — |
| `VPN-C-TR-020` | `REMOTE_PROCESSING` | `WAIT_NETWORK` | budget remains | `WAITING_NETWORK`; resume `REMOTE_PROCESSING` | — |
| `VPN-C-TR-021` | `REMOTE_PROCESSING` | `BACKOFF_REPLAY` | budget remains | `RETRY_SCHEDULED`; resume `REMOTE_PROCESSING` | — |
| `VPN-C-TR-022` | `RESULT_AVAILABLE` | `RESULT_CHECKSUM_VALID` | length and SHA-256 match | `RESULT_VERIFIED` | — |
| `VPN-C-TR-023` | `RESULT_AVAILABLE` | `RESULT_CHECKSUM_INVALID` | terminal integrity failure | `FAILED_FINAL` | `FINAL_REJECT` |
| `VPN-C-TR-024` | `RESULT_VERIFIED` | `DELETE_REQUESTED` | separate explicit synthetic action | `DELETE_PENDING`; substatus `DELETE_RECEIPT_POLL_ELIGIBLE` | — |
| `VPN-C-TR-025` | `DELETE_PENDING` | `DELETION_RECEIPT_PENDING` | revision nondecreasing | `DELETE_PENDING`; substatus `DELETE_RECEIPT_POLL_ELIGIBLE`; preserve deletion record | — |
| `VPN-C-TR-026` | `DELETE_PENDING` | `DELETION_RECEIPT_VERIFIED` | `verifiedAbsent=true` and stable receipt | `DELETED` | — |
| `VPN-C-TR-027` | `DELETE_PENDING` | `WAIT_NETWORK` | automatic attempt budget remains | `DELETE_PENDING`; substatus `DELETE_WAITING_NETWORK`; preserve deletion record | `WAIT_NETWORK` |
| `VPN-C-TR-028` | `DELETE_PENDING` | `BACKOFF_REPLAY` | automatic attempt budget remains and `nextAttemptAt` is finite | `DELETE_PENDING`; substatus `DELETE_RETRY_SCHEDULED`; preserve deletion record | `BACKOFF_REPLAY` |
| `VPN-C-TR-029` | `cancelEligibleStates` | `CANCEL_REQUESTED` | current state is `cancelEligibleStates` and profile valid | `CANCEL_PENDING` | — |
| `VPN-C-TR-030` | `CANCEL_PENDING` | `CANCEL_COMMIT_WON` | stable cancel receipt | `CANCELLED` | — |
| `VPN-C-TR-031` | `CANCEL_PENDING` | `RESULT_COMMIT_WON` | server returns `ALREADY_TERMINAL` with stable `resultId` | `RESULT_AVAILABLE` | — |
| `VPN-C-TR-032` | `CANCEL_PENDING` | `WAIT_NETWORK` | budget remains | `WAITING_NETWORK`; resume `CANCEL_PENDING` | — |
| `VPN-C-TR-033` | `WAITING_NETWORK` | `NETWORK_AVAILABLE` | profile valid and budget remains | `$persistedResumeState` | — |
| `VPN-C-TR-034` | `RETRY_SCHEDULED` | `BACKOFF_DUE` | profile valid and budget remains | `$persistedResumeState` | — |
| `VPN-C-TR-035` | `retryBudgetEligibleStates` | `RETRY_BUDGET_EXHAUSTED` | current state is `retryBudgetEligibleStates` | `FAILED_FINAL` | — |
| `VPN-C-TR-036` | `RESULT_AVAILABLE` | `RESULT_FETCH_RESPONSE_LOST_OR_RETRYABLE` | same job/profile/endpoint/region and retry budget remains | `RETRY_SCHEDULED`; resume `RESULT_AVAILABLE` | `REPLAY_SAME_OPERATION` |
| `VPN-C-TR-037` | `remoteResponseEligibleStates` | `FINAL_TLS_TRUST_OR_NAME_REJECT` | TLS trust or hostname validation failed | `FAILED_FINAL` | `FINAL_REJECT` |
| `VPN-C-TR-038` | `remoteResponseEligibleStates` | `FINAL_SCHEMA_OR_UNSUPPORTED_REJECT` | schema, API version or input format is rejected | `FAILED_FINAL` | `FINAL_REJECT` |
| `VPN-C-TR-039` | `UPLOADING`/`COMPLETING` | `FINAL_CHECKSUM_OR_MANIFEST_REJECT` | part, manifest or overall integrity predicate failed | `FAILED_FINAL` | `FINAL_REJECT` |
| `VPN-C-TR-040` | `CREATING`/`WAITING_UPLOAD`/`UPLOADING`/`COMPLETING`/`CANCEL_PENDING` | `FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH` | same scope and key bind a different canonical request digest | `FAILED_FINAL` | `FINAL_REJECT` |
| `VPN-C-TR-041` | `remoteResponseEligibleStates` | `FINAL_CROSS_TENANT_OR_PROFILE_REJECT` | non-enumerating scope rejection received | `FAILED_FINAL` | `FINAL_REJECT` |
| `VPN-C-TR-042` | `CANCELLED` | `DELETE_REQUESTED` | separate explicit synthetic delete and valid profile | `DELETE_PENDING`; substatus `DELETE_RECEIPT_POLL_ELIGIBLE` | — |
| `VPN-C-TR-043` | `CANCEL_PENDING` | `BACKOFF_REPLAY` | budget remains | `RETRY_SCHEDULED`; resume `CANCEL_PENDING` | — |
| `VPN-C-TR-044` | `DELETE_PENDING` | `CANCEL_REQUESTED` | accepted deletion record exists | `DELETE_PENDING`; same substatus and deletion record; `REJECTED_NO_STATE_CHANGE`; `CANCEL_NOT_APPLICABLE_DELETE_PENDING` | — |
| `VPN-C-TR-045` | `DELETE_PENDING` | `PROFILE_ABSENT_STALE_EXPIRED_REVOKED_OR_MISMATCHED` | accepted deletion record exists | `DELETE_PENDING`; `DELETE_REVALIDATION_REQUIRED`; preserve deletion record; `DELETE_PROFILE_REVALIDATION_REQUIRED` | `USER_ACTION_REQUIRED` |
| `VPN-C-TR-046` | `DELETE_PENDING` | `FINAL_TLS_TRUST_OR_NAME_REJECT` | accepted deletion exists and TLS trust/name validation failed | `DELETE_PENDING`; `DELETE_USER_ACTION_REQUIRED`; preserve deletion record; `DELETE_TLS_TRUST_OR_NAME_REJECTED` | `USER_ACTION_REQUIRED` |
| `VPN-C-TR-047` | `DELETE_PENDING` | `FINAL_SCHEMA_OR_UNSUPPORTED_REJECT` | accepted deletion exists and schema, API version or response format is rejected | `DELETE_PENDING`; `DELETE_USER_ACTION_REQUIRED`; preserve deletion record; `DELETE_SCHEMA_OR_FORMAT_REJECTED` | `USER_ACTION_REQUIRED` |
| `VPN-C-TR-048` | `DELETE_PENDING` | `FINAL_CHECKSUM_OR_MANIFEST_REJECT` | accepted deletion exists and deletion-response integrity failed | `DELETE_PENDING`; `DELETE_USER_ACTION_REQUIRED`; preserve deletion record; `DELETE_RESPONSE_INTEGRITY_REJECTED` | `USER_ACTION_REQUIRED` |
| `VPN-C-TR-049` | `DELETE_PENDING` | `FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH` | accepted deletion exists and replay request digest mismatches | `DELETE_PENDING`; `DELETE_USER_ACTION_REQUIRED`; preserve deletion record; `DELETE_IDEMPOTENCY_PAYLOAD_MISMATCH` | `USER_ACTION_REQUIRED` |
| `VPN-C-TR-050` | `DELETE_PENDING` | `FINAL_CROSS_TENANT_OR_PROFILE_REJECT` | accepted deletion exists and non-enumerating scope rejection is received | `DELETE_PENDING`; `DELETE_REVALIDATION_REQUIRED`; preserve deletion record; `DELETE_SCOPE_REVALIDATION_REQUIRED` | `USER_ACTION_REQUIRED` |
| `VPN-C-TR-051` | `DELETE_PENDING` | `RETRY_BUDGET_EXHAUSTED` | accepted deletion exists and automatic attempt or elapsed budget is exhausted | `DELETE_PENDING`; `DELETE_MANUAL_RETRY_REQUIRED`; preserve deletion record; no automatic wakeup; `DELETE_FINITE_BUDGET_EXHAUSTED` | `USER_ACTION_REQUIRED` |
| `VPN-C-TR-052` | `DELETE_PENDING` | `DELETE_REVALIDATED_OR_USER_ACTION_CONFIRMED` | profile valid and explicit action grants one positive finite attempt budget from a revalidation, user-action or manual-retry substatus | `DELETE_PENDING`; `DELETE_RECEIPT_POLL_ELIGIBLE`; preserve deletion record | `REPLAY_SAME_OPERATION` |

For one durable state/event snapshot, filter by explicit source set and true guard. Lower numeric
priority wins; within one priority, the first listed transition wins. At most one selected event,
priority and transition ID is atomically persisted with the state change. If none is eligible, record
a content-free `NO_STATE_CHANGE` and retain the state.

| Priority | Arbitration group | Exact secondary order |
|---:|---|---|
| 0 | `DURABLE_SERVER_OUTCOME_RECONCILIATION` | `004`, `007`, `010`, `011`, `015`, `019`, `022`, `026`, `030`, `031` |
| 10 | `PROFILE_FAIL_CLOSED` | `001`, `045` |
| 20 | `IMMEDIATE_FINAL_REJECT` | `023`, `037`, `038`, `039`, `040`, `041`, `046`, `047`, `048`, `049`, `050` |
| 30 | `RETRY_BUDGET_EXHAUSTED` | `035`, `051` |
| 40 | `EXPLICIT_USER_CANCEL_OR_DELETE` | `024`, `029`, `042`, `044`, `052` |
| 50 | `NONTERMINAL_PROGRESS` | `018`, `025` |
| 60 | `BOUNDED_RETRY_OR_WAIT` | `005`, `006`, `008`, `009`, `012`, `013`, `014`, `016`, `017`, `020`, `021`, `027`, `028`, `032`, `036`, `043` |
| 70 | `WAKE_OR_RESUME` | `033`, `034` |
| 80 | `PROFILE_READY_OR_START` | `002`, `003` |

`DELETED` and `FAILED_FINAL` have no outgoing client transitions. `CANCELLED` ignores profile-invalid
and automatic events; it permits only `VPN-C-TR-042` under a valid profile.

`DELETE_PENDING` is excluded from every general cancel, profile-invalid, final-reject and exhaustion
source set. Its explicit transitions retain `DELETE_PENDING` or accept one verified receipt into
`DELETED`; none may enter `BLOCKED_NO_PROFILE`, `CANCEL_PENDING`, `WAITING_NETWORK`,
`RETRY_SCHEDULED` or `FAILED_FINAL`.

The durable pending-deletion record contains `jobId`, `deleteIdempotencyKeyLedgerRef`,
`deleteIdempotencyKeyDigest`, `deleteRequestDigest`, `deletionId`, `profileBindingSha256`,
`endpointId`, `regionId` and `lastReceiptRevision`. Every `DELETE_PENDING` transition preserves that
identity. Its exact visible substatuses are `DELETE_RECEIPT_POLL_ELIGIBLE`,
`DELETE_WAITING_NETWORK`, `DELETE_RETRY_SCHEDULED`, `DELETE_REVALIDATION_REQUIRED`,
`DELETE_USER_ACTION_REQUIRED` and `DELETE_MANUAL_RETRY_REQUIRED`. Its exact content-free error codes
are `CANCEL_NOT_APPLICABLE_DELETE_PENDING`, `DELETE_PROFILE_REVALIDATION_REQUIRED`,
`DELETE_TLS_TRUST_OR_NAME_REJECTED`, `DELETE_SCHEMA_OR_FORMAT_REJECTED`,
`DELETE_RESPONSE_INTEGRITY_REJECTED`, `DELETE_IDEMPOTENCY_PAYLOAD_MISMATCH`,
`DELETE_SCOPE_REVALIDATION_REQUIRED` and `DELETE_FINITE_BUDGET_EXHAUSTED`.

Cancel during `DELETE_PENDING` is rejected with no state/substatus or record change. Wait/backoff may
schedule only a due attempt inside the current positive finite attempt and elapsed budgets. Profile or
scope invalidation requires revalidation; final transport/response rejection requires user action;
exhaustion requires manual retry and schedules no automatic wakeup. Only explicit user action after a
valid profile may grant one new positive finite budget, replaying the same delete/receipt operation and
binding. A stable verified-absent receipt for the same `deletionId` moves to `DELETED`; otherwise the
record remains durably pending without a busy loop, and unconditional liveness is not claimed.

Immediate final-reject coverage is exact:

| Family | Transition IDs | Fault IDs | Error codes |
|---|---|---|---|
| `TLS_TRUST_OR_HOSTNAME` | `037`, `046` | `006` | — |
| `SCHEMA_API_VERSION_OR_UNSUPPORTED_FORMAT` | `038`, `047` | — | `SCHEMA_VALIDATION_FAILED`, `UNSUPPORTED_FORMAT`, `UPGRADE_REQUIRED` |
| `CHECKSUM_OR_MANIFEST` | `023`, `039`, `048` | `014`, `029`, `030` | `CHECKSUM_MISMATCH`, `MANIFEST_INVALID`, `OVERALL_CHECKSUM_MISMATCH` |
| `IDEMPOTENCY_PAYLOAD_MISMATCH` | `040`, `049` | `009`, `013` | `IDEMPOTENCY_KEY_PAYLOAD_MISMATCH` |
| `CROSS_TENANT_OR_PROFILE` | `001`, `041`, `045`, `050` | `003` | `RESOURCE_NOT_FOUND_OR_NOT_AUTHORIZED` |

After process death, validate profile, load the checksum-protected state/operation ledger, reconcile
server receipts with the same keys, and restore exactly that state or its waiting wrapper. A deletion
record is always restored directly to `DELETE_PENDING` with its persisted visible substatus, never a
general wrapper. Never reset to `READY` or implicitly create a job or deletion. Remote work is durable
queued work and never blocks the local workflow.

Server states (12): `ABSENT`, `CREATED`, `WAITING_UPLOAD`, `UPLOADING`, `UPLOAD_COMPLETE`, `QUEUED`,
`PROCESSING`, `RESULT_READY`, `DELIVERED`, `DELETE_PENDING`, `DELETED`, `CANCELLED`. Terminal states
contain only `DELETED`. `CANCELLED` is durably settled with no processing retry, but remains eligible
for one later explicit `DELETE_COMMIT` into `DELETE_PENDING`.

| ID | From | Event / guard | To |
|---|---|---|---|
| `VPN-S-TR-001` | `ABSENT` | valid new create commit | `CREATED` |
| `VPN-S-TR-002` | `CREATED` | upload plan commit; binding preserved | `WAITING_UPLOAD` |
| `VPN-S-TR-003` | `WAITING_UPLOAD` | first valid part commit | `UPLOADING` |
| `VPN-S-TR-004` | `UPLOADING` | next new valid part commit | `UPLOADING` |
| `VPN-S-TR-005` | `UPLOADING` | valid ordered manifest and overall digest | `UPLOAD_COMPLETE` |
| `VPN-S-TR-006` | `UPLOAD_COMPLETE` | one queue record commits | `QUEUED` |
| `VPN-S-TR-007` | `QUEUED` | one worker lease starts | `PROCESSING` |
| `VPN-S-TR-008` | `PROCESSING` | first result commit wins | `RESULT_READY` |
| `VPN-S-TR-009` | `RESULT_READY` | stable result fetch | `DELIVERED` |
| `VPN-S-TR-010` | `DELIVERED` | repeated read-only fetch | `DELIVERED` |
| `VPN-S-TR-011` | `CREATED`/`WAITING_UPLOAD`/`UPLOADING`/`UPLOAD_COMPLETE`/`QUEUED`/`PROCESSING` | cancel commit wins first | `CANCELLED` |
| `VPN-S-TR-012` | `RESULT_READY`/`DELIVERED` | cancel after result → 409 | `$sameState` |
| `VPN-S-TR-013` | `RESULT_READY`/`DELIVERED`/`CANCELLED` | one deletion record commits | `DELETE_PENDING` |
| `VPN-S-TR-014` | `DELETE_PENDING` | verified-absent receipt commits | `DELETED` |
| `VPN-S-TR-015` | `DELETED` | repeat delete/stable IDs | `DELETED` |
| `VPN-S-TR-016` | `CREATED`/`WAITING_UPLOAD`/`UPLOADING`/`UPLOAD_COMPLETE`/`QUEUED`/`PROCESSING`/`RESULT_READY`/`DELIVERED`/`DELETE_PENDING`/`DELETED`/`CANCELLED` | read-only poll/nondecreasing revision | `$sameState` |
| `VPN-S-TR-017` | `CREATED`/`WAITING_UPLOAD`/`UPLOADING`/`UPLOAD_COMPLETE`/`QUEUED`/`PROCESSING`/`RESULT_READY`/`DELIVERED`/`CANCELLED` | same create replay | `$sameState` |
| `VPN-S-TR-018` | `WAITING_UPLOAD`/`UPLOADING` | identical part replay | `$sameState` |
| `VPN-S-TR-019` | `UPLOAD_COMPLETE`/`QUEUED`/`PROCESSING`/`RESULT_READY`/`DELIVERED` | identical complete replay | `$sameState` |
| `VPN-S-TR-020` | `WAITING_UPLOAD` | next plan generation/same binding | `WAITING_UPLOAD` |

Create atomically persists the idempotency record, job, binding and one synthetic economic event.
Each later commit atomically persists its idempotency record, state and effect. Server restart reloads
those ledgers and never reconstructs a second resource/effect. `DELETED` is absorbing; its read and
repeat-delete self-transitions cannot create an effect.

The deterministic transition traces are exactly `VPN-TRACE-001..005`:

| ID | Preconditions / deterministic selected path | Expected |
|---|---|---|
| `VPN-TRACE-001` `LOST_RESULT_REPLAY` | From `RESULT_AVAILABLE` with stable job/profile/endpoint/region and one attempt remaining: `RESULT_FETCH_RESPONSE_LOST_OR_RETRYABLE` selects `VPN-C-TR-036` -> `RETRY_SCHEDULED` with resume `RESULT_AVAILABLE`; `BACKOFF_DUE` selects `VPN-C-TR-034` -> `RESULT_AVAILABLE`; `RESULT_CHECKSUM_VALID` selects `VPN-C-TR-022` -> `RESULT_VERIFIED`. `RETRY_BUDGET_EXHAUSTED` instead selects `VPN-C-TR-035` from `RESULT_AVAILABLE` -> `FAILED_FINAL`. | Same fetch operation and binding; at most one committed result. |
| `VPN-TRACE-002` `IMMEDIATE_FINAL_REJECT_FAMILIES` | `CREATING` + `FINAL_TLS_TRUST_OR_NAME_REJECT` -> `VPN-C-TR-037`; `REMOTE_PROCESSING` + `FINAL_SCHEMA_OR_UNSUPPORTED_REJECT` -> `VPN-C-TR-038`; `UPLOADING` + `FINAL_CHECKSUM_OR_MANIFEST_REJECT` -> `VPN-C-TR-039`; `CREATING` + `FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH` -> `VPN-C-TR-040`; `RESULT_AVAILABLE` + `FINAL_CROSS_TENANT_OR_PROFILE_REJECT` -> `VPN-C-TR-041`. | Every case reaches `FAILED_FINAL` in one transition, without `WAITING_NETWORK` or `RETRY_SCHEDULED`. |
| `VPN-TRACE-003` `CANCEL_THEN_EXPLICIT_DELETE` | Valid profile: `CREATING` + `CANCEL_REQUESTED` -> `VPN-C-TR-029`/`CANCEL_PENDING`; `CANCEL_COMMIT_WON` -> `VPN-C-TR-030` plus `VPN-S-TR-011`/`CANCELLED`; `DELETE_REQUESTED` -> `VPN-C-TR-042` plus `VPN-S-TR-013`/`DELETE_PENDING`; `DELETION_RECEIPT_PENDING` -> `VPN-C-TR-025`; `DELETION_RECEIPT_VERIFIED` -> `VPN-C-TR-026` plus `VPN-S-TR-014`/`DELETED`. | One cancellation receipt, one deletion record and one verified deletion receipt; `CANCELLED` is deletion-eligible and `DELETED` absorbing. |
| `VPN-TRACE-004` `TERMINAL_PROFILE_AND_PRIORITY_OVERLAP` | `PROFILE_ABSENT_STALE_EXPIRED_REVOKED_OR_MISMATCHED` in `DELETED`, `CANCELLED` or `FAILED_FINAL` selects no transition and preserves state. Simultaneous: in `CREATING`, `CREATE_COMMITTED` (priority 0) beats profile-invalid (10), selecting `VPN-C-TR-004` -> `WAITING_UPLOAD`; in `CREATING`, profile-invalid (10) beats `FINAL_TLS_TRUST_OR_NAME_REJECT` (20) and `WAIT_NETWORK` (60), selecting `VPN-C-TR-001` -> `BLOCKED_NO_PROFILE`; in `CANCEL_PENDING`, `CANCEL_COMMIT_WON` (0) beats profile-invalid (10), selecting `VPN-C-TR-030` -> `CANCELLED`; in `RESULT_AVAILABLE`, `RESULT_CHECKSUM_INVALID` (20) beats `RETRY_BUDGET_EXHAUSTED` (30) and `RESULT_FETCH_RESPONSE_LOST_OR_RETRYABLE` (60), selecting `VPN-C-TR-023` -> `FAILED_FINAL`. | Explicit source sets plus numeric/secondary arbitration select exactly one transition. |
| `VPN-TRACE-005` `DELETE_PENDING_PRESERVATION_AND_RECOVERY` | Start `DELETE_PENDING`/`DELETE_RECEIPT_POLL_ELIGIBLE` with one opaque deletion token and stable delete-key/request/profile-binding digests. `CANCEL_REQUESTED` selects `C044`, is `REJECTED_NO_STATE_CHANGE` and preserves the substatus/record. Profile-invalid selects `C045` -> `DELETE_REVALIDATION_REQUIRED`; explicit valid revalidation selects `C052` -> poll eligible. TLS final reject selects `C046` -> `DELETE_USER_ACTION_REQUIRED`; action selects `C052`; schema final reject selects `C047` -> user action; action selects `C052`. Exhaustion selects `C051` -> `DELETE_MANUAL_RETRY_REQUIRED` with no automatic retry; explicit action selects `C052` and grants one positive finite budget. Receipt pending selects `C025`; the same verified receipt selects `C026` plus `S014` -> `DELETED`. Additional exact cases: checksum/integrity `C048` and idempotency mismatch `C049` retain `DELETE_PENDING`/`DELETE_USER_ACTION_REQUIRED`; cross-scope `C050` retains `DELETE_PENDING`/`DELETE_REVALIDATION_REQUIRED`. | Every step preserves the same deletion record and ID until the verified receipt. No general wrapper, final state replacement, busy retry or second deletion occurs. |

Invalid attempts never influence PASS/FAIL:

| ID | Invalid condition |
|---|---|
| `VPN-INV-001` | Any non-synthetic or user-derived input is selected. |
| `VPN-INV-002` | Execution or measured-execution authority is absent. |
| `VPN-INV-003` | Contract, fault schedule, fixture manifest or positive finite execution profile is not frozen before start. |
| `VPN-INV-004` | A fault, retry, timeout or manual intervention not declared in the run manifest occurs. |
| `VPN-INV-005` | Evidence contains payload, text, URL, object key, IP, token, credential or raw idempotency key. |
| `VPN-INV-006` | Retry is unbounded, busy-looping or exceeds frozen attempt/elapsed budgets. |
| `VPN-INV-007` | Client or server resets without durable-ledger reconciliation after process death. |
| `VPN-INV-008` | Fake/loopback evidence is labelled device, VPN, physical route, provider or production PASS. |
| `VPN-INV-009` | Harness/infrastructure failure is not separated from a contract failure. |
| `VPN-INV-010` | Idempotency record, upload or job is reused across tenant/profile scope. |
| `VPN-INV-011` | Endpoint or region changes after route, network or VPN state changes. |
| `VPN-INV-012` | A required evidence file, digest, sequence number or failure-ledger row is missing or duplicated. |

## 9. Exact fault matrix

All rows use synthetic bytes. `SIMULATED_ROUTE_ONLY` can never support a physical VPN/route verdict.

| ID | Boundary | Injection | Deterministic expected result | Retry class | Proof class |
|---|---|---|---|---|---|
| `VPN-FLT-001` | PREFLIGHT | Consent profile absent | `BLOCKED_NO_PROFILE`; zero transport attempts/effects. | `FINAL_REJECT` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-002` | PREFLIGHT | Profile stale, expired or revoked | `BLOCKED_NO_PROFILE`; zero transport attempts/effects. | `FINAL_REJECT` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-003` | PREFLIGHT | Endpoint/region mismatches persisted profile | Before deletion acceptance enter `BLOCKED_NO_PROFILE` as immediate final reject before transport. During `DELETE_PENDING`, preserve the record and expose `DELETE_REVALIDATION_REQUIRED`. No fallback, switch or migration. | `FINAL_REJECT` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-004` | CREATE | DNS failure before create commit | Wait, replay same create key/digest within budget; at most one job/effect. | `WAIT_NETWORK` | `LOOPBACK_CATEGORY_SIMULATION` |
| `VPN-FLT-005` | INIT_OR_REFRESH | Connect failure before plan commit | Wait, replay same plan key/digest within budget; no second upload. | `WAIT_NETWORK` | `LOOPBACK_CATEGORY_SIMULATION` |
| `VPN-FLT-006` | TLS | Trust or hostname failure | Ordinary active work enters `FAILED_FINAL` immediately. During `DELETE_PENDING`, preserve the record and expose `DELETE_USER_ACTION_REQUIRED`. No insecure fallback or transient automatic retry. | `FINAL_REJECT` | `CATEGORY_ONLY_UNTIL_SECURITY_SCOPE` |
| `VPN-FLT-007` | CREATE | Create committed; success response lost | Replay returns same job/economic effect. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-008` | CREATE | Same key and same payload repeated | Original 201 digest, job/effect; duplicate counters zero. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-009` | CREATE | Same key, different canonical payload | Enter `FAILED_FINAL` immediately after 409 mismatch; no new state/effect. | `FINAL_REJECT` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-010` | UPLOAD_PART | Part interrupted before commit | No receipt; replay same part key/digest and commit at most once. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-011` | UPLOAD_PART | Part committed; receipt lost | Replay same `partReceiptId`; count unchanged. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-012` | UPLOAD_PART | Duplicate same upload/ordinal/length/hash | Same receipt; no replacement/duplicate. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-013` | UPLOAD_PART | Same upload/ordinal, different length/hash | Enter `FAILED_FINAL` immediately after 409 mismatch; committed part immutable. | `FINAL_REJECT` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-014` | UPLOAD_PART | Transmitted checksum mismatch | Enter `FAILED_FINAL` immediately after 422; server emits no receipt/state advance. | `FINAL_REJECT` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-015` | ANY_MUTATION | 429 with valid in-budget `Retry-After` | Persist due time; replay once due inside both budgets. | `BACKOFF_REPLAY` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-016` | ANY_MUTATION | 429 without `Retry-After` | Frozen local backoff; preserve key/digest; stop on exhaustion. | `BACKOFF_REPLAY` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-017` | ANY_MUTATION | 429 malformed/out-of-budget `Retry-After` | Ignore unsafe value; use bounded local policy while budget remains. Ordinary exhaustion enters `FAILED_FINAL`; deletion exhaustion preserves the record in `DELETE_PENDING`/`DELETE_MANUAL_RETRY_REQUIRED`, with no work until explicit action grants one positive finite retry budget. | `BACKOFF_REPLAY` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-018` | ANY_RETRYABLE_OPERATION | Retryable 5xx | Bounded same-operation backoff; no duplicate effect. | `BACKOFF_REPLAY` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-019` | ANY_RETRYABLE_OPERATION | Per-attempt timeout | Unknown commit; same-operation replay and reconcile within budget. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-020` | UPLOAD_PART | Part URL expired | Next plan generation/new plan key; same job/upload/profile/endpoint/region. | `REFRESH_UPLOAD_PLAN` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-021` | ROUTE | Route changes during a part | Attempt may finish/fail; retry same endpoint/profile/region and one receipt. | `REPLAY_SAME_OPERATION` | `SIMULATED_ROUTE_ONLY` |
| `VPN-FLT-022` | ROUTE | Wi-Fi → cellular | No migration; bounded replay and one outcome. | `WAIT_NETWORK` | `SIMULATED_ROUTE_ONLY` |
| `VPN-FLT-023` | ROUTE | Cellular → Wi-Fi | No migration; bounded replay and one outcome. | `WAIT_NETWORK` | `SIMULATED_ROUTE_ONLY` |
| `VPN-FLT-024` | VPN | VPN off → on | No migration; bounded replay and one outcome. | `WAIT_NETWORK` | `SIMULATED_ROUTE_ONLY` |
| `VPN-FLT-025` | VPN | VPN on → off | No migration; bounded replay and one outcome. | `WAIT_NETWORK` | `SIMULATED_ROUTE_ONLY` |
| `VPN-FLT-026` | PROCESS_DEATH | Client dies after receipt before checkpoint | Restore/reconcile; no already committed part becomes a new effect. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-027` | PROCESS_DEATH | Client dies after complete commit before response | Replay same complete key/digest; same commit/one result path. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-028` | COMPLETE | Duplicate complete | Original 202/commit; no second queue/result/effect. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-029` | COMPLETE | Missing/duplicate/out-of-order manifest ordinal | Client enters `FAILED_FINAL` immediately after 422; server stays `UPLOADING` and never queues. | `FINAL_REJECT` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-030` | COMPLETE | Overall length/checksum mismatch | Client enters `FAILED_FINAL` immediately after 422; server stays `UPLOADING` and never queues. | `FINAL_REJECT` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-031` | POLL | Timeout, 5xx or repeated ETag | Bounded read repeat; nondecreasing revision; no effect. | `BACKOFF_REPLAY` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-032` | RESULT | Response lost or repeated fetch | From `RESULT_AVAILABLE`, replay same fetch inside finite budget with job/profile/endpoint/region unchanged; return to `RESULT_AVAILABLE` for identical result or `FAILED_FINAL` on exhaustion; result count one. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-033` | CANCEL | Cancel races result or repeats | First terminal commit wins: cancel-first one receipt/no result; result-first 409 and one stable result. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |
| `VPN-FLT-034` | DELETE_AND_RECEIPT | While one accepted deletion is pending: cancel request, invalid profile, final transport/response rejection, finite-budget exhaustion, revalidation/manual action, then stable receipt | Every interruption preserves `DELETE_PENDING`, the same `deletionId`, idempotency binding and record. Cancel is `REJECTED_NO_STATE_CHANGE`; profile/final errors expose revalidation/user-action substatus; exhaustion schedules no wakeup until one explicit positive finite retry budget. The same verified receipt alone moves to `DELETED`; no second deletion effect. | `REPLAY_SAME_OPERATION` | `FAKE_OR_LOOPBACK` |

Fault-to-transition coverage is total and deterministic. In this table, `Cddd`, `Sddd` and `Tddd`
mean `VPN-C-TR-ddd`, `VPN-S-TR-ddd` and `VPN-TRACE-ddd`; `—` means an empty list.

| Fault | Client transitions | Server transitions | Traces | Additional rule targets |
|---|---|---|---|---|
| `001` | `C001` | — | `T004` | — |
| `002` | `C001` | — | `T004` | — |
| `003` | `C001`, `C045` | — | `T004`, `T005` | — |
| `004` | `C005`, `C033` | — | — | — |
| `005` | `C008`, `C033` | — | — | — |
| `006` | `C037`, `C046` | — | `T002`, `T005` | — |
| `007` | `C006`, `C034`, `C004` | `S017` | — | — |
| `008` | `C004` | `S017` | — | — |
| `009` | `C040` | — | `T002` | — |
| `010` | `C013`, `C034`, `C010` | `S003`, `S004` | — | — |
| `011` | `C013`, `C034`, `C010` | `S018` | — | — |
| `012` | `C010` | `S018` | — | — |
| `013` | `C040` | — | `T002` | — |
| `014` | `C039` | — | `T002` | — |
| `015` | `C006`, `C009`, `C013`, `C017`, `C021`, `C028`, `C043`, `C034` | — | — | — |
| `016` | `C006`, `C009`, `C013`, `C017`, `C021`, `C028`, `C043`, `C034` | — | — | — |
| `017` | `C006`, `C009`, `C013`, `C017`, `C021`, `C028`, `C043`, `C034`, `C035`, `C051`, `C052` | — | `T005` | — |
| `018` | `C006`, `C009`, `C013`, `C017`, `C021`, `C028`, `C043`, `C034` | — | — | — |
| `019` | `C006`, `C009`, `C013`, `C017`, `C021`, `C028`, `C036`, `C043`, `C034`, `C035` | — | `T001` | — |
| `020` | `C014`, `C007` | `S020` | — | — |
| `021` | `C012`, `C013`, `C033`, `C034`, `C010` | `S018` | — | — |
| `022` | `C005`, `C008`, `C012`, `C016`, `C020`, `C027`, `C032`, `C033` | — | — | — |
| `023` | `C005`, `C008`, `C012`, `C016`, `C020`, `C027`, `C032`, `C033` | — | — | — |
| `024` | `C005`, `C008`, `C012`, `C016`, `C020`, `C027`, `C032`, `C033` | — | — | — |
| `025` | `C005`, `C008`, `C012`, `C016`, `C020`, `C027`, `C032`, `C033` | — | — | — |
| `026` | `C010` | `S018` | — | `clientStateMachine.processDeathRule`, `serverStateMachine.processDeathRule` |
| `027` | `C015` | `S019` | — | `clientStateMachine.processDeathRule`, `serverStateMachine.processDeathRule` |
| `028` | `C015` | `S019` | — | — |
| `029` | `C039` | — | `T002` | — |
| `030` | `C039` | — | `T002` | — |
| `031` | `C021`, `C034`, `C018`, `C019` | `S016` | — | — |
| `032` | `C036`, `C034`, `C022`, `C035` | `S010` | `T001` | — |
| `033` | `C029`, `C030`, `C031`, `C043` | `S011`, `S012` | `T003` | — |
| `034` | `C024`, `C042`, `C044`, `C045`, `C046`, `C047`, `C048`, `C049`, `C050`, `C051`, `C052`, `C027`, `C028`, `C025`, `C026` | `S013`, `S014`, `S015`, `S016` | `T003`, `T005` | — |

## 10. Deterministic synthetic fixtures

Part size is 1024. For a valid fixture, concatenate SHA-256 blocks of
`UTF8(contractId + '|' + fixtureId + '|block=' + eight-digit-decimal blockIndex)` and truncate.
`VPN-FIX-007` derives from `VPN-FIX-006` and flips byte offset 1024 with XOR `0x01`.

| ID | Purpose / size / overall SHA-256 | Ordered part `(number:length:sha256)` |
|---|---|---|
| `VPN-FIX-001` | one byte; 1; `a25513c7e0f6eaa80a3337ee18081b9e2ed09e00af8531c8f7bb2542764027e7` | `1:1:a25513c7e0f6eaa80a3337ee18081b9e2ed09e00af8531c8f7bb2542764027e7` |
| `VPN-FIX-002` | short last; 1023; `3d516a78221f801198e8a93a0b7feb28dfe68a4c9abf8ce71a0f7cd761c49883` | `1:1023:3d516a78221f801198e8a93a0b7feb28dfe68a4c9abf8ce71a0f7cd761c49883` |
| `VPN-FIX-003` | exact one part; 1024; `283ccdcd625d21b257d030741772652401c2a96323c38ff90db72a69d90bc1dc` | `1:1024:283ccdcd625d21b257d030741772652401c2a96323c38ff90db72a69d90bc1dc` |
| `VPN-FIX-004` | one full + one; 1025; `be988299821e0a1e5c034717bb16fa1599547c0be7306b1c9fea8ed1495328ab` | `1:1024:defe84dc282e8c154c909b29989cfa547cbd11f2a978fc15dc09954cd8850b6b`<br>`2:1:6d90fbacc073ee0b4c43f3a3291cecda33764f6d66d14224ad60f471f2c8334b` |
| `VPN-FIX-005` | exact two parts; 2048; `a3ee850d2dcfbca80c9d3b25c7f1ce8f9d104f9897678e6f58fb466ea289d932` | `1:1024:f5d3ef66e570ab320e8d13ccba675a4ff3b1df248aba5b6a6d4341992a7fab6c`<br>`2:1024:a1d7cd6d8bcfe3ebd3dbef09bebd24a72ba5b968db76eba70d8470732bfe95c5` |
| `VPN-FIX-006` | two full + 17/resume; 2065; `7c532444ccdf2780a0ab7cf3e63d8d304b4bcd87296ad67ba22a14f6ebe6df29` | `1:1024:bd9b110ac865a980bc399213c439fd3c969cf368f66ac15ee84a39a545f545e3`<br>`2:1024:38f00d24bfad2c1cba218150ac6f7060e8bef8ea506de3e93a630c14512576e8`<br>`3:17:21311d6a193f9d83e374538449ce991e801fde60c87bd186600ba9ca42405f9f` |
| `VPN-FIX-007` | corrupted view; 2065; actual `d512cdca53b76f40e9853d094b345c87e265703583cf11e8e8eed7fbd12aca38`, declared expected `7c532444ccdf2780a0ab7cf3e63d8d304b4bcd87296ad67ba22a14f6ebe6df29` | `1:1024:bd9b110ac865a980bc399213c439fd3c969cf368f66ac15ee84a39a545f545e3`<br>`2:1024:01b894db08c783b9c4ead7636208a51bf797d31fa2176adeebfbcc8d560c3b6e` (declared `38f00d24bfad2c1cba218150ac6f7060e8bef8ea506de3e93a630c14512576e8`)<br>`3:17:21311d6a193f9d83e374538449ce991e801fde60c87bd186600ba9ca42405f9f` |

## 11. Preflight and machine evidence

| ID | Required check | Current status |
|---|---|---|
| `VPN-PRE-001` | Record exact contract commit/tree and artifact digests. | `NOT_RUN` |
| `VPN-PRE-002` | Pass Markdown/JSON parity, strict JSON and unique contiguous ID validation. | `NOT_RUN` |
| `VPN-PRE-003` | Separate execution and measured-execution authority exists. | `NOT_AUTHORIZED` |
| `VPN-PRE-004` | Positive finite execution profile and deterministic seed frozen. | `NOT_AUTHORIZED` |
| `VPN-PRE-005` | Manifest contains only seven bounded synthetic fixtures/digests. | `NOT_RUN` |
| `VPN-PRE-006` | Current unrevoked consent profile binds one allowlisted endpoint/region. | `NOT_RUN` |
| `VPN-PRE-007` | Harness mode allowed; loopback verified loopback-only. | `NOT_AUTHORIZED` |
| `VPN-PRE-008` | Fault schedule is exactly `VPN-FLT-001..034`; manual mutation disabled. | `NOT_RUN` |
| `VPN-PRE-009` | Client/server clocks, schedulers and fault-order seed deterministic. | `NOT_RUN` |
| `VPN-PRE-010` | Ledgers empty or match an explicitly resumed attempt. | `NOT_RUN` |
| `VPN-PRE-011` | Content-free logger rejects forbidden fields. | `NOT_RUN` |
| `VPN-PRE-012` | Evidence destination and retention TTL separately approved. | `NOT_AUTHORIZED` |
| `VPN-PRE-013` | No credentials, provider account, billing instrument, real endpoint/data configured. | `NOT_RUN` |
| `VPN-PRE-014` | Proof label limited to fake/loopback; no device/VPN/provider/production PASS. | `NOT_RUN` |

Current preflight necessarily fails closed at `VPN-PRE-003`, `004`, `007` and `012`.

Future package names and current status:

| File | Status |
|---|---|
| `contract.json` | `NOT_RUN` |
| `fixture-manifest.json` | `NOT_RUN` |
| `fault-matrix.json` | `NOT_RUN` |
| `execution-profile.json` | `NOT_AUTHORIZED` |
| `preflight.json` | `NOT_RUN` |
| `run-result.json` | `NOT_RUN` |
| `client-state-ledger.json` | `NOT_RUN` |
| `server-state-ledger.json` | `NOT_RUN` |
| `idempotency-ledger.json` | `NOT_RUN` |
| `synthetic-economic-effect-ledger.json` | `NOT_RUN` |
| `failure-ledger.json` | `NOT_RUN` |
| `evidence-index.json` | `NOT_RUN` |

Required content-free field groups:

- Run metadata: `schemaVersion`, `contractId`, run/attempt IDs, contract commit/tree, harness and
  environment class, authorization ID, start/finish, result/proof scope, profile/tenant/endpoint/
  region, fixture/fault/execution-profile digests, tool versions and device/provider/production
  evidence statuses.
- State ledgers: sequence/event ID, machine, from/event/guard/to, operation, attempt, monotonic
  offset and state digest.
- Idempotency ledger: sequence, tenant/profile, operation class, key SHA-256, request/response
  digests, resource ID, replay marker and effect count.
- Synthetic economic ledger: sequence, job/effect IDs and type, commit sequence, duplicate
  suppression and total count.
- Failure ledger: sequence, fault/operation/attempt, category, retry class/source, next monotonic
  attempt, remaining budgets, terminal code and content-free detail code.
- Evidence index: relative path, media type, byte length, SHA-256, record count, status and explicit
  synthetic/forbidden-content booleans.

Evidence destination/retention is `UNRESOLVED_NOT_AUTHORIZED`. Result taxonomy is:

| Result | Meaning |
|---|---|
| `NOT_RUN` | No authorized valid attempt exists. |
| `INCONCLUSIVE` | Valid attempt exists but evidence scope/completeness cannot decide every applicable predicate. |
| `PASS` | Valid future attempt satisfies every applicable frozen predicate; label must include bounded proof class. |
| `FAIL` | Valid future attempt violates at least one applicable frozen invariant. |
| `INVALID_ATTEMPT` | Any `VPN-INV-001..012` condition; cannot influence PASS/FAIL. |

Current result is `NOT_RUN`.

## 12. Proof and acceptance boundary

A future transport fake could prove client FSM/resume choices, canonical digests/keys, finite retry
classification, one-job/result/economic behavior against the fake and evidence shape. A future
loopback server could add real HTTP serialization, multipart bytes/order/checksum, local socket
interrupt/lost-response behavior, client/server FSM agreement and loopback restart reconciliation.

Neither proves D2/D4/D5 physical routing, real VPN on/off, Wi-Fi/cellular handoff, public DNS/TLS or
pinning, provider residency/availability/retention/delete/billing, production auth/tenancy/security,
SLA or API conformance. A separately authorized controlled-network protocol and physical D2/D4/D5
campaign are mandatory for any device/VPN/route verdict. Search emulator networking and Actions
downloads are explicitly not POC-VPN evidence.

This contract artifact may be accepted separately as `CONTRACT_COMPLETE` when Markdown/JSON parity,
deterministic IDs/schemas/FSMs/faults/fixtures, authority boundaries and backlog/status locators are
validated. That does not make the PoC READY or DONE. `POC-VPN-001` remains `TODO`, `NOT_READY`,
`NOT_RUN`, `NOT_AUTHORIZED`; synthetic server implementation remains absent and no PASS is claimed.

## 13. Gated decisions and findings

The contract deliberately does not choose:

| ID | Unresolved gated question |
|---|---|
| `VPN-GATE-001` | RU backend/provider/region under `DEC-010` and BE-LEGAL/BE-PROVIDER-001 |
| `VPN-GATE-002` | Global backend/provider/region under `DEC-011` and BE-LEGAL/BE-PROVIDER-002 |
| `VPN-GATE-003` | Cloud retention TTL and verified delete under `DEC-012`/BE-DELETE-001 |
| `VPN-GATE-004` | Production billing model and dedup ledger |
| `VPN-GATE-005` | Cloud E2EE requirement and key custody |
| `VPN-GATE-006` | Production authN/authZ, tenancy and certificate pinning |
| `VPN-GATE-007` | Production admission of `BE-API-001` or provider adapter |
| `VPN-GATE-008` | Authorized physical D2/D4/D5 devices, networks, VPNs and transitions |
| `VPN-GATE-009` | Positive finite timeouts/retry/backoff/plan/jitter values per environment |
| `VPN-GATE-010` | Evidence destination/retention/access controls and future flags |

Findings at contract freeze:

- P0: none.
- P1 `VPN-FND-P1-001`: `POC-OFFLINE-001` is TODO; integrated reconnect is not evidenced.
- P1 `VPN-FND-P1-002`: no synthetic server/client/fake/loopback implementation or authority exists.
- P1 `VPN-FND-P1-003`: `DEC-010/011/012` and Legal/provider/consent/delete gates remain open.
- P1 `VPN-FND-P1-004`: production protection, auth, tenancy, retention/delete and pinning unapproved.
- P1 `VPN-FND-P1-005`: no authorized physical D2/D4/D5 availability or evidence.
- P2 `VPN-FND-P2-001`: positive finite execution-profile and evidence-retention values unfrozen.
- P2 `VPN-FND-P2-002`: provider sandbox, production billing/SLA and API admission deferred.

Disposition remains `PROSPECTIVE_CONTRACT_DOSSIER_READY`; there is no authoritative conflict and no
safe basis for a PoC PASS.

## 14. Future decision template — approvals intentionally blank

```yaml
templateId: POC-VPN-001-FUTURE-EXECUTION-DECISION-v0.1
decisionRecordId: ""
decisionTimestamp: ""
decisionOwnerOrCoordinator: ""
harnessOptionApproval: ""
serverBoundaryApproval: ""
idempotencyContractApproval: ""
faultMatrixApproval: ""
retryExecutionProfileApproval: ""
consentProfileBindingApproval: ""
allowedEnvironmentClasses: []
evidenceDestinationAndRetentionApproval: ""
authorityFlagsConfirmation: ""
gatedQuestionDispositions: []
formalReviewer: false
legalApproval: ""
securityApproval: ""
deviceExecutionApproval: ""
providerExecutionApproval: ""
productionAdmissionApproval: ""
mergeApproval: ""
```

This blank template cannot itself authorize execution. The current machine record preserves all
execution, measured, device, network, VPN, provider, production, dependency, data, billing, flag and
merge authority flags as `false`, and all execution/device/provider/production evidence as
`NOT_RUN` or `NOT_AUTHORIZED`.
