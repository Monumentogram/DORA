# ADR-AUDIO-005 — Independent capture and asynchronous durability

Status: owner-approved architecture ruling; implementation candidate, PENDING_FINAL_PUBLICATION.
Parent: `3e1603dbad9083977b1e08b6a5cce9a52de2148d`. Scope: Stage8.3 remediation v2 only. Stage8.4 NOT_STARTED.

## Problem and ruling

PR93 measured approximately1.5–2.2s Pause completion because PAUSED waited for encrypted tail persistence. The owner's v2 ruling supersedes only ADR-AUDIO-004 decision1's durable-Pause prerequisite. It restores the independent Capture and Durability axes of design specification18.1 and the microphone-release Pause semantics of technical plan30.1. All predecessor evidence remains historical and immutable.

PAUSED means admission fenced, exact accepted boundary captured, and native reader stopped/released. PAUSED/PENDING and RECORDING/PENDING are normal. SAVED still requires all preceding writes and verified finalization. No SQLCipher policy, fsync, key validation, journal, readback or Recovery checkpoint changes.

## Ownership and ordering

One control executor owns mutable RecordingSession state, native control commands and bounded memory-only queue draining. It seals every transport unit's bytes, ordinal, physical identity, physical first frame and logical first frame before enqueueing. One separate persistence executor writes these immutable units in order. Completion returns to the control executor and advances only confirmed durable frames. Reserved/sealed offsets are independent of durable offsets, including when an older physical segment finishes after a newer segment begins.

Each admitted block carries native generation, physical identity and exact admission first frame. Admission and fence share the same short monitor. Memory-only drain validates identity and logical continuity before assembly. Pause drains the old generation after native release, seals its tail and publishes PAUSED without waiting for storage. Resume allocates a fresh physical identity and retains the existing exact-current authorization checks. No overlapping native readers or concurrent writer calls are introduced.

The admission budget remains charged until durable completion, across raw queue, assembler, sealed queue and in-flight write. It bounds both PCM bytes (512000) and outstanding read blocks (320), thereby also bounding nonempty sealed-task metadata. Empty pause/resume cycles enqueue no writes. Exhaustion produces PERSISTENCE_BACKPRESSURE and terminates capture; accepted data cannot be silently dropped to keep capture running.

The first writer failure atomically fences subsequent writes and native starts, requests microphone stop immediately and reports FAILED/INTERRUPTED to the control owner. Queued plaintext is cleared under the existing typed-failure/Recovery disposition. Late completion cannot revive capture or publish SAVED. Access retirement waits for both native release and the persistence executor's settlement barrier, avoiding an Access.close monitor wait on the control path.

## Product decision

While paused and behind, show “Запись приостановлена” with “Сохраняем последние секунды…”, with enabled Resume. While resumed and behind, show “Запись продолжается” with “Сохраняем…”, with enabled Pause. Existing confirmed-prefix information remains explicitly limited to durable frames. The timer counts admitted frames; paused wall time and durability delay add zero frames. Waveform updates stop at the admission fence and require new real samples after Resume.

The owner-approved development marker is separate from acceptance. Physical acceptance uses ordinary system authorization; the marker is restored only after test capture is stopped. Background/lock/revocation/process death preserve fresh-authentication requirements. FLAG_SECURE remains enabled.

## Prospective acceptance

Thirty Pause and thirty Resume operations, no exclusions or missing presentation associations. Nearest-rank p95 uses sorted sample29 of30. Pause limits: ACK100ms, admission50ms, native release200ms, visible PAUSED250ms. Resume limits: ACK100ms, native RECORDING150ms, first PCM300ms, visible RECORDING300ms. Durability catch-up is separately measured, without a250ms threshold. Rapid physical cycles, final visual verification, exact ordering/race tests, complete inherited regressions, exact-SHA CI, independent review and leak audit are mandatory. Until all pass, no PRODUCT_RECORDING_INSTANT_CONTROL_READY claim or Sheet closure update is permitted.
