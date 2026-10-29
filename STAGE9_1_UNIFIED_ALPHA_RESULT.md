# Stage 9.1 — Unified Owner Alpha result

Date: 2026-10-29. **BLOCKED / DIRECT_CLOUD_NETWORK_UNRELIABLE**.

The unified Owner Preview 5 is installed on the actual POCO. Permanent offline Local RU/EN, cancellation, restart and existing-data preservation passed. Physical Cloud RU/EN and autonomous USB-free acceptance remain blocked by reproduced transport failures on both available networks. **`PASS / UNIFIED_LOCAL_CLOUD_ALPHA_READY` is not awarded.**

## Source and review

- Branch: `stage/9.1-unified-owner-alpha`.
- Base: PR119 `e967fa4d10f66a318b066a3fe185a1c50f879708`.
- Installed application source: `471ccea0c4d75dc615c3c7c666a00acd28ee1459`.
- Public HTTPS option: `2e6d38e97f4b85049d4ef6fa0ed347502f1fc78a`.
- Final configuration source: `985a1609cea2a212926c5fd9c46ea91c4a8f6a29`. Later report/evidence changes are documentation only; the PR identifies their containing commit.
- [Draft PR120](https://github.com/Monumentogram/DORA/pull/120), stacked on [PR119](https://github.com/Monumentogram/DORA/pull/119). No merge performed.
- Verified ancestry: PR116 → PR117 → PR118 → PR119. Neither main nor the old device-backport branch was substituted for the working baseline.
- Independent review resolved stale Preview instrumentation identity/INTERNET expectations and found no remaining important issue in the reviewed packaging, persistence-test or public-HTTPS delta. The network blocker below remains open.

## Artifact and safe upgrade

| Parameter | Verified value |
|---|---|
| APK | `dora-unified-owner-preview-vc5.apk`, 83,850,597 bytes, retained owner-locally |
| SHA-256 | `5f4edbccd4b58a0a519c79c5800886f20e289db5d537312f6cbd320fb1f0a177` |
| Application ID | `com.monumentogram.dora.alpha.preview` |
| Version | code 5 / `0.1.0-owner-preview.5` |
| Certificate SHA-256 | `a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d` |
| Target | minSdk 28, targetSdk 36, arm64-v8a |
| Actual phone | POCO M5, Android 14 / API 34, explicit serial recorded privately |
| Android profile | user 10; user 0 remains `installed=false` |

The original owner signing key was available locally. Pulled pre-update Preview4 matched SHA-256 `284db30f699c53500ea15d8cb2db7a15f45dd581ad20fece20114001d89e4095`; certificate comparison passed. After separate approval of the exact APK and temporary installer foreground switch to user 0, the update succeeded at 09:52:09 Moscow. The installed APK hash equals the approved hash. Foreground returned to user 10 and the owner unlocked it. Two streamed transfers had lost USB transport without installing; the bounded `--no-streaming --user 10 -r` installation succeeded.

Owner-local artifact checks passed: manifest/permissions, backup exclusions, release diagnostic denials, DEX, unchanged native payload, resolved native dependencies and 16-KB alignment. No Whisper weights are packaged. A second reproducibility build is not claimed.

No uninstall, clear-data, storage/key reset, historical-record edit or model deletion was performed. The same model store and original Android Keystore remain in use. The existing Whisper SMALL q8 model is 264,464,607 bytes; actual decoding confirms the native runtime is available.

**Recovery boundary:** DTR2 reads DTR1, but old Preview4 cannot safely read new DTR2 writes. After new writes, repair forward with the same package, signer and keys at code 5 or higher, retaining both codecs. Do not downgrade to the old codec or reinstall destructively. The owner was informed before approving installation. Original APK/signing evidence remains private.

## Acceptance results

| Check | Status | Evidence and limit |
|---|---|---|
| Unified APK Build | PASS | Local and manual-consent Cloud code included in the same audited APK |
| Same Signer Upgrade | PASS | Actual user 10 update; installed hash and certificate match |
| Permanent Local Model | PASS | Controlled-clock tests at the former deadline, November 4, 2026 and year 2100, including import across the former deadline; phone date unchanged |
| Local RU | PASS | Actual new 11.05-second recording recognized offline; expected phrase matches after trim |
| Local EN | PASS | Actual new 11.05-second recording recognized offline; expected phrase matches after trim |
| Offline / cancellation | PASS | Android reported no active default network; cancellation during recognition added no version |
| Local restart / edits | PASS | Both new Local results and the explicit RU Local edit survived process restart |
| Cloud RU/EN on phone | BLOCKED | RU reaches registration/job creation but accepts 0 audio bytes; EN not submitted; no actual Transcribe result on this APK |
| Transcript Persistence | PASS within tested scope | Encrypted emulator migration and independent Local/Cloud edits/selection/reopen passed; actual device Local/history passed. Device Cloud edit/selection/restart remains BLOCKED |
| Existing Data Preservation | PASS | Four pre-existing documents/seven versions: immutable text, rendered edits, timestamps and active selection match before/after |
| Existing original audio | PASS | All four originals authenticated after upgrade: 489,600 frames/7 blocks; 215,200/3; 660,800/9; 280,800/5 |
| Direct HTTPS | BLOCKED | Mobile control requests can succeed, sustained exchange fails; Wi-Fi initial TLS delivery fails |
| POCO full acceptance | BLOCKED | Local upgrade accepted; Cloud and physical USB-disconnected operation not completed |
| Targeted CI | PASS at final code/configuration source 985a160 | Exact Stage18 and PIN-free runs below; nginx also validated live |
| Generic CI | FAIL, inherited | Historical identity and protected diagnostic-vault incompatibilities documented separately; no broad Recovery rerun |

Functional Local PASS and quality evidence are distinct: the two known synthetic phrases matched after trimming whitespace. This is not a general ASR-quality benchmark. Cloud functional and quality acceptance are both uncompleted.

Only the two newly approved synthetic clips were eligible for Cloud, each 176,800 frames / 11.05 seconds. Historical owner recordings were never uploaded. An already active approximately 92-second recording encountered before controlled recording was stopped and preserved; it was excluded from recognition and Cloud. There are seven recordings total, six transcript documents and nine successful transcript versions at this boundary.

## Tests and CI

- JVM: 117 Preview, 266 audio Debug, 267 audio Release, 5 model tests; zero failures/skips.
- Formatting, Detekt, Preview/core Lint and private ARM64 build passed.
- Backend: 56 tests passed, including public-HTTPS opt-in states; no live AWS jobs in this suite. Host guards: 68 owner, 44 transcript contract, 4 version-label and 2 Cloud-runner tests passed.
- Disposable API36 emulator: old installed-code lineage `acfd8a7` seeded encrypted synthetic data; new same-signer test APK updated in place and passed the existing restart/read test without replacing Keystore data. Five encrypted persistence tests and one exact Preview manifest test passed. No owner-phone instrumentation or owner-data copy was used.
- APK-source CI: [Stage18 40831006154](https://github.com/Monumentogram/DORA/actions/runs/40831006154), backend/software/API28/API36 all passed; [PIN-free 40831006108](https://github.com/Monumentogram/DORA/actions/runs/40831006108) passed.
- Combined application/network source 2e6d38e: [Stage18 40835566565](https://github.com/Monumentogram/DORA/actions/runs/40835566565), backend/software/API28/API36 all passed; [PIN-free 40835566552](https://github.com/Monumentogram/DORA/actions/runs/40835566552) passed.
- Configuration source 985a160: live `nginx -t`, reload, exact configuration hash and service checks passed; backend 56 passed. [PIN-free 40838811241](https://github.com/Monumentogram/DORA/actions/runs/40838811241) passed. [Stage18 40838811236](https://github.com/Monumentogram/DORA/actions/runs/40838811236) completed successfully, including backend/software/API28/API36.

The independently serialized rich DTR1 fixture verifies multiple versions, edits, older active selection and cancelled jobs through DTR2 and subsequent Local/Cloud append. The encrypted Cloud test verifies no automatic selection/overwrite, explicit conflict resolution, independent edits and reopen.

[Generic CI follow-up](docs/STAGE9_1_GENERIC_CI_FOLLOWUP.md) preserves the historical `Product identity differs from 7.3`, SBOM coupling and schema-3 protected diagnostic fixture versus schema-4 production fixture evidence. Current package identity was not changed to satisfy the frozen baseline. Historical test records and security checks were not weakened.

## AWS and transport findings

The existing Cloud backend, IAM, buckets, KMS, authorization, encryption, Transcribe contracts, cleanup/watchdog and topology remain unchanged. Necessary network changes were expressly authorized by Stage9.1 section 6:

1. A reviewed parameter-only change initially admitted the phone's exact mobile `/32`. Android trusted HTTPS then returned 401 in 326 ms. Ordinary mobile toggles changed the carrier egress repeatedly, invalidating that restriction.
2. The default-off `EnablePublicOwnerHttps` option was reviewed and enabled on the existing stack. Only TCP 443 ingress expanded; operator SSH, egress, other parameter values, physical resource IDs and outputs remained unchanged. Trusted Android mobile HTTPS returned 401 in 337 ms after another egress change.
3. Sequential Android no-auth probes reproduced first-response success and second-request timeout. A request-only `Connection: close` comparison passed. The reviewed nginx site change adds only a comment and `keepalive_requests 1`; exact preimage retained, `nginx -t` and reload passed, all other site bytes/services preserved. The original probe without client overrides then passed three requests in 491/302/299 ms.

These are **partial transport improvements, not complete connectivity acceptance**. The existing APK subsequently created the RU server operation but timed out before an audio chunk was accepted. A matched synthetic 87,427-byte POST reproduced the failure without credentials/audio/jobs. Fresh-process 2/8/16-KB probes also failed: the 2-KB request received HTTP422 headers but timed out reading its response body; larger requests timed out awaiting status. Thus a size-only, EOF-reader-only or TLS-resumption-only cause is not established. No speculative timeout, chunk-size or TLS weakening was introduced.

After the owner enabled Wi-Fi, Android confirmed validated default Wi-Fi with no VPN. A two-byte synthetic request timed out opening its output stream after 20.2 seconds. Header-only server observation saw TCP handshake but no payload. The larger Wi-Fi probe was skipped. All bounded probes ended and temporary diagnostic files were removed. Packet headers do not establish the exact failing network component or justify blaming a particular ISP.

Final device recheck confirmed user 10, the original RU Local machine text, saved manual edit and active Local version unchanged, with no Cloud version; the package remained absent from user 0.

At 08:10:39 UTC, the exact new Preview installation had one RU operation in `UPLOADING`, **0 uploaded bytes, 0 chunks, 0 provider attempts and 0 reservations**. No new Transcribe job started, and no Cloud transcript was returned. This statement concerns these tests, not total AWS-account spending. The pending manual action is retained for idempotent retry; there is no automatic upload.

Normal TLS trust, request proofs, manual consent, per-IP 5 requests/second plus burst 20, 100-KiB body bound, existing $0.25 daily reservation bound and $20 monthly budget/notifications remain. Budget notifications are not a hard account spending cap. No new paid service, domain, hosted zone, tunnel or proxy was created. See [network decision and rollback](docs/STAGE9_1_MOBILE_HTTPS_DECISION.md) and [sanitized transport evidence](docs/evidence/stage9_1/mobile-transport.json).

Preview exported its own public invitation; the existing operator enrolled it until **2026-11-04T23:59:59Z**. Private installation keys were not exported or copied. Finite Cloud authorization is independent of the permanent local model.

## Remaining work and next practical step

- **P0 / BLOCKED:** establish reliable complete HTTPS request/response delivery from POCO. Compare a different available network with the same trusted endpoint and bounded synthetic request before retrying the existing RU action. Current evidence does not justify an Android rewrite or speculative AWS architecture change.
- Once transport passes: complete the two authorized actual RU/EN Cloud jobs, independent Cloud edit/selection/restart checks, exact-job cleanup verification and a physical USB-disconnected acceptance test. Those checks are not claimed from CI or previous Stage18 tests.
- Generic CI remains a scoped follow-up, separate from the current network blocker.
- DORA Signal implementation was not started. The already prepared `stage/10-signal-ui-preparation` plan remains the next phase after unified acceptance, per the owner's Stage9.1 request.
- No merge performed. No background continuation is promised after this session.

## Changed files

Relative to PR119 base, including this report and sanitized evidence:
- `.github/workflows/stage18-cloud-asr.yml`
- `STAGE9_1_UNIFIED_ALPHA_RESULT.md`
- `android/app/build.gradle.kts`
- `android/app/src/androidTestOwnerPilot/kotlin/com/monumentogram/dora/ownerpilot/OwnerPilotInstrumentedTest.kt`
- `android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/CloudTranscriptPersistenceTest.kt`
- `android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/transcript/TranscriptStateTest.kt`
- `android/core/audio/src/test/resources/generate_transcript_v1_rich.py`
- `android/core/audio/src/test/resources/transcript-v1-rich.b64`
- `backend/deployment/alpha/README.md`
- `backend/deployment/alpha/stack.json`
- `backend/deployment/nginx.conf.example`
- `backend/tests/test_infrastructure_template.py`
- `docs/STAGE9_1_GENERIC_CI_FOLLOWUP.md`
- `docs/STAGE9_1_MOBILE_HTTPS_DECISION.md`
- `docs/evidence/stage9_1/mobile-transport.json`
- `docs/specs/STAGE9_1_UNIFIED_OWNER_ALPHA.md`
- `docs/superpowers/plans/2026-10-29-unified-owner-alpha.md`
- `tools/owner_preview_artifact.py`
- `tools/test_owner_preview_artifact.py`
- `tools/test_version_label.py`
- `tools/version_label.py`
