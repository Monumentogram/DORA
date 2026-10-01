# Stage 8.1 audio and Recovery integration plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation and a fresh independent review before publication.

**Goal:** Compile and verify product audio ports and adapters over the accepted Recovery implementation, without admitting encrypted persistence runtime or starting Stage 8.2.

**Architecture:** Reuse the sealed microfile publisher and authenticated-prefix reader through a narrow library boundary. Compile the existing Recovery source files directly; do not copy or modify their implementation. Keep the product composition unavailable until the encrypted journal/key/storage composition is admitted in 8.2.

**Spec:** Owner Stage 8.1 instructions, accepted 7.4 security architecture, ADR-0003 through ADR-0007, and the prospective ADR-AUDIO-001 produced by this task.

**Tech stack:** Kotlin/JVM 17, Android minSdk 28, existing pinned Tink 1.23.0, JUnit4.

## Global constraints

- Baseline f3e58b12d0e9353cdb17e986336f23f48f33511c; stage/7-alpha-foundation; PR #87 remains OPEN/DRAFT/UNMERGED; main unchanged.
- Stage 8.1 only. No capture UI/service, microphone, VAD, Cloud calls, Google authentication, SQLCipher migration, signing secrets or signed release.
- Preserve accepted Recovery sources, fixtures and evidence byte-for-byte.
- No persistent plaintext audio or sensitive journal metadata, static keys, unverified finalization or guessed key replacement.
- Product input is the existing PCM S16LE, mono, 16000 Hz profile. Native capture normalization/provenance is explicit and outside this adapter.
- API applicability is the compiled integration boundary; encrypted persistent runtime remains NOT_IMPLEMENTED until 8.2.

## Review focus

1. Authenticated available prefix without an exact finalization record must remain partial.
2. Reordered, substituted or duplicate recording/asset/run identities must fail before exposure or overwrite.
3. A stale successful write or uncertain finalization must not allow retries to silently duplicate audio.
4. Corrupt ciphertext and missing/unavailable keys must remain distinct outcomes.
5. The product factory must never instantiate the API33 PoC journal or a test-only fallback.

## Tasks

- [x] Verify baseline, clean worktree, PR state and existing contracts; independent feasibility review.
- [x] Write ADR-AUDIO-001 with format, identities, frame timeline, finalization, prefix semantics, API compatibility obligations and K12 disposition.
- [x] Add minimal product ports and a source-sharing integration library. Exercise real accepted bootstrap/publication/reconciliation with in-memory test-only encrypted artifacts.
- [x] Add integration and mutation controls for format/frame alignment, identity/order/collision, exact bytes/timeline, finalization, partial/corrupt/key states and composition restrictions. Observe RED then GREEN.
- [x] Add bounded source/provenance and API/static verification; integrate into mandatory CI without weakening existing gates.
- [x] Run full required regressions and fresh-checkout validation. Obtain independent P0/P1/P2 review and resolve findings.
- [ ] Publish one bounded implementation commit (and only necessary evidence finalization), verify exact remote SHA CI, update only the requested Sheet row and read back every changed cell.

## Preflight rulings

The existing API33-only plaintext PoC journal is a reference implementation, not the product journal. Its guard remains unchanged. The required 8.2 encrypted journal must preserve per-connection FULL durability, WAL, foreign keys, disabled automatic checkpointing, atomic unit/manifest insertion, exact schema/readback and fail-closed ownership.

The streaming controller requires a full synthetic plaintext oracle and does not admit ordinary K12 consumption. Select the accepted microfile route, preserving streaming code/tests/evidence. K12 streaming consumer is NOT_APPLICABLE to this product dependency route; it is not retired or declared accepted.

The owner's explicit execution authorization governs this bounded task. Implementation remains in the existing isolated Alpha worktree and within the requested one-commit preference.

## Verification checkpoint

The fresh checkout uses an independent object database (no hardlinks or alternates),
with the exact reviewed-source object fetched from GitHub. It executed 499 JVM
tests (414 Recovery and 20 audio) with no failures/skips, the two VPN suites,
unsigned release graph/SBOM and native/16-KiB checks. A new method-length finding
was corrected by extracting the unchanged rejection mapping; formatting, Detekt
and all audio tests then passed. The second independent clean candidate passed
the full Gradle regression. All 56 Recovery governance self-tests and 245 Python
regression tests passed. The final test-only deterministic corruption correction
passed locally; final candidate checks and external exact remote-SHA CI/readback
are still mandatory. Five early P1 findings were resolved
with regressions; final independent review has no unresolved P0/P1/P2.
