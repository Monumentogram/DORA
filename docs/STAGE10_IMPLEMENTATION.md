# Stage 10 — Signal-inspired Android UI

2026-10-29. Execution of the owner's Stage 10 brief. Implementation in progress.

Base: `a6130cf` (Stage 9.3), a descendant of PR119 `e967fa4`. The earlier
`stage/10-signal-ui-preparation` plan and ADR-SIGNAL-001 were inspected. No PR
is merged. Target branch: `codex/stage10-signal-ui`.

## Execution decisions

- Current brief supersedes the earlier mint/dark-only direction: blue semantic
  accents, light and dark system themes, existing Compose/Material3 and type scale.
- Retain the real sampled level visualization. The optional PCM envelope/adaptive
  FPS work is deferred to avoid changing capture in this presentation task.
- Use existing authenticated recovery and transcript ports for the home library.
  No search index exists; no search box claiming full-text search will be added.
- The baseline stores neither recording titles nor recording wall-clock dates.
  Display an active transcript excerpt as title where available, otherwise a stable
  short recording identifier. Label transcript dates as transcript dates; never
  invent a recording date or add a storage migration for cosmetic metadata.
- Add foreground-only original audio playback using the existing authenticated,
  bounded ProtectedPcm extraction. Never write plaintext audio to disk. Clear audio
  on background/disposal/focus loss, and expose honest unavailable/error states.
- Preserve package/version/signing, native libraries, model, encrypted formats,
  existing Cloud identity, consent and backend. Build OwnerPreview with existing
  signing custody. Emulator only; physical install requires separate owner consent.

## Tasks and verification

1. Semantic theme and real library/navigation, accessible empty/loading/error states.
2. Recording presentation with existing lifecycle, consent, pause/resume/stop handlers.
3. Original player and readable transcript/editor/history with guarded draft exit.
4. Local/Cloud status and existing settings entry points; RU/EN presentation.
5. Unit, lint/static checks, emulator flows at large text and light/dark screenshots,
   original-signer APK, independent branch review, Draft PR and final evidence.

Status ledger and concrete test outcomes are appended during implementation.
Physical acceptance remains `DEVICE_VALIDATION_PENDING`.

## Latest verification and disk cleanup checkpoint (2026-10-29)

- Baseline unit tests and backend contract suite passed (64 backend tests).
- An earlier complete emulator UI run passed 9/9 tests; a large-font run passed
  3/3. The latest native-enabled UI run failed 2/9: recording start timed out,
  and the transcript fixture could not create its logical recording. Investigate
  these failures before declaring acceptance; prior passing runs do not supersede
  the latest failures.
- Independent review identified playback opening a vault session during a busy
  transcript action. Playback now observes the action busy gate and pauses when
  it becomes busy; the regression is covered by the acceptance test.
- Static verification and the final signed OwnerPreview APK remain pending.
  Native build succeeded, but a prior static-check process exhausted host memory.
- The user requested disk cleanup. C: reported zero free bytes. A read-only scan
  identified 515 ignored cache/intermediate directories in older worktrees,
  approximately 3.81 GiB, excluding this worktree, APK outputs and reports.
  Automated deletion was rejected by the execution policy (`blocked by policy`),
  including a narrowed attempt on one verified ignored intermediates directory.
  No cleanup deletion was executed. A preview-by-default cleanup script and its
  manifest are in the workspace-level `outputs` folder for manual use.
- Stopped the two Gradle daemons used by this task after checking their build
  directories. Source changes are retained. Final APK, final evidence, commit,
  push and Draft PR are still outstanding. Stage 10 is not accepted as complete.

## Resolution after explicit cleanup authorization (2026-10-29)

Cleanup completed: all 515 checked directories removed, free-space increase 4.14 GiB.
Fixture session ownership and large-font screenshot visibility checks were corrected.
The final editor test, 9-test UI suite, 3-test large-font suite, English navigation,
real EN/RU native recognition, 390 unit tests and 64 backend tests passed. Formatting,
detekt, lint and the signed OwnerPreview build passed. The final artifact's signer,
manifest, native payload and runtime assets match Preview5. See STAGE10_RESULT.md for
the bounded SIGNAL_UI_ALPHA_READY result and the remaining physical/live-Cloud checks.
