# Dora MVP 1 — Storage, Retention and Delete Contract

Status: reusable design/governance contract for `DES-STORAGE-001`\
Contract identity: `des-storage-retention-delete-v0.1`\
Version: 0.1\
Date: 15 August 2026\
Source snapshot: `main` `4cba244c614dd258a1d228d5775de653b3df8df0`, tree
`b570821171a0bd0e80688f2197332541fdc971fc`\
Decision boundary: `DEC-013` is **Approved only for this versioned reversible design/governance contract**

## 1. Purpose, authority and non-claims

This contract defines the reusable scope, interaction, privacy, restart/retry, accessibility and
synthetic acceptance baseline for future Dora storage, audio-retention and deletion flows. It is
subordinate to the Technical Plan, Design Spec, Product Decisions, accepted ADRs and Test Strategy.
An authoritative conflict blocks only the affected behavior until a DEC or ADR resolves it.

The Project owner delegated ordinary reversible project/product decisions to the OpenAI Codex
project coordinator so the workflow does not stop. The exact wording and bounded interpretation
are recorded in
[the machine-readable decision record](../evidence/des-storage-001/decision-record-v0.1.json).
The coordinator is the decision executor/reviewer with `formalReviewer=false`.

Completing this contract means only `CONTRACT_COMPLETE`. It does not implement or approve Android
code, Figma, resources, schema, database/filesystem/Keystore behavior, WorkManager, provider or
backend wiring, retention/deletion execution, accessibility conformance, user research, Legal or
Security approval, dependency or production admission, real-data collection, Recovery/device/
measured execution or merge. It represents no human, Legal or Security signature.

## 2. Authoritative traceability

| Source | Requirement carried into this contract |
|---|---|
| Technical Plan §§14.3, 22, 24, 26–28, 34–35 and P13 | local source durability; automatic-cleanup guard; scoped logical/crypto deletion; separate cloud receipt; source/FTS/jobs topology; no physical-overwrite claim |
| Design Spec §§14, 18, 23, 28.4, 31–35 and `ST-05`/`DL-02` | storage breakdown; audio-only loss-of-source warning; explicit whole-conversation scope; local/remote state separation; truthful errors, focus and accessibility |
| `DEC-009`, `DEC-012`–`DEC-015`, `DEC-017`, `DEC-038`–`DEC-040` | local-first defaults; cloud separately gated; local retention decision; no training; export-temp boundary; privacy/accessibility constraints |
| Test Strategy `TS-STORAGE`, `TS-PRIVACY`, `TS-ACCESSIBILITY`, `TS-DB-MIGRATION` | deterministic cleanup/reconciliation, content-free evidence, restart/partial-failure proof and accessible critical flows |
| Privacy Threat Model §§2–5, 10–13 | no silent deletion/outbound flow; deletion graph; logical/crypto wording; content-free evidence; managed policy may restrict but not initiate |
| Dataset Governance §§2, 9–14 | synthetic fixtures only; no real meetings/training/public sensitive evidence |
| Export Interaction Contract | Dora-managed export temp is non-canonical; safe-release, warned Delete-now, one-hour maximum and startup recovery remain independent |
| Component Accessibility Contract | names/roles/states, modal focus, 48-dp targets, 200% reflow, input alternatives, focus restoration and fail-closed evidence |

## 3. Normative principles and defaults

1. Local audio is retained until explicit deletion by default.
2. Automatic retention is `OFF`, never preselected, remembered, silent or enabled by managed
   policy. It can govern Audio only, never structured content or a whole Conversation.
3. This contract approves no numeric automatic-retention period. The versioned allowed-period
   catalog is `UNAPPROVED` with zero entries. The control is visibly unavailable until a future
   decision approves that catalog.
4. A future catalog selection needs an impact review and explicit confirmation. Shortening needs a
   new explicit confirmation; already-overdue Audio becomes eligible only after that confirmation
   and all automatic-cleanup guards.
5. Automatic cleanup requires a durable, internally validated local result. External export alone
   and cloud success alone do not qualify. Age is anchored to durable finalization. Cleanup cannot
   run while recording is active or before source-job reconciliation.
6. The result/export guard applies only to automatic retention. Explicit audio-only and
   whole-conversation deletion are available without a result or export after scoped confirmation.
7. Cancellation before confirmation mutates nothing. After confirmation creates a durable
   operation and irreversible work begins, closing the UI is not cancellation and no undo is
   promised.
8. Physical flash overwrite is never claimed. Cryptographic erasure is claimed only when exact
   per-artifact key granularity and deletion are verified; otherwise wording is scoped best-effort
   cleanup with visible partial failure and retry.
9. Local and remote deletion are independent. Local success never means remote success.
10. Logs, analytics, notifications, diagnostics and public evidence are content-free. They contain
    no conversation title, transcript, participant, path, URI, object key, provider credential,
    cryptographic key or payload.

## 4. Operation scope matrix

The seven rows below are the complete operation classes for this contract. A future implementation
may split internal steps but may not broaden a row silently.

