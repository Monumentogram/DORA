# POC-OFFLINE-001 I1 pure-host oracle Definition of Ready — Stage 0 v0.1

Scope ID: `OFF-I1-SCOPE-001`

Machine mirror: [`docs/evidence/poc-offline-001/i1-host-oracle-scope-stage0-v0.1.json`](../evidence/poc-offline-001/i1-host-oracle-scope-stage0-v0.1.json)

Disposition: `PROSPECTIVE_HOST_ORACLE_SCOPE_READY`

Artifact state: `SCOPE_CONTRACT_COMPLETE`

Backlog truth: `POC-OFFLINE-001 = TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED`

Recorded: `2026-08-16T09:49:16.9967484+03:00`, `Europe/Moscow`

This dossier defines the Definition of Ready for a later, separately authorized pure-host Offline
state/ledger oracle. It is docs/evidence only. It does not authorize or implement that oracle, run
an Offline experiment, close a readiness blocker, admit a dependency, or claim device, emulator,
network, model, provider, Android, production, Legal or Security evidence. Any semantic mismatch
between this Markdown file and its strict JSON mirror invalidates both artifacts.

## 1. Frozen snapshot and source boundary

The dossier branch starts at GitHub `main`
`78e9dd07d616989987118f26bb16ebb9932ddb2b`, tree
`f403bf60b86273ee8a2634ed5e8530c9d4af20e4`. GitHub reports a valid verified signature. Exact-main
post-merge CI run `31931582444` completed successfully: required `android-bootstrap=success` and
unrelated Search-only `search-smoke=success`. Open PR count was zero before the branch was created.

The semantic parent is the merged Offline readiness contract:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `docs/stage0/DORA_MVP1_POC_OFFLINE_READINESS_CONTRACT_STAGE0_V0_1.md` | 45377 | `9a905eabbcd75601fc598cccc001dcff56a5b6b65eeb936f2a9c4602d658682a` |
| `docs/evidence/poc-offline-001/readiness-contract-stage0-v0.1.json` | 58124 | `a564cf9031f610006327c374baff82983de2ea9ba4b6c9d13d6185e7967a6dae` |

Applicable sources were reconciled in repository precedence: Technical Plan; Design Spec; Product
Decisions `DEC-009`, `DEC-012`, `DEC-013`, `DEC-015`, `DEC-017`, `DEC-020` and owner records;
accepted ADRs; Test Strategy; Backlog; Stage Status; privacy threat model; Dataset Governance; IP
policy; Stage 0 PoC gates/execution order; README and CONTRIBUTING. No source conflict blocks this
scope dossier. The existing `OFF-CFL-001..005` conflicts still block execution claims and remain
outside I1 closure.

Reviewer/model: `OpenAI Codex / GPT-5`

Organization: `OpenAI`

Role: `AI independent POC-OFFLINE I1 pure-host oracle scope analyst`

`formalReviewer=false`. All 25 authority flags in the machine mirror are `false`.

## 2. Scope result

The only permitted later implementation is a disposable, pure-host, standard-library-only semantic
oracle. It may model five-axis state vectors, deterministic reducer decisions, content-free ledgers,
replay/effect invariants and immutable in-memory snapshot/restore. The future implementation location,
toolchain digest and package name must be frozen by its separate implementation task; this dossier
creates no module or source path.

The following are explicitly excluded:

1. Android application or feature integration.
2. Android SDK, Room, SQLite, FTS, WorkManager, Binder, AccountManager or GMS APIs.
3. Network, socket, DNS, HTTP, VPN, route, TLS, provider or backend behavior.
4. Filesystem persistence, wall clock, randomness, sleeping, background threads or uncontrolled
   scheduling.
5. Audio, transcript, participant, title, URI, filesystem path, packet, credential, token, model or
   user-derived fixtures.
6. Device, emulator, process-kill, reboot, OS/firewall monitor, model, battery or physical route
   execution.
7. Dependency, compiler plugin, runtime, model, provider, production API or feature admission.
8. Backlog or Stage Status transition, readiness claim, Offline PASS or closure of any `OFF-RDY`
   blocker.
9. Reuse of the VPN client/server FSM as the normative Offline reducer.
10. Legal, Security, Recovery or formal-human approval.
11. A production data schema, database migration or wire protocol.
12. Real data or raw public evidence.

## 3. I1 requirements ledger

