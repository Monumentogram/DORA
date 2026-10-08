# ADR-RECORDING-006: reduced autonomous POCO Alpha acceptance

Owner decision: **OD-86B-REDUCED-AUTONOMOUS-ALPHA-ACCEPTANCE** (2026-10-08).
Status: scope approved; implementation/preflight in progress; physical acceptance NOT_RUN.

This decision replaces the remaining physical campaign scope for the current owner-only
Alpha with exactly 60 real five-second Start/Stop/Finalize/authenticated-readback cycles
and one continuous 3,600-second screen-off DORA run. All 60 cycles must succeed; a failed
attempt stays in the denominator and cannot be replaced by restarting the campaign.
The sample does not demonstrate operational reliability of 99.5%.

The historical 200-cycle and three-hour requirements remain NOT_RUN/NOT_SATISFIED.
Reduced coverage increases residual risk of rare defects; Beta/release may require the
expanded campaign. The only permitted successful verdict for this task is
`PASS / POCO_REDUCED_ALPHA_PHYSICAL_ACCEPTANCE_READY`.

Canonical integrity, 9,600,000-frame CAP, 32,000-frame overlap, pinned real VAD, one logical
source, authenticated readback, 125,000,000 bytes/hour, no SEVERE thermal/OOM/process
death, hour-budget/low-space UX, notification controls, Doze, Battery Saver, permission
recovery, explicit Recovery Resume, preservation of 46 owner recordings and verified
test-only deletion remain required. CI/assertions/timeouts are not weakened.

An independent watchdog and bounded autonomous Stop policy are mandatory. Missing safe
state evidence is BLOCKED, never successful cleanup. Unverified durable prefixes remain
on the device for diagnosis. No PIN/App Lock/security-policy modification is authorized.
Existing accepted development settings are retained. A verified nonsecure swipe screen
may be dismissed through Android's standard window-manager command; a secure keyguard
or sensitive dialog requiring the owner stops the campaign instead of being bypassed.

Battery efficiency remains DEFERRED / NON_BLOCKING_FOR_ALPHA under ADR-PERF-002;
hardware microWh and comparative ratio remain NOT_EVALUATED. No battery experiments.
Sheet C78 stays unchanged, Stage 8 IN_PROGRESS, Group D/Stage 9/Cloud/ASR NOT_STARTED.
Security restoration remains OPEN and PERF-REC-001 deferred/non-blocking.

Starting local and remote SHA: `2cb4b470da88a52f2ce89ce0ae6c640c3222cfe1`.
PR99 remains draft/open/unmerged. The inherited working tree contains unfinished 8.6B
storage-budget and test-harness changes; it was not clean at scope transition. These are
preserved and must be reviewed, admitted and committed before the accepted campaign.
Earlier failed diagnostic receipts are immutable; this decision does not turn them PASS.
