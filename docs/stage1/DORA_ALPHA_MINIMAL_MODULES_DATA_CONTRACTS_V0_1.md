# 7.2 — Minimal Alpha modules and data contracts v0.1

Status: IMPLEMENTED_AWAITING_EXACT_SHA_CI; no final or Sheet PASS yet.

## Authority and design

Scope is the owner's 7.2 task, starting at
`0492ff34a44a6b5becdc232b16c2f3f99de87456` on `stage/7-alpha-foundation`.
Parents are `4a2350a02eba8adc76c04bf250e7769513ad9aa7` and
`4e7742d88377d3d915be904618fc220d479ab25d`. Fetch, clean worktree, exact
lineage and open/draft/unmerged PRs #87/#86 were verified before edits.

Frozen authority: Technical Plan §§12/14/30, Design Spec §23, ASR user
scenarios/Gherkin/data-and-versioning v0.1, ADR-0009, and 7.1 identity contract.
This is an in-memory application handoff contract, not a persistent recording
or processing schema. Capture and recognition have separate states. One
coordinator follows one recording; another recording may use another coordinator.
No account, network, optional Local package or provider is required by capture.

Reuse `core:model` for immutable IDs, references, states and ports; `app/flow`
owns a pure Kotlin coordinator and explicit unavailable composition. `core:common`
is already independent shared utilities and needs no new responsibility.
No new module/dependency. Alternatives rejected: new domain/data/usecase modules
have no admitted implementation to isolate; putting Android/provider DTOs in the
contract would couple future implementations to the UI.

Identifiers are distinct UUID-backed values. AudioAssetId identifies one immutable
original-audio version (a new version gets a new ID), not a physical filename.
OriginalAudioRef binds it to RecordingId. TranscriptId already identifies a
result-version in the frozen ASR contract; TranscriptRef adds exact source and
RecognitionJobId correlation without introducing a competing version counter.
No transcript content, segments, DB, active pointer, edit anchors or provider IDs.

## Implementation plan

Execute inline under the owner's autonomous instruction; keep the requested two
commits rather than a separate design commit. Spec and plan are this document.

- [x] Add ID validation/equality and coordinator boundary tests first; verify RED.
- [x] Implement `AlphaIdentities`, `AlphaPorts`, `AlphaFlowState` in `core:model`;
  implement `AlphaFlowCoordinator` and unavailable composition in `app/flow`.
- [x] Connect the existing recording action to unavailable composition; preserve
  its honest unavailable notice and existing identity/signing/version.
- [x] Verify delayed completion, wrong source/job, invalid transitions, cancellation,
  reset while busy, normalized failure and absence of success before acknowledgment.
- [x] Add a fail-closed compiled-bytecode dependency guard and negative controls;
  run it in Android CI after compilation alongside existing governance checks.
- [ ] Run Stage00, unit, formatting, detekt, lint, assembly and applicable validators;
  independently review scope and commit/push implementation, await exact-SHA CI.
- [ ] After technical PASS update Sheet with readback, write evidence/status-only
  commit, push, reconcile Sheet exact final HEAD and await final CI if triggered.

Review focus: callbacks after cancellation/reset; wrong recording/audio/job result;
unavailable adapters; reset or duplicate requests during pending work; provider
exceptions/content and dependencies escaping into the app contract.

## Scope boundary

7.2C/D/E and 7.3 NOT_STARTED. Recording/storage/ASR runtime NOT_IMPLEMENTED.
AWS NOT_CALLED. Recovery integration NOT_RUN; clean replacement remains required
before Stage 8 recording/storage acceptance. main NOT_CHANGED; PR #86 UNTOUCHED;
PR #87 remains draft/unmerged. No signing backup restoration claim.

## Dependency direction and UI connection

`app/ui -> app/flow -> core:model/alpha`; future Stage 8/9 adapters implement
`core:model/alpha` ports. No product dependency on any `poc:*` module. Model source
and coordinator are pure Kotlin/JVM even though the existing core module uses the
Android library convention. Existing core:common/core:testing remain unchanged.
The recording affordance dispatches a typed Start intent into unavailable
composition and continues to show the existing unavailable notice. There is no
microphone permission request, fake audio, fake persistence or fake transcript.

| UI intent | Application state/coordinator | Port | Identity/data | Future implementation |
|---|---|---|---|---|
| Start after preflight | Idle -> Starting -> Recording or failure | CapturePort.start | RecordingId | Stage 8 |
| Pause / Resume | Recording -> Pausing -> Paused -> Resuming -> Recording | CapturePort.pause/resume | Same RecordingId | Stage 8 |
| Confirm Stop | Recording/Paused -> Stopping -> Captured | CapturePort.stop | OriginalAudioRef | Stage 8 |
| Save | Captured + Empty -> Saving -> Recorded | OriginalAudioStoragePort.persist | StoredAudio acknowledgment | Stage 8 |
| Resolve existing original | Adapter-owned lookup | OriginalAudioStoragePort.resolve | Exact OriginalAudioRef -> StoredAudio or failure | Stage 8 |
| Recognize | Recorded + Idle -> Processing -> Result/Failed | RecognitionPort.recognize | StoredAudio + RecognitionJobId -> TranscriptRef | Stage 9 |
| Cancel recognition | Processing -> Cancelling -> Cancelled/CancellationFailed | RecognitionPort.cancel | RecognitionJobId retained | Stage 9 |
| Reset terminal view | Settled state -> Idle | None; no deletion | External data retained | 7.2 presentation only |

