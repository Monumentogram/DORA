# Stage 8.6 — POCO physical recording acceptance preflight

**BLOCKED / POCO_ENERGY_MEASUREMENT_UNAVAILABLE**

No physical acceptance PASS is claimed. The mandatory energy-source preflight
failed before any recording campaign began. No product code, private artifact,
model/profile, oracle arithmetic, CI workflow, parent branch or historical
Samsung evidence was changed. Stage 8 remains IN_PROGRESS; Group D is not started.

## Source and device

The branch `stage/8.6-poco-recording-acceptance` starts at exact accepted Stage 8.5
`b8cac85f30f446d98c1a6b1d21490d8c10bc5b41`. Its draft PR targets
`stage/8.5-logical-recording-recovery`. PR #98 was independently read as
OPEN/DRAFT/UNMERGED at that SHA; #97 remains OPEN/DRAFT/UNMERGED at
`b035d8e35e3fc604ae3b805a1cfd23ea66963804`. Neither was rewritten or merged.
The baseline's PENDING_FINAL_PUBLICATION status text is historical: the owner
and separate accepted Stage 8.5 receipt establish predecessor PASS. Existing
status-validator headers are retained. This new evidence records only Stage 8.6.

[Device receipt](device-preflight.json): Xiaomi POCO M5 (22071219CG), Android 14,
API 34, arm64-v8a, firmware V816.0.6.0.ULURUXM, patch 2024-10-01, runtime pages
4096 bytes. Only a generic build fingerprint digest is published. No serial,
Android ID, MAC, account, network address or other persistent identifier is stored.
Free space was 95,100,592,128 bytes at the recorded snapshot. Thermal status was
NONE with no override. Battery Saver was OFF, Wi-Fi and Bluetooth were ON.
USB power was present throughout diagnostic checks, so none is an eligible run.
Battery percentage, voltage, temperature, current and charge are diagnostics only.

The installed accepted Stage 8.5 APK hash was read directly on the device:
`17c71b24ffd320d76a8ce4b886f67edb9fb6cf9a56053cc7ab17bb407ec36ed3`.
It matches the prior Stage 8.5 binding receipt. No Stage 8.6 campaign APK was
built, installed or tested. This is not a new physical APK acceptance claim.

## Why the energy gate is blocked

The read-only [framework probe](EnergyProbe.java) invokes the local Android
`BatteryManager.getLongProperty` implementation. Its runtime energy-property ID
is 5. The public API defines that property as remaining energy in nWh and returns
`Long.MIN_VALUE` when unavailable. Local results are preserved in the
[first probe](framework-energy-probe.txt) and
[screen-off probe](framework-energy-probe-screen-off.txt): all six energy reads
returned `-9223372036854775808`; the charge-counter control returned a positive
2,796,000 microAh. No sentinel was converted to zero or energy.

The bounded second diagnostic was bracketed by `mWakefulness=Dozing` and
`mWakefulness=Asleep` [power-state receipts](screen-off.txt), with USB powered.
These snapshots support the diagnostic context; they do not prove an entire
one-hour noninteractive interval or production screen-off capture. The prior
awake screen state was restored after the diagnostic.

Two completed 15-second Perfetto traces requested `android.power`:

| Probe | Battery packets | Rail descriptors / values | Energy consumer descriptors / values |
|---|---:|---:|---:|
| [Rails](perfetto-summary.json) | 16 charge/current | 0 / 0 | Not requested |
| [Rails plus consumers](perfetto-consumer-summary.json) | 16 charge/current | 0 / 0 | 0 / 0 |

The second config explicitly enables `collect_energy_estimation_breakdown` as
well as rails. Its one consumer packet contains only an empty descriptor.
Both completed traces were independently decoded with successful final flush
and no discarded/invalid data. Raw traces remain local; public summaries retain
their exact hashes. `dumpsys powerstats` returned empty output; direct battery
sysfs listing was permission-denied. Batterystats exposed estimated mAh and
microcoulomb labels, not an accepted measured microWh source. No conversion from
percentage, charge times nominal voltage, estimated mAh, or current sampling was
invented. No root, firmware change or battery-state simulation was attempted.

Therefore no stable positive physical energy source, exact conversion, direction
validation or common baseline/DORA source could be admitted on this setup.
This conclusion covers the accessible sources tested, not every possible
external laboratory instrument. Resume requires a defensible source and physical
non-charging control before any accepted pair starts.

