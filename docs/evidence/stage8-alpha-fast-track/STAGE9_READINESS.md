# Stage9 implementation admission

Disposition after the limited software verification is green:
`STAGE9_IMPLEMENTATION_READY_ON_LIMITED_STAGE8_CONTRACTS`.

This permits independent implementation against verified software contracts.
It neither implements Stage9 nor admits signed Alpha distribution, physical
historical-vault access, a Cloud provider call or an unverified model runtime.

| Dependency | Existing interface / authority | Stage9 disposition |
| --- | --- | --- |
| Exact original audio | `OriginalAudioPort.acquire`, `OriginalAudioReference` with identity/version/digest/frames; ADR-AUDIO-002 | Available after authenticated durable finalization; partial recovery must not masquerade as final |
| Secure reads | `inspect`, `extract`, `withAvailable`; source lease, authentication and revocation fences | Recheck at dispatch/retry/late-result acceptance; borrowed PCM is zeroized; do not cache access authority |
| Recording ownership | `AudioIdentity`, typed `RecordingId` and `AudioAssetId`; logical projection/chunks | Every result must retain exact source provenance; chunks are not consent units |
| Local ASR | `RecognitionPort`, `RecognitionRequest`, original-reader input; admitted model evidence remains separate | Local adapter, scheduling and model integration are Stage9 implementation tasks; capture does not depend on them |
| Cloud ASR | ADR-0009/0017/0019/0020; provider, ownership and deterministic contract harness | Contract-only implementation can proceed; live runtime/identity/authorization/legal/retention admission is separate |
| Transcript identity | Existing `TranscriptId`, `TranscriptRef`, `RecognitionJobId` in core:model | Create a distinct immutable result-version identity, bind it to RecordingId and exact OriginalAudioReference |
| Version persistence | ADR-0018, `DORA_ALPHA_TRANSCRIPT_VERSIONING_EDIT_CONTRACT_V0_1.json` | Implement encrypted persistence and transaction/migration tests in Stage9; current logical contract is not an implemented transcript database |
| Editing | Frozen ASR data/versioning contract, separate authoritative edit history and guarded active selection | Implement later in Stage9; never overwrite user corrections with a late ASR result |

Verified C3 baseline: `6f5e87317b163e4567ffa76da3cd3191bcfac826`.
This change preserves these APIs. The new tests use real finalized references,
authenticated reopen/readback, stale-reference rejection and independent sources.
The current software CI also runs the existing transcript contract negative tests.

The absent physical five-minute smoke and open development-security restoration
gate are release blockers, not a need to redesign the source/version contracts.
No Cloud endpoint, upload, credentials, model weights, ASR runtime, transcript
schema or editing UI is introduced in this branch.
