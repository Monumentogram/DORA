# Samsung audio P0 implementation plan

> **For agentic workers:** Use the applicable systematic-debugging, TDD, parallel-agent and verification skills. Parent integrates distinct file ownership; an independent reviewer checks the final patch and release evidence.

**Goal:** Remove redundant key opens, stop abandoned authenticated reads and reduce player preparation without weakening storage guarantees.
**Architecture:** Per-reconciliation validated-key scope; transient cancel predicates through source ports/coordinator/player; bounded playback-only PCM windows; scalar diagnostics.
**Tech stack:** Existing Kotlin/JVM17, Android API28/36, Compose, SQLCipher, Android Keystore and Tink.
**Spec:** ../specs/2026-11-02-samsung-audio-performance-design.md

## Constraints and review focus

- Keep exact formats, AAD, owner/vault/source identity, revocation, deletion and key invalidation.
- No personal-device operation until new owner authorization; no AWS or merge.
- Watch cancellation racing corrupt reads: cancel must not permanently poison the source.
- Watch late successful handles discarded on revoke/interruption: close exactly once.
- Watch cached primitive reuse after failure, deletion, scope exit or from another thread/run/vault.
- Watch five-second range boundaries, partial final units, seeks, prefetch cancellation and PCM wiping.
- Watch opt-in diagnostics: bounded fields, no content, no logging exceptions or identity.

## Task1 — scoped key reuse and measured call counts

Files: RunKeyFailureScopes.kt, AndroidVaultKeyBackend.kt, dedicated pure scope helper if needed;
RunKeyFailureScopesTest, AudioMemoryFixture, ScopedRecoveryKeyReuseTest,
RunKeyBoundaryTest/RunKeyReusePipelineTest.

- [ ] RED: real recovery pipeline demonstrates three opens/nine provider cipher calls per unit before reuse; new target assertion fails.
- [ ] Implement reuse only for the active synchronous RECONCILIATION run; live existence, provider failure, finally expiry and deletion invalidation remain.
- [ ] GREEN: one validated open/five provider calls; mandatory three decrypts still run, real corruption and failure/isolation controls pass.
- [ ] Run scoped JVM and API28/API36 tests; preserve baseline/after scalar receipts.

## Task2 — cancellation and playback windows

Files: OriginalAudioPort.kt, OriginalAudioLifecycle.kt, AudioRuntimeCoordinator.kt,
OriginalAudioPlayer.kt, PlaybackSourceCache.kt, PcmWindows.kt and corresponding tests.
Interface: cancellable authenticated-open overloads with a default false predicate;
existing callers retain their semantics. New playback window limit80,000frames.

- [ ] RED: cancel after one authenticated unit stops remaining work, wipes bytes and releases lease; retry succeeds without permanent-loss retention.
- [ ] Implement propagation, late-result disposal, player close/seek cancellation and five-second playback windows.
- [ ] GREEN: revocation, interruption ownership, real corruption, exact range boundaries, prefetch cleanup and new-recording handoff tests pass.
- [ ] Run scoped Android player/recording/history/transcript/search/export/delete/restart regressions on API28/API36.

## Task3 — scalar diagnostics and measured comparison

Files: new AudioOperationDiagnostics.kt/test, PersistenceLatency.kt, RecoveryAudioBridge.kt,
EncryptedAudioVault operation boundaries, MainActivity/service dump and narrowly coordinated player/key hooks.

- [ ] RED: fake-clock stages, progress, bounded enum fields, independent operation/thread snapshots and no stale running state on exception/cancel.
- [ ] Implement fixed-scope snapshots and stage counts/times, no content-bearing input.
- [ ] Compare the same bounded synthetic source before/after; distinguish structural counts from emulator/physical wall times.
- [ ] Record findings/limits and Stage13 Local Whisper progress backlog, decision and status.

## Task4 — qualify, review and deliver

