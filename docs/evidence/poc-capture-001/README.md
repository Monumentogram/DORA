# POC-CAPTURE-001 — sanitized physical-device evidence

Status: **Run A reviewed; overall result INCONCLUSIVE; Run B may proceed**

Reviewed: 5 August 2026

Device slice: one owner-provided D2 candidate only

## Public evidence boundary

The owner returned `dora-capture-poc-export-run-a-20260805T133208Z-349c5e0c.zip`
through the existing private Codex task. The source ZIP is not committed. Its SHA-256 is
`f7d00a3675de539908641b513254e7fb8161f82b20685b64f27a4f080fdd05b1` and its size is
5294 bytes.

Human review confirmed that the archive:

- passes ZIP CRC validation;
- contains only `device-profile.json`, `run-result.json`, `deletion-receipt.json`,
  `sanitized-event-log.json` and `README.txt`;
- contains no audio, waveform, transcript, crash dump, secret, account data, unique
  hardware identifier, private local path or nested/archive-traversal path;
- contains schema-valid JSON and a `run-result.json` conforming to
  `docs/stage0/benchmark-result.schema.json`;
- reports verified raw-audio deletion and verified file absence before export.

Only the allowed public copies and aggregates are retained here. The JSON copies have one
repository-final newline added; their semantic content is unchanged. Source-entry SHA-256
values were:

| Entry | Source-entry SHA-256 | Repository copy SHA-256 |
|---|---|---|
| `device-profile.json` | `d2534f55b1155a09fae5b13ccbe38e692bb46b19ea0722b88a41138fe8c685eb` | `e3cf3829a718db15692d5afe9e1e88fb0dd8ca16bb8439bf21be476033cb6c66` |
| `run-result.json` | `0e232e0a2bc10285f1a805fa6d1e3e973d1c7e904ab97ee4f32db87e17b060c1` | `93a565e1e45f384f4bb47a2d48787f92280f4b8fad7feb33d78d1bf4699cee4d` |
| `deletion-receipt.json` | `cb8451afe5b869d0d8a5de1722f487c8c4854b288f09ef9c73f3b1fcc00c8788` | summarized without the deleted WAV digest |
| `sanitized-event-log.json` | `7076a18ad318288908f1eb9d056510aecc95f1acf68496c4ab3ef6bbdcfaf41b` | not committed; aggregate findings below |
| `README.txt` | `58b00579ed9a8c59092f8dbc71078000d11ffffeead58000efe45b3948f090ef` | not committed |

The optional fixture manifest is correctly absent because the built-in test signal was
disabled. The generic in-app README sentence mentioning a fixture manifest is a wording
defect only; it does not imply an omitted archive entry.

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

The sample/byte/WAV-size equations reconcile exactly: PCM16 bytes equal two bytes per
sample and the finalized WAV adds the expected 44-byte header. No whole-session loss,
corruption, AudioRecord error, unexpected stop, charging, thermal escalation or deletion
failure was observed.

## Manual observations and limitations

The owner recorded: notification visibility `unknown`, screen mostly off `no`, call or
interruption `no`, charging `no`, and overheating or unexpected stop `no`. Therefore Run A
does not positively prove notification visibility; Run B must include an explicit visible
notification check before the screen is turned off. `unknown` is not evidence that the
approved hidden-recording failure predicate triggered.

The numeric sample-gap tolerance remains Proposed in Gate Set `stage0-v0.1`. In addition,
the current harness computes expected samples from an end snapshot taken after stop/finalize
work, so the -158 ms delta cannot be interpreted as an unexplained microphone dropout. It
is retained as an observation and cannot produce PASS or retrospectively define a gate.

The source event log contains elapsed values `0, 16, 180189, 180367, 180258`; the deletion
event is therefore not monotonically ordered after `wav_finalized`. Source review traced
this to export instrumentation assigning deletion `outcome.actualDurationMs`, not to deletion
occurring before finalization. The independent deletion receipt and export readiness guard
confirm that the finalized file was analyzed, deleted and absent before ZIP creation. Event
timing is not used as pass evidence for this run.

The exported device profile retained the earlier 72% preflight reading, while the run-level
start/end snapshots report 67%. Run-level battery values are authoritative for Run A; no
comparative battery claim is made from this single uncontrolled three-minute run.

## Verdict and sequence gate

The formal PoC result remains **INCONCLUSIVE** because one phone cannot cover D1–D7, one
run cannot prove the 99.5% campaign gate, and the sample-gap threshold remains Proposed.
For the bounded owner-phone exploratory sequence, Run A completed without an observed
critical failure: Start/Stop/finalize succeeded, the WAV was valid, no whole-session loss
or classified AudioRecord error occurred, and raw deletion was verified. Run B may proceed
on the same build and device. Run C remains blocked until Run B evidence is reviewed.