| ID | Operation | Trigger and mandatory guard | Local targets | Preserved / external boundary | Completion boundary |
|---|---|---|---|---|---|
| `OP-AUTO-AUDIO` | Automatic retention cleanup | Future approved period; explicit opt-in; age from durable finalization; durable internally validated local result; recording inactive; source jobs reconciled | eligible Audio, verifiably per-audio key/material, audio-only cache | all structured content and FTS; external exports; cloud state | every scoped local target verified absent or crypto-erased; otherwise partial failure |
| `OP-EXPLICIT-AUDIO` | Explicit audio-only deletion | enhanced warning and explicit confirmation; no result/export prerequisite | selected local Audio, verifiably per-audio key/material, audio-only cache; cancel/reconcile source jobs and queued uploads | transcript, protocol, decisions, tasks, summary, FTS and source links remain with exact unavailable reason; external copies remain | scoped Audio inventory reconciled; retained content receives exact reason; partial failure stays visible |
| `OP-EXPLICIT-CONVERSATION` | Explicit whole-conversation deletion | scoped warning and explicit confirmation; no result/export prerequisite | local canonical Audio and structured content, derivatives, FTS, caches; tombstone; jobs cancelled; queued uploads revoked | minimum content-free operation evidence; external copies remain; remote deletion separate | every scoped local category reconciled; partial failure stays visible |
| `OP-ACTIVE-STOP-DELETE` | Stop active recording and delete/discard captured local data | separate accessible action and confirmation while capture remains visibly active | stop capture, reconcile/finalize only as needed for safe deletion, then apply explicit scoped deletion | cancel before confirmation changes nothing; external copies and remote state remain separate | capture stopped and selected local scope reconciled; UI close after confirmation is not cancel |
| `OP-EXPORT-TEMP` | Delete Dora-managed export temp | `DEC-017` safe release, warned Delete-now, expiry or startup rule | only the Dora-managed temporary export | canonical Conversation/Audio/content and external copies remain; temp never blocks conversation deletion | governed temp absent; cleanup failure remains visible/retryable |
| `OP-UNUSED-MODEL` | Delete unused model | explicit action; model not active, selected or in use by a running/retryable job | selected model artifact and model-only cache | conversations, Audio, structured data and safety reserve remain | model inventory reconciled; in-use model is blocked without mutation |
| `OP-STORAGE-GUIDANCE` | Soft/hard storage guidance | observed storage pressure | no automatic target; route only to an explicitly selected compatible operation | reserved safety space is never a cleanup target; no content is selected implicitly | guidance dismissed or explicit operation opened; observation alone never deletes |

### 4.1. Explicit audio-only topology

After confirmation, the durable operation performs idempotent steps in this order:

1. prevent new processing/upload work for the selected Audio;
2. cancel or reconcile local source jobs and revoke/dequeue unsent uploads;
3. classify any already-remote scope only in the separate remote state machine;
4. delete verifiably per-audio key/material when that granularity is proved, otherwise make no
   crypto-erasure claim;
5. delete local Audio files and audio-only caches with bounded allowlists;
6. retain structured content and FTS, preserving source links with one exact source-unavailable
   reason;
7. verify the scoped inventory and retain only content-free operation evidence.

### 4.2. Whole-conversation topology

After confirmation, the durable operation tombstones the Conversation, blocks new work, cancels
local jobs, revokes/dequeues unsent uploads, deletes local canonical Audio, structured content,
derivatives, FTS and scoped caches, and verifies each category. Remote deletion is not inferred or
started unless its separate gate permits a request. External export/provider copies are never
claimed deleted.

### 4.3. Source-unavailable reasons

The retained source link after audio-only loss uses exactly one reason:

| Reason | RU label | EN label |
|---|---|---|
| `USER_DELETED` | `Аудио удалено вами` | `Audio deleted by you` |
| `RETENTION_DELETED` | `Аудио удалено по настроенному сроку хранения` | `Audio deleted by the configured retention period` |
| `MISSING` | `Аудиофайл отсутствует` | `Audio file is missing` |
| `CORRUPT` | `Аудиофайл повреждён` | `Audio file is corrupt` |
| `KEY_UNAVAILABLE` | `Ключ аудио недоступен` | `Audio key is unavailable` |

`MISSING`, `CORRUPT` and `KEY_UNAVAILABLE` are not deletion-success labels. Whole-conversation
deletion removes the structured source link in scope rather than manufacturing one of these
audio-only reasons.

## 5. Exact RU/EN non-Legal copy

These strings define product meaning, not final Legal wording. They must not be shortened so that
scope, external-copy or physical-overwrite limits disappear. `{periodLabel}` may only come from a
future approved allowed-period catalog and has no value in version 0.1.