- [ ] Relevant JVM/host/format/lint/security-storage regressions and exact-head required CI; preserve all required inventories and prior failures.
- [ ] Version/sign the next candidate only for actual Android changes; verify package/signer/native/model continuity and retained-data update.
- [ ] Independent code and concrete artifact review. Draft PR stacked on130→128→127; never merge.
- [ ] Ask for Samsung permission only after the candidate and exact same-recording comparison procedure are reviewable. If unavailable, report physical targets PENDING and do all software work.
- [ ] Publish a signed Owner Alpha only after the applicable release gates; no physical or full Stage13 PASS by inference.

## Execution evidence

Baseline: clean45e1e09, new dedicated branch. Initial Gradle invocation could not locate
SDK; no tests ran. With the existing ANDROID_HOME explicitly set, core-audio and app
unit suites passed in22seconds (`p0-baseline-unit-sdk.log`, private local outputs).

### Software evidence, November 2–3

Scoped reuse, cancellation, five-second playback windows and scalar diagnostics are
implemented. Core JVM: 317 tests passed. Integrated debug app/core unit tests and
instrumentation assembly passed. Formatting, Detekt and debug lint passed after
fixing the nullable Activity.dump signature and five style findings. The original
failed logs are retained locally; none is counted as a successful check.

Native key/recovery suite: 23 tests passed on each API28 and API36. This includes
provider invalidation, alias deletion, corruption/quarantine and original-audio
runtime boundaries. A first batch used the wrong OriginalAudioRuntimeTest package;
the corrected exact class inventory passed. API36 abrupt process-death recovery
passed INTENT, PUBLISHED and COMMITTED in separate processes.

Matched deterministic 20-second PCM fixtures use four encrypted units. Each run
generates fresh vault identifiers, keys and nonces: this is not the same ciphertext
and is not the owner's retained 30-minute Samsung recording. Wall times are single
observations under concurrent emulator activity, not a latency acceptance result.

| API | Operation | Before ms | After ms |
|---|---|---:|---:|
| 36 | Finalize | 398.764 | 277.155 |
| 36 | Authenticated open 1 | 270.471 | 192.655 |
| 36 | Authenticated open 2 | 277.221 | 195.770 |
| 28 | Finalize | 724.230 | 719.579 |
| 28 | Authenticated open 1 | 517.654 | 588.050 |
| 28 | Authenticated open 2 | 490.844 | 618.000 |

For every successful four-unit operation, validated key opens and KeyInfo reads
fell from 12 to 4; provider cipher creations fell from 36 to 20. All three mandatory
authenticated envelope decrypts per unit remain. Baseline Android test APK SHA256:
`284485cd49bfc31150e8a00cf16c69c75136261b577d340dd25070569bb234df`.
Raw content-free baseline/after counters are retained outside Git.

The two new UI tests prove cancellation/disposal stops after the first authenticated
unit, wipes borrowed PCM and permits a new recording writer in the same process on
both APIs. API36's complete10-test UI regression batch passed. API28's combined
batch exposed Compose idle synchronization failures: the three-second playback
state was missed; live recording's Pause scroll waited for idle and its failed
teardown affected later tests. This run remains FAIL, with isolated reproductions
retained. Test-only synchronization/cleanup corrections subsequently passed the
same 10-test inventory on API28 (109.693 seconds) and API36 (109.892 seconds).
The isolated API28 playback/recording pair passed in 23.898 seconds. Independent
review confirmed the assertions and original waits remain intact. API28 abrupt
process-death recovery also passed INTENT, PUBLISHED and COMMITTED.

Independent static review approved the production Kotlin patch over45e1e09 with no
actionable P0/P1/P2 findings. It does not qualify an APK, CI head or Samsung timing.
The candidate identity and existing CI inventories include v11 and the new tests;
65 scoped host identity/inventory tests passed. No P0 commit, new Draft PR, signed
v11 distribution or physical acceptance is claimed yet. The Stage00 validator's
content checks passed; its publication check correctly refuses an uncommitted
working tree. Repeat it against the clean committed source before publication.

The owner's subsequent Cloud-activation request prioritizes the existing installed
v10 and existing backend. P0 work is preserved on its dedicated branch; Cloud
registration does not authorize a P0 APK installation or a long paid test.
