## 2026-10-02 — Stage 8.3 pause/resume latency remediation

Remediation = PENDING_FINAL_PUBLICATION
Historical 8.3 functional gate = PASS (external publication for c596f5256e34bebf308d4c90d89c9f97affc3abb)
Pause latency = P1 OPEN until all physical thresholds pass
8.4 = NOT_STARTED
Stage 8 = IN_PROGRESS
Source checks do not certify the 20-cycle physical timing, exact-SHA CI or publication gates.

## 2026-10-02 — Stage 8.3 product recording

8.3 = PENDING_FINAL_PUBLICATION
Target = PRODUCT_RECORDING_RUNTIME_READY
8.2C = PASS (external publication for a1b9a8a56fe62ceb2332a0e7147ab537f8ce3431)
8.4 = NOT_STARTED
Stage 8 = IN_PROGRESS
Physical POCO microphone, exact-SHA CI, independent review, artifact audit and Sheet readback remain publication gates.

## 2026-10-02 — Stage 8.2C original audio lifecycle

8.2C = PENDING_FINAL_PUBLICATION
Target = ORIGINAL_AUDIO_LIFECYCLE_RUNTIME_READY
8.2 = PASS (external publication for 5af0f28046237a42d2d5b8c51a3b419013d7ee8b)
8.3 = NOT_STARTED
Stage 8 = IN_PROGRESS
Exact-SHA CI, independent review, artifacts and Sheet readback remain publication gates.

## 2026-10-01 — Stage 8.2 encrypted product persistence

8.2 = PENDING_FINAL_PUBLICATION
Target = ENCRYPTED_PRODUCT_PERSISTENCE_RUNTIME_READY
Stage 8.2C = NOT_STARTED
Stage 8.3 = NOT_STARTED
Group C = IN_PROGRESS
Exact-SHA CI, independent review and external publication receipt remain required.

## 2026-10-01 — Stage 8.1 audio Recovery boundary

**8.1 = PENDING_FINAL_PUBLICATION.** The owner-authorized boundary selects the accepted sealed microfile writer and authenticated reader. ADR-AUDIO-001 defines PCM S16LE / 16000 Hz / mono, exact frame time, unique storage-unit/run mappings, physical-segment overlap provenance, typed interruption recovery and exact-source finalization. The new library shares accepted source without modifying it; product persistence remains unavailable pending the separately admitted encrypted 8.2 composition.

**7.4 = PASS / SECURITY_IDENTITY_ARCHITECTURE_FROZEN_FOR_STAGE8. Recovery integration prerequisite = SATISFIED. Group C = IN_PROGRESS. Stage 8.2 = NOT_STARTED. Encrypted product persistence runtime = NOT_ACCEPTED.** This publication is conditional on the complete local verification, fresh checkout, independent review, exact remote SHA CI and exact Sheet read-back. The final external receipt supplies CI and Sheet results without this commit certifying its own execution. Stage 8 as a whole is not PASS.

Historical Recovery, 7.4, release APK/SBOM and device evidence below remain unchanged and source-specific. No real user audio, capture UX/service, VAD, Cloud provider/backend call, product Google authentication, SQLCipher migration, signing-secret access or new signed release is part of 8.1. PR #87 remains OPEN / DRAFT / UNMERGED; main is unchanged. Do not start 8.2 or any later stage automatically.

## 2026-10-01 — 7.4 owner-directed remediation

**7.4 = BLOCKED / PENDING_FINAL_PUBLICATION.** Target result: PASS / SECURITY_IDENTITY_ARCHITECTURE_FROZEN_FOR_STAGE8 only after independent review, fresh remote-object preflight, every mandatory exact-SHA CI step and exact Sheet read-back. The external publication receipt records the final outcome; this commit cannot certify its own CI.

The owner selects Google Sign-In through Credential Manager → verified backend ID token → DORA USER/session. Installation Keystore proof remains a separate mandatory device binding. Mandatory sensitive-content App Lock uses BIOMETRIC_STRONG/system device credential, zero background grace, memory-only foreground unlock and fresh authentication for sensitive operations. Offline local access requires no account/network/GMS. Encryption, server secrets, Recovery, durable revocation and RFC9421/9530 proof remain intact. The current contract explicitly supersedes the earlier identity/optional-lock assumptions; older entries below remain historical.

Failed candidate ee8b3e70217307a3ba110a80b707dcd0a8bc7e3a / CI 36842326571 is retained: android-bootstrap FAILURE, search-smoke SUCCESS. The correction moves the existing exact pinned Recovery fetch before Alpha release validation. No provenance bypass; regressions cover order and empty-object failure. One bounded remediation commit only.

**Stage 8 = NOT_STARTED. Recovery integration prerequisite = SATISFIED. Group C = IN_PROGRESS.** Product security runtime NOT_IMPLEMENTED. After 7.4 closure, the next separately owner-authorized product task is the Stage 8 local recording/storage vertical slice, starting with 8.1/ADR-AUDIO adapters, API28–32 applicability and K12 consumer/retirement disposition. No new gate, Stage 8 READY, real audio/auth/provider operation or signed release is claimed.

## 2026-10-01 — 7.4 Security & Identity architecture

**7.4 = PASS / SECURITY_IDENTITY_ARCHITECTURE_FROZEN_FOR_STAGE8.** The [human contract](security/DORA_SECURITY_IDENTITY_ARCHITECTURE_V0_1.md) and [machine contract](contracts/DORA_SECURITY_IDENTITY_ARCHITECTURE_V0_1.json) freeze one architecture gate. Closure is conditional on independent review, deterministic checks, final exact-SHA mandatory CI and Google Sheet read-back; the final task publication receipt supplies the commit identity without self-reference.

Invited installation proof-of-key remains the accepted Alpha Cloud identity; Google Sign-In is NOT_SELECTED. Local recording/storage stays account/network/GMS independent. App lock, Keystore, encrypted audio/SQLCipher, finite rotating credentials, server-only secrets, redaction, scoped secure screens and key-loss/delete/Recovery boundaries are explicit. All product security runtime = NOT_IMPLEMENTED; no production certification, real credentials/auth/audio, backend/provider calls or new signed release.

**Stage 8 = NOT_STARTED. Recovery integration prerequisite = SATISFIED. Group C = IN_PROGRESS.** Stage 7 and 7.3C remain PASS; 7.3D–G accepted closure is preserved. PR #86 CLOSED / UNMERGED / SUPERSEDED; PR #88 MERGED; PR #87 OPEN / DRAFT / UNMERGED. Main remains 55940df0c95e919a00708ae57e1b8aa23d89b6de; com.monumentogram.dora / 0.1.0-alpha.2 (4) unchanged.

Next product task: a separately owner-authorized Stage 8 local recording/storage vertical slice, beginning with existing 8.1/ADR-AUDIO production storage/extraction adapters, API28–32 applicability and K12 consumer/retirement disposition. These implementation/admission obligations remain open; no Stage 8 READY or runtime acceptance is claimed. Earlier entries below are immutable historical snapshots.

## 2026-10-01 — Recovery replacement integrated; bounded closure publication

