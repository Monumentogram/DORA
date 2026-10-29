# Stage 10.3C search implementation plan

> Execute inline using executing-plans; independent whole-change review at completion.

**Goal:** offline substring search in the real Signal recording library.
**Spec:** owner's Stage 10.3C brief in the task; baseline PR123 c20b6ce.
**Architecture:** reuse SQLCipher candidate paging and authenticated transcript reads. The
current schema stores versioned documents as binary payloads, not searchable text columns.
PoC FTS is not product-admitted and prefix token matching does not meet substring semantics.
Scan bounded candidate pages on the persistence worker; render only activeId plus its latest
edit, recover audio only for matches, retain bounded display projections, cancel between rows.
No migration, persistent index, dependencies, network, query logging or saved-state text.
**Tech stack:** existing Kotlin, Compose, Room/SQLCipher, executor and runtime authority.

## Constraints and review focus

- Preserve audio, history, keys, model, identity, Cloud and historical evidence.
- Existing title = active first nonblank line, otherwise localized short recording ID.
- Transcript date remains explicitly a transcript date; no invented recording date.
- Empty/trimmed query restores ordinary paging; literal Unicode case-insensitive substring.
- Suppress stale results immediately, cancel obsolete scans, invalidate on editor dismissal,
  background/resume and refresh; hide/suspend library while editor owns the vault session.
- Distinguish missing transcript from read failure; never show technical exceptions.
- Guard sparse matches across pages, page boundaries, long text, rapid queries, access revocation,
  edit/selection/delete freshness and navigation to editor/player.
- Physical device installation is excluded; DEVICE_VALIDATION_PENDING.

## Tasks

- [x] 1. Add RecordingSearch projection and cancellable bounded page scan in core audio.
  Unit tests: title/full text, RU/EN/mixed case/substring/Unicode/literal metacharacters,
  trimmed/empty query, latest edits/active selection, sparse paging, cancellation, failure,
  long text and bounded snippets. Run red, implement, run green.
- [x] 2. Wire authenticated vault/runtime search page through existing candidate query;
  integrate debounced Signal library with search/open/clear/close/focus, snippets and states.
  UI tests on synthetic encrypted recordings, real editor/player regression checks.
- [x] 3. Test real encrypted storage at 10/100/1000 synthetic rows, restart/offline,
  edits, selection, tombstones, long text and rapid input. Capture synthetic screenshots.
- [x] 4. Run relevant units, formatting, static analysis and lint; independent review;
  build/sign with existing Preview identity, compare native/assets/signature, hash APK.
- [ ] 5. Append decision/status/evidence, commit, push Draft PR123 without merge;
  report exact results and remaining physical acceptance separately.

## Ledger

- PR123 remote verified OPEN/DRAFT/UNMERGED, head c20b6ce matches local clean worktree.
- Owner supplied detailed implementation scope and minimal integration preference;
  routine design choices resolved inline without an additional approval round.
- Search is linear in candidate count for rare/no hits; each scan holds one decoded
  document and at most 20 display projections. The UI retains projections for explicitly
  loaded pages, as in the existing library; it never retains full documents/history.
- Core matching tests passed after the initial missing-implementation red run.
- Independent review found a quadratic long-query cancellation issue. Added a failing
  matcher regression, then linear Unicode code-point matching with cancellation checkpoints.
  Final core unit suite passed. Oversized synthetic performance fixture corrected to fit
  the existing 262144-byte text limit; storage limits are unchanged.

- Final verification: 397 unit, 3 host, 5 applicable UI, 2 encrypted persistence tests PASS; static/lint/build/signing PASS. Full 1000-row scan median 4005 ms, max 5900 ms; documented limitation. Legacy ASR absent-model assertion timeout retained separately and not investigated.
