# Alpha security and release preflight implementation plan

Goal: prepare the closed five-minute Alpha software and exact-source release evidence while preserving the OPEN user 0 restoration gate.

Authority: owner task `DORA — ALPHA SECURITY & RELEASE READINESS`, 2026-10-21. Execute inline without further software approval. Starting commit: `d56a64f079d7bf9acf1d548545dfbb9c38858bde`; stacked base `codex/stage8-isolated-alpha-operator`.

Architecture: retain existing fail-closed runtime and debug/release source separation. Add a bounded successor artifact verifier/build procedure and release acceptance documents; preserve historical release contracts. Production identity becomes code 5 / 0.1.0-alpha.3 because read-only package metadata on POCO reports code 4. No installation is authorized.

## Constraints and review focus

- No user 0 launch, storage read, Recovery, marker, PIN, key or package mutation. No physical smoke repetition.
- No merge, rebase, force-push, main changes, Stage9, Cloud or Sheet writes.
- Keep 4,800,000 frames, VAD policy, Stage8.6 NOT_READY and restoration OPEN.
- Reject unexpected manifest permissions/components, debug/test flags, release marker grants, test operator code, signer drift and repeated version codes.
- Preserve signing custody outside Git; local outputs and private dependencies remain local.
- Source/JVM proof and emulator tests do not establish historical-vault continuity or production physical acceptance.

## Tasks

- [x] Audit security source, existing tests and installed version; create ten-control matrix and owner restoration runbook.
- [x] Add successor artifact verification with negative tests; extend release override controls; admit exact successor scope and version without rewriting historical evidence.
- [ ] Run local host/JVM/static checks and exact-SHA API28/API36 CI (4 inherited + 3 isolated + artifact checks).
- [ ] Execute two clean strict production builds, validate package/DEX/manifest/native dependencies, generate SBOM, verify authorized owner-local signing or retain explicit unsigned blocker.
- [ ] Independent whole-branch review, publication audit, commit/push/draft PR; record final results and owner-only next gate.

## Execution ledger

- Native worktree creation returned `Not a git repository` because chat cwd is its parent. Used an adjacent Git worktree; original checkout and #105 unchanged.
- Remote #104/#105 refs matched supplied SHAs. Read-only `dumpsys package com.monumentogram.dora` reports installed code 4/name alpha.2; no application launch or vault access.
- Historical `build_internal_alpha.py` deliberately admits only Stage7 and no microphone. Preserve it and add a successor process.
- Independent reviewer dispatched under requesting-code-review. Runtime review so far finds no confirmed bypass; full result remains pending.
- Baseline strict release build passed after fixing a Windows CRLF checkout transformation of the vendor POM; its original pinned SHA256 was retained. Local Detekt/JVM/test-APK compilation passed after splitting the new matrix helper out of the large instrumentation class.
- Final independent review raised two P2s: bind artifact digest to builder source receipt, and move marker fixture writes after secondary-user admission. Both addressed in successor changes, with stale-source/hash/dirty receipt rejection tests and admission before helper invocation. Candidate outputs from the earlier SHA are not final release evidence.
- Source receipt binding is local build provenance, not a cryptographically signed remote attestation. CI creates its digest receipt immediately after its own build in the same clean exact-SHA job.
