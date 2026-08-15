# Dora MVP 1 — Export Interaction Contract

Status: reusable design/governance contract for `DES-EXPORT-001`\
Contract identity: `des-export-interaction-v0.1`\
Version: 0.1\
Date: 15 August 2026\
Source snapshot: `main` `c35178662206180505896f6ab3044a5e6fa60718`, tree
`237a54d67958b107635a2e1ecf7864d162ae8461`\
Decision boundary: `DEC-017` is **Approved only for this versioned reversible design/governance
contract**

## 1. Purpose, authority and non-claims

This contract defines the reusable interaction, privacy, accessibility and synthetic acceptance
baseline for a future Dora export flow. It is subordinate to the Technical Plan, Design Spec,
Product Decisions, accepted ADRs and Test Strategy. If a higher-precedence source conflicts with a
future implementation, that affected work remains blocked until a DEC or ADR resolves the conflict.

The Project owner delegated ordinary reversible project/product decisions to the OpenAI Codex
project coordinator so the workflow does not stop. The exact source wording and authority boundary
are recorded in
[the machine-readable decision record](../evidence/des-export-001/decision-record-v0.1.json).
The coordinator is an advisory decision executor/reviewer with `formalReviewer=false`.

Completing this document means only `CONTRACT_COMPLETE`. It does not implement or approve Android
code, Figma, resources, schema, provider integration, FileProvider, URI grants, Storage Access
Framework, Sharesheet wiring or export execution. It does not establish accessibility conformance,
user-research evidence, device evidence, Legal approval, Security approval, dependency or
production admission, real-data collection, Recovery/device/measured execution or merge authority.
No human signature is represented or implied.

## 2. Authoritative traceability

| Source | Requirement carried into this contract |
|---|---|
| Technical Plan §§7, 20, 22, 24.2, 24.4, 31, 34–35 and P17 | local-first copy/export; portable text/Markdown/JSON/CSV; conscious destination; bounded temporary export; no physical-overwrite claim; content-free diagnostics |
| Design Spec §§4, 14, 25.3–25.4, 26.4, 28.4, 31.4–31.7, 32, 33–35 and `ST-07` | truthful stages, accessible selection and controls, sensitive-preview warning, explicit export/delete states, real progress only, Android destination surfaces |
| `DEC-009`, `DEC-014`, `DEC-015`, `DEC-017`, `DEC-038`–`DEC-040` | cloud off by default; no account/network/GMS prerequisite; no research reuse; explicit export baseline; privacy/accessibility boundaries |
| Test Strategy `TS-OFFLINE`, `TS-STORAGE`, `TS-PRIVACY`, `TS-ACCESSIBILITY` | offline usability, source-safe cleanup, no content leakage, semantics/reflow/input evidence |
| Privacy Threat Model §§2–5, 8, 10–13 | no silent outbound flow; exports inherit source sensitivity; scoped preview; temporary cleanup; external-copy residual risk; managed policy cannot initiate capture/export |
| Dataset Governance §§2, 9–14 | synthetic fixtures first; no real meetings, training or public sensitive evidence |
| Component Accessibility Contract | names/roles/states, focus, 48-dp targets, 200% reflow, gesture alternatives, truthful manual evidence status |

This contract resolves the `DEC-017` export baseline without modifying the Technical Plan, Design
Spec, token JSON or screen inventory. `ST-07` remains the screen identity; this contract supplies
its reusable state and acceptance detail.

## 3. Export plan and defaults

An export plan is a set of user-selected output items plus one explicitly selected destination.
Each output item is a `(content class, format/mode)` pair. The flow validates every pair and the
destination together before it can reach Review.

The following defaults are invariant:

1. No content, format/mode or destination is preselected.
2. No content, format/mode, destination or acknowledgement is remembered for a later export.
3. `Select all` may select Summary, Protocol, Transcript and Tasks only. It never selects Audio.
4. Audio is `OFF` for every new export and needs a separate acknowledgement every time it is
   selected.
