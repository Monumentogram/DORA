# POCO comparative battery instrumentation: partial evidence

**PARTIAL / BATTERYSTATS_COMPARATIVE_ONLY.** The physical short probes establish that
content-free battery diagnostics can be extracted. They do not admit a comparative
acceptance source or the suggested 1.25 ratio. `primarySource` and `acceptanceFormula`
remain null in `comparative-gate-disposition.json`.

The original hardware gate remains **NOT_EVALUATED / HARDWARE_ENERGY_COUNTER_UNAVAILABLE**.
The historical `poco-recording-8.6` blocked evidence and Samsung INCONCLUSIVE evidence
are unchanged. No Stage 8.6 PASS, device-matrix closure, physical 16-KiB acceptance,
full battery PoC closure, or endurance acceptance is claimed.

## Physical observations

| Probe | Observer span | Samples / largest gap | AccuBattery whole-session estimate | Batterystats target UID estimate |
|---|---:|---:|---:|---:|
| A-04 idle | 654.313 s | 36 / 488.230 s | 1 mAh, displayed 12 min | DORA idle UID 0 mAh; no CPU entry |
| B-01 minimal AudioRecord + FGS | 600.002 s | 41 / 15.003 s | 9 mAh, displayed 12 min | UID 10255: 2.15 mAh |
| C-01 actual DORA | 600.002 s | 41 / 15.003 s | 17 mAh, displayed 12 min | UID 10300: 2.35 mAh |

These columns describe different quantities and boundaries. No acceptance ratios
were calculated. AccuBattery values are rounded total-device session estimates;
they are not exact observer-subwindow consumption or measured DORA energy.
Batterystats values are modeled UID attribution. Its system consumed estimate was
0 despite nonzero UID estimates, so no additive reconciliation is claimed.

All measured charge-counter samples were 2,796,000 microAh. An unchanging supported
property does not establish zero physical consumption. Every energy-counter sample
was unsupported Long.MIN_VALUE. Capacity stayed 100 percent and was diagnostic only.
Current snapshots varied and were not integrated. The source preference was declared
before probes and was not changed in response to the numbers.

Each measured B/C sample was unplugged, noninteractive and thermal NONE. System
history also records the discharge and screen transitions. Whole discharge sessions
contain brief setup/reconnect screen activity outside the observer window. A's
large suspend gap is retained; periodic telemetry alone cannot prove continuous
thermal state. The observer holds no wake lock and its cadence is best effort.
No battery simulation command, security weakening, root, optimization exemption,
unofficial download, or permission escalation was used.

## Attempts and method changes

- A-01 and A-03 timed out before physical measurement; their dispositions are retained.
- A-02 discharged for 776.862 s but both Android and AccuBattery report screen-on
  throughout; the shell sampler ended before any measurement sample. Its 20 mAh
  AccuBattery result is retained and invalidated, never removed as unfavorable data.
- Before A-04, the failed shell-lifetime sampler was replaced with a self-target
  Android instrumentation process. The original and revised short protocols remain
  separate files. USB smoke validated API access only, not physical discharge.
- A-04, B-01 and C-01 are instrumentation probes, not accepted energy pairs.
- Read-only sysfs probes returned permission denied for every listed candidate;
  this does not prove those filenames are absent. No permissions were changed.
- Batterystats reset emitted a UID SecurityException while new epochs/zero counters
  were observed. This is retained as an OEM limitation, not called an error-free reset.

## Package and recording evidence

Official Google Play AccuBattery `com.digibites.accubattery`, Digibites, version
2.1.8 / 201008, installer `com.android.vending`, is bound in `result.json` and the
package receipt. Usage Access was granted through supported AppOps. Notification
permission was optional and not granted; no location, Accessibility, Device Admin,
VPN/profile/certificate, or optional account was added. Battery optimization policy
was preserved. AccuBattery remains installed as an unadmitted diagnostic candidate.
UI hierarchy extraction of session totals worked repeatedly. Per-app entries still
showed a wait-for-measurements state. Its own UI describes foreground app attribution;
screen-off DORA-specific attribution was not demonstrated.

