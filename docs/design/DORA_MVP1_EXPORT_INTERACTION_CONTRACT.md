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

The narrow `DEC-017` reconciliations in Technical Plan §24.4 and Design Spec §31.1 explicitly defer
the encrypted bundle and remove persisted/default export selections. No token JSON or screen
inventory is changed. `ST-07` remains the screen identity; this contract supplies its reusable
state and acceptance detail.

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
| Android Sharesheet | File outputs only; use a Dora-managed temp copy, never the canonical source | The share was handed to Android | Handoff is not recipient delivery. Retain the temp until an observable safe-release event, warned `Delete now` or effective expiry; startup recovery must preserve that access window. |
| Storage Access Framework | File outputs only; direct write or optional Dora staging | Final destination write and close completed | A Dora staging temp becomes cleanup-eligible only after successful destination close. The selected provider controls its copy and may independently use network. |
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

On startup, `HANDOFF_SUCCEEDED` is also the conservative live-temp retention state when corrupt or
missing recovery context makes the original handoff boundary unprovable. In that degraded case the
UI says that handoff is unverified and makes no success or delivery claim; the state is reused only
to avoid premature deletion without adding a sixteenth state.

### 5.1. Normative transition relation

The tuple `(source, event)` is the deterministic key. Every row has one guard, one action and one
target. Product UI that offers alternative outcomes must expose distinct events or fix the named
policy input before the triggering action. The machine record contains the same 86 rows; row-by-row
Markdown/JSON parity is mandatory.

