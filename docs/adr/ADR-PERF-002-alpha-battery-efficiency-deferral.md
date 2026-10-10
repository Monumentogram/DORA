# ADR-PERF-002 — Battery efficiency deferred beyond Alpha

Date: 2026-10-05 (Europe/Moscow). Authority: explicit Project Owner decision
`OD-86-ALPHA-BATTERY-DEFER`. Scope: current owner-only Alpha on POCO M5.

## Decision

Battery efficiency is **DEFERRED / NON_BLOCKING_FOR_ALPHA**. The Owner accepts
the unresolved comparative-efficiency risk for Alpha and moves validation to
Beta or a separately authorized performance stage, tracked as **PERF-REC-002**.
This is a scope/risk decision, not a measured battery PASS.

| Field | Current disposition |
|---|---|
| batteryFreshness | PASS / OPERATIONALLY_VALIDATED |
| batteryRepeatability | DEFERRED / NOT_RUN |
| comparativeSourceAdmission | DEFERRED |
| doraBaselineRatio | NOT_EVALUATED |
| hardwareMicroWhGate | NOT_EVALUATED / HARDWARE_ENERGY_COUNTER_UNAVAILABLE |
| batteryEfficiencyGate | DEFERRED / NON_BLOCKING_FOR_ALPHA |

Stop six 600-second repeatability runs, USB disconnect/reconnect experiments,
Wi-Fi debugging remediation for measurement, the one-hour battery campaign and
additional AccuBattery/Batterystats investigations. Resumption requires a new
Owner decision. This supersedes the earlier demand for an admitted comparative
source before Alpha progression; it does not alter the historical source oracle,
three-pair arithmetic, thresholds or definition of a physical energy PASS.

## Evidence and precise limits

The successful `freshness-isolated-02` test establishes operational response of
current `dumpsys batterystats -c` in one isolated CPU-worker diagnostic. Six reads
took 459/459/490/365/364/443 ms; the raw UID CPU delta was 18,614 ms against a
process bracket of 18,568–18,570 ms within the prospectively frozen 100 ms bound.
The modeled pulse was 0.00883 mAh with displayed late delta 0.0000 mAh. The
retained host connection gap and longer-than-target quiescent waits remain
explicit. This is not repeatability of DORA energy, hardware accuracy, a bound
on every backend input's age, or continuous host monitoring.

Collector USB smoke succeeded, including current-stat acquisition of 241 ms and
history acquisition of 214 ms. USB diagnostics are not discharge evidence. Local
collector/parser work remains diagnostic tooling, not a fully admitted six-run
campaign. The six-run protocol was never finally sealed or executed.

Historical C-01 demonstrated one short product recording with 15,444,800 frames,
real VAD, successful Stop/readback/deletion and sampled thermal status NONE.
It did not demonstrate one-hour stability, hourly storage, 200-cycle reliability
or a controlled comparative energy ratio. The Owner's observation of no obvious
drain/thermal anomaly is accepted as risk rationale only; limited observations
cannot establish absence of abnormal drain or overheating generally.

Android ENERGY_COUNTER remains unsupported; CHARGE_COUNTER was unusable for
delta; AccuBattery is a controller-derived whole-session estimate; Batterystats
is modeled per-UID attribution. No percentage/current snapshot is converted to
energy. Every historical blocked/partial/invalidated result remains unchanged.
[Evidence and attempt ledger](../evidence/poco-battery-alpha-deferral/summary.json)
bind the retained sources without publishing private logs, audio or artifacts.

## Stage 8.6 closure assessment

**NOT_READY / NON_BATTERY_ACCEPTANCE_GAPS_OPEN**. Do not assign
`PASS_WITH_DEFERRED_BATTERY_EFFICIENCY_GATE` yet. The battery waiver is narrow:

- The 200 physical Start/finalize campaign and its loss denominator are absent.
- Three clean hour-long production screen-off runs and corresponding canonical
  integrity, CAP rotation, storage and sustained resource/thermal evidence are absent.
- Hour-budget/free-versus-required storage UX and injected low-storage checks
  remain unfulfilled; a 16 MiB floor/free-MiB display is not proof of this gate.
- Stage 8.6 Doze/Battery Saver, background notification controls and bounded
  physical Recovery regression are not closed by the short instrumentation probe.
- Final physical-acceptance APK/HEAD binding, complete physical campaign review,
  publication audit and Sheet closure have not occurred.

The [machine-readable gate audit](../governance/poco-battery-alpha-deferral.json)
separates inherited/short-run evidence from unmet physical gates. No replacement
physical campaign, production fix or broader waiver is inferred here. A new scoped
non-battery completion task must define the execution arrangement while preserving
these criteria, or obtain an explicit further Owner decision for any changed gate.

Stage 8 stays IN_PROGRESS. The development-security restoration blocker stays
OPEN and independently blocks final/signed Alpha closure. PERF-REC-001 remains
deferred/non-blocking. VAD AAR/model/profile, production source and all historical
Recovery/segmentation/security assertions remain unchanged.

## Next product work and publication

The immediate unfinished work is the non-battery remainder of Stage 8.6, then the
separately scoped Stage 8 closure assessment. The admitted Alpha roadmap identifies
the next product stage as **Stage 9 — ASR/versioning**, including optional Local
package installation/integrity/removal/account-free operation (9.1C), recognition
from the canonical source, immutable transcript versions and safe editing.
Cloud runtime retains its consent/authentication/upload/backend/security gates.
See [accepted development admission](../stage0/DORA_ALPHA_DEVELOPMENT_ADMISSION_6_3_V0_1.md).
This is a roadmap description, not authorization to start Group D/Cloud/ASR.

Current branch/PR remains `stage/8.6-poco-recording-acceptance`, #99,
DRAFT / OPEN / UNMERGED, stacked on accepted Stage 8.5. No Sheet write. Additive
governance and narrowly sealed validator changes only; prior document bodies and
historical evidence are byte-preserved. CI verifies source/regressions, never
physical energy. Final commit/CI outcomes are reported externally against exact SHA.