The separate baseline package contains only an Activity, AudioRecord microphone
foreground service, notification and bounded PCM read/discard. Format is 16 kHz,
mono PCM S16LE. No VAD, persistence, model, Recovery, ASR or network permission.
Its successful receipt covers the complete B-01 observer interval: 13,254,400 frames,
zero short reads/errors, built-in route, no saved audio. Its package/UID differs
from DORA and it is not a production dependency.

C used the installed admitted product APK with SHA-256
`17c71b24ffd320d76a8ce4b886f67edb9fb6cf9a56053cc7ab17bb407ec36ed3`.
No production runtime, private AAR/model/profile or Android source changed.
Capture started and stopped through visible product controls. Finalizing counters:
15,444,800 canonical and durable frames, zero short reads/read errors,
canonical queue high-water 3, VAD high-water 6, 30,165 real VAD inferences,
3 deadline misses, no VAD/segmentation failure. UI reached saved, microphone was
released and recording FGS terminated. Readback/cleanup is separately receipted.

C's full recording plus finalization increased apparent `files + no_backup`
storage by 34,843,198 bytes. This includes directory metadata and excludes APK/cache;
pre-existing model bytes occur on both sides. It is not exact 600-second storage
growth and was not normalized into an hourly acceptance claim.

Process PID was unchanged during capture. Start/end PSS were 149,871/138,134 kB;
RSS from meminfo 249,664/207,140 kB; threads 48/47; FDs 176/140. Proc CPU tick deltas
were 38,869 user and 5,137 system at 100 ticks/s, over the larger recording observation
interval. No OOM/process death was observed. Start/end native/Java heap rows are
retained in C's receipt. No p50/p95/peak or CPU/memory threshold is invented.

## Verification and scope

Run `python -m unittest discover -s tools -p 'test_logical_recovery_poco_comparative.py'`.
The 13 parser tests and four admission guards cover missing/unsupported/invalid values, UID isolation,
charging, window identity/terminal binding, and complete baseline coverage.
The sealed repository validator retains all original Stage 8.6 evidence checks
and only admits the explicitly hashed comparative tooling/evidence additions.

Independent review rejected source admission and found no unresolved P0/P1/P2 in
the measurement observer/parser. Authenticated readback verified all 15,444,800 frames across chunks [0, 9,600,000)
and [9,600,000, 15,444,800). Product deletion then verified absence of 970 artifact
files, 194 key references and 194 run directories, while preserving all 46 other
assets. The three diagnostic helper packages were uninstalled and their absence
verified; AccuBattery remains installed. See `C-01-readback-deletion.json`.
Exact-SHA CI results are reported against the final publication commit; inherited
API28 cancellation is not PASS. Historical Recovery 414 is inherited, not newly
claimed as executed by these short probes. Applicable automated suites remain in
the existing four mandatory CI jobs; no product implementation changed.

Google Sheet was not written. No one-hour campaign or 200-cycle campaign was run.
Group D, Cloud and ASR remain NOT_STARTED. Security-restoration blocker remains
OPEN; PERF-REC-001 remains deferred/non-blocking. Bluetooth was restored to its
original ON state after the last probe. No POCO result here generalizes to other
Android devices.

The next task is to validate same-condition repeatability and defensible session
boundaries for a controller-derived estimate, or explicitly review a Batterystats
estimate interpretation. Only then can a separate comparative gate be frozen.

## Sources and units

- [Android BatteryManager](https://developer.android.com/reference/android/os/BatteryManager):
  charge counter microAh, current properties microA, energy counter nWh;
  unsupported `getLongProperty` is Long.MIN_VALUE.
- [Android dumpsys battery statistics](https://developer.android.com/tools/dumpsys#battery):
  physical unplug workflow and modeled attribution.
- [Android 14 BatteryStats source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/android/os/BatteryStats.java):
  locally observed checkin version 9/report version 36 subset; unrecognized layouts
  are rejected, never guessed.
- [Official AccuBattery listing](https://play.google.com/store/apps/details?id=com.digibites.accubattery):
  publisher/package identity and controller-derived measurement description.

Only content-free allowlisted receipts and source are published. Raw system dumps,
UI inventories containing unrelated recording history, APKs, private runtime
artifacts, signing material and audio stay outside Git.