5. An incompatible or policy-restricted item stays visible but disabled with a readable reason.
6. Empty, unsupported and policy-restricted validation creates no payload or temporary artifact.
7. An export cannot start from a remembered or managed-policy selection.

### 3.1. Content/format matrix

| Format or mode | Summary | Protocol | Transcript | Tasks | Audio | Availability and notes |
|---|---:|---:|---:|---:|---:|---|
| Copy/plain text | yes | yes | yes | yes | no | `Copy` is clipboard-only. A plain-text file is a file output. |
| Markdown | yes | yes | yes | yes | no | Unencrypted file output. |
| Versioned JSON v1 | yes | yes | yes | yes | no | Structured content classes only; raw audio bytes are excluded. The future schema remains separately scoped. |
| CSV | no | no | no | yes | no | Tasks only. Other content/CSV pairs are disabled with reason. |
| Audio WAV/PCM | no | no | no | no | yes | Explicit one-shot output only; default off; never selected by `Select all`. |
| Encrypted bundle | no | no | no | no | no | Deferred/not MVP and unavailable until a separate key/recovery decision. |

`JSON v1` must identify its version and may include only the explicitly selected structured content
classes. It must not contain raw audio bytes, file paths, content URIs, keys or an implicit audio
attachment. Exact field/schema authoring is not performed by this contract.

The contract does not require implicit cross-format aggregation. Every produced output item must be
explicit in Review. If a destination cannot accept the whole selected plan, that destination is
disabled with the specific incompatibility reason; Dora must not silently drop an output.

### 3.2. Destination matrix

| Destination | Allowed output | Success boundary | Required disclosure |
|---|---|---|---|
| Clipboard | `Copy`/plain text only | Android clipboard API accepted Dora's clip | Clearing is best-effort; other apps/system surfaces may already hold or read the clip. |
| Android Sharesheet | File outputs only | The share was handed to Android | Dora does not know whether another app read, saved, synced or delivered the file. |
| Storage Access Framework | File outputs only | Final write and close completed | The selected storage provider controls the resulting copy and may independently use network. |
| Dora cloud upload | none | not applicable | Unavailable; export has no Dora cloud route. |

Account, GMS, network and Dora cloud configuration are never prerequisites for opening or using the
approved local export flow. A selected external app or storage provider may independently require
an account or network; that behavior belongs to the selected external destination and is disclosed
before transfer.

## 4. Mandatory transfer warning

Before every clipboard, plain-text or other unencrypted transfer, show the warning below after
Review and before payload/artifact creation or transfer. There is no skip, remembered
acknowledgement or managed-policy bypass.

RU, exact:

> Dora передаст выбранные данные разговора как незашифрованный текст или файл. После копирования, сохранения или отправки Dora не управляет копией в буфере обмена, другом приложении или выбранном хранилище.

EN, exact:

> Dora will transfer the selected conversation data as unencrypted text or files. After copying, saving, or sharing, Dora does not control copies held by the clipboard, another app, or the selected storage provider.

The acknowledgement is one-shot and belongs only to the current export attempt. Audio
acknowledgement is a separate step and cannot be merged into this warning.

## 5. Deterministic 15-state interaction contract

The export session uses exactly the following 15 interaction states. Domain code may use different
symbols only if it maintains a documented one-to-one mapping and preserves every transition and
invariant below.