References: [Android BatteryManager](https://developer.android.com/reference/android/os/BatteryManager),
[Android 14 implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/android/os/BatteryManager.java),
[Perfetto v34 power config](https://raw.githubusercontent.com/google/perfetto/v34.0/protos/perfetto/config/power/android_power_config.proto).

## Preserved attempts and frozen intended protocol

[Attempts](attempts.json) preserves all seven preflight attempts, including the
two rejected Perfetto setups (config-file permission denial; unsupported VOLTAGE
enum). These are setup diagnostics, not completed valid energy runs. Campaign
attempts = 0; accepted pairs = 0. No unfavorable completed run was discarded.

[Protocol](protocol.json) predeclares PAIR-01 through PAIR-03 and order
BASELINE-01, DORA-01, DORA-02, BASELINE-02, BASELINE-03, DORA-03, with a hashed
quiet-room [fixture](fixture-manifest.json). It retains the existing host oracle
and exact `4 * captureMicroWh <= 5 * baselineMicroWh` condition for **each** pair.
No mean, median, best-run selection or cross-pair aggregation is allowed.
Storage is frozen at 125,000,000 bytes per 3,600-second slice, each run separately.
Every control must match, slices must be physically unplugged and screen-off for
3,600.000 monotonic seconds, and invalid attempts must remain visible.
The source ID/conversion and unresolved actual radio/signal controls are explicitly
null/unset: this is a predeclared plan, not an executable/admitted campaign.

The intended isolated baseline remains AudioRecord + microphone FGS + notification
+ bounded read/discard, PCM S16LE 16 kHz mono, without VAD, persistence,
segmentation, Recovery or ASR. No comparator APK was implemented after the
mandatory source precondition failed. The DORA numerator must use the complete
accepted product recording path. No reduced or mocked product run was performed.

A narrow evidence-only successor validator admits this new branch while sealing all
public receipts and allowing only its explicit evidence/tooling paths. The historical
validator receives only a reversible three-line dispatch. Two historical Recovery
branch guards also admit the new branch only through that same sealed current-tree
validator; removing those two additions restores the exact accepted file. The first
CI attempt on 793fe04 failed at this remaining branch guard; that failure is preserved
as infrastructure history, not an energy attempt. The successor checks the true current
CI ref/SHA, preserves every production/workflow/oracle byte, and cannot issue PASS
for physical acceptance. The first local validation failed with Wrong Stage8.5 branch;
this was reproduced before the scoped successor fix.

The unchanged Stage 0 oracle compiled under JDK 17 with `-Xlint:all -Werror` and
passed `-ea -Xverify:all`: [result](oracle-result.txt). This is host arithmetic and
contract verification only, not new physical battery evidence.

## Remaining acceptance work

No 200-cycle reliability, one-hour capture, storage measurement, Doze/Battery
Saver recording, Recovery smoke or physical VAD smoke was run. No test audio was
created. Whole-recording loss, corruption and gap/duplicate counts are **unknown**,
not measured zero. DORA microphone was idle at the preflight snapshot.

Static storage inspection found a 16 MiB Start/ongoing floor in
`RecordingController` (lines 201, 606, 782) and free-MiB display in
`RecordingScreen` (line 413). The required hour-budget/free-versus-required UX
and injected low-storage acceptance remain unfulfilled. This is an open item for
resumption; no accepted budget gate or product remediation is claimed here.

## Requested final-report ledger

This table accounts for every requested field without substituting predecessor
evidence or diagnostics for a new campaign. Final HEAD, PR, audit/review and CI
publication metadata are recorded in the final handoff outside this commit.

| Item | Disposition |
|---|---|
| 1 verdict | BLOCKED / POCO_ENERGY_MEASUREMENT_UNAVAILABLE |
| 2 baseline SHA | b8cac85f30f446d98c1a6b1d21490d8c10bc5b41 |
| 3 final HEAD | Exact evidence commit in final handoff; no runtime delta |
| 4 branch / PR | stage/8.6-poco-recording-acceptance; new stacked draft |
| 5–6 campaign source / APK SHA | null; campaign never started |
| 7 profile | device-preflight.json; physical POCO, API34, arm64, 4-KiB |
| 8 private inputs | Required AAR 64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db; model 1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3; no packaging/re-admission |
| 9–10 baseline comparator / APK | Design above; not built, APK hash null |
| 11 source and unit proof | Documented nWh API unsupported locally; Perfetto empty; no admitted source |
| 12–14 pairing / attempts / valid pairs | protocol.json / attempts.json / none |
| 15–17 PAIR-01 baseline / DORA / ratio | null / null / null; NOT_RUN |
| 18–20 PAIR-02 baseline / DORA / ratio | null / null / null; NOT_RUN |
| 21–23 PAIR-03 baseline / DORA / ratio | null / null / null; NOT_RUN |
| 24 thermal start/max/end for runs | No energy runs; preflight NONE only |
| 25 screen-off proof | Bounded diagnostic snapshots only; hour gate NOT_RUN |
| 26–27 Start / finalize | 0/200 executed; finalization denominator 0, no success ratio |
| 28 whole-recording losses | null; no campaign, no zero-loss acceptance claim |
| 29–35 frames / CAP / gaps / reads / VAD / queues / storage | NOT_RUN; all campaign measurements null |
| 36 storage budget | Existing floor/free-MiB display; hour-budget UX and injected tests open |
| 37–39 memory / CPU / FDs and threads | NOT_RUN; no thresholds invented |
| 40 FGS/mic lifecycle | Idle mic at preflight; recording lifecycle NOT_RUN |
| 41–42 Doze/Battery Saver / Recovery | Recording functional checks NOT_RUN |
| 43 automated regression | Unchanged host oracle PASS; no new full runtime suite result |
| 44 historical Recovery 414/414 | Predecessor evidence retained; not rerun or reclassified |
| 45 API28/API36 | No local rerun; new exact-SHA CI status in final handoff |
| 46 exact-SHA CI | Required before PASS; status in final handoff; no physical measurement claim |
| 47 independent review | Blocker/source evidence reviewed; full physical acceptance unfulfilled |
| 48 publication audit | Explicit file allowlist, privacy canaries and final diff audit in handoff |
| 49 cleanup | No audio created; four diagnostic device files deleted and absence verified |
| 50 Sheet | Not written; C78 not marked ПРОЙДЕНО; no fresh readback claimed |
| 51–52 Group D / Cloud / ASR | No work started; NOT_STARTED within this task scope |
| 53 VAD runtime/model/profile | Unchanged; no new physical verification |
| 54 security restoration | DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE remains OPEN |
| 55 PERF-REC-001 | Deferred/non-blocking |
| 56 support claim | No all-device, D1–D7, beta-crash-free, 16-KiB, multi-device battery or eight-hour claim |

Earlier Samsung POC-CAPTURE-001 remains INCONCLUSIVE. Stage 8.3/8.4/8.4C/8.5
accepted results remain historical predecessor evidence. Stage 8.6 is not closed.