| ID | Source | Event | Guard | Action | Target |
|---|---|---|---|---|---|
| `TR-001` | `CLOSED` | `OPEN_EXPORT` | no active export session | create a fresh attempt; clear selections, acknowledgements and pendingOutcome | `SELECT_CONTENT` |
| `TR-002` | `CLOSED` | `STARTUP_SHARESHEET_CLEANUP_DELETE_NOW` | valid recovery context has destinationClass=SHARESHEET, pendingOutcome=COMPLETED and deleteNowConfirmedAfterWarning=true | restore context; retain pendingOutcome; set cleanupReason=DELETE_NOW_CONFIRMED; request temp cleanup | `CLEANUP_PENDING` |
| `TR-003` | `CLOSED` | `STARTUP_SHARESHEET_CLEANUP_SAFE_RELEASE` | valid recovery context has destinationClass=SHARESHEET, pendingOutcome=COMPLETED, deleteNowConfirmedAfterWarning=false and safeReleaseObserved=true | restore context; retain pendingOutcome; set cleanupReason=SAFE_RELEASE_OBSERVED; request temp cleanup | `CLEANUP_PENDING` |
| `TR-004` | `CLOSED` | `STARTUP_SHARESHEET_CLEANUP_EXPIRED` | valid recovery context has destinationClass=SHARESHEET, pendingOutcome=COMPLETED, deleteNowConfirmedAfterWarning=false, safeReleaseObserved=false and now>=effectiveExpiresAt | restore context; retain pendingOutcome; set cleanupReason=EXPIRED; request temp cleanup | `CLEANUP_PENDING` |
| `TR-005` | `CLOSED` | `STARTUP_SHARESHEET_RESTORE_UNEXPIRED` | valid recovery context has destinationClass=SHARESHEET, pendingOutcome=COMPLETED, deleteNowConfirmedAfterWarning=false, safeReleaseObserved=false and now<effectiveExpiresAt | restore content-free recovery context and Recent exports entry; retain temp; expose warned Delete now; claim no delivery | `HANDOFF_SUCCEEDED` |
| `TR-083` | `CLOSED` | `STARTUP_SAF_STAGING_CLEANUP_ELIGIBLE` | valid recovery context has destinationClass=SAF, pendingOutcome=COMPLETED and finalDestinationCloseObserved=true | restore context; retain pendingOutcome; set cleanupReason=SAF_CLOSE_OBSERVED; request staging cleanup | `CLEANUP_PENDING` |
| `TR-084` | `CLOSED` | `STARTUP_ABORTED_TEMP_CLEANUP_ELIGIBLE` | valid recovery context has pendingOutcome fixed as one member of {FAILED_RETRYABLE, CANCELLED} | restore context; retain pendingOutcome; set cleanupReason=NO_EXTERNAL_ACCESS_WINDOW; request temp cleanup | `CLEANUP_PENDING` |
| `TR-085` | `CLOSED` | `STARTUP_CONTEXT_INVALID_RETAIN` | recoveryContextStatus=INVALID and now<conservativeRetentionDeadline | reconstruct content-free context with destinationClass=SHARESHEET_CONSERVATIVE, pendingOutcome=CANCELLED and handoffEvidence=UNKNOWN; retain temp; expose warned Delete now; claim neither success nor delivery | `HANDOFF_SUCCEEDED` |
| `TR-086` | `CLOSED` | `STARTUP_CONTEXT_INVALID_RETENTION_EXPIRED` | recoveryContextStatus=INVALID and now>=conservativeRetentionDeadline | reconstruct content-free context with pendingOutcome=CANCELLED; set cleanupReason=CONSERVATIVE_RETENTION_EXPIRED; request temp cleanup | `CLEANUP_PENDING` |
| `TR-006` | `SELECT_CONTENT` | `CONTENT_SELECTION_ACCEPTED` | selection is non-empty, compatible and policy-allowed | store explicit content selection for this attempt | `SELECT_FORMAT` |
| `TR-007` | `SELECT_CONTENT` | `CONTENT_SELECTION_EMPTY` | selection contains zero content classes | show accessible empty-selection reason; create no artifact | `SELECT_CONTENT` |
| `TR-008` | `SELECT_CONTENT` | `CONTENT_SELECTION_POLICY_BLOCKED` | selected content is visibly restricted by managed policy | show policy reason; create no artifact; select no fallback | `SELECT_CONTENT` |
| `TR-009` | `SELECT_CONTENT` | `CANCEL_FROM_CONTENT` | no artifact exists | discard the attempt selection | `CANCELLED` |
| `TR-010` | `SELECT_FORMAT` | `FORMAT_SELECTION_ACCEPTED` | every selected content-format pair is compatible and policy-allowed | store explicit formats for this attempt | `SELECT_DESTINATION` |
| `TR-011` | `SELECT_FORMAT` | `FORMAT_SELECTION_INCOMPATIBLE` | a selected content-format pair is unsupported | disable the pair with an accessible reason; create no artifact | `SELECT_FORMAT` |
| `TR-012` | `SELECT_FORMAT` | `FORMAT_SELECTION_POLICY_BLOCKED` | a selected format is visibly restricted by managed policy | show policy reason; create no artifact; select no fallback | `SELECT_FORMAT` |
| `TR-013` | `SELECT_FORMAT` | `BACK_TO_CONTENT` | no artifact exists | retain current attempt values for editing | `SELECT_CONTENT` |
| `TR-014` | `SELECT_FORMAT` | `CANCEL_FROM_FORMAT` | no artifact exists | discard the attempt selection | `CANCELLED` |
| `TR-015` | `SELECT_DESTINATION` | `DESTINATION_CONFIRMED` | destination accepts the complete selected plan and policy allows it | store the explicit destination for this attempt | `REVIEW` |
| `TR-016` | `SELECT_DESTINATION` | `DESTINATION_INCOMPATIBLE` | destination cannot accept the complete selected plan | disable the destination with an accessible reason; create no artifact | `SELECT_DESTINATION` |
| `TR-017` | `SELECT_DESTINATION` | `DESTINATION_POLICY_BLOCKED` | destination is visibly restricted by managed policy | show policy reason; create no artifact; select no fallback | `SELECT_DESTINATION` |
| `TR-018` | `SELECT_DESTINATION` | `PICKER_CANCEL_RETURN` | pickerCancelPolicy=RETURN_TO_DESTINATION was fixed before picker launch | record cancellation; restore focus to destination control; create no artifact | `SELECT_DESTINATION` |
| `TR-019` | `SELECT_DESTINATION` | `PICKER_CANCEL_ATTEMPT` | pickerCancelPolicy=CANCEL_ATTEMPT was fixed before picker launch | record cancellation; discard the attempt; create no artifact | `CANCELLED` |
| `TR-020` | `SELECT_DESTINATION` | `BACK_TO_FORMAT` | no artifact exists | retain current attempt values for editing | `SELECT_FORMAT` |
| `TR-021` | `SELECT_DESTINATION` | `CANCEL_FROM_DESTINATION` | no artifact exists | discard the attempt selection | `CANCELLED` |
| `TR-022` | `REVIEW` | `REVIEW_CONFIRM_NO_AUDIO` | source, structured selection, formats, destination and policy revalidate; Audio is absent | freeze reviewed plan for this attempt | `UNENCRYPTED_WARNING` |
| `TR-023` | `REVIEW` | `REVIEW_CONFIRM_AUDIO` | source, selection, formats, destination and policy revalidate; Audio is present | freeze reviewed plan; require a fresh Audio acknowledgement | `AUDIO_ACKNOWLEDGEMENT` |
| `TR-024` | `REVIEW` | `REVIEW_SOURCE_INVALID` | source no longer exists at the reviewed version | invalidate reviewed plan; show source reason; create no artifact | `SELECT_CONTENT` |
| `TR-025` | `REVIEW` | `REVIEW_CONTENT_INVALID` | selected content no longer exists at the reviewed version | invalidate reviewed plan; show content reason; create no artifact | `SELECT_CONTENT` |
| `TR-026` | `REVIEW` | `REVIEW_FORMAT_INVALID` | formatInvalidReason is fixed before evaluation as one member of {INCOMPATIBLE, POLICY_BLOCKED} | invalidate reviewed plan; show the reason named by formatInvalidReason; create no artifact | `SELECT_FORMAT` |
| `TR-027` | `REVIEW` | `REVIEW_DESTINATION_INVALID` | destinationInvalidReason is fixed before evaluation as one member of {INCOMPATIBLE, POLICY_BLOCKED} | invalidate reviewed plan; show the reason named by destinationInvalidReason; create no artifact | `SELECT_DESTINATION` |
| `TR-028` | `REVIEW` | `BACK_TO_DESTINATION` | no artifact exists | unfreeze reviewed plan for editing | `SELECT_DESTINATION` |
| `TR-029` | `REVIEW` | `CANCEL_FROM_REVIEW` | no artifact exists | discard the reviewed attempt | `CANCELLED` |
| `TR-030` | `AUDIO_ACKNOWLEDGEMENT` | `AUDIO_ACK_ACCEPTED` | Audio remains selected; acknowledgement is fresh for this attempt | record attempt-scoped Audio acknowledgement | `UNENCRYPTED_WARNING` |
| `TR-031` | `AUDIO_ACKNOWLEDGEMENT` | `AUDIO_DECLINE_REMOVE` | user chooses the explicit Remove Audio action | remove Audio; invalidate Audio acknowledgement; return to review | `REVIEW` |
| `TR-032` | `AUDIO_ACKNOWLEDGEMENT` | `AUDIO_DECLINE_CANCEL` | user chooses the explicit Cancel export action | discard the attempt; record no acknowledgement | `CANCELLED` |
| `TR-033` | `UNENCRYPTED_WARNING` | `WARNING_ACCEPT_CLIPBOARD` | warning is fresh; destination=Clipboard; plan contains Copy/plain text only | record warning acknowledgement; create no file temp; invoke clipboard transfer | `TRANSFERRING` |
| `TR-034` | `UNENCRYPTED_WARNING` | `WARNING_ACCEPT_SAF_FILE` | warning is fresh; destination=SAF; plan contains policy-allowed file outputs; safWriteMode is fixed as one member of {DIRECT_DESTINATION, DORA_STAGING} | record warning acknowledgement; begin serialization under safWriteMode | `PREPARING` |
| `TR-035` | `UNENCRYPTED_WARNING` | `WARNING_ACCEPT_SHARESHEET_FILE` | warning is fresh; destination=Sharesheet; plan contains policy-allowed file outputs | record warning acknowledgement; create a Dora-managed temp copy | `PREPARING` |
| `TR-036` | `UNENCRYPTED_WARNING` | `WARNING_DECLINE_REVIEW` | user chooses the explicit Back to review action | record no acknowledgement; create no artifact | `REVIEW` |
| `TR-037` | `UNENCRYPTED_WARNING` | `WARNING_CANCEL_EXPORT` | user chooses the explicit Cancel export action | record no acknowledgement; discard the attempt | `CANCELLED` |
| `TR-038` | `PREPARING` | `PREPARATION_SUCCEEDED_SAF` | destination=SAF; every selected file output serialized; writer can start | seal the prepared bytes; retain staging metadata; begin SAF write | `TRANSFERRING` |
| `TR-039` | `PREPARING` | `PREPARATION_SUCCEEDED_SHARESHEET` | destination=Sharesheet; every selected file output serialized into Dora temp | seal the temp; register one-hour expiry; invoke Android Sharesheet | `TRANSFERRING` |
| `TR-040` | `PREPARING` | `PREPARATION_FAILED_NO_TEMP` | preparation failed before a Dora temp was created | discard partial memory; record content-free failure | `FAILED_RETRYABLE` |
| `TR-041` | `PREPARING` | `PREPARATION_FAILED_WITH_TEMP` | preparation failed after a Dora temp was created | stop writes; set pendingOutcome=FAILED_RETRYABLE; request temp cleanup | `CLEANUP_PENDING` |
| `TR-042` | `PREPARING` | `PREPARATION_CANCELLED_NO_TEMP` | cancellation became effective before a Dora temp was created | stop preparation; discard partial memory | `CANCELLED` |
| `TR-043` | `PREPARING` | `PREPARATION_CANCELLED_WITH_TEMP` | cancellation became effective after a Dora temp was created | stop writes; set pendingOutcome=CANCELLED; request temp cleanup | `CLEANUP_PENDING` |
| `TR-044` | `PREPARING` | `PREPARATION_TEMP_EXPIRED` | Dora temp reached its effective expiry during preparation | stop writes; set pendingOutcome=FAILED_RETRYABLE; request temp cleanup | `CLEANUP_PENDING` |
| `TR-045` | `TRANSFERRING` | `CLIPBOARD_API_ACCEPTED` | destination=Clipboard; Android clipboard API accepted the unchanged Dora clip | record clipboard success boundary; schedule independent best-effort clear | `HANDOFF_SUCCEEDED` |
| `TR-046` | `TRANSFERRING` | `CLIPBOARD_API_REJECTED` | destination=Clipboard; clipboard API did not accept the clip | record content-free failure; retain no payload | `FAILED_RETRYABLE` |
| `TR-047` | `TRANSFERRING` | `SAF_FINAL_CLOSE_SUCCEEDED_NO_STAGING` | destination=SAF; final write and close succeeded; no Dora staging exists | record SAF success boundary; claim no provider persistence | `HANDOFF_SUCCEEDED` |
| `TR-048` | `TRANSFERRING` | `SAF_FINAL_CLOSE_SUCCEEDED_WITH_STAGING` | destination=SAF; final write and close succeeded; Dora staging exists | set pendingOutcome=COMPLETED; persist finalDestinationCloseObserved=true; record SAF success boundary; mark staging cleanupEligible=true | `HANDOFF_SUCCEEDED` |
| `TR-049` | `TRANSFERRING` | `SAF_WRITE_FAILED_NO_STAGING` | destination=SAF; the final-close success boundary was not reached; no Dora staging exists | close writer best-effort; disclose provider partial outside Dora control; record failure | `FAILED_RETRYABLE` |
| `TR-050` | `TRANSFERRING` | `SAF_WRITE_FAILED_WITH_STAGING` | destination=SAF; the final-close success boundary was not reached; Dora staging exists | close writer best-effort; disclose provider partial outside Dora control; set pendingOutcome=FAILED_RETRYABLE | `CLEANUP_PENDING` |
| `TR-051` | `TRANSFERRING` | `SHARESHEET_HANDOFF_ACCEPTED` | destination=Sharesheet; Android accepted the share handoff; Dora temp exists | set pendingOutcome=COMPLETED; persist content-free recovery context; record handoff boundary; retain temp; claim no recipient delivery | `HANDOFF_SUCCEEDED` |
| `TR-052` | `TRANSFERRING` | `SHARESHEET_HANDOFF_REJECTED` | destination=Sharesheet; Android did not accept the share handoff; Dora temp exists | set pendingOutcome=FAILED_RETRYABLE; request temp cleanup | `CLEANUP_PENDING` |
| `TR-053` | `TRANSFERRING` | `TRANSFER_PICKER_CANCELLED_NO_TEMP` | system picker cancelled; no Dora temp exists | record cancellation; restore focus; claim no success | `CANCELLED` |
| `TR-054` | `TRANSFERRING` | `TRANSFER_PICKER_CANCELLED_WITH_TEMP` | system picker cancelled; Dora temp exists | record cancellation; restore focus; set pendingOutcome=CANCELLED | `CLEANUP_PENDING` |
| `TR-055` | `TRANSFERRING` | `TRANSFER_CANCELLED_NO_TEMP` | user cancellation became effective; no Dora temp exists | stop transfer when safe; claim no success | `CANCELLED` |
| `TR-056` | `TRANSFERRING` | `TRANSFER_CANCELLED_WITH_TEMP` | user cancellation became effective; Dora temp exists | stop transfer when safe; set pendingOutcome=CANCELLED; request temp cleanup | `CLEANUP_PENDING` |
| `TR-057` | `TRANSFERRING` | `TRANSFER_TEMP_EXPIRED` | Dora temp reached its effective expiry during transfer | stop transfer when safe; set pendingOutcome=FAILED_RETRYABLE; request temp cleanup | `CLEANUP_PENDING` |
| `TR-058` | `TRANSFERRING` | `PROGRESS_WITH_REAL_DENOMINATOR` | real numerator and denominator are both available | publish determinate progress from the real fraction | `TRANSFERRING` |
| `TR-059` | `TRANSFERRING` | `PROGRESS_WITHOUT_REAL_DENOMINATOR` | a real denominator is unavailable | publish stage-only progress; publish no percentage | `TRANSFERRING` |
| `TR-060` | `HANDOFF_SUCCEEDED` | `CLIPBOARD_SUCCESS_RECORDED` | destination=Clipboard; no Dora file temp exists | retain only content-free result metadata | `COMPLETED` |
| `TR-061` | `HANDOFF_SUCCEEDED` | `SAF_SUCCESS_RECORDED_NO_STAGING` | destination=SAF; final close succeeded; no Dora staging exists | retain only content-free result metadata | `COMPLETED` |
| `TR-062` | `HANDOFF_SUCCEEDED` | `SAF_STAGING_CLEANUP_ELIGIBLE` | destination=SAF; final close succeeded; Dora staging cleanupEligible=true | retain pendingOutcome=COMPLETED; request staging cleanup | `CLEANUP_PENDING` |
| `TR-063` | `HANDOFF_SUCCEEDED` | `SHARESHEET_SAFE_RELEASE_OBSERVED` | destinationClass is one member of {SHARESHEET, SHARESHEET_CONSERVATIVE}; exact lifecycle signal is observable and separately evidenced as safe release | retain pendingOutcome; persist safeReleaseObserved=true; request temp cleanup | `CLEANUP_PENDING` |
| `TR-064` | `HANDOFF_SUCCEEDED` | `SHARESHEET_DELETE_NOW` | destinationClass is one member of {SHARESHEET, SHARESHEET_CONSERVATIVE}; live Dora temp exists; user confirms access-loss warning | retain pendingOutcome; persist deleteNowConfirmedAfterWarning=true; request temp cleanup; leave external copies untouched | `CLEANUP_PENDING` |
| `TR-065` | `HANDOFF_SUCCEEDED` | `SHARESHEET_TEMP_EXPIRED` | destinationClass is one member of {SHARESHEET, SHARESHEET_CONSERVATIVE}; live Dora temp reached the recovery-context-selected active retention deadline | retain pendingOutcome; set cleanupReason=EXPIRED; request temp cleanup; leave external copies untouched | `CLEANUP_PENDING` |
| `TR-066` | `HANDOFF_SUCCEEDED` | `CLOSE_HANDOFF_VIEW_WITH_TEMP` | destination=Sharesheet; live Dora temp exists | close the view; retain state and content-free Recent exports entry | `HANDOFF_SUCCEEDED` |
| `TR-067` | `CLEANUP_PENDING` | `CLEANUP_SUCCEEDED_COMPLETED` | deletion succeeded; pendingOutcome=COMPLETED | remove Recent exports entry; clear cleanup metadata | `COMPLETED` |
| `TR-068` | `CLEANUP_PENDING` | `CLEANUP_SUCCEEDED_FAILED_RETRYABLE` | deletion succeeded; pendingOutcome=FAILED_RETRYABLE | remove Recent exports entry; clear cleanup metadata | `FAILED_RETRYABLE` |
| `TR-069` | `CLEANUP_PENDING` | `CLEANUP_SUCCEEDED_CANCELLED` | deletion succeeded; pendingOutcome=CANCELLED | remove Recent exports entry; clear cleanup metadata | `CANCELLED` |
| `TR-070` | `CLEANUP_PENDING` | `CLEANUP_DELETE_FAILED` | Dora temp deletion returned failure | retain pendingOutcome; persist content-free failure; expose retry | `CLEANUP_FAILED` |
| `TR-071` | `CLEANUP_PENDING` | `DISMISS_CLEANUP_PROGRESS` | cleanup has not reached a terminal result | close the view; retain state and content-free Recent exports entry | `CLEANUP_PENDING` |
| `TR-072` | `CLEANUP_FAILED` | `RETRY_CLEANUP` | failed Dora temp still exists | retain pendingOutcome; retry deletion only | `CLEANUP_PENDING` |
| `TR-073` | `CLEANUP_FAILED` | `DELETE_NOW_RETRY` | failed Dora temp still exists; user invokes Delete now | retain pendingOutcome; retry deletion only; leave source and external copies untouched | `CLEANUP_PENDING` |
| `TR-074` | `CLEANUP_FAILED` | `DISMISS_CLEANUP_FAILURE` | failed Dora temp still exists | close the view; retain failure state and visible content-free Recent exports entry | `CLEANUP_FAILED` |
| `TR-075` | `FAILED_RETRYABLE` | `RETRY_EXPORT_REVALIDATED` | source, selection, formats, destination and policy all revalidate | discard every partial output; create a fresh reviewed attempt; reuse no payload | `REVIEW` |
| `TR-076` | `FAILED_RETRYABLE` | `RETRY_SOURCE_INVALID` | source revalidation failed | discard every partial output; show source reason | `SELECT_CONTENT` |
| `TR-077` | `FAILED_RETRYABLE` | `RETRY_CONTENT_INVALID` | content revalidation failed | discard every partial output; show content reason | `SELECT_CONTENT` |
| `TR-078` | `FAILED_RETRYABLE` | `RETRY_FORMAT_INVALID` | format revalidation failed | discard every partial output; show format reason | `SELECT_FORMAT` |
| `TR-079` | `FAILED_RETRYABLE` | `RETRY_DESTINATION_INVALID` | retryDestinationInvalidReason is fixed before evaluation as one member of {INCOMPATIBLE, POLICY_BLOCKED} | discard every partial output; show the reason named by retryDestinationInvalidReason | `SELECT_DESTINATION` |
| `TR-080` | `FAILED_RETRYABLE` | `DISMISS_RETRYABLE_FAILURE` | no Dora temp and no cleanup failure remain | discard the failed attempt; retain no export history | `CLOSED` |
| `TR-081` | `COMPLETED` | `CLOSE_COMPLETED` | no Dora temp and no cleanup failure remain | clear attempt-only state; retain no export history | `CLOSED` |
| `TR-082` | `CANCELLED` | `CLOSE_CANCELLED` | no Dora temp and no cleanup failure remain | clear attempt-only state; retain no export history | `CLOSED` |