| ID | State | Meaning and permitted actions | Artifact rule |
|---|---|---|---|
| `EX-00` | `CLOSED` | No active export session. Opening creates a fresh, unselected plan. | No artifact. |
| `EX-01` | `SELECT_CONTENT` | Explicitly select structured content; Audio remains separate/off. Empty selection has an accessible reason and cannot advance. | No artifact. |
| `EX-02` | `SELECT_FORMAT` | Select explicit compatible formats/modes. Unsupported pairs are disabled with reason. | No artifact. |
| `EX-03` | `SELECT_DESTINATION` | Select Clipboard, Sharesheet or SAF where compatible. Policy restrictions are visible. | No artifact. |
| `EX-04` | `REVIEW` | Show exact output items, destination class, external-control disclosure, policy and expiry. Revalidate source, selection and compatibility. | No artifact. |
| `EX-05` | `AUDIO_ACKNOWLEDGEMENT` | Required once for this attempt when Audio is selected; it explicitly names WAV/PCM and external-copy control. | No artifact. |
| `EX-06` | `UNENCRYPTED_WARNING` | Show the exact RU/EN warning for this attempt; no skip or remembered acknowledgement. | No artifact. |
| `EX-07` | `PREPARING` | Create/serialize only the reviewed outputs after required acknowledgements. Cancel remains available when safe. | First state in which a Dora-managed temporary artifact may be created. |
| `EX-08` | `TRANSFERRING` | Call clipboard API, launch/complete SAF write, or hand file(s) to Android Sharesheet. Stage-only unless a real denominator exists. | Partial outputs are not reusable. |
| `EX-09` | `HANDOFF_SUCCEEDED` | Dora-specific success boundary was met; this is not recipient/provider delivery. | Live Dora temp, if any, remains governed until cleanup. |
| `EX-10` | `CLEANUP_PENDING` | Wait for/perform safe cleanup. Context stores `pendingOutcome = COMPLETED | FAILED_RETRYABLE | CANCELLED`. `Delete now` is available for a live Dora temp. | Live temp is visible in Recent exports with content-free metadata. |
| `EX-11` | `COMPLETED` | Dora handoff succeeded and no live temp or cleanup failure remains. | No persistent export-history entry. |
| `EX-12` | `FAILED_RETRYABLE` | Preparation/transfer failed after partial output was cleaned or no partial existed. Retry begins only through revalidation and Review. | No partial output may be reused. |
| `EX-13` | `CANCELLED` | User/system destination picker cancellation is complete and no live temp remains. | No persistent export-history entry. |
| `EX-14` | `CLEANUP_FAILED` | Dora could not delete a managed temp. Show content-free failure and retry/delete action. | Source content is untouched; failed temp stays visible and retryable. |

### 5.1. Transition rules

1. `CLOSED → SELECT_CONTENT` always creates an empty plan.
2. Valid explicit selection advances `SELECT_CONTENT → SELECT_FORMAT → SELECT_DESTINATION → REVIEW`.
   Back navigation reverses these steps without generating data.
3. Empty content, unsupported pair, destination incompatibility or managed-policy restriction cannot
   advance and cannot create an artifact. The exact reason remains focusable/readable.
4. `REVIEW` revalidates source existence/version, selected content, formats, destination and policy.
   A changed/invalid item returns to the applicable selection state with a reason.
5. If Audio is selected, confirmation follows `REVIEW → AUDIO_ACKNOWLEDGEMENT`. A decline returns to
   Review with Audio removed or cancels the attempt; it never acknowledges the unencrypted warning.
6. Every approved MVP transfer is unencrypted, so the current path reaches
   `UNENCRYPTED_WARNING` after Review and any separate Audio acknowledgement. A future encrypted
   path cannot bypass a new decision merely because the state exists.
7. Accepting the warning enters `PREPARING` for file outputs or `TRANSFERRING` for Clipboard when no
   temporary artifact is needed.
8. Cancelling before any artifact exists enters `CANCELLED`. Cancelling in `PREPARING` or
   `TRANSFERRING` with a live/partial Dora artifact enters `CLEANUP_PENDING` with
   `pendingOutcome=CANCELLED`.
9. Cancelling a system destination picker is not success. If no Dora temp exists it enters
   `CANCELLED`; otherwise it converges through `CLEANUP_PENDING`.
10. A preparation/transfer failure with a live/partial Dora artifact enters `CLEANUP_PENDING` with
    `pendingOutcome=FAILED_RETRYABLE`; without one it enters `FAILED_RETRYABLE`.
11. A Dora-specific success enters `HANDOFF_SUCCEEDED`. If no Dora temp exists, it may enter
    `COMPLETED`; otherwise it enters `CLEANUP_PENDING` with `pendingOutcome=COMPLETED` after safe
    handoff.
