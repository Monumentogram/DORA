# Stage 8.5 implementation plan

Specification: owner Stage 8.5 request and ADR-AUDIO-007.
Implementation proceeds in this session; independent adversarial review is separate.
Baseline b035d8e35e3fc604ae3b805a1cfd23ea66963804; draft PR targets
stage/8.4c-logical-recording-chunks. Source status PENDING_FINAL_PUBLICATION.

## Review focus

- PCM commit before metadata, dangling/empty OPEN, CAP coincidence and repeated kill.
- One bad candidate, >20 pagination, deletion/scan/start/revocation races.
- Pending finalization and strict prefix verification; no middle-gap stitching.
- VAD discontinuity after 89 seconds of silence; no invented semantic closure.
- Five-second storage units do not establish five-second total product tail loss.

## Tasks

1. Snapshot and metadata reconciliation (core/audio recording, journal, bridge,
   logical projection). First test explicit RECOVERY pairing, zero abort, exact
   source ranges, replay idempotence, and malformed metadata. Run audio JVM suite.
   Implement one leased recovery operation with typed per-candidate disposition;
   preserve legacy canonical continuation and original-source semantics.
2. Prospective OPEN ordering and UI (RecordingSession, RecordingRecoveryCard).
   Test OPEN before first append, crashes at every metadata boundary, new epoch
   on explicit Resume, automatic authorized discovery without automatic capture,
   permission/acknowledgement and accessible status text. Run audio/app/VAD tests.
3. Add encrypted Android integration and host-kill campaign with independent new
   phase denominator: append/bootstrap/publication/readback/catalog, metadata,
   CAP, Pause, Resume, VAD degradation, finalization and deletion. Assert exact
   prefix, source identity, final reference, chunks, pagination and idempotency.
   Execute API28/API36 and controlled POCO cases with admitted private VAD.
4. Add sealed successor governance/CI and content-free receipts; retain historical
   source/evidence and inherited blanket failures separately. Run mandatory JVM,
   Python, formatting, Detekt, lint, strict dependency, SBOM/native/search gates.
5. Independent review; resolve P0/P1/P2. Bounded positive-canary leak audit, commit,
   stacked draft PR, exact-SHA four-job CI and artifact audit. Only full acceptance
   allows C77/D77 Sheet write after A76:F79 reread and neighboring-row verification.
   External receipt alone may declare PASS. Do not start 8.6.
