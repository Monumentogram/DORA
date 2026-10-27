# ADR-0025 — Manual Cloud ASR through the DORA backend

Date: 2026-10-27. Status: implementation decision within the explicit Stage18.1C
owner request; software qualification and live deployment admission are separate.
Base: `98a975f3aff268db605573e1cd666dbc237f3123`, accepted Stage9/Owner Preview.

## Authority and scope

This additive decision implements the owner's Stage18.1C request for one saved
recording. It preserves the [ASR scenarios](../product/DORA_ASR_USER_SCENARIOS_V0_1.md),
[data/versioning contract](../contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md),
[DORA control boundary](ADR-0009-alpha-cloud-execution-boundary.md),
[retention decision](ADR-0012-alpha-retention-periods-and-provider-copy-limits.md),
[ownership contract](ADR-0019-cloud-identity-authorization-ownership.md) and
[Stage9 transcript storage](ADR-0022-stage9-bounded-local-transcript.md).
Historical documents and evidence remain unchanged.

The [6.2D terminal closure](../stage0/DORA_CLOUD_62D_DUPLICATE_NAME_CLOSURE_RESULT_V0_1.md)
and [owner English overlay](../stage0/DORA_CLOUD_62D_EN_OWNER_ACCEPTANCE_SCOPE_OVERLAY_V0_1.md)
constrain the adapter to standard Amazon Transcribe Batch, eu-central-1, ru-RU or
en-US, private input/output and distinct customer CMKs. JobExecutionSettings and
DataAccessRoleArn remain absent for that exact admitted configuration. Historical
EN READ is 20.4420%, a failed 18% benchmark accepted only for the sole owner;
this implementation does not produce a new quality measurement or admit another speaker.

## Android decision

Expose a manual Cloud action in the existing transcript editor. Persist the
recording/source-scoped disclosure version, manual basis, grant time and exact
DORA HTTPS destination before outbound work. Global connection configuration is
not consent. ALWAYS, ASK_EACH_RECORDING, batch grants and automatic reconnect are
outside Stage18.1C. No local model is required for Cloud-only use.

Reuse the protected original-audio port and SQLCipher transcript journal. Convert
verified PCM16LE mono 16-kHz originals to canonical WAV in bounded memory; cap
provider input at 300 seconds and retain the accepted 0.5-second minimum. Do not
write plaintext audio files or send a Local transcript as ASR input.

Extend the versioned transcript payload compatibly with immutable Cloud evidence:
provider, service configuration, provider job, exact source digest/version, stable
completion time and available segment timings/confidence. Missing evidence stays
unavailable; service configuration is not an invented provider model version.
Cloud completion preserves existing active text, edits and explicit selections.
The first valid result can become active; later Cloud versions are selectable
through the existing conflict/selection flow and revision checks.

Use the Android Keystore installation signing key and proof-bound HTTPS requests.
Android holds no AWS credentials. Foreground-owned processing stops on cancellation
or foreground loss; restart does not automatically send audio. A separate bounded
Keystore-encrypted noBackup cancellation ledger stores only action UUIDs and exact
destinations, so cancellation can survive retirement of the foreground vault lease.
These monotonic tombstones contain no audio or transcript and do not create a
second audio/version store. Read or write uncertainty blocks further Cloud work.

## Backend decision and explicit bounded choices

The new [backend](../../backend/README.md) uses Python/FastAPI/Pydantic, boto3 and
cryptography, with the [HTTP contract](../../backend/API.md) as its client boundary.
The prior research tooling is evidence, not production authentication or deployment.

For this owner-only vertical slice, SQLite WAL with FULL synchronization and an
OS/process lock replaces a PostgreSQL queue. One API process and an independent
watchdog share one ledger on one host. Multi-host/multi-replica deployment is not
supported. Encrypted bounded chunks pass through controlled HTTPS ingress rather
than unrestricted presigned upload URLs; each part rechecks current authorization.
This deliberately favors auditable revocation and small volume over throughput.

Manual operator invitations bind installation keys to server-owned owners and
finite expiry. Each request proves its method, path, timestamp, nonce and exact
body; durable nonces reject replay. Access tokens last at most 15 minutes and are
bound to installation proof. Fresh proof obtains another session; no reusable
refresh secret is issued. Authentication never substitutes for recording consent.

Input/output CMKs and the backend AES-GCM custody key are separate. This bounded
implementation uses one restricted runtime workload identity for ingress/dispatch/
ingestion, with key administration outside it. It does not claim the full separated
control/ingress/dispatcher/ingester/audit role deployment described in ADR-0011.
Actual least-privilege role/key policy review and deployment admission remain open;
software fixture success does not waive those controls or permit public deployment.

Dispatch intent, deterministic provider name and a monetary reserve are durable
before external calls. An uncertain Start is never submitted again. Explicit retry
can authorize attempt 2 only after known terminal provider failure. One active
action per owner, ten new actions per UTC day, two attempts per action and a bounded
daily reserve are distinct controls. Reserve estimates are not invoice amounts.

## Retention and operational consequences

The first accepted byte fixes the original 24-hour audio/transient-output deadline.
Retries and restarts do not extend it. Validated result custody commits before
temporary cleanup. Cancellation fences later bytes/publication, with pending
cleanup persisted independently. Running AWS computation cannot be hard-cancelled;
no receipt promises instantaneous erasure or deletion of every internal provider copy.
The watchdog and S3 lifecycle backstop are required independently of client polling.

Ordinary backend jobs/results have a 30-day bound; consent history and installation
retirement follow the existing 90-day rules. Identity-only tombstones do not expire
automatically while reassignment/callback uncertainty remains. No audio backup,
replication, archive or restore workflow is introduced. Local original audio and
transcript history remain governed by explicit user actions and existing contracts.

Live admission requires current account/region, effective Transcribe opt-out,
private S3 controls, regional CMKs/rotation, deletion lifecycle, TLS/proxy isolation,
watchdog operation and cost bounds. No live resources or jobs are authorized merely
by this ADR. `AWS_LIVE_RUNTIME = NOT_RUN` until separate real evidence exists.

## Verification boundary

The [Stage18 evidence draft](../evidence/stage18-cloud-asr/RESULT.md) distinguishes
backend/Android software checks, synthetic HTTPS integration, exact-head CI and
AWS live execution. CI uses emulator-only synthetic audio, no private models,
owner signing keys or AWS credentials. It checks real Android request signing,
HTTPS, server ownership/consent, encrypted Cloud versions, edits and process restart
against a test-only provider. It does not measure Amazon recognition quality.

No POCO operation, profile change, Pilot v2 update, LONG-01/LONG-02 access,
Stage8.6 endurance, streaming, diarization, LLM or public release is included.
Stage8.6 remains NOT_READY. Physical Cloud acceptance requires a separately scoped
owner interaction procedure; this decision neither starts nor authorizes it.
