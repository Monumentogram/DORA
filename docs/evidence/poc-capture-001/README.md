# POC-CAPTURE-001 — sanitized physical-device evidence

Status: **exploratory campaign complete; overall result INCONCLUSIVE**

Reviewed: 10 August 2026

Device slice: one owner-provided D2 candidate only

## Public evidence boundary

The owner returned the Run A, Run B and Run C exports through the existing private Codex
task. The source ZIPs are not committed:

| Run | Source archive | Bytes | SHA-256 |
|---|---|---:|---|
| A | `dora-capture-poc-export-run-a-20260805T133208Z-349c5e0c.zip` | 5,294 | `f7d00a3675de539908641b513254e7fb8161f82b20685b64f27a4f080fdd05b1` |
| B | `dora-capture-poc-export-run-b-20260810T054638Z-0b71124a.zip` | 5,423 | `8d8161a57478f13036e3b23ec3c7abc7642b85594f9cd4e38f48a0872a841647` |
| C | `dora-capture-poc-export-run-c-20260810T082734Z-d6abea38.zip` | 5,319 | `7f8b71182b1fa1ea015f8251a2d872005e8a3f47897f1c95e9062a5353bfdc69` |

Human review confirmed for all three archives that they:

- pass ZIP CRC validation and contain no duplicate, nested or traversal paths;
- contain only `device-profile.json`, `run-result.json`, `deletion-receipt.json`,
  `sanitized-event-log.json` and `README.txt`;
- contain no audio, waveform, transcript, crash dump, secret, account data, unique
  hardware identifier, private local path, serial number, Android ID, IMEI, MAC or IP;
- contain valid UTF-8 JSON, with each `run-result.json` conforming to
  `docs/stage0/benchmark-result.schema.json`;
- report verified raw-audio deletion and verified file absence before export.

Only the allowed public JSON copies and aggregates are retained here. Repository run-result
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
| C / `device-profile.json` | `f4dbba4d466656964f22d9668894da3290f46087a5fe3a21d23b8415a48981b0` | not committed; refreshed aggregates below |
| C / `run-result.json` | `de156f367cdb5355fa3baa4316233a84d957f5e0e733fe1e0f4f0f953487c486` | `63766554536b93195d9bc3b5bf016ed6661c5105d5c0c0decbb52ef05014a8f6` |
| C / `deletion-receipt.json` | `4caa2c390d0c42f40e0f32e1511056cc1058bb989ac6c4e6f5d059f807a5005b` | summarized in `deletion-summary.json` |
| C / `sanitized-event-log.json` | `bcdbd88262e5f094ea5f8659cac9dc31f227646b55a70ed2ef9037fddfbbe70c` | not committed; aggregate findings below |
| C / `README.txt` | `bf7fbf00d397d9416b0665949462ec943342c96f8785eb293525821a659617da` | not committed |

The optional fixture manifest is correctly absent from all three archives because the
built-in test signal was disabled. The generic in-app README sentence mentioning a fixture
manifest is a wording defect only; it does not imply an omitted archive entry.

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
| Raw audio deletion | succeeded; absence verified; no raw audio retained |

Run A's PCM16 sample/byte equation and 44-byte WAV-header equation reconcile exactly.
Notification visibility was recorded as `unknown`; this did not positively prove visibility
but did not trigger the approved hidden-state failure predicate. Its event elapsed values
are `0, 16, 180189, 180367, 180258`.

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
| Persistent notification manual observation | `manual.notification_visible=yes` |
| Manual call or intervention | `manual.call_or_interruption=yes` |
| Battery / thermal | 80% → 78%; `NONE` → maximum `NONE` |
| Peak PSS / native heap | 136.281 MiB / 18.496 MiB |
| Raw audio deletion | succeeded; absence verified; no raw audio retained |

Run B was not a clean uninterrupted protocol execution. It overshot the plan, and the
sanitized event log records a route change to `Bluetooth SCO` at 1,092.599 seconds and back
to the built-in microphone at 1,099.075 seconds, a 6.476-second interval. The available
aggregates cannot prove frame-level continuity through that transition. Its event elapsed
values are `0, 6, 1092599, 1099075, 1316577, 1316829, 1316644`.

## Run C result and owner disposition

The immutable in-app result is retained in `run-c-result.json`. Post-run owner review is
authoritative for protocol classification and corrects the manual questionnaire without
rewriting that completed machine report.

