# POC-CAPTURE-001 — sanitized physical-device evidence

Status: **Run A and Run B reviewed; overall result INCONCLUSIVE; Run C may proceed**

Reviewed: 10 August 2026

Device slice: one owner-provided D2 candidate only

## Public evidence boundary

The owner returned the Run A and Run B exports through the existing private Codex task.
The source ZIPs are not committed:

| Run | Source archive | Bytes | SHA-256 |
|---|---|---:|---|
| A | `dora-capture-poc-export-run-a-20260805T133208Z-349c5e0c.zip` | 5,294 | `f7d00a3675de539908641b513254e7fb8161f82b20685b64f27a4f080fdd05b1` |
| B | `dora-capture-poc-export-run-b-20260810T054638Z-0b71124a.zip` | 5,423 | `8d8161a57478f13036e3b23ec3c7abc7642b85594f9cd4e38f48a0872a841647` |

Human review confirmed for both archives that they:

- pass ZIP CRC validation and contain no duplicate, nested or traversal paths;
- contain only `device-profile.json`, `run-result.json`, `deletion-receipt.json`,
  `sanitized-event-log.json` and `README.txt`;
- contain no audio, waveform, transcript, crash dump, secret, account data, unique
  hardware identifier, private local path, serial number, Android ID, IMEI, MAC or IP;
- contain valid UTF-8 JSON, with each `run-result.json` conforming to
  `docs/stage0/benchmark-result.schema.json`;
- report verified raw-audio deletion and verified file absence before export.

Only the allowed public JSON copies and aggregates are retained here. The repository run
copies have one repository-final newline added; their JSON semantics are unchanged.
Source-entry hashes and repository-copy hashes are:

| Run / entry | Source-entry SHA-256 | Repository copy SHA-256 or disposition |
|---|---|---|
| A / `device-profile.json` | `d2534f55b1155a09fae5b13ccbe38e692bb46b19ea0722b88a41138fe8c685eb` | `e3cf3829a718db15692d5afe9e1e88fb0dd8ca16bb8439bf21be476033cb6c66` |
| A / `run-result.json` | `0e232e0a2bc10285f1a805fa6d1e3e973d1c7e904ab97ee4f32db87e17b060c1` | `93a565e1e45f384f4bb47a2d48787f92280f4b8fad7feb33d78d1bf4699cee4d` |
| A / `deletion-receipt.json` | `cb8451afe5b869d0d8a5de1722f487c8c4854b288f09ef9c73f3b1fcc00c8788` | summarized in `deletion-summary.json` |
| A / `sanitized-event-log.json` | `7076a18ad318288908f1eb9d056510aecc95f1acf68496c4ab3ef6bbdcfaf41b` | not committed; aggregate findings below |
| A / `README.txt` | `58b00579ed9a8c59092f8dbc71078000d11ffffeead58000efe45b3948f090ef` | not committed |
| B / `device-profile.json` | `1690f854f06de62915243b95ec2ab9c249509e709c40af9c340347de47862273` | not committed; refreshed aggregates below |
| B / `run-result.json` | `527417f466c19655cd1376f9bd6d53a811094e004f0ae82d6b6d46c1874c78d3` | `91ee6de2070fe90216021393bb4ee28cd5cc79301d44f686c178469069c94340` |
| B / `deletion-receipt.json` | `ff060f9ee04b534ef6513368165376c5987131d733755c92a05be6aa8248d4fa` | summarized in `deletion-summary.json` |
| B / `sanitized-event-log.json` | `ed7c28ae15869b3a435bb9673f5d01e3ce75dec867e5ed8baca1911992d13c3e` | not committed; aggregate findings below |
| B / `README.txt` | `37be2d74752c2edbe32cf9168aec03bf6ae9c626eb1e7e455cb25ecdf068697c` | not committed |

The optional fixture manifest is correctly absent from both archives because the built-in
test signal was disabled. The generic in-app README sentence mentioning a fixture manifest
is a wording defect only; it does not imply an omitted archive entry.

## Run A result

| Field | Observed |
|---|---|
| Run ID | `run-a-20260805T133208Z-349c5e0c` |
| Harness commit | `5d9a8aceebaa7175a7a5cbaa139e8295df87d632` |
| Planned / actual duration | 180 s / 180.258 s |
| Actual / expected samples | 2,881,600 / 2,884,128 |
| Sample delta | -2,528 samples (-0.087652%; -158 ms at 16 kHz) |
| PCM bytes / finalized WAV bytes | 5,763,200 / 5,763,244 |
| Short reads / AudioRecord errors | 1 / 0 |
| Start / finalization latency | 20 ms / 44 ms |
| WAV valid | yes |
| Route changes / interruptions | 0 / 0 |
| Screen-off duration | 0 s, as prescribed for Run A |
| Battery | 67% → 67%; charge counter 3,066,810 → 3,044,160 µAh |
| Thermal | `NONE` → `NONE` |
| Peak PSS / native heap | 132.299 MiB / 21.863 MiB |
| End free app storage | 35,874 MiB |
| Raw audio deletion | succeeded; absence verified; no raw audio retained |

