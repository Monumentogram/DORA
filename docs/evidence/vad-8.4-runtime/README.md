# Stage 8.4 product implementation

Status: **PENDING_FINAL_PUBLICATION**. This source commit cannot certify later
physical acceptance, exact-SHA CI, publication audit or the Google Sheet update.
Admission history in `vad-8.4-admission`, `vad-8.4-remediation` and `vad-8.4-closure`
is unchanged. Parent: `4073107108604136425071a51e63703374a660b8`.

The frozen `profile.json` and separate calibration receipts predate acceptance.
Profile SHA-256: `1c5ceb70f08593dfa96e522f43c51b7b28e36ab8b65a423fae417cc4e1cd2292`.
Onset is 4,800 frames; hysteresis 16,000; pre-roll 32,000; continuous semantic
silence 1,440,000; technical cap 9,600,000; overlap 32,000. At 16 kHz these are
0.3 s, 1 s, 2 s, 90 s, 600 s and 2 s. Hysteresis does not delay the silence clock.
The reference JSON hash includes its final LF. ADR-VAD-002 defines the semantics.

## Reproduction boundaries

Normal CI has no custody credentials or private binary. It compiles the real
provider's repository code, tests it with a fake binding, and tests frame logic,
encrypted persistence, migration, controls and seven process-death phases on
API28/API36. An unprovisioned runtime reports typed unavailability. CI does not
prove sherpa/Silero execution and cannot silently ship a test provider.

For a controlled local product build, retrieve the AAR through
`tools/vad_admission/custody.py` using the existing private Drive custody manifest.
Keep both verified inputs outside Git in one directory:

- `sherpa-onnx-vad-1.13.8-dora.1-arm64.aar` — 23,396,212 bytes,
  SHA-256 `64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db`.
- `silero.onnx` — Silero 6.2.1, 2,327,524 bytes,
  SHA-256 `1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3`.

From `android`, run Gradle with strict dependency verification and
`-PdoraPrivateVadDir=<controlled-local-directory> :app:assembleDebug`.
`verifyPrivateVad` must succeed before packaging. It verifies sizes, hashes,
the six AAR entries and both notices; it verifies staged copies again. Private
provisioning is rejected under `GITHUB_ACTIONS=true`. The output APK is private.
Do not attach it, the AAR, model or audio fixtures to GitHub or public CI.

Sherpa source remains `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf` (1.13.8).
Runtime and model remain distinct admitted supply-chain objects. Runtime
attributions are taken verbatim from the admitted AAR; the model MIT notice is
included separately. Static native alignment does not claim physical 16-KiB use.

## Evidence separation

- JVM acceptance uses canonical frame events, with no timing sleeps: exact
  89.5/90/90.5 s, 89.9 s cancellation, onset/hysteresis/pre-roll, discontinuities,
  failures, bounded queues, races and virtual 1/3/8-hour timelines.
- SQLCipher tests compare encrypted-source readback and source-reference overlap,
  preserve historical recordings, and reject future/changed metadata.
- `run_vad_segmentation_crash.py` observes actual emulator process death at seven
  named phases. This is synthetic component evidence, not physical microphone
  or native VAD quality evidence. The predecessor crash driver is unchanged.
- Physical product acceptance must identify the exact installed APK, loaded
  admitted libraries, controlled fixture hashes, frame-based observations,
  screen-off operation, resources, >600 s continuous capture and strict SAVED.
- `VadProductReadbackTest` is an opt-in local observer started before the host
  campaign. It reads the exact session identity on the existing control executor
  and checks SAVED for that same object. It reads only that encrypted source;
  it does not discover or reconcile other recordings, inject a provider, or
  export PCM. Receipts contain an identity hash, APK hash and campaign ID.
- `audit_vad_publication.py` checks the sealed publication inventory and explicitly
  selected content-free physical logs, with synthetic positive controls. It is
  a bounded audit, not a claim to inspect all private workstation data.

Independent source reviews are separate from physical and CI receipts. No
threshold may be changed after observing acceptance outcomes. Failed attempts
remain historical evidence; successful subchecks do not make a failed campaign
an overall PASS.

Stage 8.4C and 8.5 remain **NOT_STARTED**. PERF-REC-001 remains deferred and
non-blocking for Alpha. DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE remains **OPEN**.
No ASR, Cloud, consent-per-chunk behavior or production security restoration is
included. Do not update C75 until all external acceptance gates pass.