| Copy ID | RU exact | EN exact |
|---|---|---|
| `COPY-AUDIO-DELETE` | `Удалить только аудио этого разговора? Расшифровка, протокол, решения, задачи, резюме и поиск останутся, но Dora больше не сможет открыть или повторно проверить их по записи. Удаление может завершиться частично; физическая перезапись памяти не гарантируется.` | `Delete only this conversation’s audio? The transcript, protocol, decisions, tasks, summary, and search entries will remain, but Dora will no longer be able to open or re-check them against the recording. Deletion may complete only partially; physical storage overwrite is not guaranteed.` |
| `COPY-CONVERSATION-DELETE` | `Удалить весь разговор с этого устройства? Dora удалит локальное аудио, расшифровку, протокол, решения, задачи, резюме, производные данные и поисковый индекс, отменит локальную обработку и отзовёт ожидающие отправки. Внешние экспортированные копии не удалятся. Удаление в облаке, если оно доступно, отслеживается отдельно. Физическая перезапись памяти не гарантируется.` | `Delete the whole conversation from this device? Dora will delete the local audio, transcript, protocol, decisions, tasks, summary, derived data, and search index, cancel local processing, and revoke queued sends. External exported copies will not be deleted. Cloud deletion, when available, is tracked separately. Physical storage overwrite is not guaranteed.` |
| `COPY-ACTIVE-STOP-DELETE` | `Остановить запись и удалить уже записанное? До подтверждения запись продолжается и ничего не удаляется. После подтверждения Dora остановит запись и начнёт удаление; закрытие экрана не отменит операцию, отмены после начала удаления нет.` | `Stop recording and delete what has already been captured? Until you confirm, recording continues and nothing is deleted. After confirmation, Dora will stop recording and begin deletion; closing the screen will not cancel the operation, and there is no undo after deletion starts.` |
| `COPY-RETENTION-OPT-IN` | `Включить автоудаление аудио через {periodLabel}? Автоудаление касается только аудио и сработает лишь после подтверждённого локального результата и завершения связанных задач. Расшифровка и другие материалы разговора останутся без аудиоисточника.` | `Turn on automatic audio deletion after {periodLabel}? Automatic deletion applies only to audio and runs only after a validated local result and related jobs are complete. The transcript and other conversation materials will remain without the audio source.` |
| `COPY-RETENTION-SHORTEN` | `Сократить срок хранения аудио до {periodLabel}? Аудио, уже вышедшее за новый срок, станет доступно для автоудаления только после этого подтверждения и проверки всех условий.` | `Shorten audio retention to {periodLabel}? Audio already older than the new period becomes eligible for automatic deletion only after this confirmation and all required checks.` |
| `COPY-EXPORT-TEMP-DELETE` | `Удалить временный файл Dora сейчас? Приложение, которому уже передан доступ, может перестать открывать файл. Копии вне Dora не удалятся, исходный разговор не изменится.` | `Delete Dora’s temporary file now? An app that was already given access may stop being able to open it. Copies outside Dora will not be deleted, and the source conversation will not change.` |
| `COPY-REMOTE-PENDING` | `Локальные данные удалены. Удаление в облаке ещё не подтверждено.` | `Local data has been deleted. Cloud deletion has not yet been confirmed.` |
| `COPY-PARTIAL-FAILURE` | `Удаление завершилось не полностью. Сохранённые данные и незавершённые шаги перечислены отдельно. Можно повторить только оставшиеся шаги.` | `Deletion did not complete fully. Preserved data and unfinished steps are listed separately. You can retry only the remaining steps.` |

## 6. Orthogonal state model

Retention policy, local deletion operation, storage-pressure cleanup and remote deletion are four
independent state machines. A UI projection may show them together, but one axis never overwrites
another. The tuple `(machine, source, event)` is deterministic; an absent event or false guard
leaves the state unchanged with a content-free reason.

### 6.1. Automatic-retention states and transitions

States: `RT_OFF`, `RT_UNAVAILABLE`, `RT_REVIEW`, `RT_ACTIVE`, `RT_SHORTEN_REVIEW`, `RT_ELIGIBLE`,
`RT_CLEANUP_PENDING`, `RT_PARTIAL_FAILURE`.

