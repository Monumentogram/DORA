# 7.2C вЂ” Replaceable Cloud ASR provider contract v0.1

Status: CONTRACT_DEFINED_AWAITING_EXACT_SHA_CI. Runtime NOT_IMPLEMENTED.

## Scope and authority

Start: `b380fb352d20fa62ed2de3a4fae3b05e51b86f5a`, branch
`stage/7-alpha-foundation`; clean tree, fetched exact lineage and PR #87/#86/main
verified before edits. ADR-0009, the frozen ASR data/versioning and user/Gherkin
contracts, Technical Plan В§В§22/24/28/30 and the completed 7.2 contract govern this
design. The final 6.2D duplicate-name closure admits only the Project Owner's
closed internal Alpha; its referenced technical record/configuration and media
preflight remain evidence, not a general provider guarantee. Historical failures
are retained. English quality limitation and second-speaker revalidation remain.

## Decision

`Android в†’ DORA Cloud Control Plane в†’ Cloud ASR Worker в†’ CloudAsrProvider в†’ Adapter в†’ Provider`.

CloudAsrProvider is a server-side architectural interface represented by a closed,
versioned JSON type catalogue and this document. There is no backend application
tree to extend. A new server/Gradle/Python runtime module would create unused
infrastructure; reusing app RecognitionPort would cross trust boundaries. Both
alternatives are rejected. The existing RecognitionPort and all Android code stay
unchanged. Only offline contract validators execute in this stage.

The separate adapter profile contains provider wire mappings and their evidence
levels. Generic schemas contain no storage vendor, endpoints, credentials, wire
fields or client-supplied policy proof. No additional dependency is introduced.

## Request and identities

CloudRequest binds `processing_action_id`, `processing_job_id`, `idempotency_key`,
`attempt_id`, `trace_id`, exact original audio and opaque authorization, consent
and ownership references. These instantiate existing frozen concepts, not competing
IDs. `recording_id` maps to RecordingId; `audio_asset_id` is the frozen immutable
source_audio_version / 7.2 AudioAssetId; processing_job_id maps to RecognitionJobId;
`transcript_id` is TranscriptId / asr_result_id, not a second version identifier.

AuthorizedAudioSource contains that exact source, content digest and opaque
server-side access reference. It is not an Android path, storage URL or authorization
token. Only the control plane can establish this reference after authorization;
schema validation of an opaque string does not authorize anything. Revalidate
ownership, consent/revocation, source integrity, route scope, quota and budget at
dispatch/retry. Authorization implementation remains 7.2E.

Audio properties are container, encoding, duration in integer microseconds, sample
rate and channel count. Configuration binds language and an opaque immutable
configuration reference. The current profile constrains these separately. Cloud
input is ORIGINAL_AUDIO only; neither Local transcript nor derived text is input.

One intended action keeps its action/job/idempotency/source/config identity across
retries; each actual attempt gets a new attempt ID. Explicit reprocessing is a new
action. Trace IDs correlate diagnostics and never establish ownership. Adapters
retain provider operation references separately and correlate them to the exact
attempt before dispatch acknowledgment; provider IDs never replace DORA IDs.
BoundOperation also freezes the selected route/provider/profile ID and profile
version as a generic provider_namespace. Status/result/cancel/reconcile must use
that dispatch namespace; identical provider-local operation references in another
namespace are unrelated. Every normalized result must match all namespace fields.

## Lifecycle interface

| Operation | Input | Output / obligation |
|---|---|---|
| submit | CloudRequest | Observation: ACCEPTED only with positive provider acknowledgment and bound operation reference; otherwise FAILED only for definite rejection or UNKNOWN for possible acceptance. |
| status | BoundOperation | Observation; missing response or poll timeout is UNKNOWN, never terminal failure. |
| result | BoundOperation | Validated immutable CloudResult, or normalized failure/UNKNOWN; provider completion alone is OUTPUT_AVAILABLE. |
| cancel | BoundOperation | Cancellation observation; REQUESTED is not CONFIRMED. Unsupported/uncertain cancellation retains operation identity and UNKNOWN. |
| reconcile | CloudRequest plus optional known operation reference | Observation for the same action/attempt, based on authoritative matching evidence. No match without conclusive non-acceptance proof remains UNKNOWN. |

Mutable states: ACCEPTED, PROCESSING, OUTPUT_AVAILABLE, SUCCEEDED, FAILED, UNKNOWN.
Cancellation is orthogonal: NONE, REQUESTED, CONFIRMED, UNKNOWN. SUCCEEDED requires
an independently validated result. OUTPUT_AVAILABLE means only reported completion.
FAILED requires a definite failure; neither a timeout nor a missing result proves it.
CONFIRMED requires positive cancellation evidence, never a delete response assumption.
Its normalized representation is FAILED + CANCELLED + CONFIRMED, with an existing
bound ACCEPTED operation and DO_NOT_RETRY. Failure of a read/cancel API call is
not a terminal job failure: any RECONCILE_BEFORE_RETRY observation stays UNKNOWN.

Allowed progress is ACCEPTED в†’ PROCESSING в†’ OUTPUT_AVAILABLE в†’ SUCCEEDED; a
definite rejection/failure can become FAILED. Any incomplete observation can become
UNKNOWN and be reconciled back to a proven state. Do not regress confirmed facts
based on absent/stale observations. No terminal result may be mutated by later polls.
Duplicate delivery is idempotent by action/result/source identity; conflicting
results require reconciliation, never a second logical winner. Cancellation intent
prevents late output activation even when computation eventually succeeds. Retain
late output provenance under future retention/deletion rules; no resurrection or
remote erasure is implied. This stage implements no durable ledger or active pointer.

