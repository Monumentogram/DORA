# Dora MVP 1 — Stage Status

Updated: 12 August 2026
Baseline: `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`
Stage 00 merge commit: `a4aae302f9033e5471f6759f513e7e351c375a72`
Stage 0A merge commit: `91b9916b01ff70f63d82412bafbed0d72307dbe1`
Repository: public `Monumentogram/DORA` by temporary owner-approved decision (ADR-0002)
Default branch: `main`
Stage 0B merge commit: `5e748469b22c6e7303fe6eb5f95394ea40088d84`
Stage 0C merge commit: `849d9d0406a619b334c9b707a4b6b42b34885b4b`
Active stage: `Stage 0D — POC-RECOVERY-001 governance/readiness package`
Active branch: `stage/0d-poc-recovery-governance`
Active PoC: `POC-RECOVERY-001`
Stage state: **GOVERNANCE REMEDIATION v0.3 — JSR305 EXCLUSION PROVEN; PRODUCT-IP DECISION, IMPLEMENTATION AND EXECUTION BLOCKED**

## Stage 00 closure

- Stage 00 is complete.
- Pull Request #1 was merged into `main`.
- The merge commit is `a4aae302f9033e5471f6759f513e7e351c375a72`.
- The Stage 00 Android bootstrap, CI, governance baseline and validation tooling are present on `main`.
- Production functionality was not started in Stage 00.

## Stage 0A closure

- Stage 0A is complete.
- Pull Request #7 was merged into `main`.
- The merge commit is `91b9916b01ff70f63d82412bafbed0d72307dbe1`.
- Owner decisions `OD-01`–`OD-10`, privacy/IP/data governance, the device matrix, Gate Set `stage0-v0.1`, benchmark schema and PoC execution order are present on `main`.
- No technical PoC or production functionality was started in Stage 0A.

## Stage 0B closure

- Stage 0B is complete and merged.
- Pull Request #8 was merged into `main`.
- The merge commit is `5e748469b22c6e7303fe6eb5f95394ea40088d84`.
- The formal `POC-CAPTURE-001` result remains `INCONCLUSIVE` and is not reinterpreted by Stage 0C.
- No production capture, storage, ML, backend, account or cloud functionality was admitted.

## Stage 0C closure scope

Stage 0C is complete and Pull Request #10 was merged into `main` at
`849d9d0406a619b334c9b707a4b6b42b34885b4b`. The historical search evidence and verdict below are
closure context only; Stage 0D does not modify or rerun that PoC.

Stage 0C evaluated only `POC-SEARCH-001` in the isolated `:poc:search` contour. It uses a
versioned deterministic generator and fully synthetic text to evaluate Room 2.8.4 with
SQLite FTS4 at 10,000 conversations and exactly 1,000,000 transcript segments. The
pre-run dataset, query, mutation, warm-up, repetition, metric and Gate Set contracts are
frozen before the first full run. The generated database is temporary and must not enter
Git or Actions artifacts.

Host/emulator evidence records generated-scale correctness, schema, mapping, mutation,
logical rebuild and exploratory latency observations. It does not establish a gate-complete
host `PASS`: prospective `stage0-v0.2` now contains owner-approved Option B, but it was not measured
and cannot reclassify the historical campaign. Under `OD-13`, the exact external-artifact evidence
packet is `EVALUATION_APPROVED` only for internal synthetic Stage 0 research. A formal
`PASS` and device latency/support claim also remain unavailable until the required physical
D1-D3 search slices exist. FTS4 and the PoC schema are not production-admitted by this stage.

The final checkpointed host/emulator campaign completed the frozen 10k/1M measurements without a
repeat of the multi-hour query-plan failure. Exploratory p95 is `97.537984 ms` and p99 is
`144.481302 ms`; both independent builds passed all `61/61` correctness cases, the FTS4-driven
count and page plans were accepted without temporary page sorting, and mutation, deterministic
rebuild and cleanup checks passed. The original `PASS`/`GO` host conclusion is superseded by the
versioned 11 August review assessment. The current v5 closure result is `INCONCLUSIVE` with
recommendation `BLOCKED`; no measurement was changed or rerun. D1/D3 and the measured v0.2
campaign are deferred to separately authorized future scope.

## Stage 0D governance/readiness scope