| ID | Requirement |
|---|---|
| `OFF-I1-REQ-001` | The future oracle is Offline-owned and models the parent contract's exact five orthogonal axes; it does not import the VPN FSM as authority. |
| `OFF-I1-REQ-002` | Inputs, clock, IDs, connectivity, consent/profile/model eligibility, retry budget and faults are injected deterministic values. |
| `OFF-I1-REQ-003` | One reducer input selects at most one rule by `(priority, secondaryOrder, ruleId)`; no match returns explicit `NO_STATE_CHANGE`. |
| `OFF-I1-REQ-004` | The reducer preserves the exact five-key canonical state-vector encoding and its SHA-256 digest rule. |
| `OFF-I1-REQ-005` | The parent `contractId`, exact 12-field flow ledger, exact 19-field queue ledger, inherited identifier formulas, lifecycle/nullability rules and flow-to-queue intent equality remain exact; raw identifiers never enter evidence. |
| `OFF-I1-REQ-006` | Unresolved hash, non-deletion transition, invalid-input, counter, digest-correlation, restore-decoding and revalidation semantics remain fail-closed blockers until a later versioned decision freezes them; implementation may not guess them. |
| `OFF-I1-REQ-007` | Exactly 26 semantic traces project the 26 parent scenarios and exactly 67 action events bijectively project their ordered action slots without deduplication. |
| `OFF-I1-REQ-008` | The exact 15-row remote-deletion mapping, six pending substatuses, eight content-free errors and phase-derived hash invariants are preserved. |
| `OFF-I1-REQ-009` | Same logical key plus same canonical input replays the same outcome with zero new effects; a different canonical input returns categorical mismatch, `NO_STATE_CHANGE` and zero effects. |
| `OFF-I1-REQ-010` | Every effect counter is in `{0,1}`; local canonical truth is unchanged by wait, replay, restart or an unapplied remote candidate. |
| `OFF-I1-REQ-011` | Immutable in-memory snapshot/restore yields the same state vector, ledgers, rule result and counters for the same injected continuation. |
| `OFF-I1-REQ-012` | Evidence is typed, synthetic and content-free; monitor/model/capture/export traces validate only reducer and ledger shape. |
| `OFF-I1-REQ-013` | The 13 operation labels are versioned I1 semantic labels only; lifecycle, calibration and restart actions use `null`. |
| `OFF-I1-REQ-014` | Any missing pin, duplicate ID, incomplete coverage, nondeterministic input, forbidden API/data, invalid vector/deletion phase or inflated evidence claim fails closed. |

## 4. Frozen inherited catalogs

The exact ordered state-vector keys are `local`, `processingCapability`, `connectivity`, `model`,
`queue`.

| Axis | Exact values |
|---|---|
| `local` (5) | `FRESH_LOCAL_DEFAULT`, `LOCAL_READY`, `LOCAL_OPERATION_RUNNING`, `LOCAL_OPERATION_SUCCEEDED`, `LOCAL_OPERATION_FAILED_SCOPED` |
| `processingCapability` (7) | `PROCESSING_NOT_REQUESTED`, `PROCESSING_QUEUED`, `PROCESSING_ACTIVE`, `WAITING_MODEL`, `PENDING_CAPABILITY`, `PROCESSING_SUCCEEDED`, `PROCESSING_FAILED_SCOPED` |
| `connectivity` (4) | `NETWORK_DENIED`, `AIRPLANE_MODE`, `AVAILABLE`, `RECONNECTING` |
| `model` (3) | `MODEL_NOT_INSTALLED`, `MODEL_INSTALLED_APPROVED`, `MODEL_UNAVAILABLE_OR_INVALID` |
| `queue` (13) | `LOCAL_ONLY`, `PENDING_UPLOAD`, `WAITING_NETWORK`, `UPLOADING`, `REMOTE_PROCESSING`, `RESULT_AVAILABLE`, `APPLIED`, `DELETE_PENDING`, `DELETED_REMOTE`, `CONFLICT`, `FAILED_RETRYABLE`, `FAILED_FINAL`, `CANCELLED` |

Pending deletion substatuses (6): `DELETE_RECEIPT_POLL_ELIGIBLE`, `DELETE_WAITING_NETWORK`,
`DELETE_RETRY_SCHEDULED`, `DELETE_REVALIDATION_REQUIRED`, `DELETE_USER_ACTION_REQUIRED`,
`DELETE_MANUAL_RETRY_REQUIRED`.

Content-free deletion errors (8): `CANCEL_NOT_APPLICABLE_DELETE_PENDING`,
`DELETE_PROFILE_REVALIDATION_REQUIRED`, `DELETE_TLS_TRUST_OR_NAME_REJECTED`,
`DELETE_SCHEMA_OR_FORMAT_REJECTED`, `DELETE_RESPONSE_INTEGRITY_REJECTED`,
`DELETE_IDEMPOTENCY_PAYLOAD_MISMATCH`, `DELETE_SCOPE_REVALIDATION_REQUIRED`,
`DELETE_FINITE_BUDGET_EXHAUSTED`.

Network call kinds (8), used only as semantic evidence labels: `DNS`, `SOCKET`, `HTTP`,
`GMS_BIND`, `ACCOUNT_AUTH`, `REMOTE_CONFIG`, `ANALYTICS`, `PROVIDER`.

I1 operation labels (13): `LOCAL_CAPTURE`, `LOCAL_STORAGE`, `LOCAL_HISTORY`, `LOCAL_SEARCH`,
`LOCAL_TASK`, `LOCAL_EDIT`, `LOCAL_COPY`, `LOCAL_EXPORT`, `REQUIRED_MODEL_PROCESSING`,
`OPTIONAL_SUMMARY_REQUEST`, `NON_DELETION_CLOUD_INTENT`, `LOCAL_DELETE`,
`DELETE_CLOUD_COPY`. These labels do not admit a production operation catalog or API.

## 5. Deterministic reducer and hash boundary

