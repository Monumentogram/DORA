# ADR-SIGNAL-003: Current-text search in the encrypted Alpha library

Date: 2026-10-29. Status: accepted within the owner's explicit Stage 10.3C scope.
Baseline: PR123 `c20b6ce0d15174f4ad1fb8e78f5cfac834e823b7`.

## Context

The owner requests working offline substring search in Signal UI with authoritative
user edits and active-version selection. The product's Room/SQLCipher v3 journal
stores a versioned binary `transcript_state.payload`; it has no current-text column
or product FTS index. `:poc:search` has an isolated FTS4 schema and prefix-token
contract, not this document/authority contract. Importing that PoC would require a
migration and transactional derived-state maintenance outside this narrow Alpha scope.

## Decision

- Reuse `recordingCandidatePage`, its owner/vault binding, keyset cursor, tombstone
  exclusion and limit of 20. Read documents through the existing authenticated
  transcript port; missing document is distinct from a failed read.
- Search only `activeId` and `rendered(activeId)`, which resolves the latest saved edit
  for that version. Never search inactive machine texts or historical edits. Search
  is read-only on transcripts; it does not change selection, revisions or history.
- Preserve the existing first-nonblank-line title and localized short-ID fallback.
  No user-title system, capture date or timestamp alignment is introduced.
- Scan candidates on the existing serialized persistence worker, recovering audio
  only for matches through the existing library recovery path. Do not decrypt/scan
  audio to decide text matches. Deliver up to 20 matches and the last scanned cursor.
- Literal Unicode simple-case substring matching uses a linear prefix matcher with
  cancellation checkpoints inside long text. Query edge whitespace is trimmed; empty
  input restores ordinary library paging. No token syntax, ranking or semantic search.
- Display only bounded title/snippet/date/processing projections; at most one decoded
  document and one result page are held by a scan. The UI retains projections for
  explicitly loaded pages, as before, not full version histories. Transcript timestamps
  remain labelled as transcript dates. Matching snippets use the semantic blue accent.
- Debounce input by 200 ms; immediately clear obsolete rows and advance a generation.
  Check generation/foreground/session before delivery. Cancel before opening editor,
  refresh on dismissal, and clear query/results on background. No query in saved state,
  files, logs, telemetry or network calls.
- Keep storage schema, encryption, Keystore, package/signer, native code, model and
  Cloud registration/backend unchanged. POCO installation requires separate permission.

## Consequences

Rare/no-match searches are linear in library size. This is measured at 10/100/1000
synthetic encrypted recordings, not presented as production-scale FTS qualification.
Cancellation occurs between candidates and during matching; existing bounded storage
reads/audio recovery calls remain indivisible. Historical text remains accessible in
the editor, separate from search. Canonical normalization, accent folding, fuzzy search,
word-level audio seek and a persistent index are outside this scope.

This supersedes only ADR-SIGNAL-002's deferred-search bullet. The owner's Stage 10.3C
scope selects the minimal Alpha implementation over the broader future FTS architecture
in Technical Plan §26.3; it does not alter that historical MVP plan or its PoC gates.
