# Owner-only manual Amazon Cloud ASR

This is the Stage18.1C product backend for the approved owner's closed internal
Alpha. AWS live execution has **not been run**. The test provider and TLS fixture
exist only under `tests/`; the production CLI has no fake mode, self-enrollment,
infrastructure provisioning, direct S3 upload URLs, or automatic paid retry.

The contract is [API.md](API.md). Python 3.12, FastAPI/Pydantic v2, boto3,
cryptography AES-256-GCM and SQLite are used. This bounded single-host ledger is an
explicit alternative to the technical plan's PostgreSQL queue. One API process,
an independent watchdog and operator CLI share an OS lock. The lock spans bounded
provider calls, so committed revocation fences later acceptance and dispatch.
Transactions commit reservation/intent before external side effects. Do not deploy
multiple API replicas, multiple hosts, network filesystems or database replicas.

## Verify without AWS

From `backend/`, create an isolated environment and install the complete pins:

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.lock.txt
.venv/Scripts/python.exe -m unittest discover -s tests -v
```

POSIX uses `.venv/bin/python`. Tests use synthetic PCM, real ECDSA signatures,
real HTTP routing, real SQLite/encryption and SDK `Stubber`; no AWS credential
lookup or AWS connection is needed. Root-level unittest discovery is not the
backend test command because `backend/` is an independent Python source root.

For Android emulator integration only:

```powershell
.venv/Scripts/python.exe tests/fixture_server.py --invitation C:/private/public-invitation.json --state-dir C:/private/dora-fixture --port 8443
```

This generates a one-day ephemeral TLS certificate for localhost, 127.0.0.1 and
10.0.2.2. Trust that exact test certificate only in the test harness. The provider
completes with synthetic text. This does not demonstrate AWS quality or live
authentication against a deployed service. Never deploy or package `tests/`.

## Operator setup

1. Place a private copy of `config.example.json` outside the repository. Replace
   every placeholder with current, separately authorized owner resources. Leave
   `live_enabled: false` until the account and service are approved. Use an IAM
   workload role, never access keys in config or Android. The exact account and
   region must be eu-central-1; input/output buckets and customer CMKs are distinct.
2. Create the key with `python -m dora_cloud --config PRIVATE_CONFIG init-key`.
   The file is exclusive-create, 32 random bytes, mode 0600 on POSIX. Isolate `/etc/dora`
   and `/var/lib/dora` with owner-only ACLs (Windows requires an operator ACL review).
   Run as a dedicated unprivileged OS account. The custody key is separate from
   both AWS CMKs and must remain recoverable while any encrypted state exists.
   Lost/mismatched keys fail closed. No key or state backup is created by this code.
3. Export the installation public key from Android. The JSON is exactly
   `{"installation_id":"UUID","public_key_spki_base64":"BASE64_DER_SPKI"}`.
   Independently confirm its provenance with the approved owner; public-key upload
   is not invitation proof. Enroll with an explicit finite epoch-seconds expiry:
   `python -m dora_cloud --config PRIVATE_CONFIG invite --invitation PUBLIC_JSON --expires-at EPOCH_SECONDS`.
   The server supplies owner_id from its private config. A used/retired installation
   ID cannot be rebound to another key or owner. Reinstall/identity transfer is not
   implemented. Revoke with the `revoke --installation UUID` subcommand; the watchdog
   then deletes pending content independently of any client's consent or session.
4. An authorized operator must configure private bucket ownership, all four public
   access blocks, TLS-only policies, disabled versioning/replication/object lock,
   exact default CMKs and one-day expiration/abort-incomplete-upload lifecycle rules.
   Enable rotation on single-region symmetric customer-managed keys. Do not enable
   archival transitions, audio backups, access logging of payloads or replicas.
   `deployment/` contains review-only IAM/bucket/proxy/service templates. Nothing
   applies them automatically. Resolve key policies and workload-role trust under
   separate administrator authority; runtime has no key-management permissions.
5. Effective Organizations `AISERVICES_OPT_OUT_POLICY` must target the exact
   account and resolve Transcribe (or its default) to the effective string
   `optOut`. An attached policy or a screenshot is not sufficient. Enable live mode
   only in the private config, then run `preflight`. It performs **read-only AWS
   calls** and fails closed on missing permissions, malformed controls or drift.
   No resource is created. Live API startup repeats this check; new uploads/starts
   recheck at least every 60 seconds and reject changed configuration.
6. Install the separate watchdog service with automatic restart, then the API
   service and a trusted HTTPS reverse proxy. Examples bind API only to loopback,
   disable request logging and restrict proxy headers to loopback. Set TLS 1.2+
   with a current certificate; Android has no cleartext fallback. Keep firewall
   access owner-only, restrict request sizes/rates and monitor watchdog health.
   `serve` reconciles persisted expiry/revocation before listening. `watchdog`
   sweeps every 15 seconds; `watchdog --once` is available for a supervised timer.
   Cleanup remains allowed even when live admission is disabled. A configuration
   change must preserve original resource bindings: stop admission, drain cleanup,
   then start a **new ledger** for different buckets/keys/account. Never retarget
   existing persisted jobs by editing their configuration.

The backend issues short-lived, single-current-session tokens stored only as
hashes. New signed session requests replace refresh credentials; a stolen token
without the installation key is insufficient. This bounded implementation keeps
ingress/dispatch/ingestion under one restricted workload identity, while key
administration and server custody remain separate. Full separate service-role
deployment and PostgreSQL/multiple-worker scaling are outside this owner Alpha.

## Safety and failure behavior

Only explicit manual actions carry a per-recording grant. No local transcript is
sent as input. The server holds source identity separately from verified WAV hash.
Canonical 16-kHz mono PCM16 WAV must be complete, 0.5–300 seconds, and match exact
frame/byte counts before S3 or paid dispatch. Upload chunks are <=64 KiB decoded
and encrypted at rest; no more than 100 KiB enters a request buffer.

One active action per owner, ten new actions per UTC day, two attempts per action
and a configured daily reserve cap are separate checks. The default per-minute
and ancillary reserves are **conservative configurable estimates, not prices or
invoices**; validate current rates/bounds before live authorization. Reservations
are never refunded on uncertain outcomes. There are no SDK paid retries (total
attempts = 1), and only an explicit retry after a durably observed terminal FAILED
job may make attempt 2. Both attempts retain the original 24-hour clock.

Before external calls, the unique `dora-<action UUID>-<attempt>` name and full
reservation are durable. A crash between reservation and Start, a connection loss,
429/5xx, conflicting name, or missing Get response remains uncertain. Get/retry
reconcile that name and never repeat Start. This deliberately sacrifices
availability: absence after ambiguous intent does not authorize another attempt.
The watchdog never initiates paid work.

The adapter never follows a provider transcript URL. It reads only its fixed
output bucket/key with ExpectedBucketOwner, checks output CMK and exact result
job/account identity, and validates the observed job language/media configuration.
Unknown timestamp/confidence stays null. Out-of-range/nonmonotonic timing and
unrepresentable sub-millisecond values fail closed. The `model` value identifies
the chosen standard-batch configuration; it is not an invented model version or
a claim of timing accuracy. Historical EN limitations remain as admitted in 6.2D.

Completion means the validated result has been encrypted and durably committed
before temporary input/output/job cleanup. Duplicate requests cannot overwrite it.
Cancellation fences future bytes/publication and drops local temporary ciphertext,
then deletes S3 input promptly. AWS running computation cannot be hard-cancelled;
terminal output and job deletion remain a persistent outbox. Failed cleanup is
visible as `cleanup_pending`, never an all-copy erasure receipt. Lost terminal
delete acknowledgments reconcile exact-name absence without parsing AWS messages.

Audio and transient output inherit a 24-hour maximum from the first accepted byte.
The independent watchdog and S3 lifecycle backstop are mandatory operational
controls. A service/network outage can delay deletion; no exact physical-erasure
SLA is claimed. Alert on a pending/overdue outbox and restore cleanup promptly.
Ordinary job/result/reservation data expires at 30 days; consent history is encrypted
and kept for 90 days after retirement. Immutable source/installation authorization
bindings survive while finite authority is active, then expire after 90 days.
Opaque identity-only tombstones remain to prohibit reassignment; they have no
automatic retirement because unresolved inventories/callbacks must never resurrect
an ID. A broader erasure/restore workflow requires the ADR-0012 120-day minimum and
separate inventory review. No database/audio backup or restore tool is supplied.

## Deployment evidence still required

The tests establish code behavior with synthetic fixtures. Real AWS execution,
current private IAM/key policies, effective opt-out, deployed TLS/proxy ACLs,
watchdog supervision, current billing reserve and first-real-audio authorization
must be verified for the owner's configuration before use. Previous research
resources were retired; this code does not reuse their credentials or authority.
External/public use and a second speaker remain unadmitted. Do not run a live
recognition merely because the unit suite is green.