Transition evaluation is fail-closed:

- an event absent from this table cannot advance the export;
- guards are evaluated against the current attempt snapshot; a false guard leaves state unchanged
  and exposes a content-free reason;
- every file-output path accepts the warning, enters `PREPARING`, fires exactly one of
  `PREPARATION_SUCCEEDED_SAF` or `PREPARATION_SUCCEEDED_SHARESHEET`, and then reaches
  `TRANSFERRING`;
- `pendingOutcome` is content-free and has exactly one value: `COMPLETED`,
  `FAILED_RETRYABLE` or `CANCELLED`;
- a Sharesheet safe-release event is unavailable unless an exact observable lifecycle signal has
  separate evidence. Handoff alone never satisfies its guard.
- startup evaluates valid Sharesheet recovery evidence in this strict priority: (1) warned
  `Delete now` confirmation, (2) observed safe release, (3) expiry reached, (4) otherwise retain the
  unexpired temp. Each lower-priority guard negates every higher-priority predicate, so exactly one
  event/reason is selected even when evidence overlaps. Mere process restart or
  `pendingOutcome=COMPLETED` is never cleanup eligibility for an unexpired Sharesheet temp.

Startup overlap assertions are normative:

| Assertion ID | Inputs | Selected event | Cleanup reason | Suppressed events |
|---|---|---|---|---|
| `STARTUP-OVERLAP-001` | valid Sharesheet context; Delete-now=true; safe-release=true; expired=true | `STARTUP_SHARESHEET_CLEANUP_DELETE_NOW` | `DELETE_NOW_CONFIRMED` | `STARTUP_SHARESHEET_CLEANUP_SAFE_RELEASE`; `STARTUP_SHARESHEET_CLEANUP_EXPIRED`; `STARTUP_SHARESHEET_RESTORE_UNEXPIRED` |
| `STARTUP-OVERLAP-002` | valid Sharesheet context; Delete-now=false; safe-release=true; expired=true | `STARTUP_SHARESHEET_CLEANUP_SAFE_RELEASE` | `SAFE_RELEASE_OBSERVED` | `STARTUP_SHARESHEET_CLEANUP_EXPIRED`; `STARTUP_SHARESHEET_RESTORE_UNEXPIRED` |

