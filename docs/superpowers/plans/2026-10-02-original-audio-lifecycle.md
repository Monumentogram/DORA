# Stage 8.2C original audio lifecycle implementation plan

> Use superpowers:executing-plans inline; independent adversarial review before publication.

**Goal:** Durable exact original-audio references and deletion-safe dependent operations, without Cloud or transcript persistence.

**Spec:** Owner's Stage 8.2C task; frozen ASR v0.1 at 00ce84b, transcript contract/ADR-0018, privacy v0.4 at 75e45a1, ADR-AUDIO-001 and accepted Stage 8.2 at 5af0f28046237a42d2d5b8c51a3b419013d7ee8b.

**Architecture:** Reuse the encrypted catalog's exact finalized ordered source and the existing vault lease, authorization epoch and deletion journal. A v2 additive encrypted reference table retains a canonical digest and permanent-unavailability observation. Existing tombstones remain the deletion authority. Future work uses a guarded callback boundary, never a reusable permission snapshot.

**Tech stack:** Kotlin/JVM17, Room/SQLCipher, Android Keystore, existing Recovery microfile adapter.

## Constraints and decisions

- Branch stage/8.2c-original-audio-lifecycle from the exact accepted parent. Keep PR90/main/history unchanged; final PR draft against stage/8.2-encrypted-persistence.
- Commit only after implementation, local checks and independent review. Source status PENDING_FINAL_PUBLICATION; external receipt alone records subsequent CI/Sheet PASS.
- Reference v1 uses domain-separated, length-prefixed UTF-8 strings and big-endian integers, SHA-256 over owner/vault/recording/asset/session, PCM format and exact ordered unit/physical mapping, generation 1, manifest digests and frames. It never includes a path, provider or model.
- New reference rows are derived from validated finalized catalog state under its lease. Existing v1 rows are admitted lazily after complete authentication; migration never invents valid source authority.
- Permanent source loss is distinct from temporary key/auth access, uncertainty, incomplete capture and explicit user deletion. Terminal evidence cannot be reset by callbacks/restart.
- Deletion retains its existing tombstone-first, key-first path. Reference provenance survives; no transcript/edit/job table or cascade is added.
- No TTL: references and terminal evidence follow the local original-audio/source-provenance lifecycle in the frozen retention authority. Consent and remote deletion remain separate.
- Use synthetic data only; no new dependencies, network, credentials, unencrypted sidecars or historical Recovery edits.

## Review focus

Identity collision and inconsistent ordered mapping; migrated partial/deleting sources; callbacks that outlive authorization; two readers racing deletion; exact source mismatch and attempted callback resurrection. Tests below exercise each condition.

## Tasks

### 1. Exact identity and lifecycle boundary

Files: new OriginalAudioPort.kt and OriginalAudioReferenceCodec.kt; focused JVM tests.

- [ ] Write failing tests for deterministic identity, binding changes, malformed/partial finalization, no provider coupling and typed unavailable states.
- [ ] Implement reference canonicalization and product types. Exact same source yields the same reference; mismatched finalization cannot yield one.
- [ ] Verify focused tests with :core:audio:testDebugUnitTest; expected green.

### 2. Encrypted persistence and migration

Files: AudioJournalSchema.kt, RoomAudioJournal.kt, JournalSchemaVerifier.kt, new migration/reference journal helpers, v2 schema; Android migration tests.

- [ ] Write failing Android tests loading exact exported v1 DDL into SQLCipher with valid audio, pending and deleted fixtures.
- [ ] Add only the reference table/index through explicit MIGRATION_1_2; preserve every existing table and row. Reject tampered v1 DDL before migration.
- [ ] Bind references to exact catalog readback under the existing lease; no uncertain mutation can publish a reference.
- [ ] Verify migration and reference survival on API28/API36; expected green and exact old rows retained.

### 3. Runtime, reads and dependent work

Files: EncryptedAudioVault.kt, product-owned RecoveryAudioBridge.kt, AudioRuntimeCoordinator.kt and new lifecycle controller; JVM/Android tests.

- [ ] Write failing deterministic tests for every required race using latches/fault seams, plus failure preservation, audio-only deletion and late work.
- [ ] Expose acquire/inspect/extract/withAvailable through generation-bound ProductAudioSession. Borrow one vault lease across exact validation and the entire callback. Authorize each plaintext/callback delivery.
- [ ] Persist permanent-loss observations only for exact finalized sources; temporary access failures remain retryable and never delete anything.
- [ ] Verify focused JVM and real encrypted runtime tests; retain text/edit stand-ins and reject source substitution.

### 4. Evidence and publication

Files: bounded lifecycle ADR/status/evidence/validator, test inventory and mandatory CI additions.

- [ ] Run complete JVM suite including 414 Recovery regressions; Stage8.1/8.2 validators and persistence Python suites; formatting, Detekt, lint, strict dependency verification, debug/release graph/SBOM/native checks.
- [ ] Run focused instrumentation plus existing persistence inventory and abrupt process-death phases on API28/API36, audit canaries and record actual runtime matrix.
- [ ] Independent adversarial review; fix and verify all P0/P1/P2 findings.
- [ ] Inventory exact files, commit reviewed changes, push, create/attach draft stacked PR, verify every exact-SHA CI job/artifact.
- [ ] Only after gates pass read C73/I73/J73 and neighbors/format/validation, update exact cells and verify readback. Write external publication receipt. Do not start 8.3.

## Execution ledger

2026-10-02: Parent and frozen contract equality verified; PR90 remains open/draft/unmerged. Existing isolated alpha-foundation checkout was clean and now carries the requested new branch. Owner's explicit execution/publication request governs continuation; defer all commits until verification as required by the task.

2026-10-02 implementation verification: Tasks 1–3 implemented. Full Android build,
formatting, Detekt, lint and strict dependency checks pass; 566 JVM tests pass,
including all 414 historical Recovery regressions. Compiled boundary/release/native
alignment checks pass. Real API28 and API36 x86_64 (4096-byte pages) each pass
the complete 100-test inventory, with no-credential controls before/after, plus
three original persistence and three original-audio abrupt process-death phases.
Bounded runtime log/private-file/receipt canary audits pass. Separate offline
process-death verification asserts no active default network before and after.
No physical ARM/OEM or 16-KiB runtime result is inferred.

Independent adversarial source review and a focused governance follow-up report
0 unresolved P0/P1/P2 over the final 37-path candidate. Initial findings were fixed
with regressions: retryable quarantine is not permanent source loss; multiple assets
for one recording cannot silently select a current source; migration fixtures cover
populated Recovery/quarantine/deletion rows and readable finalized audio. Additive
governance routing admits only the sealed successor, preserving historical checks.

All final local result records, known failed attempts and privacy/runtime receipts
are preserved in the external ORIGINAL-AUDIO-8.2C-20261002 packet. Exact post-commit
CI, artifacts, final review identity and Sheet publication remain separate gates;
this source document does not claim their future result.