| ID | Source | Event | Guard | Action | Target |
|---|---|---|---|---|---|
| `RT-001` | `RT_OFF` | `OPEN_WITH_CATALOG_UNAPPROVED` | allowed-period catalog has zero approved entries | expose unavailable reason; select no period | `RT_UNAVAILABLE` |
| `RT-002` | `RT_UNAVAILABLE` | `CATALOG_APPROVED_LATER` | a separately approved versioned catalog is installed | expose allowed entries; retain automatic retention off | `RT_OFF` |
| `RT-003` | `RT_UNAVAILABLE` | `CLOSE_UNAVAILABLE` | no selection exists | retain automatic retention off | `RT_OFF` |
| `RT-004` | `RT_OFF` | `SELECT_ALLOWED_PERIOD` | period key exists in the approved catalog; no value is preselected | create impact review; mutate no content | `RT_REVIEW` |
| `RT-005` | `RT_REVIEW` | `CANCEL_OPT_IN` | confirmation not accepted | discard draft policy; mutate no content | `RT_OFF` |
| `RT-006` | `RT_REVIEW` | `CONFIRM_OPT_IN` | exact opt-in copy shown; user explicitly confirms | activate selected period for Audio only; do not delete yet | `RT_ACTIVE` |
| `RT-007` | `RT_ACTIVE` | `REQUEST_SHORTENING` | shorter period key exists in approved catalog | show impact, including already-overdue eligibility; do not change policy yet | `RT_SHORTEN_REVIEW` |
| `RT-008` | `RT_SHORTEN_REVIEW` | `CANCEL_SHORTENING` | confirmation not accepted | retain prior period; do not change eligibility | `RT_ACTIVE` |
| `RT-009` | `RT_SHORTEN_REVIEW` | `CONFIRM_SHORTENING` | exact shortening copy shown; user explicitly confirms | activate shorter period; re-evaluate guards without deleting yet | `RT_ACTIVE` |
| `RT-010` | `RT_ACTIVE` | `DISABLE_AUTOMATIC_RETENTION` | explicit user action | clear active period; cancel future eligibility; do not restore deleted Audio | `RT_OFF` |
| `RT-011` | `RT_ACTIVE` | `AGE_REACHED_GUARDS_PASS` | durable finalization age reached; local result valid; recording inactive; jobs reconciled | freeze exact eligible Audio scope | `RT_ELIGIBLE` |
| `RT-012` | `RT_ACTIVE` | `AGE_REACHED_GUARDS_BLOCKED` | any required guard false, including export-only evidence | retain Audio; expose bounded reason; schedule no deletion | `RT_ACTIVE` |
| `RT-013` | `RT_ELIGIBLE` | `START_AUTOMATIC_CLEANUP` | frozen scope still revalidates and policy remains active | create/resume durable `OP-AUTO-AUDIO` operation | `RT_CLEANUP_PENDING` |
| `RT-014` | `RT_CLEANUP_PENDING` | `AUTOMATIC_CLEANUP_SUCCEEDED` | every scoped target verified | record `RETENTION_DELETED`; keep policy active for future Audio | `RT_ACTIVE` |
| `RT-015` | `RT_CLEANUP_PENDING` | `AUTOMATIC_CLEANUP_PARTIAL` | any scoped target failed or is unverified | persist remaining steps and content-free failure | `RT_PARTIAL_FAILURE` |
| `RT-016` | `RT_PARTIAL_FAILURE` | `RETRY_AUTOMATIC_REMAINING` | operation scope and policy still revalidate | retry only incomplete idempotent steps | `RT_CLEANUP_PENDING` |

### 6.2. Local deletion states and transitions

States: `LD_IDLE`, `LD_REVIEW_AUDIO`, `LD_REVIEW_CONVERSATION`, `LD_REVIEW_ACTIVE_CAPTURE`,
`LD_RECONCILING`, `LD_IRREVERSIBLE`, `LD_PARTIAL_FAILURE`, `LD_COMPLETED`, `LD_CANCELLED`.

| ID | Source | Event | Guard | Action | Target |
|---|---|---|---|---|---|
| `LD-001` | `LD_IDLE` | `REQUEST_AUDIO_ONLY_DELETE` | selected Conversation exists; active capture uses its separate event | show exact audio-only warning; mutate nothing | `LD_REVIEW_AUDIO` |
| `LD-002` | `LD_IDLE` | `REQUEST_CONVERSATION_DELETE` | selected Conversation exists; active capture uses its separate event | show exact whole-conversation warning; mutate nothing | `LD_REVIEW_CONVERSATION` |
| `LD-003` | `LD_IDLE` | `REQUEST_ACTIVE_STOP_DELETE` | selected capture is active | show exact stop-and-delete warning while capture remains active | `LD_REVIEW_ACTIVE_CAPTURE` |
| `LD-004` | `LD_REVIEW_AUDIO` | `CANCEL_AUDIO_DELETE` | confirmation not accepted | discard draft; restore focus; mutate nothing | `LD_CANCELLED` |
| `LD-005` | `LD_REVIEW_CONVERSATION` | `CANCEL_CONVERSATION_DELETE` | confirmation not accepted | discard draft; restore focus; mutate nothing | `LD_CANCELLED` |
| `LD-006` | `LD_REVIEW_ACTIVE_CAPTURE` | `CANCEL_ACTIVE_STOP_DELETE` | confirmation not accepted | leave capture and data unchanged; restore focus | `LD_CANCELLED` |
| `LD-007` | `LD_REVIEW_AUDIO` | `CONFIRM_AUDIO_DELETE` | exact warning shown; explicit confirmation accepted | durably create `OP-EXPLICIT-AUDIO`; cancellation ends | `LD_RECONCILING` |
| `LD-008` | `LD_REVIEW_CONVERSATION` | `CONFIRM_CONVERSATION_DELETE` | exact warning shown; explicit confirmation accepted | durably create `OP-EXPLICIT-CONVERSATION`; cancellation ends | `LD_RECONCILING` |
| `LD-009` | `LD_REVIEW_ACTIVE_CAPTURE` | `CONFIRM_ACTIVE_STOP_DELETE` | exact warning shown; explicit confirmation accepted | durably create `OP-ACTIVE-STOP-DELETE`; stop capture; cancellation ends | `LD_RECONCILING` |
| `LD-010` | `LD_RECONCILING` | `RECONCILIATION_READY` | jobs/uploads/keys/files/DB scope frozen and safe to mutate | begin idempotent irreversible scoped steps | `LD_IRREVERSIBLE` |
| `LD-011` | `LD_RECONCILING` | `RECONCILIATION_BLOCKED` | a required step cannot yet be verified | persist remaining steps; claim no completion | `LD_PARTIAL_FAILURE` |
| `LD-012` | `LD_IRREVERSIBLE` | `LOCAL_DELETE_SUCCEEDED` | every scoped category verified | persist content-free completion and exact source reason if retained | `LD_COMPLETED` |
| `LD-013` | `LD_IRREVERSIBLE` | `LOCAL_DELETE_PARTIAL` | any scoped category failed or is unverified | persist completed/remaining category bitmap without content | `LD_PARTIAL_FAILURE` |
| `LD-014` | `LD_PARTIAL_FAILURE` | `RETRY_LOCAL_REMAINING` | durable scope still revalidates | retry only incomplete idempotent steps | `LD_RECONCILING` |
| `LD-015` | `LD_COMPLETED` | `CLOSE_LOCAL_RESULT` | local terminal result visible | clear transient UI; retain minimum content-free evidence | `LD_IDLE` |
| `LD-016` | `LD_CANCELLED` | `CLOSE_CANCELLED` | no durable destructive operation exists | clear transient UI | `LD_IDLE` |

