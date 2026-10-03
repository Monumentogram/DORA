# ADR-PERF-001 — Alpha recording-control latency acceptance

Date: 2026-10-03. Authority: explicit Project Owner decision in this task.
Disposition: **ACCEPTED_FOR_ALPHA / INSTANT_CONTROL_FUNCTIONALLY_RESOLVED**.
Product source reviewed: `1272ea212308eeb8cb9b27e81aa35bf939967d8f`, PR #94,
`stage/8.3-instant-recording-controls`; PR remains DRAFT and UNMERGED.
Publication completion is certified only by the later external receipt.

## Exact Owner decision

> The original product defect, where Pause/Resume visibly took about 1.5–2.2 seconds, is considered resolved for Alpha.

> Current remaining latency variance of roughly 10–15 ms around the previously frozen presentation thresholds is accepted for Alpha because no material user-visible defect has been demonstrated and further production optimization currently has an unfavorable risk/reward profile.

> The historical strict thresholds remain factual historical criteria and are not retroactively declared passed.

> Further micro-latency optimization is deferred to a separate performance-hardening activity.

## Evidence and limits

- [ADR-AUDIO-005](ADR-AUDIO-005-instant-recording-controls.md) documents the
  original approximately 1.5–2.2 s Pause delay and separation of capture control
  from encrypted durability. Final SAVED still requires durable verification.
- On the above product SHA, historical observed Resume ACK was approximately
  111.6–113.7 ms against the 100 ms limit; visible PAUSED approximately
  250.7–258.9 ms against the 250 ms limit. These are observed values from a
  partial campaign, not an asserted complete 30-operation p95 distribution.
- Status display-list writes occurred materially before presentation: Resume
  ACK 34.53/45.26 ms versus 111.62/113.71 ms; PAUSED 189.57/182.28 ms versus
  250.72/258.86 ms. A draw witness is after drawContent, not draw start.
- A read-only POCO check reported a 60 Hz active display mode. It does not prove
  an unchanged refresh mode throughout every prior sample. One problematic
  main-thread frame took about 31.45 ms wall time, including recomposition
  12.78 ms and layout measure 10.73 ms; this is not CPU-time attribution.
- Diagnosis: **DIAGNOSIS / INSUFFICIENT_EVIDENCE**. Exact StateFlow publication,
  Compose observation and onClick entry were not instrumented; the final cause
  was not proven. No evidence established materially noticeable remaining
  variance. This absence is not proof of universal human imperceptibility.
- [Content-free evidence and retained-source hashes](../governance/recording-control-alpha-acceptance.json)
  preserve the measured basis. Raw private traces, audio and video are not published.
  The campaign used owner-authorized debug no-PIN development mode and cannot
  certify release App Lock or production authentication.

## Historical criteria remain factual

Historical strict campaign disposition remains
**NOT_READY / PHYSICAL_INSTANT_CONTROL_GATE_FAILED**. Its receipts, failed or
missing attempts, measurements, prospective denominators and thresholds remain
unchanged. The old gate is not technically passed; this decision does not grant
`PASS / PRODUCT_RECORDING_INSTANT_CONTROL_READY`.

| Historical p95 metric, 30 operations per control | Limit (ms) |
|---|---:|
| Pause ACK / admission / microphone release / visible PAUSED | 100 / 50 / 200 / 250 |
| Resume ACK / native RECORDING / first PCM / visible RECORDING | 100 / 150 / 300 / 300 |

Durability catch-up remains separate, with no 250 ms criterion. No threshold,
runtime, security, capture, persistence or recovery behavior changes here.

This later governance disposition supersedes only the earlier requirement
that the strict micro-latency campaign must pass before Alpha progression and
the Stage 8.3 Sheet disposition can be updated (ADR-AUDIO-005 / ADR-DEV-002).
It does not change what counts as passing that campaign. Earlier dated
PENDING/P1 entries in status/backlog remain historical; the strict micro-latency
work is now deferred rather than an active Alpha blocker.

## Deferred work and stage state

**PERF-REC-001 — Pause/Resume presentation micro-latency hardening** is
DEFERRED_AFTER_ALPHA_ACCEPTANCE, non-blocking Alpha, and outside Stage 8.4.
Investigate input-to-presentation variance; add precise publication/observation
timing only if necessary; profile main-thread/recomposition/layout; optimize
only for a measurable user or release need. Re-evaluate before public release.
The Owner accepts the present risk/reward trade-off for Alpha; no unsupported
root cause or optimization is selected by this decision.

- 8.3 = PASS / functional recording controls.
- 8.3 latency remediation = OWNER_ACCEPTED_FOR_ALPHA.
- 8.4 = NOT_STARTED; READY_TO_START as a separate task after publication.
- Stage 8 = IN_PROGRESS.
- DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE = OPEN: final Alpha closure and signed
  Alpha security acceptance still require the existing ten restoration checks.

## Publication and validation

Docs/governance only. The narrow governance validator admits this exact additive
record and byte-preserved historical status, while retaining all inherited
source/security/threshold checks. The existing workflow is unchanged; its
normal exact-SHA CI is still required, without a new physical device campaign.
Neither docs validation nor the unchanged runtime CI is new physical latency
or production-authentication evidence.

Read the live Sheet row and neighbors before updating existing descriptions;
preserve C74=ПРОЙДЕНО and Stage 8.4's planned status. Do not recreate deleted
columns. Publish the external governance receipt only after the commit is
pushed, consistency/required CI passes and exact Sheet readback succeeds.