Auxiliary lifecycle/evidence events do not change the 15-state interaction state:

| ID | Event | Precondition | Action | State effect |
|---|---|---|---|---|
| `AUX-001` | `CLIPBOARD_CLEAR_TIMER_UNCHANGED` | scheduled Dora clipboard check fires; clip identity and content are unchanged | attempt best-effort clipboard clear; disclose no guarantee | `NONE` |
| `AUX-002` | `CLIPBOARD_CLEAR_TIMER_CHANGED` | scheduled Dora clipboard check fires; clip comparison result=CHANGED | leave replacement clipboard content untouched | `NONE` |
| `AUX-003` | `DIAGNOSTIC_SNAPSHOT_REQUESTED` | current state/error requires diagnostic evidence | emit bounded categorical codes and numeric counters only | `NONE` |
| `AUX-004` | `ACCESSIBILITY_FIXTURE_RENDERED` | synthetic RU, EN, scale, input and motion fixture is selected | render semantics, focus, reflow and action reachability without user data | `NONE` |


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
- Logs, diagnostics, state persistence and Recent exports contain no payload, user content, title,
  path, URI, key or destination credential. The internal recovery context may contain only the
  content-free fields enumerated in §6.1.

## 6. Temporary-artifact lifecycle

1. Create a Dora-managed temp only after Review, separate Audio acknowledgement when applicable,
   and the mandatory unencrypted warning.