Stage 0D prepares and remediates only the governance/readiness package for `POC-RECOVERY-001`.
The v0.2 package at reviewed commit `70cf26125dbecbb347311ca0bb9ce1ad5c637e18` received findings
F-01–F-06 with `CHANGES_REQUIRED`. Proposed `DEC-044` and owner-linked Gate Set/protocol `stage0-v0.3` now fix the
prospective design semantics: public AES-GCM-HKDF Streaming AEAD with
`DURABLE_ONE_SEGMENT_LOOKAHEAD` versus sealed five-second `AES256_GCM_TINK_IV12_TAG16` microfiles
and exact authenticated binary manifest. Gate Set/protocol v0.1 and v0.2 remain unchanged superseded audit artifacts
and cannot govern future execution.

The v0.3 contract fixes one derived AES key per streaming ciphertext stream, exact lookahead/read
math and 8160-byte/0.255-second streaming design bound; deterministic big-endian AAD and manifest;
the exact non-deprecated Keystore Builder/key-confirmation/classification path; 9/13/21 candidate
publication sequences; `final + ".tmp"`, collision/no-overwrite and five reconciliation states;
successful SQLite `endTransaction()` as semantic commit; WAL/FULL rows with exact identities and
UNIQUE deterministic processing intents; candidate-specific K01–K12 barriers; and 33 mandatory
replay/rollback/key/parser/path/quarantine/event fault rows. The
original safety gates remain zero committed-byte loss, no more than five seconds tail per valid
kill, 12 strata, 120 base kills/candidate, at least 100 valid and at least eight valid/stratum.
Fifteen/30-second microfiles remain non-PASS observations or post-failure fallbacks.

The exact `tink-android:1.23.0` published JAR/POM/transitive closure, SHA-256 values,
per-coordinate license/copyright/NOTICE evidence, 16 publisher checksum matches and 16 verified
OpenPGP signatures, relevant advisory history, non-native composition and shaded protobuf 4.33.6
are recorded under `docs/evidence/poc-recovery-001/`. All eight coordinate authenticity
classifications are verified through publisher-bound signatures or exact multisource source
correspondence. The signed `jsr305:3.0.2` Maven POM declares Apache-2.0 while the exact release
source POM/LICENSE declares BSD-3-Clause. F-06 closes because the exact evidence is complete; the
underlying conflict remains uninterpreted. Governance-only source/bytecode/Kotlin/JVM/D8/R8
analysis proves conditioned exclusion: a Tink-local Gradle edge exclusion, zero resolved JSR-305
components and three exact R8 warning rules. A bare exclusion fails the exact AGP 9.3.1 R8 probe.
With all seven remaining closure JARs as program inputs, that exact rule removes the JSR-305
diagnostics but R8 then fails independently on `javax.lang.model.element.Modifier` from
`error_prone_annotations:2.41.0`; the future real graph/release build must resolve any such issue
without broadening the rule.
Project-owner / Stage 0 Product/IP acceptance of `REC-JSR305-EXCLUDE-001` remains pending. This is
package preparation, not dependency admission. No Tink coordinate or rule was added to Gradle.

Phase A is described only prospectively for the pinned emulator and available physical D2; actual
execution is withheld. Without D1/D5 it can produce only `FAIL` or `INCONCLUSIVE`. A full physical
verdict requires D1/D2/D5; D1/D5 procurement is deferred. The Project owner is assigned as Stage 0
Product/IP reviewer, but acceptance/rejection of the exclusion policy and final approval remain
null. A distinct accountable recovery
Engineering/Security reviewer is unassigned and blocking; the current Codex remediation does not
claim formal independence. Production Legal and Production Security remain null and separate.

No `:poc:recovery`, recovery harness, production schema, `:app` change, kill campaign, device run,
benchmark or recovery measurement exists. The fail-closed readiness record remains
`executionAllowed=false`.

## Closed Stage 0B evidence record

Stage 0B implemented only the disposable `POC-CAPTURE-001` evidence harness selected by `OD-01`. It did not admit that implementation into production Dora.

The owner phone could not be attached to the remote development workstation. Stage 0B therefore used a remote manual-device workflow without ADB:

1. GitHub Actions builds and publishes a debug-signed PoC APK.
2. The owner installs it manually and starts each test explicitly.
3. The PoC discovers sanitized device characteristics and records technical metrics locally.
4. Raw audio is analyzed and deleted in app-private storage before export is enabled.
5. The owner returns only the sanitized exported profile/report to Codex.

