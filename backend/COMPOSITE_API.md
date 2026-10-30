# Composite Batch recognition, candidate HTTP v1 extension

This source-only extension is disabled by default (`long_runs_enabled=false`).
It does not deploy a backend, change the existing USD0.25 daily reserve, change
the USD5 policy ceiling, or change the AWS USD20/month Budget. Legacy short-job
routes and proofs remain compatible. A deployed old backend returns404 for these
routes; the client must reject a long submission before creating a remote run.

Every route uses the existing HTTPS, exact-body ECDSA proof, installation-bound
15-minute access session, current owner authority and replay protection. Renewing
a session does not renew consent or audio deadlines. No provider URLs, AWS keys,
plaintext temporary WAV files or unrestricted upload URLs are introduced.

`POST /v1/runs/quote` and `POST /v1/runs` accept the same exact JSON object:

```json
{
  "action_id": "00000000-0000-4000-8000-000000000001",
  "recording_id": "00000000-0000-4000-8000-000000000002",
  "source_digest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "source_version": 1,
  "frames": 1382400000,
  "language": "en-US",
  "consent_version": "dora-cloud-manual-v1",
  "consent_source": "MANUAL_USER_ACTION",
  "consent_granted_at": 1,
  "window_policy": "cloud-balanced-1800-v1"
}
```

The timestamp above is illustrative; a new run requires the existing positive,
not-more-than60-seconds-future manual consent timestamp. Frames are integers in
8000..1382400000, at16kHz mono PCM16LE. Full-source byte offsets can exceed2GiB;
there is no full-source WAV upload. The opaque vault source digest is distinct
from each child WAV SHA-256.

Quote returns `enabled`, `affordable`, `required_micro_usd`,
`available_micro_usd`, `part_count`, `window_policy`, `max_frames` and
`max_part_frames`. Monetary values are integer micro-USD. A quote has no paid
side effects. Creating a new run atomically reserves all first attempts before
any child can put S3 audio or start Transcribe. Exact creation/retry reuses the
same hold. Insufficient aggregate reserve returns429 `AGGREGATE_RESERVE_LIMIT`.
Disabled admission returns403 `LONG_RUNS_DISABLED`.

For `n=ceil(frames/28800000)`, part `i` starts at `floor(frames*i/n)` and ends
before `floor(frames*(i+1)/n)`. There are at most48 parts, each at most30minutes.
Balancing preserves every frame and avoids an invalid sub0.5-second tail.
The server derives child UUIDs from parent UUID, versioned policy, index and
exact range. Client-supplied child bindings are not accepted.

`GET /v1/runs/{id}` and create return bounded metadata only: parent identity,
source digest/version/frames, policy, reserved micro-USD, deadline and at most48
part descriptors (`index`, child `id`, `first_frame`, `frames`, `status`). No
combined transcript or audio is returned. Parent states are `UPLOADING`,
`PARTIAL`, `COMPLETED`, `CANCELLED`. Partial results are not a complete version.

`POST /v1/runs/{id}/parts/{index}` takes `{}` and creates or returns that child.
Earlier children must be completed. Its response is the existing job state plus
`parent_id`, `part_index`, `first_frame` and child `frames`. Existing
`/v1/jobs/{child}/audio`, status, retry and cancel routes carry its bounded WAV.
Each WAV has its own canonical44-byte header and exact `frames*2+44` length,
at most57600044bytes, using decoded chunks at most65536bytes. Metadata requests
are at most100KiB and audio JSON at most192KiB. A declared Content-Length must
match actual bounded bytes; signed proofs always cover those actual bytes.

Child attempt1 consumes the parent hold without another reservation. Explicit
retry after known terminal provider failure can reserve attempt2; unknown Start
outcomes never authorize another Start. One active owner chain, ten new parent
or ordinary actions perUTCday, two attempts perchild and finite daily reserves
remain enforced. Children are not48 unrelated new user actions.
If an unstarted child crosses aUTCday boundary, its unused allocation is moved
atomically from the original hold into a child reservation on the actual start
day, after checking that day's available reserve. The overall amount is not
charged twice. Already started or uncertain attempts never receive this credit.

The first accepted byte of any child atomically pins the parent's original
24-hour audio deadline. Later children inherit it, even when created later.
Retry, reconnect, restart and session refresh cannot extend it. The independent
watchdog cancels overdue unfinished work and reconciles cleanup. Completed
validated results retain the existing bounded30-day result custody; expiry is
not permission to continue sending audio. Provider/internal physical erasure
limitations remain those of the existing contract.

`POST /v1/runs/{id}/cancel` takes `{}` and fences the parent and children.
The existing `/v1/jobs/{parent}/cancel` route is also an alias returning an
ordinary cancellation state, so durable Android cancellation markers can
reconcile parent IDs. Revoked parents cannot resume. An interrupted foreground
attempt can explicitly resume the same non-revoked parent and completed parts.
Cancellation does not delete accepted local transcript pages or prior edits.
Parent revocation, every child revocation and encrypted temporary/result erasure
commit in one SQLite transaction before external cleanup. The watchdog also
reconciles a previously committed parent-only cancellation intent after restart.
Completed child reads always honor explicit parent/grant revocation; only a
persisted `DEADLINE_EXCEEDED` reason with still-valid installation authority
permits the existing completed-result retention exception.

Backend validation iterates authenticated encrypted SQLite chunks and computes
the WAV checksum before dispatch. S3 receives a seekable reader with one cached
64KiB plaintext chunk, bounded reads, explicit ContentLength and checksum. It
never receives a joined plaintext WAV. The SDK is configured for one attempt;
an offline HTTP transport test exercises actual SDK serialization without AWS.

Composite provider JSON is bounded to8MiB; its text is at most1MiB, segment count
at most20000 and combined segment text at most1MiB. Android permits an8MiB
response for composite part resume routes and children learned from this run's
bounded manifest. The paged
journal separately bounds encoded provenance to2MiB. Oversize/malformed outputs
fail the child without silent truncation or publication of an incomplete final
version. Legacy provider JSON/text/segment limits remain1MiB/256KiB/10000.

Each child result is committed locally before the next child. Provider times
are relative to the child; Android validates that interval and converts them to
global milliseconds from its source-bound `first_frame`. Missing times remain
missing. The shared paged transcript journal preserves complete parts across
failure/restart, and publishes one final version only after every part commits.

## Cost verification, 2026-10-30

The current official [Frankfurt public price offer](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/transcribe/current/eu-central-1/index.json)
(publication2026-09-11, effective2026-06-01, SKU9BDG889E5Y3GVM5S,
EUC1-TranscribeAudio) lists USD0.0001/second, or USD0.006/minute. The
[pricing page](https://aws.amazon.com/transcribe/pricing/) specifies per-second
billing with no minimum. This public HTTP lookup used no AWS credentials.

The unchanged conservative reserve is USD0.03/minute plusUSD0.01/attempt, with
the existing15-second conservative floor. Thirty minutes reserveUSD0.91; a
24-hour48-child plan reservesUSD43.68. The ASR-only published price for24hours
isUSD8.64, excluding storage, requests, encryption, compute, network and taxes.
Neither value is a measured invoice. Both exceed the unchanged production
USD0.25 daily reserve; the full request must be blocked before paid work.
No reserve increase or production deployment is authorized by software tests.

The test-only TLS fixture supports `--composite`, defaults off, and retains a
finite USD5 reserve. Tests that exercise a full24-hour plan use explicitly
fictional injected provider prices; these are not live pricing or permission.