One reducer input contains `scenarioId`, `actionId`, `eventId`, the exact `preStateVector`, injected
monotonic time and deterministic IDs, scripted guards, and immutable prior ledgers. One result
contains `selectedRuleId`, a categorical `outcome`, the exact `postStateVector`, typed ledger deltas,
and `effectCount`, `applyCount`, `remoteDeletionEffectCount`. A total order of
`(priority, secondaryOrder, ruleId)` resolves overlap. Zero eligible rules returns
`selectedRuleId=null`, `outcome=NO_STATE_CHANGE`, identical pre/post vectors and zero deltas/effects.

Canonical state encoding is UTF-8 JSON with the five keys emitted in the exact order above,
JSON-escaped string values and no insignificant whitespace. `preStateDigest` and `postStateDigest`
are lowercase hexadecimal SHA-256 of those exact bytes.

Inherited identifier formulas are exact:

- `processingRequestIdHash = lowercaseHex(SHA-256(UTF8(contractId + LF + "PROCESSING_REQUEST" + LF + opaqueProcessingRequestId)))`;
- `queueIntentIdHash = lowercaseHex(SHA-256(UTF8(contractId + LF + "QUEUE_INTENT" + LF + opaqueQueueIntentId)))`;
- `intentIdHash = lowercaseHex(SHA-256(UTF8(contractId + LF + "QUEUE_INTENT" + LF + opaqueQueueIntentId)))`;
- `LF=U+000A`; inputs receive no normalization or whitespace insertion;
- for the same intent, flow `queueIntentIdHash ==` queue-ledger `intentIdHash`.

The inherited identifier domain is exact `contractId=poc-offline-readiness-stage0-v0.1`.

The flow-ledger field set is exact and ordered (12): `sequence`, `scenarioId`, `actionId`,
`preStateVector`, `postStateVector`, `outcome`, `monotonicOffsetMs`, `preStateDigest`,
`postStateDigest`, `processingRequestIdHash`, `queueIntentIdHash`, `contentFreeErrorCode`.

The queue-ledger field set is exact and ordered (19): `operationClass`, `intentIdHash`,
`logicalKeyHash`, `jobIdHash`, `resultIdHash`, `deletionScopeDigest`, `deletionIdHash`,
`deletionReceiptIdHash`, `queueState`, `deletionSubstatus`, `contentFreeDeletionErrorCode`,
`deletionReceiptVerificationOutcome`, `attemptCount`, `replayMarker`, `effectCount`, `applyCount`,
`remoteDeletionEffectCount`, `preLocalStateDigest`, `postLocalStateDigest`.

Inherited lifecycle rules are exact:

- `processingRequestIdHash` and `queueIntentIdHash` are immutable for the same logical request or
  intent and null only when the corresponding axis has no request or queue intent;
- flow `queueIntentIdHash` equals queue `intentIdHash` for the same intent;
- every remote-deletion row uses `operationClass=DELETE_CLOUD_COPY`;
- `logicalKeyHash` and `deletionScopeDigest` are required and immutable from durable enqueue;
- `deletionIdHash` is null before an accepted response and required/immutable afterward;
- `deletionReceiptIdHash` and `deletionReceiptVerificationOutcome` are null until the exact scoped
  receipt verifies; after verification the receipt hash is required/immutable and outcome is
  `VERIFIED`;
- `deletionSubstatus` is required only for `DELETE_PENDING`, null for `DELETED_REMOTE` and all
  non-deletion rows, and otherwise one of the exact six values;
- `contentFreeDeletionErrorCode` is nullable, limited to the exact eight values when present and
  null for `DELETED_REMOTE`;
- every deletion non-receipt row remains `DELETE_PENDING`; only a verified scoped receipt permits
  `DELETED_REMOTE`; `FAILED_RETRYABLE` and `FAILED_FINAL` are non-deletion only;
- every remote-deletion row follows the exact 15-row mapping in section 6.

The parent contract does not freeze exact byte preimages for `logicalKeyHash`, `jobIdHash`,
`resultIdHash`, `deletionScopeDigest`, `deletionIdHash` or `deletionReceiptIdHash`. Their domain
separation, field order, separators and null encoding are `UNRESOLVED_FAIL_CLOSED`. A later
versioned decision must freeze all six before any I1 implementation; this dossier deliberately does
not invent them.

The parent also does not freeze a total non-deletion event→state table, exact invalid-transition
dispositions, complete attempt/effect/apply/delete counter algebra, equality between queue-local and
flow state digests, strict snapshot decoding/equality, or typed consent/profile/runtime revalidation
outcomes. These seven decision groups are `UNRESOLVED_FAIL_CLOSED`. The 26/67 projection below
defines coverage and vocabulary only; it is not an executable transition table. A later versioned
contract or accepted ADR must close every group before implementation.

Deletion phase is derived, never a sixth state axis:

- `PRE_ACCEPTANCE`: `queue=DELETE_PENDING`, required immutable `logicalKeyHash` and
  `deletionScopeDigest`, `deletionIdHash=null`, `deletionReceiptIdHash=null`;
