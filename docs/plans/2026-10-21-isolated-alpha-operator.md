# Isolated Alpha operator implementation

The owner-approved scope is a separate Secondary User test package, authenticated readback and exact-recording deletion. This is software readiness only.

## Global constraints

- Base: `1442f0db8a6936920a692913035d100f68871f81`; child branch, stacked draft PR to `codex/stage8-alpha-fast-track`.
- Runtime ID `com.monumentogram.dora.alpha.test`; production and debug IDs unchanged.
- No physical device mutations or historical vault access. Synthetic emulator data only.
- Preserve App Lock, encryption, Recovery, 4,800,000-frame cap and 256,000 outstanding-frame fence.
- No PCM/key export, copied production credentials, cloud additions or release readiness claims.
- Owner user 0 always rejected; explicit existing full Secondary User required.
- Existing four exact-SHA jobs plus isolated-package jobs must pass; independent review has no unresolved P0/P1/P2.

## Task 1: Isolated variant and authenticated in-app operator

Read repository AGENTS.md and relevant contracts/sources completely. Implement a minimal `alphaTest` build type based on release with runtime applicationId suffix `.alpha.test`, no inherited debug suffix, and release library fallbacks. Keep namespace/classes stable. Only this source set includes the operator activity and launcher entry; no exported diagnostic service/provider. Use ordinary App Lock, no bypass. Instrumentation may select this build type explicitly without altering default debug tests.

Implement a secured operator UI requiring an explicitly entered userId, checked against actual process user and UserManager full-secondary properties, rejecting owner/system/managed/restricted/wrong package. Require exact RecordingId with local-vault ownership. Use the existing AndroidProductAudioRuntime auth/session and storage contracts. Full readback validates finished durable canonical ranges, encrypted units, gaps/duplicates/corruption and counts, clearing transient PCM. Emit metadata only. A one-use/session-bound readback result permits explicit deletion of that exact recording through the existing confirmed deletion lifecycle. Verify tombstone, audio artifacts and key references before reporting deletion complete. Do not add any route to arbitrary storage roots or production identities. Invalid or stale authorization/identity must fail closed.

Add focused JVM and real-adapter instrumentation tests with synthetic PCM, including wrong package/user/RecordingId, user 0, managed profiles, stale proof, corrupted/gapped/duplicate data, incomplete durable state, cancellation, deletion ownership and reopen. Keep actual runtime constructors authoritative. Use TDD for behavioral changes, record RED/GREEN. Tests must exercise the operator rather than a parallel implementation. No physical ADB operations. Commit only Task 1 files after checks; report implementation, test commands/results and concerns. Do not spawn subagents.

## Task 2: Host guard, build evidence and future protocol

Implement host-side strict, default-read-only preflight and documented future installation using explicit serial and userId/serial number, exact source/APK identity, existing full-secondary topology and package-code fences. Reject user 0 before ADB. Reject global prior/retained candidate installations unless exact allowed identity is established; no automatic current-user selection. Host tools must not read private vault/audio. Add negative fixture tests.

Create isolated build tooling with separate local signing identity, pinned private AAR/model verification, two clean-build payload comparisons and metadata receipts. Do not change production build tooling's identity pins. Document manifest/DEX/resources/native differences and accepted unavoidable UID/authority differences. Add mandatory isolated API28/API36 operator tests to CI without replacing original four jobs. Add POCO_ISOLATED_ALPHA_PHYSICAL_SMOKE_PROTOCOL with separate functional/historical safety gates and exact future creation/install/cleanup procedures requiring separate owner approval. No physical commands executed here.

## Task 3: Integration, evidence and publication

Run affected JVM, host, UI, encrypted adapter, Recovery and auth tests; API28/API36 operator fixtures and cross-user negative controls. Run format, Detekt, lint, dependency verification, SBOM and native checks. Perform two clean builds with real private VAD input locally, audit outputs, preserve only content-free public evidence. Review the full branch independently, resolve all P0/P1/P2. Publish child branch and stacked draft PR, verify all exact-SHA jobs and final report. Keep PR104 draft/open/unmerged, Sheet unchanged and security/physical/Stage8.6 gates open. Stop before any physical environment creation or APK installation.
