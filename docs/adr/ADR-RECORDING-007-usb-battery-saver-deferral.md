# OD-86B-LITE-BATTERY-SAVER-DEFERRAL

Owner approved on 2026-10-08. This narrow decision supersedes the Battery Saver
requirement in ADR-RECORDING-006. The retained USB-powered diagnostic found that
the POCO did not enable Saver despite a successful command. Its outcome is
`DEFERRED / USB_POWER_CONSTRAINT`, not PASS and not a DORA recording defect.
Do not repeat the diagnostic, unplug USB, simulate unplugging or weaken security.

Continue the autonomous 60-cycle and one-hour screen-off non-battery campaign.
Reuse the passed screen/readback/deletion/watchdog diagnostic. Doze may be
`DEFERRED / AUTONOMOUS_DEVICE_CONSTRAINT` if it requires owner participation.
All other mandatory integrity, storage, thermal, preservation and safety gates
remain unchanged. Existing failed and invalidated attempts remain historical.

ADR-PERF-002 remains unchanged: battery efficiency is deferred/non-blocking for
Alpha; hardware microWh remains not evaluated/unavailable. No battery experiments.
The permitted reduced terminal verdict is
`PASS / POCO_REDUCED_AUTONOMOUS_NON_BATTERY_ACCEPTANCE_READY` only after all remaining
mandatory gates, exact-SHA CI and independent review. It does not close the former
full Stage 8.6 gate. Sheet C78 remains untouched; no next stage is authorized.

Before the main series, bind the product APK to its complete runtime source
manifest and the helper to its source manifest, and seal the run protocol. Keep
the unfinished source/evidence for reviewed publication. No result may be called
final-SHA acceptance until the final runtime/source relationship is verified.

The short cycles last at least 5,000 ms and admit at least 80,000 canonical frames.
The frame wait has an additional 5,000 ms deadline; failure halts the fixed series.
The short-cycle watchdog remains 240 s per attempt. The hour has a predeclared
5,400 s total driver budget: at least 3,600 s uninterrupted screen-off capture,
then bounded finalization, authenticated readback and verified deletion. This is
a physical-driver budget, not a change to any CI or instrumentation-suite timeout.