- `POST_ACCEPTANCE`: `queue=DELETE_PENDING`, the same required immutable logical/scope hashes,
  immutable non-null `deletionIdHash`, `deletionReceiptIdHash=null`;
- `VERIFIED`: `queue=DELETED_REMOTE`, immutable deletion and receipt hashes,
  `deletionReceiptVerificationOutcome=VERIFIED`;
- `NOT_APPLICABLE`: non-deletion row.

## 6. Exact remote-deletion event projection

| Event/outcome class | Queue | Substatus | ID outcome | Error | Receipt | Deterministic rule |
|---|---|---|---|---|---|---|
| `DURABLE_ENQUEUE_WHILE_NETWORK_DENIED` | `DELETE_PENDING` | `DELETE_WAITING_NETWORK` | `NULL` | null | null | Pre-acceptance: `deletionIdHash=null`; no transport attempt occurs while denied. |
| `PRE_ACCEPTANCE_RESUME` | `DELETE_PENDING` | `DELETE_RETRY_SCHEDULED` | `NULL` | null | null | Before acceptance, revalidation, user-action confirmation or manual-retry confirmation preserves `deletionIdHash=null` and schedules only one bounded eligible acceptance attempt. |
| `ACCEPTED_RESPONSE_OR_RECEIPT_POLL_PENDING` | `DELETE_PENDING` | `DELETE_RECEIPT_POLL_ELIGIBLE` | `REQUIRED_IMMUTABLE` | null | null | After acceptance, `deletionIdHash` is required and immutable; `deletionReceiptIdHash` remains null. |
| `NETWORK_DENIED_AFTER_ACCEPTANCE` | `DELETE_PENDING` | `DELETE_WAITING_NETWORK` | `REQUIRED_IMMUTABLE` | null | null | Post-acceptance: preserve required immutable `deletionIdHash`; no transport attempt occurs while denied. |
| `RETRYABLE_FAILURE_OR_BACKOFF` | `DELETE_PENDING` | `DELETE_RETRY_SCHEDULED` | `PRESERVE_PHASE` | null | null | Preserve the current acceptance phase and schedule only bounded eligible work. |
| `PROFILE_INVALID` | `DELETE_PENDING` | `DELETE_REVALIDATION_REQUIRED` | `PRESERVE_PHASE` | `DELETE_PROFILE_REVALIDATION_REQUIRED` | null | Preserve the deletion record and require profile revalidation. |
| `SCOPE_INVALID` | `DELETE_PENDING` | `DELETE_REVALIDATION_REQUIRED` | `PRESERVE_PHASE` | `DELETE_SCOPE_REVALIDATION_REQUIRED` | null | Preserve the deletion record and require scope revalidation. |
| `FINAL_TLS_TRUST_OR_NAME_REJECT` | `DELETE_PENDING` | `DELETE_USER_ACTION_REQUIRED` | `PRESERVE_PHASE` | `DELETE_TLS_TRUST_OR_NAME_REJECTED` | null | Preserve the deletion record; no automatic retry. |
| `FINAL_SCHEMA_OR_FORMAT_REJECT` | `DELETE_PENDING` | `DELETE_USER_ACTION_REQUIRED` | `PRESERVE_PHASE` | `DELETE_SCHEMA_OR_FORMAT_REJECTED` | null | Preserve the deletion record; no automatic retry. |
| `FINAL_RESPONSE_INTEGRITY_REJECT` | `DELETE_PENDING` | `DELETE_USER_ACTION_REQUIRED` | `PRESERVE_PHASE` | `DELETE_RESPONSE_INTEGRITY_REJECTED` | null | Preserve the deletion record; no automatic retry. |
| `FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH` | `DELETE_PENDING` | `DELETE_USER_ACTION_REQUIRED` | `PRESERVE_PHASE` | `DELETE_IDEMPOTENCY_PAYLOAD_MISMATCH` | null | Preserve the deletion record; no automatic retry. |
| `RETRY_BUDGET_EXHAUSTED` | `DELETE_PENDING` | `DELETE_MANUAL_RETRY_REQUIRED` | `PRESERVE_PHASE` | `DELETE_FINITE_BUDGET_EXHAUSTED` | null | Preserve the deletion record and schedule no automatic wakeup. |
| `CANCEL_REQUESTED_WHILE_DELETE_PENDING` | `DELETE_PENDING` | `PRESERVE_CURRENT` | `PRESERVE_PHASE` | `CANCEL_NOT_APPLICABLE_DELETE_PENDING` | null | Return `REJECTED_NO_STATE_CHANGE`; preserve the current substatus and record with zero state change. |
| `DELETE_REVALIDATED_OR_USER_ACTION_CONFIRMED_AFTER_ACCEPTANCE` | `DELETE_PENDING` | `DELETE_RECEIPT_POLL_ELIGIBLE` | `REQUIRED_IMMUTABLE` | null | null | Post-acceptance: preserve required immutable `deletionIdHash`; an explicit revalidation, user-action or manual-retry grant supplies one new positive finite attempt budget. |
| `VERIFIED_SCOPED_RECEIPT` | `DELETED_REMOTE` | null | `REQUIRED_IMMUTABLE` | null | `VERIFIED` | Require immutable `deletionReceiptIdHash`; preserve identity and create no second deletion effect. |

