# ADR-0003: Internal Alpha manual local workspace

Status: Accepted for `DORA-ALPHA-001` Increment 1 only\
Date: 17 August 2026\
Decision owner: Project owner\
Related: `ALPHA-GATE-001`, `DEC-045`, `RDY-002`, ADR-0001

## Context

Stage 0 is an evidence program and does not yet admit production capture, encrypted storage, ML or
backend dependencies. The Project owner has separately authorized a product Alpha that must deliver
useful value without waiting for formal 100% Stage 0 closure and without representing incomplete
PoCs as working capabilities.

The largest honest slice available from the admitted toolchain is a user-authored local meeting
workspace. It can validate the core edit/history/task workflow without audio, models, automatic
analysis, account, GMS or network. It also resolves the Alpha-specific provenance problem: manual
tasks are user-owned and must not receive a fabricated transcript event.

This internal build needs restart persistence, but production Room/SQLCipher and audio crypto are
not admitted. Adding either would expand the authority and dependency/security surface beyond the
selected Alpha increment.

## Decision

1. Reuse the existing `:app` module, Kotlin/Compose stack, semantic design tokens and non-release
   `com.monumentogram.dora.bootstrap` application ID.
2. Implement only conversation-scoped user-authored title, transcript/notes, manual summary and
   manual tasks, plus local view/edit/search/task-toggle/delete.
3. Keep recording/import/audio, VAD, ASR, diarization, automatic summary/tasks, models, export,
   network/cloud/account/GMS visibly unavailable.
4. Persist one versioned Alpha snapshot in app-private storage through `android.util.AtomicFile`.
   Use a bounded defensive binary codec with exact magic/version, explicit UTF-8 lengths, maximum
   file/record/task/field limits and unique identifier validation.
5. On a corrupt, truncated, oversized or unsupported snapshot, expose a fail-closed error and do
   not silently overwrite it as empty state. A verified previous AtomicFile backup may be recovered
   by the platform adapter.
6. Every save writes the complete canonical Alpha snapshot atomically. A reported successful edit
   or delete must already be present in that persisted snapshot.
7. Tasks have `origin=USER`, belong to one Alpha conversation and do not require or fabricate a
   source event. Standalone tasks are outside Increment 1. This resolves `RDY-002` only for the
   disposable Alpha model and does not change the future production schema decision.
8. Show an in-app warning that this is an internal, unencrypted Alpha for synthetic or
   non-sensitive test text. Do not claim SQLCipher, cryptographic erasure, secure backup, physical
   overwrite or production privacy readiness.
9. Add no new runtime dependency, Android permission, service, receiver, provider, native artifact,
   model, endpoint or analytics path.

## Consequences

Positive:

- the first useful Alpha can be installed and exercised without blocked recording/ML/data gates;
- every visible result is authored by the user;
- restart, editing, search, task and delete behavior can be tested end to end;
- the adapter is small, replaceable and dependency-free.

Costs and risks:

- the store is bounded and not suitable for production scale;
- content has Android app-private/platform protection only and no Dora database encryption;
- no audio-linked evidence or automatic result exists;
- a future admitted production store needs an explicit migration/discard decision.

## Verification

- codec round-trip, Unicode, bounds, duplicate IDs, truncation and unsupported-version tests;
- atomic save/load/restart and save-failure behavior tests at the repository/controller boundary;
- UI tests for create/edit/search/task-toggle/delete confirmation and unavailable recording;
- no microphone/network permission or new dependency in manifests/graph;
- repository-required formatting, detekt, unit, androidTest compile, lint, assemble, validator and
  APK/native checks;
- physical smoke before the full Alpha DoD claim.

## Supersession rule

This ADR cannot admit Room, SQLCipher, audio, cryptography, models, production identity or release.
A later ADR must define exact migration, dependency/security evidence and owner scope. Alpha data
must never be silently reclassified as production-secure data.
