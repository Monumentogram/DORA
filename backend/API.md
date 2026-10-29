# DORA manual Cloud ASR HTTP v1

Frozen Stage18.1C interface. All JSON names are snake_case; JSON is UTF-8. HTTPS
through a trusted reverse proxy is mandatory; no query strings or redirects.
Errors are `{"error_code":"SAFE_CODE"}` without reflected request/provider text.
400/422 malformed input, 401 proof/session, 403 consent, 404 inaccessible resource,
409 identity/chunk conflict, 413 size, 429 quota, 503 unavailable. Never retry a
paid operation automatically. Use a new proof for every HTTP request.

Every request includes X-Dora-Installation (canonical UUID), X-Dora-Time (integer
epoch seconds), X-Dora-Nonce (canonical UUID), X-Dora-Signature (standard Base64
ASN.1 DER ECDSA P-256/SHA-256 signature). Sign exact UTF-8 bytes:

```
DORA-ASR-V1\nMETHOD\nPATH\nTIME\nNONCE\nSHA256_HEX_BODY
```

There is no trailing LF. METHOD is uppercase; PATH is the exact allowlisted
path, UUIDs lowercase; hash the actual body bytes (GET uses empty body). Timestamp
tolerance is 60 seconds. Nonces are durably single-use across paths. Public keys
are admitted only by an operator invitation command with server-owned owner binding.
An operator then confirms permanent trust for the exact existing key with `trust`.
Unmigrated invitations remain finite; trusted registrations last until explicit
revocation and do not inherit the invitation deadline. All paths except session also require Authorization:
Bearer access_token. A token cannot be used with another installation's proof.

| Method/path | Request | Response |
|---|---|---|
| POST /v1/session | `{}` | access_token, expires_at (epoch seconds) |
| POST /v1/recordings | recording_id UUID, source_digest 64 lowercase hex, source_version 1, frames integer 1..4800000 | recording_id |
| POST /v1/jobs | action_id UUID, recording_id UUID, language `ru-RU` or `en-US`, consent_version `dora-cloud-manual-v1`, consent_source `MANUAL_USER_ACTION`, consent_granted_at epoch milliseconds | job state |
| POST /v1/jobs/{id}/audio | offset integer WAV byte offset, data_base64 standard Base64 <=65536 decoded bytes, final boolean | job state |
| GET /v1/jobs/{id} | empty body | job state |
| POST /v1/jobs/{id}/retry | `{}` | job state |
| POST /v1/jobs/{id}/cancel | `{}` | job state |

Job state: id (= action_id), status (`UPLOADING`, `QUEUED`, `IN_PROGRESS`,
`COMPLETED`, `FAILED`, `CANCELLED`), uploaded_bytes, retryable boolean,
error_code nullable, cleanup_pending boolean, result nullable.

Result: text, provider_id `amazon-transcribe`, model
`standard-batch:eu-central-1:ru-RU` (or `en-US`), provider_job_id,
source_digest, source_version, completed_at (stable epoch milliseconds),
segments [{text, start_ms nullable integer, end_ms nullable integer,
confidence nullable number}]. Missing provider evidence remains null.

Stage9.2R envelope correction: audio-route requests are limited to 192 KiB,
including JSON escaping, while all other routes retain the 100 KiB limit.
The decoded audio chunk limit remains 65536 bytes. Android JSONObject escapes
Base64 slashes, so a valid chunk can need 174808 JSON bytes at offset zero.
Proofs still cover the exact transmitted bytes. Upload is a canonical
44-byte WAV header followed by mono PCM16LE at 16000 Hz, exact frames*2+44 bytes.
Registration permits 1..4800000 frames; provider dispatch additionally rejects
fewer than 8000 frames (the accepted 6.2D minimum is 0.5 seconds). No bytes reach
AWS until the complete WAV is validated. source_digest is opaque original vault
identity, **not** the uploaded WAV SHA-256; the server records both separately.

Identical recording registration and action creation are idempotent; changing any
binding is rejected. Exact duplicate chunks return progress, conflicting bytes and
holes are rejected. Consent is checked even for duplicate uploads. Fresh actions
require a positive consent timestamp no more than 60 seconds in the future;
existing exact actions retain the original grant and immutable deadline. Cancel
revokes that action and fences future bytes, dispatch and result publication.
Cancellation requires authentication and ownership, independently of ASR consent.

Access sessions last at most 15 minutes (also bounded by invitation expiry for
unmigrated finite grants). Obtain a
new session by fresh key proof; there is no stored reusable refresh credential.
The current installation grant is checked on each operation.

Retry resumes the same action: uploading returns current offset; active/uncertain
starts reconcile the deterministic provider name. Only a known terminal provider
failure can authorize attempt 2, and only by explicit retry. Repeated retries
cannot create attempt 3. A lost/uncertain Start never permits another Start.

The first accepted audio byte fixes an immutable 24-hour deadline. Retries and
restarts do not extend it. Cleanup is durable pending work, independently swept
by a watchdog; running provider jobs cannot be hard-cancelled. Completed results
are durable and encrypted before temporary data deletion. One active action per
owner, 10 new actions/day and 2 paid attempts/action are hard ceilings; a configured
small daily USD reserve cap is separately enforced. Reserved amounts are upper
bounds, not billed cost.

Bounded implementation: one service process plus its watchdog on one SQLite WAL
ledger, serialized operations, AES-GCM payload encryption with a separate
server-owned key. This is an explicit owner-only alternative to PostgreSQL, not
a horizontally scalable deployment. Live mode requires fresh account/configuration
preflight; missing evidence fails closed. Android never receives AWS credentials.