`PRESERVE_PHASE` means null before durable acceptance evidence and the same required immutable hash
after acceptance. `PRESERVE_CURRENT` is a transition directive, not a stored seventh substatus.
All remote-deletion events use I1 operation label `DELETE_CLOUD_COPY`.

## 7. Exact 67 action slots

Repeated action text is intentionally not deduplicated. `null` operation labels identify lifecycle,
calibration, monitoring or compound non-operation actions.

| Action ID | Parent scenario | Ordinal | I1 operation label | Exact parent action text |
|---|---|---:|---|---|
| `OFF-I1-ACT-001-01` | `OFF-SYN-001` | 1 | null | launch local mode |
| `OFF-I1-ACT-001-02` | `OFF-SYN-001` | 2 | null | inspect available local surfaces |
| `OFF-I1-ACT-002-01` | `OFF-SYN-002` | 1 | `LOCAL_CAPTURE` | capture |
| `OFF-I1-ACT-002-02` | `OFF-SYN-002` | 2 | `LOCAL_CAPTURE` | finalize |
| `OFF-I1-ACT-002-03` | `OFF-SYN-002` | 3 | `LOCAL_STORAGE` | save locally |
| `OFF-I1-ACT-003-01` | `OFF-SYN-003` | 1 | null | restart |
| `OFF-I1-ACT-003-02` | `OFF-SYN-003` | 2 | `LOCAL_STORAGE` | open local source |
| `OFF-I1-ACT-004-01` | `OFF-SYN-004` | 1 | `LOCAL_HISTORY` | open history |
| `OFF-I1-ACT-004-02` | `OFF-SYN-004` | 2 | `LOCAL_SEARCH` | search local index |
| `OFF-I1-ACT-004-03` | `OFF-SYN-004` | 3 | `LOCAL_SEARCH` | open result |
| `OFF-I1-ACT-005-01` | `OFF-SYN-005` | 1 | `LOCAL_TASK` | create local task |
| `OFF-I1-ACT-005-02` | `OFF-SYN-005` | 2 | `LOCAL_EDIT` | edit protocol field |
| `OFF-I1-ACT-005-03` | `OFF-SYN-005` | 3 | `LOCAL_TASK` | reopen |
| `OFF-I1-ACT-006-01` | `OFF-SYN-006` | 1 | `LOCAL_COPY` | copy approved local representation |
| `OFF-I1-ACT-007-01` | `OFF-SYN-007` | 1 | `LOCAL_EXPORT` | export through SAF |
| `OFF-I1-ACT-007-02` | `OFF-SYN-007` | 2 | `LOCAL_EXPORT` | verify local bytes and cleanup |
| `OFF-I1-ACT-008-01` | `OFF-SYN-008` | 1 | `LOCAL_EXPORT` | prepare Dora temp |
| `OFF-I1-ACT-008-02` | `OFF-SYN-008` | 2 | `LOCAL_EXPORT` | handoff to external target |
| `OFF-I1-ACT-008-03` | `OFF-SYN-008` | 3 | null | observe Dora process only |
| `OFF-I1-ACT-009-01` | `OFF-SYN-009` | 1 | `REQUIRED_MODEL_PROCESSING` | request model-dependent processing |
| `OFF-I1-ACT-009-02` | `OFF-SYN-009` | 2 | null | continue non-model flows |
| `OFF-I1-ACT-010-01` | `OFF-SYN-010` | 1 | `REQUIRED_MODEL_PROCESSING` | run bounded local processing |
| `OFF-I1-ACT-011-01` | `OFF-SYN-011` | 1 | `REQUIRED_MODEL_PROCESSING` | run bounded local processing |
| `OFF-I1-ACT-012-01` | `OFF-SYN-012` | 1 | `REQUIRED_MODEL_PROCESSING` | run bounded local processing |
| `OFF-I1-ACT-013-01` | `OFF-SYN-013` | 1 | `NON_DELETION_CLOUD_INTENT` | enqueue intent |
| `OFF-I1-ACT-013-02` | `OFF-SYN-013` | 2 | null | advance deterministic scheduler |
| `OFF-I1-ACT-014-01` | `OFF-SYN-014` | 1 | `LOCAL_SEARCH` | search |
| `OFF-I1-ACT-014-02` | `OFF-SYN-014` | 2 | `LOCAL_EDIT` | edit |
| `OFF-I1-ACT-014-03` | `OFF-SYN-014` | 3 | `LOCAL_TASK` | task |
| `OFF-I1-ACT-014-04` | `OFF-SYN-014` | 4 | `LOCAL_COPY` | copy |
| `OFF-I1-ACT-014-05` | `OFF-SYN-014` | 5 | `LOCAL_EXPORT` | export |
| `OFF-I1-ACT-015-01` | `OFF-SYN-015` | 1 | null | kill process |
| `OFF-I1-ACT-015-02` | `OFF-SYN-015` | 2 | null | restart |
| `OFF-I1-ACT-016-01` | `OFF-SYN-016` | 1 | null | reboot |
| `OFF-I1-ACT-016-02` | `OFF-SYN-016` | 2 | null | open app |
| `OFF-I1-ACT-017-01` | `OFF-SYN-017` | 1 | null | reconcile |
| `OFF-I1-ACT-017-02` | `OFF-SYN-017` | 2 | `NON_DELETION_CLOUD_INTENT` | resume eligible work |
| `OFF-I1-ACT-017-03` | `OFF-SYN-017` | 3 | `NON_DELETION_CLOUD_INTENT` | send bounded attempts |
| `OFF-I1-ACT-017-04` | `OFF-SYN-017` | 4 | `NON_DELETION_CLOUD_INTENT` | validate result |
| `OFF-I1-ACT-017-05` | `OFF-SYN-017` | 5 | `NON_DELETION_CLOUD_INTENT` | apply once |
| `OFF-I1-ACT-018-01` | `OFF-SYN-018` | 1 | `NON_DELETION_CLOUD_INTENT` | replay same logical operation |
| `OFF-I1-ACT-018-02` | `OFF-SYN-018` | 2 | `NON_DELETION_CLOUD_INTENT` | reconcile receipt |
| `OFF-I1-ACT-019-01` | `OFF-SYN-019` | 1 | null | run one pre-run canary for every call kind in the calibration window |
| `OFF-I1-ACT-019-02` | `OFF-SYN-019` | 2 | null | close calibration window |
| `OFF-I1-ACT-019-03` | `OFF-SYN-019` | 3 | null | exercise every eligible local flow in the measured window |
| `OFF-I1-ACT-019-04` | `OFF-SYN-019` | 4 | null | close measured window |
| `OFF-I1-ACT-019-05` | `OFF-SYN-019` | 5 | null | run one post-run canary for every call kind in the calibration window |
| `OFF-I1-ACT-020-01` | `OFF-SYN-020` | 1 | null | advance beyond frozen token lease catalog and remote-config intervals |
| `OFF-I1-ACT-020-02` | `OFF-SYN-020` | 2 | null | restart |
| `OFF-I1-ACT-020-03` | `OFF-SYN-020` | 3 | null | exercise local flows |
| `OFF-I1-ACT-021-01` | `OFF-SYN-021` | 1 | `OPTIONAL_SUMMARY_REQUEST` | request optional summary |
| `OFF-I1-ACT-021-02` | `OFF-SYN-021` | 2 | null | continue required local flows |
| `OFF-I1-ACT-022-01` | `OFF-SYN-022` | 1 | null | apply revocation |
| `OFF-I1-ACT-022-02` | `OFF-SYN-022` | 2 | null | restart queue observer |
| `OFF-I1-ACT-022-03` | `OFF-SYN-022` | 3 | null | exercise local flows |
| `OFF-I1-ACT-023-01` | `OFF-SYN-023` | 1 | `LOCAL_DELETE` | confirm scoped local deletion |
| `OFF-I1-ACT-023-02` | `OFF-SYN-023` | 2 | `LOCAL_DELETE` | reconcile local indexes and derivatives |
| `OFF-I1-ACT-023-03` | `OFF-SYN-023` | 3 | null | restart |
| `OFF-I1-ACT-024-01` | `OFF-SYN-024` | 1 | `LOCAL_DELETE` | complete scoped local deletion |
| `OFF-I1-ACT-024-02` | `OFF-SYN-024` | 2 | `DELETE_CLOUD_COPY` | create or preserve idempotent remote-deletion operation |
| `OFF-I1-ACT-024-03` | `OFF-SYN-024` | 3 | null | restart |
| `OFF-I1-ACT-025-01` | `OFF-SYN-025` | 1 | null | revoke non-deletion sends |
| `OFF-I1-ACT-025-02` | `OFF-SYN-025` | 2 | null | preserve local canonical data |
| `OFF-I1-ACT-025-03` | `OFF-SYN-025` | 3 | `DELETE_CLOUD_COPY` | create or preserve idempotent remote-deletion operation |
| `OFF-I1-ACT-025-04` | `OFF-SYN-025` | 4 | null | restart |
| `OFF-I1-ACT-026-01` | `OFF-SYN-026` | 1 | `DELETE_CLOUD_COPY` | send or replay the same logical deletion key and scope |
| `OFF-I1-ACT-026-02` | `OFF-SYN-026` | 2 | `DELETE_CLOUD_COPY` | reconcile accepted response, error or receipt |