2. Clipboard creates no Dora-managed file temp. Clipboard success and the independent 60-second
   best-effort clear therefore never enter file cleanup.
3. SAF may serialize directly to the chosen destination or use Dora staging. A successful final
   destination write and close is the only success boundary. On success, staging becomes
   cleanup-eligible only after that close is observed. On cancel/failure, stop and close the writer
   before cleaning Dora staging. A partial provider copy is outside Dora control and must never be
   reported as cleaned by Dora.
4. Sharesheet uses a Dora-managed temp copy and never grants the canonical source. Android handoff
   is not evidence that a recipient read, saved, synced or delivered it. The temp remains live after
   handoff until an exact OS/provider lifecycle signal is both observable and separately evidenced
   as safe release. If no such signal exists, automatic post-handoff cleanup does not fire; cleanup
   occurs only through warned `Delete now` or effective expiry. Restart alone never makes the temp
   cleanup-eligible.
5. `Delete now` warns that pending external access to a Sharesheet temp may stop. It deletes only
   the Dora temp, never a SAF/provider copy or canonical source.
6. A Dora-managed temp has a hard maximum lifetime of one hour from creation. Managed policy may
   shorten, never extend, this limit.
7. On startup, inspect only the exact allowlisted Dora temp area. Restore an unexpired Sharesheet
   temp to governed retention unless the recovery context proves observable safe release, warned
   `Delete now` or expiry. SAF staging is cleanup-eligible only when successful destination close is
   persisted. Failed/cancelled pre-handoff temps can resume cleanup without an external access
   window. Unknown files are not export artifacts and are not deleted by this contract.
8. Missing/corrupt recovery context never triggers immediate deletion while the artifact could be
   an unexpired Sharesheet handoff. On first observation, persist
   `conservativeRetentionDeadline` as the earliest valid value among recovered
   `effectiveExpiresAt`, recovered `createdAt + one hour`, and `firstObservedAt + one hour`; invalid
   candidates are ignored. If no earlier trustworthy value exists, use `firstObservedAt + one
   hour`. This degraded rule is bounded, never indefinite, and is recorded as a conformance failure
   because the original creation-based maximum cannot be proved. Until that deadline, retain the
   temp with `handoffEvidence=UNKNOWN` and no success claim; then clean with
   `pendingOutcome=CANCELLED`. Warned `Delete now` or separately evidenced safe release may trigger
   that cleanup earlier without changing the fail-closed outcome.
