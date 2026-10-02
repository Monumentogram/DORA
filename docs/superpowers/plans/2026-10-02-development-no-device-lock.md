# Development no-device-lock implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. The owner supplied the explicit design, required change and execution scope; independent adversarial review is required.

**Goal:** Permit opted-in local debug development without an Android PIN, preserve release security and existing storage, then obtain complete physical latency evidence.

**Architecture:** A shared device-security boundary separates foreground device readiness from the already-active writer's credential prerequisite. A debug-only opt-in supplies the narrow insecure-device exception; the release source set cannot grant it. Existing App Lock session and recording generations still supply all resource authority.

**Tech Stack:** Existing Kotlin/JVM17 Android source sets and test infrastructure; no new product dependency.

**Spec:** Owner's 2026-10-02 Temporary Development No-PIN Mode request; ADR-DEV-002 records scope. Parent 1fce758237942f03e35b6560b48a2f4983389eab, existing branch and draft PR94.

## Global constraints

- Exact debug package, FLAG_DEBUGGABLE, empty private no-backup marker; release always denies.
- Old no-prompt marker and new no-device-lock marker retain separate meanings.
- No key replacement, plaintext, destructive migration, persisted authority or background auto-start.
- Owner alone changes the PIN through Android Settings after implementation verification and pre-removal vault evidence.
- All historical contracts/evidence retained; full regression and exact-SHA CI required.
- 30+30 from new SHA, no missing presentation, no replacement; fail fast on ambiguity.
- Stage8.4 NOT_STARTED; restoration blocker OPEN; no merge/rebase/force-push.

## Review focus

- Marker replaced with nonempty file, directory, symlink or wrong location: deny.
- Marker removed after a grant: device boundary rechecks; stale authority cannot regain access.
- Background writer continuity versus foreground start: existing capture may continue, new start cannot.
- Release compiled with debug-like injected inputs: grant remains impossible.
- PIN removal affects existing keys: inspect exact original storage; never fall back to create.

## Task 1: narrow security policy and regression tests

- [x] RED behavior tests for debug/release DeviceSecurityPolicy and marker validation, foreground generation, stale handles and process restart.
- [x] Implement DeviceSecurityPolicy, AndroidDeviceSecurityPolicy and debug/release DevelopmentDeviceSecurityOverride; wire AndroidAppLock and AndroidRecordingAccessManager consistently.
- [x] Keep real authentication proof/activity on real system security; development disclosure resides only in debug source.
- [x] Run debug and release unit suites; independent boundary review.

## Task 2: nondestructive device evidence and exact presentation collection

- [x] Add a bounded instrumentation-only existing-vault/key receipt through the real target app; no production/exported authority route, no keys or audio emitted.
- [x] Prove baseline existing-vault readback and aliases before the owner changes PIN.
- [ ] Harden private per-operation input/state/frame correlation and fail-fast completeness. Smoke the collection pipeline before the full 30+30 campaign.
- [x] Only after implementation/build/review verification ask for the one owner-side Settings action; verify the same vault/aliases/readback afterwards or stop.

## Task 3: bounded successor governance and full verification

- [x] Add sealed development successor contract, validators and negative tests; preserve immutable parent checks and prospective thresholds.
- [x] Add OPEN DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE in backlog/status and Alpha closure admission.
- [x] Full local JVM/414 Recovery/Python/stage gates/API28/API36/process-death/UI/lint/Detekt/format/dependencies/SBOM/native16KiB checks. Local JVM: 779 passed, one Windows symlink capability skip; Python: 384 passed. API28 and API36 each: 102 persistence/auth, six process-death phases and 15 UI checks passed. Bounded local audits passed; publication audit remains pending.
- [ ] Commit exact reviewed change, update existing draft PR94, run exact-SHA CI and inspect published logs/artifacts; no merge.

## Task 4: physical acceptance and qualified closure

Pre-PIN and post-PIN selected-existing-recording readback passed on the dedicated physical device: five identical existing sources, 17,456,800 frames, and all 621 persistent wrapping-key identities/creation times unchanged. All 3,103 ciphertext files were byte-identical immediately across the owner-side Settings change, before reopening the application. This is selected-recording readback, not exhaustive historical-recording validation. Private receipts retain exact comparisons; no audio is published.

- [ ] New exact-SHA debug no-PIN preflight; mic OFF, correct opt-ins, existing vault intact.
- [ ] 30 Pause and30 Resume with per-attempt exact presentation validation, rapid cycles, PAUSED/PENDING, Resume/priorPENDING and strict final SAVED.
- [ ] Independent adversarial review with0 unresolved P0/P1/P2; content-free public evidence and external receipt.
- [ ] PASS only with all gates and explicit debug no-PIN evidence qualification; otherwise precise NOT_READY. Sheet only after successful publication; future restoration blocker remains OPEN.
