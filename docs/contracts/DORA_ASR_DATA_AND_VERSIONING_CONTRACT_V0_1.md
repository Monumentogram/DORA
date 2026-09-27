# DORA ASR data and versioning contract v0.1

Status: APPROVED FOR ALPHA ARCHITECTURE

Owner authority: explicit Cloud / Local ASR product-contract task, 27 September 2026.
This contract defines intended Alpha behavior.
It does not assert that Cloud functionality is already implemented.

Canonical product authority: [user scenarios](../product/DORA_ASR_USER_SCENARIOS_V0_1.md).
Acceptance contract: [Gherkin scenarios](../product/DORA_ASR_USER_SCENARIOS_V0_1.feature).
Existing [technical plan](../DORA_MVP1_TECHNICAL_PLAN.md) job/versioning/segment contracts
remain the compatibility baseline; names below describe concepts, not a mandatory database schema.
The explicit owner-approved Alpha ASR overlay is scoped in the product authority; it does not
approve production integration, a provider, retention period or legal policy.

## Relationship and identities

`Original Audio → Cloud Authorization → ASR Processing Versions → User Edits → Active Transcript`.

A logical recording owns an immutable/versioned source-audio identity, technical chunks,
authorization records, processing actions, raw results and edit history. A chunk is not a consent
unit. Preserve source-audio version/digest identity and segment-to-audio mapping without placing
sensitive identifiers or content in public telemetry. `recording.transcript = mutable string`
as the sole representation is prohibited.

## User policy and consent scope

| Concept | Requirement |
|---|---|
| `cloud_upload_policy` | `ALWAYS`, `ASK_EACH_RECORDING`, `MANUAL_ONLY` |
| `cloud_consent_version` | Persist exact accepted disclosure/purpose/destination scope; a policy label alone is not consent |
| `cloud_consent_granted_at` | Timestamp of informed grant, absent until actually granted |
| `cloud_consent_updated_at` | Timestamp of preference/scope/revocation change |

No grant exists merely because the application is installed. ALWAYS grants automatic processing
within the accepted scope; ASK requires a recording decision; MANUAL_ONLY never starts autonomously.
Consent for ASR audio does not authorize training, other artifacts or unrelated downstream processing.
Provider/region changes must be checked against the recorded grant and must not silently expand it.

## Recording-level authorization

| State | Meaning | Audio upload allowed? |
|---|---|---|
| `PENDING` | No applicable grant/decision | No |
| `INHERITED_ALWAYS` | Derived from a currently valid ALWAYS grant | Only while that grant remains valid |
| `EXPLICITLY_APPROVED` | User approved this recording or selected it in an explicit batch | Yes, within recorded scope and until revoked |
| `MANUAL_USER_ACTION` | Explicit “Recognize in cloud” request for this recording | Yes, within recorded scope and until revoked |
| `DEFERRED` | “Not now” or presented-but-unselected recording | No |

Persist recording ID, authorization basis, consent version/scope, decision time, prompt-presented
marker/time, batch identity and exact selected recording IDs where relevant. Revocation validity
is an orthogonal concept; the listed states need not be expanded into one combined storage enum.
The global user policy and this recording record are **separate entities**.

ASK automatically prompts at most once per logical recording across restarts, chunks and policy
changes. Batch presentation counts as that one prompt for every presented recording. A batch
authorizes only its selected snapshot, not new arrivals; previously DEFERRED items are not included
in new automatic prompts. Explicitly opening the pending list may authorize deferred items.
Persist the grant before enqueuing outbound work; no audio byte may precede it.

Before every upload chunk/retry, verify recording ownership, effective authorization and unrevoked
scope. A policy change never silently lifts DEFERRED. Leaving ALWAYS invalidates not-yet-started
operations supported solely by the old inherited grant. Explicit recording grants remain valid
within scope unless revoked; explain their continued applicability when preferences change.
Revocation prevents further bytes, cancels queued work and attempts in-flight cancellation.
Already-transferred bytes and remote deletion/cancellation receipts remain separately tracked.
Do not represent revocation as guaranteed retroactive erasure.

## Transcript versions

Every ASR result has concepts equivalent to:

| Field | Meaning |
|---|---|
| `transcript_id` | Stable, unique result-version identity |
| `recording_id` | Logical recording owner |
| `source_audio_reference` | Exact original audio identity/version and segment mapping |
| `engine` | Local or Cloud engine kind and implementation identity |
| `provider_id`, `provider_version`, `model_id`, `model_version` | Exact processing identity where supplied; unavailable provider version explicitly unknown, never invented |
| `version` | Recording-scoped immutable version identity/order |
| `processing_status` | Job/result lifecycle, including incomplete/failed results |
| `created_at`, `processed_at` | Creation and processing timestamps |
| `provenance` | Job/action/attempt/request/result correlation and source/parent versions |
| `segments` | Text, segment identity, audio-relative start/end and timestamp availability/quality |

Support LOCAL, CLOUD and MERGED result provenance without requiring these exact enum names.
ACTIVE is the selected role, not another ASR engine. Separate mutable job state from immutable
completed output. Preserve failed/partial-result status without promoting it to a valid transcript.
Local engine A/B and Cloud provider A/B can coexist with traceable identities.

`active_transcript_id` points to a valid version or is absent before the first result. Changing
the pointer does not delete old raw outputs, merged versions or user edits. If no user edits exist,
a valid Cloud result may safely replace a Local active pointer after source and generation checks.
If edits exist, build a proposed merged version and wait for explicit acceptance. A compare/replace
decision records its actor, timestamp, base version and edit revision. A rejected proposal keeps
the current pointer. Replacing a user correction requires an explicit conflict-level user decision;
the replaced correction remains in history.

## User edits and anchors

Store edits separately from raw engine output, including edit ID, recording ID, actor/provenance,
timestamp, base transcript/version, base edit revision, segment relationship, original text where
useful, replacement/deletion/insertion and mapping/conflict state. Prefer semantic/audio anchors
such as `segment_id`, `start_ms`, `end_ms`, source-audio version and contextual alignment.
Absolute character offsets alone are insufficient. Do not pretend cross-engine segment IDs match.
Unavailable or unreliable timestamps are explicitly marked; Stage 5 does not prove timestamp quality.

`USER EDIT > CLOUD ASR > LOCAL ASR` is the proposal priority. Re-transcribe original audio,
align output to source segments, and reapply only confidently mapped edits. If mapping is unsafe:
preserve the original edit, mark unmapped/conflicted status, keep the current active version and
require review. Never silently drop or apply an edit to the wrong segment. Compare the proposal's
base edit revision at activation; concurrent edits make a stale proposal require regeneration/review.
Manual choices, including choosing an older version, take precedence over automated ranking.

## Cloud adapter and execution boundary

Use the existing technical-plan job/versioning concepts through a replaceable Cloud ASR port.
Request: job/action/idempotency ID, recording owner, original audio version/reference or authorized
stream, audio encoding/duration, language/configuration, consent-scope reference and request trace.
Response: job/request/result IDs, processing status, immutable transcript/segments/timestamps,
provider/model identity, duration/usage, normalized error and retry classification. Provider wire
formats must terminate at the adapter. Alpha may implement one provider, not couple all code to it.

Normalize authorization, quota/rate-limit, invalid input, network, timeout, provider 4xx/5xx,
cancellation and unknown-result failures. Define bounded timeout/backoff, retryability and
Retry-After handling; never retry all 4xx or amplify unknown outcomes blindly.
Missing timestamps/metadata have explicit availability, not invented values.

**Execution boundary: DECISION REQUIRED before implementation.** Compare direct mobile-to-provider
against a DORA-controlled backend; record selected approach and rationale in an admission/architecture
decision. Assess credential exposure, authentication, request ownership, quota/abuse control,
observability, provider replacement, privacy and cost accounting. Never embed privileged provider
credentials in the app. A direct approach requires a safe constrained authorization mechanism;
this contract does not select either approach silently or provision any endpoint.

## Jobs, queue, idempotency and state

Conceptual identities: `processing_job_id`, `processing_action_id`, `recording_id`,
`source_audio_version`, `asr_result_id`, provider request ID and attempt ID. One intended action
has one stable idempotency key across retries; attempt IDs may differ. Explicit reprocessing
creates a new action with its exact source/model/configuration identity. Duplicate requests,
callbacks and late results cannot create competing logical winners or regress the active pointer.
Do not assume provider idempotency exists: reconcile uncertain outcomes before resubmission.