## 8. Exact 26 semantic trace projections

Every trace status is `SEMANTIC_PROJECTION_ONLY_NOT_RUN`. Action IDs are ordered.

| Trace | Parent scenario | Action IDs | Proof shape |
|---|---|---|---|
| `OFF-I1-TRACE-001` | `OFF-SYN-001` | `001-01,001-02` | local-default vector and available-surface ledger shape |
| `OFF-I1-TRACE-002` | `OFF-SYN-002` | `002-01..03` | capture/storage semantic ordering only |
| `OFF-I1-TRACE-003` | `OFF-SYN-003` | `003-01..02` | immutable snapshot/restore projection |
| `OFF-I1-TRACE-004` | `OFF-SYN-004` | `004-01..03` | history/search local-truth preservation |
| `OFF-I1-TRACE-005` | `OFF-SYN-005` | `005-01..03` | task/edit/reopen local-truth preservation |
| `OFF-I1-TRACE-006` | `OFF-SYN-006` | `006-01` | copy ledger shape only |
| `OFF-I1-TRACE-007` | `OFF-SYN-007` | `007-01..02` | local export ledger/cleanup shape only |
| `OFF-I1-TRACE-008` | `OFF-SYN-008` | `008-01..03` | Dora/external handoff attribution boundary shape |
| `OFF-I1-TRACE-009` | `OFF-SYN-009` | `009-01..02` | processing wait remains orthogonal to local readiness |
| `OFF-I1-TRACE-010` | `OFF-SYN-010` | `010-01` | approved-model branch shape, no model execution |
| `OFF-I1-TRACE-011` | `OFF-SYN-011` | `011-01` | approved-model branch shape, no model execution |
| `OFF-I1-TRACE-012` | `OFF-SYN-012` | `012-01` | approved-model branch shape, no model execution |
| `OFF-I1-TRACE-013` | `OFF-SYN-013` | `013-01..02` | denied queue and finite scheduler shape |
| `OFF-I1-TRACE-014` | `OFF-SYN-014` | `014-01..05` | local operations do not mutate waiting queue identity |
| `OFF-I1-TRACE-015` | `OFF-SYN-015` | `015-01..02` | immutable in-memory snapshot/restore projection only |
| `OFF-I1-TRACE-016` | `OFF-SYN-016` | `016-01..02` | reboot projection only; no Android reboot evidence |
| `OFF-I1-TRACE-017` | `OFF-SYN-017` | `017-01..05` | eligible resume and exactly-one logical apply/effect |
| `OFF-I1-TRACE-018` | `OFF-SYN-018` | `018-01..02` | same-input replay and zero duplicate effects |
| `OFF-I1-TRACE-019` | `OFF-SYN-019` | `019-01..05` | monitor calibration ledger shape only |
| `OFF-I1-TRACE-020` | `OFF-SYN-020` | `020-01..03` | injected monotonic clock/expiry-independence shape |
| `OFF-I1-TRACE-021` | `OFF-SYN-021` | `021-01..02` | optional capability wait does not block required local flow |
| `OFF-I1-TRACE-022` | `OFF-SYN-022` | `022-01..03` | revoke non-deletion work without inventing remote delete |
| `OFF-I1-TRACE-023` | `OFF-SYN-023` | `023-01..03` | local deletion reducer/snapshot shape only |
| `OFF-I1-TRACE-024` | `OFF-SYN-024` | `024-01..03` | remote deletion durable enqueue, zero attempts, null deletion ID |
| `OFF-I1-TRACE-025` | `OFF-SYN-025` | `025-01..04` | revoke non-deletion sends while preserving remote deletion intent |
| `OFF-I1-TRACE-026` | `OFF-SYN-026` | `026-01..02` | exact 15 deletion event/outcome subcases and phase invariants |