Three bounded phone runs were returned after two invalid pre-recording starts. Run A attempt 003 and Run B attempt 004 on `5d9a8ac` recorded, stopped and finalized successfully. Run C attempt 005 recorded for 63:49 but is classified by the owner as an `invalidated exploratory attempt`: only 25:58 was screen-off, a TrueConf call occurred and the phone was charging. Bluetooth was fully disabled before Run C and no route change was reported. All three completed recordings produced valid WAVs, zero AudioRecord errors and verified raw-audio deletion/absence; no approved critical capture failure was observed on the tested Samsung device. The formal result remains `INCONCLUSIVE`, and the owner accepts exploratory closure without repeating Run C on the primary work phone. Production capture, storage, ML, backend, account, production identity/signing and model weights remain outside this stage. The bootstrap `:app` must not receive microphone permission.

## Owner decisions effective 4, 11 and 12 August 2026

- `OD-01`: first experiment is `POC-CAPTURE-001`, limited to a physical microphone and explicit Start/Stop; call, system-audio and passive recording are prohibited.
- `OD-02`: every test run requires a separate reminder checkbox; it is not legal permission.
- `OD-03`/`OD-04`: synthetic-first; separately consented adult volunteer phrases may be used only after governance controls; real meetings and training/model improvement are prohibited.
- `OD-05`: fully specified `stage0-v0.1` gates are Approved only for Stage 0; critical data-loss/source/consent gates cannot be weakened after results. The six section 7 thresholds remain `Proposed`.
- `OD-06`: the first exploratory run is limited to one owner-provided physical phone; no other device procurement, global D1–D7 PASS or support claim is allowed.
- `OD-07`: eight hours is best effort only for the exact tested device, firmware, power, temperature and free-space conditions.
- `OD-08`/`OD-09`: GitHub receives only sanitized reports and aggregate metrics; raw evidence requires controlled private storage and the approved 90/180/30-day maximum deletion rules.
- `OD-10`: local mode works without account, network or GMS; cloud remains off until separate explicit consent.
- `OD-11`: Project owner is Product and IP policy reviewer and acts as Engineering/Security reviewer only for Stage 0 evaluation. This does not replace production Legal or independent production Security. Embedded platform SQLite may use the containing system-image digest plus exact image/runtime identity for Stage 0; that boundary must be reconsidered before production admission.
- `OD-12`: Project owner prospectively approves Option B for `stage0-v0.2`, based on the local-MVP storage/update/one-second visibility balance and not on prior Dora results. Benchmark execution remains separately withheld.
- `OD-13`: Project owner approves the exact 66-component/license/NOTICE/platform package only for internal synthetic Stage 0 evaluation and accepts formal `INCONCLUSIVE` closure without a new benchmark. This is not production Legal/Security approval, does not admit FTS4 automatically, is not retroactive, and leaves D1/D3 plus measured execution deferred.
- `OD-14`: Project owner constraints now link governance protocol v0.3 after F-01–F-06 `CHANGES_REQUIRED` and authorize only documentary/static remediation. The selected construction remains `DESIGN_SELECTED_IMPLEMENTATION_VERIFICATION_REQUIRED`; authenticity is verified and a conditioned exclusion path is now technically proven, while owner Product/IP acceptance/final approval, the uninterpreted `jsr305:3.0.2` license conflict, distinct accountable Engineering/Security review, implementation verification and later separate owner execution authorization remain mandatory; `executionAllowed=false`.

## Current gates and blockers

- `POC-RECOVERY-001` remains `BLOCKED`, not READY. Proposed `DEC-044`, Gate Set
  `poc-recovery-stage0-v0.3`, exact machine protocol and remediation evidence exist. v0.1/v0.2 are
  unchanged superseded audit artifacts. Coordinate authenticity and conditioned JSR-305 exclusion
  are technically verified, but the owner has not accepted/rejected `REC-JSR305-EXCLUDE-001`, the
  underlying license conflict is not interpreted, Product/IP final approval is null, and a distinct
  accountable recovery Engineering/Security reviewer/approval remains unresolved.