9. Failed cleanup remains visible and retryable until resolved. It is not renamed `complete`.
10. SAF and other external-destination copies are outside Dora expiry and cannot be deleted by
   `Delete now` or startup cleanup.
11. Cleanup deletes only Dora-managed temporary outputs. Source Conversation, Audio, Summary,
    Protocol, Transcript and Tasks are never cleanup targets.
12. Logical/best-effort deletion wording is used. Physical overwrite is never claimed.

### 6.1. Content-free recovery context

Persist atomically with every Dora-managed temp, before handoff, only:

- opaque transfer ID and `destinationClass` from
  `{SAF, SHARESHEET, SHARESHEET_CONSERVATIVE}`;
- `pendingOutcome`, `createdAt`, `effectiveExpiresAt` and, only for degraded recovery,
  `firstObservedAt` plus `conservativeRetentionDeadline`;
- `safeReleaseObserved`, `deleteNowConfirmedAfterWarning`, `finalDestinationCloseObserved` and
  `handoffEvidence` from `{ANDROID_ACCEPTED, UNKNOWN}`, plus `recoveryContextStatus` from
  `{VALID, INVALID}`.

These fields decide retention/cleanup but are not an export history. They contain no title,
participant, content, excerpt, payload, format payload, path, URI, provider/destination identity,
credential or key. A destination class is a bounded enum, not an external destination identity.
Before an external handoff succeeds, initialize `pendingOutcome=CANCELLED` fail-closed; replace it
with `COMPLETED` only when the Sharesheet handoff or SAF final-close boundary is observed, and with
`FAILED_RETRYABLE` on a retryable failure.

### 6.2. Recent exports

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