Closing the UI in `LD_RECONCILING`, `LD_IRREVERSIBLE` or `LD_PARTIAL_FAILURE` changes no operation
state. Restart resumes from the durable completed/remaining category bitmap; it never repeats a
verified step or converts an unverified step to success.

### 6.3. Storage-pressure states and transitions

States: `SC_IDLE`, `SC_SOFT_PRESSURE`, `SC_HARD_PRESSURE`, `SC_REVIEW_ACTION`, `SC_RUNNING`,
`SC_PARTIAL_FAILURE`, `SC_COMPLETED`.

| ID | Source | Event | Guard | Action | Target |
|---|---|---|---|---|---|
| `SC-001` | `SC_IDLE` | `OBSERVE_SOFT_PRESSURE` | soft threshold observed | show sizes and explicit reversible suggestions; delete nothing | `SC_SOFT_PRESSURE` |
| `SC-002` | `SC_IDLE` | `OBSERVE_HARD_PRESSURE` | hard threshold observed | show safe-stop/storage guidance; delete nothing | `SC_HARD_PRESSURE` |
| `SC-003` | `SC_SOFT_PRESSURE` | `SELECT_EXPLICIT_CLEANUP_ACTION` | action is visible and compatible | show exact target/scope impact; preselect nothing else | `SC_REVIEW_ACTION` |
| `SC-004` | `SC_HARD_PRESSURE` | `SELECT_EXPLICIT_CLEANUP_ACTION` | action is visible and compatible | show exact target/scope impact; preselect nothing else | `SC_REVIEW_ACTION` |
| `SC-005` | `SC_SOFT_PRESSURE` | `DISMISS_SOFT_GUIDANCE` | no operation confirmed | delete nothing | `SC_IDLE` |
| `SC-006` | `SC_HARD_PRESSURE` | `DISMISS_HARD_GUIDANCE` | no operation confirmed | delete nothing; preserve safety reserve | `SC_IDLE` |
| `SC-007` | `SC_REVIEW_ACTION` | `CANCEL_CLEANUP_ACTION` | confirmation not accepted | discard draft; mutate nothing | `SC_IDLE` |
| `SC-008` | `SC_REVIEW_ACTION` | `MODEL_IN_USE_BLOCKED` | selected model is active/selected/in use | show in-use reason and alternative; mutate nothing | `SC_REVIEW_ACTION` |
| `SC-009` | `SC_REVIEW_ACTION` | `SAFETY_SPACE_TARGET_BLOCKED` | proposed target is reserved safety space | reject target; mutate nothing | `SC_REVIEW_ACTION` |
| `SC-010` | `SC_REVIEW_ACTION` | `ROUTE_TO_LOCAL_DELETE` | selected action is audio-only or whole-conversation deletion | open the matching local-deletion review; start no cleanup here | `SC_IDLE` |
| `SC-011` | `SC_REVIEW_ACTION` | `CONFIRM_NON_SOURCE_CLEANUP` | selected target is DEC-017 temp or unused model and all guards pass | create/resume matching durable operation | `SC_RUNNING` |
| `SC-012` | `SC_RUNNING` | `CLEANUP_SUCCEEDED` | every selected non-source target verified | persist content-free result | `SC_COMPLETED` |
| `SC-013` | `SC_RUNNING` | `CLEANUP_PARTIAL` | any selected target failed or is unverified | persist remaining steps and failure | `SC_PARTIAL_FAILURE` |
| `SC-014` | `SC_PARTIAL_FAILURE` | `RETRY_CLEANUP_REMAINING` | exact scope still revalidates | retry only incomplete steps | `SC_RUNNING` |
| `SC-015` | `SC_COMPLETED` | `CLOSE_CLEANUP_RESULT` | terminal result visible | clear transient UI | `SC_IDLE` |