12. Successful cleanup from `CLEANUP_PENDING` enters the stored `pendingOutcome`. Failed cleanup
    enters `CLEANUP_FAILED` while retaining that outcome.
13. `Retry cleanup` enters `CLEANUP_PENDING` and retries deletion only. On success it enters the
    retained outcome. It never repeats the export.
14. `Retry export` from `FAILED_RETRYABLE` discards partial state, revalidates source/selection/policy
    and returns to `REVIEW` only when valid. If revalidation fails, it returns to the applicable
    selection state with a reason. There is no background resume or partial-output reuse.
15. Closing `COMPLETED` or `CANCELLED` enters `CLOSED`; the next session is empty. Closing a failure
    must not hide a live temp or cleanup failure from Recent exports.
16. `Delete now`, expiry and startup orphan cleanup enter `CLEANUP_PENDING` with the retained
    content-free `pendingOutcome`. An orphan without a valid retained outcome uses `CANCELLED`
    fail-closed, so cleanup cannot manufacture an export-success claim. Success and failure then
    follow rules 12–13.

### 5.2. Global invariants

- Handoff is not recipient delivery. No state may claim that another app/provider read, saved,
  synced or delivered the output.
- No payload/temp creation occurs in `EX-00` through `EX-06`, including empty/unsupported/policy
  validation or destination-picker exploration.
- Audio acknowledgement and the unencrypted warning are separate, attempt-scoped gates.
- No cleanup transition deletes or mutates the source Conversation, source Audio or canonical
  content.
- No state or deletion copy promises physical overwrite of flash/provider storage.
- A managed policy can remove capabilities or shorten expiry only. It cannot select, enable,
  acknowledge or initiate export.
- Logs, diagnostics, state persistence and Recent exports contain no payload, user content, path,
  URI, key or destination credential.

## 6. Temporary-artifact lifecycle

1. Create a Dora-managed share temp only after Review, separate Audio acknowledgement when
   applicable, and the mandatory unencrypted warning.
2. Clean it on cancellation, preparation/transfer failure and after safe handoff.
3. Offer `Удалить сейчас` / `Delete now` while a live Dora temp exists.
4. A Dora-managed share temp has a hard maximum lifetime of one hour from creation. Managed policy
   may shorten, never extend, this limit.
5. On startup, discover and clean expired/orphaned Dora temps from the exact allowlisted Dora temp
   area. Unknown files are not export artifacts and are not deleted by this contract.
6. Failed cleanup remains visible and retryable until resolved. It is not renamed `complete`.
7. SAF and other external-destination copies are outside Dora expiry and cannot be deleted by
   `Delete now` or startup cleanup.
8. Cleanup deletes only Dora-managed temporary outputs. Source Conversation, Audio, Summary,
   Protocol, Transcript and Tasks are never cleanup targets.
9. Logical/best-effort deletion wording is used. Physical overwrite is never claimed.

### 6.1. Recent exports

MVP has no separate persistent export history. `Recent exports` is a live operational surface only:

- show currently live Dora-managed temporary artifacts and cleanup failures;
- use content-free metadata only: opaque transfer ID, created/expiry time, format identifiers, file
  count/byte count and cleanup state/error code;
- never show conversation title, excerpt, participant, path, URI, destination identity, provider
  credential or payload;
- remove the entry immediately after successful cleanup;
- do not add Clipboard or completed SAF/external copies as history entries.

## 7. Clipboard contract

- Clipboard export is an explicit user action and accepts only Copy/plain text.
- Show the mandatory warning for every Copy attempt.
- Success means only that the Android clipboard API accepted Dora's clip.
- Schedule a best-effort clear after 60 seconds only if the clipboard still contains Dora's exact,
  unchanged clip identity/content. Never clear a changed or replacement clip.
- Clipboard clearing is not guaranteed: Android, another app or the user may replace, retain, read
  or transform the clip before or after the scheduled check.
- Clipboard content and any comparison material must not enter logs, diagnostics, analytics or the
  machine-readable evidence record.

