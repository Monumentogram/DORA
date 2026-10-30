# ADR-0030 — Thirty-minute owner Alpha

Date:2026-10-30. Authority: owner's explicit Stage12 security completion and long-recording request. Source base b310283. Implementation decision; qualification remains evidence-gated.

Supersede ADR-RECORDING-011's300s product capture cap prospectively with1800s. Preserve five-second encrypted transport, the256,000-frame outstanding budget, technical ten-minute rotation, schemas, key identities, user edits and recovery. Historical Stage8.6/LONG02 failures remain unchanged. This is the requested intermediate30-minute scope, not a two-hour admission.

Replace whole-long-source PCM allocation for Local ASR/playback with authenticated windows of at most4,800,000 frames. Each load verifies the entire source before returning samples. Decode outside the vault callback and publish complete ordered text only. Playback owns at most two windows with bounded prefetch and one AudioTrack. Retain the pinned native Whisper and its300s input boundary. Additional I/O and initial latency are accepted implementation tradeoffs; do not weaken authentication/durability to optimize them.

Preview retains com.monumentogram.dora.alpha.preview and the owner certificate; versionCode8. No migration, production deployment, personal phone installation or merge is authorized here. Test-signed emulator continuity and owner-signed artifact identity are distinct evidence.

Cloud Android remains visibly capped at300s pending a separately authorized compatible deployment and bounded client upload. Candidate backend validation permits1800s, with no change to existing daily cost limits. No long Cloud qualification is claimed. ADR0012/0025/0029 retention exceptions remain; issue125 and required privacy/control approvals remain external blockers.

See [security matrix and lifecycle](../STAGE12_LONG_RECORDING_SECURITY.md) and [implementation plan](../superpowers/plans/2026-10-30-long-recording-security.md). Final statuses, limits and artifact identity must refer to executed evidence.