Run A's PCM16 sample/byte equation and 44-byte WAV-header equation reconcile exactly.
Notification visibility was recorded as `unknown`; Run A therefore did not positively
prove notification visibility, but `unknown` did not trigger the approved hidden-state
failure predicate.

The Run A event log contains elapsed values `0, 16, 180189, 180367, 180258`; its deletion
event has the same known non-monotonic telemetry labeling described below for Run B.

## Run B result

| Field | Observed |
|---|---|
| Run ID | `run-b-20260810T054638Z-0b71124a` |
| Harness commit | `5d9a8aceebaa7175a7a5cbaa139e8295df87d632` |
| Planned / actual duration | 900 s / 1,316.644 s (21 min 56.644 s) |
| Screen-off duration | 1,020.154 s (17 min 0.154 s) |
| Actual / expected samples | 21,032,314 / 21,066,304 |
| Sample delta | -33,990 samples (-0.161343%; -2.124375 s at 16 kHz) |
| PCM bytes / finalized WAV bytes | 42,064,628 / 42,064,672 |
| Short reads / AudioRecord errors | 1 / 0 |
| Start / finalization latency | 15 ms / 31 ms |
| WAV valid | yes |
| Route changes / automated interruption counter | 2 / 0 |
| Persistent notification manual observation | `yes` |
| Screen mostly off / manual call or intervention | `yes` / `yes` |
| Charging / overheat or unexpected stop | `no` / `no` |
| Battery | 80% → 78%; charge counter 3,651,180 → 3,565,110 µAh |
| Thermal | `NONE` → maximum `NONE` |
| Peak PSS / native heap | 136.281 MiB / 18.496 MiB |
| End free app storage | 33,660 MiB |
| Raw audio deletion | succeeded; absence verified; no raw audio retained |

Run B exceeded the required 900 seconds of cumulative screen-off time and continued until
an explicit Stop. Its PCM16 sample/byte equation and 44-byte WAV-header equation reconcile
exactly. No whole-session loss, WAV corruption, AudioRecord error, hidden-state observation,
unexpected stop, overheat or deletion failure was reported. The positive persistent-
notification observation closes the specific visibility gap left by Run A.
The source records `manual.notification_visible=yes`.

Run B was not a clean uninterrupted protocol execution. It ran 416.644 seconds beyond the
planned duration, and the owner marked `manual.call_or_interruption=yes`. The sanitized
event log records a route change to `Bluetooth SCO` at 1,092.599 seconds and back to the
built-in microphone at 1,099.075 seconds, a 6.476-second interval occurring after the
nominal 15-minute target but before the eventual Stop. The automated
`capture.interruptions=0` counter and the manual observation measure different things; the
available aggregates cannot determine the exact cause or frame-level effect of the route
transition. This deviation is retained and prevents a claim of clean uninterrupted sample
integrity from this run.

The numeric sample-gap tolerance remains Proposed in Gate Set `stage0-v0.1`. The harness
also computes expected samples from an end snapshot taken after Stop/finalization work.
Consequently the -33,990-sample observation is not retrospectively classified as PASS or
FAIL, and the archive does not contain frame timestamps that could prove continuity through
the route transition.

The event log contains elapsed values
`0, 6, 1092599, 1099075, 1316577, 1316829, 1316644`. As in Run A, the deletion event is
non-monotonic because export instrumentation assigns `outcome.actualDurationMs` instead of
the deletion event's actual time. This is a known non-fatal measurement-telemetry defect.
The independent receipt and export readiness guard confirm that the finalized file was
analyzed, deleted and absent before ZIP creation; event timing is not used as pass evidence.

The 80% → 78% battery observation has no mWh value, controlled baseline or repeated slice.
No battery-gate or comparative energy claim is made from it.

## Verdict and sequence gate

The formal PoC result remains **INCONCLUSIVE** because one phone cannot cover D1–D7, two
runs cannot prove the 99.5% campaign gate, and the numeric sample-gap threshold remains
Proposed. Run B's route/intervention deviation also prevents a clean continuity claim.

For the bounded owner-phone sequence, however, Run B completed without an observed approved
critical failure: capture survived more than 15 minutes of cumulative screen-off time, the
persistent notification was positively observed, explicit Stop/finalize produced a valid
WAV, and raw deletion/absence were verified. Therefore Run C may proceed on the same build
and device. Run C is the only next capture run opened; three-hour and eight-hour runs remain
outside this Draft PR. Bluetooth must be fully disabled before Run C Start so the one-hour
screen-off run can serve as a cleaner baseline without another Bluetooth SCO transition.
