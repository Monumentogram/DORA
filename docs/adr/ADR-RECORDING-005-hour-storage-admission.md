# ADR-RECORDING-005 — One-hour storage admission

Date: 2026-10-08. Scope: owner-authorized Stage 8.6B storage preflight.
Status: implementation under verification; physical acceptance NOT_RUN.

The technical plan §14.4 requires a calculated recording budget before Start and
headroom for safe finalization. The previous free-MiB label and 16-MiB floor did
not explain the expected hour budget. ADR-PERF-002 records this unresolved gap.

Fresh recording Start, including a recovered recording's explicit continuation
Start, requires **141,777,216 available bytes**: the frozen **125,000,000 bytes per
hour** allowance plus the existing **16,777,216-byte** finalization headroom.
The check uses exact bytes, without rounding into admission. Missing/invalid
capacity rejects Start. The controller checks before requesting recording access
and again under the existing capture authority immediately before native Start.
The UI explains available versus required capacity, why recording cannot start,
and offers a refresh after the user frees space.

This is a calculated admission budget, not filesystem preallocation or a promise
that another application cannot consume the same storage. During active capture
the existing 16-MiB graceful interruption floor remains unchanged. Existing paused
Resume ownership and recording latency governance remain unchanged.

The one-hour minimum can reject a shorter planned recording when less space is
available; Stage 8.6B explicitly authorizes safe refusal at the accepted minimum
budget. No storage subsystem, retention policy, schema or automatic deletion is
introduced. The owner's 46 existing recordings remain protected.

The isolated test driver injects an available-byte supplier into the actual
controller and restores it in `finally`. It never fills the physical device.
Tests must prove refusal before microphone/FGS ownership or partial asset creation,
and preserve existing recordings. Physical acceptance still requires independent
measurement of storage growth for each of three long runs.

Battery efficiency remains deferred under ADR-PERF-002. This decision admits no
energy measurement, new VAD profile, future pipeline or Sheet closure.
