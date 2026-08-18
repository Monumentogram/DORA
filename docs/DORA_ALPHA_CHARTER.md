# DORA Alpha Charter

Status: Approved for `DORA-ALPHA-001` Increment 1 by direct Project-owner scope\
Date: 17 August 2026\
Parallel track: `STAGE0-CLOSURE-PARALLEL-001`\
Authority record: the Project owner explicitly requested an Alpha product track in parallel with
Stage 0, authorized the necessary DEC/ADR/backlog/status changes and authorized implementation to
start immediately after this minimum contract was frozen.

## 1. Outcome

DORA Alpha exists to deliver a useful internal Android product without misrepresenting incomplete
Stage 0 evidence as production readiness. The first installable vertical build is due within seven
days. The target useful Alpha is four weeks; eight weeks is the outer bound for the Alpha Definition
of Done.

Alpha does not change the verdict of any Stage 0 PoC. A failed or inconclusive PoC blocks only the
capability that depends on it. The Alpha must omit that capability or show an honest disabled state.

## 2. Increment 1 user value

The first vertical slice is a manual, local meeting workspace:

1. create a conversation note;
2. enter and edit a title, transcript/notes, manual summary and manual tasks;
3. save it locally without account, network or GMS;
4. reopen it after Activity/process restart;
5. find it by local text search;
6. mark its tasks complete or incomplete;
7. delete the whole local conversation after explicit confirmation.

All content in this increment is authored by the user. The application does not label any manual
text as ASR, AI summary, diarization, extracted task or verified meeting source.

### Honest unavailable stages

The following are visibly unavailable in Increment 1 and have no hidden implementation path:

- microphone recording, audio import and playback;
- VAD, ASR, diarization and speaker identity;
- automatic protocol, summary, decision or task generation;
- models, cloud processing, sync, account, network and GMS;
- export or sharing.

The central microphone action remains disabled and explains this boundary. No synthetic or
placeholder result is shown as a product result.

## 3. Authority and stage gate

`ALPHA-GATE-001` authorizes product code only for the Increment 1 manual workspace described above.
It supersedes the previous Stage-00 neutral-shell restriction only for this bounded Alpha branch and
does not admit capture, storage crypto, Room/SQLCipher, native code, models, backend or production
identity.

Authority flags:

| Flag | Value |
|---|---|
| `alphaManualWorkspaceImplementationAllowed` | `true` |
| `internalDebugApkAllowed` | `true` |
| `recordingOrAudioAllowed` | `false` |
| `automaticProcessingAllowed` | `false` |
| `networkOrCloudAllowed` | `false` |
| `realMeetingOrSensitiveDataAllowed` | `false` |
| `productionIdentityOrSigningAllowed` | `false` |
| `storeOrProductionReleaseAllowed` | `false` |
| `stage0PassImplied` | `false` |

Until a later privacy/data decision, the build is for synthetic or non-sensitive test text only.
This restriction is shown inside the application. No real meeting, voice or unapproved personal
data is authorized by this Charter.

## 4. Technical boundary

- Reuse `:app`, Kotlin, Compose and existing semantic tokens. Do not create unused modules.
- Preserve the non-release `com.monumentogram.dora.bootstrap` application ID and debug-only
  distribution boundary.
- Use a small, versioned, size-bounded app-private `AtomicFile` adapter for Alpha records. It must
  write atomically, validate every length/count/identifier, recover Android's backup file where
  available and fail closed on corrupt or unsupported data.
- The Alpha store is not encrypted beyond Android platform storage, is not a production database
  and makes no SQLCipher, cryptographic erasure, backup, forensic-resistance or security claim.
- No new runtime dependency, permission, service, receiver, provider, native library, model,
  network client or analytics SDK is admitted.
- Every stored task has `origin=USER`, belongs to one Alpha conversation and has no fabricated
  transcript/source event. Increment 1 does not implement standalone tasks.
- Whole-conversation deletion is local-only, explicitly confirmed and verified by the persisted
  snapshot. Remote and audio deletion states are not manufactured.

The Alpha file format is disposable and replaceable. A later production storage admission must
migrate or deliberately discard Alpha data under a separate ADR; it must not silently relabel this
adapter as Room/SQLCipher production storage.

## 5. Critical path

1. Freeze this Charter, `DEC-045`, `ADR-0003`, Alpha backlog and status.
2. Implement bounded model/codec/atomic repository and corruption/restart tests.
3. Implement create/edit/view/search/task-toggle/delete UI with semantic tokens and accessible
   labels/targets.
4. Prove the app has no microphone/network/account/GMS path and produces an installable debug APK.
5. Run host/static/lint/assemble checks, device smoke and independent review.
6. Publish an internal build and installation/check instructions.

Stage 0 closure, devices, Recovery, battery, VAD, ASR, diarization and evidence continue in the
parallel coordinator. None is placed on this Increment 1 critical path unless its capability is
actually admitted later.

## 6. Seven-day increment

| Day | Verifiable result |
|---|---|
| 1 | governance commit; bounded data contract; local repository and codec tests |
| 2 | create/edit/save/reopen flow |
| 3 | history, search and task completion |
| 4 | scoped delete, corruption/restart recovery and honest unavailable states |
| 5 | Compose semantics, compact/wide/large-text checks and defect fixes |
| 6 | clean CI, independent review and physical smoke preparation/execution |
| 7 | installable internal APK, known-limitations release note and verification guide |

Work may complete earlier. A day is not marked complete without its evidence.

## 7. Increment 1 Definition of Done

- a clean checkout builds an installable debug APK with the non-release ID;
- a user can create, view, edit, search and delete an Alpha conversation locally;
- manual tasks persist and can be completed/reopened;
- process/Activity restart preserves the last successful atomic snapshot;
- corrupt, truncated, oversized or unsupported snapshots fail closed without fabricated content;
- no account, GMS, network, microphone permission, recording, model or automatic-result path exists;
- the application names the Alpha limitation and synthetic/non-sensitive-data restriction;
- relevant unit, Compose/instrumentation compilation, lint, formatting, static-analysis, APK and
  native-allowlist checks are green;
- one physical Android profile passes the documented smoke flow before an Alpha DoD claim;
- no known P0/P1 remains; known limitations are in-app or in release notes;
- install and verification instructions identify the exact commit and APK digest.

## 8. Full Alpha Definition of Done

The Project-owner definition remains authoritative: an internal APK; one physical end-to-end
profile; a useful non-placeholder result; local operation without account/GMS/network; view/edit/
delete; restart/interruption recovery; green automated and physical smoke tests; no known P0/P1;
visible limitations; and concise install/test instructions.

Capabilities added after Increment 1 require their own admission evidence. In particular, capture,
audio storage, ASR, diarization and automatic analysis cannot enter merely because the manual Alpha
is useful.

## 9. Branch and Pull Request plan

- Branch: `codex/dora-alpha-001`, forked from verified `main`.
- Commit 1: Charter/DEC/ADR/backlog/status only.
- Commit 2+: implementation and tests in reviewable vertical boundaries.
- Open a Draft PR. Ready/merge is allowed only when exact-head CI is green, independent review has
  no P0/P1, scope has not expanded and no Security/Privacy/IP/Legal specialist blocker applies.
- Merge does not mean Stage 0 PASS, production release or store publication.

## 10. Reporting and non-goals

Milestone reports state user-visible behavior, PR/build, checks, P0/P1, critical path and forecast.
The Google Sheet `Dora MVP 1 — дорожная карта разработки` is not updated.

Increment 1 is not a production database, secure notes vault, recorder, transcription product,
AI demo, Stage 0 verdict, beta, release candidate or store build.