### 6.4. Remote deletion states and transitions

The only remote states are `ABSENT`, `NOT_REQUESTED`, `PENDING`, `FAILED`, `RECEIPT`.

| ID | Source | Event | Guard | Action | Target |
|---|---|---|---|---|---|
| `RM-001` | `ABSENT` | `REMOTE_SCOPE_NOT_APPLICABLE` | no approved remote representation is known | retain absent state; make no backend promise | `ABSENT` |
| `RM-002` | `ABSENT` | `REMOTE_SCOPE_DISCOVERED` | an approved provider record says remote scope may exist | expose separate remote action; send nothing | `NOT_REQUESTED` |
| `RM-003` | `NOT_REQUESTED` | `LOCAL_DELETE_COMPLETED_ONLY` | local operation completed; no remote request authorized | keep local result independent; send nothing | `NOT_REQUESTED` |
| `RM-004` | `NOT_REQUESTED` | `REQUEST_REMOTE_DELETE` | provider/region/account/consent/authZ/delete API are separately approved | enqueue one idempotent request; expose pending | `PENDING` |
| `RM-005` | `PENDING` | `REMOTE_DELETE_FAILED` | provider returned retryable/final failure or status is unverified | persist content-free failure; do not change local result | `FAILED` |
| `RM-006` | `PENDING` | `REMOTE_RECEIPT_VERIFIED` | receipt authenticates exact approved remote scope | store content-free receipt identity/status | `RECEIPT` |
| `RM-007` | `FAILED` | `RETRY_REMOTE_DELETE` | separate remote gate still valid | retry same idempotent operation | `PENDING` |
| `RM-008` | `FAILED` | `KEEP_REMOTE_FAILURE_VISIBLE` | retry not selected or unavailable | retain failure and local result independently | `FAILED` |
| `RM-009` | `RECEIPT` | `CLOSE_REMOTE_RECEIPT` | receipt status visible | retain content-free receipt; clear transient UI | `RECEIPT` |

No backend is selected or implemented by this contract. `RECEIPT` means only that the separately
approved provider receipt for the declared remote scope was verified; it makes no undisclosed
backup/replica promise.

## 7. Durable operation, restart and partial failure

A future implementation persists the minimum operation context needed to resume safely. This is a
behavioral contract, not a database schema. It contains an opaque local operation ID, operation
class, confirmed scope version, current state, content-free category completion bitmap, attempt
counter, last categorical error, local/remote axes and timestamps. Exact internal entity binding
may exist only in app-private storage; it is opaque outside that boundary and never logged.

- Before confirmation is durably accepted, restart returns to no operation and mutates nothing.
- After confirmation, restart resumes reconciliation or only the remaining irreversible steps.
- A verified completed category is never repeated; a missing record is never assumed complete.
- Partial failure lists preserved categories and remaining actions without content.
- Retry revalidates scope and performs only idempotent remaining steps.
- UI dismissal does not change a confirmed operation. No background resume creates new scope.
- Final success requires inventory reconciliation, not merely a successful API return.

## 8. DEC-017 export-temp boundary

A Dora-managed export temp is a non-canonical copy and never blocks audio-only or whole-
conversation deletion. If an opaque local binding is needed for lifecycle reconciliation, it is
not logged. Deleting the Conversation clears no external copy and does not shorten the temp's
existing `DEC-017` lifecycle. An unexpired temp remains governed by observable safe release,
separately warned `Delete now`, the one-hour hard maximum and startup recovery. `Delete now` uses
`COPY-EXPORT-TEMP-DELETE`, deletes only the Dora temp, and may end pending granted access. SAF or
other external copies remain outside Dora control.

## 9. Model and storage-pressure boundary

Model cleanup is a separate explicit operation. An active/selected/in-use model cannot be deleted;
the user receives a reason and may first stop/change the dependent job under its own contract.
Storage pressure may recommend deleting a DEC-017 temp, an unused model, Audio or a Conversation,
but it must route to the corresponding explicit review and confirmation. It never selects content,
uses the reserved safety space as a target or manufactures an automatic deletion because storage
is low or full.

## 10. Accessibility contract

Audio-only deletion, whole-conversation deletion, active Stop-and-delete, retention opt-in/
shortening, cleanup partial failure and remote status are `CRITICAL` for this flow. A future
implementation creates component records under the Component Accessibility Contract; this
document claims no pass.

1. Modal initial focus lands on title/explanatory scope, never the destructive confirmation.
2. Reading order is title → local scope → preserved/external/remote scope → irreversible limits →
   primary action → cancel/secondary action.