## Port obligations and data ownership

| Contract | Responsibility and allowed inputs | Outputs/errors | Owner and exclusions |
|---|---|---|---|
| CapturePort | Non-blocking explicit Start/Pause/Resume/Stop of one logical recording; Start includes future permission/disclosure checks | Unit acknowledgment or exact OriginalAudioRef; unavailable, permission/capture/internal errors | Stage 8 owns resources and captured original. Failure must prove quiescence before callback. No capture cancellation/deletion, Android API, VAD, encoding, Recovery or timing algorithm here. |
| OriginalAudioStoragePort | Persist captured original; resolve exact immutable version without exposing a path or stream | StoredAudio only after durable validation; unavailable, storage/source unavailable/internal errors | Stage 8 owns audio/version/digest mapping and durable bytes; failures preserve source. No FS, DB, encryption, Recovery, retention or transcript persistence here. |
| RecognitionPort | Request processing of persisted original; explicit cancellation of its local logical job | Validated immutable TranscriptRef bound to source/job, or typed failure; cancel acknowledgment concerns local job only | Stage 9 owns processing and retained result; future implementations check source availability and consent/auth/ownership before route dispatch. No CloudAsrProvider, wire schema, retry/timeout/provider/model mapping, auth or active pointer here. |

`Capability.AVAILABLE` describes an admitted adapter's ability to receive requests;
it does not prove microphone permission, audio durability or Cloud authorization.
Every production port is UNAVAILABLE. Capability blocks new acquisition/processing;
loss of capability cannot prevent cleanup/control of an already active session/job.
Ports must return immediately; callbacks may arrive on any thread. Dispatch and
callback updates serialize under the coordinator monitor; adapters must not wait
for another thread's callback while holding the call open.

## State, completion and error rules

Capture: Idle; Session(Starting/Recording/Pausing/Paused/Resuming/Stopping);
Captured; Failed; Uncertain. Storage: Empty; Saving; Recorded; Failed.
Recognition: Idle; Processing; Cancelling; CancellationFailed; Cancelled; Result;
Failed. These are independent sealed axes, not one persisted combined lifecycle enum.
Processing means a request is outstanding, not that an engine has accepted it.
No UI-level upload or provider progress is fabricated.

The coordinator accepts only supported transitions; invalid intents return
INVALID_STATE without changing state or issuing work. PortResult.Success/Failure
are completion events. Callback tickets are monotonic within the coordinator;
duplicates and stale callbacks cannot advance state. Captured is not durable:
only an exact-source storage acknowledgment creates Recorded. Only a validated
exact-source/exact-job transcript acknowledgment creates Result. Result is a
reference to an immutable output; it never sets an active/merged pointer.

Cancellation invalidates recognition callbacks immediately so user cancellation
cannot later become success. Failed cancellation remains CancellationFailed with
the original job, blocks Reset and permits explicit cancellation retry. The
adapter retains any late output; this presentation layer neither deletes it nor
activates it. No remote erasure is asserted. Capture exceptions become Uncertain,
retain recording identity and permit Stop while blocking Reset. A wrong-recording
Stop result is also Uncertain. No automatic mic restart, retries or deletion.
Reset also rejects captured audio without a durable storage acknowledgment,
including a failed save. Explicit Save may retry that same source; persistence
must be idempotent for its immutable identity. There is no automatic retry policy.

Failures are closed enum values: UNAVAILABLE, PERMISSION_DENIED, CAPTURE, STORAGE,
SOURCE_UNAVAILABLE, RECOGNITION, CANCELLED, INVALID_STATE, INVALID_RESULT, INTERNAL.
Raw exception messages/causes are absent from snapshots. Unexpected RuntimeException
is normalized as INTERNAL without logging. Adapters must not throw fatal errors
as ordinary failures. Original-audio and existing transcript ownership stays with
adapters; neither failure nor resetting the view deletes stored data.

## Privacy and validation boundary

Public values carry only canonical opaque IDs and typed states/errors. There are
no content bytes, text, paths, tokens, provider payloads, analytics or network calls.
IDs are application correlation, not provider request identifiers; do not derive
them from filenames, UI screen instances, content, credentials or external IDs.
Capture/processing adapters allocate and retain stable IDs across their future
durable lifecycle. This stage does not implement restart or idempotency persistence.

The static guard uses JDK jdeps on compiled model and coordinator classes, with an
allowlist, and checks the model's locked runtime dependencies. Missing bytecode or
unrecognized/empty dependency evidence fails closed. This catches fully qualified
provider references without a fragile Kotlin source grep. Negative controls compile
synthetic Java fixtures for provider, filesystem, network and logging violations.
This is an ordinary dependency guard, not an adversarial reflection/supply-chain audit.

JVM tests cover identities, command/acknowledgment sequences, wrong source/job,
delayed/duplicate callbacks, invalid transitions, failure preservation, uncertain
capture, capability loss and failed cancellation. Test fakes exist only under test.
The frozen product Gherkin scenarios remain future runtime acceptance, NOT_RUN.