## 9. Typed fixture and evidence boundary

Future fixtures consist only of schema-versioned state vectors, categorical events, boolean guards,
small bounded counters, deterministic synthetic opaque-ID seeds and lowercase SHA-256 digests. They
contain no PCM, transcript, summary, task text, participant, title, URI/path, endpoint, IP, packet,
credential, token, model bytes, provider response or user-derived value. Public result records may
contain only catalog IDs, categorical outcomes, counts, digests, bounded timestamps relative to an
injected monotonic origin and relative repository paths.

Each action slot has exactly one event ID `OFF-I1-EVT-<scenario>-<ordinal>` with the same numeric
suffix. Each trace references every and only the action/event IDs of its parent scenario. Repeated
text remains separate. Trace 026 additionally references every and only the 15 event/outcome classes
in section 6. No trace may be labeled Offline, device, network, model, provider or production PASS.

## 10. Invalid-attempt catalog

| ID | Fail-closed condition |
|---|---|
| `OFF-I1-INVALID-001` | Any non-synthetic or user-derived input. |
| `OFF-I1-INVALID-002` | Missing or changed semantic parent pin. |
| `OFF-I1-INVALID-003` | Missing, duplicate or noncontiguous catalog, action, event or trace ID. |
| `OFF-I1-INVALID-004` | Coverage other than exactly 26 scenarios, 67 actions/events and 15 deletion rows. |
| `OFF-I1-INVALID-005` | Wall clock, uncontrolled ID/random source, sleep or nondeterministic scheduling. |
| `OFF-I1-INVALID-006` | Android, network, socket, DNS, filesystem persistence or external dependency. |
| `OFF-I1-INVALID-007` | Forbidden or free-form content in fixture, diagnostic, log or public evidence. |
| `OFF-I1-INVALID-008` | Unbounded retry, tight loop or attempt while connectivity is denied. |
| `OFF-I1-INVALID-009` | Restore without exact immutable snapshot reconciliation. |
| `OFF-I1-INVALID-010` | Missing/extra vector key, unknown state or incorrect canonical digest. |
| `OFF-I1-INVALID-011` | Invalid deletion phase, primary state, substatus, hash or receipt invariant. |
| `OFF-I1-INVALID-012` | Host evidence labeled as Offline/device/network/model/provider/production PASS. |