- The exact published Tink closure is inventoried without Gradle wiring. A future harness-resolved
  graph remains a P0 pre-execution check because repository Kotlin alignment may differ from the
  publisher POM closure. It must enumerate every resolvable recovery/consumer configuration, prove
  zero `com.google.code.findbugs:jsr305:3.0.2` components, verify the scoped exclude/exact narrow
  R8 rule and pass debug/release/package checks with no unresolved R8 missing classes. No runtime
  dependency or production admission exists.
- No recovery implementation or non-metric implementation verification exists. Phase A execution
  is separately withheld. Fresh emulator and D2 recovery preflight must record effective WAL/FULL,
  `wal_autocheckpoint=0`, `foreign_keys=ON`, `sqlite_version()`, `sqlite_source_id()` and canonical
  compile-options digest; all runtime fields remain null. D1/D5 are unavailable, so Phase A PASS is
  structurally forbidden and the full physical verdict is deferred.
- `tools/check_poc_recovery_run_readiness.py` must fail closed while
  `executionAllowed=false`; completion of any prerequisite cannot silently authorize execution.
- `POC-SEARCH-001` retains its frozen generated-scale observations. The earlier valid full result
  failed the latency gate; the corrected streaming rowid page plan passed the targeted scale guard
  before the final full measurement campaign met the evaluated latency/correctness predicates.
  Frozen manifests, parameter binding, repetitions, percentile definition and historical gates
  were not weakened.
- The Stage 0 v0.1 search row says that a storage/update gate failure is mandatory and lists no
  status exception, but no numeric overhead threshold was frozen. `DEC-043` / Gate Set
  `stage0-v0.2` now prospectively approves Option B with exact paired metrics, scale, repetitions,
  aggregation, physical environment and fallbacks. The historical gate remains `not_evaluated`;
  `benchmarkExecutionAllowed=false`, so no rerun is authorized.
- The PoC module now has a dependency lock and exact 66-component artifact/POM inventory covering
  both debug and formal benchmark configurations. All 66 effective licenses and discovered
  license/NOTICE entries are inventoried; `OD-13` makes the exact packet
  `EVALUATION_APPROVED` only for Stage 0 evaluation. `OD-11` records the Stage 0 Product/IP and
  Engineering/Security roles. The Android API 36 Google APIs x86_64 r07 archive is
  pinned by official SHA-1 and independently computed SHA-256. Embedded SQLite 3.44.3 uses that
  containing-image digest with exact image ID/revision, fingerprint, API and ABI for Stage 0; no
  extracted binary digest is required. This boundary must be reconsidered before production;
  production Legal is unassigned and independent production Security remains mandatory.
- The new paired control/indexed harness and nearest-rank combiner are implemented and bound to
  commit `b5bcf0951f3cb16d3fec65174395e7715c49a7d7`; debug and benchmark compilation, synthetic
  combiner tests and API 36 emulator runtime smoke pass. Smoke timings are not gate evidence. No
  formal 10k/1M v0.2 benchmark was executed.
- Physical availability is now explicit: D2 (`owner-phone-001`) exists, while D1 and D3 remain
  `unknown`. Missing D1/D3 blocks execution and any formal search `PASS` or support claim.
