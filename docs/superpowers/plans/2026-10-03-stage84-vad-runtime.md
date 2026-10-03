# Stage8.4 production VAD implementation plan

> For agentic workers: use superpowers:executing-plans inline; independent final adversarial review.

**Goal:** implement actual observer VAD, deterministic semantics and exact technical rotation without changing canonical audio.
**Architecture:** bounded derived observer and pure frame reducer; canonical session owns technical splitting; SQLCipher stores derived metadata.
**Tech stack:** existing Kotlin/Android/Room/SQLCipher, exact admitted sherpa1.13.8 and Silero6.2.1, credential-free CI.
**Spec:** owner's68-section Stage8.4 Implementation request and [ADR-VAD-002](../../adr/ADR-VAD-002-production-segmentation.md).

## Global constraints

- Baseline4073107108604136425071a51e63703374a660b8; new child branch/stacked draft PR; no merges/history rewrite.
- Canonical S16LE/16kHz/mono;90s=1,440,000 frames;600s=9,600,000;pre-roll32,000.
- Exact AAR64969d0f...2208db and model1a153a22...788e3; never public/private CI credentials.
- Onset0.3-0.5s,hysteresis0.8-1.2s,overlap1.5-2s calibrated/frozen before final acceptance.
- No product8.4C/8.5/ASR/Cloud; PENDING_FINAL_PUBLICATION until all external gates.

## Review focus

- Observer stalled during Pause/Stop must neither block capture nor emit stale boundaries.
- Rotation inside borrowed block must retain every canonical byte exactly once.
- Metadata delayed beyond canonical durability must not reference an uncommitted future.
- Model576-sample tensor uses64 context +512 new samples without moving logical origins.
- Private real build must fail closed on provisioning mismatch; default CI must not ship a fake speech claim.

### Task1: private provisioning and calibration freeze

Files: `tools/vad_runtime/provision.py`, calibration metadata under `docs/evidence/vad-8.4-runtime/`; private fixtures outside Git.
Consumes: custody manifest/verifier at admission HEAD. Produces: verified local inputs and immutable profile JSON.
- [ ] Test wrong-size/hash/inventory/model rejection before consumption; run failures.
- [ ] Retrieve private AAR anew; verify against frozen custody contract; separately hash existing exact model.
- [ ] Generate separate synthetic calibration categories, run exact compute API on POCO, evaluate onset/hysteresis candidates; record decision and hashes.
- [ ] Freeze all profile values and untouched final acceptance definitions; no later retuning.

### Task2: deterministic core and bounded observer

Files: `android/ml/vad-api/` (profile, frame adapter, reducer, rotation planner, observer, scripted test provider).
Interfaces: `VadEngine.probability(FloatArray):Float`, `reset()`, `close()`; frame-indexed observations; immutable metadata events.
- [ ] Write/run failing unit tests for thresholds, pre-roll, unknown/gap, Pause/Resume/Stop, source discontinuity and exact caps.
- [ ] Implement pure reducer and sample adapter, Long frame arithmetic, generation fencing and bounded queues; no wall clock authority.
- [ ] Verify arbitrary block partition equivalence, normalization/context/reset, boundary races and bounded worker shutdown/stall.
- [ ] Run untouched89.5/90/90.5,89.9cancel,1/3/8h and23minute speech/cap tests after profile freeze.

### Task3: actual sherpa provider and local build boundary

Files: `android/ml/vad-sherpa/`, settings/app composition, real-build provisioning script.
Consumes: exact verified local AAR/model; produces provider-neutral engine factory.
- [ ] Test provider API mapping/config/shape/reset/error lifecycle with controlled binding, then implement reflection of exact Java API.
- [ ] Verify local-only explicit build property, pre-compilation identity gate and private assets packaging; CI compiles all source with runtime unavailable, fakes tests-only.
- [ ] Verify product APK exact native/model/NOTICE inventory and no fallback; isolated calibration does not count as product proof.

### Task4: canonical session rotation and observer integration

Files: `RecordingSession.kt`, separate session segmentation coordinator, `RecordingController.kt` composition/drain, capture epoch provenance values/tests.
- [ ] Write failing exact cap/mid-block/multiple-cap and capture-epoch tests against canonical writer.
- [ ] Split before cap, seal existing encrypted units asynchronously, change technical ID independently of capture epoch.
- [ ] Tap accepted borrowed PCM on control drain into bounded observer; no JNI on capture/control thread.
- [ ] Pause/Resume/Stop ordering and stale-callback tests; VAD unavailable/failure/stall cannot affect canonical recording.
- [ ] Compare identical deterministic PCM through enabled/disabled encrypted source, frame counts/order/hashes and overlap view mapping.

### Task5: SQLCipher metadata and recovery compatibility

Files: segmentation metadata port, Room entities/DAO/migration3/schema verifier, journal/vault/access boundary, instrumented migration/recovery tests.
- [ ] Test oldv1/v2 readable/NOT_EVALUATED, transactional idempotence/bounds/order, failure before/after metadata commit.
- [ ] Persist profile and closed events asynchronously only against committed canonical ranges; typed metadata failure without fake completion.
- [ ] Test process death around open speech,89.x,90s,600s/after cap/durability pending; no restored silence/recurrent state, no source duplicates.
- [ ] Run414 Recovery tests and relevant8.1-8.3 regressions; no8.5UX.

### Task6: production physical acceptance and diagnostics

Files: content-free diagnostic measurements and controlled campaign tooling/evidence.
- [ ] Build real locally provisioned product; verify installedAPK/model/native identities.
- [ ] Run untouched controlled speech/silence/noise, before90s cancellation and >90s boundary cases through actual microphone path.
- [ ] Run >10minute continuous recording with exact technical rotation, screen-off, Pause/Resume/notification/Stop and complete saved source.
- [ ] Measure CPU/PSS/FD/threads/inference percentiles/queue/deadlines/capture reads; prove0 unexplained canonical gaps/duplicates.

### Task7: governance, review and publication

Files: exact successor validator/negative controls, mandatory CI stages, SBOM/native inventory, additive stage evidence/status.
- [ ] Preserve inherited forbidden-delta/branch/hash gates while admitting exact implementation paths and no private CI packaging.
- [ ] Formatting/Detekt/lint/strict dependencies/unit/instrumentation/API28/API36/search all mandatory jobs.
- [ ] Independent adversarial review0 unresolvedP0/P1/P2; bounded real-run/Git/CI audit with known canaries.
- [ ] Commit/push stacked DRAFT PR; exact-SHA CI and artifact audit; external receipt only after all gates.
- [ ] Re-read SheetA74:F77; after fullPASS updateC75/D75 only and verify exact neighbors;8.4C/8.5 remainNOT_STARTED.
