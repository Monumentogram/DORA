# Limited Alpha reliability disposition

Owner decision: 2026-10-20, ADR-RECORDING-011. Parent evidence:
`6f5e87317b163e4567ffa76da3cd3191bcfac826`, PR99 C3 result and
[exact C3 CI](https://github.com/Monumentogram/DORA/actions/runs/38010786387).
This is a prospective limited profile, not retroactive acceptance of failed tests.

| Backlog item | Disposition | Required future evidence |
| --- | --- | --- |
| 1h/3h/8h uninterrupted capture | DEFERRED_TO_BETA | Separately authorized endurance profile and safe devices |
| Industrial persistence latency and stall diagnosis | DEFERRED_TO_BETA | Reproducible cause and measured remediation; do not repeat C3 without a new question |
| Thousands-of-blocks performance | DEFERRED_TO_BETA | C3 improvement evidence retained; no new benchmark campaign here |
| Full Android/OEM/ABI matrix | DEFERRED_TO_BETA | Actual supported-device evidence, not inferred from API28/36 x86_64 |
| Battery/thermal efficiency | DEFERRED_TO_BETA | Separate controlled physical energy experiments |
| 200 physical cycles / 99.5% reliability | DEFERRED_TO_BETA | Prospectively fixed population, denominator and failures |
| LONG01/LONG02 root-cause investigation | Separate reliability work | Historical failures remain failures; LONG02 cause NOT_PROVEN |
| LONG02 data custody | Separate preservation task | Owner decision and verified protection before any physical access; prepared successor is NOT_INSTALLED |
| PERF-REC-001 | Existing deferred performance item | Separate presentation-latency hardening if needed |

The former 60/60 short physical cycles remain historical PASS and are not repeated.
The hour-recording failure remains `NOT_READY / POCO_SCREEN_OFF_CAPTURE_FAILED`.
No physical POCO operation, new recording, battery experiment or historical audio
mutation is part of this task.

Exact incompatible scenarios retained unchanged:

- Four JVM session tests named in `stage8-alpha-limited-v1.json`: two technical
  rotations, durable physical CAP/overlap, and VAD-on/off across two CAPs.
- Android `SegmentationPersistenceTest#encryptedRotationPreservesCanonicalHashAndExactOverlapAcrossReopen`.
- Four logical-recovery death phases: BEFORE_CAP, CAP_CLOSED, CAP_OPEN_EMPTY,
  CAP_OVERLAP.

These require >300 seconds in a session that is now capped at 300. They are
excluded only by the explicit limited acceptance profile; original runners,
contracts and source remain available with their original defaults. The technical
timeline, source projection, overlap, crypto and Recovery algorithms are unchanged.
Their applicable unit tests and short fault cases still run.
