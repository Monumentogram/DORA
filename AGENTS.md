# Dora repository instructions

These rules apply to the entire repository and to every later Codex task.

## Physical-device owner interaction

`OWNER_INTERACTION_NOTIFICATION_REQUIRED` is mandatory for every DORA task that
needs Yuri to look at the phone, confirm a permission, press a button, enter
information, select a file or otherwise act on the device. Before planning or
performing physical-device work, read and follow
[`docs/OWNER_INTERACTION_PROTOCOL.md`](docs/OWNER_INTERACTION_PROTOCOL.md).

- Prepare unattended work only within the current authorization. This rule does
  not itself authorize device commands, installation or a retry after STOP.
- Send a separate **🔔 ТРЕБУЕТСЯ УЧАСТИЕ ЮРИЯ** notice with the action, reason,
  Android user, expected dialog, estimated duration and exact requested response.
- Enter `WAITING_FOR_OWNER_ON_DEVICE`; send no phone commands while waiting.
- A new installation after STOP requires **«РАЗРЕШАЮ ОДНУ УСТАНОВКУ. Я У ТЕЛЕФОНА».**
  Presence alone, earlier approval and silence do not authorize a new attempt.
- After explicit confirmation, announce **«НАЧИНАЮ СЕЙЧАС. СМОТРИ НА ЭКРАН POCO».**
  Then perform only the agreed action. Notify and wait again for each additional
  interaction. Unexpected user 0, security-settings or administrative prompts
  require STOP; never bypass them or automatically retry.
- Preview targets user 10 only. Do not switch users automatically, touch user 0
  data/settings/apps, update Pilot, delete apps/data, run Recovery or access
  LONG-01/LONG-02. Never change security restrictions to bypass an install failure.

The protocol must accompany this instruction on future branches. A pending PR
does not make it present in other checkouts; before device work, verify the
checkout contains the rule and its linked protocol. Preserve historical evidence.

## Authoritative sources

Read the relevant source completely before changing its area. Requirement precedence is:

1. `docs/DORA_MVP1_TECHNICAL_PLAN.md`
2. `docs/DORA_MVP1_DESIGN_SPEC.md`
3. `docs/DORA_MVP1_PRODUCT_DECISIONS.md`
4. accepted files in `docs/adr/`
5. `docs/DORA_MVP1_TEST_STRATEGY.md`
6. `docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md`
7. `docs/DORA_MVP1_STAGE_STATUS.md`

The token JSON and screen inventory are normative handoff artifacts under their parent design specification. If sources conflict, stop only the affected code, record the conflict, and request/land a DEC or ADR. Never choose silently.

## Stage gate

- Work only on the stage and backlog IDs explicitly placed in scope by the user.
- Stage 00 permits repository/bootstrap/CI/docs only. It does not permit Dora product features.
- After Stage 00, the next safe work is isolated Stage 0 PoC. Production capture, storage, ML and backend code remain gated by the readiness review.
- A PoC is evidence, not an admitted production dependency. Admission needs an ADR plus license, security, ABI and test evidence.
- Do not scaffold unused future modules, SDKs, tables or providers merely because the target architecture names them.

## Git and GitHub

- `main` is protected conceptually: no development commits directly to it.
- Start from up-to-date `main`, use a dedicated branch and open a Pull Request.
- Never amend or rewrite the baseline commit `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`.
- Never force-push, hard-reset, clean unknown files, delete user work or rewrite shared history.
- Inspect `git status`, diff and staged diff. Stage only files belonging to the task.
- Do not merge a PR unless the user explicitly scopes a later task to that merge.
- Repository visibility is temporarily public by the explicit owner decision recorded in `docs/adr/ADR-0002-public-repository-for-branch-protection.md`. Do not change visibility again without explicit owner scope, a fresh secret/privacy audit and verification that `main` protection remains enforceable.
- Treat tracked content, Git history, Pull Requests and Actions logs as public. Do not put secrets, signing material, private audio, credentials, tokens or unapproved model weights in Git, LFS, logs or Actions artifacts.

## Change discipline

- Preserve baseline documents. Amend them only with demonstrated necessity and an explicit decision/change record.
- Record product behavior in Product Decisions; record consequential technical choices in an ADR.
- Update backlog/status when a result changes readiness, dependency order or truth.
- Use synthetic fixtures unless a documented, consent-governed dataset process explicitly authorizes other data.
- User edits and confirmations are authoritative. Model reprocessing may propose a diff but may not overwrite them silently.
- Local mode must remain functional without account, network, GMS or cloud configuration.

## Android baseline

- Android project lives under `android/`.
- Baseline: JVM 17, Kotlin, Gradle Kotlin DSL, version catalog, Compose, `minSdk 28`, `compileSdk/targetSdk 36`.
- The Stage 00 application ID is non-release and must not be registered or published.
- Use semantic design tokens in feature UI; do not bind components directly to raw brand colors.
- No native binary/model is admitted without reproducible build or provenance, exact digest/license, 16-KB page-size packaging/runtime evidence and a replaceable port.
- Capture UI/rendering, database/network/ML and audio callback/thread must remain isolated by design.

## Required checks

From `android/`, run the repository-documented Gradle verification tasks. The required levels, environments and release gates are defined in `docs/DORA_MVP1_TEST_STRATEGY.md`. For changes that add native code, recording, storage, data schema, UI or backend, add the relevant tests from the readiness/backlog gate; a green bootstrap build alone is never sufficient evidence.

## Handoff

Every task report states:

- branch and commit(s);
- exact files/behavior changed;
- local and CI checks with outcomes;
- unresolved P0/P1 blockers and owner action;
- PR URL/status when published;
- confirmation that no PR was merged unless explicitly requested.