## 11. Required validators for a later implementation

| ID | Required check |
|---|---|
| `OFF-I1-VAL-001` | Strict JSON parse with duplicate-key rejection and exact allowed keys. |
| `OFF-I1-VAL-002` | Exact source bytes/hashes and current semantic contract pins. |
| `OFF-I1-VAL-003` | Markdown to JSON semantic parity and unique contiguous IDs. |
| `OFF-I1-VAL-004` | Exact five axis catalogs, 13 I1 operation labels, six substatuses, eight errors and eight call kinds. |
| `OFF-I1-VAL-005` | Exact 26 to 26 trace projection and 67 to 67 action/event bijection. |
| `OFF-I1-VAL-006` | Exact 15-row deletion mapping and all phase/state/hash invariants. |
| `OFF-I1-VAL-007` | Deterministic arbitration and explicit no-rule outcome. |
| `OFF-I1-VAL-008` | Canonical five-axis encoding and independently recomputed SHA-256 digests. |
| `OFF-I1-VAL-009` | Exact inherited identifier formulas, flow/queue equality and raw-ID absence. |
| `OFF-I1-VAL-010` | Six unresolved hash formulas remain fail closed until versionedly resolved. |
| `OFF-I1-VAL-011` | Same-input replay, different-input mismatch and zero-or-one effect counters. |
| `OFF-I1-VAL-012` | Exact immutable snapshot/restore equality. |
| `OFF-I1-VAL-013` | All 12 invalid attempts reject with zero prohibited state/effect delta. |
| `OFF-I1-VAL-014` | No Android/network/filesystem/external dependency APIs; bounded static API and dependency inventory. |
| `OFF-I1-VAL-015` | Recursive content-free evidence allowlist, secret scan and public-path scan. |
| `OFF-I1-VAL-016` | Exactly 25 false authority flags and unchanged TODO/NOT_READY/NOT_RUN/NOT_AUTHORIZED truth. |
| `OFF-I1-VAL-017` | Exact parent `contractId`, ordered 12/19 ledger fields and every inherited hash lifecycle/nullability rule. |

## 12. Readiness predicates and blockers

| ID | Current state | Predicate |
|---|---|---|
| `OFF-I1-RDY-001` | `SATISFIED_BY_DOSSIER` | Exact current main and semantic parent bytes are pinned. |
| `OFF-I1-RDY-002` | `SATISFIED_BY_DOSSIER` | Future scope, non-goals, catalogs, trace/action coverage and validators are versioned. |
| `OFF-I1-RDY-003` | `BLOCKED` | A separate implementation scope and independent review are authorized. |
| `OFF-I1-RDY-004` | `BLOCKED` | All unresolved semantic groups are versioned: six hash preimages; total non-deletion transitions; invalid-input dispositions; counter algebra; local/flow digest correlation; strict restore decoding/equality; typed revalidation outcomes. |
| `OFF-I1-RDY-005` | `BLOCKED` | Future module/package/toolchain paths and exact tool hashes are frozen without admitting dependencies. |
| `OFF-I1-RDY-006` | `NOT_RUN` | Exact 26/67/15 executable semantic coverage passes. |
| `OFF-I1-RDY-007` | `NOT_RUN` | Reducer determinism, no-rule behavior, replay and effect invariants pass. |
| `OFF-I1-RDY-008` | `NOT_RUN` | All 12 invalid-attempt tests pass fail closed. |
| `OFF-I1-RDY-009` | `NOT_RUN` | Static API, dependency, bytecode and content-free evidence boundaries pass. |
| `OFF-I1-RDY-010` | `NOT_RUN` | Repeated host runs and immutable snapshot/restore checks are deterministic. |
| `OFF-I1-RDY-011` | `NOT_RUN` | Proportional repository validators and non-device Tier A pass on the exact implementation head. |
| `OFF-I1-RDY-012` | `BLOCKED` | Independent exact-head implementation review returns no P0/P1/P2 and separately authorizes publication or later gate. |

All inherited blockers `OFF-RDY-01..10` remain `BLOCKED` exactly as recorded by the parent contract.
I1 does not close or narrow any of them. In particular it does not prove an integrated Offline
harness, an approved model, calibrated call monitoring, physical devices, reconnect transport,
manifest/APK truth or execution authority.

## 13. Acceptance boundary

This two-file dossier may be called `SCOPE_CONTRACT_COMPLETE` after strict JSON/parity/scope/privacy
validation and independent advisory review. A future implementation remains fail closed while any
`OFF-I1-RDY-003..012` predicate is not satisfied. Even a green future pure-host oracle would be only
`HOST_SEMANTIC_ORACLE_EVIDENCE`; `POC-OFFLINE-001` would remain `TODO / NOT_READY / NOT_RUN /
NOT_AUTHORIZED` until the separate integrated/device/model/network gates are explicitly satisfied.

Current findings for this dossier: `P0=0`, `P1=0`, `P2=0`. The seven unresolved semantic groups and
all implementation/execution predicates are recorded blockers, not silently assumed decisions.
