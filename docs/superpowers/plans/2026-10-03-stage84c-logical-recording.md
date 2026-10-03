# Stage 8.4C implementation plan

> Execute inline using superpowers:executing-plans; independent final review.

**Goal:** one logical recording, exact source and authorization scope across chunks.
**Architecture:** derived immutable metadata projection under the existing source
lease; pure recording-scoped consent kernel; no materialized second store.
**Tech stack:** existing Kotlin, JUnit, Room/SQLCipher and Android runtime.
**Spec:** owner Stage 8.4C request and ADR-AUDIO-006.

## Global constraints

Exact baseline e7370bae6ee4d862d2a04e9d52c53bbeb68c6f6f; child draft PR targets
stage/8.4-vad-runtime-integration. 16000 Hz; CAP 9600000 frames; overlap 32000.
OriginalAudioReference unchanged. Source PENDING_FINAL_PUBLICATION. No 8.5,
Cloud, new dependencies, private runtime/model/profile changes or parent rewrites.

## Review focus

- Metadata incomplete or mutated during source binding must never publish partial chunks.
- CAP coincident with Pause or Stop must remain compatible with Stage 8.4 rows.
- Timestamp floor rounding must not turn an overlap frame into another source.
- Retained projections/consent state cannot admit audio after deletion/revocation.
- Historical rows and independent semantic overlap must not be forced into technical pairs.

### Task 1: immutable projection and mapping

Files: core/audio logical package and its JVM tests.
Produces LogicalRecordingProjection.bind(source, rows), immutable references,
typed Ready/NotEvaluated/Incomplete/SourceUnavailable and exact frame mappings.
- [ ] Write/run failing pairing, coverage, malformed, historical, mapping and 1/3/8h tests.
- [ ] Implement validation, defensive immutable lists, stable IDs and two independent axes.
- [ ] Run core/audio and VAD JVM suites; expected all pass.

### Task 2: recording authorization contract

Files: recording authorization kernel and JVM tests; consumes RecordingId only.
- [ ] Test ASK 1/2/10 chunks, mixed events, DEFERRED, ALWAYS, MANUAL and batch 1/8/3.
- [ ] Implement immutable state transitions with no audio authority or network dependency.
- [ ] Run dedicated and existing core tests; expected one recording-level opportunity.

### Task 3: authenticated runtime, restart and physical readback

Files: existing session/vault/coordinator boundary, additive Android tests and crash driver.
Consumes exact source reference; produces atomic projection within withAvailable lease.
- [ ] Test finalized/reopen, stale/deleted/pending source, no rows and process-death phases.
- [ ] Expose projection through authenticated session, preserving all lifecycle fences.
- [ ] Read existing POCO recording before/after restart with exact private identity comparison.
- [ ] Run API28/API36 and historical 414 Recovery; expected no identity drift or resurrection.

### Task 4: publication

Files: additive successor validator, CI normalization, evidence/status and contract.
- [ ] Admit exact bounded delta while preserving every historical sealed check and artifact.
- [ ] Run formatting, Detekt, lint, dependency/SBOM/native and all mandatory tests.
- [ ] Independent review and leak audit; resolve every P0/P1/P2.
- [ ] Commit, push draft child PR, verify mandatory CI on exact SHA and artifact audit.
- [ ] Only after full PASS reread A75:F78, update C76/D76, verify neighbors and external receipt.
