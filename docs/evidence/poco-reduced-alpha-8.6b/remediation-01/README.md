# LONG01 / UI / build remediation

Disposition: **PARTIAL / LONG_CAPTURE_ROOT_CAUSE_UNPROVEN**. This is not Stage 8.6
acceptance, and no instrumented long retry has been authorized by an external review.

The frozen no-mutation inspector authenticated all 388 committed storage units:
31,040,000 readable frames, contiguous from zero, with no corrupt committed unit or
duplicate range. The source remains unfinalized with no pending catalog intent.
Three CAP closes occur at 9,600,000, 19,200,000 and 28,800,000 frames; overlap is
32,000 frames. All 8,550 original vault files remained byte-identical. The 46
original recording identities/frame/finalized-state map also remained identical.
No recording, Stop, Finalize, Resume or normal Recovery was invoked by the inspector.

These are distinct authorities: admitted 31,203,200; last observed durable
completion 30,960,000; authenticated catalog/readable prefix 31,040,000. One 80,000
frame append exists beyond the last observed completion. The accepted asynchronous
pipeline permits this timing, and synthetic tests reproduce delayed completion,
but historical timestamps/terminal enum were not retained. Backpressure remains
supported but unproven. No hypothetical writer optimization or cap increase was made.

The successor adds bounded content-free terminal diagnostics: exact native failure
enum and admission rejection boundary, separately timed control observation,
writer failure operation, pending append completions, append duration, queue
high-water, service/mic/permission/revocation state. The first failure survives
cleanup; diagnostics never advance durability. Native capture performs no logging
or file IO. A separate bounded worker writes the latest receipt to app-private
no-backup storage; unavailable/failed receipt storage is reported explicitly.
Process death before a receipt can be flushed remains a limitation.

The preflight regression is reproduced with the original visible-Cancel assertion.
Information and acknowledgement can scroll; Start/Cancel remain outside that
scroll. Tests cover recreation, compact/landscape/IME-sized height, 200% text,
insufficient storage and granted microphone permission without implicit Start.
The storage budget remains 125,000,000 + 16,777,216 bytes. Test semantics and the
138-method persistence / 22-phase Recovery inventories are unchanged.

Two independent fresh nonincremental builds of the starting SHA are byte-identical
to `b1073295...`. The predecessor-to-storage-UI incremental sequence reproduces
the physical `classes16.dex` and all 169 physical APK ZIP payloads exactly. Four
Compose methods have different changed/stability flags between incremental and
clean builds. Their harmlessness is not assumed, and old physical evidence does
not transfer to a successor APK. Future physical packaging requires two fresh
nonincremental builds with strict dependency verification and exact pinned inputs.

Physical successor smoke is **NOT_RUN**: ordinary authorized preflight automatically
loads Recovery and can reconcile LONG01. Independent review confirms there is no
exact-source exclusion/read-only fence on that normal UI path. The original
installed APK is preserved. A reviewed isolation or separately authorized source
disposition is required before a normal-UI smoke; no owner phone action is needed
for the remaining host/CI work.

Evidence files here are successor receipts. Historical failure evidence is unchanged.
CI authority is the exact publication SHA's four GitHub Actions jobs and their
runtime receipts; host tests do not certify physical endurance. The terminal chat
report identifies that SHA/run and any incomplete checks.

Sheet C78 unchanged. Stage 8.6 remains NOT_READY. Battery efficiency remains
DEFERRED / NON_BLOCKING_FOR_ALPHA under ADR-PERF-002; hardware microWh and ratio
NOT_EVALUATED. Security restoration OPEN; PERF-REC-001 deferred/non-blocking.
No hour run, new cycle campaign, Stage 9, Group D, Cloud or ASR was started.
