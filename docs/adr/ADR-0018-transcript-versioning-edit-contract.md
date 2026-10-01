# ADR-0018: Immutable transcript versions, separate edits and explicit selection

Status: ACCEPTED FOR 7.2D LOGICAL CONTRACT ONLY
Date: 30 September 2026

Preserve frozen ASR v0.1: raw LOCAL/CLOUD output is immutable; user edits are
separate authoritative history; alignment and merge proposals are derived evidence.
MERGED creates a new immutable version. ACTIVE is a guarded selection, never an engine.
Existing TranscriptId identifies the result version; publication order, edit revision
and selection revision are distinct logical concepts.

Compound source/version/segment/audio/context anchors replace offset-only assumptions.
Cross-version mapping is explicit; ambiguous/unmapped/conflicted edits are retained
for user resolution. Proposals bind edit and selection revisions. No stale decision,
late callback or automatic ranking may override a manual choice or correction.

Choose a closed versioned logical catalogue and offline validation. Reject a mutable
transcript string because it destroys provenance; defer physical persistence and a
real alignment/replay algorithm to their separately admitted implementation stages.
DEC-013 and the frozen deletion contract remain authoritative; no retention policy,
physical schema, migration, tombstone payload or erasure mechanism is selected here.

See [specification and plan](../stage1/DORA_ALPHA_TRANSCRIPT_VERSIONING_EDIT_CONTRACT_V0_1.md)
and [logical catalogue](../contracts/DORA_ALPHA_TRANSCRIPT_VERSIONING_EDIT_CONTRACT_V0_1.json).
7.2E/7.3/7.3C/Stage 8 NOT_STARTED; persistence, auth and merge runtime NOT_IMPLEMENTED.

Retain monotonic processing history across cancellation, and require all merge
parent and contributing edit-base versions to precede the derived publication.
Snapshot checks cover retained historical decision invariants; current source
availability and scheduling eligibility require transition validation.