- The fully specified predicates in Gate Set `stage0-v0.1` are **Approved for Stage 0**. Exact ASR RTF by tier, maximum PSS/native heap, diarization corrections/minute, absolute battery drain without mWh, numeric capture sample-gap tolerance and minimum raw-trace retention remain **Proposed**.
- The owner's physical phone is sanitized as Samsung `SM-S908B`, Android 16 / API 36, build `BP2A.250605.031.A3`, primary ABI `arm64-v8a` and 10515 MiB RAM. It is assigned to D2 as the closest hardware profile; D1 and D3-D7 availability remains `unknown`.
- The refreshed pre-Run-A profile reports 36432 MiB free app storage, above the D2 start threshold of 8192 MiB, with 72% battery, unplugged power and thermal status `NONE`; the Run B export refreshed this to 33707 MiB and 80% with the same unplugged/`NONE` state. The Run C profile reports 33645 MiB, 70%, charging and `NONE`. The D2 hardware inventory remains valid; Run C battery data is not comparative evidence.
- The first Run A attempt on build `f351695…` is invalid: recording did not start, no sanitized Run ZIP or deletion receipt was produced, and the screenshot-era error can only be classified as a legacy unscoped `IllegalArgumentException` at `beginServiceCapture`.
- Replacement build `56fe23a` is published as prerelease `poc-capture-001-build-56fe23a` after successful push and PR CI. It makes optional battery/AudioRecord telemetry best-effort, adds sanitized capture-start stage codes, hardens recorder/player cleanup and propagates semantic dark-theme content colors.
- The replacement CI debug certificate differs from build `f351695`. The owner-provided post-install preflight showed the replacement-only corrected dark-theme UI, Run A target `00:03:00`, 36252 MiB free storage, 80% battery, unplugged power, thermal status `NONE` and synthetic signal disabled.
- The preflight screenshot does not display a commit identifier; association with `56fe23a` relies on the prescribed clean-install workflow and replacement-only corrected UI behavior. The source screenshot is not committed.
- The subsequent Run A attempt on `56fe23a` is invalid: recording did not start, no raw audio/Run ZIP/deletion receipt was created, and the visible stable code localizes the failure to `CAPTURE_START_PRIVATE_FILE`.
- Root cause is deterministic: generated Run IDs contain safe uppercase UTC delimiters `T`/`Z`, while the private WAV basename validator accidentally allowed only lowercase ASCII. Fix `5d9a8ac` accepts ASCII case consistently while retaining traversal, nested-path and unsupported-suffix rejection.
- Prerelease `poc-capture-001-build-5d9a8ac` targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`; push run `31006645902` and PR run `31006649411` are successful. APK SHA-256 is `dc7c01b8fd0f6f66c2674a8542595aab8817d60c4435d2f1c26ef8b1c4d2ddb9`.
- The new CI debug certificate `4052ae88…` differs from build `56fe23a` (`dd28877d…`). The owner-provided post-install preflight showed readable build `5d9a8ac` Run A UI with target `00:03:00`, 35945 MiB free storage, 72% battery, unplugged power, thermal status `NONE` and synthetic signal disabled; this was the accepted preflight for Run A attempt 003.
- The new preflight screenshot does not display a commit identifier; association with `5d9a8ac` relies on the prescribed clean-install workflow for the differently signed replacement APK. The source screenshot is not committed.
- Run A attempt 003 (`run-a-20260805T133208Z-349c5e0c`) targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`. The returned 5294-byte source ZIP has SHA-256 `f7d00a3675de539908641b513254e7fb8161f82b20685b64f27a4f080fdd05b1`; it passed CRC, allowlist, JSON parse, benchmark schema and sanitization review and is not committed.
- Run A completed 180.258 s with 2881600 actual versus 2884128 expected samples, 5763200 PCM bytes, one short read, zero AudioRecord errors, a valid 5763244-byte WAV, 20 ms start latency, 44 ms finalization latency, zero route changes/interruptions, 67%→67% battery, thermal `NONE`→`NONE`, 132.299 MiB peak PSS and 21.863 MiB peak native heap.
- The deletion receipt reports `deletionSucceeded=true`, `absenceVerified=true` and `containsAudio=false`; no raw audio is retained or committed. Public evidence is under `docs/evidence/poc-capture-001/`.
- Run A notification visibility is `unknown`, not a positive visibility proof and not evidence of a hidden recording. Run B positively records `manual.notification_visible=yes`, so the approved hidden-state failure predicate was not triggered.
- Run B attempt 004 (`run-b-20260810T054638Z-0b71124a`) targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`. The returned 5423-byte source ZIP has SHA-256 `8d8161a57478f13036e3b23ec3c7abc7642b85594f9cd4e38f48a0872a841647`; it passed CRC, flat allowlist, JSON parse, benchmark schema and sanitization review and is not committed.
- Run B completed 1316.644 s against a 900 s plan and accumulated 1020.154 s screen-off. It recorded 21032314 actual versus 21066304 expected samples, 42064628 PCM bytes, one short read, zero AudioRecord errors, a valid 42064672-byte WAV, 15 ms start latency, 31 ms finalization latency, 80%→78% battery, thermal `NONE`→`NONE`, 136.281 MiB peak PSS and 18.496 MiB peak native heap.
- Run B's deletion receipt reports `deletionSucceeded=true`, `absenceVerified=true` and `containsAudio=false`; deleted-WAV SHA-256 is `9f24052322b2ead6f3d528b8d69f8f06a25c4e6eb97a975b77a2394be13b6486`. No raw audio or source ZIP is retained or committed.
- Run B is not treated as a clean uninterrupted protocol execution: it overshot the planned duration by 416.644 s, the owner marked a call or other intervention, and the event log records a 6.476 s route transition to Bluetooth SCO and back after the nominal 15-minute target. The aggregate report has no frame-level timing to prove continuity through that transition, and the -33990 sample observation has no retrospectively approved tolerance.
- Run C attempt 005 (`run-c-20260810T082734Z-d6abea38`) targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`. The returned 5319-byte source ZIP has SHA-256 `7f8b71182b1fa1ea015f8251a2d872005e8a3f47897f1c95e9062a5353bfdc69`; it passed CRC, flat allowlist, JSON parse, benchmark schema and sanitization review and is not committed.
- Run C recorded 3829.050 s (63:49.050) against a 3600 s plan but only 1558.004 s (25:58.004) was screen-off. It recorded 61087644 actual versus 61264800 expected samples, 122175288 PCM bytes, one short read, zero AudioRecord errors, a valid 122175332-byte WAV, 24 ms start latency, 68 ms finalization latency, zero route changes/automated interruptions, thermal maximum `NONE`, 135.712 MiB peak PSS and 17.602 MiB peak native heap.
- Owner review confirms Bluetooth was fully disabled before Start, the 25:58 screen-off telemetry is correct, a TrueConf call occurred and the phone was charging. These corrections override the source questionnaire for campaign interpretation without rewriting the immutable machine report. The 70%→86% battery result is excluded from battery evidence.
- Run C raw-audio deletion succeeded, absence was verified and deleted-WAV SHA-256 is `50502a92a48825cf666057191b847d32d0b80aaeed010ec7e99abd604717d6ce`; no source ZIP, raw audio or raw event log is committed.
- Run C is an `invalidated exploratory attempt`, neither PASS nor FAIL. It did not complete the required clean 60-minute screen-off slice, but no approved critical failure gate was observed. Its -177156 sample delta is retained only as an observation because the numeric threshold remains Proposed.
- All three sanitized event logs are non-monotonic at raw deletion because the deletion event is labeled with `outcome.actualDurationMs`; the independent receipts/export guard prove deletion happened only after finalized-file analysis. This is a known non-fatal measurement-telemetry defect, and event timing is not used as pass evidence.
- The owner accepts closure of the current exploratory campaign without repeating Run C on the primary work phone. The clean 60-minute screen-off baseline remains deferred evidence for a separately scoped campaign on a dedicated test device.
- Exploratory conclusion: `No approved critical capture failure was observed on the tested Samsung device during Run A, Run B and the interrupted Run C campaign.`
- The formal result remains `INCONCLUSIVE`: one phone/three completed recordings cannot prove D1-D7, 99.5%, an eight-hour run, clean one-hour screen-off stability, an approved sample-gap threshold or support for all Android devices.
- One-device evidence cannot `PASS` the matrix and remains `INCONCLUSIVE` unless an approved failure gate produces `FAIL`.
- No controlled non-public evidence store or custodian has been configured. Until then, only synthetic data and sanitized aggregate/public evidence are allowed; raw traces/audio and purpose-recorded volunteer phrases remain blocked.
- Production markets/lawful basis/copy under `DEC-001` and Legal review remain unresolved. The Stage 0 reminder checkbox does not resolve production consent legality.
- No PoC result admits a production dependency. Native code or model admission later requires an ADR plus license, provenance, ABI, 16-KiB and runtime evidence.
- `main` remains the protected integration branch. Stage 0C/PR #10 is already merged at the recorded
  commit. Stage 0D stays on `stage/0d-poc-recovery-governance`; its new Draft PR must remain unmerged.

## Next safe action

Perform a repeat read-only review of the exact remediation commit without implementation or
execution. The Project owner must record the Product/IP final disposition and assign a distinct
accountable recovery Engineering/Security reviewer. That reviewer must verify or require revision
of the selected v0.3 Streaming/microfile construction, manifest/key/AAD contract, publication/temp-state/commit/durability,
SQLite profile, public barriers and complete fault/recovery state machine. Only a later separately
scoped task may implement and non-metrically verify the isolated harness; only after exact resolved
Gradle graph and fresh device/SQLite preflight may a still later owner record change
`executionAllowed`. Draft PR #11 must remain unmerged.

## Update protocol

Every later task updates this file only when stage truth changes. Live PR/build status remains authoritative in GitHub and should not be copied as a stale badge or hard-coded run ID here.