## 8. Managed-policy contract

A visible managed policy may:

- disable/restrict one or more formats, destinations or Audio;
- shorten the one-hour maximum Dora-temp expiry;
- provide a content-free reason and policy source label.

It may not:

- enable or preselect content, format, destination or Audio;
- accept either acknowledgement;
- initiate export or destination selection;
- suppress the exact warning or external-control disclosure;
- silently switch Clipboard/Sharesheet/SAF or invoke Dora cloud;
- extend expiry or change source-deletion boundaries.

## 9. Progress, errors and diagnostics

- Determinate progress is allowed only with a real numerator and denominator, such as bytes written
  over known total bytes or completed items over a known item count.
- Otherwise show stage-only text such as `Preparing`, `Writing`, `Handing to Android` or `Cleaning`.
- Do not infer Sharesheet progress after handoff or SAF provider sync/delivery progress.
- Error UI states what remains safe, names the failed stage, gives a retry/cleanup/change-selection
  action and never reports source loss from export cleanup.
- Diagnostics use bounded categorical codes and numeric counters only. They contain no path, URI,
  filename derived from content, key, payload, title, excerpt, participant, destination credential
  or user content.

## 10. Accessibility contract

The selection, Review, Audio acknowledgement, unencrypted warning, transfer cancellation and
cleanup-failure actions are `CRITICAL` for this privacy flow. A future implementation must create
component records under the Component Accessibility Contract; this document does not mark them
`PASS`.

Minimum requirements:

1. Each content, format and destination option exposes a stable accessible name, role, selected or
   disabled state and readable disabled reason. Disabled reason is not color-only.
2. `Select all` announces exactly the four structured content classes and explicitly states that
   Audio is excluded.
3. Audio has an independent control and independent one-shot acknowledgement. It is not merged
   semantically with Select all or the unencrypted warning.
4. The exact warning is fully available to TalkBack, Switch Access and keyboard/D-pad users before
   the confirmation action. Initial modal focus is the warning title/body, not confirmation.
5. Review reading order is title → selected output items → destination/external-control disclosure
   → temp expiry → primary action → secondary/cancel actions.
6. Touch targets are at least 48 × 48 dp with at least 8 dp between independent actions. Picker,
   swipe or drag interactions have visible alternatives where applicable.
7. At 200% font scale, critical content/actions reflow without clipping, hidden warning text or
   horizontal content pan. RU, EN, long/pseudo-localized and maximum-display-size fixtures are
   required.
8. Status never relies on color, animation, percentage or icon alone. Live regions announce only
   meaningful state changes and do not repeat byte/timer ticks.
9. Determinate progress exposes its real value; stage-only progress exposes a stage description,
   not a fabricated percentage.
10. `CLEANUP_FAILED` exposes `Retry cleanup` and, where safe, `Delete now`; focus remains stable and
    failure cannot disappear behind a transient snackbar.
11. System destination-picker cancellation returns focus to the invoking destination/action and is
    announced as cancelled, not failed or complete.
12. Reduced motion/animator 0× preserves every state, warning, action and progress meaning.

## 11. Synthetic fixture and acceptance matrix

Only original deterministic synthetic text/audio metadata is allowed. No real meeting, person,
voice, title, path, provider credential or private destination is needed or authorized.