**Recovery clean replacement = INTEGRATED; integrated Recovery governance = PASS; Recovery integration prerequisite = SATISFIED.** [PR #88](https://github.com/Monumentogram/DORA/pull/88) is MERGED through the provenance-preserving local no-ff anchor `49595ece8cac22f3a1da4a4d5dd43924ac28f1df`. Its exact ordered parents are `fd943aff885028c6143550ee7bb843798b8105fb` and `3c1986d03321b183efaabfd0f4a86fe42159672e`; its tree `03738da9d0b1beb3bec8cf7779c9f2e2b9f9f188` equals the accepted preparation tree. Implementation `49537b8a53c56a2f07cc1456926fce5c9b903b42` remains unchanged in ancestry. The committed [integration receipt](evidence/recovery-clean-replacement-integration-v0.1.json) preserves all identities and CI history.

The immutable reconciliation remains **124 rows / 42 TRANSFER / 82 EXCLUDE**, with 48 implementation/admission paths and six preparation evidence/status paths. Recovery implementation, transferred blob provenance, historical campaigns, product release graph and historical/preparation governance policies are unchanged. No omitted PR #86 scope has been imported.

Historical [integration CI 36763551078](https://github.com/Monumentogram/DORA/actions/runs/36763551078) remains **FAILURE**: search-smoke succeeded; android-bootstrap failed at Recovery governance; later steps were skipped, not passed or failed. Remediation `d406b6dabb64009cc0a6ee6b017004c757108e26` passed [36773896872](https://github.com/Monumentogram/DORA/actions/runs/36773896872), including all downstream mandatory gates. Its focused evidence remains source-specific: 414 Recovery JVM tests, 30 streaming-source tests, FutureTask publication, descriptor lifetime, escaped-read rejection, exactly-once close and assertion/timeout/read-failure cleanup. Preparation CI 36749820939 and 36752788543 remain PASS.

**Closure governance G2 `358619c68368a21e0ccf4b00e7eb4662617bd00d` passes [exact-SHA CI 36778761263](https://github.com/Monumentogram/DORA/actions/runs/36778761263), android-bootstrap and search-smoke, every mandatory step.** The finite sequence admits only the exact integration anchor, exact remediation, one two-file governance transition, and one direct three-file documentation/evidence finalization; another descendant is rejected. Independent review's two receipt-validation findings were fixed with negative controls; no findings remain. The final documentation commit requires its own exact-SHA CI and external Sheet/PR read-back. Those results must be recorded externally, not self-certified inside this commit.

**PR #86 = SUPERSEDED / CLOSURE_AUTHORIZED**, historically `b951bc454d550e33669ebf4f276a4b09177a99ca`; it is still OPEN / DRAFT / UNMERGED at this publication checkpoint. Owner-authorized comment/closure without merge follows only after final exact-SHA CI and Sheet synchronization/read-back. Historical evidence is valid within its original source-specific scope. PR #87 remains OPEN / DRAFT / UNMERGED and follows the Alpha branch; main remains `55940df0c95e919a00708ae57e1b8aa23d89b6de`.

The live development plan was audited before this publication. Existing Stage 7 identifiers were 7, 7.1, 7.2, 7.2C, 7.2D, 7.2E, 7.3, 7.3C, **7.4 Security & Identity**, then 8. The next unused lettered children 7.3D–7.3G describe the Recovery sequence, without renumbering or replacing 7.4:

| Stage | Work ID | Scope | Publication-checkpoint status |
| --- | --- | --- | --- |
| 7.3D | ALPHA-RECOVERY-7.3D | Clean replacement preparation / PR #88 | PASS / RECOVERY_CLEAN_REPLACEMENT_PR_READY |
| 7.3E | ALPHA-RECOVERY-7.3E | Provenance-preserving integration | INTEGRATED; initial CI FAILURE retained |
| 7.3F | ALPHA-RECOVERY-7.3F | Integrated governance admission | PASS / INTEGRATED_RECOVERY_GOVERNANCE_ADMITTED |
| 7.3G | ALPHA-RECOVERY-7.3G | Bounded closure, final CI, plan synchronization and PR #86 retirement | Final publication gates pending; close only after all pass |

**Stage 7 foundation and 7.3C remain PASS; Group C IN_PROGRESS; Stage 7.4 Security & Identity NOT_STARTED; Stage 8 NOT_STARTED.** Preserve 7.4's independent identity, Google Sign-In/backend validation, app lock/BiometricPrompt, Keystore/local encryption, credential/session lifecycle and server-side secret requirements. Recovery closure does not complete that gate. Full parent POC-RECOVERY-001 and its separate full-plan/device execution gates remain blocked; the satisfied prerequisite is clean replacement integration into Alpha, not product recording/storage implementation.

Product identity remains `com.monumentogram.dora`, **0.1.0-alpha.2 (4)**. Accepted APK/SBOM hashes in the receipt are historical identities, not newly generated signed artifacts. No Stage 8 implementation, Recovery campaign, microphone/audio work, upload, AWS/provider/backend call, signing-secret access, new signed release, repository merge-setting change, rebase, history rewrite or force push occurred.

**Next gate: independently tracked Stage 7.4 and all remaining Stage 8 prerequisites, followed by a separate owner-authorized task. Stage 8 must not start automatically.** Earlier dated entries below are preserved byte-for-byte as historical snapshots; their old PR/branch states are not current-state claims.

## 2026-09-30 — Recovery clean replacement preparation; implementation verified

**PASS / RECOVERY_CLEAN_REPLACEMENT_IMPLEMENTATION_VALIDATED.** Draft [PR #88](https://github.com/Monumentogram/DORA/pull/88) targets exact Alpha `fd943aff885028c6143550ee7bb843798b8105fb`; implementation `49537b8a53c56a2f07cc1456926fce5c9b903b42` passes [exact-SHA CI 36749820939](https://github.com/Monumentogram/DORA/actions/runs/36749820939), both mandatory jobs and every mandatory step. [Reconciliation matrix](stage0/DORA_PR86_CLEAN_RECOVERY_INTEGRATION_V0_1.md) transfers 42 of 124 PR #86 paths and excludes 82; exact source/blob pins and 48 implementation paths are in its machine contract. No wholesale cherry-pick or historical campaign re-execution. Independent source/matrix review has no unresolved P0/P1.

The Future publication race is reproduced with controlled synchronization and fixed by constructing FutureTask before scheduling. Descriptor lifetime, escaped-read rejection and failure/timeout cleanup remain asserted. 479 JVM tests and 38 selected governance tests pass; frozen 7.2C/D/E, deterministic 7.3C, VPN, Stage00, formatting/Detekt, instrumentation compilation, lint, assembly, exact dependencies, release graph/SBOM/native alignment and CI search smoke pass. [Local checks](evidence/recovery-clean-replacement-local-v0.1.json) preserve every unsuccessful attempt, including failed nonmandatory historical blanket discovery and its 22 individually reviewed context failures. [CI](evidence/recovery-clean-replacement-ci-v0.1.json), [Sheet](evidence/recovery-clean-replacement-sheet-v0.1.json) and [closure](evidence/recovery-clean-replacement-closure-v0.1.json) retain bounded evidence. The later docs-only commit still requires its own exact-SHA CI and final Sheet readback, recorded externally to avoid self-reference; this entry does not preclaim that result.

**Stage 7 PASS / ALPHA_FOUNDATION_READY; 7.3C PASS / DETERMINISTIC_CLOUD_CONTRACT_HARNESS_READY; Group C IN_PROGRESS; Stage 8 NOT_STARTED.** Alpha 0.1.0-alpha.2 (4), accepted APK/SBOM, product runtime/release graph and current workflow are unchanged. All ten Recovery execution blockers remain. Main and PR #86 and #87 remain unchanged; all three PRs are open/draft/unmerged. No product Recovery integration, recording/storage implementation, Recovery campaigns, microphone/audio/upload, AWS/provider/backend calls, signing-secret access or new signed release.

**Next gate: separate owner authorization for replacement integration and PR #86 disposition under CLEAN_REPLACEMENT_PR.** An open draft is not an integrated prerequisite. Cross-link/close #86 as superseded without merge only after independent acceptance, exact-source CI, complete omitted-work accounting and separate owner authorization for PR-state changes, preserving its SHA and history. Product extraction/storage route, API28-32 obligation, K12 consumer and ADR-AUDIO remain separate Stage 8 gates. Do not start Stage 8 automatically. Earlier entries below remain immutable historical evidence.

# Dora MVP 1 — Executable Backlog

## 2026-09-30 — 7.3C deterministic Cloud harness; Stage 7 foundation closed

**7.3C = PASS / DETERMINISTIC_CLOUD_CONTRACT_HARNESS_READY.** [Host-only harness specification](stage1/DORA_ALPHA_CLOUD_CONTRACT_HARNESS_V0_1.md), ADR-0020 and the machine-readable catalogue compose frozen 7.2C/D/E through current authorization → bounded bytes → provider observation/result → immutable transcript and guarded selection. 62 synthetic scenarios and 35 harness tests prove zero unauthorized accepted bytes, cumulative ceilings, reconciliation before retry, one logical winner, and late/manual/edit/tombstone fences. Two independent processes produce identical results/counters/trace digests. No backend or provider runtime follows.

Implementation `7899d3eb134b549258316ae9369459afa77857a3`, parent `1e34b9d7fe4a7f4aa43095244d75bbe3e1b49018`, passes [exact-SHA CI 36731600770](https://github.com/Monumentogram/DORA/actions/runs/36731600770): android-bootstrap, search-smoke and every mandatory step. [Local checks/review](evidence/alpha-7.3c-local-v0.1.json), [CI](evidence/alpha-7.3c-ci-v0.1.json), [scenarios](evidence/alpha-7.3c-scenarios-v0.1.json), [Sheet readback](evidence/alpha-7.3c-sheet-v0.1.json) and [closure](evidence/alpha-7.3c-closure-v0.1.json) retain evidence. Five Important and one Minor independent-review findings were corrected; no unresolved P0/P1. 219 Python tests and 401 JVM tests pass locally; CI search smoke passes 7 tests. The later docs-only HEAD requires its own exact-SHA CI and final PR/Sheet reconciliation, retained in the publication receipt to avoid self-reference.

**Stage 7 = PASS / ALPHA_FOUNDATION_READY. Group C = IN PROGRESS. Stage 8 = NOT_STARTED.** The Stage 7 DoD is satisfied for the admitted internal foundation: installable reproducible build, minimal modules/required contracts, CI/dependencies/architecture and applicable migration checks (no product persistence schema yet). This does not claim working recording, Cloud ASR flow or a user-ready Alpha. DORA Alpha **0.1.0-alpha.2 (4) remains UNCHANGED / REQUALIFICATION_NOT_REQUIRED**; the 7.3 signed APK/SBOM and POCO 2→4 evidence remain immutable. No new signed APK or Android source change.

**Next separate roadmap task: Stage 8 preparation. Recovery clean replacement is required before Stage 8 recording/storage acceptance. Do not execute automatically.** Recording/VAD/audio storage/transcript persistence/merge/auth/backend/object-storage/AWS-adapter runtime NOT_IMPLEMENTED; Recovery integration/audio upload NOT_RUN; harness real network/provider/audio NOT_USED; AWS NOT_CALLED, spend 0; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Main unchanged; PR86 untouched; PR87 remains open/draft/unmerged. Earlier dated entries below remain historical.

## 2026-09-30 — 7.3 internal alpha.2 release verified

**7.3 = PASS / INTERNAL_ALPHA_APK_BUILD_CI_DEVICE_READY.** [Release contract](stage1/DORA_ALPHA_INTERNAL_RELEASE_7_3_V0_1.md) binds clean implementation `351874fff41774f10298e8a186bdc78bf6bb720f`, [exact-SHA CI 36720820501](https://github.com/Monumentogram/DORA/actions/runs/36720820501), recoverable owner signer, actual locked release graph/CycloneDX 1.6 SBOM, byte-identical signed clean builds and trusted physical POCO upgrade 2→4. Current product: `com.monumentogram.dora`, **0.1.0-alpha.2 (4)**; historical code 2/3 facts remain unchanged.

[Backup restore](evidence/alpha-7.3-signing-backup-v0.1.json): NEW_ENCRYPTED_BACKUP_CREATED_AND_RESTORE_VERIFIED; encrypted offline copy, matching certificate/private-key signing proof and temporary cleanup PASS. [Signed build/device](evidence/alpha-7.3-build-device-v0.1.json): 22,760,139-byte APK, SHA `ad13ddbd2b01e4e61112ecfb889408576dd04748b9e65627abd6700d1440fa57`; same-source repeat BYTE_IDENTICAL. POCO M5 Android14/API34 keeps UID 10306 and firstInstallTime; cold launch, visible version, pulled APK identity PASS. Code4 remains installed. [SBOM](evidence/alpha-7.3-sbom-v0.1.cdx.json): 107 external runtime coordinates and 3 local modules; no library upgrades; SHA `230fbb099d99fee5a2dd1f698cddeeac722de83ea0e0be0cb51583f26da165ba`. Signed manifest/native16K/secret checks PASS; migration **N/A / NO_PRODUCT_PERSISTENCE_SCHEMA_YET**.

[Local checks](evidence/alpha-7.3-local-v0.1.json): 203 Python and 401 JVM tests, VPN host/loopback, lint/format/detekt/build/boundaries PASS; CI search-smoke 7 tests PASS. Review's runtime file-dependency inventory issue fixed RED→GREEN; deferred minor: mocked full builder publication-orchestration test. [CI receipt](evidence/alpha-7.3-ci-v0.1.json) explicitly preserves 14 stale API step entries and reconciles them with completed successful job logs; no mandatory step skipped. [Sheet readback](evidence/alpha-7.3-sheet-v0.1.json) and [closure](evidence/alpha-7.3-closure-v0.1.json) retain evidence. The evidence-only HEAD requires its own exact-SHA CI and final current-HEAD Sheet/PR readback, retained in the post-publication receipt to avoid self-reference.

**Stage 7 / Group C IN PROGRESS. Next separate task: 7.3C, NOT_STARTED. Stage 8 NOT_STARTED.** Recording, VAD, product audio storage, transcript persistence, merge, auth/backend/object storage/AWS adapter runtime NOT_IMPLEMENTED. Recovery integration/audio upload NOT_RUN; real product audio NOT_USED; AWS NOT_CALLED, spend 0 by this task; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Main unchanged; PR86 untouched; PR87 open/draft/unmerged. No automatic downstream execution. Earlier dated entries remain historical.

## 2026-09-30 — 7.2E Cloud identity, authorization and ownership contract closed

**7.2E = PASS / CLOUD_IDENTITY_AUTHORIZATION_OWNERSHIP_CONTRACT_READY.** [Versioned logical contract](stage1/DORA_ALPHA_CLOUD_IDENTITY_AUTH_OWNERSHIP_CONTRACT_V0_1.md), ADR-0019 and the closed JSON catalogue separate installation principal, verified credential evidence, recording/source/job ownership, current consent and operation-specific authorization. Exact-source upload authority requires validated issuance and current decisions, immutable generation-bound evidence and cumulative byte limits. Retries resolve the prior exact context and reauthorize; ASK/batch transitions preserve prompt history and deferred records. ADR-0009/0011 and existing retention, provider, transcript and Local/offline semantics remain intact. No auth technology is selected and no runtime is implemented.

Implementation `29c9f7da7f9a41e001c61c8f6ebd93bda9eaba85`, parent `88d203ea364981db7917b3d7285078f9de16266f`, passes [exact-SHA CI 36708561117](https://github.com/Monumentogram/DORA/actions/runs/36708561117), both required jobs and every mandatory step. [Local checks/review](evidence/alpha-7.2e-local-v0.1.json), [CI](evidence/alpha-7.2e-ci-v0.1.json), [Sheet readback](evidence/alpha-7.2e-sheet-v0.1.json) and [closure](evidence/alpha-7.2e-closure-v0.1.json) retain evidence. 82 new tests and 68 existing related tests pass; independent review's one P1 and two P2 findings were reproduced and fixed. No unresolved P0/P1 or deferred minor findings. The later evidence-only HEAD requires its own exact-SHA CI and final Sheet HEAD readback, recorded in the task publication receipt.

**Stage 7 and Group C IN PROGRESS. Next separate task: 7.3 — Сборка, CI и проверка внутреннего APK, NOT_STARTED.** 7.3C prerequisites 7.2C–E are satisfied; 7.3C and Stage 8 NOT_STARTED. Auth/backend/token issuance/login/ownership/consent/authorization/upload/object storage/AWS adapter, transcript persistence, merge and recording runtime NOT_IMPLEMENTED. Recovery integration/audio upload NOT_RUN; real audio NOT_USED; AWS NOT_CALLED, spend 0 by this task; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Recovery clean replacement remains separate before Stage 8 acceptance. Main unchanged; PR #86 untouched; PR #87 open/draft/unmerged. No automatic next-stage execution. Earlier dated entries remain historical.

## 2026-09-30 — 7.2D transcript versioning and edit contract closed

**7.2D = PASS / VERSIONED_TRANSCRIPT_AND_EDIT_CONTRACT_READY.** [Versioned logical contract](stage1/DORA_ALPHA_TRANSCRIPT_VERSIONING_EDIT_CONTRACT_V0_1.md), ADR-0018 and the closed JSON catalogue define immutable LOCAL/CLOUD/MERGED versions, separate edit history and recording-wide revisions, source/segment/time/context anchors, explicit alignment/proposals, per-edit conflict resolutions and guarded active selection. Parent/edit-base ordering prevents causal cycles; processing history survives cancellation; historical and new decisions preserve user authority. No physical database, persistence or merge algorithm is implemented.

Implementation `3ad8f297a72b64eb0997e986ed8d817bede23cf7`, parent `55ae8aef51527d87b719369b9e55feaf189674ff`, passes [exact-SHA CI 36701576520](https://github.com/Monumentogram/DORA/actions/runs/36701576520), both required jobs and every mandatory step. [Local checks/review](evidence/alpha-7.2d-local-v0.1.json), [CI](evidence/alpha-7.2d-ci-v0.1.json) and [Sheet readback](evidence/alpha-7.2d-sheet-v0.1.json) retain evidence. 44 new tests pass; all three Important and one Minor independent review findings were reproduced and fixed. 7.2C mojibake hygiene = FIXED / NO_SEMANTIC_CHANGE. The later evidence-only HEAD requires its own exact-SHA CI and final Sheet HEAD readback, recorded in the task publication receipt.

**Stage 7 and Group C IN PROGRESS. Next separate task: 7.2E — Cloud identity, auth and recording/job ownership, NOT_STARTED.** 7.3/7.3C and Stage 8 NOT_STARTED. Physical DB/Room/SQLite/server DB, DAO/repository/migrations, merge algorithm, transcript persistence, auth/ownership, recording and Cloud/AWS adapter runtime NOT_IMPLEMENTED. Recovery integration/audio upload NOT_RUN; real audio NOT_USED; AWS NOT_CALLED, spend 0 by this task; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Recovery clean replacement remains separate before Stage 8 acceptance. Main unchanged; PR #86 untouched; PR #87 open/draft/unmerged. Earlier dated entries remain historical.

## 2026-09-30 — 7.2C replaceable Cloud ASR provider contract closed

**7.2C = PASS / REPLACEABLE_CLOUD_ASR_PROVIDER_CONTRACT_READY.** [Versioned contract](stage1/DORA_ALPHA_CLOUD_ASR_PROVIDER_CONTRACT_V0_1.md), ADR-0017, closed JSON types and a separate evidence-bound Amazon Transcribe profile freeze the server-side provider interface. Original audio, DORA identities, provider namespace/provenance, normalized results/timestamps, errors and reconciliation-before-retry are explicit. Android RecognitionPort and runtime code are unchanged. Unknown model/cancellation/timestamp properties remain unknown; final 6.2D owner-only admission is the sole current provider authority.

Implementation `5c3ce1f0044f29582e2dc99283293b32468e2a9c`, parent `b380fb352d20fa62ed2de3a4fae3b05e51b86f5a`, passes [exact-SHA CI 36695084857](https://github.com/Monumentogram/DORA/actions/runs/36695084857), both required jobs and all mandatory steps. [Local checks/review](evidence/alpha-7.2c-local-v0.1.json), [CI](evidence/alpha-7.2c-ci-v0.1.json) and [Sheet readback](evidence/alpha-7.2c-sheet-v0.1.json) retain evidence. Independent review's two Important findings were reproduced and fixed before publication; 24 new tests pass. The subsequent evidence-only HEAD requires a separate exact-SHA CI, recorded in the task publication receipt to avoid a self-referential commit.

**Stage 7 and Group C IN PROGRESS. Next separate task: 7.2D — Versioned transcript storage and edit anchors, NOT_STARTED.** 7.2E, 7.3/7.3C and Stage 8 NOT_STARTED. Backend/worker/adapter/Android Cloud API, persistence/auth/ownership runtime NOT_IMPLEMENTED. AWS NOT_CALLED, spend 0 by this task; no audio; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Recovery integration NOT_RUN; clean replacement remains separate before Stage 8 acceptance. Main unchanged; PR #86 untouched; PR #87 open/draft/unmerged. Earlier dated entries remain historical.

## 2026-09-30 — 7.2 minimal application contracts closed

**7.2 = PASS / ALPHA_MINIMAL_MODULES_AND_DATA_CONTRACTS_READY.** [Versioned contracts](stage1/DORA_ALPHA_MINIMAL_MODULES_DATA_CONTRACTS_V0_1.md) and ADR-0016 define typed opaque IDs, independent capture/storage/recognition state axes, generic asynchronous ports, typed failures and a JVM-testable app coordinator. Existing UI is wired to explicit UNAVAILABLE production ports. Only `:app` and `:core:model` changed; no new modules or dependencies. Exact implementation `9ee5d718348fc0682ae94e16a5a20ecd75e706c4`, parent `0492ff34a44a6b5becdc232b16c2f3f99de87456`, passes [Android CI 36683669682](https://github.com/Monumentogram/DORA/actions/runs/36683669682), both required jobs and every mandatory step. [Local checks](evidence/alpha-7.2-local-v0.1.json), [CI](evidence/alpha-7.2-ci-v0.1.json) and [Sheet readback](evidence/alpha-7.2-sheet-v0.1.json) retain evidence.

**Stage 7 and Group C IN PROGRESS; next separate task: 7.2C — Replaceable Cloud ASR interface and adapter contract, NOT_STARTED.** 7.2D/E and 7.3 NOT_STARTED; Stage 8 NOT_STARTED. Recording/storage/Cloud ASR runtime NOT_IMPLEMENTED; FIRST_REAL_PRODUCT_AUDIO NOT_READY. 7.1 identity/version/signing remain unchanged. No AWS or Recovery integration; PR #86 untouched, main unchanged, PR #87 open/draft/unmerged. Recovery clean replacement remains separately required before Stage 8 recording/storage acceptance. Earlier dated entries remain historical.

## 2026-09-30 — 7.1 PASS; next implementation task is 7.2

**7.1 = PASS / ALPHA_IDENTITY_SIGNING_INSTALL_READY**, limited to OWNER_ONLY_CLOSED_INTERNAL_ALPHA. [Versioned decision/runbook](stage1/DORA_ALPHA_IDENTITY_SIGNING_INSTALL_V0_1.md) freezes `com.monumentogram.dora`, version 2 / `0.1.0-alpha.1`, PROJECT_OWNER key custody outside Git and local signed APK distribution. POCO M5 install/reinstall/launch, same-key code 2→3 upgrade and wrong-key rejection are proven. Exact implementation `4a2350a02eba8adc76c04bf250e7769513ad9aa7` passes [Android CI 36677470351](https://github.com/Monumentogram/DORA/actions/runs/36677470351), both required jobs. S01-ID-001/S01-RELEASE-001 are satisfied only for this internal Alpha scope; their historical production/store gates remain outside this decision.

**Stage 7 and Group C IN PROGRESS; next: 7.2 — Минимальные модули и контракты данных, NOT_STARTED.** Do not execute automatically. Recovery reconciliation decision is CLEAN_REPLACEMENT_PR; implement its clean replacement separately before Stage 8 recording/storage acceptance. PR #86 untouched; no merge, AWS, recording/storage/ASR product-flow implementation or Cloud runtime readiness. Existing downstream gates remain unchanged.

## 2026-09-29 — 6.3 closed; first implementation task is 7.1

The [versioned 6.3 decision](stage0/DORA_ALPHA_DEVELOPMENT_ADMISSION_6_3_V0_1.md) admits `OWNER_ONLY_CLOSED_INTERNAL_ALPHA` implementation from exact baseline `92f00f7dd4a18a3d4b2fdd159fdaef86fa3f8699`, branch `chat/alpha-asr-runner-scope`, after full exact-source Android CI PASS. **6.3 = PASS / DEVELOPMENT_ADMISSION_AND_EXACT_BASELINE_FROZEN; STAGE_0 = PASS / ALPHA_IMPLEMENTATION_ADMITTED.** No implementation is performed by this decision.

**First next: 7.1 — Идентификатор, подпись и установка alpha (READY / NEXT).** Establish the internal application identity, signing custody outside Git and installation route under the existing 7.1 criteria. Then follow Stage 7 foundation → 8 recording/storage/privacy → 9 ASR/versioning → 10 local history/lexical search → 11 offline/reconnect/delete → 12 integrated Alpha acceptance. Stage 18 backend/control-plane dependencies must close at their actual predecessor points; this high-level order cannot defer a pre-audio or pre-integration gate.

Future task **PR86-INTEGRATION-RECONCILIATION**: independently compare accepted Recovery implementation/evidence with the frozen Alpha baseline and decide merge as-is, rebase/reconcile, selective transplant or a clean replacement PR. This task is not executed or decided here; PR #86 is outside the baseline and remains OPEN / DRAFT / UNMERGED.

All 28 downstream Cloud gates retain their existing 3 OPEN / 25 NOT_RUN dispositions. Cloud runtime NOT_IMPLEMENTED; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Product authorization, consent/deletion ledgers, immutable transcript versions, canonical media preflight and provider-independent contracts require implementation evidence. Diagnostic harnesses are not production backend code. The owner-only provider scope, EN measured limitation and mandatory second-speaker revalidation are unchanged. Earlier entries remain historical.

## 2026-09-29 — 6.2D closed; separate 6.3 is next

The [technical closure result](stage0/DORA_CLOUD_62D_DUPLICATE_NAME_CLOSURE_RESULT_V0_1.md) records **6.2D = PASS / OWNER_ONLY_CLOSED_INTERNAL_ALPHA_PROVIDER_ADMITTED**. Local preflight/typed negative-case handling and the new two-Start valid duplicate-name collision satisfy the current product boundary. Exact Output CMK, cleanup, retirement, independent review and cost bounds passed; historical raw technical verdicts are unchanged. No owner quality recording or WER was rerun.

**Next: 6.3 — Development admission and exact baseline = ELIGIBLE_TO_START / NEXT; NOT_RUN.** Execute only under a separate task. EVALUATION and PROVIDER are satisfied for owner-only closed internal Alpha; ADMISSION stays BLOCKED until that separate step. Effective39: 6 general satisfied + 2 existing scoped + 2 owner-only scoped + 3 OPEN + 1 BLOCKED + 25 NOT_RUN. External Legal is separate.

Retain RU 17/179 = 9.4972% measured PASS and EN READ 37/181 = 20.4420% historical benchmark FAIL, with only the existing OD-62D-EN-01 owner acceptance. Revalidation is mandatory before a second real speaker. EN spontaneous/noise/timestamp accuracy and external/customer/public admission remain outside the proven scope. Diagnostics are 11/12; this task authorizes no further Start. Reserved total USD9.996384/10 and ASR USD0.541500/2 do not claim actual billed charges.

## 2026-09-29 — Resolve observed technical case failures before admission

The [terminal technical result](stage0/DORA_CLOUD_62D_OWNER_TECHNICAL_RESULT_V0_1.md) records one synthetic smoke PASS and 13 required technical Starts across 12 cases, with no new owner quality Starts. Raw technical oracles were not all PASS (tech-empty, tech-near_empty, tech-truncated, tech-wrong_format, tech-missing_s3, tech-duplicate_name, tech-permission_denial); append-only adjudications are shown separately from those raw outcomes. The technical suite is **NOT_PASS**. Exact task content cleanup and stack retirement were verified; both task CMKs are pending deletion, not physically deleted. The owner-only English acceptance decision does not waive these technical results.

**6.2D = BLOCKED / REQUIRED_TECHNICAL_CASES_NOT_SATISFIED.** EVALUATION and PROVIDER remain OPEN; broad ADMISSION remains BLOCKED; 6.3 is NOT_RUN / NOT_ELIGIBLE. The frozen 39 gates remain 6 SATISFIED + 2 scoped SATISFIED + 0 PARTIALLY_SATISFIED + 5 OPEN + 1 BLOCKED + 25 NOT_RUN. Historical RU 17/179 (9.4972%) PASS and EN 37/181 (20.4420%) FAIL against 18% remain unchanged. OD-62D-EN-01 accepts the EN limitation for the Project Owner only; it does not establish provider admission. Reserved total USD9.9685 stays within USD10; ASR reservation USD0.5385 stays within USD2. Actual invoiced charges are not observed. The shared diagnostic ledger moved from 8 to 9/12; 13 technical Starts are counted separately.
Exact CloudShell task-content copies were purged after preserving private evidence.

Do not rerun the eight owner quality jobs for a best-of result. Address the exact technical case evidence under a new reviewed protocol; retain all failed and incomplete outcomes.

## 2026-09-29 — 6.2D measured owner result: quality blocker and incomplete technical evaluation

The [bounded result](stage0/DORA_CLOUD_62D_OWNER_BOUNDED8_MEASURED_RESULT_V0_1.md) and [aggregate evidence](evidence/cloud-6.2d-owner-bounded8-result-v0.1.json) close this campaign without another dispatch. Eight owner primary jobs completed and were scored: RU total normalized WER **17/179 = 9.4972% PASS** against 20%; EN READ **37/181 = 20.4420% FAIL** against 18%. Both required four-clip latency diagnostics and eight transcript timestamp-structure audits passed. The frozen composite recipe hash mismatch stopped generation before Put; all four composite objects were absent and **0/13 planned technical Starts** ran. Do not promote technical cases to PASS or infer broad provider quality from eight clips.

All task jobs and bucket objects were cleared, the CloudFormation stack and its 13 resources reached `DELETE_COMPLETE`, both CMKs are pending deletion, and nine exact CloudShell WAV transport copies plus eleven output/evidence-transfer copies were removed after preserving the private archive locally. The shared diagnostic ledger remains **8/12**; four historical owner failures remain separate, and there were no automatic retries. Actual charges are not measured. **6.2D is BLOCKED / MEASURED_PROVIDER_QUALITY_FAIL**; technical evaluation is independently `INCOMPLETE_HARNESS_DEFECT`. `CLD-ADM-EVALUATION-001`/`CLD-ADM-PROVIDER-001` remain OPEN, owner-only and broad admission NOT_ESTABLISHED, 6.3 NOT_RUN / NOT_ELIGIBLE, and all 39 frozen gate statuses unchanged (6 SATISFIED, 2 scoped SATISFIED, 0 PARTIALLY_SATISFIED, 5 OPEN, 1 BLOCKED, 25 NOT_RUN).

Next authorized work requires a new explicit owner decision and prospective protocol: investigate the measured EN READ WER miss and separately repair/review the composite recipe encoding before any new technical campaign. Do not repeat primary jobs for a best-of result, reset diagnostic accounting, change the frozen v0.3 result, or start 6.3 from this outcome.

## 2026-09-29 — 6.2D bounded owner Phase A v0.3; next authorized sequence

The prospective [Phase A v0.3](stage0/DORA_CLOUD_EVALUATION_PHASE_A_BOUNDED8_V0_3.md) and [machine record](contracts/DORA_CLOUD_EVALUATION_PHASE_A_BOUNDED8_V0_3.json) bind the same eight authorized owner clips, fresh no-DataRole B1/B2 synthetic PASS and cleanup, original rejected role-based B1 with cause UNPROVEN, the exact target/source receipts and USD9.4370 reservation. Eight of 12 shared engineering diagnostic Starts are already consumed. The Owner's task-specific exception omits `JobExecutionSettings` only for this bounded evaluation; ADR-0011's general Alpha design and historical records are unchanged.

Next: commit, push and independently refetch the exact v0.3 JSON/Markdown; save the private publication proof before any resumed owner WAV upload or Start. Recheck effective Transcribe opt-out, frozen task configuration, owner authority and each exact WAV at dispatch. Then run eight primaries sequentially, generate four composites only after all eight, run the 12 planned technical cases within 13 Start dispatches, prove cleanup and publish a separate result record. Unknown Start outcomes, budget/deadline drift or changed scope stop the affected action without automatic retry. **6.2D remains BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED** pending real measurements; EVALUATION/PROVIDER OPEN, admission and 6.3 BLOCKED/NOT_RUN, and 39 gates unchanged. No broader Alpha, Android, Recovery, PR86 or main work is authorized by this milestone.

## 2026-09-28 — 6.2D Phase A prospective protocol; live blocked

Owner-authorized 6.2D thresholds are prospectively frozen in [Phase A v0.1](stage0/DORA_CLOUD_EVALUATION_PHASE_A_V0_1.md)
and its [machine record](contracts/DORA_CLOUD_EVALUATION_PHASE_A_V0_1.json). Source baseline:75e45a152800c9c1589db201fc71fec9a6200fef.
**6.2D = BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED**. **Phase A = PARTIAL / PROTOCOL_FROZEN_DATA_AUTHORITY_AND_LIVE_OPERATOR_UNBOUND**.
Real-speech admission: **BLOCKED / CLOUD_DATA_AUTHORITY_NOT_PROVEN**, RU0/EN0. Owner confirms AWS access is not prepared.

RU normalized WER<=20%; EN<=18%, independently by required slice; raw WER also reported.
Noisy/speakerphone<=35%, with no invented LOW_CONFIDENCE waiver. Exact-reference timestamp start/end median<=500ms,p95<=1500ms.
Batch service turnaround=(completion-observed minus submit)/audio duration, p50<=1x,p95<=2x; <=60s files absolutep95<=120s.
Nearest-rank quantiles and complete attempt accounting are frozen. Mixed-language and diarization remain excluded/deferred by6.1.
Primary input only mono PCM16LE16000Hz WAV, <=600s. Amazon Transcribe standard batch/eu-central-1/ru-RU/en-US is a candidate, not admitted.
Current official Frankfurt standard-batch priceUSD0.0001/s (USD0.006/min); budget ceilingUSD10. No AWS spend incurred by this task.

Seven generated non-speech fixtures and26synthetic host tests cover scoring and the shared terminal interface for AWS-shaped/fake adapters.
No live transport is installed/bound; no live quality, timing, latency, errors/limits or cleanup result exists. Mocks cannot close PROVIDER.
All34scope capabilities are dispositioned; no extra AI provider/function admitted. Existing Common Voice Local permission is not Cloud authority.
Exact real-corpus/timing manifest, live client/journal/cleanup binding and effective account controls must be frozen in a successor PhaseA,
pushed/refetched before any live test. Preserve v0.1 and every later benchmark; never amend thresholds from observed results.

EVALUATION OPEN; PROVIDER OPEN; ADMISSION BLOCKED. Unchanged39 counts:
6 SATISFIED +2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA +0 PARTIALLY_SATISFIED +5 OPEN +1 BLOCKED +25 NOT_RUN =39.
11.1C scoped PASS, PRIVACY/CONTROL scoped satisfaction and RETENTION satisfaction remain unchanged.
PRIVACY-LEGAL-EXTERNAL-001 remains separate mandatory future governance outside39, never waived by internal approval.
6.3 NOT_RUN/BLOCKED; runtime NOT_IMPLEMENTED; first real user audio NOT_READY. Android, Recovery, Stage5 and PR86 untouched; no merge/main change.
Next: Cloud-authorized RU/EN/timing data and test AWS access, then an execution-ready prospective PhaseA successor. Earlier entries below are historical.


## 2026-09-28 — 11.1C closed internal Alpha governance amendment

Effective only for `CLOSED_INTERNAL_ALPHA`: **11.1C = PASS / PRIVACY_RETENTION_CONTROL_PREREQUISITES_SATISFIED_FOR_CLOSED_INTERNAL_ALPHA**. Authority: Owner-approved OD-11C-23..26 in [v0.4](design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.md), [machine record](contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.json), [ADR-0013](adr/ADR-0013-closed-internal-alpha-expert-review-governance.md) and prospective [expert-review governance v0.1](design/DORA_INTERNAL_ALPHA_EXPERT_REVIEW_GOVERNANCE_V0_1.md). Baseline `7e05a74984a2cfbac3790b1f963b392ef7870fd8`. Earlier entries below are historical and retain their original status/evidence.

Independent AI-assisted Privacy/Legal review = `APPROVED_FOR_CLOSED_INTERNAL_ALPHA_WITH_CONDITIONS`; explicit Owner risk/scope acceptance = `APPROVED_BY_PROJECT_OWNER`. This supplies project governance only, not attorney/DPO opinion, statutory certification, regulator/external-audit/public-release approval. Frozen criterion5 historically required qualified scope approval; it is expressly amended for effective internal governance, never silently rewritten or declared satisfied by an external reviewer.

| Item | Current disposition |
|---|---|
| PC-01 | BLOCKED_EXTERNAL_APPROVAL → RESOLVED_BY_INTERNAL_ALPHA_GOVERNANCE |
| PC-02 / RT-01 / RT-02 / SC-01 | RESOLVED, unchanged |
| SC-02 | BLOCKED_BY_PRIVACY_ONLY → RESOLVED |
| CLD-ADM-PRIVACY-001 | SATISFIED_FOR_CLOSED_INTERNAL_ALPHA |
| CLD-ADM-RETENTION-001 | SATISFIED; v0.3 56-cell matrix and ADR-0012 unchanged |
| CLD-ADM-CONTROL-001 | SATISFIED_FOR_CLOSED_INTERNAL_ALPHA; ADR-0011 architecture unchanged |
| CLD-ADM-EVALUATION-001 / CLD-ADM-PROVIDER-001 | OPEN |
| CLD-ADM-ADMISSION-001 / 6.3 | BLOCKED by EVALUATION/PROVIDER; execution NOT_RUN |
| 6.2D | ELIGIBLE_TO_START / NEXT; execution NOT_RUN, not PASS |
| 6.1 / 6.2 | PASS, unchanged |

Hard population boundary: Owner and invited informed internal Alpha participants with freely given explicit recording/Cloud-test opt-in only. Customer/production calls, non-Alpha third-party or non-agreeing voices, public/anonymous users, hidden/passive recording and employee surveillance are outside scope. Existing mic-only explicit Start and synthetic-data authority remain. Live disclosure is disabled; all12 OD-11C-26 checks are NOT_RUN. Actual operator/contact, jurisdiction, lawful basis/recording duties, AWS contract applicability and usable rights/DSR handling (also after credential loss) are checked before real audio. `GDPR_REFERENCE_FRAMEWORK_WHERE_APPLICABLE`, not a universal applicability claim.

**Mandatory future gate: `PRIVACY-LEGAL-EXTERNAL-001` = `DEFERRED_MANDATORY_PRE_EXTERNAL_USE`** (canonical qualification status `DEFERRED_TO_PRE_EXTERNAL_USE`; residual `DEFERRED_MANDATORY_BEFORE_EXTERNAL_USE`). Owner commissions a qualified external reviewer BEFORE the earliest of: first external/non-internal Alpha user; first real customer production-like recording; conversations involving non-Alpha third parties; public beta; public release; material provider change; processing-region change; material processing-purpose expansion; significant new personal-data category; any legal/contractual circumstance explicitly requiring professional qualification. Verify actual operator/controller, participant jurisdictions, lawful bases, roles, AWS contract, transfers, final notice, participant rights, DSR process and market recording/communications law. NOT_RUN, not COMPLETED. This gate cannot be waived by internal governance, and qualification requirements may apply before internal use; unknown applicability blocks the affected operation pending facts. Full MVP/public governance is unchanged.

39 frozen gates: **6 SATISFIED + 2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA + 0 PARTIALLY_SATISFIED + 5 OPEN + 1 BLOCKED + 25 NOT_RUN = 39**. Internal normalized count:8/0/5/1/25, with the two scope-qualified promotions retained explicitly. The future external gate is separate governance, not a fortieth frozen gate. Frozen v0.1, privacy v0.1/v0.2/v0.3 and historical 6.1/6.2 evidence are preserved. No Android/runtime/AWS API/audio/inference/device/Recovery/PR#86 changes; no 6.2D/6.3 execution, main update or merge. Next task only: **6.2D — Cloud provider evaluation and technical admission**; no automatic execution.


## Current 11.1C retention closure — 2026-09-28 — v0.3

OD-11C-13..22 are APPROVED_BY_PROJECT_OWNER. [Current retention package](design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_3.md), [machine contract](contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_3.json) and [ADR-0012](adr/ADR-0012-alpha-retention-periods-and-provider-copy-limits.md) supersede only the enumerated retention interpretations of OD-11C-04/08/10. All prior text below is historical where it differs; v0.1/v0.2 and ADR-0011 remain preserved. Baseline `99976ce21a5ee2fde408e1bee78b12902a675b81`.

56/56 lifecycle cells classified: DORA-controlled audio hard 24h with explicit immediate safe success cleanup; transient output immediate after ingestion / failed 24h; ordinary metadata 30d, audit 90d, active consent/identity plus90d; non-audio restorable backup maximum 30d; guarded tombstone 120d after logical deletion. Hidden AWS copies use actual documented purpose/deletion controls and accepted limitations, never an invented physical 24h guarantee. No audio backup/cross-region replica or resurrection. Local defaults and DEC-017 stay unchanged.

`RT-01 = RESOLVED`; `RT-02 = RESOLVED`; `CLD-ADM-RETENTION-001 = SATISFIED`. `PC-02 = RESOLVED` and `SC-01 = RESOLVED` remain accepted. `PC-01 = BLOCKED_EXTERNAL_APPROVAL` is unchanged; `SC-02 = BLOCKED_BY_PRIVACY_ONLY`, depending only on PC-01 / PRIVACY. `CLD-ADM-PRIVACY-001 = BLOCKED`; `CLD-ADM-CONTROL-001 = BLOCKED`; EVALUATION/PROVIDER OPEN; ADMISSION BLOCKED.

Effective 39 gates: 6 SATISFIED, 0 PARTIALLY_SATISFIED, 5 OPEN, 3 BLOCKED, 25 NOT_RUN. 6.1/6.2 PASS; 11.1C PARTIAL; 6.2D/6.3 NOT_RUN and blocked. Remaining 6.3 predecessors: PRIVACY, CONTROL, EVALUATION, PROVIDER. No runtime/Android/Recovery/PR86 work, AWS runtime calls, audio upload or inference. Next 11.1C prerequisite only: qualified Privacy/Legal actual-scope approval PC-01, then SC-02 dependency readback; not automatically started.

---

## Current 11.1C owner/AWS reassessment — 28 September 2026

Source baseline: `cccf85f982436af0bf9675d738dfe2dd61308842`. [Current policy v0.2](design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.md),
[contract v0.2](contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.json) and
[ADR-0011](adr/ADR-0011-alpha-transcribe-privacy-retention-key-custody.md) apply **OD-11C-01..12 = APPROVED_BY_PROJECT_OWNER**.
This is the sole current successor to immutable v0.1; older sections below are historical snapshots.

**11.1C = PARTIAL / OWNER_DECISIONS_APPLIED_PC02_SC01_RESOLVED_RESIDUAL_PRIVACY_AND_RETENTION_PREREQUISITES**.
PC-02 (current candidate AWS fact package) and SC-01 (accepted reviewed bounded architecture) are RESOLVED.
Remaining: PC-01 BLOCKED_EXTERNAL_APPROVAL; RT-01/RT-02 PARTIALLY_RESOLVED;
SC-02 BLOCKED_BY_FROZEN_CRITERION (PRIVACY/RETENTION dependencies).

Closed internal invitation-only Alpha; one region eu-central-1; Amazon Transcribe candidate only.
Local original until explicit deletion, automatic retention OFF. Cloud audio immediate post-success cleanup
and <=24 hours maximum; durable DORA transcript separate. Explicit customer S3 input/output cleanup,
no long-term/archival/cross-region audio copies or deleted-audio versions; mandatory effective Transcribe opt-out.
Opt-out effective-policy/account/runtime verification NOT_RUN. No E2EE or 24-hour provider-internal erasure guarantee.
90-day job-record automatic expiry is not a mandatory minimum: terminal jobs must be deleted earlier.

Qualified privacy/legal actual-scope approval remains PC-01. RT-01 now concerns only undocumented internal-provider
audio deadline compatibility and enumerated DORA log/record/failed-output periods; RT-02 concerns non-audio copy/
restore horizons and tombstone/receipt retirement. No broad absent-owner-approval or quality benchmark blocker retained.
Accepted RDY-012/013 architecture is documented; applicable RDY-011/013/018 cross-gate residuals remain explicit.

PRIVACY BLOCKED; RETENTION PARTIALLY_SATISFIED; CONTROL BLOCKED. EVALUATION OPEN; PROVIDER OPEN; ADMISSION BLOCKED.
39-gate totals unchanged: 5 SATISFIED / 1 PARTIALLY_SATISFIED / 5 OPEN / 3 BLOCKED / 25 NOT_RUN.
6.1/6.2 PASS; SCOPE/GAPS SATISFIED; AWS SELECTED_ALPHA_PLATFORM; AWS_TECHNICAL_ADMISSION NOT_RUN;
6.2D BLOCKED / NOT_RUN; 6.3 BLOCKED / NOT_RUN; FIRST_REAL_AUDIO_ADMISSION NOT_READY;
CLOUD_RUNTIME NOT_IMPLEMENTED; CLOUD_ALPHA_ACCEPTANCE NOT_RUN. Frozen 6.2C and historical acceptance unchanged.

Next: only PC-01 qualified scope approval, RT-01 exact remaining period/provider evidence and RT-02 non-audio
restore/ledger horizon; then SC-02 dependency reconciliation. No next task, runtime, device campaign or merge started.


## Current 11.1C policy/design evidence — 27 September 2026

[Policy/design package](design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.md) and
[machine-readable evidence](contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.json)
bind the accepted repaired baseline `5015e1cd7db7c37315dba41503f6962e3db82408`.

**11.1C = PARTIAL / DESIGN_PACKAGE_COMPLETE_WITH_BLOCKING_APPROVAL_AND_POLICY_DECISIONS.**
Data-flow inventory, RU/EN unavailable-state disclosure, 56 artifact/holder lifecycle cells,
installation/credential/key-custody and consent/deletion-ledger proposals, and four RDY risk
dispositions exist. They supply no qualified Privacy/Legal/Security approval or runtime proof.

| Gate | Before | Current | Exact remaining evidence |
|---|---|---|---|
| CLD-ADM-PRIVACY-001 | BLOCKED | BLOCKED | PC-01 qualified actual-scope approval; PC-02 provider/service/location/terms/subprocessor/data-use fact pack |
| CLD-ADM-RETENTION-001 | PARTIALLY_SATISFIED | PARTIALLY_SATISFIED | RT-01 approved periods/triggers; RT-02 backups/replicas/receipt coverage and suppression horizon |
| CLD-ADM-CONTROL-001 | OPEN | BLOCKED | SC-01 accepted bounded design/credential parameters/key-custody ADR; SC-02 PRIVACY and RETENTION dependencies |

Existing local retention remains until explicit deletion; automatic retention OFF, numeric catalog
unavailable. No Cloud TTL, legal entity, region, qualified signature or identity provider invented.
The user-visible 0.1 notice has no enabled Cloud grant action; actual profile/approval gaps block it.
New design choices remain proposals pending SC-01; no owner DEC/ADR is silently superseded.

Effective 39-gate counts: **5 SATISFIED / 1 PARTIALLY_SATISFIED / 5 OPEN / 3 BLOCKED /
25 NOT_RUN**. Only CONTROL changes status relative to repaired 6.2. EVALUATION and PROVIDER
remain OPEN; ADMISSION remains BLOCKED and is the output of 6.3. All five 6.3 predecessors remain.
6.1/6.2 remain PASS; SCOPE/GAPS remain SATISFIED. Frozen 6.2C and 6.2 matrix/evidence are unchanged.
AWS SELECTED_ALPHA_PLATFORM; AWS_TECHNICAL_ADMISSION NOT_RUN; FIRST_REAL_AUDIO_ADMISSION NOT_READY;
CLOUD_RUNTIME NOT_IMPLEMENTED; CLOUD_ALPHA_ACCEPTANCE NOT_RUN; 6.2D and 6.3 BLOCKED.

**Next task: 11.1C owner/qualified Privacy-Legal-Security review of PC-01/02, RT-01/02,
SC-01/02 with scope-specific documentary evidence.** This package starts no further task.
Runtime/device tests NOT_RUN / NOT_REQUIRED; Recovery/Stage5/PR86 unchanged; no implementation,
campaign, AWS API, real audio or merge. Publication requires one docs-only commit, push, exact
remote re-fetch and existing Sheet changed-cell readback; final task report supplies the receipt.

Earlier sections below remain historical snapshots, including their former next-step wording.

## Current Alpha readiness 6.2 — 27 September 2026

[Versioned disposition](stage0/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.md),
[machine matrix](contracts/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.json) and
[current additive gate evidence](evidence/alpha-readiness-6.2-closeout-v0.1.json)
supersede only the prospective current state in earlier snapshots below.

`6.1 = PASS / ALPHA_SCOPE_AND_SUPPORTED_ENVIRONMENT_FROZEN` remains accepted.
`6.2 = PASS / ALPHA_READINESS_GAPS_DISPOSITIONED` and `CLD-ADM-GAPS-001 = SATISFIED`
close disposition only, conditional on this atomic docs commit being pushed, exact remote HEAD
confirmed and all changed Sheet cells read back. Final task report and Sheet carry that receipt.

Capture: reuse bounded Samsung observations; clean 60-minute screen-off/POCO evidence required
at 8.6, with format/lifecycle/measurement decisions before affected code. VAD: reuse host mechanics;
artifact/profile/governed-corpus admission before 8.4, physical acoustic/realtime proof at 8.4/8.6.
Search: reuse 10k/1M mechanics/observations; product dependency/schema/protocol admission before
10.2, actual-build correctness/latency/storage/update and version/deletion acceptance at 10.2/10.3C.
Battery: comparator only; capture-first scheduling/protocol before affected code, physical
capture/VAD energy at 8.6 and integrated energy/thermal before 12.1/12.4. Offline is split into
recording, preservation, deferred state, persistent queue, reconnect, unauthorized upload,
optional package, installed Local ASR, no-account/no-GMS and OS/background evidence at 8/9/11/12.
No unresolved item is waived or assigned an unspecified future date; see exact matrix gates.

Recovery remains `0D.6 = ALPHA CLOSED / FULL OPEN`, reused without a rerun; product integration
is separately gated. Stage 5 remains `PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED`,
optional exact POCO/Small q8_0 scope; timestamp quality NOT_EVALUABLE, actual 16KiB runtime NOT_RUN.
All historical PoC DONE/INCONCLUSIVE/FAIL/BLOCKED/TODO and frozen 6.2C bytes remain unchanged.

`AWS = SELECTED_ALPHA_PLATFORM`; `AWS_TECHNICAL_ADMISSION = NOT_RUN`;
`FIRST_REAL_AUDIO_ADMISSION = NOT_READY`; `CLOUD_RUNTIME = NOT_IMPLEMENTED`;
`CLOUD_ALPHA_ACCEPTANCE = NOT_RUN`; `CLOUD_IMPLEMENTATION_ADMISSION = NOT_READY`.
`6.2D = BLOCKED`; `6.3 = BLOCKED`. Remaining 6.3 predecessor gates are PRIVACY, RETENTION,
CONTROL, EVALUATION and PROVIDER (all `CLD-ADM-...-001`); ADMISSION is the output of 6.3.
Only GAPS changes relative to 6.1: **5 SATISFIED / 1 PARTIALLY_SATISFIED / 6 OPEN /
2 BLOCKED / 25 NOT_RUN**. All first-audio gates and all 14 Cloud exits remain mandatory.

**Next task: 11.1C policy/design — resolve CLD-ADM-PRIVACY-001, CLD-ADM-RETENTION-001
and CLD-ADM-CONTROL-001 prerequisites.** Then separately 6.2D and 6.3. Nothing is started here.
Stage 6 / Group B remain in progress. No runtime/device campaign, Android/runtime source edit,
main change, PR #86 operation or merge. Runtime/device tests = `NOT_RUN / NOT_REQUIRED`.

Earlier dated sections below are preserved historical snapshots, including their old next-step
wording. The matrix is the explicit prospective first-Alpha applicability/sequencing overlay;
it does not pass the broader full-MVP PoCs or authorize implementation before 6.3.

## First Alpha scope 6.1 and AWS owner selection — 27 September 2026

[Scope MD](product/DORA_ALPHA_SCOPE_V0_1.md), [scope JSON](contracts/DORA_ALPHA_SCOPE_V0_1.json),
[owner decision](stage0/DORA_AWS_SELECTED_FOR_ALPHA_OWNER_DECISION_V0_1.md), [ADR-0010](adr/ADR-0010-first-alpha-scope-and-aws-platform.md) and
[current gate evidence](evidence/alpha-scope-6.1-closeout-v0.1.json) are the current prospective authority.

`6.1 = PASS / ALPHA_SCOPE_AND_SUPPORTED_ENVIRONMENT_FROZEN` and
`CLD-ADM-SCOPE-001 = SATISFIED` (specification evidence only). Publication closure requires
this atomic docs commit to be pushed, exact remote HEAD confirmed and the existing Alpha Sheet
updated/read back; the final task report and Sheet are the exact-commit closure receipt.

`AWS = SELECTED_ALPHA_PLATFORM`: owner-approved one-platform Alpha selection after
`18.1A = PASS / MARKET_AND_PRICING_DATASET_READY` and
`18.1B = PASS / TCO_AND_ALPHA_PROVIDER_ECONOMICS_READY` (owner-confirmed independent review;
live Sheet evidence is referenced in the owner record). Some multi-provider choices have lower
raw cash TCO; AWS is not claimed cheapest overall. Provider-neutral ASR/diarization/LLM/embeddings
and practical storage/queue/job boundaries remain required; AWS-specific types stop at adapters.

IN: user-started foreground microphone recording with Pause/Resume/Stop, durable original audio,
accepted bounded Recovery and VAD/chunk contracts, RU/EN Cloud ASR, offline recording, consent,
transcript/version/provenance, timestamps only in proven scope, edits, original-audio reprocessing,
local history/lexical search, explicit active-version export, deletion/privacy and Cloud safety controls.
OPTIONAL: exact bounded Stage 5 Local candidate; no forced Local install for Cloud-only.
DEFERRED: speaker/diarization flows, protocol, decisions, tasks, summaries, LLM, embeddings/semantic
search, voice biometrics, connectors, team sync, billing and public release. Stages 13–19 remain;
Stage 18 Cloud services/operations are necessary before Stage 12 Alpha acceptance.
Support: POCO M5 / Android 14 API 34 / arm64-v8a / RU and EN only. Mixed RU/EN is not guaranteed;
minSdk 28 is compatibility only; Stage 5 timestamps NOT_EVALUABLE and actual 16KiB runtime NOT_RUN.

`AWS_TECHNICAL_ADMISSION = NOT_RUN`; `FIRST_REAL_AUDIO_ADMISSION = NOT_READY`;
`CLOUD_RUNTIME = NOT_IMPLEMENTED`; `CLOUD_ALPHA_ACCEPTANCE = NOT_RUN`.
6.2D remains BLOCKED / technical admission NOT_RUN; exact AWS service/configuration/model/region
remain unadmitted. 11.1C remains the privacy/retention/control policy-design prerequisite.
6.3 remains BLOCKED by GAPS, PRIVACY, RETENTION, CONTROL, EVALUATION and PROVIDER (all
`CLD-ADM-…-001`); ADMISSION is its own output. Effective gate counts: 4 SATISFIED, 1 PARTIAL,
7 OPEN, 2 BLOCKED, 25 NOT_RUN. Only SCOPE changes; frozen 6.2C and historical evidence remain intact.

**Next task: 6.2 — close remaining Alpha readiness/gap disposition.** Not executed here.
Stage6/Group B stay in progress; no implementation, real Cloud audio, new campaign, Recovery,
main change, PR #86 change or merge. Earlier dated entries below are historical snapshots and
retain their original next-step/provider wording; the current overlay supersedes only prospective status.


## Alpha Cloud admission gates 6.2C — 27 September 2026

[Human contract](contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.md) and
[machine matrix](contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json) freeze the complete gate set.
`6.2C = PASS / CLOUD_ALPHA_ADMISSION_GATES_FROZEN` is the closure result only after this atomic
docs commit is pushed, remote HEAD confirmed, and the existing Sheet updated/read back for it.
Until that external receipt exists, stage closure is pending; contract approval is not runtime admission.

Cloud implementation admission = **NOT READY / BLOCKED BY OPEN ADMISSION GATES**.
First real Cloud audio admission = **NOT READY**. Cloud Alpha acceptance = **NOT_RUN / NOT READY**.
Provider/model = **NOT SELECTED**. Cloud runtime = **NOT IMPLEMENTED**; runtime tests = **NOT_RUN**.

6.3 remains BLOCKED. Minimum predecessor blockers (all prefixed `CLD-ADM-`, suffixed `-001`):
SCOPE (6.1), GAPS (6.2), PRIVACY and RETENTION (11.1C policy), CONTROL (11.1C/BE-AUTH design),
EVALUATION and PROVIDER (live 6.2D per-function admission). ADMISSION is 6.3's own output.
Full Alpha runtime tests are subsequent acceptance gates, not prerequisites to writing code.
Policy/design work in later-numbered ownership lanes precedes 6.3; implementation follows it.

Immediate next task: **6.1 — formal Alpha scope/support decision** (`CLD-ADM-SCOPE-001`), required
to determine applicable gaps and per-function 6.2D admission. Do not execute it in 6.2C.
The live Sheet's newly present 6.2D is retained: ASR plus any other AI actually included by 6.1
needs its own admitted provider/model or explicit local/deferred disposition; no single vendor default.
CLOUD-01 stays closed for boundary selection; CLOUD-02 stays blocked; CLOUD-03 stays NOT_RUN.

Stage 5 remains PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED, exact POCO scope only.
Recovery, PR #86 and historical evidence are unchanged. Earlier dated sections below are historical
snapshots; this section supersedes their prospective “6.2C next” planning, not their measurements.

## Alpha Cloud execution boundary 6.1C — 27 September 2026

[ADR-0009](adr/ADR-0009-alpha-cloud-execution-boundary.md) records the owner-approved
`DORA_CONTROLLED_BACKEND_SELECTED` decision. Boundary selection no longer blocks planning;
commit/push and existing Sheet update/readback are required for stage closure, subject to
independent verification. This selects no provider/model and admits no Cloud implementation.

| Alpha roadmap item | Dependency / next action | Status |
|---|---|---|
| 6.2C | Define Cloud readiness/admission gates against accepted ADR-0009 and 6.1A/B; retain bounded Stage 5 limitations. | PLANNED |
| 7.2C | After 6.3, define the server-side CloudAsrProvider port and adapter contract under ADR-0009 and 6.1B. | NOT STARTED |
| 7.2E | After 6.3 and BE-AUTH-001, define DORA identity/ownership/consent enforcement under ADR-0009; local core stays account-free. | NOT STARTED |
| 9.2C | After 7.2C–E, 8.4C, 18.1C and 11.1C, implement authorized original-audio upload to DORA-controlled storage under ADR-0009. | NOT STARTED |
| 18.1C | After readiness/admission, privacy/auth/retention gates and separate provider admission, implement DORA backend/storage/worker and provider adapter under ADR-0009. | NOT STARTED |

Resolve only CLOUD-01 boundary selection with published evidence; preserve its history.
11.1C/CLOUD-02 and 12.1C/12.4C runtime acceptance remain unresolved. Immediate next gate:
`6.2C — Cloud readiness / admission gates`; it is not started here. Historical entries below
remain unchanged; Cloud runtime is NOT IMPLEMENTED.

## ASR Stage5 terminal closeout 5.6D.3 — 27 September2026

Stage5 `PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED`; POC-ASR-001 `PASS / BOUNDED_ALPHA_ONLY`.
Small q8_0 `PASS / ACCEPTED_FOR_BOUNDED_ALPHA_ON_POCO_M5`. One campaign invocation, 48 primary attempts, zero retries/replacements;
RU 24/24 (36/191 = 18.848168%, PASS), EN 24/24 (31/333 = 9.309309%, PASS).
Cleanup VERIFIED; no historical rescoring, tuning, threshold/normalizer/runtime change or next model campaign.
5.6D.1 data PASS and5.6D.2 admission PASS remain immutable. All48 new cases are consumed.
Recovery0D.6 and PR#86 unchanged. Previous entries below remain byte-identical.
Next: Stage6 may consume this bounded POCO admission while preserving all limitations; do not generalize it to production.
See [terminal report](stage0/DORA_ASR_SMALL_Q8_STAGE5_CLOSEOUT_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-small-q8-stage5-closeout-stage0-v0.1.json).

## ASR SMALL q8 admission and operator source 5.6D.2 — 27 September 2026

`PASS / SMALL_Q8_ARTIFACT_INIT_OPERATOR_READY`. Exact small q8_0 artifact264464607bytes, static479tensors,
one model download, one init SUCCESS, one no-model lifecycle hold/cancellation probe; cleanup VERIFIED.
ASR/whisper_full0, corpus/reference transfers during init0, measured campaign invocations0.
Exact accepted ARM82 reuse and16KiB packaging checks PASS; actual device pages4096, actual16KiB runtime NOT_RUN.
98 targeted host tests (15 new), Stage00 checks7/7 and Gradle197tasks PASS.
Operator SOURCE ready. Immutable execution envelope is sealed only AFTER this operator commit and pinned in5.6D.3;
it is not claimed frozen here. Stage5 remains NOT_PASS; POC-ASR-001 BLOCKED / NOT_READY pending measured gates.
Historical results, Recovery0D.6 and PR#86 unchanged. Previous entries below remain byte-identical.
See [5.6D.2 report](stage0/DORA_ASR_SMALL_Q8_ADMISSION_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-small-q8-admission-stage0-v0.1.json).

## ASR expanded holdout 5.6D.1 — 27 September 2026

`PASS / UNTOUCHED_EXPANDED_HOLDOUT_AVAILABLE`. Owner-authorized new RU CV27 test + retained EN SPS5 train:
eligible RU10326/EN1112, provider participant keys RU2233/EN224. Frozen exactly RU24+EN24;
all48 immediately consumed, known historical source/audio/provider-key overlap0.
Cross-corpus real-world participant equality UNKNOWN; no identification or inferred mapping.
Exact references bound before unchanged normalization; all selected normalized counts>=1.
No model download/freeze, device operation or inference in this data phase.
Stage5 remains NOT_PASS pending the already-authorized q8 artifact/init/operator/campaign closeout.
Historical results, Recovery0D.6 and PR#86 unchanged. Previous entries below remain byte-identical.
See [5.6D.1 report](stage0/DORA_ASR_EXPANDED_HOLDOUT_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-expanded-holdout-stage0-v0.1.json).

## ASR train holdout availability audit — 26 September 2026

`BLOCKED / INSUFFICIENT_UNTOUCHED_HOLDOUT_AFTER_TRAIN_AUDIT`.
Same retained SPS5.0 release/archives, owner-authorized provider train only: complete RU0/EN1569;
after all exclusions RU0/EN1112, eligible participants RU0/EN224. Empty provider split is not train.
All144 historical sources/audio digests and55 participants remain excluded, including unexecuted selections.
No ranking, selection, holdout manifest, q8_0 experiment freeze, inference, device execution or download.
Prospective reference rule remains NORMALIZED_REFERENCE_TOKEN_COUNT >=1 with the unchanged normalizer.
Historical ARM82 RU73/349=20.916905% VALID_FAIL; EN3/24 NOT_EVALUABLE; BASE/SMALL unchanged.
Stage5 remains NOT_PASS; POC-ASR-001 BLOCKED / NOT_READY. Recovery0D.6 and PR#86 untouched.
Next: STOP; Project Owner must separately authorize any further data-authority work preserving every isolation rule.
No q8_0 freeze or execution follows from this audit. Historical entries below remain byte-for-byte intact.
See [train audit report](stage0/DORA_ASR_TRAIN_HOLDOUT_AVAILABILITY_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-train-holdout-availability-stage0-v0.1.json).

## ASR ARM82 quality forensics — 26 September 2026

`BLOCKED / INSUFFICIENT_UNTOUCHED_HOLDOUT`. Read-only reconciliation preserves RU24/24,
73/349=20.916905% FAIL (integer gate maximum69, four errors over); EN3/24 and aggregate resources NOT_EVALUABLE.
Accepted ASR-SMALL-ARM82-01 VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE remains unchanged.
Errors concentrate in P1 (63/73); EN04's bracket-only raw reference normalizes empty.
Prospective eligibility now requires NORMALIZED_REFERENCE_TOKEN_COUNT >=1 using the exact frozen normalizer before ranking.
All144 selected records/audio digests and55 participants are excluded, including all unexecuted selections.
Same admitted dev authority leaves RU0/EN137: no new holdout selected, no successor experiment frozen.
Small q8_0 is the conditional next artifact preference only. No inference, rerun, tuning, threshold/model/decoding change.
Stage5/GroupB remain NOT_PASS; POC-ASR-001 BLOCKED / NOT_READY. Historical sections below remain intact.
Next: owner-authorized narrow data-source/partition expansion while preserving participant isolation and corrected eligibility.
See [forensic report](stage0/DORA_ASR_ARM82_QUALITY_FORENSICS_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-arm82-quality-forensics-stage0-v0.1.json).


## ASR-SMALL-ARM82-01 measured terminal — 26 September 2026

`INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED`.
Candidate: `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`. One API invocation, 28 primary attempts,
zero retries; RU 24/24, EN 3/24; cleanup VERIFIED.
Quality FAIL; resources NOT_EVALUABLE.
Owner-authorized data revision uses the same exact SPS5.0 dev partition for both languages:
eligible RU51/EN408 after all exclusions, selected RU24/EN24, zero prior participant/source/audio overlap.
Source/materialization/operator/binary identities were frozen before inference.
Exact ARM82 build and preflight PASS; unchanged SMALL q5_1, CPU4, decoding and thresholds.
The old test-only blocker and historical SMALL VALID_FAIL remain unchanged.
ASR-SMALL-ARM82-01 is now TERMINAL / MEASURED; Stage5/GroupB NOT_PASS;
POC-ASR-001 BLOCKED / NOT_READY. Historical 5.6C.2B remains INCOMPLETE.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 OPEN, DRAFT, UNMERGED, untouched.
Next: Assess the RU quality rejection and normalized-empty reference stop, then prospectively freeze one next candidate experiment and reference-eligibility correction on a new untouched holdout; do not rerun this campaign.
See [measured result and revised data authority](stage0/DORA_ASR_SMALL_ARM82_01_MEASURED_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/asr-small-arm82-01-measured-stage0-v0.1.json).
All earlier dated sections remain historical evidence.


## ASR-SMALL-ARM82-01 execution — 26 September 2026

`BLOCKED / INSUFFICIENT_FRESH_HOLDOUT_AFTER_PARTICIPANT_EXCLUSION`.
Physical POCO M5 ISA admission PASS: ASIMD, FPHP, ASIMDHP and ASIMDDP present.
Exact admitted source/test metadata leaves at most RU 8 and EN 69 after verified exclusions;
RU has at most 9 records before other eligibility checks after lawful prior-participant exclusion.
The frozen minimum is 24 per language. No selection/materialization, ARM82 build or campaign
was performed; API invocations 0/1, attempts/retries/inference 0, evaluator not invoked.
Candidate disposition NOT_FORMED; quality/resources NOT_EVALUABLE. Probe cleanup VERIFIED.
This is a proven frozen data-contract blocker, requiring a prospective owner data-authority
revision before execution can resume; do not waive exclusions or substitute cases here.
Historical SMALL remains VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE.
ASR-SMALL-ARM82-01 BLOCKED / NOT_EXECUTED; Stage 5/Group B NOT_PASS;
POC-ASR-001 BLOCKED / NOT_READY; 5.6C.2B/5.6C.2 measured completion remains INCOMPLETE.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 OPEN, DRAFT, UNMERGED, untouched.
See [execution blocker report](stage0/DORA_ASR_SMALL_ARM82_01_EXECUTION_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/asr-small-arm82-01-execution-stage0-v0.1.json).
All earlier dated sections remain historical evidence.


## ASR SMALL resource postmortem and next experiment — 26 September 2026

`PASS / SMALL_RESOURCE_FAILURE_ASSESSED_NEXT_EXPERIMENT_FROZEN`.
All 19 retained attempts analyzed read-only. The decisive RU attempt 19 is the shortest
audio and fastest absolute inference: evidence supports systemic short-input RTF pressure,
not an isolated latency stall. Historical SMALL remains VALID_FAIL for its frozen resource gate.
Selected exactly one next experiment: `ASR-SMALL-ARM82-01`, same multilingual SMALL q5_1,
whisper.cpp v1.9.4 rebuilt for `armv8.2-a+fp16+dotprod`, CPU-only, 4 threads;
unchanged decoding/full context, normalizer/oracle and quality/resource thresholds.
Require a new untouched RU24+EN24 holdout excluding all BASE48 and consumed SMALL48.
Next: implement the isolated successor binding/build, materialize/preflight/freeze,
then one separately authorized physical POCO M5 campaign. No configuration search.
This task performed zero ASR inference, device execution, holdout reruns or tuning.
5.6C.2B/5.6C.2 measured completion remain INCOMPLETE; next experiment FROZEN / NOT_EXECUTED.
Stage 5/Group B remain NOT_PASS; POC-ASR-001 BLOCKED / NOT_READY.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 OPEN, DRAFT, UNMERGED, untouched.
See [postmortem and prospective contract](stage0/DORA_ASR_SMALL_RESOURCE_POSTMORTEM_NEXT_EXPERIMENT_STAGE0_V0_1.md)
and [decision evidence](evidence/poc-asr-001/asr-small-resource-postmortem-next-experiment-stage0-v0.1.json).
All earlier dated sections remain historical evidence.


## ASR 5.6C.2B measured SMALL terminal result — 26 September 2026

`5.6C.2B = INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED`.
Frozen candidate disposition: `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE`.
One real v0.3 API invocation; 19 primary attempts;
RU 19/24 and EN 0/24 completed;
zero retries. Quality NOT_EVALUABLE; resources FAIL.
Decisive resource failures: RTF_MAXIMUM.
The unchanged frozen evaluator produced the disposition; host-only preparation
corrections changed no model/data/source semantics, gates or attempt policy.
Frozen cleanup: VERIFIED; marker/journal/terminal and private audit evidence retained.
5.6C.2 measured completion is INCOMPLETE; 5.6C has a terminal outcome.
Stage 5 and Group B remain NOT_PASS; POC-ASR-001 remains BLOCKED / NOT_READY.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 stays OPEN, DRAFT, UNMERGED, untouched.
Next: Assess the measured resource rejection and decide a separately scoped, prospectively frozen candidate/runtime experiment. Do not rerun this campaign or tune on this holdout.
See the [measured campaign report](stage0/DORA_ALPHA_ASR_SMALL_MEASURED_CAMPAIGN_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-measured-campaign-stage0-v0.1.json).
All earlier dated sections below remain historical evidence.


## ASR 5.6C.2A.3 private storage ACL diagnosis — 26 September 2026

`5.6C.2A.3 = PASS / PRIVATE_STORAGE_BOUNDARY_READY`.
The preceding v0.3 call stopped before its marker with PRIVATE_STORAGE_ACL_INVALID:
one API call, zero primary attempts, no journal/terminal aggregate, evaluator not
reached, quality/resources NOT_EVALUABLE, candidate disposition NOT FORMED.
Diagnosis identifies ACL_QUERY_FAILURE at the acceptance-root query: a Windows
PowerShell module-loading failure through the inherited Python environment.
The admitted protected owner-only acceptance root and all 111 descendants pass.
Process-local module-path isolation restores the unchanged v0.3 storage check.
Existing ACL changes: 0; one new empty future work boundary has its own protected
owner-only DACL. v0.3 is retained byte-identical; no v0.4 is created.
233 applicable host tests and Stage00 pass; 16 campaign-API tests were excluded.
All actual campaign/content/model/device counters in this task are zero.
**5.6C.2B remains BLOCKED / NOT_AUTHORIZED.** POC-ASR-001 stays BLOCKED / NOT_READY;
Stage 5 and Group B are not PASS. Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN;
PR #86 remains OPEN, draft, unmerged and untouched. See the
[ACL remediation report](stage0/DORA_ALPHA_ASR_SMALL_PRIVATE_STORAGE_ACL_REMEDIATION_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-private-storage-acl-remediation-stage0-v0.1.json).



## ASR 5.6C.2A.2 execution-capable successor freeze — 26 September 2026

`5.6C.2A.2 = PASS / EXECUTION_CAPABLE_SMALL_SUCCESSOR_OPERATOR_FROZEN`.
The additive v0.3 operator composes unchanged v0.2 metadata authority with unchanged
v0.1 one-shot measurement/evaluation semantics. Its seven-field locator config
forbids caller bindings and SHA overrides; cases are derived from pinned metadata.
Future execution requires `OWNER_AUTHORIZED_5.6C.2B_V03` and a separately reviewed
commit/owner decision. The token in source is not authorization; CLI is information-only.

37 generated v0.3 tests, 136 SMALL host tests, 249 ASR host tests and Stage00 pass.
Actual private metadata reads: 0; all real content/model/device/execution/write
counters: 0. Historical v0.1/v0.2 files and BLOCKED 5.6C.2B evidence are unchanged.
**5.6C.2B = BLOCKED / NOT_AUTHORIZED**; no measured quality/resource result is added.
POC-ASR-001 remains BLOCKED / NOT_READY; Stage 5 and Group B are not marked PASS.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 remains draft and unmerged.
See the [execution operator report](stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_EXECUTION_OPERATOR_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-campaign-execution-operator-stage0-v0.1.json).


## ASR 5.6C.2A.1 metadata compatibility remediation — 26 September 2026

`5.6C.2A.1 = PASS / PRIVATE_MANIFEST_OPERATOR_COMPATIBILITY_REMEDIATED`.
The additive v0.2 operator distinguishes selected `samples`, materialized
`bindings`, transfer records and freeze. Actual controlled metadata passed all
four pinned identities and exact 48-case cross-binding (24 RU + 24 EN; 97 transfer
records). Metadata reads: 8; all content/model/device/execution/write counters: 0.
38 successor tests, 99 SMALL host tests, 212 ASR host tests and Stage00 pass.
The historical three-file operator and blocked result remain unchanged; prior
5.6C.2A PASS and 5.6C.2B BLOCKED retain their historical meanings.

**5.6C.2B remains BLOCKED / NOT_AUTHORIZED after this task.** No new execution
authorization is issued; the successor's measured API rejects every call.
POC-ASR-001 remains BLOCKED / NOT_READY; Stage 5 and Group B are not marked PASS.
Recovery remains 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 is draft and unmerged.
See the [remediation report](stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_SCHEMA_REMEDIATION_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-campaign-schema-remediation-stage0-v0.1.json).


## ASR 5.6C.2B pre-campaign schema blocker — 26 September 2026

`5.6C.2B = BLOCKED / FROZEN_OPERATOR_PRIVATE_MANIFEST_SCHEMA_MISMATCH`.
All three private metadata canonical identities match, but the exact admitted
materialized manifest lacks the top-level `samples` field required by the frozen
operator. Verification stopped before the campaign marker, model/audio/reference
reads or device access. Primary attempts: 0; completed RU/EN: 0/0; inference: 0.
Quality and resources are NOT_EVALUABLE; SMALL candidate disposition is
`INCONCLUSIVE / CAMPAIGN_EVIDENCE_INCOMPLETE`.

No retry, schema remapping, operator change or corpus modification occurred.
Separate compatibility-remediation scope and review are required before another
execution decision. Prior 5.6C.2A host/synthetic PASS remains historical evidence,
not proof of compatibility with the actual private package. POC-ASR-001 remains
BLOCKED / NOT_READY; Stage 5 and Group B are not marked PASS. Recovery remains
0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 stays draft and unmerged. See the
[result report](stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_RESULT_STAGE0_V0_1.md) and
[aggregate evidence](evidence/poc-asr-001/alpha-asr-small-campaign-result-stage0-v0.1.json).


## ASR 5.6C.2A SMALL operator/profile freeze — 25 September 2026

`5.6C.2A = PASS / SMALL_CAMPAIGN_OPERATOR_AND_EVALUATION_PROFILE_FROZEN`.
The additive SMALL evaluator binds the exact fresh acceptance/model/freeze and
unchanged decoding/measurement identities. Integer/exact-ratio quality and resource
predicates, one-primary-attempt policy, safe stop and pre-result dispositions are
frozen and validated with generated host fixtures. Historical BASE operators,
normalization, Java oracle, native runtime, gates and all earlier evidence remain
unchanged. Execution completion and candidate acceptance are separate results.

No private data, model bytes or device were accessed; model load, audio decode,
ASR inference, whisper_full and measured attempts are all zero. This preparation
does not establish SMALL quality, resource fitness or production readiness.
`5.6C.2B = NOT_STARTED / NOT_AUTHORIZED`; `POC-ASR-001 = BLOCKED / NOT_READY`.
Retention remains 2026-10-25. See the
[operator report](stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_OPERATOR_STAGE0_V0_1.md) and
[aggregate evidence](evidence/poc-asr-001/alpha-asr-small-campaign-operator-stage0-v0.1.json).


## ASR 5.6C.1.1 exact recovery and init readiness — 25 September 2026

`5.6C.1.1 = PASS / EXACT_CORPUS_RECOVERED_FRESH_HOLDOUT_MATERIALIZED_SMALL_PREFLIGHT_READY`.
Both exact historical RU/EN archive bytes were recovered through owner-authenticated
official MDC downloads. The unchanged metadata CLI reproduces all prior digests;
exactly the previous 24 RU + 24 EN are materialized with matching audio/reference
hashes and a complete private pre-inference freeze. Exact SMALL and pinned native
artifacts pass hash checks; model-init-only execution on POCO M5 succeeds and
task-owned process/file cleanup verifies. ASR inference and whisper_full remain zero.

Resulting `5.6C.1 = PASS / FRESH_48_HOLDOUT_MATERIALIZED_SMALL_MODEL_INIT_PREFLIGHT_READY`.
`5.6C.2 = NOT_STARTED / NOT_AUTHORIZED`; `POC-ASR-001 = BLOCKED / NOT_READY`.
Historical BASE remains `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`.
The 422 ms diagnostic initialization observation does not satisfy the campaign
cold-load gate. Retention deadline stays 2026-10-25. No selector, runtime, gate,
Recovery or production-admission change follows. Historical BLOCKED evidence remains
unchanged. See the [source recovery report](stage0/DORA_ALPHA_ASR_SMALL_HOLDOUT_SOURCE_RECOVERY_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-holdout-source-recovery-stage0-v0.1.json).


## ASR 5.6C.1 holdout preparation — 25 September 2026

`5.6C.1 = BLOCKED / FRESH_HOLDOUT_SOURCE_UNAVAILABLE_ON_THIS_DEVICE`. The new exclusion-aware
metadata validator passes; pinned inventory/original48 authority matches. After
332 unique exclusions, 190 RU and 220 EN candidates remain; the deterministic
metadata selection is 24 RU + 24 EN. Materialized counts are 0/0: the owner confirms
the complete controlled corpus is on another device, while this host has only the
original48 transfer subset. Private metadata is retained under owner-only storage,
with deletion deadline 2026-10-25. Full pre-inference freeze and POCO SMALL init
preflight are not run; ASR inference remains zero.

`5.6C.2 = NOT_STARTED / NOT_AUTHORIZED`; `POC-ASR-001 = BLOCKED / NOT_READY`.
Historical BASE remains `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`.
Source availability must be resolved within the existing retention authority before
materialization/freeze and device preflight can resume. No runtime/profile/threshold,
Recovery or production-admission change follows. See the
[5.6C.1 report](stage0/DORA_ALPHA_ASR_SMALL_HOLDOUT_PREFLIGHT_STAGE0_V0_1.md) and
[aggregate evidence](evidence/poc-asr-001/alpha-asr-small-holdout-preflight-stage0-v0.1.json).


## Alpha ASR 5.6B SMALL governance freeze — 25 September 2026

5.6B = PASS / SMALL_Q5_1_EVALUATION_DATA_AND_RESOURCE_GATES_FROZEN.
See the [approved governance contract](stage0/DORA_ALPHA_ASR_SMALL_EVALUATION_GOVERNANCE_STAGE0_V0_1.md)
and [machine-readable freeze](evidence/poc-asr-001/alpha-asr-small-evaluation-governance-stage0-v0.1.json).
The owner prospectively approves minimum necessary reuse of controlled Common Voice 5.0 RU/EN
archives/inventories for one future bounded SMALL acceptance campaign and its audit, with the
unchanged absolute deletion deadline 2026-10-25. If work cannot finish before it, stop and execute
the existing deletion obligation with receipts; no retention extension or current private access.

Fresh acceptance is exactly 24 RU + 24 EN after mandatory original-48 source/audio-digest,
prior evaluation/tuning, duplicate-audio and development-set exclusions. Freeze the unchanged
SHA-256 key family, bytewise tie-break, first 24 per locale and RU24-then-EN24 order. No tuning,
quality-based substitution or original-48 regression reuse is approved. Pool availability is unverified.
For SMALL on POCO M5 only: normalized aggregate WER RU <=20%, EN <=18%; all inference RTF gates
must pass (duration-weighted <=2.0, nearest-rank p95 <=4.0, maximum <=6.0); maximum cold load
<=15000000 us; observed PSS <=1610612736 bytes; sampled native heap <=1342177280 bytes.
End-to-end latency is diagnostic only. Require zero evidenced OOM, no SEVERE-or-worse thermal
status, complete telemetry with existing valid-gap rules, complete attempts and no quality retries.
600/605-second timeout/alarm remain safety controls, not performance budgets.

All private corpus/reference/result access, decode, model load, inference, device and subset
materialization counts are zero. No model bytes read. These are approved rules, not measured PASS.
5.6A/5.6A.1 admission and historical BASE rejection remain unchanged. Earlier pending-decision and
blocked-attempt sections remain historical; this amendment updates only current governance truth.
POC-ASR-001 stays BLOCKED / NOT_READY; no production/all-device support claim. 5.6C is
NOT_STARTED / NOT_AUTHORIZED. No PR/merge; PR #86, Recovery and production code remain untouched.


## Alpha ASR 5.6A.1 controlled model storage remediation — 25 September 2026

5.6A.1 = PASS / CONTROLLED_MODEL_STORAGE_ESTABLISHED_SMALL_Q5_1_BYTES_VERIFIED.
Current 5.6A = PASS / SMALL_Q5_1_ARTIFACT_ADMITTED_PRE_RUN_PACKAGE_READY.
See the [remediation report](stage0/DORA_ALPHA_ASR_NEXT_CANDIDATE_STORAGE_REMEDIATION_STAGE0_V0_1.md)
and [byte/storage evidence](evidence/poc-asr-001/alpha-asr-next-candidate-storage-remediation-stage0-v0.1.json).
The explicitly authorized replacement owner-only LOCAL_PRIVATE_CONTROLLED_STORAGE holds exactly
one ggml-small-q5_1.bin, 190085487 bytes, from HF revision f281eb45af861ab5e5297d23694b7d46e090c02c.
Complete acquisition and post-promotion SHA-256 both equal
ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb. Static GGML parsing verifies
multilingual SMALL/q5_1 and all 479 tensor names/shapes/types with exact EOF. Pinned whisper.cpp
927cfce34f31707e17f2bff35c349632fb9e2c3a is unchanged; no static incompatibility was demonstrated.

The preceding blocked 5.6A attempt remains immutable history; this additive result resolves only
its model-storage/actual-byte blocker. Artifact admission is not quality/device/production PASS.
Private corpus/reference/hypothesis/result access, audio decode, inference, native model load,
device execution and new subset materialization are all zero. Original BASE remains VALID_FAIL /
REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE. Historical 5.2–5.5, normalizer/oracle/runner/native code,
production allowlist, Recovery and PR #86 remain unchanged; no PR or merge.
Data reuse/retention, fresh 24 RU + 24 EN holdout and numeric RTF/PSS/native-heap decisions remain
pending owner approval. Private-data deletion deadline remains 2026-10-25, without extension.
POC-ASR-001 remains BLOCKED / NOT_READY. 5.6B and 5.6C are NOT_STARTED / NOT_AUTHORIZED.
The following dated amendments are preserved historical states, including the blocked first attempt.


## Alpha ASR 5.6A next-candidate admission / pre-run package — 25 September 2026

5.6A = BLOCKED / CONTROLLED_MODEL_STORAGE_UNAVAILABLE_ARTIFACT_BYTES_UNVERIFIED.
See the [admission and owner-decision package](stage0/DORA_ALPHA_ASR_NEXT_CANDIDATE_ADMISSION_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/alpha-asr-next-candidate-admission-stage0-v0.1.json).
The selected target is multilingual ggml-small-q5_1.bin at immutable HF revision
f281eb45af861ab5e5297d23694b7d46e090c02c, upstream size 190085487 bytes and expected SHA-256
ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb.
Public metadata, license and pinned-runtime source were reviewed. The existing controlled model
store is not established on this host; no model download occurred. Actual bytes/hash/header are
unverified, so artifact admission is blocked. Establish the existing model store (or separately
authorize a storage boundary), then complete byte/hash/non-inference checks in a later task.

Fresh untouched 24 RU + 24 EN acceptance data, exclusion of the original 48, and separate tuning
data if needed remain PROPOSED_NOT_APPROVED; no subset was materialized. Data reuse/retention and
numeric RTF/PSS/native-heap gates require explicit owner decisions before the next comparison.
Current retention remains ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS, completion
2026-09-25 and default deletion deadline 2026-10-25. No extension or follow-on reuse is approved.
Private-data access/audio decode/inference/device execution are all zero. The original BASE
candidate remains VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE; 5.4/5.5 are unchanged.
POC-ASR-001 stays BLOCKED / NOT_READY; quality/device/production admission is not granted.
5.6B and any new campaign are NOT_STARTED / NOT_AUTHORIZED. No PR/merge; PR #86 and Recovery untouched.
The following dated amendments remain historical; this one updates only next-candidate status.


## Alpha ASR 5.5 evidence assessment amendment — 25 September 2026

5.5 = PASS / CURRENT_MODEL_REJECTED_NEXT_CANDIDATE_REQUIRED. See the
[assessment report](stage0/DORA_ALPHA_ASR_ASSESSMENT_STAGE0_V0_1.md) and
[sanitized aggregate assessment](evidence/poc-asr-001/alpha-asr-assessment-stage0-v0.1.json).
All 48 retained primary attempts (24 RU / 24 EN) independently reconcile through the unchanged
5.3A normalizer and Java oracle. No common evaluation defect was demonstrated. RU 30.5000%
(87/19/16; 400 reference tokens) and EN 18.4300% (44/8/2; 293 reference tokens) remain FAIL.
CURRENT_MODEL_QUALITY_RESULT = VALID_FAIL; the exact base-q5_1 model/runtime/decoding profile
is REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE. This is not a conclusion about other candidates.

Recommended next experiment: same whisper.cpp runtime plus one separately admitted higher-capacity
multilingual Whisper artifact, with a prospectively frozen protocol and valid held-out design.
No exact artifact selected, admission started, model downloaded, inference run or decoder tuned.
RTF/PSS/native-heap numeric thresholds remain PROPOSED_NOT_APPROVED; the owner proposal is not approval.
RETENTION_ACTION_REQUIRED: authorize any follow-on reuse/extension explicitly, or delete retained
corpus and derived private evidence by 25 October 2026 under the existing 30-calendar-day obligation.
No deletion during assessment and no silent retention extension.

5.1/5.2 retain bounded PASS; 5.3 retains PRE_5_4_PREPARATION_COMPLETE; original 5.4 evidence and
its campaign PASS/quality FAIL split remain unchanged. POC-ASR-001 remains BLOCKED / NOT_READY
because the current exact candidate failed both approved quality gates and alternate evidence is absent.
Fresh host/scoring/parity and required offline Android checks passed; CI NOT_RUN. Production admission
is false. PR #86, Recovery and production allowlist are untouched; no PR or merge.
The following sections are preserved historical states; this amendment supersedes only the earlier
5.5 NOT_RUN/model-disposition-deferred state and evaluates the existing retention obligation.


## Alpha ASR 5.4 bounded POCO campaign amendment — 25 September 2026

The owner authorized the exact original 5.1C transfer, frozen 48-clip physical POCO M5 campaign
and one atomic task-branch commit/push. See the [campaign report](stage0/DORA_ALPHA_ASR_CAMPAIGN_STAGE0_V0_1.md)
and [sanitized aggregate evidence](evidence/poc-asr-001/alpha-asr-campaign-stage0-v0.1.json).

5.1C_TRANSFER_IMPORT = PASS / VERIFIED_ON_LAPTOP (48/48 audio, references and original duration
bindings; no replacement, rematerialization or reselection). The missing-private-corpus blocker
is resolved. Synthetic full inference passed before code/config freeze and selected corpus access.
5.4 = PASS / BOUNDED_POCO_48_CLIP_CAMPAIGN_COMPLETE: 24 RU + 24 EN, 48 successful primary attempts,
zero retries/failures/invalid/timeouts/cancellations; device cleanup VERIFIED.

Quality remains separate from execution: RU normalized WER 30.5000%, gate FAIL;
EN normalized WER 18.4300%, gate FAIL. Complete-language S/D/I sums and
reference counts are in aggregate evidence. Raw WER is diagnostic only. RTF/PSS/native-heap
numeric gates are NOT_EVALUATED / PROPOSED_NOT_APPROVED. Thermal maximum is NONE,
with no SEVERE-or-worse observation. Timestamp quality is NOT_EVALUABLE; timestamp gates NOT_EVALUATED.

5.1 and 5.2 retain bounded PASS; 5.3 retains PRE_5_4_PREPARATION_COMPLETE and its unchanged
historical model-init/lifecycle semantics. 5.5 = NOT_RUN; full POC-ASR-001 remains BLOCKED / NOT_READY;
model disposition is deferred to 5.5. No production integration/admission or 16-KiB runtime claim.
99 ASR tests, scoped offline Android baseline checks and native/synthetic checks passed.
Only sanitized aggregates are published. Private corpus/evidence remain retained through 5.5.
PR #86, Recovery, production allowlist and all historical text/counts are unchanged. No PR or merge.
The following entries are historical states; this amendment supersedes only the earlier absence
of authority and evidence for bounded task 5.4.



## Alpha ASR 5.3C bounded runner preflight amendment — 25 September 2026

The owner authorized continuing the six preserved local files, exact r28c installation,
isolated physical-device model initialization and one atomic task-branch commit/push.
See the [runner/preflight technical record](stage0/DORA_ALPHA_ASR_RUNNER_PREFLIGHT_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/alpha-asr-runner-preflight-stage0-v0.1.json).

5.3C = PASS / BOUNDED_ASR_RUNNER_INTEGRATION_PREFLIGHT_READY.
The exact 5.2 model/source and 5.3B toolchain passed identity checks. Two isolated native builds,
actual ELF/dependency/16-KiB package checks, 44 runner tests and 22 unchanged 5.3A regressions
(including Java oracle/bridge) passed. On authorized Android 14/API34 arm64, one model context
initialization returned typed SUCCESS; live cancellation and timeout returned their typed outcomes.
All three outcomes survived reopen/replay without another native start. Device cleanup = VERIFIED.
Independent read-only review and final privacy/scope checks precede publication.

5.3A and 5.3B retain their existing bounded PASS. Overall 5.3 = PRE_5_4_PREPARATION_COMPLETE,
not full ASR PoC completion. 5.4 = NOT_AUTHORIZED; ASR_INFERENCE and WER = NOT_RUN;
RU/EN, RTF/PSS/native-heap/thermal = NOT_EVALUATED. Corpus/transcript access and published private
attempt rows/hypotheses = 0. Production integration/admission = false. Static 16-KiB compatibility
does not establish 16-KiB-device runtime support. Full POC-ASR remains BLOCKED / NOT_READY.
The following sections remain immutable historical states; this amendment supersedes only their
absence of the bounded runner/model-init proof. Historical counts and Recovery are unchanged.
No PR or merge; PR #86 is untouched. The production allowlist, native verifier, 5.3A contracts,
Java oracle and 5.3B evidence remain unchanged.


## Alpha ASR 5.3B isolated native candidate amendment — 24 September 2026

The owner's explicit resumed 5.3B scope authorized reproducible SDK/cache cleanup, installation
of NDK 28.2.13676358, and an isolated Android arm64-v8a/API28 candidate from the exact 5.2 pin.
See the [native build record](stage0/DORA_ALPHA_ASR_NATIVE_BUILD_STAGE0_V0_1.md) and
[sanitized evidence](evidence/poc-asr-001/alpha-asr-native-build-16k-stage0-v0.1.json).

5.3B = PASS / ISOLATED_WHISPER_CPP_ANDROID_16K_CANDIDATE_VERIFIED.
Two clean source builds and both package fixtures passed full five-library dependency closure,
ELF64/AArch64, all sixteen PT_LOAD 16-KiB checks, zipalign and the unchanged Dora verifier.
Reproducibility is NON_BIT_IDENTICAL_EXPLAINED: only DWARF/build-id differ; other original bytes
and comparison copies without debug/build-id match. NDK libc++ is separately pinned.
NATIVE_RUNTIME_BINARY = BUILT_ISOLATED_NOT_PRODUCT_ADMITTED. The fixture is a lib-only ZIP,
not an installable app or Android runtime/load proof. Production native allowlist is unchanged.

5.1/5.2/5.3A remain PASS within their existing bounded scopes. 5.3C = NOT_STARTED;
5.4 = NOT_AUTHORIZED; overall 5.3 is not complete. ASR_INFERENCE, MODEL_LOAD, DEVICE_RUNTIME,
real WER, RTF/PSS/thermal, POCO and Recovery = NOT_RUN; MODEL_QUALITY = NOT_EVALUATED.
Full POC-ASR-001 remains BLOCKED / NOT_READY / NOT_RUN; no production admission is granted.
The following sections are immutable historical states. This amendment supersedes only the
older absence of a bounded isolated native build; historical counts and Recovery are unchanged.
Publication is one atomic task-branch commit/push, no PR or merge; PR #86 is out of scope.


## Alpha ASR 5.3A host contract amendment — 24 September 2026

The owner's explicit 5.3A instruction freezes the
[bounded evaluation contract](stage0/DORA_ALPHA_ASR_EVAL_CONTRACT_STAGE0_V0_1.md) under
[ADR-0008](adr/ADR-0008-alpha-asr-evaluation-contract.md), with
[synthetic host evidence](evidence/poc-asr-001/alpha-asr-eval-contract-local-evidence-stage0-v0.1.json).
This additive entry supersedes only historical blanket absence of bounded data/model/text
contracts: 5.1 is PASS_BOUNDED_ALPHA_DATA_SCOPE_ONLY and
[5.2](stage0/DORA_ALPHA_ASR_MODEL_ADMISSION_STAGE0_V0_1.md) is
PASS / MODEL_ARTIFACT_AND_RUNTIME_SOURCE_PINNED_FOR_STAGE0_EVALUATION.

5.3A = PASS / VERSIONED_ASR_EVALUATION_CONTRACT_READY.
VERSIONED_TOKENIZATION_AND_NORMALIZATION_CONTRACT_NOT_APPROVED is closed only for the bounded
48-clip RU/EN pilot. Original I1 evidence and Java source remain unchanged. RU/EN normalized WER
gates stay 20%/18%; no mixed/noisy/speakerphone claim. Timestamp quality is NOT_EVALUABLE,
both timestamp gates NOT_EVALUATED, RTF/PSS/native-heap thresholds PROPOSED_NOT_APPROVED.

5.3B = NOT_STARTED; 5.3C = NOT_STARTED; overall 5.3 is not complete; 5.4 = NOT_AUTHORIZED;
ASR_INFERENCE = NOT_RUN; MODEL_QUALITY = NOT_EVALUATED. Full POC-ASR-001 stays
BLOCKED / NOT_READY / NOT_RUN. No model/audio/private-manifest access, native build, real WER,
Android/Gradle/device/POCO/Recovery execution, production admission or merge occurs.
Historical sections and backlog counts remain unchanged. Publication is one task-branch
commit/push without PR or merge, as explicitly instructed.



Версия: Stage 0D post-PR43 main integration / REC-I2A-I2B merged / REC-I3 implementation authority / bounded E-slot checks / Recovery campaign hold\
Дата: 19 августа 2026 года\
Owner approvals effective: 4 августа 2026 года (`OD-01`–`OD-10`), 11 августа 2026 года (`OD-11`–`OD-13`), historical recovery constraints / prospective `REC-JSR305-EXCLUDE-001` 12 августа 2026 года (`OD-14`), exact pure-foundation `REC-I1-AUTH-20260813-01`, and current `OWNER-AUTH-BATCH-20260819-01` / `OD-15` on 19 августа 2026 года (REC-I3 implementation/non-metric verification/conditional merge; bounded non-measured E-slot functional/fault/compatibility/preflight checks after exact-pin availability and task prerequisites; no Recovery Phase A/measured campaign or production admission)\
Owner GOV-OMI scope: 18 августа 2026 года — task definition plus
`GOV-OMI-PHASE-A-PUBLIC-METADATA-AUDIT-AUTH-20260818-01`; public GitHub metadata collection is
complete, while source/blob/archive and issue/PR-content retrieval, copying, execution and
admission remain unauthorized.\
Источник порядка: Technical Plan §37/§39, Design Spec §36/§39 и readiness gates.

Current `POC-RECOVERY-001` delta: PR #38 protected-squash-merged exact REC-I2A resolved-graph/
package/release-R8 and scoped Stage 0 Product/IP evidence plus the reviewed REC-I2B Tink runtime as
`a7e23c9a2758a3ee2cc8aba26be397b07ffc8f5b`. Its exact-head CI succeeded; the first exact-main run
failed only because governance dispatch still selected the historical REC-I1 successor profile.
PR #50 changed only that validator dispatch, preserved all REC-I2A/I2B runtime/evidence bytes,
merged as `0136a6904aac2909582ba228a7e24aafa7fdc4f7`, and restored green exact-main CI. The additive
[post-PR43 main closure](evidence/stage0-post-pr43-main-integration-closure-2026-08-19.json) records
that lineage without rewriting immutable Recovery evidence.

Current owner record `OWNER-AUTH-BATCH-20260819-01` / `OD-15` sets
`recI3ImplementationAllowed=true`, `recI3NonMetricVerificationAllowed=true` and
`recI3ConditionalMergeAllowed=true`, while `phaseAAllowed=false`, `executionAllowed=false`,
`measuredExecutionAllowed=false` and `productionAdmissionAllowed=false`. Historical
`implementationAllowed=false` and `implementationAllowedByThisPackage=false` fields remain
unchanged in their older package/readiness records and do not negate the new named REC-I3 overlay.
Active identities remain Gate Set `poc-recovery-stage0-v0.6` and protocol
`poc-recovery-protocol-stage0-v0.6`; historical `REC-REV-20260812-01` closure and the accountable
`APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` disposition remain preserved in their exact scopes.
The same current record separately authorizes all available bounded non-measured functional,
fault, compatibility and preflight checks on exact pinned E slots after task-specific prerequisites;
Recovery preflight additionally follows successful REC-I3, and D2 remains limited to intrinsically
physical evidence. The harness/controller, fresh REC-I3 graph, preflight and campaign remain absent; all ten active
`REC-RDY` blockers remain open. Aggregate backlog truth remains exactly `DONE=27`, `BLOCKED=18`,
`TODO=9`, `READY=0`. This reconciliation performs no emulator/device execution and elevates no PoC
state, PASS, readiness or admission.

REC-I3 first-slice amendment, 5 September 2026: the
[read-only key-confirmation controller scope](stage0/DORA_MVP1_POC_RECOVERY_I3_KEY_CONFIRMATION_CONTROLLER_SCOPE_STAGE0_V0_1.md)
and [local evidence](evidence/poc-recovery-001/rec-i3-key-confirmation-controller-local-evidence-stage0-v0.1.json)
supersede only the pre-slice blanket absence of a controller in the historical summary/table.
The full harness, durable bootstrap/publication/journal/quarantine, exact package verification and
required implementation review remain unfinished. This partial REC-I3 result closes no readiness
blocker, unlocks no Recovery preflight and leaves `POC-RECOVERY-001` `BLOCKED / NOT_READY`, aggregate
counts and every campaign/admission flag unchanged. Subsequent implementation slices remain under
the same current OD-15 authority; the current task performs no PR merge.

REC-I3 run-key bootstrap amendment, 5 September 2026: the additive
[bootstrap scope](stage0/DORA_MVP1_POC_RECOVERY_I3_RUN_KEY_BOOTSTRAP_SCOPE_STAGE0_V0_2.md)
and [local evidence](evidence/poc-recovery-001/rec-i3-run-key-bootstrap-local-evidence-stage0-v0.2.json)
add the exact KC01–KC13 new-run controller, typed crypto boundary, minimal PoC Android `Os`
publication adapter and versioned platform SQLite run-row journal. Host tests establish ordering,
short-write/error/closure behavior and the post-KC12 capability boundary; they do not prove Android
filesystem, Keystore or SQLite runtime behavior. Reconciliation, quarantine, candidate writers,
external kill controller, full fresh graph/package/R8 review and accountable implementation review
remain unfinished. All ten readiness blockers, aggregate counts, Recovery preflight lock and every
campaign/admission flag remain unchanged. This task performs no device/emulator execution,
preflight, hard kill, fault campaign, measurement, PR merge or future merged-main admission.

REC-I3 sequential microfile amendment, 5 September 2026: the additive
[scope](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_PUBLICATION_SCOPE_STAGE0_V0_1.md),
[clarification](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_PUBLICATION_CLARIFICATION_STAGE0_V0_1.md)
and [local evidence](evidence/poc-recovery-001/rec-i3-sequential-microfile-publication-local-evidence-stage0-v0.1.json)
add a repeatable `REC-MICROFILE-TINK` unit plus cumulative-manifest writer through
exact `MICRO-P01`–`P21`. The slice shares the bootstrap run lease and advances only the PoC journal
to a non-destructive schema v2 in the existing database. Host tests exercise actual Tink
round trips, sequential state, conservative failure remainders and exact processing intents; host
SQLite executes the production DDL and migration statements but is not Android runtime proof.
Reconciliation, quarantine, streaming, external control and campaign execution remain unfinished.
`fullRecI3Completed=false`, all ten blockers and the Recovery preflight lock remain unchanged.

REC-I3 reconciliation/quarantine amendment, 5 September 2026: the additive
[scope](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_RECONCILIATION_QUARANTINE_SCOPE_STAGE0_V0_1.md),
[ADR-0004](adr/ADR-0004-poc-recovery-reconciliation-and-quarantine.md) and local evidence add the
bounded authenticated microfile-prefix and durable quarantine controller. Host tests cover actual
Tink authentication, replay ordering and exact SQLite v1-to-v2-to-v3 migration; compiled Android
adapters and host fakes are not device durability proof. Streaming, external control and campaign
execution remain unfinished. `fullRecI3Completed=false`, all ten blockers and every preflight,
execution, measurement and production-admission lock remain unchanged.

The additive
[review-correction scope](stage0/DORA_MVP1_POC_RECOVERY_I3_RECONCILIATION_REVIEW_CORRECTION_STAGE0_V0_1.md)
supersedes the first local coverage claim after independent review found missing behavior inside
that bounded slice. The corrected host boundary owns platform observations, binds publication
authority to the candidate, manifest and ordered rows, preserves authenticated fallback prefixes,
and exercises pending quarantine replay, typed failures, descriptor bounds and schema-v3
validation. Independent review of corrected head `5de34577295b5e5477970785f9472d51e21bfc43`
returned `REVISE` (P0/P1/P2 = 0/7/1). Author-local successor `a020944f0444edfcbabc4690d4a367b1cf9e83d7`
then passed its reported host checks, but exact independent review also returned `REVISE`
(P0/P1/P2 = 0/4/2). The historical a020 six findings were pending then; ca2 closed optional-quarantine/no-prefix retention, confirmed-Q05 remainder, row framing and production unique-readback. Exact ca2 round-four truth preserves the immutable original `REVISE 0/3/0` review and its addendum-effective `REVISE 0/4/0` disposition. Independent review of `bc4f4423b535982956575f5a3480c9361eab6378` returned `REVISE 0/2/0`; the later reviewed successor `de735735ace6da3572c45dfdc58a8bbff98145b0` closed the four ca2 findings, the bc4 findings, and the duplicate-check acceptance gap, but found two distinct current P1s: zero-byte inventory regression and missing-final failure retention. The current author successor separates zero-permitting inventory reads from strict non-empty role reads and retains a durable-row missing final as primary with at most one temporary-observation secondary. Its 224 Android host tests and current governance checks are author evidence only; both new P1s remain open pending exact-candidate independent review. No readiness or execution authority changes, and all immutable ca2/addendum history and nonclaims remain recorded.
The prior author-local closure and acceptance mapping are superseded claims rather than independent
closure. Immediate lstat plus rename is not kernel-atomic no-replace proof; Android filesystem
durability and every wider REC-I3 gate remain unproven.

REC-I3 streaming Option A decision amendment, 5 September 2026: approved
[DEC-045](stage0/DEC-045-POC-RECOVERY-STREAMING-AUTHENTICATED-TAIL.md) records the owner-selected
authenticated-tail semantic from final proof `19807f6ea8166fff972e1537beeb131def8bfa1d` / tree
`8920855c9b8642162e5df16204d796d51db4fa86`. The bounded synthetic/non-metric streaming slice is
started by its additive scope; this resolves the owner A/B semantic choice, rejects Option B only
for this scope, and does not complete implementation. `C` remains durable, `R` may include only
independently authenticated contiguous oracle-equal bytes, and an unauthenticated/corrupt remainder
is rejected for bounded quarantine. All ten `REC-RDY` blockers, aggregate counts, the Recovery
preflight lock, active Gate Set/protocol, 8,160-byte/0.255-second bound and every campaign/admission
flag remain unchanged. No device/process-death/durability campaign, production admission, schema or
quarantine-mechanics implementation is authorized here.
## 1. Правила выполнения

- `DONE` означает: артефакт существует, acceptance выполнен и evidence доступно в commit/CI/report.
- `READY` означает: scope, dependency, fixture, gate и fallback определены; работа может начаться в отдельной ветке.
- `BLOCKED` означает: указан конкретный DEC/legal/license/device dependency; код, зависящий от решения, не начинается.
- `TODO` означает: задача известна, но ещё не прошла Definition of Ready.
- Один PR решает одну измеримую задачу или тесно связанный вертикальный slice.
- PoC не превращается в production dependency автоматически. Admission требует отдельный ADR/lock PR.
- Raw private audio, credentials, signing keys и unapproved model weights не входят в Git/LFS/Actions artifacts.

## 2. Stage 00 — GitHub, readiness и bootstrap

| ID | Состояние | Задача | Зависимости | Результат / acceptance |
|---|---|---|---|---|
| S00-GIT-001 | DONE | Проверить baseline/local Git | — | root/branch/status/history/remote проверены; SHA `1be83e…` подтверждён |
| S00-GIT-002 | DONE | Создать initial private `Monumentogram/DORA` и опубликовать baseline `main` | S00-GIT-001 | repository created without an extra initial commit; remote `main` указывает на exact baseline; later visibility change governed by ADR-0002 |
| S00-GIT-003 | DONE | Создать `stage/00-readiness-bootstrap` | S00-GIT-002 | branch fork point = baseline; `main` unchanged |
| S00-DOC-001 | DONE | Полностью прочитать и cross-check четыре baseline artifacts | S00-GIT-003 | readiness review with P0/P1/P2 and traceability |
| S00-DOC-002 | DONE | Создать Product Decisions registry | S00-DOC-001 | стабильный namespace DEC-001–DEC-042 и scoped Stage 0A owner approval record; каждый `Approved` status имеет прямой owner/date/scope record |
| S00-DOC-003 | DONE | Зафиксировать backlog, status, ADR и Codex rules | S00-DOC-001 | root `AGENTS.md`, contributing/status/backlog/ADR linked |
| S00-TEST-001 | DONE | Создать сквозную Test Strategy | S00-DOC-001 | уровни unit→release, environment/pass gates, CI tiers и physical matrix закреплены в `DORA_MVP1_TEST_STRATEGY.md` |
| S00-ANDROID-001 | DONE | Создать минимальный Android skeleton | DEC-005/006/015; ADR-0001 | wrapper/JVM 17/min 28/compile-target 36; adaptive four-destination placeholder shell; separate non-recording action; light/dark semantic token mapping; no microphone permission/product behavior |
| S00-TEST-002 | DONE | Добавить meaningful bootstrap tests и instrumentation infrastructure | S00-TEST-001, S00-ANDROID-001 | destination order/selection, unavailable recording action, theme/tokens and compact/wide threshold covered; Compose UI suite compiles and has a documented device command |
| S00-QUALITY-001 | DONE | Закрепить formatting и Kotlin static analysis | S00-ANDROID-001 | Spotless 8.9.0 + ktfmt 0.63 and Detekt 1.23.8 are version-pinned; checks pass without a baseline or disabled rule set |
| S00-DEPS-001 | DONE | Устранить `androidx.core` catalog/lock drift | S00-ANDROID-001 | catalog intentionally pins `1.18.0`, matching the Activity 1.13.0 graph and regenerated/reviewed dependency locks |
| S00-CI-001 | DONE | Добавить GitHub Actions CI | S00-ANDROID-001, S00-TEST-002, S00-QUALITY-001 | pinned least-privilege workflow validates wrapper/docs, formatting, static analysis, locks, unit/androidTest compilation, lint/assemble and native alignment; debug bootstrap APK is retained for seven days |
| S00-VERIFY-001 | DONE | Локально проверить clean checkout commands | S00-CI-001 | 197-task formatting/detekt/test/androidTest-compile/lint/assemble graph green; Stage 00 validator, dependency insight and 16-KiB ELF/APK gates green; generated artifacts ignored |
| S00-PR-001 | DONE | Commit/push/open PR without merge | S00-VERIFY-001 | checked Stage 00 commit/branch published; ready-for-review PR #1 targets `main`; no merge |
| S00-CI-002 | DONE | Проверить/исправить GitHub Actions | S00-PR-001 | PR-triggered `android-bootstrap` completed successfully, including test/lint/assemble and native gates |
| S00-SEC-001 | DONE | Провести pre-public secret/privacy audit | S00-CI-002 | checksum-verified Gitleaks 8.30.1 scanned all refs/full history and both branch trees; all three commit trees, filenames, identities, Actions configuration/logs and GitHub secret metadata were independently checked; no real secret, PII, private path or accidental artifact found |
| S00-GIT-004 | DONE | Защитить `main` после появления stable check name | S00-SEC-001 | owner explicitly approved temporary public visibility; existing repository changed in place and API-verified public; `main` requires up-to-date GitHub Actions app `15368` check `android-bootstrap`, PR, linear history and conversation resolution; admin enforcement on, force-push/delete off; secret scanning and push protection enabled; ADR-0002 |

## 3. Stage 0 — обязательные governance и PoC

Ни одна задача этого раздела не разрешает production feature implementation. Каждый PoC получает отдельную ветку, synthetic/consent-governed data и machine-readable report.

| ID | State | Size | Задача | Depends on | Deliverable | Acceptance / fallback |
|---|---|---:|---|---|---|---|
| GOV-001 | BLOCKED | M | Markets/legal/consent decision pack | DEC-001; production Legal review | counsel/product memo, market-specific lawful-basis and versioned copy scopes | `OD-02` approves only Stage 0 reminder checkbox; recording beta and production consent claims stay off |
| GOV-PRIVACY-001 | DONE | M | Privacy/data-flow/threat assumptions v1 | DEC-009/014/015 | `docs/stage0/DORA_MVP1_PRIVACY_DATA_FLOW_THREAT_MODEL.md` | data inventory, forbidden telemetry, trust boundaries, deletion and local/corporate modes documented; unresolved cloud/legal flows explicitly blocked |
| GOV-TRADEMARK-001 | TODO | S | Name/package/trademark availability | DEC-025 | evidence + approved production identifier candidate | no registration/store asset before approval |
| GOV-IP-001 | DONE | S | Reference/font/model asset IP rules | DEC-024/042 | `docs/stage0/DORA_MVP1_IP_ASSET_POLICY.md` | artifact-level provenance, license, attribution and admission rules documented; no model/binary/reference asset admitted |
| GOV-OMI-001 | BLOCKED | XL | Exact-snapshot Omi reuse, test and hazard audit without changing Dora invariants | GOV-IP-001; exact future source/content retrieval authority and IP disposition; named reviewers | [`gov-omi-reuse-stage0-v0.1`](stage0/DORA_MVP1_GOV_OMI_REUSE_AUDIT_TASK.md), [machine-readable task record](stage0/gov-omi-reuse-task-stage0-v0.1.json), and five sanitized [Phase A evidence artifacts](evidence/gov-omi-001/audit-report.md) | public-metadata Phase A is complete at Omi commit `7d99abcc4efb9e46a5853b21fc01289e4b891837` / tree `85db621ffd5dc5386bcbd7c87713cc69638be7e3`: untruncated 13,341-entry tree, complete 998-release and 2,953-issue indexes, capped 1,000/1,470 tags and 5,000/8,794 PRs. Rights are `BLOCKED_RIGHTS`; component/hazard conclusions are `INSUFFICIENT_EVIDENCE`. No source/blob/body/comment/patch/diff/archive retrieval, copying, execution, admission, active-stage or product change |
| GOV-REPO-001 | TODO | S | Long-term repository visibility, account plan and licensing/contribution terms | ADR-0002, owner | explicit decision and, only if approved, matching license/contribution updates | before merging an external contribution or returning the repository to private visibility |
| POC-GATES-001 | DONE | M | Approve versioned gates and result schema | DEC-020 / `OD-05` | `docs/stage0/DORA_MVP1_POC_GATES.md`, `docs/stage0/benchmark-result.schema.json`; defined `stage0-v0.1` gates Approved for Stage 0 | six undefined section 7 thresholds remain `Proposed`; affected verdict stays `INCONCLUSIVE` until pre-run approval |
| POC-SEARCH-GATES-002 | DONE | S | Select prospective storage/update predicates for `POC-SEARCH-001` | `DEC-043`; Project owner / `OD-12` | approved prospective `stage0-v0.2` Markdown + machine-readable Option B with paired control, physical D1–D3, exact repetitions/aggregation/environment/fallback | Option B approved on 2026-08-11 independently of prior Dora results; `benchmarkExecutionAllowed=false`; historical v0.1 evidence is not reclassified |
| POC-DEVICE-001 | DONE | M | Device/firmware matrix D1–D7 and first-run inventory | DEC-005/006/018; `OD-06` | `docs/stage0/device-matrix.yaml`; sanitized owner-phone-001 inventory assigned to D2 hardware profile | API/firmware/ABI/RAM inventory is recorded without a unique hardware ID; refreshed profile reports 36432 MiB free storage and satisfies D2 preflight; verdict remains `INCONCLUSIVE` |
| POC-DATA-001 | BLOCKED | L | RU/EN/mixed corpus governance and manifest | `OD-03`/`OD-04`/`OD-08`/`OD-09`; controlled storage/custodian/consent process | foundation in `docs/stage0/DORA_MVP1_DATASET_GOVERNANCE.md`; merged repository-owned [synthetic-public validator](evidence/poc-data-001/synthetic-public-manifest-validator-local-evidence-stage0-v0.1.json); PR #44 bounded [control-plane dry-run](evidence/poc-data-001/control-plane-dry-run-local-evidence-stage0-v0.1.json), synthetic manifest and non-formal review; [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json) | these host-only artifacts prove deterministic synthetic metadata/control and ownership-safe sentinel mechanics only. `CUSTODIAN_UNASSIGNED`, collection `NOT_AUTHORIZED` and overall `NOT_RUN` remain; no governed manifest/dataset/consented record or controlled store exists. Purpose-recorded data remains blocked; real meetings and training are prohibited |
| POC-CAPTURE-001 | DONE | XL | Exploratory physical-microphone capture: 3 min, 15 min screen-off, then attempted 60 min screen-off | DEC-002/003/004/018/020; `OD-01`/`OD-06`/`OD-08`; POC-DEVICE-001 | isolated capture app/harness in PR #8 + sanitized Run A/B/C reports; no raw trace/audio in Git | Run A and Run B completed; Run C recorded 63:49 but is an `invalidated exploratory attempt` because only 25:58 was screen-off, a TrueConf call occurred and the phone was charging. All three completed recordings produced valid WAVs, zero AudioRecord errors and verified deletion/absence; no approved critical capture failure was observed on the tested Samsung device. Owner accepts exploratory closure with formal verdict `INCONCLUSIVE`; this is not production approval, D1–D7 PASS, eight-hour evidence, clean one-hour screen-off stability or all-device support. The clean 60-minute screen-off baseline is deferred for a separately scoped campaign on a dedicated test device. |
| POC-RECOVERY-001 | BLOCKED | XL | Encrypted writer kill/recovery | POC-GATES-001, POC-DEVICE-001, active v0.6 Gate Set/protocol, completed accountable governance and REC-I2B reviews, merged REC-I2A/REC-I2B, current `OWNER-AUTH-BATCH-20260819-01` / `OD-15`, future successful REC-I3, bounded exact-pin E-slot/D2 Recovery preflight, separate owner Recovery Phase A/measured execution authorization | All 15 v0.1–v0.5 artifacts remain immutable superseded audit records; active v0.6 still has 46 unique rows, one effective KEY-04, 184 Phase A injections and 138 full-physical injections. PR #38 source-equal squash-merged [REC-I2A graph/Product-IP evidence](evidence/poc-recovery-001/rec-i2a-actual-graph-product-ip-disposition-2026-08-17.json) and [REC-I2B runtime/review evidence](evidence/poc-recovery-001/rec-i2b-runtime-crypto-implementation-evidence-2026-08-17.json); PR #50 fixed only squash-main validator dispatch. [OD-15](stage0/DORA_MVP1_STAGE0_OWNER_DECISION_OD15.md) now permits REC-I3 implementation/non-metric verification/conditional merge, authorizes available bounded non-measured E-slot functional/fault/compatibility/preflight checks after exact-pin availability and task prerequisites, and defines E28/E30/E36-GAPI/E-NOGMS/E16K/E-NEXT ordering, with only E36-GAPI exactly pinned. | **Not READY / BLOCKED:** older package snapshots retain `implementationAllowed=false` and `implementationAllowedByThisPackage=false`; current named overlay is `recI3ImplementationAllowed=true`, `recI3NonMetricVerificationAllowed=true`, `recI3ConditionalMergeAllowed=true`. The REC-I3 harness/controller and refreshed exact graph are not yet implemented. `phaseAAllowed=false`, `executionAllowed=false`, `measuredExecutionAllowed=false`, `productionAdmissionAllowed=false` govern the Recovery campaign; ten blockers remain (`REC-RDY-01`, `REC-RDY-03`–`REC-RDY-11`). No preflight, fault campaign or measurement ran in this reconciliation. Recovery preflight remains after successful REC-I3; no Recovery hard-kill/fault or measured campaign is authorized. Full PASS still requires valid D1/D2/D5; D1/D5 remain deferred, and emulators cannot substitute physical mic/flash/battery/thermal/OEM/radio/VPN/arm64 evidence. Final `ADR-AUDIO-001` remains post-evidence. |
| POC-VAD-001 | BLOCKED | L | 90 s silence/max-cap deterministic replay | POC-GATES-001, POC-DATA-001 | merged [P2 frame-timing](evidence/poc-vad-001/p2-host-oracle-local-evidence-stage0-v0.1.json), PR #46 [P3 deterministic replay](evidence/poc-vad-001/p3-deterministic-integrated-replay-local-evidence-stage0-v0.1.json) and PR #49 [P4 PCM rotation](evidence/poc-vad-001/p4-synthetic-pcm-rotation-local-evidence-stage0-v0.1.json), indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json); acoustic matrix remains absent | pure-host evidence covers frozen timing/replay/file-rotation mechanics only; it is not acoustic, realtime, device, governed-corpus, storage-product or support evidence. Physical/acoustic execution and the overall PoC remain blocked/not run |
| POC-ASR-001 | BLOCKED | XL | Local RU/EN/mixed ASR benchmark | POC-GATES-001, POC-DATA-001, POC-DEVICE-001 | PR #53 merged [I1 synthetic WER/timestamp aggregation mechanics](evidence/poc-asr-001/i1-synthetic-scoring-oracle-local-evidence-stage0-v0.1.json) with source-equal tree/review/CI indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json); artifact/corpus/device report absent | the I1 oracle does not define normalization/alignment, choose or run a model, evaluate WER/RTF/PSS/thermal/16-KiB/device support or close a gate; PoC remains BLOCKED / NOT_READY / NOT_RUN |
| POC-DIAR-001 | BLOCKED | XL | Local/server diarization and correction load | POC-GATES-001, POC-DATA-001 | PR #55 merged [I1 synthetic DER/JER/count/review-flag mechanics](evidence/poc-diar-001/i1-synthetic-scoring-oracle-local-evidence-stage0-v0.1.json) with source-equal tree/review/CI indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json); governed corpus/model/license/device/correction report absent | no collar/overlap/alignment/threshold/model/license/device or correction-burden claim; no forced speaker if later gate/license fails; PoC remains BLOCKED / NOT_READY / NOT_RUN / NOT_AUTHORIZED |
| POC-BATTERY-001 | BLOCKED | L | Capture/VAD/ML energy and thermal matrix | POC-CAPTURE-001 | merged [capture-only controlled-comparator host-oracle evidence](evidence/poc-battery-001/capture-only-controlled-comparator-host-oracle-local-evidence-stage0-v0.1.json) and combined [publication closure](evidence/stage0-host-oracle-publication-closure-2026-08-18.json); controlled physical baseline/repeats/batterystats/thermal policy remain absent | the pure-host comparator validates arithmetic and attribution semantics only; it contains no energy, thermal, device, screen-off, VAD or ML measurement. The PoC remains blocked and no threshold or PASS is claimed |
| POC-DECISION-001 | BLOCKED | XL | Decision revision graph benchmark | POC-GATES-001, POC-DATA-001 | merged projection oracle plus PR #45 [I2 deterministic synthetic harness](evidence/poc-decision-001/decision-deterministic-synthetic-harness-local-evidence-stage0-v0.1.json) and PR #54 [I3 synthetic metamorphic campaign](evidence/poc-decision-001/decision-i3-synthetic-campaign-local-evidence-stage0-v0.1.json), indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json); governed source/corpus/model scoring remains absent | host mechanics validate source-range/revision/user-ownership and metamorphic aggregation only. The synthetic 144-case campaign counts as zero governed cases, cannot make a decision final automatically and is not benchmark/model/quality evidence; overall PoC remains BLOCKED / NOT_RUN |
| POC-SEARCH-001 | DONE | M | Room FTS4 10k/1M Stage 0 evaluation | POC-GATES-001; POC-SEARCH-GATES-002; `OD-11`–`OD-13` | immutable valid FAIL/targeted/final observations, exact 66-component evaluation packet, paired harness and fail-closed readiness under `docs/evidence/poc-search-001/`; PR #52 exact [KSP 2.3.11 build-tool lock overlay](evidence/poc-search-001/build-tool-lock-overlay-ksp-2.3.11.json) indexed in the [post-PR43 main closure](evidence/stage0-post-pr43-main-integration-closure-2026-08-19.json) | Stage 0C remains formal `INCONCLUSIVE` with recommendation `BLOCKED`; PR #52 is build-tool maintenance only and does not reclassify historical measurements or admit KSP/Room/FTS/schema/runtime. Physical D1/D3, fresh preflight and measured v0.2 remain deferred; `benchmarkExecutionAllowed=false`; no production Legal/Security admission |
| POC-OFFLINE-001 | TODO | L | Airplane/no-GMS core dependency audit | prospective readiness contract/machine record; merged I1/I2 host evidence and I2 review; PR #48 [I3 static call-ledger validator](evidence/poc-offline-001/i3-static-call-ledger-local-evidence-stage0-v0.1.json); [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json) | I1/I2/I3 prove bounded synthetic semantics and static call-ledger structure only. All 10 readiness blockers remain open (`0` closed); calibrated monitor, E-NOGMS/D4 and physical matrix, approved local model, durable product integration, reconnect and OS-blocked execution remain absent / `NOT_READY` / `NOT_RUN` / `NOT_AUTHORIZED` | no runtime zero-call claim follows from static structure. Usable local core still requires calibrated/device evidence; host semantics, static absence and repository CI are not an Offline PASS |
| POC-VPN-001 | TODO | L | VPN/route/idempotent multipart harness | BE-API-001 synthetic server contract; prospective [`poc-vpn-synthetic-api-stage0-v0.1`](stage0/DORA_MVP1_POC_VPN_SYNTHETIC_CONTRACT_STAGE0_V0_1.md), [machine record](evidence/poc-vpn-001/contract-record-stage0-v0.1.json), [pure-host oracle implementation record](evidence/poc-vpn-001/contract-kernel-implementation-stage0-v0.1.json), task-scoped [I2 hermetic-loopback implementation/evidence](evidence/poc-vpn-001/loopback-transport-implementation-evidence-stage0-v0.1.json) and [sanitized I2 advisory review record](evidence/poc-vpn-001/i2-implementation-advisory-review-2026-08-15.json) | I2 synthetic host-loopback subset is implemented, independently advisory-reviewed with `formalReviewer=false` and protected-squash-merged with exact-main CI green; physical VPN/route execution and the overall PoC verdict remain `NOT_RUN` / `NOT_AUTHORIZED` | one synthetic job/result in I2, no region switch or duplicate economic/deletion effect; neither kernel nor I2 is a physical POC-VPN PASS and this row remains TODO |

Post-PR43 POC-VPN additive publication fact: PR #51 merged the independently advisory-reviewed
[I3 host-hermetic fault-completion slice](evidence/poc-vpn-001/i3-host-fault-completion-local-evidence-stage0-v0.1.json)
with source/merge-tree and exact-head/main CI reconciliation in the
[post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json). The canonical
`POC-VPN-001` table row above remains byte-preserved for its immutable I2 integration locator. I3
adds no real DNS/TLS/external network/VPN/radio/route/provider/device execution or PASS; the PoC
remains `TODO`, `NOT_READY`, `NOT_RUN` and `NOT_AUTHORIZED`.

## 4. Design evidence backlog

| ID | State | Задача | Dependency | Exit evidence |
|---|---|---|---|---|
| DES-FOUND-001 | BLOCKED | Foundations/tokens/light-dark/device contrast | DEC-021–024 | D1 token report, no raw color drift |
| DES-FONT-001 | BLOCKED | Exact Manrope artifact test | DEC-024, GOV-IP-001 | RU/EN glyph, hinting, 200%, bytes, OFL/digest |
| DES-BRAND-001 | BLOCKED | Wordmark/icon directions | GOV-TRADEMARK-001 | three original directions; mdpi/themed/store checks |
| DES-IA-001 | BLOCKED | Low-fi shell/navigation/adaptive flow | DEC-026/032/041 | tree/first-click results and navigation ADR |
| DES-START-001 | BLOCKED | Permission/consent/preflight D2 | GOV-001, DEC-027 | comprehension/time/abandonment report |
| DES-WAVE-001 | BLOCKED | DoraWave D1 and state fixtures | POC-CAPTURE-001/VAD fixtures | state comprehension, no TalkBack spam/jank/audio impact |
| DES-STOP-001 | BLOCKED | Pause/Back/Stop/finalize D3 | capture state contract | zero accidental stop; persistent-state comprehension |
| DES-STORAGE-001 | DONE | Storage/retention/delete comprehension | DEC-013 | [`des-storage-retention-delete-v0.1`](design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md) and [machine-readable decision evidence](evidence/des-storage-001/decision-record-v0.1.json) define exact scope, loss-of-source warnings, deterministic synthetic fixtures and accessible partial/retry states; contract complete; no implementation/conformance/user-research/deletion-execution claim |
| DES-EXPORT-001 | DONE | Export scope/privacy flow | DEC-017 | [`des-export-interaction-v0.1`](design/DORA_MVP1_EXPORT_INTERACTION_CONTRACT.md) and [machine-readable decision evidence](evidence/des-export-001/decision-record-v0.1.json) cover accessible selection, plain-share warning and cleanup state; contract complete; no implementation/conformance/user-research claim |
| DES-A11Y-001 | DONE | Component accessibility contract | DEC-040 | [versioned semantics/focus/touch/contrast/200% contract and evidence template](design/DORA_MVP1_COMPONENT_ACCESSIBILITY_CONTRACT.md); no component audit/conformance claim |
| DES-ADAPT-001 | TODO | D10 compact→expanded/posture | DES-IA-001 | resize/state/inset/hinge evidence |

## 5. Stage 1 — production project foundation (после Stage 0 go/no-go)

| ID | State | Задача | Gate | Acceptance |
|---|---|---|---|---|
| S01-ID-001 | BLOCKED | Approve production application ID and signing custody | GOV-TRADEMARK-001, owner | documented owner, package registration and key backup/runbook |
| S01-BUILD-001 | TODO | Admit pinned Android dependencies | Stage 00 green; update audit | lock/verification metadata, SBOM/notices, reproducible build |
| S01-ARCH-001 | TODO | Core IDs/clocks/result/error contracts | Stage 0 ADR outcomes | deterministic unit/property tests; no provider DTO leakage |
| S01-TEST-001 | TODO | Room migration harness and test fixtures | data ADRs | non-destructive migration tests; generated data only |
| S01-PORTS-001 | TODO | Add only near-term engine ports | selected PoC admission ADR | one fake + contract tests; no unused future SDK/modules |
| S01-RELEASE-001 | BLOCKED | Internal signing/release pipeline | S01-ID-001 | secrets outside Git, auditable ownership and update test |

## 6. Downstream task IDs reserved by decisions

These are not Ready until their stage dependencies pass.

| Area | IDs |
|---|---|
| Audio/data | `ADR-AUDIO-001`, `STORAGE-RETENTION-001`, `DATA-TASK-001`, `DATA-SUMMARY-001` |
| ML/NLP | `ML-CATALOG-001`, `NLP-SUMMARY-001`, `TASK-001` |
| Backend | `BE-LEGAL-001`, `BE-PROVIDER-001`, `BE-PROVIDER-002`, `BE-CONSENT-001`, `BE-AUTH-001`, `BE-API-001`, `BE-DELETE-001` |
| UI | `UI-HOME-001`, `EXPORT-001`, `SEC-PRIVACY-001`, `QA-A11Y-001` |
| Release | `REL-API-001`, `REL-STORE-001`, `REL-SIGN-001` |

## 7. Definition of Ready

Задача переводится в `READY`, когда в PR/issue description указаны:

1. hypothesis/user value и явный non-goal;
2. source requirements/DEC/ADR;
3. dependencies и разрешённые artifacts/data;
4. measurable acceptance и failure fallback;
5. test matrix, privacy/logging limits и cleanup;
6. expected files/modules и branch name;
7. отсутствие необходимости принять P0 решение молча.

## 8. Definition of Done

- change scoped and reviewed through PR;
- relevant local/CI checks green;
- evidence/report is reproducible and versioned;
- no secrets/private datasets/unapproved binaries;
- status/backlog/DEC/ADR updated when outcome changes truth;
- user/manual truth is never overwritten by a model result;
- no merge to `main` from the current task unless the owner explicitly scopes that merge.


## REC-I3 streaming persistence governance amendment — 6 September 2026

Owner-confirmed `DEC-046`, accepted ADR-0005, and prospective Gate Set/protocol v0.7 pin the exact streaming persistence contract to combined baseline `3c63ab09874f4d089e4363985aa8b5c99900c122` / tree `718eae8d8d619d17c25ac9d025e0e24db3d52f9e`. The governance candidate adds schema-v4 migration, checkpoint/source-witness, sealed outcome/rejected-observation, exact/conservative ACTIVE retained-range, hash-only replay, K12-PERSISTENCE, and TRU-03 stream semantics only as a contract. It preserves the 46/184/138/120 campaign counts, keeps K12-CONSUMER deferred, and changes no v0.1-v0.6 artifact.

Persistence source remains blocked until this exact eight-file governance commit receives an independent CLEAN review. `POC-RECOVERY-001` remains `BLOCKED / NOT_READY`; all ten active blockers remain open and `REC-RDY-02` remains historically closed. No device/emulator execution, Recovery preflight, process-death/fault/measured campaign, durability claim, PASS/READY, dependency/production admission, consumer intent, cross-process guarantee, retirement, or merge follows.

## REC-I3 streaming result-boundary governance amendment — 6 September 2026

'DEC-047', ADR-0006, and Gate Set/protocol v0.8 supersede v0.7 only for the controller result mapping, class-specific strict references, receipt lifecycle/evidence delivery, and legacy ambiguous-commit spelling. v0.7 remains byte-identical and immutable. The corrected boundary has twenty mappings, preserves canonical 'JOURNAL_OPERATIONAL', separates exact-readback receipt core from the final post-close/post-sink receipt, and promises one bounded caller-retryable attempt without autonomous delivery.

This exact ten-path patch creates no v0.8 result-boundary/controller implementation, execution, or evidence. Later persistence source edits remain blocked pending independent CLEAN review. 'POC-RECOVERY-001' stays 'BLOCKED / NOT_READY'; ten blockers remain open, REC-RDY-02 remains historically closed, OD-15 flags and 46/184/138/120 counts remain unchanged, and no execution/admission/consumer/cross-process/retirement/merge claim follows.

## REC-I3 proven-VALID-rollback correction — 6 September 2026

'DEC-048' and ADR-0007 resolve only the outward representation of a semantic VALID attempt after both framework-proven rollback and reconciled absence. The existing 'Retry(JOURNAL, JOURNAL_OPERATIONAL, SQLITE)' mapping applies with no receipt, attempted IDs, references, admitted endpoint, success or ACTIVE claim. Absence without proven rollback stays 'JOURNAL_COMMIT_STATE_UNRESOLVED'; the 4/6/20/4 boundary and pinned v0.7/v0.8 artifacts remain unchanged.

Independent focused review of the immutable amendment returned CLEAN before the affected mapping was enabled. No readiness, execution, campaign, production, consumer, retirement or merge status changes.

## REC-I3 observable streaming controller local candidate — 6 September 2026

The isolated `:poc:recovery` local candidate now composes the accepted v0.8 controller boundary with the existing one-descriptor streaming gateway, journal, Tink prerequisite crypto and bounded evidence port. Host tests cover the exact 4/6/20/4 mapping, constructor/reference invariants, read and precedence boundaries, replay/conflict/rollback outcomes, receipt/evidence ordering, sanitization, resource lifetime and zero-effect guards. Independent final review of immutable head `2f3d48cf813759e391a0281195b689cad1721b91` returned `REVISE 0/3/0`: persisted public evidence omitted mandatory safe positive facts, sealed replay touched prerequisite artifacts/Tink before outcome discovery, and replay did not bind oracle-derived row facts and identities to the supplied oracle. The first author-local repair projected the full sanitized positive evidence shape, discovered exact replay before the fresh-only prerequisite path, and reconstructed the oracle-bound outcome/range before source replay. Independent rereview of repaired head `7232c3f489908c6f7abdaeb2e2a1597996dc540f` then found that replay still trusted the stored retained-range hash. The current successor recomputes that exact range hash through the same frozen descriptor, compares it before receipt/evidence, and rejects a self-consistent altered hash and range identity. The focused Recovery tests, full Recovery JVM suite, Spotless, Detekt, Android lint and host SQLite schema-v4 verifier pass; exact source digests and commands are recorded in the local evidence JSON.

The published `7d7b81bf593cd1fa348f04703e9e22278db6be22` candidate's CI exposed a governance-only omission: Stage 00 still required the registry to end at DEC-044 although the authoritative ordered registry ends at DEC-048. The current successor pins that exact terminal decision, invokes Stage 00 from the Recovery validator, and admits `tools/validate_stage00.py` only through the resulting exact 16-path observable-controller profile. Kotlin bytes and the reviewed Android evidence remain unchanged.

Independent review of CI-correction head `5f897f585b7a4f2131324057823fb8ba5a42dde5` returned `REVISE 0/0/1/0`: its decision-label check used substring membership and could accept a required token in prose, a prefixed label, or an unknown metadata label. The current successor parses anchored field lines, preserves the ordered historical DEC-001–044 required schema, and enforces the exact ordered closed metadata blocks for DEC-045–048. Direct in-memory mutation tests cover relocation, prefix/misspelling, omission, extra metadata, the authoritative registry, and DEC-049 rejection.

Exact PR merge-ref CI run `34034200851` then exposed a test-fixture isolation defect: v0.8 regression tests changed only the simulated branch and retained the current observable-controller pull-request identity. The production validator correctly rejected that mismatched head ref before the intended assertions. The test-only successor constructs simulated v0.8 local lifecycles without inherited pull-request context and separately pins rejection of the real mismatched context.

Exact-head CI run `34035616019` showed that the first test-fixture repair cleared the verified pull-request context but retained GitHub's synthetic two-parent merge commit as the simulated-local HEAD. The production v0.8 linear-history guard correctly rejected that merge. The current test-only successor restores the verified pull-request source head before clearing context; an exact two-parent merge-ref regression reaches the real history check, proves the restored range contains no merge commit, and keeps the identity-drift negative control.

Exact-head CI run `34036673882` is terminal `SUCCESS`; both `android-bootstrap` and `search-smoke` passed with the source-head fixture repair. Under the owner's recurrence-prevention amendment, the mocked source-head check is now replaced by a retained regression that creates an isolated real two-parent merge checkout and invokes the actual Recovery `--self-test` entry point. Its verified-PR leaf proves source-head restoration and retains actual-entrypoint rejection for a wrong event head plus rejection of a genuine merge treated as local history.

Immutable review of retained-regression head `9111913753d40d9640ed07b43e0c6320290cf61e` returned `REVISE 0/0/1/0` because its two nested entrypoint subprocesses had no test-owned timeout, recursion guard, or descendant cleanup. The current test-only repair fails closed before local orchestration when PR event state lacks verified context, runs both children in bounded process groups, terminates their process trees on timeout or failure, and retains the successful leaf marker and both identity/history negatives.

Immutable rereview of bounded-child head `2de6d8238d99e71ae573ffa29481a59e052c0efd` returned `REVISE 0/0/1/0` because Windows cleanup targeted the already-dead nonzero parent PID and could leave its grandchild alive. The current successor creates each Windows child suspended, assigns it to a kill-on-close Job Object before resuming it, and retains the Job handle through completion. Live controls prove both timeout and immediate nonzero-parent descendants are terminated, their delayed markers remain absent, and the temporary directories are removable; POSIX process-group cleanup is unchanged.

This Job-bound retained-regression successor remains pending immutable independent review. `POC-RECOVERY-001` remains `BLOCKED / NOT_READY`; ten active blockers remain open, `REC-RDY-02` remains historically closed, and `fullRecI3Completed=false`. No device/emulator or preflight run, process-death/fault/measured campaign, Android durability claim, production/dependency admission, consumer intent, cross-process guarantee, range retirement, push, Pull Request edit, merge or next slice is claimed.