3. Every action is at least 48 × 48 dp with at least 8 dp between independent actions.
4. TalkBack, Switch Access and keyboard/D-pad reach every action; no swipe/gesture is the sole path.
5. At 200% text and maximum display stress, exact RU/EN warnings and all actions reflow without
   clipping, hidden text or horizontal content pan.
6. Cancellation restores focus to the invoking control. Completion/partial failure moves focus to
   the result heading; restart restores it to the current operation heading/action.
7. Status uses text plus a non-color cue. Color, animation, haptic or icon alone is insufficient.
8. Announce only confirmation, operation start, local completion, remote-state change and partial
   failure. Do not announce byte/file ticks or repeated retries.
9. Once irreversible work starts, Back/Escape/close may leave the screen but is announced as not
   cancelling the operation.
10. Partial failure remains persistently reachable with `Retry remaining`; it is not a transient
    snackbar and cannot be labelled complete.

## 11. Exactly 28 synthetic acceptance fixtures

All fixtures use deterministic synthetic identifiers and content-free state. No real meeting,
voice, person, private path, provider credential or destructive execution is needed or authorized.

| Fixture ID | Scenario | Events / inputs | Expected outcome and invariants |
|---|---|---|---|
| `DST-SYN-001` | Default retention | fresh install/settings | audio retained until explicit delete; `RT_OFF`; no period selected; no deletion |
| `DST-SYN-002` | Catalog unavailable | `OPEN_WITH_CATALOG_UNAPPROVED` | `RT_OFF→RT_UNAVAILABLE`; zero entries; readable reason; no numeric period or mutation |
| `DST-SYN-003` | Future deterministic opt-in | `CATALOG_APPROVED_LATER`, `SELECT_ALLOWED_PERIOD`, `CONFIRM_OPT_IN` with catalog key only | `RT_UNAVAILABLE→RT_OFF→RT_REVIEW→RT_ACTIVE`; exact copy; Audio only; no immediate deletion |
| `DST-SYN-004` | Future shortening | `REQUEST_SHORTENING`, `CONFIRM_SHORTENING` | `RT_ACTIVE→RT_SHORTEN_REVIEW→RT_ACTIVE`; exact impact; overdue Audio only re-evaluated after confirmation |
| `DST-SYN-005` | Automatic cleanup with durable local result | age reached; result validated; recording inactive; jobs reconciled; cleanup succeeds | `RT_ACTIVE→RT_ELIGIBLE→RT_CLEANUP_PENDING→RT_ACTIVE`; reason `RETENTION_DELETED`; structured content/FTS preserved |
| `DST-SYN-006` | Export-only automatic candidate | external export exists; no durable local result; `AGE_REACHED_GUARDS_BLOCKED` | remains `RT_ACTIVE`; no deletion; export is not result |
| `DST-SYN-007` | Explicit audio-only with result | audio warning, confirm, reconcile, success | `LD_IDLE→LD_REVIEW_AUDIO→LD_RECONCILING→LD_IRREVERSIBLE→LD_COMPLETED`; result irrelevant; structured content/FTS preserved |
| `DST-SYN-008` | Explicit audio-only with export only | export exists; no result; explicit confirm and success | same successful explicit path; export neither required nor a blocker; external copy untouched |
| `DST-SYN-009` | Explicit audio-only with neither | no result and no export; explicit confirm and success | same successful explicit path; reason `USER_DELETED`; no result/export prerequisite |
| `DST-SYN-010` | Whole-conversation deletion | whole warning, confirm, reconcile, success | canonical/structured/derivatives/FTS deleted locally; minimum content-free evidence retained; remote independent |
| `DST-SYN-011` | Active capture cancel | `REQUEST_ACTIVE_STOP_DELETE`, `CANCEL_ACTIVE_STOP_DELETE` | capture continues; bytes/data unchanged; focus restored; no durable destructive operation |
| `DST-SYN-012` | Active capture confirm | `REQUEST_ACTIVE_STOP_DELETE`, `CONFIRM_ACTIVE_STOP_DELETE`, reconcile and success | capture stops then scoped deletion runs; closing UI does not cancel; no undo claim |
| `DST-SYN-013` | Processing and queued upload reconciliation | explicit deletion with running local job and unsent queued upload | new work blocked; job cancelled/reconciled; queued send revoked; already-remote scope moves only through remote axis |
| `DST-SYN-014` | Per-audio key failure | explicit audio operation cannot verify per-audio key deletion | no crypto-erasure claim; `LD_PARTIAL_FAILURE`; file/data state listed content-free; retry remaining available |
| `DST-SYN-015` | File deletion failure | allowlisted Audio file delete fails | `LD_PARTIAL_FAILURE`; completion forbidden; source reason not falsely finalized; retry remaining |
| `DST-SYN-016` | Database reconciliation failure | file/key step may have completed but DB reason/tombstone update fails | durable bitmap preserves completed steps; partial visible; restart/retry only incomplete DB step |
| `DST-SYN-017` | FTS failure | whole-conversation FTS removal fails | whole deletion remains partial; FTS category retryable; no local-complete claim |
| `DST-SYN-018` | Source reason taxonomy | synthetic retained links exercise `USER_DELETED`, `RETENTION_DELETED`, `MISSING`, `CORRUPT`, `KEY_UNAVAILABLE` | each exact RU/EN label distinct; missing/corrupt/key unavailable never called deletion success |
| `DST-SYN-019` | Source links after audio-only delete | transcript/protocol/decisions/tasks/summary/FTS with timestamps | all retained and searchable; source action exposes exact unavailable reason without fake playback |
| `DST-SYN-020` | Live DEC-017 temp during conversation delete | unexpired Sharesheet temp plus whole-conversation confirmation | Conversation deletion proceeds; temp remains under safe-release/warned Delete-now/one-hour/startup lifecycle; external copy untouched |
| `DST-SYN-021` | Model in use | `MODEL_IN_USE_BLOCKED` | `SC_REVIEW_ACTION` unchanged; model and data untouched; reason/action accessible |
| `DST-SYN-022` | Unused model cleanup | explicit model action, confirm, cleanup success | `SC_REVIEW_ACTION→SC_RUNNING→SC_COMPLETED`; only model/model-cache target; source and safety reserve untouched |
| `DST-SYN-023` | Soft storage pressure | `OBSERVE_SOFT_PRESSURE`, dismiss | sizes/suggestions shown; no target selected; `SC_IDLE→SC_SOFT_PRESSURE→SC_IDLE`; no deletion |
| `DST-SYN-024` | Hard storage pressure | `OBSERVE_HARD_PRESSURE`, attempted safety-space target | safety target blocked; safe-stop guidance shown; no automatic deletion or invented cleanup |
| `DST-SYN-025` | Restart before irreversible work | confirmation not durably accepted, then restart | no operation and no mutation; return to idle; confirmation required again |
| `DST-SYN-026` | Restart after partial work | durable operation bitmap has key complete, file pending, DB pending | resume/retry only pending steps; completed key step not repeated; no success until full reconciliation |
| `DST-SYN-027` | Remote-state matrix | no scope; discover scope; local-only completion; separately gated request; failure/retry; verified receipt | only `ABSENT/NOT_REQUESTED/PENDING/FAILED/RECEIPT`; local result unchanged; no backend promise |
| `DST-SYN-028` | Accessibility matrix | RU/EN, 200%, TalkBack, Switch, keyboard/D-pad, reduced motion, modal cancel/result/partial states | exact warnings/actions reachable; focus deterministic/restored; ≥48 dp; meaningful announcements; no color-only state; no conformance claim |