| Fixture ID | Scenario | Inputs | Preconditions | Events | Expected states and outcome | Invariants |
|---|---|---|---|---|---|---|
| `EXP-SYN-001` | Fresh open | none | state=CLOSED; no active export session | `OPEN_EXPORT` | `CLOSED` → `SELECT_CONTENT`; fresh empty attempt | no selection; no acknowledgement; no artifact |
| `EXP-SYN-002` | Empty content then Continue | content=[] | state=SELECT_CONTENT | `CONTENT_SELECTION_EMPTY` | `SELECT_CONTENT` → `SELECT_CONTENT`; accessible empty-selection reason | no auto-selection; no payload; no temp |
| `EXP-SYN-003` | Summary plus Copy plus Clipboard | Summary; Copy/plain text; Clipboard | state=CLOSED; policy allows plan | `OPEN_EXPORT`; `CONTENT_SELECTION_ACCEPTED`; `FORMAT_SELECTION_ACCEPTED`; `DESTINATION_CONFIRMED`; `REVIEW_CONFIRM_NO_AUDIO`; `WARNING_ACCEPT_CLIPBOARD`; `CLIPBOARD_API_ACCEPTED`; `CLIPBOARD_SUCCESS_RECORDED` | `CLOSED` → `SELECT_CONTENT` → `SELECT_FORMAT` → `SELECT_DESTINATION` → `REVIEW` → `UNENCRYPTED_WARNING` → `TRANSFERRING` → `HANDOFF_SUCCEEDED` → `COMPLETED`; clipboard API accepted | exact warning shown; no file temp; recipient read not claimed |
| `EXP-SYN-004` | Protocol and Transcript plus Markdown plus Sharesheet | Protocol; Transcript; Markdown; Sharesheet | state=CLOSED; policy allows plan | `OPEN_EXPORT`; `CONTENT_SELECTION_ACCEPTED`; `FORMAT_SELECTION_ACCEPTED`; `DESTINATION_CONFIRMED`; `REVIEW_CONFIRM_NO_AUDIO`; `WARNING_ACCEPT_SHARESHEET_FILE`; `PREPARATION_SUCCEEDED_SHARESHEET`; `SHARESHEET_HANDOFF_ACCEPTED` | `CLOSED` → `SELECT_CONTENT` → `SELECT_FORMAT` → `SELECT_DESTINATION` → `REVIEW` → `UNENCRYPTED_WARNING` → `PREPARING` → `TRANSFERRING` → `HANDOFF_SUCCEEDED`; Android accepted handoff; live Dora temp retained | warning precedes temp; handoff is not delivery; source not granted |
| `EXP-SYN-005` | Four structured classes plus JSON v1 plus SAF | Summary; Protocol; Transcript; Tasks; JSON v1; SAF | state=CLOSED; policy allows plan; SAF direct write selected | `OPEN_EXPORT`; `CONTENT_SELECTION_ACCEPTED`; `FORMAT_SELECTION_ACCEPTED`; `DESTINATION_CONFIRMED`; `REVIEW_CONFIRM_NO_AUDIO`; `WARNING_ACCEPT_SAF_FILE`; `PREPARATION_SUCCEEDED_SAF`; `SAF_FINAL_CLOSE_SUCCEEDED_NO_STAGING`; `SAF_SUCCESS_RECORDED_NO_STAGING` | `CLOSED` → `SELECT_CONTENT` → `SELECT_FORMAT` → `SELECT_DESTINATION` → `REVIEW` → `UNENCRYPTED_WARNING` → `PREPARING` → `TRANSFERRING` → `HANDOFF_SUCCEEDED` → `COMPLETED`; SAF final write and close completed | JSON version visible; raw audio excluded; path URI and key excluded |
| `EXP-SYN-006` | Summary plus CSV | Summary; CSV | state=SELECT_FORMAT; Summary selected | `FORMAT_SELECTION_INCOMPATIBLE` | `SELECT_FORMAT` → `SELECT_FORMAT`; CSV pair disabled with reason | Tasks-only CSV; no artifact |
| `EXP-SYN-007` | Tasks plus CSV plus SAF with staging | Tasks; CSV; SAF; Dora staging; effective temp expiry=15 minutes | state=CLOSED; policy allows plan; managed policy shortens temp expiry to 15 minutes | `OPEN_EXPORT`; `CONTENT_SELECTION_ACCEPTED`; `FORMAT_SELECTION_ACCEPTED`; `DESTINATION_CONFIRMED`; `REVIEW_CONFIRM_NO_AUDIO`; `WARNING_ACCEPT_SAF_FILE`; `PREPARATION_SUCCEEDED_SAF`; `SAF_FINAL_CLOSE_SUCCEEDED_WITH_STAGING`; `SAF_STAGING_CLEANUP_ELIGIBLE`; `CLEANUP_SUCCEEDED_COMPLETED` | `CLOSED` → `SELECT_CONTENT` → `SELECT_FORMAT` → `SELECT_DESTINATION` → `REVIEW` → `UNENCRYPTED_WARNING` → `PREPARING` → `TRANSFERRING` → `HANDOFF_SUCCEEDED` → `CLEANUP_PENDING` → `COMPLETED`; Review displays the policy-shortened 15-minute expiry; destination closed before Dora staging cleanup | policy expiry visible at Review; policy cannot extend expiry; no cleanup eligibility before close; external copy outside Dora expiry; source untouched |
| `EXP-SYN-008` | Select all | Select all | state=SELECT_CONTENT; all structured classes available | `CONTENT_SELECTION_ACCEPTED` | `SELECT_CONTENT` → `SELECT_FORMAT`; Summary Protocol Transcript and Tasks selected | Audio remains off; no remembered state |
| `EXP-SYN-009` | Audio WAV/PCM plus Sharesheet | Audio; WAV/PCM; Sharesheet | state=CLOSED; policy allows Audio | `OPEN_EXPORT`; `CONTENT_SELECTION_ACCEPTED`; `FORMAT_SELECTION_ACCEPTED`; `DESTINATION_CONFIRMED`; `REVIEW_CONFIRM_AUDIO`; `AUDIO_ACK_ACCEPTED`; `WARNING_ACCEPT_SHARESHEET_FILE`; `PREPARATION_SUCCEEDED_SHARESHEET`; `SHARESHEET_HANDOFF_ACCEPTED` | `CLOSED` → `SELECT_CONTENT` → `SELECT_FORMAT` → `SELECT_DESTINATION` → `REVIEW` → `AUDIO_ACKNOWLEDGEMENT` → `UNENCRYPTED_WARNING` → `PREPARING` → `TRANSFERRING` → `HANDOFF_SUCCEEDED`; one-shot Audio handed to Android; live temp retained | Audio acknowledgement fresh; warning separate and fresh; Audio not selected by Select all |
| `EXP-SYN-010` | Encrypted bundle | Encrypted bundle | state=SELECT_FORMAT | `FORMAT_SELECTION_INCOMPATIBLE` | `SELECT_FORMAT` → `SELECT_FORMAT`; deferred not-MVP reason shown | no artifact; no Security approval claim; separate key and recovery decision required |
| `EXP-SYN-011` | Managed policy disables Audio | Audio; managed policy restriction | state=SELECT_CONTENT; restriction visible | `CONTENT_SELECTION_POLICY_BLOCKED` | `SELECT_CONTENT` → `SELECT_CONTENT`; Audio remains disabled with policy reason | policy selects no fallback; policy initiates nothing; no artifact |
| `EXP-SYN-012` | Restart before managed-policy expiry then expire | valid Sharesheet recovery context; effective temp expiry=15 minutes; now=10 minutes | state=CLOSED after restart; pendingOutcome=COMPLETED; safeReleaseObserved=false; deleteNowConfirmedAfterWarning=false | `STARTUP_SHARESHEET_RESTORE_UNEXPIRED`; `SHARESHEET_TEMP_EXPIRED`; `CLEANUP_SUCCEEDED_COMPLETED` | `CLOSED` → `HANDOFF_SUCCEEDED` → `CLEANUP_PENDING` → `COMPLETED`; restart retains access window, then effective expiry cleans temp | no cleanup on restart; below one-hour maximum; policy cannot extend expiry; no delivery claim |
| `EXP-SYN-013` | External provider may use network | SAF external provider | state=REVIEW; selected file plan valid | none | `REVIEW`; external-control and independent-network disclosure shown | Dora account not required; GMS not required; Dora network not required |
| `EXP-SYN-014` | Destination picker cancelled before temp | pickerCancelPolicy=RETURN_TO_DESTINATION | state=SELECT_DESTINATION; no artifact | `PICKER_CANCEL_RETURN` | `SELECT_DESTINATION` → `SELECT_DESTINATION`; focus restored and cancellation announced | no success claim; no artifact; policy fixed before launch |
| `EXP-SYN-015` | Transfer picker cancelled after temp exists | Sharesheet; live Dora temp | state=TRANSFERRING | `TRANSFER_PICKER_CANCELLED_WITH_TEMP`; `CLEANUP_SUCCEEDED_CANCELLED` | `TRANSFERRING` → `CLEANUP_PENDING` → `CANCELLED`; cancel converges through cleanup | pendingOutcome=CANCELLED; no delivery claim; source untouched |
| `EXP-SYN-016` | Cancel during PREPARING | live Dora temp | state=PREPARING | `PREPARATION_CANCELLED_WITH_TEMP`; `CLEANUP_SUCCEEDED_CANCELLED` | `PREPARING` → `CLEANUP_PENDING` → `CANCELLED`; partial temp deleted | no partial reuse; no background resume; source untouched |
| `EXP-SYN-017` | Cancel during TRANSFERRING | live Dora temp | state=TRANSFERRING | `TRANSFER_CANCELLED_WITH_TEMP`; `CLEANUP_SUCCEEDED_CANCELLED` | `TRANSFERRING` → `CLEANUP_PENDING` → `CANCELLED`; transfer stops safely then temp is deleted | no partial reuse; no background resume; source untouched |
| `EXP-SYN-018` | SAF write fails before close | SAF; Dora staging; provider partial possible | state=TRANSFERRING; final close not observed | `SAF_WRITE_FAILED_WITH_STAGING`; `CLEANUP_SUCCEEDED_FAILED_RETRYABLE` | `TRANSFERRING` → `CLEANUP_PENDING` → `FAILED_RETRYABLE`; Dora staging cleaned; export retry available | provider partial outside Dora control; no success claim; retry must revalidate |
| `EXP-SYN-019` | Sharesheet accepts handoff | Sharesheet; live Dora temp | state=TRANSFERRING | `SHARESHEET_HANDOFF_ACCEPTED` | `TRANSFERRING` → `HANDOFF_SUCCEEDED`; Android handoff boundary recorded; temp retained | recipient read not claimed; recipient save not claimed; recipient delivery not claimed |
| `EXP-SYN-020` | Clipboard clear timer fires with unchanged clip | unchanged Dora clip; elapsed=60 seconds | clipboard API previously accepted clip | `CLIPBOARD_CLEAR_TIMER_UNCHANGED` | `COMPLETED`; best-effort clear attempted without state change | clear not guaranteed; clip content not logged |
| `EXP-SYN-021` | Clipboard changed before clear timer | replacement clip; elapsed=60 seconds | clipboard API previously accepted Dora clip | `CLIPBOARD_CLEAR_TIMER_CHANGED` | `COMPLETED`; replacement clip left untouched | no clear attempt on changed clip; replacement content not logged |
| `EXP-SYN-022` | Delete now on live Sharesheet temp | live Dora temp; user confirms access-loss warning | state=HANDOFF_SUCCEEDED; destination=Sharesheet | `SHARESHEET_DELETE_NOW`; `CLEANUP_SUCCEEDED_COMPLETED` | `HANDOFF_SUCCEEDED` → `CLEANUP_PENDING` → `COMPLETED`; Dora temp deleted and Recent exports entry removed | external copies untouched; canonical source untouched; no physical overwrite claim |
| `EXP-SYN-023` | Sharesheet temp reaches effective expiry | live Dora temp; effective expiry reached | state=HANDOFF_SUCCEEDED; destination=Sharesheet | `SHARESHEET_TEMP_EXPIRED`; `CLEANUP_SUCCEEDED_COMPLETED` | `HANDOFF_SUCCEEDED` → `CLEANUP_PENDING` → `COMPLETED`; expired Dora temp deleted | hard maximum one hour; external copies unaffected; source untouched |
| `EXP-SYN-024` | Missing recovery context across restart | allowlisted Dora temp; recovery context missing; now before conservativeRetentionDeadline | state=CLOSED after restart; artifact could be an unexpired Sharesheet handoff | `STARTUP_CONTEXT_INVALID_RETAIN`; `SHARESHEET_TEMP_EXPIRED`; `CLEANUP_SUCCEEDED_CANCELLED`; `CLOSE_CANCELLED` | `CLOSED` → `HANDOFF_SUCCEEDED` → `CLEANUP_PENDING` → `CANCELLED` → `CLOSED`; temp is retained first, then bounded conservative expiry cleans without manufacturing success | no immediate deletion; handoff unverified; warned Delete now remains available; unknown files and source untouched |
| `EXP-SYN-025` | Cleanup deletion fails | live Dora temp; delete returns failure | state=CLEANUP_PENDING; pendingOutcome retained | `CLEANUP_DELETE_FAILED` | `CLEANUP_PENDING` → `CLEANUP_FAILED`; content-free cleanup failure remains visible | retry available; completion not claimed; source untouched |
| `EXP-SYN-026` | Cleanup retry succeeds | failed Dora temp; pendingOutcome=COMPLETED | state=CLEANUP_FAILED | `RETRY_CLEANUP`; `CLEANUP_SUCCEEDED_COMPLETED` | `CLEANUP_FAILED` → `CLEANUP_PENDING` → `COMPLETED`; retained outcome reached after deletion | export not repeated; Recent exports entry removed; source untouched |
| `EXP-SYN-027` | Retry after transfer failure | previous transfer failure | state=FAILED_RETRYABLE; source selection and policy still valid | `RETRY_EXPORT_REVALIDATED` | `FAILED_RETRYABLE` → `REVIEW`; fresh reviewed attempt | partial output discarded; no background resume; no payload reuse |
| `EXP-SYN-028` | Known and unknown transfer progress | known byte total; then unavailable denominator | state=TRANSFERRING | `PROGRESS_WITH_REAL_DENOMINATOR`; `PROGRESS_WITHOUT_REAL_DENOMINATOR` | `TRANSFERRING` → `TRANSFERRING` → `TRANSFERRING`; real fraction then stage-only progress | no fabricated percentage; no provider sync progress |
| `EXP-SYN-029` | Diagnostics across every state and error | all 15 states; representative categorical errors | isolated synthetic state snapshots | `DIAGNOSTIC_SNAPSHOT_REQUESTED` | `CLOSED` → `SELECT_CONTENT` → `SELECT_FORMAT` → `SELECT_DESTINATION` → `REVIEW` → `AUDIO_ACKNOWLEDGEMENT` → `UNENCRYPTED_WARNING` → `PREPARING` → `TRANSFERRING` → `HANDOFF_SUCCEEDED` → `CLEANUP_PENDING` → `COMPLETED` → `FAILED_RETRYABLE` → `CANCELLED` → `CLEANUP_FAILED`; state remains unchanged for every snapshot | no path URI key payload user content or destination credential |
| `EXP-SYN-030` | 200 percent RU EN accessibility matrix | RU; EN; 200 percent text; TalkBack; Switch; keyboard; reduced motion | synthetic content only | `ACCESSIBILITY_FIXTURE_RENDERED` | `SELECT_CONTENT` → `SELECT_FORMAT` → `SELECT_DESTINATION` → `REVIEW` → `AUDIO_ACKNOWLEDGEMENT` → `UNENCRYPTED_WARNING` → `CLEANUP_FAILED`; critical text actions reasons cleanup and retry remain reachable | focus deterministic; no clipping; status not color-only; no user research claim |

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
