# Stage8 Alpha acceptance matrix

Profile: `STAGE8_ALPHA_LIMITED_RECORDING_V1`, ADR-RECORDING-011.
Verified parent SHA: `6f5e87317b163e4567ffa76da3cd3191bcfac826`.
Historical proof: [C3 CI 4/4](https://github.com/Monumentogram/DORA/actions/runs/38010786387).
Rows marked SATISFIED_FOR_ALPHA describe the bounded software contract; the final
source receipt in VERIFICATION.json and exact-SHA CI must both pass before the
software-ready claim. Physical and release rows remain independently open.

| Stage / requirement | Classification | Evidence and limit |
| --- | --- | --- |
| 8.1 PCM format, canonical frames, unique source identity, authenticated prefix | SATISFIED_FOR_ALPHA | Unchanged bridge and REC-I3 source; core audio/Recovery JVM regressions; runtime fault inventory |
| 8.2 SQLCipher, Keystore, AEAD/AAD, atomic durable transactions | SATISFIED_FOR_ALPHA | Unchanged adapters/schema/barriers; inherited encrypted failures/concurrency/migration/credential controls on API28/36 |
| 8.2C final OriginalAudioReference, ownership, stale-source rejection | SATISFIED_FOR_ALPHA | Original lifecycle tests plus Alpha full-boundary authenticated reopen and independent-recording preservation |
| 8.3 Start/Pause/Resume/Stop/Finalize, honest pending/saved/error states | SATISFIED_FOR_ALPHA | Existing session/admission/authority/backpressure/native-owner regressions and 25 Compose controls |
| 8.3 capture admission <=4,800,000 frames | SATISFIED_FOR_ALPHA | AlphaCaptureAdmissionTest and AsyncCaptureIntegrationTest: boundary trimming before enqueue, native release, cumulative generations |
| 8.3 session and direct scoped-writer cap | SATISFIED_FOR_ALPHA | AlphaRecordingLimitTest and RecordingAccessTest; no configurable runtime escape |
| 8.3 saving at cap / failed native stop | SATISFIED_FOR_ALPHA | Existing native timeout tests and control-owner stop-before-finalize ordering; no finalization when shutdown is unconfirmed |
| 8.3 low storage and asynchronous failure | SATISFIED_FOR_ALPHA | Existing storage-budget/UI denial and shared 256,000-frame backpressure tests; uncertain writes remain interrupted |
| 8.4 VAD/technical segmentation and overlap | SATISFIED_FOR_ALPHA | Algorithms unchanged; canonical bytes and short-session semantic tests retained. Five-minute limit occurs before the existing 600-second technical CAP |
| 8.4C logical source/chunk mapping | SATISFIED_FOR_ALPHA | Existing pure projection and encrypted logical recording tests; no source identity or overlap changes |
| 8.5 authenticated discovery after app restart, explicit continuation | SATISFIED_FOR_ALPHA | Existing Recovery UI/runtime tests, 18 applicable logical process-death phases and new real death at 299 seconds |
| 8.5 recovery already at/above five minutes | SATISFIED_FOR_ALPHA | New session test, real encrypted interrupted-cap test, runtime continuation denial; historical source not truncated or silently finalized |
| 8.6 C3 algorithmic optimization / successor preparation | SATISFIED_FOR_ALPHA | Accepted parent C3 proof reused; optimization and protection implementation unchanged, no new benchmark |
| 8.6 extended capture/reliability/device/energy thresholds | DEFERRED_TO_BETA | Exact items in DEFERRED_RELIABILITY_BACKLOG.md; failed historical evidence unchanged |
| One real <=5-minute screen-off capture and full readback | PHYSICAL_PENDING | NOT_RUN / SAFE_DEVICE_UNAVAILABLE; emulator is not physical evidence |
| Physical LONG02 custody | SEPARATE_RELEASE_GATE | Gate for historical-vault use only, not separate-device Alpha; successor NOT_INSTALLED, no POCO operations |
| Debug no-PIN/App Lock restoration | SEPARATE_RELEASE_GATE | DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE OPEN; physical restoration unverified |
| Signed-user-Alpha distribution | SEPARATE_RELEASE_GATE | BLOCKED by physical acceptance and security restoration |

The original Stage8.6 remains NOT_READY. This matrix does not rewrite its
acceptance denominator or claim a supported physical-device matrix.