| Fixture ID | Scenario | Expected contract result |
|---|---|---|
| `EXP-SYN-001` | Fresh open | `CLOSED → SELECT_CONTENT`; all selections empty; no artifact. |
| `EXP-SYN-002` | Empty content then Continue | Remain `SELECT_CONTENT`; accessible reason; no payload/temp. |
| `EXP-SYN-003` | Summary + Copy + Clipboard | Review; exact warning; clipboard success means API accepted only. |
| `EXP-SYN-004` | Protocol/Transcript + Markdown + Sharesheet | File-only destination; warning before temp; handoff does not claim recipient delivery. |
| `EXP-SYN-005` | Four structured classes + JSON v1 | JSON version visible; raw audio/path/URI/key excluded. |
| `EXP-SYN-006` | Summary + CSV | Pair disabled with reason; no artifact. |
| `EXP-SYN-007` | Tasks + CSV + SAF | Allowed; success only after final write/close. |
| `EXP-SYN-008` | `Select all` | Selects Summary/Protocol/Transcript/Tasks only; Audio remains off. |
| `EXP-SYN-009` | Audio WAV/PCM | Separate acknowledgement every attempt, then exact warning; no remembered state. |
| `EXP-SYN-010` | Encrypted bundle | Visible deferred/not-MVP disabled reason; no artifact. |
| `EXP-SYN-011` | Managed policy disables Audio/Clipboard | Options visibly disabled with reason; policy does not select a fallback. |
| `EXP-SYN-012` | Policy expiry 15 minutes | Review shows 15 minutes; still below hard one-hour maximum. |
| `EXP-SYN-013` | External provider may use network | Disclosure present; Dora itself has no network/account/GMS prerequisite. |
| `EXP-SYN-014` | Destination picker cancelled before temp | `CANCELLED` or return to selection by explicit UI policy; no artifact/success claim; focus restored. |
| `EXP-SYN-015` | Picker cancelled after a temp exists | `CLEANUP_PENDING(pendingOutcome=CANCELLED)`; success→`CANCELLED`; failure→`CLEANUP_FAILED`. |
| `EXP-SYN-016` | Cancel during `PREPARING` | Partial is not reusable; cleanup convergence; source remains byte-for-byte/logically unchanged. |
| `EXP-SYN-017` | Cancel during `TRANSFERRING` | Same cleanup convergence; no background resume. |
| `EXP-SYN-018` | SAF write fails before close | Not success; cleanup partial; retry can start only through revalidation and Review. |
| `EXP-SYN-019` | Sharesheet accepts intent | `HANDOFF_SUCCEEDED`; never claim another app read/saved/synced/delivered. |
| `EXP-SYN-020` | Clipboard clear at 60 s, clip unchanged | Best-effort clear attempted; no guarantee claim. |
| `EXP-SYN-021` | Clipboard changed before 60 s | Dora does not clear the replacement clip. |
| `EXP-SYN-022` | `Delete now` on live Dora temp | Delete only temp; remove Recent entry after success; source untouched. |
| `EXP-SYN-023` | Dora temp reaches one hour | Cleanup triggered; external/SAF copy unaffected. |
| `EXP-SYN-024` | Expired/orphaned temp found at startup | Allowlisted Dora temp cleanup; unknown file untouched. |
| `EXP-SYN-025` | Cleanup deletion fails | `CLEANUP_FAILED` stays visible/content-free/retryable; no complete claim. |
| `EXP-SYN-026` | Cleanup retry succeeds | Return through `CLEANUP_PENDING` to retained pending outcome; no export retry. |
| `EXP-SYN-027` | Export retry after transfer failure | Discard partial; revalidate; return to Review; no background resume/reuse. |
| `EXP-SYN-028` | Known bytes/items vs unknown provider stage | Real fraction only for known denominator; otherwise stage-only. |
| `EXP-SYN-029` | Diagnostics capture on every state/error | No path, URI, key, payload, user content or destination credential. |
| `EXP-SYN-030` | 200% RU/EN + TalkBack/Switch/keyboard/reduced motion | Warning, selections, reasons, cancel, cleanup and retry remain reachable and understandable. |

### 11.1. Future implementation acceptance

An exact implementation can claim `PASS` only when all applicable fixtures above have reproducible
automated evidence and every critical accessibility record required at its current gate passes.
Additional required assertions:

- zero artifact creation before `PREPARING`;
- zero Audio selection through `Select all` or remembered state;
- warning shown once per attempt and never skipped;
- zero source deletion/mutation across cancel, failure, expiry, orphan cleanup and cleanup retry;
- no persistent export-history row after cleanup;
- no prohibited diagnostics field or outbound Dora network prerequisite;
- no success text beyond the destination-specific Dora boundary.