| Field | Observed / reviewed disposition |
|---|---|
| Run ID | `run-c-20260810T082734Z-d6abea38` |
| Harness commit | `5d9a8aceebaa7175a7a5cbaa139e8295df87d632` |
| Planned / actual duration | 3,600 s / 3,829.050 s (63 min 49.050 s) |
| Screen-off duration | 1,558.004 s (25 min 58.004 s; 40.689% of actual duration) |
| Actual / expected samples | 61,087,644 / 61,264,800 |
| Sample delta | -177,156 samples (-0.289164%; -11.072250 s at 16 kHz), observation only |
| PCM bytes / finalized WAV bytes | 122,175,288 / 122,175,332 |
| Short reads / AudioRecord errors | 1 / 0 |
| Start / finalization latency | 24 ms / 68 ms |
| WAV valid | yes |
| Route changes / automated interruption counter | 0 / 0 |
| Persistent notification manual observation | `yes` |
| Bluetooth before Start | fully disabled, confirmed by the owner after review |
| External intervention | TrueConf call, confirmed by the owner |
| Charging | phone was charging, confirmed by the owner; source questionnaire `no` is corrected |
| Battery | 70% → 86%; excluded from battery evidence because the phone was charging |
| Thermal | `NONE` → maximum `NONE` |
| Peak PSS / native heap | 135.712 MiB / 17.602 MiB |
| End free app storage | 33,507 MiB |
| Raw audio deletion | succeeded; absence verified; no raw audio retained |
| Campaign disposition | `invalidated exploratory attempt`; neither PASS nor FAIL |

The owner confirmed that the measured 25:58 screen-off value is correct; therefore the
source questionnaire answer `manual.screen_mostly_off=yes` is not used as evidence of a
one-hour screen-off interval. The owner also confirmed that the phone was charging, so the
source `manual.phone_charging=no`, charger-state field and battery increase are not used for
an energy or baseline claim. The TrueConf call explains the manual intervention even though
the automated AudioRecord interruption counter stayed at zero. Bluetooth was fully disabled
before Start and `capture.route_changes=0`; no repeat Bluetooth SCO transition was observed.

Run C's PCM16 sample/byte equation and 44-byte WAV-header equation reconcile exactly. The
sample delta remains an observation because the numeric threshold is Proposed and the
aggregate report has no frame-level timing that could explain the interval. The event log
contains elapsed values `0, 5, 3828946, 3829455, 3829050`. Its deletion timestamp is
non-monotonic for the same instrumentation reason seen in Run A and Run B. This remains a
known non-fatal measurement-telemetry defect; the independent deletion receipt and export
guard prove finalized-file analysis, deletion and verified absence before ZIP creation.

The run did not trigger an approved critical failure gate: there was no whole-session loss,
WAV corruption, hidden notification observation, AudioRecord error, unexpected stop,
overheat or deletion failure. It is nevertheless invalidated for the intended clean
60-minute screen-off slice because only 25:58 was screen-off, a TrueConf call occurred and
the phone was charging.

## Verdict and exploratory conclusion

The formal POC-CAPTURE-001 result is **INCONCLUSIVE**. Run C is an
`invalidated exploratory attempt`; it is neither PASS nor FAIL. The owner accepts closure of
the current exploratory campaign without a repeat because the owner phone is a primary work
device and an uninterrupted one-hour run is currently impractical.

Exploratory conclusion:

> No approved critical capture failure was observed on the tested Samsung device during Run A, Run B and the interrupted Run C campaign.

The evidence establishes only that, on the tested Samsung device and harness build, the
reviewed attempts exercised explicit Start/Stop, produced valid finalized WAV containers,
reported zero AudioRecord errors, showed the persistent notification in Run B and Run C,
and verified raw-audio deletion/absence after each completed recording.

The evidence does **not** establish:

- production approval or admission of the PoC implementation;
- an eight-hour recording result or any three-hour/eight-hour endurance behavior;
- PASS or support coverage for D1–D7 or all Android devices;
- a clean 60-minute screen-off stability baseline;
- a retrospectively approved sample-gap threshold or clean frame-level continuity;
- comparative battery or energy behavior from Run C.

The clean 60-minute screen-off baseline remains deferred evidence. It may be repeated later
as a separately scoped campaign on a dedicated test device under controlled power, screen,
radio and interruption conditions. No additional capture run is opened by this closure.