## Immutable normalized result

CloudResult binds transcript_id, action/job/idempotency/attempt, source, configuration,
processing identity and provider provenance. Full text and ordered segments exist
server-side only, never in diagnostics or public evidence. Each segment has a
result-scoped ID and audio-relative microsecond boundaries. IDs do not align edits
across engines. Full text preserves punctuation independently of lexical segments.

Timestamp availability is KNOWN (both bounds), PARTIAL (exactly one bound), or
UNAVAILABLE (both null). Every known bound must fit audio duration; pairs must be
ordered. Missing boundaries are never zero-filled or interpolated. Quality is
separate: UNKNOWN unless independently validated; structural validity is not accuracy.
Empty readable text and zero segments can represent valid silence; failed, malformed
or partial provider output cannot become success. Partial timestamp availability
does not itself mean partial transcript completion.

Processing identity distinguishes CLOUD engine, route, provider identity, provider
version, model identity/version and adapter profile version. Metadata uses tagged
KNOWN/UNKNOWN/NOT_SUPPLIED/NOT_APPLICABLE values; only KNOWN carries a nonempty value.
Creation time is adapter-observed UTC; provider processing times and usage are tagged
known only when factual. Provider operation/request/result IDs are provenance only.
No current provider/model version is invented from the service name or API version.

## Failure and retry decisions

Normalized failure category is separate from RetryDisposition. Permanent input,
format/language/configuration, source, access and invalid-media failures use
DO_NOT_RETRY. Proven transient rejection can be RETRYABLE, subject to valid source,
authorization, budget and downstream bounded backoff / factual Retry-After.
Timeout/connection loss after possible dispatch and duplicate/conflict require
RECONCILE_BEFORE_RETRY. Unknown dispatch effect overrides transient classification.
An adapter mapping/internal failure cannot create success or automatically resubmit
an operation that may have been accepted. Confirmed cancellation is DO_NOT_RETRY;
uncertain cancellation requires reconciliation. Unknown provider codes stay UNKNOWN.

Errors contain only closed categories, operation/dispatch-effect classifications
and typed retry advice. No raw messages, response body, transcript, signed URL,
credentials or exception cause. A retry recommendation is not a scheduler or consent.
Reconciliation may authorize a new attempt only after conclusive non-acceptance or
safe terminal resolution, with current authorization; inability to prove remains UNKNOWN.

## Current profile and evidence ceiling

The separate Amazon Transcribe profile freezes STANDARD_BATCH_FILE_ASR,
eu-central-1, explicit ru-RU/en-US, mono PCM16LE/16 kHz WAV, 0.5вЂ“600 seconds,
and the admitted disabled-feature set. These are admission constraints, not all
service capabilities. No region/provider fallback or native idempotency is claimed.
COMPLETED maps to OUTPUT_AVAILABLE pending identity/output validation; observed
FAILED maps to FAILED with bounded error classification. Unproven states and
cancellation capabilities remain NOT_YET_MAPPED. Retained harness mapping is an
offline structural precedent, not production runtime or live behavior proof.

Final duplicate-name ConflictException is evidence of a collision, not idempotent
success. Missing-input/access mappings require the retained contextual adjudications;
generic BadRequest is not sufficient proof. Provider/model versions remain unknown,
timestamp accuracy NOT_EVALUATED. No private raw evidence or audio is opened here.

## Implementation plan and review focus

Using writing-plans and executing-plans inline under the explicit autonomous task;
the owner's two-commit sequence overrides separate spec/plan commits and approval pauses.

- [x] Define closed JSON types, obligations and separate evidence-bound profile.
- [x] Write deterministic validator tests first; observe missing validator fail.
- [x] Implement structural/type/correlation/retry/profile validation in tools only.
- [x] Run offline tests, negative mutations, Stage00 and existing relevant checks;
  wire the validator/tests into mandatory Android CI.
- [x] Review provider leakage, identity substitution, uncertain retries, invented
  metadata/cancellation, persistence/auth/runtime scope and credential fields.
- [ ] Publish implementation commit; await both exact-SHA CI jobs.
- [ ] Update Sheet with complete readback; publish evidence/status-only commit;
  reconcile final HEAD and await its CI separately.

Review focus tests: unknown dispatch effect must override retryable errors; a mutated
schema must not silently permit provider wire fields; success cannot carry another
source/job; timestamp absence cannot become zero; profile evidence cannot promote
unproven cancellation/model properties. Fixtures are inert synthetic JSON only.

## Non-execution

7.2D/E, 7.3/7.3C and Stage 8 NOT_STARTED. Backend/worker/adapter/Android Cloud API,
transcript persistence and auth/ownership runtime NOT_IMPLEMENTED. No HTTP server,
queue, DB, storage, SDK, credentials, network/provider execution, upload or audio.
AWS NOT_CALLED; AWS spend 0 BY THIS TASK; FIRST_REAL_PRODUCT_AUDIO NOT_READY.
Recovery integration NOT_RUN; main unchanged; PR #86 untouched; PR #87 draft/unmerged.
Repository publication/CI and the requested Google Sheet update are the only remote
coordination; none is a Cloud/provider runtime test. Next separate task is 7.2D.