### 11.1. Future implementation acceptance

An exact implementation can claim `PASS` only when every applicable fixture has reproducible
evidence and all current critical accessibility/component gates pass. Additional assertions:

- zero automatic deletion with retention off, catalog unavailable, export-only evidence, active
  recording or unreconciled jobs;
- zero structured/whole-conversation deletion through automatic retention;
- zero mutation before explicit confirmation;
- zero source/result/export prerequisite for explicit audio/Conversation deletion;
- exact local target verification and no false physical/crypto-erasure claim;
- exact source reason and retained source-link behavior after audio-only deletion;
- no source deletion from DEC-017 temp cleanup;
- no content-bearing logs/evidence and no silent remote request;
- restart/retry performs only incomplete idempotent steps.

## 12. Current evidence status

| Result | Meaning |
|---|---|
| `CONTRACT_COMPLETE` | This reusable `DES-STORAGE-001` contract, exact copy and synthetic acceptance matrix exist. No implementation/conformance claim follows. |
| `PASS` | A separately scoped exact implementation/prototype passed every applicable contract and gate with linked evidence. |
| `BLOCKED` | A required state, scope, privacy/accessibility control or evidence is missing/failed. |
| `NOT_RUN` | The named implementation, device, accessibility, Legal/Security or research evidence was not executed. |

Current version 0.1 status:

- contract/traceability/copy/state/fixture specification: `CONTRACT_COMPLETE`;
- Android/Figma/schema/storage/database/filesystem/Keystore/jobs/provider/backend implementation:
  `NOT_RUN` and not created;
- synthetic fixture execution, deletion execution and device evidence: `NOT_RUN`;
- TalkBack/Switch/keyboard/accessibility conformance and user research: `NOT_RUN`;
- Legal/Security approval, dependency/production admission and real-data collection: `NOT_RUN` and
  not claimed;
- all nine Recovery execution/admission authority flags: unchanged and false;
- Ready transition and merge: not authorized by this contract.

## 13. `DES-STORAGE-001` exit traceability

| Backlog exit evidence | Contract location |
|---|---|
| Exact scope | §§3–4, state machines §§6.1–6.4 and fixtures `DST-SYN-001`–`017`, `020`–`027` |
| Loss-of-source test | §§4.1, 4.3, exact copy §5 and fixtures `DST-SYN-007`–`009`, `014`–`019` |
| Accessible comprehension contract | §§5, 10 and fixture `DST-SYN-028` |

This mapping closes only the reusable design/governance contract deliverable. It makes no
implementation, conformance, research or deletion-execution claim.