## 12. Copyable future evidence record

Copy the fields below for an exact implementation or Figma flow. Do not remove fields; use
`NOT_RUN` or `NOT_APPLICABLE` with a reason.

```text
# Export interaction evidence record

recordId:
recordVersion:
recordDate/timezone:
contractIdentity: des-export-interaction-v0.1
implementationOrPrototypeCommit:
treeOrArtifactDigest:
owner:
formalReviewer:
authoritativeSources:

## Plan/matrix
contentFixtures:
formatFixtures:
destinationFixtures:
policyFixtures:
freshDefaultsResult:
compatibilityDisabledReasonResult:
audioSelectAllExclusionResult:

## State machine
stateMappingAll15:
transitionCoverage:
noArtifactBeforePreparingResult:
destinationPickerCancelResult:
cancelPrepareTransferResult:
pendingOutcomeCleanupResult:
handoffBoundaryResult:
retryFromReviewResult:

## Privacy/lifecycle
exactWarningRuEnResult:
externalControlDisclosureResult:
tempExpiryDeleteNowStartupOrphanResult:
cleanupFailureRetryResult:
sourceNeverDeletedResult:
clipboard60SecondUnchangedCheckResult:
recentExportsContentFreeNoHistoryResult:
diagnosticRedactionResult:

## Accessibility
semanticRecords:
talkBackResult: NOT_RUN | PASS | FAIL
switchAccessResult: NOT_RUN | PASS | FAIL
keyboardDpadResult: NOT_RUN | PASS | FAIL | NOT_APPLICABLE
fontScale200RuEnResult:
reducedMotionResult:
focusRestorationResult:

## Verification
syntheticFixtureResultsEXP_SYN_001_to_030:
automatedCommandsAndResults:
manualDeviceEvidence:
userResearchStatus: NOT_RUN | COMPLETE
legalReviewStatus: NOT_RUN | COMPLETE
securityReviewStatus: NOT_RUN | COMPLETE

## Verdict
result: PASS | BLOCKED | NOT_RUN
unresolvedFindings:
nonClaims:
reviewerAndFormalStatus:
```

## 13. Result vocabulary and evidence status

| Result | Meaning |
|---|---|
| `CONTRACT_COMPLETE` | This reusable `DES-EXPORT-001` contract and its synthetic acceptance matrix exist. No implementation or conformance claim is implied. |
| `PASS` | A separately scoped exact implementation/prototype has passed every applicable contract and gate with linked evidence. |
| `BLOCKED` | A required decision, state, privacy/accessibility control or evidence is missing/failed. |
| `NOT_RUN` | The named implementation, device, accessibility, Legal/Security or research evidence was not executed. |

Current evidence status for version 0.1:

- contract and traceability: `CONTRACT_COMPLETE`;
- Android/code/resources/manifests/dependencies: not changed, `NOT_RUN` for implementation;
- Figma/prototype: `NOT_RUN`;
- JSON export schema/provider/SAF/Sharesheet/FileProvider wiring: not created, `NOT_RUN`;
- synthetic fixture execution: `NOT_RUN`; the matrix is the future acceptance input;
- TalkBack/Switch/keyboard/device/accessibility conformance: `NOT_RUN`;
- user research/real data: `NOT_RUN` and not authorized;
- Legal/Security approval: `NOT_RUN` and not claimed;
- the nine implementation/execution/admission Recovery flags snapshotted in the machine record:
  unchanged/false;
- production/dependency admission and merge: not authorized.

## 14. `DES-EXPORT-001` exit traceability

| Backlog exit evidence | Contract location |
|---|---|
| Accessible selection | §§3, 5, 10 and fixtures `EXP-SYN-001`–`013`, `030` |
| Plain-share warning | §4, state `EX-06`, fixtures `003`–`009` |
| Cleanup state | §§5–7, states `EX-10`/`EX-14`, fixtures `015`–`027` |

This mapping closes only the reusable design/governance contract deliverable. It makes no
implementation, accessibility-conformance or user-research claim.