Persist queue membership, authorization references, source identity, attempt count, cancellation
and result association across application/device restart. Reconnect is a scheduling signal, never
new consent. Recheck audio availability and permission at dispatch. Define upload progress,
resumability, network constraints and Android background/battery/data policy before wiring workers.
Recording continues independently of Local installation or Cloud readiness.

Behavioral states (may map to separate existing job/recording state machines):

| States | Required transitions |
|---|---|
| `RECORDED` | Durable original audio; choose Local, authorized Cloud or wait |
| `CLOUD_PENDING`, `CLOUD_UPLOADING`, `CLOUD_PROCESSING` | Wait/dispatch only with permission; bounded retry/cancellation; false-positive connectivity never implies success |
| `CLOUD_READY`, `CLOUD_FAILED` | Validate and retain success; failure leaves existing data intact and can be retried |
| `LOCAL_PENDING`, `LOCAL_PROCESSING`, `LOCAL_READY`, `LOCAL_FAILED` | Optional installed engine; failure does not delete recording or prior valid text |
| `CLOUD_REPROCESS_AVAILABLE`, `CLOUD_REPROCESSING` | Original audio retained; schedule according to effective policy/grant |
| `CLOUD_REPROCESS_READY`, `CLOUD_REPROCESS_FAILED` | Preserve Local/raw/edit history; safe proposal/review or unchanged active result |

Cancellation has an explicit orthogonal state/reason and rejects stale callbacks. Cloud failure
at upload, processing, merge or activation never destroys original audio, a Local transcript,
the active transcript or user edits. A retry is permitted when source and authorization still exist.

## Deletion and retention compatibility

Future implementation must specify transactions/references, job cancellation, tombstones,
retry/restart and independent local/remote outcomes for each operation below. This document
implements no deletion and invents no legal retention policy or automatic cleanup period.
Follow DEC-013 and the [storage contract](../design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md).

| User deletes | Required explicit semantics |
|---|---|
| Recording/conversation | Disclosed cascade scope, dependent versions/edits/jobs, local and remote deletion tracking; late results must not resurrect it |
| Original audio only | Preserve existing transcripts/edits/structured results; mark source unavailable; reconcile/cancel audio-dependent work; Cloud reprocessing cannot use transcript text as a substitute |
| ASR version | Guard active-pointer and edit/provenance references; choose an explicit safe replacement or retain a tombstone; no orphaned edits |
| Active transcript | Distinguish clearing/changing active selection from deleting its version; no silent arbitrary fallback |
| User edit | Explicit user action with history/conflict consequences; ASR completion is never an edit-deletion request |

Remote deletion success needs verifiable result/receipt; local success must not claim remote success.
Consent revocation and deletion are different actions. Retention/disclosure must be settled before
the first Cloud upload and compatible with future reprocessing promises.

## Privacy, cost and observability

Use secure transport, least-privilege request authorization, protected secrets and ownership checks.
Document sensitive audio handling, destination disclosure, retention, preference revocation and
redaction requirements before implementation. Do not log raw audio/transcript or sensitive content
unnecessarily. Legal/security decisions remain dependencies, not approvals supplied by this contract.

Minimal telemetry: job/recording correlation, provider/result state, request/upload/ASR duration,
failure class, retry count and provider/model identity. Apply suitable access/retention controls.
Measure processed duration/bytes, request counts, error rates and estimated cost; handle quotas,
rate limits and bounded retry budgets without amplification. Billing UI is not required.

## Validation boundary

The product document's 14 exit criteria and the Gherkin contract are future Alpha acceptance,
not executed runtime tests. Include online policies, offline/reconnect/batch, Cloud-only, optional
Local, original-audio reprocessing, mapped/unmapped/concurrent edits, all four failure-preservation
invariants, upload loss, processing loss, timeout, 4xx/5xx, duplicate retry, app/device restart,
revocation and stale completion. Cloud implementation and all these runtime gates remain
`NOT IMPLEMENTED / NOT STARTED`. Historical evidence and Recovery are unchanged.
