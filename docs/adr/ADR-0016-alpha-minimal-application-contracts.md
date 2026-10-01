# ADR-0016: Minimal Alpha application data and port contracts

Status: ACCEPTED FOR 7.2 APPLICATION CONTRACT ONLY
Date: 30 September 2026
Authority: explicit owner task 7.2; no downstream runtime admission.

## Decision

Reuse core:model for typed opaque recording/audio-version/job/transcript-version
identities, immutable snapshots, normalized errors and generic capture/storage/
recognition ports. Keep a small JVM-testable coordinator in app/flow and wire the
existing UI to explicit unavailable adapters. Do not create a new Gradle module
or add a dependency. core:common remains independent utilities; PoCs stay isolated.

Capture, storage and recognition states remain separate, following Technical Plan
§30. Each coordinator tracks one sequential original-audio handoff and is not the
global recording/job schema. Stage 8 can host multiple coordinators to preserve
capture independence. Stable audio ID means one immutable source version; frozen
ASR TranscriptId already means a result-version. References alone do not select,
overwrite or persist active transcript text.

Callbacks report factual outcomes and are correlated to an in-memory operation.
Duplicate/late completions fail closed. Uncertain resource shutdown and failed
cancellation retain control references and prohibit Reset until resolved. Ports
own async scheduling, resources and retained data; no blocking adapter call is
allowed under the coordinator monitor. Availability does not grant Cloud consent.

## Alternatives

New domain/data/usecase modules: rejected because this slice has no infrastructure
implementation requiring such separation. Direct PoC/provider reuse: rejected
because those dependencies are not product adapters and would violate admission
and replacement boundaries. One combined capture/job enum: rejected because
capture and processing are independent activities.

## Consequences and evidence

See [versioned contract and implementation plan](../stage1/DORA_ALPHA_MINIMAL_MODULES_DATA_CONTRACTS_V0_1.md)
and [machine-readable scope](../contracts/DORA_ALPHA_MINIMAL_MODULES_DATA_CONTRACTS_V0_1.json).
Compile-time dependency inspection, JVM state/boundary tests and full exact-SHA CI
are required before closing 7.2. The contract contains no audio/text/path/secret.

ADR-0009 remains the mandatory future DORA-controlled Cloud boundary. This generic
app RecognitionPort does not implement CloudAsrProvider (7.2C), version persistence
(7.2D), identity/ownership/auth (7.2E), release acceptance (7.3), Stage 8 runtime or
Stage 9 ASR. 7.1 identity, version and signing are unchanged. Recovery clean
replacement remains separately required before Stage 8 recording/storage acceptance.
